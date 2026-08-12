package darling;

import annotation.Draft;
import annotation.Intention;
import annotation.Required;
import annotation.Volatile;
import lang.Vec4;
import nio.ForeignMemory;
import oop.TypeRegister;

import java.lang.foreign.Arena;
import java.lang.invoke.MethodHandles;
import java.lang.invoke.VarHandle;

/**
 * Off-heap layout base for every darling UI node. Flat struct, zero-GC,
 * registered in oop.TypeRegister like every other engine type. It owns the
 * layout core — position, size, scale, the two-anchor system, percentage
 * placement, z-order and the visible/enabled/dirty flags — and is the
 * structural parent of darling.Panel (see TypeRegister.getParentClass).
 *
 * Subclass layout contract: a subclass slots its own payload directly after
 * this class's user payload (Container.USER_STRIDE), so a subclass pointer can
 * be passed to any Container accessor and the prefix bytes line up. Container
 * accessors accept BOTH standalone Container pointers and subclass pointers.
 *
 * Anchor encoding: 3x3 grid, row = top/middle/bottom, col = left/center/right.
 *   ANCHOR_* = row * 3 + col, values 0-8.
 *
 * Resolution order:
 *   layout-space (x,y,w,h + anchors + percents) -> resolved rect
 *   -> scale about element anchor -> screen rect
 */
@Draft
@Intention("Retained-mode off-heap UI layout base: two-anchor absolute layout, percentage centers, scale about the element anchor, dirty propagation. Structural parent of Panel.")
public final class Container {

    @Required
    public static final int CLASS_ID = TypeRegister.ID_CONTAINER;

    public static final int TYPE_SINGLETON = TypeRegister.CONTAINER_SINGLETON; // 0x10000079
    public static final int TYPE_ARRAY     = TypeRegister.CONTAINER_ARRAY;     // 0x20000079
    public static final int TYPE_POINTER   = TypeRegister.CONTAINER_POINTER;   // 0x30000079

    // --- Anchor constants (3x3 grid, row*3+col) ---
    public static final int ANCHOR_TOP_LEFT      = 0;
    public static final int ANCHOR_TOP_CENTER    = 1;
    public static final int ANCHOR_TOP_RIGHT     = 2;
    public static final int ANCHOR_MIDDLE_LEFT   = 3;
    public static final int ANCHOR_MIDDLE_CENTER = 4;
    public static final int ANCHOR_MIDDLE_RIGHT  = 5;
    public static final int ANCHOR_BOTTOM_LEFT   = 6;
    public static final int ANCHOR_BOTTOM_CENTER = 7;
    public static final int ANCHOR_BOTTOM_RIGHT  = 8;

    public static final int ANCHOR_MIN = ANCHOR_TOP_LEFT;
    public static final int ANCHOR_MAX = ANCHOR_BOTTOM_RIGHT;

    // Percent sentinel: < 0 means "not set, use anchor".
    public static final float PERCENT_UNSET = -1.0f;

    // --- Field offsets (relative to userPtr). Subclass payloads start at USER_STRIDE. ---
    static final int OFF_X          = 0;   // float
    static final int OFF_Y          = 4;   // float
    static final int OFF_W          = 8;   // float
    static final int OFF_H          = 12;  // float
    static final int OFF_SCALE_X    = 16;  // float
    static final int OFF_SCALE_Y    = 20;  // float
    static final int OFF_REF_ANCHOR = 24;  // int
    static final int OFF_ELEM_ANCHOR = 28; // int
    static final int OFF_PERCENT_X  = 32;  // float
    static final int OFF_PERCENT_Y  = 36;  // float
    static final int OFF_Z          = 40;  // int
    static final int OFF_VISIBLE    = 44;  // byte
    static final int OFF_ENABLED    = 45;  // byte
    static final int OFF_DIRTY      = 46;  // byte

    static final long USER_STRIDE = 48L; // bytes of user payload
    static final long SLOT_SIZE   = 56L; // 8B header + 48B payload

