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

    // --- 1. Parent Anchor constants (forwarded from Container) ---
    public static final int PARENT_ANCHOR_TOP_LEFT      = Container.PARENT_ANCHOR_TOP_LEFT;
    public static final int PARENT_ANCHOR_TOP_CENTER    = Container.PARENT_ANCHOR_TOP_CENTER;
    public static final int PARENT_ANCHOR_TOP_RIGHT     = Container.PARENT_ANCHOR_TOP_RIGHT;
    public static final int PARENT_ANCHOR_MIDDLE_LEFT   = Container.PARENT_ANCHOR_MIDDLE_LEFT;
    public static final int PARENT_ANCHOR_MIDDLE_CENTER = Container.PARENT_ANCHOR_MIDDLE_CENTER;
    public static final int PARENT_ANCHOR_MIDDLE_RIGHT  = Container.PARENT_ANCHOR_MIDDLE_RIGHT;
    public static final int PARENT_ANCHOR_BOTTOM_LEFT   = Container.PARENT_ANCHOR_BOTTOM_LEFT;
    public static final int PARENT_ANCHOR_BOTTOM_CENTER = Container.PARENT_ANCHOR_BOTTOM_CENTER;
    public static final int PARENT_ANCHOR_BOTTOM_RIGHT  = Container.PARENT_ANCHOR_BOTTOM_RIGHT;

    public static final int PARENT_ANCHOR_MIN = Container.PARENT_ANCHOR_MIN;
    public static final int PARENT_ANCHOR_MAX = Container.PARENT_ANCHOR_MAX;

    // --- 2. Self Anchor constants (forwarded from Container) ---
    public static final int SELF_ANCHOR_TOP_LEFT     = Container.SELF_ANCHOR_TOP_LEFT;
    public static final int SELF_ANCHOR_TOP_RIGHT    = Container.SELF_ANCHOR_TOP_RIGHT;
    public static final int SELF_ANCHOR_BOTTOM_LEFT  = Container.SELF_ANCHOR_BOTTOM_LEFT;
    public static final int SELF_ANCHOR_BOTTOM_RIGHT = Container.SELF_ANCHOR_BOTTOM_RIGHT;

    public static final int SELF_ANCHOR_MIN = Container.SELF_ANCHOR_MIN;
    public static final int SELF_ANCHOR_MAX = Container.SELF_ANCHOR_MAX;

    // --- 3. Pivot Reference constants (forwarded from Container) ---
    public static final int PIVOT_REFERENCE_TOP_LEFT      = Container.PIVOT_REFERENCE_TOP_LEFT;
    public static final int PIVOT_REFERENCE_TOP_RIGHT     = Container.PIVOT_REFERENCE_TOP_RIGHT;
    public static final int PIVOT_REFERENCE_BOTTOM_LEFT   = Container.PIVOT_REFERENCE_BOTTOM_LEFT;
    public static final int PIVOT_REFERENCE_BOTTOM_RIGHT  = Container.PIVOT_REFERENCE_BOTTOM_RIGHT;
    public static final int PIVOT_REFERENCE_CENTER        = Container.PIVOT_REFERENCE_CENTER;

    public static final int PIVOT_REFERENCE_MIN = Container.PIVOT_REFERENCE_MIN;
    public static final int PIVOT_REFERENCE_MAX = Container.PIVOT_REFERENCE_MAX;

    // Percent sentinel: < 0 means "not set, use anchor".
    public static final float PERCENT_UNSET = Container.PERCENT_UNSET;

    // --- Color format: 0xAARRGGBB (alpha in high byte) ---
    public static final int COLOR_WHITE = 0xFFFFFFFF;
    public static final int COLOR_BLACK = 0xFF000000;
    public static final int COLOR_CLEAR = 0x00000000;

    // --- Panel fields: Container layout prefix (0..55) then panel payload ---
    private static final int OFF_COLOR        = (int) Container.USER_STRIDE;      // 56  int (0xAARRGGBB)
    private static final int OFF_FILTERS      = 64; // long (filters array ptr, @Draft)
    private static final int OFF_PARENT_REF_SET = 72; // long (struct.Set of VIEW copies of this node's shared payloads)
    private static final int OFF_PARENT       = 80; // long (direct parent panel ptr, 0 = none)
    private static final int OFF_CHILDREN     = 88; // long (primitive.Long array of child panel ptrs)
    private static final int OFF_CHILD_COUNT  = 96; // int
    private static final int OFF_IMAGE        = 104; // long (off-heap image payload ptr — the picture, @Draft)
    private static final int OFF_SOURCE       = 112; // long (canonical panel this copy is a VIEW of; 0 = canonical/own)

    private static final int DEFAULT_CHILDREN_CAPACITY = 4;

    static final long USER_STRIDE = 120L; // bytes of user payload
    private static final long SLOT_SIZE   = 128L; // 8B header + 120B payload

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

    static void initDefaults(long ptr) {
        Container.initDefaults(ptr);
        setBackgroundColor(ptr, COLOR_CLEAR);
        setFilters(ptr, 0L);
        setParentRefSet(ptr, 0L);
        setParent(ptr, 0L);
        setChildCount(ptr, 0);
        setChildren(ptr, 0L);
        setImage(ptr, 0L);
        setSource(ptr, 0L);
    }

    public static void free(long ptr) {
        checkActive();
        if (ptr == 0L) return;

        int type = type(ptr);
        if (type != TYPE_SINGLETON) throw new IllegalStateException("Double free or corrupt Panel pointer: 0x" + java.lang.Long.toHexString(ptr).toUpperCase());

        // If this panel is a deep-copy VIEW, detach it from its source's
        // parent-ref set so the source's shared data can outlive the view.
        long source = getSource(ptr);
        if (source != 0L && getParent(ptr) != 0L) {
            removeRefSetEntry(source, getParent(ptr));
        }

        // Deleting a copy detaches only that parent's tree: unlink from the
        // structural parent's children list (no-op for roots).
        long parentPtr = getParent(ptr);
        if (parentPtr != 0L) removeChild(parentPtr, ptr);

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

        // Release this panel's own parent-ref set if it has one (it was a source).
        long refSet = ForeignMemory.getLong(ptr + OFF_PARENT_REF_SET);
        if (refSet != 0L) {
            struct.Set.free(refSet);
            ForeignMemory.setLong(ptr + OFF_PARENT_REF_SET, 0L);
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
        if (!TypeRegister.isA(classId(ptr), CLASS_ID))
            throw new IllegalArgumentException("Pointer 0x" + java.lang.Long.toHexString(ptr).toUpperCase() + " is Class ID " + classId(ptr) + ", expected Panel (or subclass)");
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
    public static void setLocation(long ptr, float x, float y) { Container.setLocation(ptr, x, y); }
    public static void setSize(long ptr, float width, float height) { Container.setSize(ptr, width, height); }

    public static float getScaleWidth(long ptr) { return Container.getScaleWidth(ptr); }
    public static float getScaleHeight(long ptr) { return Container.getScaleHeight(ptr); }
    public static void setScaleWidth(long ptr, float scaleWidth) { Container.setScaleWidth(ptr, scaleWidth); }
    public static void setScaleHeight(long ptr, float scaleHeight) { Container.setScaleHeight(ptr, scaleHeight); }
    public static void setScale(long ptr, float scaleWidth, float scaleHeight) { Container.setScale(ptr, scaleWidth, scaleHeight); }

    // =========================================================================
    // 1. PARENT ANCHOR (Where on the parent the element is pinned)
    // =========================================================================
    public static int getParentAnchor(long ptr) { return Container.getParentAnchor(ptr); }
    public static void setParentAnchor(long ptr, int parentAnchor) { Container.setParentAnchor(ptr, parentAnchor); }

    // =========================================================================
    // 2. SELF ANCHOR (Which point of the panel sits at the parent reference point)
    // =========================================================================
    public static int getSelfAnchor(long ptr) { return Container.getSelfAnchor(ptr); }
    public static void setSelfAnchor(long ptr, int selfAnchor) { Container.setSelfAnchor(ptr, selfAnchor); }

    // =========================================================================
    // 3. PIVOT REFERENCE (The element's source-of-truth pivot point)
    // =========================================================================
    public static int getPivotReference(long ptr) { return Container.getPivotReference(ptr); }
    public static void setPivotReference(long ptr, int pivotReference) { Container.setPivotReference(ptr, pivotReference); }

    // Combined convenience setters:
    public static void setParentAnchor(long ptr, int parentAnchor, int selfAnchor) { Container.setParentAnchor(ptr, parentAnchor, selfAnchor); }
    public static void setParentAnchor(long ptr, int parentAnchor, int selfAnchor, int pivotReference) { Container.setParentAnchor(ptr, parentAnchor, selfAnchor, pivotReference); }

    public static void setCenter(long ptr) { Container.setCenter(ptr); }

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
    public static boolean isClipChildren(long ptr) { return Container.isClipChildren(ptr); }
    public static void setVisible(long ptr, boolean visible) { Container.setVisible(ptr, visible); }
    public static void setEnabled(long ptr, boolean enabled) { Container.setEnabled(ptr, enabled); }
    public static void setClipChildren(long ptr, boolean clip) { Container.setClipChildren(ptr, clip); }

    @Volatile
    static void markDirty(long ptr) { Container.markDirty(ptr); }

    static void clearDirty(long ptr) { Container.clearDirty(ptr); }

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
    public static void setBackgroundColor(long ptr, int r, int g, int b, int a) {
        setBackgroundColor(ptr, ((a & 0xFF) << 24) | ((r & 0xFF) << 16) | ((g & 0xFF) << 8) | (b & 0xFF));
    }

    // =========================================================================
    // FILTERS (render graph stage inputs — placeholder)
    // =========================================================================

    /**
     * Filters attached to this panel, as an off-heap array of filter headers
     * (see Phase 2/4 render graph). Read-through: a deep-copy VIEW proxies its
     * canonical source's slot. @Draft — the element layout and allocator are
     * not decided yet; stored so the panel struct stays stable.
     */
    @Draft
    public static long getFilters(long ptr) {
        checkPanel(ptr);
        long src = getSource(ptr);
        return (src != 0L) ? getFilters(src) : ForeignMemory.getLong(ptr + OFF_FILTERS);
    }

    @Draft
    public static void setFilters(long ptr, long filtersPtr) {
        checkPanel(ptr);
        long src = getSource(ptr);
        if (src != 0L) { markPayloadDirty(src); ForeignMemory.setLong(src + OFF_FILTERS, filtersPtr); return; }
        ForeignMemory.setLong(ptr + OFF_FILTERS, filtersPtr);
        markPayloadDirty(ptr);
    }

    // =========================================================================
    // IMAGE (off-heap picture payload — the texturing surface)
    // =========================================================================

    /**
     * The off-heap image payload this panel displays (the picture). A pointer
     * to shared off-heap data (e.g. a decoded pixel buffer or VKImage header).
     * Read-through: a deep-copy VIEW (source != 0) proxies its canonical
     * source's slot, so every view always sees the latest shared data.
     */
    @Draft
    public static long getImage(long ptr) {
        checkPanel(ptr);
        long src = getSource(ptr);
        return (src != 0L) ? getImage(src) : ForeignMemory.getLong(ptr + OFF_IMAGE);
    }

    /**
     * Write-through: setting a payload on a VIEW writes the canonical source
     * instead (the data is shared by pointer), then fans out through the
     * source's parent-ref set so every parent holding a copy re-renders.
     */
    @Draft
    public static void setImage(long ptr, long imagePtr) {
        checkPanel(ptr);
        long src = getSource(ptr);
        if (src != 0L) { markPayloadDirty(src); ForeignMemory.setLong(src + OFF_IMAGE, imagePtr); return; }
        ForeignMemory.setLong(ptr + OFF_IMAGE, imagePtr);
        markPayloadDirty(ptr);
    }

    // =========================================================================
    // COPY / SOURCE (deep-copy composition model — ROADMAP decision log)
    // =========================================================================

    /** The canonical panel whose shared data this copy is a VIEW of. 0 = canonical / owns its data. */
    public static long getSource(long ptr) { checkPanel(ptr); return ForeignMemory.getLong(ptr + OFF_SOURCE); }

    private static void setSource(long ptr, long sourcePtr) { checkPanel(ptr); ForeignMemory.setLong(ptr + OFF_SOURCE, sourcePtr); }

    /** Number of parents currently holding a view/copy of this panel's shared payloads. */
    public static int refCount(long ptr) {
        checkPanel(ptr);
        long setPtr = getParentRefSet(ptr);
        return (setPtr == 0L) ? 0 : struct.Set.size(setPtr);
    }

    /**
     * Node composition contract (private goals/ROADMAP.md decision log):
     * deep-copies the STRUCTURE of {@code node} under {@code parent} — each
     * copy's rect/anchors/children are its own — but aliases the shared data
     * payloads (filters, image) BY POINTER, so every parent holding a copy is
     * a VIEW onto the same off-heap data. Editing a payload on the source fans
     * out through the ref-set (see {@link #markPayloadDirty}). Returns the new
     * copy handle. Allocates and marks dirty, 0 GC.
     */
    public static long add(long parent, long node) {
        checkPanel(parent);
        checkPanel(node);
        if (parent == node) throw new IllegalArgumentException("add: parent == node!");

        long copy = allocate();

        // --- structural deep copy: layout is its own ---
        Container.setX(copy, Container.getX(node));
        Container.setY(copy, Container.getY(node));
        Container.setWidth(copy, Container.getWidth(node));
        Container.setHeight(copy, Container.getHeight(node));
        Container.setScaleWidth(copy, Container.getScaleWidth(node));
        Container.setScaleHeight(copy, Container.getScaleHeight(node));
        Container.setParentAnchor(copy, Container.getParentAnchor(node));
        Container.setSelfAnchor(copy, Container.getSelfAnchor(node));
        Container.setPivotReference(copy, Container.getPivotReference(node));
        if (Container.hasPercentX(node)) Container.setPercentX(copy, Container.getPercentX(node));
        if (Container.hasPercentY(node)) Container.setPercentY(copy, Container.getPercentY(node));
        Container.setZ(copy, Container.getZ(node));
        if (!Container.isVisible(node)) Container.setVisible(copy, false);
        if (!Container.isEnabled(node)) Container.setEnabled(copy, false);
        if (Container.isClipChildren(node)) Container.setClipChildren(copy, true);
        setBackgroundColor(copy, getBackgroundColor(node));

        // --- payload note: shared slots (filters/image) are accessed WRITE- and
        // READ-THROUGH this copy's canonical source (getSource), so no pointer
        // snapshot is stored per-copy — every view sees the latest shared data. ---
        setSource(copy, node);

        // --- deep-copy children ---
        int n = childCount(node);
        for (int i = 0; i < n; i++) {
            add(copy, getChild(node, i));
        }

        // --- attach + register the view ---
        addChild(parent, copy);
        addRefSetEntry(node, parent);
        return copy;
    }

    /** Lazily creates the parent-ref set and adds a parent holding a view of this panel. */
    private static void addRefSetEntry(long ptr, long parentPtr) {
        checkPanel(ptr);
        long setPtr = getParentRefSet(ptr);
        if (setPtr == 0L) {
            setPtr = struct.Set.instant(TypeRegister.ID_LONG, 4);
            setParentRefSet(ptr, setPtr);
        }
        struct.Set.add(setPtr, parentPtr);
    }

    /** Removes a parent from the ref-set; frees the set when it empties. */
    private static void removeRefSetEntry(long ptr, long parentPtr) {
        checkPanel(ptr);
        long setPtr = getParentRefSet(ptr);
        if (setPtr == 0L) return;
        struct.Set.remove(setPtr, parentPtr);
        if (struct.Set.size(setPtr) == 0) {
            struct.Set.free(setPtr);
            setParentRefSet(ptr, 0L);
        }
    }

    /**
     * Dirt marking for SHARED payloads (filters, image, future data slots):
     * marks the source AND every parent holding a copy, so each parent's
     * damage rect is recomputed. Structural edits stay on-slot via
     * Container.markDirty and never fan out.
     */
    private static void markPayloadDirty(long ptr) {
        Container.markDirty(ptr);
        long setPtr = getParentRefSet(ptr);
        if (setPtr == 0L) return;
        long listPtr = struct.Set.toSortedList(setPtr);
        try {
            int n = struct.List.size(listPtr);
            for (int i = 0; i < n; i++) {
                Container.markDirty(struct.List.get(listPtr, i));
            }
        } finally {
            struct.List.free(listPtr);
        }
    }

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