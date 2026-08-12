package darling;

import annotation.Draft;
import annotation.Intention;
import annotation.Required;
import annotation.Volatile;
import nio.ForeignMemory;
import oop.TypeRegister;

import java.lang.foreign.Arena;
import java.lang.invoke.MethodHandles;
import java.lang.invoke.VarHandle;

/**
 * Off-heap UI container / panel. Flat struct, zero-GC, registered in
 * oop.TypeRegister like every other engine type. Retained mode: the raw x/y/w/h
 * are LAYOUT inputs; the resolved screen rect is computed at rasterize time
 * from the two-anchor system (reference anchor + element anchor), percentage
 * placement and scale. Every setter marks the panel dirty so the invalidation
 * engine can walk the parent-ref set and re-render only what changed.
 *
 * Anchor encoding: 3x3 grid, row = top/middle/bottom, col = left/center/right.
 *   ANCHOR_* = row * 3 + col, values 0-8.
 *
 * Resolution order:
 *   layout-space (x,y,w,h + anchors + percents) -> resolved rect
 *   -> scale about element anchor -> screen rect
 */
@Draft
@Intention("Retained-mode off-heap UI container. Two-anchor absolute layout, percentage centers, scale about the element anchor, dirty propagation via parent-ref set.")
public final class Panel {

    @Required
    public static final int CLASS_ID = TypeRegister.ID_PANEL;

    public static final int TYPE_SINGLETON = TypeRegister.PANEL_SINGLETON; // 0x10000078
    public static final int TYPE_ARRAY     = TypeRegister.PANEL_ARRAY;     // 0x20000078
    public static final int TYPE_POINTER   = TypeRegister.PANEL_POINTER;   // 0x30000078

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

    // --- Field offsets (relative to userPtr) ---
    private static final int OFF_X          = 0;   // float
    private static final int OFF_Y          = 4;   // float
    private static final int OFF_W          = 8;   // float
    private static final int OFF_H          = 12;  // float
    private static final int OFF_SCALE_X    = 16;  // float
    private static final int OFF_SCALE_Y    = 20;  // float
    private static final int OFF_REF_ANCHOR = 24;  // int
    private static final int OFF_ELEM_ANCHOR = 28; // int
    private static final int OFF_PERCENT_X  = 32;  // float
    private static final int OFF_PERCENT_Y  = 36;  // float
    private static final int OFF_Z          = 40;  // int
    private static final int OFF_VISIBLE    = 44;  // byte
    private static final int OFF_ENABLED    = 45;  // byte
    private static final int OFF_DIRTY      = 46;  // byte
    private static final int OFF_PARENT_REF_SET = 48; // long (parent-ref set ptr)

