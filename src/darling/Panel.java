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
 * Off-heap UI panel. Structural subclass of {@link Container}: the layout core
 * (position, size, scale, anchors, percents, z, visible/enabled/dirty) lives in
 * Container and this panel's payload begins exactly at Container.USER_STRIDE,
 * so any Container accessor can be called directly with a Panel pointer and the
 * prefix bytes line up. See TypeRegister.getParentClass(PANEL) == CONTAINER.
 *
 * Panel adds what Container does not own: background color, the render-graph
 * filters slot (@Draft), the shared-slot parent-ref set, and the parent/child
 * tree. Every setter marks the node dirty so the invalidation engine can walk
 * the parent-ref set and re-render only what changed.
 */
@Draft
@Intention("Retained-mode off-heap UI panel, extending the Container layout base with background color, filters and the parent/child tree.")
public final class Panel {

    @Required
    public static final int CLASS_ID = TypeRegister.ID_PANEL;

    public static final int TYPE_SINGLETON = TypeRegister.PANEL_SINGLETON; // 0x10000078
    public static final int TYPE_ARRAY     = TypeRegister.PANEL_ARRAY;     // 0x20000078
    public static final int TYPE_POINTER   = TypeRegister.PANEL_POINTER;   // 0x30000078

    // --- Anchor constants (forwarded from Container) ---
    public static final int ANCHOR_TOP_LEFT      = Container.ANCHOR_TOP_LEFT;
    public static final int ANCHOR_TOP_CENTER    = Container.ANCHOR_TOP_CENTER;
    public static final int ANCHOR_TOP_RIGHT     = Container.ANCHOR_TOP_RIGHT;
    public static final int ANCHOR_MIDDLE_LEFT   = Container.ANCHOR_MIDDLE_LEFT;
    public static final int ANCHOR_MIDDLE_CENTER = Container.ANCHOR_MIDDLE_CENTER;
    public static final int ANCHOR_MIDDLE_RIGHT  = Container.ANCHOR_MIDDLE_RIGHT;
    public static final int ANCHOR_BOTTOM_LEFT   = Container.ANCHOR_BOTTOM_LEFT;
    public static final int ANCHOR_BOTTOM_CENTER = Container.ANCHOR_BOTTOM_CENTER;
    public static final int ANCHOR_BOTTOM_RIGHT  = Container.ANCHOR_BOTTOM_RIGHT;

    public static final int ANCHOR_MIN = Container.ANCHOR_MIN;
    public static final int ANCHOR_MAX = Container.ANCHOR_MAX;

    // Percent sentinel: < 0 means "not set, use anchor".
    public static final float PERCENT_UNSET = Container.PERCENT_UNSET;

    // --- Color format: 0xRRGGBBAA (alpha in low byte) ---
    public static final int COLOR_WHITE = 0xFFFFFFFF;
    public static final int COLOR_BLACK = 0xFF000000;
    public static final int COLOR_CLEAR = 0x00000000;

    // --- Panel fields: Container layout prefix (0..47) then panel payload ---
    private static final int OFF_COLOR        = (int) Container.USER_STRIDE;      // 48  int (0xRRGGBBAA)
    private static final int OFF_FILTERS      = 56; // long (filters array ptr, @Draft)
    private static final int OFF_PARENT_REF_SET = 64; // long (shared-slot parent-ref set ptr)
    private static final int OFF_PARENT       = 72; // long (direct parent panel ptr, 0 = none)
    private static final int OFF_CHILDREN     = 80; // long (primitive.Long array of child panel ptrs)
    private static final int OFF_CHILD_COUNT  = 88; // int

    private static final int DEFAULT_CHILDREN_CAPACITY = 4;

    private static final long USER_STRIDE = 96L;  // bytes of user payload
    private static final long SLOT_SIZE   = 104L; // 8B header + 96B payload

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
        Container.initDefaults(ptr);
        setBackgroundColor(ptr, COLOR_CLEAR);
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

    // =========================================================================
    // LAYOUT (delegated to Container)
    // =========================================================================