    // --- Pool (lock-free free-list, ABA-tagged head, expansion flag) ---
    private static final int DEFAULT_CAPACITY = 1024;

    private static final VarHandle FREE_HEAD_VH;
    private static final VarHandle EXPANDING_VH;

    private static volatile long freeHead;     // top 16 = gen tag, bottom 48 = raw ptr
    private static volatile int expanding = 0;

    private static Arena poolArena;
    private static volatile boolean active;

    static {
        try {
            MethodHandles.Lookup lookup = MethodHandles.lookup();
            FREE_HEAD_VH = lookup.findStaticVarHandle(Container.class, "freeHead", long.class);
            EXPANDING_VH = lookup.findStaticVarHandle(Container.class, "expanding", int.class);
        } catch (ReflectiveOperationException e) {
            throw new ExceptionInInitializerError(e);
        }

        poolArena = Arena.ofShared();
        active = true;
        expandPool();
    }

    private Container() {}

    private static void checkActive() {
        if (!active) throw new IllegalStateException("Container subsystem is not active!");
    }

    public static void freeAll() {
        if (active) {
            active = false;
            if (poolArena != null && poolArena.scope().isAlive()) {
                poolArena.close();
            }
        }
    }

    private static void expandPool() {
        long totalBytes = DEFAULT_CAPACITY * SLOT_SIZE;
        long baseAddress = poolArena.allocate(totalBytes, 8).address();

        for (int i = 0; i < DEFAULT_CAPACITY; i++) {
            long currentBlock = baseAddress + (i * SLOT_SIZE);
            long userPtr = currentBlock + 8L;

            while (true) {
                long oldTagged = freeHead;
                long oldRawHead = oldTagged & 0x0000FFFFFFFFFFFFL;

                ForeignMemory.setUnsafe(userPtr, oldRawHead);

                long nextGen = ((oldTagged >>> 48) + 1L) & 0xFFFFL;
                long newTagged = (nextGen << 48) | (userPtr & 0x0000FFFFFFFFFFFFL);

                if (FREE_HEAD_VH.compareAndSet(oldTagged, newTagged)) break;
            }
        }
    }

    // =========================================================================
    // ALLOCATION / RECYCLING
    // =========================================================================

    /** Allocates a standalone Container (a bare group/div). Subclasses allocate their own slots. */
    public static long allocate() {
        checkActive();

        while (true) {
            long oldTagged = freeHead;
            long rawHead = oldTagged & 0x0000FFFFFFFFFFFFL;

            if (rawHead == 0L) {
                if (EXPANDING_VH.compareAndSet(0, 1)) {
                    expandPool();
                    EXPANDING_VH.setVolatile(0);
                } else {
                    Thread.onSpinWait();
                }
                continue;
            }

            long nextRawHead = ForeignMemory.getUnsafeLong(rawHead);
            long nextGen = ((oldTagged >>> 48) + 1L) & 0xFFFFL;
            long newTagged = (nextGen << 48) | (nextRawHead & 0x0000FFFFFFFFFFFFL);

            if (FREE_HEAD_VH.compareAndSet(oldTagged, newTagged)) {
                long base = rawHead - 8L;
                ForeignMemory.setUnsafe(base, TYPE_SINGLETON);
                ForeignMemory.setUnsafe(base + 4L, 1);
                ForeignMemory.setUnsafe(rawHead, 0);
                initDefaults(rawHead);
                return rawHead;
            }
        }
    }

    /** Initializes the shared layout prefix. Subclasses call this before their own fields. */
    static void initDefaults(long ptr) {
        setX(ptr, 0f);
        setY(ptr, 0f);
        setWidth(ptr, 0f);
        setHeight(ptr, 0f);
        setScale(ptr, 1f, 1f);
        setReferenceAnchor(ptr, ANCHOR_TOP_LEFT);
        setElementAnchor(ptr, ANCHOR_TOP_LEFT);
        setPercentX(ptr, PERCENT_UNSET);
        setPercentY(ptr, PERCENT_UNSET);
        setZ(ptr, 0);
        setVisible(ptr, true);
        setEnabled(ptr, true);
        clearDirty(ptr);
    }