    private static final long USER_STRIDE = 56L;  // bytes of user payload
    private static final long SLOT_SIZE   = 64L;  // 8B header + 56B payload

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
            FREE_HEAD_VH = lookup.findStaticVarHandle(Panel.class, "freeHead", long.class);
            EXPANDING_VH = lookup.findStaticVarHandle(Panel.class, "expanding", int.class);
        } catch (ReflectiveOperationException e) {
            throw new ExceptionInInitializerError(e);
        }

        poolArena = Arena.ofShared();
        active = true;
        expandPool();
    }

    private Panel() {}

    private static void checkActive() {
        if (!active) throw new IllegalStateException("Panel subsystem is not active!");
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

    private static void initDefaults(long ptr) {
        setX(ptr, 0f);
        setY(ptr, 0f);
        setW(ptr, 0f);
        setH(ptr, 0f);
        setScaleX(ptr, 1f);
        setScaleY(ptr, 1f);
        setReferenceAnchor(ptr, ANCHOR_TOP_LEFT);
        setElementAnchor(ptr, ANCHOR_TOP_LEFT);
        setPercentX(ptr, PERCENT_UNSET);
        setPercentY(ptr, PERCENT_UNSET);
        setZ(ptr, 0);
        setVisible(ptr, true);
        setEnabled(ptr, true);
        clearDirty(ptr);
        setParentRefSet(ptr, 0L);
    }

    public static void free(long ptr) {
        checkActive();
        if (ptr == 0L) return;

        int type = type(ptr);
        if (type != TYPE_SINGLETON) throw new IllegalStateException("Double free or corrupt Panel pointer: 0x" + java.lang.Long.toHexString(ptr).toUpperCase());

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
        if (ptr == 0L) throw new NullPointerException("type() on NULL Panel pointer!");
        return ForeignMemory.getUnsafeInt(ptr - 8L);
    }

    public static int length(long ptr) {
        if (ptr == 0L) throw new NullPointerException("length() on NULL Panel pointer!");
        return ForeignMemory.getUnsafeInt(ptr - 4L);
    }

    public static int classId(long ptr) { return TypeRegister.getClassId(type(ptr)); }

    private static void checkPanel(long ptr) {
        if (ptr == 0L) throw new NullPointerException("Accessing NULL Panel pointer!");
        if (classId(ptr) != CLASS_ID) throw new IllegalArgumentException("Pointer 0x" + java.lang.Long.toHexString(ptr).toUpperCase() + " is Class ID " + classId(ptr) + ", expected Panel");
    }

    private static void checkAnchor(int anchor) {
        if (anchor < ANCHOR_MIN || anchor > ANCHOR_MAX) throw new IllegalArgumentException("Invalid anchor " + anchor + " (must be 0-8)");
    }

    // =========================================================================
    // POSITION & SIZE
    // =========================================================================

    public static float getX(long ptr) { checkPanel(ptr); return ForeignMemory.getFloat(ptr + OFF_X); }
    public static float getY(long ptr) { checkPanel(ptr); return ForeignMemory.getFloat(ptr + OFF_Y); }
    public static float getW(long ptr) { checkPanel(ptr); return ForeignMemory.getFloat(ptr + OFF_W); }
    public static float getH(long ptr) { checkPanel(ptr); return ForeignMemory.getFloat(ptr + OFF_H); }
    public static float getWidth(long ptr)  { return getW(ptr); }
    public static float getHeight(long ptr) { return getH(ptr); }

    public static void setX(long ptr, float x) { checkPanel(ptr); ForeignMemory.setFloat(ptr + OFF_X, x); markDirty(ptr); }
    public static void setY(long ptr, float y) { checkPanel(ptr); ForeignMemory.setFloat(ptr + OFF_Y, y); markDirty(ptr); }
    public static void setW(long ptr, float w) { checkPanel(ptr); ForeignMemory.setFloat(ptr + OFF_W, w); markDirty(ptr); }
    public static void setH(long ptr, float h) { checkPanel(ptr); ForeignMemory.setFloat(ptr + OFF_H, h); markDirty(ptr); }

    public static void setPos(long ptr, float x, float y) { setX(ptr, x); setY(ptr, y); }
    public static void setSize(long ptr, float w, float h) { setW(ptr, w); setH(ptr, h); }

    // =========================================================================
    // SCALE
    // =========================================================================

    public static float getScaleX(long ptr) { checkPanel(ptr); return ForeignMemory.getFloat(ptr + OFF_SCALE_X); }
    public static float getScaleY(long ptr) { checkPanel(ptr); return ForeignMemory.getFloat(ptr + OFF_SCALE_Y); }

    public static void setScaleX(long ptr, float sx) { checkPanel(ptr); ForeignMemory.setFloat(ptr + OFF_SCALE_X, sx); markDirty(ptr); }
    public static void setScaleY(long ptr, float sy) { checkPanel(ptr); ForeignMemory.setFloat(ptr + OFF_SCALE_Y, sy); markDirty(ptr); }

    /** Sets both scale factors, one dirty region. */
    public static void setScale(long ptr, float sx, float sy) { setScaleX(ptr, sx); setScaleY(ptr, sy); }

    /** Scaled (resolved) size. */
    public static float getScaledW(long ptr) { checkPanel(ptr); return getW(ptr) * getScaleX(ptr); }
    public static float getScaledH(long ptr) { checkPanel(ptr); return getH(ptr) * getScaleY(ptr); }

    // =========================================================================
    // ANCHORS
    // =========================================================================

    public static int getReferenceAnchor(long ptr) { checkPanel(ptr); return ForeignMemory.getInt(ptr + OFF_REF_ANCHOR); }
    public static int getElementAnchor(long ptr)   { checkPanel(ptr); return ForeignMemory.getInt(ptr + OFF_ELEM_ANCHOR); }

    public static void setReferenceAnchor(long ptr, int anchor) {
        checkPanel(ptr);
        checkAnchor(anchor);
        ForeignMemory.setInt(ptr + OFF_REF_ANCHOR, anchor);
        markDirty(ptr);
    }

    public static void setElementAnchor(long ptr, int anchor) {
        checkPanel(ptr);
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

    public static float getPercentX(long ptr) { checkPanel(ptr); return ForeignMemory.getFloat(ptr + OFF_PERCENT_X); }
    public static float getPercentY(long ptr) { checkPanel(ptr); return ForeignMemory.getFloat(ptr + OFF_PERCENT_Y); }

    /** Percent of parent width/height for placement. < 0 (PERCENT_UNSET) disables and falls back to anchor. */
    public static void setPercentX(long ptr, float pct) { checkPanel(ptr); ForeignMemory.setFloat(ptr + OFF_PERCENT_X, pct); markDirty(ptr); }
    public static void setPercentY(long ptr, float pct) { checkPanel(ptr); ForeignMemory.setFloat(ptr + OFF_PERCENT_Y, pct); markDirty(ptr); }

    public static boolean hasPercentX(long ptr) { return getPercentX(ptr) >= 0f; }
    public static boolean hasPercentY(long ptr) { return getPercentY(ptr) >= 0f; }

    // =========================================================================
    // Z-ORDER
    // =========================================================================

    public static int getZ(long ptr) { checkPanel(ptr); return ForeignMemory.getInt(ptr + OFF_Z); }
    public static void setZ(long ptr, int z) { checkPanel(ptr); ForeignMemory.setInt(ptr + OFF_Z, z); markDirty(ptr); }

    // =========================================================================
    // VISIBILITY / ENABLED / DIRTY
    // =========================================================================

    public static boolean isVisible(long ptr) { checkPanel(ptr); return ForeignMemory.getByte(ptr + OFF_VISIBLE) != 0; }
    public static boolean isEnabled(long ptr) { checkPanel(ptr); return ForeignMemory.getByte(ptr + OFF_ENABLED) != 0; }
    public static boolean isDirty(long ptr)    { checkPanel(ptr); return ForeignMemory.getByte(ptr + OFF_DIRTY) != 0; }

    public static void setVisible(long ptr, boolean visible) { checkPanel(ptr); ForeignMemory.setByte(ptr + OFF_VISIBLE, (byte) (visible ? 1 : 0)); markDirty(ptr); }
    public static void setEnabled(long ptr, boolean enabled) { checkPanel(ptr); ForeignMemory.setByte(ptr + OFF_ENABLED, (byte) (enabled ? 1 : 0)); markDirty(ptr); }

    /**
     * Marks the panel dirty. In Phase 1 this walks the parent-ref set and fans
     * out to every parent holding a view of this panel's shared slots; for now
     * it sets the flag that the invalidation engine polls.
     */
    @Volatile
    public static void markDirty(long ptr) {
        checkPanel(ptr);
        ForeignMemory.setVolatileByte(ptr + OFF_DIRTY, (byte) 1);
    }

    public static void clearDirty(long ptr) {
        checkPanel(ptr);
        ForeignMemory.setVolatileByte(ptr + OFF_DIRTY, (byte) 0);
    }

    // =========================================================================
    // PARENT-REF SET (shared-slot fan-out, Phase 1)
    // =========================================================================

    public static long getParentRefSet(long ptr) { checkPanel(ptr); return ForeignMemory.getLong(ptr + OFF_PARENT_REF_SET); }
    public static void setParentRefSet(long ptr, long parentRefSetPtr) { checkPanel(ptr); ForeignMemory.setLong(ptr + OFF_PARENT_REF_SET, parentRefSetPtr); }

    // =========================================================================
    // LAYOUT RESOLUTION
    // =========================================================================

    /**
     * Resolves the panel's screen rect within a parent content box.
     *
     * Writes 4 floats into outRect (primitive.Float array of length >= 4):
     * [screenX, screenY, screenW, screenH].
     *
     * Math:
     *   refPoint (on parent)  = referenceAnchor point + x/y layout offsets
     *     - anchor row/col -> (col * parentW)/2, (row * parentH)/2
     *     - percentX/Y override the anchor when >= 0
     *   element offset       = elementAnchor point of the SCALED panel
     *   screenTopLeft        = refPoint + offsets - elementOffset
     *   scale applied last, about the element anchor.
     */
    public static void resolve(long ptr, float parentX, float parentY, float parentW, float parentH, long outRect) {
        checkPanel(ptr);
        if (outRect == 0L) throw new NullPointerException("resolve() outRect is NULL!");

        float w = getW(ptr), h = getH(ptr);
        float sx = getScaleX(ptr), sy = getScaleY(ptr);
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

        // element anchor point of the SCALED panel; scale pivots here
        float elemX = (elemCol * sw) / 2f;
        float elemY = (elemRow * sh) / 2f;

        float screenX = refX + getX(ptr) - elemX;
        float screenY = refY + getY(ptr) - elemY;

        ForeignMemory.setFloat(outRect, screenX);
        ForeignMemory.setFloat(outRect + 4L, screenY);
        ForeignMemory.setFloat(outRect + 8L, sw);
        ForeignMemory.setFloat(outRect + 12L, sh);
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
