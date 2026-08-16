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

    // --- 1. Parent Anchor constants (3x3 grid, row*3+col): where on the PARENT the
    //        element is pinned. The element tracks that side/corner live on resize. ---
    public static final int PARENT_ANCHOR_TOP_LEFT      = 0;
    public static final int PARENT_ANCHOR_TOP_CENTER    = 1;
    public static final int PARENT_ANCHOR_TOP_RIGHT     = 2;
    public static final int PARENT_ANCHOR_MIDDLE_LEFT   = 3;
    public static final int PARENT_ANCHOR_MIDDLE_CENTER = 4;
    public static final int PARENT_ANCHOR_MIDDLE_RIGHT  = 5;
    public static final int PARENT_ANCHOR_BOTTOM_LEFT   = 6;
    public static final int PARENT_ANCHOR_BOTTOM_CENTER = 7;
    public static final int PARENT_ANCHOR_BOTTOM_RIGHT  = 8;

    public static final int PARENT_ANCHOR_MIN = PARENT_ANCHOR_TOP_LEFT;
    public static final int PARENT_ANCHOR_MAX = PARENT_ANCHOR_BOTTOM_RIGHT;

    // --- 2. Self Anchor constants: which point OF THE PANEL sits at the parent reference point. ---
    public static final int SELF_ANCHOR_TOP_LEFT     = 0;
    public static final int SELF_ANCHOR_TOP_RIGHT    = 1;
    public static final int SELF_ANCHOR_BOTTOM_LEFT  = 2;
    public static final int SELF_ANCHOR_BOTTOM_RIGHT = 3;

    public static final int SELF_ANCHOR_MIN = SELF_ANCHOR_TOP_LEFT;
    public static final int SELF_ANCHOR_MAX = SELF_ANCHOR_BOTTOM_RIGHT;

    // --- 3. Pivot Reference constants: the element's source-of-truth pivot point used for
    //        placement (setLocation places this point) and rotation. ---
    public static final int PIVOT_REFERENCE_TOP_LEFT      = 0;
    public static final int PIVOT_REFERENCE_TOP_RIGHT     = 1;
    public static final int PIVOT_REFERENCE_BOTTOM_LEFT   = 2;
    public static final int PIVOT_REFERENCE_BOTTOM_RIGHT  = 3;
    public static final int PIVOT_REFERENCE_CENTER        = 4;

    public static final int PIVOT_REFERENCE_MIN = PIVOT_REFERENCE_TOP_LEFT;
    public static final int PIVOT_REFERENCE_MAX = PIVOT_REFERENCE_CENTER;

    // Percent sentinel: < 0 means "not set, use anchor".
    public static final float PERCENT_UNSET = -1.0f;

    // --- Field offsets (relative to userPtr). Subclass payloads start at USER_STRIDE. ---
    static final int OFF_X          = 0;   // float
    static final int OFF_Y          = 4;   // float
    static final int OFF_W          = 8;   // float
    static final int OFF_H          = 12;  // float
    static final int OFF_SCALE_X    = 16;  // float
    static final int OFF_SCALE_Y    = 20;  // float
    static final int OFF_REF_ANCHOR = 24;  // int (byte 0: parentAnchor, byte 1: selfAnchor)
    static final int OFF_PIVOT_REF  = 28;  // int (pivotReference)
    static final int OFF_PERCENT_X  = 32;  // float
    static final int OFF_PERCENT_Y  = 36;  // float
    static final int OFF_Z          = 40;  // int
    static final int OFF_VISIBLE    = 44;  // byte
    static final int OFF_ENABLED    = 45;  // byte
    static final int OFF_DIRTY      = 46;  // byte
    static final int OFF_CLIPPING   = 47;  // byte (1 = clip children to my bounds, 0 = overflow)
    static final int OFF_BASE_W     = 48;  // float (parent size at last layout; resize-delta reference)
    static final int OFF_BASE_H     = 52;  // float

    static final long USER_STRIDE = 56L; // bytes of user payload
    static final long SLOT_SIZE   = 64L; // 8B header + 56B payload

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
        setParentAnchor(ptr, PARENT_ANCHOR_TOP_LEFT);
        // selfAnchor deliberately LEFT UNSET (raw byte 0) so it defaults to TOP_LEFT;
        // setSelfAnchor() opts in. Only ever affects the initial position, never resize.
        setPivotReference(ptr, PIVOT_REFERENCE_TOP_LEFT);
        setPercentX(ptr, PERCENT_UNSET);
        setPercentY(ptr, PERCENT_UNSET);
        setZ(ptr, 0);
        setVisible(ptr, true);
        setEnabled(ptr, true);
        setClipChildren(ptr, false);
        setBaseWidth(ptr, 0f); // unset -> first resolve captures the layout reference size
        setBaseHeight(ptr, 0f);
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
        if (!TypeRegister.isA(cls, CLASS_ID))
            throw new IllegalArgumentException("Pointer 0x" + java.lang.Long.toHexString(ptr).toUpperCase() + " is Class ID " + cls + ", not a Container (or subclass)");
    }

    private static void checkParentAnchor(int anchor) {
        if (anchor < PARENT_ANCHOR_MIN || anchor > PARENT_ANCHOR_MAX) throw new IllegalArgumentException("Invalid parent anchor " + anchor + " (must be 0-8)");
    }

    private static void checkSelfAnchor(int anchor) {
        if (anchor < SELF_ANCHOR_MIN || anchor > SELF_ANCHOR_MAX) throw new IllegalArgumentException("Invalid self anchor " + anchor + " (must be 0-3)");
    }

    private static void checkPivotReference(int pivot) {
        if (pivot < PIVOT_REFERENCE_MIN || pivot > PIVOT_REFERENCE_MAX) throw new IllegalArgumentException("Invalid pivot reference " + pivot + " (must be 0-4)");
    }

    // =========================================================================
    // POSITION & SIZE
    // =========================================================================

    public static float getX(long ptr) { checkContainer(ptr); return ForeignMemory.getFloat(ptr + OFF_X); }
    public static float getY(long ptr) { checkContainer(ptr); return ForeignMemory.getFloat(ptr + OFF_Y); }
    public static float getWidth(long ptr) { checkContainer(ptr); return ForeignMemory.getFloat(ptr + OFF_W); }
    public static float getHeight(long ptr) { checkContainer(ptr); return ForeignMemory.getFloat(ptr + OFF_H); }

    public static void setX(long ptr, float x) { checkContainer(ptr); ForeignMemory.setFloat(ptr + OFF_X, x); markDirty(ptr); invalidateBase(ptr); }
    public static void setY(long ptr, float y) { checkContainer(ptr); ForeignMemory.setFloat(ptr + OFF_Y, y); markDirty(ptr); invalidateBase(ptr); }
    public static void setWidth(long ptr, float width) { checkContainer(ptr); ForeignMemory.setFloat(ptr + OFF_W, width); markDirty(ptr); invalidateBase(ptr); }
    public static void setHeight(long ptr, float height) { checkContainer(ptr); ForeignMemory.setFloat(ptr + OFF_H, height); markDirty(ptr); invalidateBase(ptr); }

    public static void setPos(long ptr, float x, float y) { setX(ptr, x); setY(ptr, y); }
    public static void setLocation(long ptr, float x, float y) { setX(ptr, x); setY(ptr, y); }
    public static void setSize(long ptr, float width, float height) { setWidth(ptr, width); setHeight(ptr, height); }

    // =========================================================================
    // SCALE
    // =========================================================================

    public static float getScaleWidth(long ptr) { checkContainer(ptr); return ForeignMemory.getFloat(ptr + OFF_SCALE_X); }
    public static float getScaleHeight(long ptr) { checkContainer(ptr); return ForeignMemory.getFloat(ptr + OFF_SCALE_Y); }

    public static void setScaleWidth(long ptr, float scaleWidth) { checkContainer(ptr); ForeignMemory.setFloat(ptr + OFF_SCALE_X, scaleWidth); markDirty(ptr); invalidateBase(ptr); }
    public static void setScaleHeight(long ptr, float scaleHeight) { checkContainer(ptr); ForeignMemory.setFloat(ptr + OFF_SCALE_Y, scaleHeight); markDirty(ptr); invalidateBase(ptr); }

    /** Sets both scale factors, one dirty region. */
    public static void setScale(long ptr, float scaleWidth, float scaleHeight) { setScaleWidth(ptr, scaleWidth); setScaleHeight(ptr, scaleHeight); }

    // =========================================================================
    // RESIZE-DELTA REFERENCE (the parent size at which the element was last laid out)
    // =========================================================================

    static float getBaseWidth(long ptr)  { checkContainer(ptr); return ForeignMemory.getFloat(ptr + OFF_BASE_W); }
    static float getBaseHeight(long ptr) { checkContainer(ptr); return ForeignMemory.getFloat(ptr + OFF_BASE_H); }

    private static void setBaseWidth(long ptr, float baseWidth)  { ForeignMemory.setFloat(ptr + OFF_BASE_W, baseWidth); }
    private static void setBaseHeight(long ptr, float baseHeight) { ForeignMemory.setFloat(ptr + OFF_BASE_H, baseHeight); }

    /**
     * Forces the next resolveSized to re-capture the resize-delta reference at the
     * current parent size. Called only by explicit layout modifications (location,
     * size, scale, anchors); state-only mutators (hover, color, visibility) keep the
     * existing base so anchored panels never jump mid-resize.
     */
    static void invalidateBase(long ptr) {
        checkContainer(ptr);
        setBaseWidth(ptr, 0f);
        setBaseHeight(ptr, 0f);
    }

    // =========================================================================
    // 1. PARENT ANCHOR (Where on the parent the element is pinned, 3x3 grid)
    // =========================================================================

    /**
     * Parent Anchor: which side/corner of the parent the element tracks DURING RESIZE
     * (0..8). Used only for resize-delta tracking: when the parent changes size, the
     * element moves by the movement of this anchor point since its last layout
     * (e.g. PARENT_ANCHOR_BOTTOM_RIGHT makes the element follow the bottom-right
     * corner's delta exactly). It has NO effect on the element's resting position.
     */
    public static int getParentAnchor(long ptr) {
        checkContainer(ptr);
        return ForeignMemory.getInt(ptr + OFF_REF_ANCHOR) & 0xFF;
    }

    public static void setParentAnchor(long ptr, int parentAnchor) {
        checkContainer(ptr);
        checkParentAnchor(parentAnchor);
        int old = ForeignMemory.getInt(ptr + OFF_REF_ANCHOR);
        int selfAnchor = (old >>> 8) & 0xFF;
        ForeignMemory.setInt(ptr + OFF_REF_ANCHOR, (selfAnchor << 8) | (parentAnchor & 0xFF));
        markDirty(ptr);
        invalidateBase(ptr);
    }

    // =========================================================================
    // 2. SELF ANCHOR (Which point of the panel sits at the parent reference point)
    // =========================================================================

    /**
     * Self Anchor: which corner of the parent the location is a margin from (0..3).
     * Determines ONLY the element's initial/resting position, margin-wise, always
     * keeping the element fully visible (e.g. SELF_ANCHOR_BOTTOM_RIGHT + (30,30) puts
     * the element 30px in from the parent's bottom-right corner). Never affects resize.
     */
    public static int getSelfAnchor(long ptr) {
        checkContainer(ptr);
        int raw = ForeignMemory.getInt(ptr + OFF_REF_ANCHOR);
        int selfAnchor = (raw >>> 8) & 0xFF;
        return (selfAnchor == 0) ? SELF_ANCHOR_TOP_LEFT : (selfAnchor - 1);
    }

    public static void setSelfAnchor(long ptr, int selfAnchor) {
        checkContainer(ptr);
        checkSelfAnchor(selfAnchor);
        int old = ForeignMemory.getInt(ptr + OFF_REF_ANCHOR);
        int parentAnchor = old & 0xFF;
        ForeignMemory.setInt(ptr + OFF_REF_ANCHOR, ((selfAnchor + 1) << 8) | parentAnchor);
        markDirty(ptr);
        invalidateBase(ptr);
    }

    // =========================================================================
    // 3. PIVOT REFERENCE (The element's source-of-truth pivot point)
    // =========================================================================

    /**
     * Pivot Reference: the element's source-of-truth pivot point (0..4, 4 corners
     * + center). setLocation places this point at the location; rotation pivots
     * around it. TOP_LEFT is the default (like a window whose source of truth is
     * its top-left corner).
     */
    public static int getPivotReference(long ptr) {
        checkContainer(ptr);
        return ForeignMemory.getInt(ptr + OFF_PIVOT_REF);
    }

    public static void setPivotReference(long ptr, int pivotReference) {
        checkContainer(ptr);
        checkPivotReference(pivotReference);
        ForeignMemory.setInt(ptr + OFF_PIVOT_REF, pivotReference);
        markDirty(ptr);
        invalidateBase(ptr);
    }

    // Combined convenience setters:
    public static void setParentAnchor(long ptr, int parentAnchor, int selfAnchor) {
        setParentAnchor(ptr, parentAnchor);
        setSelfAnchor(ptr, selfAnchor);
    }

    public static void setParentAnchor(long ptr, int parentAnchor, int selfAnchor, int pivotReference) {
        setParentAnchor(ptr, parentAnchor);
        setSelfAnchor(ptr, selfAnchor);
        setPivotReference(ptr, pivotReference);
    }

    /**
     * Centers the element on its parent regardless of the current pivot reference
     * or self anchor: resets the self anchor to TOP_LEFT, pivots on CENTER, and
     * places it at 50% / 50% of the parent.
     */
    public static void setCenter(long ptr) {
        checkContainer(ptr);
        setSelfAnchor(ptr, SELF_ANCHOR_TOP_LEFT);
        setPivotReference(ptr, PIVOT_REFERENCE_CENTER);
        setPercentX(ptr, 0.5f);
        setPercentY(ptr, 0.5f);
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
    public static boolean isDirty(long ptr)    { return ForeignMemory.getByte(ptr + OFF_DIRTY) != 0; }
    public static boolean isClipChildren(long ptr) { checkContainer(ptr); return ForeignMemory.getByte(ptr + OFF_CLIPPING) != 0; }

    public static void setVisible(long ptr, boolean visible) { checkContainer(ptr); ForeignMemory.setByte(ptr + OFF_VISIBLE, (byte) (visible ? 1 : 0)); markDirty(ptr); }
    public static void setEnabled(long ptr, boolean enabled) { checkContainer(ptr); ForeignMemory.setByte(ptr + OFF_ENABLED, (byte) (enabled ? 1 : 0)); markDirty(ptr); }
    public static void setClipChildren(long ptr, boolean clip) { checkContainer(ptr); ForeignMemory.setByte(ptr + OFF_CLIPPING, (byte) (clip ? 1 : 0)); markDirty(ptr); }

    /**
     * Marks the node dirty. This is an INTERNAL side-effect: every layout
     * mutator below fires it automatically. Callers never assign dirtiness —
     * the invalidation engine clears it after re-rendering the damaged region.
     * Shared-payload edits fan out through the parent-ref set (see Panel).
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
     * Math (Three Distinct Operations):
     *   1. Parent Anchor: reference point on the parent (origin + inward direction).
     *   2. Self Anchor: which corner of the panel sits at the reference point.
     *   3. Pivot Reference: source-of-truth pivot of the element (placement + rotation).
     */
    public static void resolve(long ptr, float parentX, float parentY, float parentW, float parentH, long outRect) {
        resolveSized(ptr, getWidth(ptr), getHeight(ptr), parentX, parentY, parentW, parentH, outRect);
    }

    /**
     * Like {@link #resolve} but with an explicit width/height, so subclasses can
     * auto-derive their size (e.g. a Picture whose -1 dims reflect its Image).
     */
    static void resolveSized(long ptr, float w, float h, float parentX, float parentY, float parentW, float parentH, long outRect) {
        checkContainer(ptr);
        if (outRect == 0L) throw new NullPointerException("resolve() outRect is NULL!");

        float sx = getScaleWidth(ptr), sy = getScaleHeight(ptr);
        float sw = w * sx, sh = h * sy;

        // ---------------------------------------------------------------------
        // 0. RESIZE-DELTA REFERENCE: the parent size at which the element was last
        //    laid out. Refreshed on first resolve and whenever the layout is dirty
        //    (location/size/anchors changed), so the parent-anchor delta below is
        //    always measured from a known resting state.
        // ---------------------------------------------------------------------
        float baseW = getBaseWidth(ptr);
        float baseH = getBaseHeight(ptr);
        if (baseW <= 0f || baseH <= 0f) {
            // Base captured once at first layout (or after an explicit layout
            // modification — see invalidateBase). Generic dirty marks (hover, color,
            // visibility) must NOT reset it: re-capturing mid-resize would collapse
            // dW/dH to zero and make anchored panels jump to the current parent size.
            baseW = parentW;
            baseH = parentH;
            setBaseWidth(ptr, baseW);
            setBaseHeight(ptr, baseH);
        }
        clearDirty(ptr);

        float x = getX(ptr), y = getY(ptr);

        // ---------------------------------------------------------------------
        // 1. SELF ANCHOR: INITIAL POSITION ONLY. Which corner of the parent the
        //    location is a margin from, measured against the base size. The element
        //    is always placed fully inside that corner (margin-wise).
        // ---------------------------------------------------------------------
        float selfX, selfY;
        switch (getSelfAnchor(ptr)) {
            case SELF_ANCHOR_TOP_RIGHT    -> { selfX = baseW - sw - x; selfY = y; }
            case SELF_ANCHOR_BOTTOM_LEFT  -> { selfX = x;              selfY = baseH - sh - y; }
            case SELF_ANCHOR_BOTTOM_RIGHT -> { selfX = baseW - sw - x; selfY = baseH - sh - y; }
            default                       -> { selfX = x;              selfY = y; } // SELF_ANCHOR_TOP_LEFT
        }

        // ---------------------------------------------------------------------
        // 2. PARENT ANCHOR: RESIZE TRACKING ONLY. How far the parent-anchor point
        //    has moved since the base layout; the element follows that delta exactly
        //    (e.g. BOTTOM_RIGHT moves it by the window's full bottom-right delta).
        // ---------------------------------------------------------------------
        float dW = parentW - baseW;
        float dH = parentH - baseH;
        float dx, dy;
        switch (getParentAnchor(ptr)) {
            case PARENT_ANCHOR_TOP_CENTER    -> { dx = dW / 2f; dy = 0f; }
            case PARENT_ANCHOR_TOP_RIGHT     -> { dx = dW;      dy = 0f; }
            case PARENT_ANCHOR_MIDDLE_LEFT   -> { dx = 0f;      dy = dH / 2f; }
            case PARENT_ANCHOR_MIDDLE_CENTER -> { dx = dW / 2f; dy = dH / 2f; }
            case PARENT_ANCHOR_MIDDLE_RIGHT  -> { dx = dW;      dy = dH / 2f; }
            case PARENT_ANCHOR_BOTTOM_LEFT   -> { dx = 0f;      dy = dH; }
            case PARENT_ANCHOR_BOTTOM_CENTER -> { dx = dW / 2f; dy = dH; }
            case PARENT_ANCHOR_BOTTOM_RIGHT  -> { dx = dW;      dy = dH; }
            default                          -> { dx = 0f;      dy = 0f; } // TOP_LEFT
        }

        float screenX = selfX + dx + parentX;
        float screenY = selfY + dy + parentY;

        // ---------------------------------------------------------------------
        // 3. PERCENT overrides placement (still resolves against the live parent size).
        // ---------------------------------------------------------------------
        if (hasPercentX(ptr)) screenX = parentX + getPercentX(ptr) * parentW;
        if (hasPercentY(ptr)) screenY = parentY + getPercentY(ptr) * parentH;

        // ---------------------------------------------------------------------
        // 4. PIVOT REFERENCE: source-of-truth pivot of the element. Shifts so the
        //    pivot point lands at the target (default TOP_LEFT = no shift).
        // ---------------------------------------------------------------------
        int pivotRef = getPivotReference(ptr);
        float pivotOffX, pivotOffY;
        switch (pivotRef) {
            case PIVOT_REFERENCE_TOP_RIGHT    -> { pivotOffX = sw;      pivotOffY = 0f; }
            case PIVOT_REFERENCE_BOTTOM_LEFT  -> { pivotOffX = 0f;      pivotOffY = sh; }
            case PIVOT_REFERENCE_BOTTOM_RIGHT -> { pivotOffX = sw;      pivotOffY = sh; }
            case PIVOT_REFERENCE_CENTER       -> { pivotOffX = sw / 2f; pivotOffY = sh / 2f; }
            default                           -> { pivotOffX = 0f;      pivotOffY = 0f; } // PIVOT_REFERENCE_TOP_LEFT
        }

        screenX -= pivotOffX;
        screenY -= pivotOffY;

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