    public static void free(long ptr) {
        checkActive();
        if (ptr == 0L) return;

        int type = type(ptr);
        if (type != TYPE_SINGLETON) throw new IllegalStateException("Double free or corrupt Container pointer: 0x" + java.lang.Long.toHexString(ptr).toUpperCase());

        long base = ptr - 8L;
        ForeignMemory.setUnsafe(base, 0);
        ForeignMemory.setUnsafe(base + 4L, -1);

        while (true) {
            long oldTagged = freeHead;
            long oldRawHead = oldTagged & 0x0000FFFFFFFFFFFFL;

            ForeignMemory.setUnsafe(ptr, oldRawHead);

            long nextGen = ((oldTagged >>> 48) + 1L) & 0xFFFFL;
            long newTagged = (nextGen << 48) | (ptr & 0x0000FFFFFFFFFFFFL);

            if (FREE_HEAD_VH.compareAndSet(oldTagged, newTagged)) return;
        }
    }

    // =========================================================================
    // ARCHITECTURAL CHECKS & METADATA
    // =========================================================================

    public static int classId() { return CLASS_ID; }

    public static int type(long ptr) {
        if (ptr == 0L) throw new NullPointerException("type() on NULL Container pointer!");
        return ForeignMemory.getUnsafeInt(ptr - 8L);
    }

    public static int length(long ptr) {
        if (ptr == 0L) throw new NullPointerException("length() on NULL Container pointer!");
        return ForeignMemory.getUnsafeInt(ptr - 4L);
    }

    public static int classId(long ptr) { return TypeRegister.getClassId(type(ptr)); }

    /**
     * Accepts a standalone Container OR any class whose structural parent is
     * Container (e.g. Panel), since subclass payloads extend this layout.
     */
    private static void checkContainer(long ptr) {
        if (ptr == 0L) throw new NullPointerException("Accessing NULL Container pointer!");
        int cls = classId(ptr);
        if (cls != CLASS_ID && TypeRegister.getParentClass(cls) != CLASS_ID)
            throw new IllegalArgumentException("Pointer 0x" + java.lang.Long.toHexString(ptr).toUpperCase() + " is Class ID " + cls + ", not a Container (or subclass)");
    }

    private static void checkAnchor(int anchor) {
        if (anchor < ANCHOR_MIN || anchor > ANCHOR_MAX) throw new IllegalArgumentException("Invalid anchor " + anchor + " (must be 0-8)");
    }

    // =========================================================================
    // POSITION & SIZE
    // =========================================================================

    public static float getX(long ptr) { checkContainer(ptr); return ForeignMemory.getFloat(ptr + OFF_X); }
    public static float getY(long ptr) { checkContainer(ptr); return ForeignMemory.getFloat(ptr + OFF_Y); }
    public static float getWidth(long ptr) { checkContainer(ptr); return ForeignMemory.getFloat(ptr + OFF_W); }
    public static float getHeight(long ptr) { checkContainer(ptr); return ForeignMemory.getFloat(ptr + OFF_H); }

    public static void setX(long ptr, float x) { checkContainer(ptr); ForeignMemory.setFloat(ptr + OFF_X, x); markDirty(ptr); }
    public static void setY(long ptr, float y) { checkContainer(ptr); ForeignMemory.setFloat(ptr + OFF_Y, y); markDirty(ptr); }
    public static void setWidth(long ptr, float width) { checkContainer(ptr); ForeignMemory.setFloat(ptr + OFF_W, width); markDirty(ptr); }
    public static void setHeight(long ptr, float height) { checkContainer(ptr); ForeignMemory.setFloat(ptr + OFF_H, height); markDirty(ptr); }

    public static void setPos(long ptr, float x, float y) { setX(ptr, x); setY(ptr, y); }
    public static void setSize(long ptr, float width, float height) { setWidth(ptr, width); setHeight(ptr, height); }

