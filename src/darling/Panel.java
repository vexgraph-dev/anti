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

    // --- Color format: 0xRRGGBBAA (alpha in low byte) ---
    public static final int COLOR_WHITE = 0xFFFFFFFF;
    public static final int COLOR_BLACK = 0xFF000000;
    public static final int COLOR_CLEAR = 0x00000000;

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
    private static final int OFF_COLOR      = 44;  // int (0xRRGGBBAA)
    private static final int OFF_VISIBLE    = 48;  // byte
    private static final int OFF_ENABLED    = 49;  // byte
    private static final int OFF_DIRTY      = 50;  // byte
    private static final int OFF_CHILD_COUNT = 52; // int
    private static final int OFF_FILTERS     = 56; // long (filters array ptr, @Draft)
    private static final int OFF_PARENT_REF_SET = 64; // long (shared-slot parent-ref set ptr)
    private static final int OFF_PARENT      = 72; // long (direct parent panel ptr, 0 = none)
    private static final int OFF_CHILDREN    = 80; // long (primitive.Long array of child panel ptrs)

    private static final int DEFAULT_CHILDREN_CAPACITY = 4;

    private static final long USER_STRIDE = 88L;  // bytes of user payload
    private static final long SLOT_SIZE   = 96L;  // 8B header + 88B payload

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
        setWidth(ptr, 0f);
        setHeight(ptr, 0f);
        setScale(ptr, 1f, 1f);
        setReferenceAnchor(ptr, ANCHOR_TOP_LEFT);
        setElementAnchor(ptr, ANCHOR_TOP_LEFT);
        setPercentX(ptr, PERCENT_UNSET);
        setPercentY(ptr, PERCENT_UNSET);
        setZ(ptr, 0);
        setBackgroundColor(ptr, COLOR_CLEAR);
        setVisible(ptr, true);
        setEnabled(ptr, true);
        clearDirty(ptr);
        setFilters(ptr, 0L);
        setParentRefSet(ptr, 0L);
        setParent(ptr, 0L);
        setChildCount(ptr, 0);
        setChildren(ptr, 0L);
    }

    public static void free(long ptr) {
        checkActive();
        if (ptr == 0L) return;

        int type = type(ptr);
        if (type != TYPE_SINGLETON) throw new IllegalStateException("Double free or corrupt Panel pointer: 0x" + java.lang.Long.toHexString(ptr).toUpperCase());

        long base = ptr - 8L;
        ForeignMemory.setUnsafe(base, 0);
        ForeignMemory.setUnsafe(base + 4L, -1);

        // Release the children array (if this panel owns one) so child panel
        // pointers are not leaked. The children themselves are separate
        // allocations owned by whoever allocated them — we only free the list.
        long children = ForeignMemory.getLong(ptr + OFF_CHILDREN);
        if (children != 0L) {
            primitive.Long.free(children);
            ForeignMemory.setLong(ptr + OFF_CHILDREN, 0L);
        }

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
    public static float getWidth(long ptr) { checkPanel(ptr); return ForeignMemory.getFloat(ptr + OFF_W); }
    public static float getHeight(long ptr) { checkPanel(ptr); return ForeignMemory.getFloat(ptr + OFF_H); }

    public static void setX(long ptr, float x) { checkPanel(ptr); ForeignMemory.setFloat(ptr + OFF_X, x); markDirty(ptr); }
    public static void setY(long ptr, float y) { checkPanel(ptr); ForeignMemory.setFloat(ptr + OFF_Y, y); markDirty(ptr); }
    public static void setWidth(long ptr, float width) { checkPanel(ptr); ForeignMemory.setFloat(ptr + OFF_W, width); markDirty(ptr); }
    public static void setHeight(long ptr, float height) { checkPanel(ptr); ForeignMemory.setFloat(ptr + OFF_H, height); markDirty(ptr); }

    public static void setPos(long ptr, float x, float y) { setX(ptr, x); setY(ptr, y); }
    public static void setSize(long ptr, float width, float height) { setWidth(ptr, width); setHeight(ptr, height); }

    // =========================================================================
    // SCALE
    // =========================================================================

    public static float getScaleWidth(long ptr) { checkPanel(ptr); return ForeignMemory.getFloat(ptr + OFF_SCALE_X); }
    public static float getScaleHeight(long ptr) { checkPanel(ptr); return ForeignMemory.getFloat(ptr + OFF_SCALE_Y); }

    public static void setScaleWidth(long ptr, float scaleWidth) { checkPanel(ptr); ForeignMemory.setFloat(ptr + OFF_SCALE_X, scaleWidth); markDirty(ptr); }
    public static void setScaleHeight(long ptr, float scaleHeight) { checkPanel(ptr); ForeignMemory.setFloat(ptr + OFF_SCALE_Y, scaleHeight); markDirty(ptr); }

    /** Sets both scale factors, one dirty region. */
    public static void setScale(long ptr, float scaleWidth, float scaleHeight) { setScaleWidth(ptr, scaleWidth); setScaleHeight(ptr, scaleHeight); }

    // =========================================================================
    // BACKGROUND COLOR
    // =========================================================================

    public static int getBackgroundColor(long ptr) { checkPanel(ptr); return ForeignMemory.getInt(ptr + OFF_COLOR); }
    public static void setBackgroundColor(long ptr, int color) { checkPanel(ptr); ForeignMemory.setInt(ptr + OFF_COLOR, color); markDirty(ptr); }

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
    // FILTERS (render graph stage inputs — placeholder)
    // =========================================================================

    /**
     * Filters attached to this panel, as an off-heap array of filter headers
     * (see Phase 2/4 render graph). Placeholder @Draft slot: the type of array,
     * element layout and allocator are not decided yet — it is stored so the
     * panel struct is stable and existing callers keep working.
     */
    @Draft
    public static long getFilters(long ptr) { checkPanel(ptr); return ForeignMemory.getLong(ptr + OFF_FILTERS); }

    @Draft
    public static void setFilters(long ptr, long filtersPtr) { checkPanel(ptr); ForeignMemory.setLong(ptr + OFF_FILTERS, filtersPtr); markDirty(ptr); }

    // =========================================================================
    // PARENT-REF SET (shared-slot fan-out, Phase 1)
    // =========================================================================

    public static long getParentRefSet(long ptr) { checkPanel(ptr); return ForeignMemory.getLong(ptr + OFF_PARENT_REF_SET); }
    public static void setParentRefSet(long ptr, long parentRefSetPtr) { checkPanel(ptr); ForeignMemory.setLong(ptr + OFF_PARENT_REF_SET, parentRefSetPtr); }

    // =========================================================================
    // PARENT / CHILDREN
    // =========================================================================

    /**
     * The direct parent panel pointer. 0 means this panel is a root (has no
     * parent). Children are stored in a primitive.Long array owned by the
     * parent; the parent slot points back via getParent().
     */
    public static long getParent(long ptr) { checkPanel(ptr); return ForeignMemory.getLong(ptr + OFF_PARENT); }
    public static void setParent(long ptr, long parentPtr) { checkPanel(ptr); ForeignMemory.setLong(ptr + OFF_PARENT, parentPtr); markDirty(ptr); }

    /** Number of direct children. */
    public static int childCount(long ptr) { checkPanel(ptr); return ForeignMemory.getInt(ptr + OFF_CHILD_COUNT); }

    private static void setChildCount(long ptr, int count) { checkPanel(ptr); ForeignMemory.setInt(ptr + OFF_CHILD_COUNT, count); }

    private static long getChildren(long ptr) { checkPanel(ptr); return ForeignMemory.getLong(ptr + OFF_CHILDREN); }
    private static void setChildren(long ptr, long childrenPtr) { checkPanel(ptr); ForeignMemory.setLong(ptr + OFF_CHILDREN, childrenPtr); }

    /** Child panel pointer at index (0-based), or 0 if out of range. */
    public static long getChild(long ptr, int index) {
        checkPanel(ptr);
        long children = getChildren(ptr);
        if (children == 0L || index < 0 || index >= childCount(ptr)) return 0L;
        return primitive.Long.get(children, index);
    }

    /**
     * Attaches childPtr to this panel: sets the child's parent to this panel
     * and appends it to this panel's children array (growing it via
     * primitive.Long.expandArray when full). If childPtr already has a parent,
     * it is detached from that parent first so the tree stays consistent.
     */
    public static void addChild(long ptr, long childPtr) {
        checkPanel(ptr);
        if (childPtr == 0L) throw new NullPointerException("addChild: child is NULL!");
        checkPanel(childPtr);

        if (getParent(childPtr) != 0L) {
            removeChild(getParent(childPtr), childPtr);
        }
        setParent(childPtr, ptr);

        int count = childCount(ptr);
        long children = getChildren(ptr);
        if (children == 0L) {
            children = primitive.Long.allocateArray(DEFAULT_CHILDREN_CAPACITY);
            setChildren(ptr, children);
        } else if (count >= primitive.Long.length(children)) {
            children = primitive.Long.expandArray(children, count + DEFAULT_CHILDREN_CAPACITY);
            setChildren(ptr, children);
        }
        primitive.Long.set(children, count, childPtr);
        setChildCount(ptr, count + 1);
        markDirty(ptr);
        markDirty(childPtr);
    }

    /** Detaches childPtr from this panel. Returns true if it was attached. */
    public static boolean removeChild(long ptr, long childPtr) {
        checkPanel(ptr);
        if (childPtr == 0L) return false;

        int count = childCount(ptr);
        long children = getChildren(ptr);
        if (children == 0L || count <= 0) return false;

        int found = -1;
        for (int i = 0; i < count; i++) {
            if (primitive.Long.get(children, i) == childPtr) {
                found = i;
                break;
            }
        }
        if (found < 0) return false;

        for (int i = found; i < count - 1; i++) {
            primitive.Long.set(children, i, primitive.Long.get(children, i + 1));
        }
        setChildCount(ptr, count - 1);
        if (getParent(childPtr) == ptr) setParent(childPtr, 0L);
        markDirty(ptr);
        markDirty(childPtr);
        return true;
    }

    /** True if childPtr is a direct child of this panel. */
    public static boolean containsChild(long ptr, long childPtr) {
        checkPanel(ptr);
        int count = childCount(ptr);
        long children = getChildren(ptr);
        if (children == 0L) return false;
        for (int i = 0; i < count; i++) {
            if (primitive.Long.get(children, i) == childPtr) return true;
        }
        return false;
    }

    /** True if this panel has a parent (not a root). */
    public static boolean hasParent(long ptr) { return getParent(ptr) != 0L; }

    /** True if this panel has at least one child. */
    public static boolean hasChildren(long ptr) { return childCount(ptr) > 0; }

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