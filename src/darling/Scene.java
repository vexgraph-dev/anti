package darling;

import annotation.Draft;
import annotation.Intention;
import annotation.Required;
import bit.Bit64;
import nio.ForeignMemory;
import oop.TypeRegister;

/**
 * Off-heap scene root. Structural subclass of {@link Panel}: the layout core
 * lives in Container, background color + the parent/child tree live in Panel,
 * and this scene's payload begins exactly at Panel.USER_STRIDE, so any
 * Container or Panel accessor can be called directly with a Scene pointer.
 * See TypeRegister.getParentClass(SCENE) == PANEL.
 *
 * Scene adds what Panel does not own: the scene's virtual size (the fixed
 * resolution the scene renders into, independent of the window) and the
 * pixel/fit/stretch mapping mode. A window's swapchain follows the real window;
 * the scene never re-renders on resize — the present pass scales the scene into
 * whatever the window currently occupies.
 *
 * Scene2D/Scene3D are structural subclasses of this class and differ only by
 * their projection semantics, never by payload.
 */
@Draft
@Intention("Off-heap scene root: Panel base + virtual size + pixel/fit/stretch mapping, the fixed-res render target for the swapchain-scaled blit.")
public final class Scene {

    @Required
    public static final int CLASS_ID = TypeRegister.ID_SCENE;

    public static final int TYPE_SINGLETON = TypeRegister.SCENE_SINGLETON;
    public static final int TYPE_ARRAY     = TypeRegister.SCENE_ARRAY;
    public static final int TYPE_POINTER   = TypeRegister.SCENE_POINTER;

    // --- Mapping modes (same semantics as Canvas) ---
    public static final int MODE_STRETCH = 0; // whole scene -> whole window (asymmetric)
    public static final int MODE_FIT     = 1; // uniform scale, letterboxed + centered
    public static final int MODE_PIXEL   = 2; // 1 scene unit == 1 window px, top-left pinned

    // --- Scene fields: Container prefix (0..47) + Panel payload (48..111) + scene payload ---
    // The scene's size IS Container's w/h (setSize) — that fixed resolution is the
    // offscreen render target. Only the mapping mode is scene-specific payload.
    private static final int OFF_MODE = (int) Panel.USER_STRIDE; // 120 int (MODE_STRETCH/FIT/PIXEL)

    static final long USER_STRIDE = 128L; // bytes of user payload
    private static final long STRUCT_SIZE = USER_STRIDE; // native struct payload, stored in the Bit64 slot

    // =========================================================================
    // ALLOCATION / RECYCLING — DELEGATED TO THE BIT-WIDTH POOL (bit.Bit64)
    // =========================================================================

    public static void freeAll() {
        // No-op: Bit64.freeAll() manages the shared pool arena.
    }

    /** Allocates a Scene node (dirty by default). */
    public static long allocate() {
        long enginePtr = Bit64.allocateSingleton(TYPE_SINGLETON);
        long s = ForeignMemory.allocateNative(STRUCT_SIZE);
        ForeignMemory.setLong(enginePtr, s);
        initDefaults(enginePtr);
        return enginePtr;
    }

    static void initDefaults(long ptr) {
        Panel.initDefaults(ptr);
        // Default PIXEL: 1 scene unit == 1 window px, top-left pinned. Resizing reveals
        // more window (bg-clear area) instead of scaling/centering the scene.
        ForeignMemory.setInt(struct(ptr) + OFF_MODE, MODE_PIXEL);
    }

    public static void free(long ptr) {
        if (ptr == 0L) return;

        int type = type(ptr);
        if (type != TYPE_SINGLETON) throw new IllegalStateException("Double free or corrupt Scene pointer: 0x" + java.lang.Long.toHexString(ptr).toUpperCase());

        long s = struct(ptr);
        ForeignMemory.freeNative(s);
        Bit64.free(ptr);
    }

    private static long struct(long ptr) {
        if (ptr == 0L) throw new NullPointerException("Accessing NULL Scene pointer!");
        return ForeignMemory.getLong(ptr);
    }

    // =========================================================================
    // ARCHITECTURAL CHECKS & METADATA
    // =========================================================================

    public static int type(long ptr) {
        if (ptr == 0L) throw new NullPointerException("type() on NULL Scene pointer!");
        return ForeignMemory.getUnsafeInt(ptr - 8L);
    }

    public static int length(long ptr) {
        if (ptr == 0L) throw new NullPointerException("length() on NULL Scene pointer!");
        return ForeignMemory.getUnsafeInt(ptr - 4L);
    }

    public static int classId() { return CLASS_ID; }

    public static int classId(long ptr) { return TypeRegister.getClassId(type(ptr)); }

    private static void checkScene(long ptr) {
        if (ptr == 0L) throw new NullPointerException("Accessing NULL Scene pointer!");
        if (!TypeRegister.isA(classId(ptr), CLASS_ID))
            throw new IllegalArgumentException("Pointer 0x" + java.lang.Long.toHexString(ptr).toUpperCase() + " is Class ID " + classId(ptr) + ", expected Scene (or subclass)");
    }

    // =========================================================================
    // VIRTUAL SIZE & MAPPING MODE
    // =========================================================================

    // The scene's virtual size IS its Container w/h (the fixed resolution it
    // renders into). setSize() is the only size setter a caller needs.

    public static float getVirtualWidth(long ptr) { return Container.getWidth(ptr); }
    public static float getVirtualHeight(long ptr) { return Container.getHeight(ptr); }

    public static int getMode(long ptr) { checkScene(ptr); return ForeignMemory.getInt(struct(ptr) + OFF_MODE); }

    public static void setMode(long ptr, int mode) {
        checkScene(ptr);
        if (mode < MODE_STRETCH || mode > MODE_PIXEL) throw new IllegalArgumentException("Invalid scene mode " + mode);
        ForeignMemory.setInt(struct(ptr) + OFF_MODE, mode);
        Panel.markDirty(ptr);
    }

    // =========================================================================
    // LAYOUT (delegated to Container/Panel)
    // =========================================================================

    public static float getX(long ptr) { return Container.getX(ptr); }
    public static float getY(long ptr) { return Container.getY(ptr); }
    public static float getWidth(long ptr) { return Container.getWidth(ptr); }
    public static float getHeight(long ptr) { return Container.getHeight(ptr); }

    public static void setPos(long ptr, float x, float y) { Container.setPos(ptr, x, y); }
    public static void setSize(long ptr, float width, float height) { Container.setSize(ptr, width, height); }

    public static int getParentAnchor(long ptr) { return Container.getParentAnchor(ptr); }
    public static void setParentAnchor(long ptr, int parentAnchor) { Container.setParentAnchor(ptr, parentAnchor); }

    public static int getSelfAnchor(long ptr) { return Container.getSelfAnchor(ptr); }
    public static void setSelfAnchor(long ptr, int selfAnchor) { Container.setSelfAnchor(ptr, selfAnchor); }

    public static int getPivotReference(long ptr) { return Container.getPivotReference(ptr); }
    public static void setPivotReference(long ptr, int pivotReference) { Container.setPivotReference(ptr, pivotReference); }

    public static void setParentAnchor(long ptr, int parentAnchor, int selfAnchor, int pivotReference) { Container.setParentAnchor(ptr, parentAnchor, selfAnchor, pivotReference); }

    public static void setCenter(long ptr) { Container.setCenter(ptr); }

    public static int getBackgroundColor(long ptr) { return Panel.getBackgroundColor(ptr); }
    public static void setBackgroundColor(long ptr, int color) { Panel.setBackgroundColor(ptr, color); }
}