    // =========================================================================
    // SCALE
    // =========================================================================

    public static float getScaleWidth(long ptr) { checkContainer(ptr); return ForeignMemory.getFloat(ptr + OFF_SCALE_X); }
    public static float getScaleHeight(long ptr) { checkContainer(ptr); return ForeignMemory.getFloat(ptr + OFF_SCALE_Y); }

    public static void setScaleWidth(long ptr, float scaleWidth) { checkContainer(ptr); ForeignMemory.setFloat(ptr + OFF_SCALE_X, scaleWidth); markDirty(ptr); }
    public static void setScaleHeight(long ptr, float scaleHeight) { checkContainer(ptr); ForeignMemory.setFloat(ptr + OFF_SCALE_Y, scaleHeight); markDirty(ptr); }

    /** Sets both scale factors, one dirty region. */
    public static void setScale(long ptr, float scaleWidth, float scaleHeight) { setScaleWidth(ptr, scaleWidth); setScaleHeight(ptr, scaleHeight); }

    // =========================================================================
    // ANCHORS
    // =========================================================================

    public static int getReferenceAnchor(long ptr) { checkContainer(ptr); return ForeignMemory.getInt(ptr + OFF_REF_ANCHOR); }
    public static int getElementAnchor(long ptr)   { checkContainer(ptr); return ForeignMemory.getInt(ptr + OFF_ELEM_ANCHOR); }

    public static void setReferenceAnchor(long ptr, int anchor) {
        checkContainer(ptr);
        checkAnchor(anchor);
        ForeignMemory.setInt(ptr + OFF_REF_ANCHOR, anchor);
        markDirty(ptr);
    }

    public static void setElementAnchor(long ptr, int anchor) {
        checkContainer(ptr);
        checkAnchor(anchor);
        ForeignMemory.setInt(ptr + OFF_ELEM_ANCHOR, anchor);
        markDirty(ptr);
    }

    /** Sets both anchors (reference + element). */
    public static void setAnchor(long ptr, int referenceAnchor, int elementAnchor) {
        setReferenceAnchor(ptr, referenceAnchor);
        setElementAnchor(ptr, elementAnchor);
    }

    // =========================================================================
    // PERCENTAGE PLACEMENT
    // =========================================================================

    public static float getPercentX(long ptr) { checkContainer(ptr); return ForeignMemory.getFloat(ptr + OFF_PERCENT_X); }
    public static float getPercentY(long ptr) { checkContainer(ptr); return ForeignMemory.getFloat(ptr + OFF_PERCENT_Y); }

    /** Percent of parent width/height for placement. < 0 (PERCENT_UNSET) disables and falls back to anchor. */
    public static void setPercentX(long ptr, float pct) { checkContainer(ptr); ForeignMemory.setFloat(ptr + OFF_PERCENT_X, pct); markDirty(ptr); }
    public static void setPercentY(long ptr, float pct) { checkContainer(ptr); ForeignMemory.setFloat(ptr + OFF_PERCENT_Y, pct); markDirty(ptr); }

    public static boolean hasPercentX(long ptr) { return getPercentX(ptr) >= 0f; }
    public static boolean hasPercentY(long ptr) { return getPercentY(ptr) >= 0f; }

    // =========================================================================
    // Z-ORDER
    // =========================================================================

    public static int getZ(long ptr) { checkContainer(ptr); return ForeignMemory.getInt(ptr + OFF_Z); }
    public static void setZ(long ptr, int z) { checkContainer(ptr); ForeignMemory.setInt(ptr + OFF_Z, z); markDirty(ptr); }

    // =========================================================================
    // VISIBILITY / ENABLED / DIRTY
    // =========================================================================

    public static boolean isVisible(long ptr) { checkContainer(ptr); return ForeignMemory.getByte(ptr + OFF_VISIBLE) != 0; }
    public static boolean isEnabled(long ptr) { checkContainer(ptr); return ForeignMemory.getByte(ptr + OFF_ENABLED) != 0; }
    public static boolean isDirty(long ptr)    { checkContainer(ptr); return ForeignMemory.getByte(ptr + OFF_DIRTY) != 0; }