    public static float getX(long ptr) { return Container.getX(ptr); }
    public static float getY(long ptr) { return Container.getY(ptr); }
    public static float getWidth(long ptr) { return Container.getWidth(ptr); }
    public static float getHeight(long ptr) { return Container.getHeight(ptr); }

    public static void setX(long ptr, float x) { Container.setX(ptr, x); }
    public static void setY(long ptr, float y) { Container.setY(ptr, y); }
    public static void setWidth(long ptr, float width) { Container.setWidth(ptr, width); }
    public static void setHeight(long ptr, float height) { Container.setHeight(ptr, height); }

    public static void setPos(long ptr, float x, float y) { Container.setPos(ptr, x, y); }
    public static void setSize(long ptr, float width, float height) { Container.setSize(ptr, width, height); }

    public static float getScaleWidth(long ptr) { return Container.getScaleWidth(ptr); }
    public static float getScaleHeight(long ptr) { return Container.getScaleHeight(ptr); }
    public static void setScaleWidth(long ptr, float scaleWidth) { Container.setScaleWidth(ptr, scaleWidth); }
    public static void setScaleHeight(long ptr, float scaleHeight) { Container.setScaleHeight(ptr, scaleHeight); }
    public static void setScale(long ptr, float scaleWidth, float scaleHeight) { Container.setScale(ptr, scaleWidth, scaleHeight); }

    public static int getReferenceAnchor(long ptr) { return Container.getReferenceAnchor(ptr); }
    public static int getElementAnchor(long ptr)   { return Container.getElementAnchor(ptr); }
    public static void setReferenceAnchor(long ptr, int anchor) { Container.setReferenceAnchor(ptr, anchor); }
    public static void setElementAnchor(long ptr, int anchor) { Container.setElementAnchor(ptr, anchor); }
    public static void setAnchor(long ptr, int referenceAnchor, int elementAnchor) { Container.setAnchor(ptr, referenceAnchor, elementAnchor); }

    public static float getPercentX(long ptr) { return Container.getPercentX(ptr); }
    public static float getPercentY(long ptr) { return Container.getPercentY(ptr); }
    public static void setPercentX(long ptr, float pct) { Container.setPercentX(ptr, pct); }
    public static void setPercentY(long ptr, float pct) { Container.setPercentY(ptr, pct); }
    public static boolean hasPercentX(long ptr) { return Container.hasPercentX(ptr); }
    public static boolean hasPercentY(long ptr) { return Container.hasPercentY(ptr); }

    public static int getZ(long ptr) { return Container.getZ(ptr); }
    public static void setZ(long ptr, int z) { Container.setZ(ptr, z); }

    public static boolean isVisible(long ptr) { return Container.isVisible(ptr); }
    public static boolean isEnabled(long ptr) { return Container.isEnabled(ptr); }
    public static boolean isDirty(long ptr)    { return Container.isDirty(ptr); }
    public static void setVisible(long ptr, boolean visible) { Container.setVisible(ptr, visible); }
    public static void setEnabled(long ptr, boolean enabled) { Container.setEnabled(ptr, enabled); }

    @Volatile
    public static void markDirty(long ptr) { Container.markDirty(ptr); }

    public static void clearDirty(long ptr) { Container.clearDirty(ptr); }

    public static void resolve(long ptr, float parentX, float parentY, float parentW, float parentH, long outRect) {
        Container.resolve(ptr, parentX, parentY, parentW, parentH, outRect);
    }

    public static boolean hitTest(long ptr, float parentX, float parentY, float parentW, float parentH, float px, float py) {
        return Container.hitTest(ptr, parentX, parentY, parentW, parentH, px, py);
    }

    // =========================================================================
    // BACKGROUND COLOR
    // =========================================================================

    public static int getBackgroundColor(long ptr) { checkPanel(ptr); return ForeignMemory.getInt(ptr + OFF_COLOR); }
    public static void setBackgroundColor(long ptr, int color) { checkPanel(ptr); ForeignMemory.setInt(ptr + OFF_COLOR, color); markDirty(ptr); }

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
}