    public static void setVisible(long ptr, boolean visible) { checkContainer(ptr); ForeignMemory.setByte(ptr + OFF_VISIBLE, (byte) (visible ? 1 : 0)); markDirty(ptr); }
    public static void setEnabled(long ptr, boolean enabled) { checkContainer(ptr); ForeignMemory.setByte(ptr + OFF_ENABLED, (byte) (enabled ? 1 : 0)); markDirty(ptr); }

    /**
     * Marks the node dirty. In Phase 1 this walks the parent-ref set and fans
     * out to every parent holding a view of this node's shared slots; for now
     * it sets the flag that the invalidation engine polls.
     */
    @Volatile
    public static void markDirty(long ptr) {
        checkContainer(ptr);
        ForeignMemory.setVolatileByte(ptr + OFF_DIRTY, (byte) 1);
    }

    public static void clearDirty(long ptr) {
        checkContainer(ptr);
        ForeignMemory.setVolatileByte(ptr + OFF_DIRTY, (byte) 0);
    }

    // =========================================================================
    // LAYOUT RESOLUTION
    // =========================================================================

    /**
     * Resolves the node's screen rect within a parent content box.
     *
     * Writes 4 floats into outRect (primitive.Float array of length >= 4):
     * [screenX, screenY, screenW, screenH].
     *
     * Math:
     *   refPoint (on parent)  = referenceAnchor point + x/y layout offsets
     *     - anchor row/col -> (col * parentW)/2, (row * parentH)/2
     *     - percentX/Y override the anchor when >= 0
     *   element offset       = elementAnchor point of the SCALED node
     *   screenTopLeft        = refPoint + offsets - elementOffset
     *   scale applied last, about the element anchor.
     */
    public static void resolve(long ptr, float parentX, float parentY, float parentW, float parentH, long outRect) {
        checkContainer(ptr);
        if (outRect == 0L) throw new NullPointerException("resolve() outRect is NULL!");

        float w = getWidth(ptr), h = getHeight(ptr);
        float sx = getScaleWidth(ptr), sy = getScaleHeight(ptr);
        float sw = w * sx, sh = h * sy;

        int refAnchor = getReferenceAnchor(ptr);
        int refRow = refAnchor / 3;
        int refCol = refAnchor % 3;

        float refX = parentX + (refCol * parentW) / 2f;
        float refY = parentY + (refRow * parentH) / 2f;

        if (hasPercentX(ptr)) refX = parentX + getPercentX(ptr) * parentW;
        if (hasPercentY(ptr)) refY = parentY + getPercentY(ptr) * parentH;

        int elemAnchor = getElementAnchor(ptr);
        int elemRow = elemAnchor / 3;
        int elemCol = elemAnchor % 3;

        // element anchor point of the SCALED node; scale pivots here
        float elemX = (elemCol * sw) / 2f;
        float elemY = (elemRow * sh) / 2f;

        float screenX = refX + getX(ptr) - elemX;
        float screenY = refY + getY(ptr) - elemY;

        Vec4.set(outRect, screenX, screenY, sw, sh);
    }

    /** Hit-test: true if point (px, py) is inside the resolved screen rect. Uses off-heap scratch, 0 GC. */
    public static boolean hitTest(long ptr, float parentX, float parentY, float parentW, float parentH, float px, float py) {
        if (!isVisible(ptr)) return false;
        long scratch = primitive.Float.allocateArray(4);
        boolean hit;
        try {
            resolve(ptr, parentX, parentY, parentW, parentH, scratch);
            float rx = primitive.Float.get(scratch, 0);
            float ry = primitive.Float.get(scratch, 1);
            float rw = primitive.Float.get(scratch, 2);
            float rh = primitive.Float.get(scratch, 3);
            hit = px >= rx && px < rx + rw && py >= ry && py < ry + rh;
        } finally {
            primitive.Float.free(scratch);
        }
        return hit;
    }
}
