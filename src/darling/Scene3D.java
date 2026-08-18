package darling;

import annotation.Draft;
import annotation.Intention;
import annotation.Required;
import bit.Bit64;
import nio.ForeignMemory;
import oop.TypeRegister;

import nio.StringLookup;
/**
 * Off-heap 3D scene. Structural subclass of {@link Scene}: the layout core
 * (Container), the background color + tree (Panel), and the virtual size/mode
 * (Scene) all live above this class, and its own payload begins at
 * Scene.USER_STRIDE. See TypeRegister.getParentClass(SCENE3D) == SCENE.
 *
 * Today the 3D projection is not yet implemented (the demo path is the 2D
 * orthographic blit), so Scene3D carries no extra payload yet — it exists so
 * the renderer can dispatch on class and so camera/perspective/depth state can
 * be added here without touching Scene2D or Scene.
 */
@Draft
@Intention("3D scene: perspective projection placeholder, structural subclass of Scene with no extra payload yet.")
public final class Scene3D {

    @Required
    public static final int CLASS_ID = TypeRegister.ID_SCENE3D;

    public static final int TYPE_SINGLETON = TypeRegister.SCENE3D_SINGLETON;
    public static final int TYPE_ARRAY     = TypeRegister.SCENE3D_ARRAY;
    public static final int TYPE_POINTER   = TypeRegister.SCENE3D_POINTER;

    private static final long USER_STRIDE = Scene.USER_STRIDE; // no extra payload yet
    private static final long STRUCT_SIZE = USER_STRIDE; // native struct payload, stored in the Bit64 slot

    // =========================================================================
    // ALLOCATION / RECYCLING — DELEGATED TO THE BIT-WIDTH POOL (bit.Bit64)
    // =========================================================================

    public static void freeAll() {
        // No-op: Bit64.freeAll() manages the shared pool arena.
    }

    /** Allocates a Scene3D node (dirty by default). */
    public static long allocate() {
        long enginePtr = Bit64.allocateSingleton(TYPE_SINGLETON);
        long s = ForeignMemory.allocateNative(STRUCT_SIZE);
        ForeignMemory.setLong(enginePtr, s);
        Scene.initDefaults(enginePtr);
        return enginePtr;
    }

    public static void free(long ptr) {
        if (ptr == 0L) return;

        int type = type(ptr);
        if (type != TYPE_SINGLETON) throw new IllegalStateException(StringLookup.getJavaString(632) + java.lang.Long.toHexString(ptr).toUpperCase());

        long s = struct(ptr);
        ForeignMemory.freeNative(s);
        Bit64.free(ptr);
    }

    private static long struct(long ptr) {
        if (ptr == 0L) throw new NullPointerException(StringLookup.getJavaString(633));
        return ForeignMemory.getLong(ptr);
    }

    // =========================================================================
    // ARCHITECTURAL CHECKS & METADATA
    // =========================================================================

    public static int type(long ptr) {
        if (ptr == 0L) throw new NullPointerException(StringLookup.getJavaString(634));
        return ForeignMemory.getUnsafeInt(ptr - 8L);
    }

    public static int length(long ptr) {
        if (ptr == 0L) throw new NullPointerException(StringLookup.getJavaString(635));
        return ForeignMemory.getUnsafeInt(ptr - 4L);
    }

    public static int classId() { return CLASS_ID; }

    public static int classId(long ptr) { return TypeRegister.getClassId(type(ptr)); }

    private static void checkScene3D(long ptr) {
        if (ptr == 0L) throw new NullPointerException(StringLookup.getJavaString(633));
        if (!TypeRegister.isA(classId(ptr), CLASS_ID))
            throw new IllegalArgumentException(StringLookup.getJavaString(28) + java.lang.Long.toHexString(ptr).toUpperCase() + StringLookup.getJavaString(29) + classId(ptr) + StringLookup.getJavaString(636));
    }

    // =========================================================================
    // SCENE API (delegated to Scene/Panel/Container)
    // =========================================================================

    public static float getX(long ptr) { return Scene.getX(ptr); }
    public static float getY(long ptr) { return Scene.getY(ptr); }
    public static float getWidth(long ptr) { return Scene.getWidth(ptr); }
    public static float getHeight(long ptr) { return Scene.getHeight(ptr); }

    public static void setPos(long ptr, float x, float y) { Scene.setPos(ptr, x, y); }
    public static void setSize(long ptr, float width, float height) { Scene.setSize(ptr, width, height); }

    public static int getParentAnchor(long ptr) { return Scene.getParentAnchor(ptr); }
    public static void setParentAnchor(long ptr, int parentAnchor) { Scene.setParentAnchor(ptr, parentAnchor); }

    public static int getSelfAnchor(long ptr) { return Scene.getSelfAnchor(ptr); }
    public static void setSelfAnchor(long ptr, int selfAnchor) { Scene.setSelfAnchor(ptr, selfAnchor); }

    public static int getPivotReference(long ptr) { return Scene.getPivotReference(ptr); }
    public static void setPivotReference(long ptr, int pivotReference) { Scene.setPivotReference(ptr, pivotReference); }

    public static void setCenter(long ptr) { Scene.setCenter(ptr); }

    public static int getBackgroundColor(long ptr) { return Scene.getBackgroundColor(ptr); }
    public static void setBackgroundColor(long ptr, int color) { Scene.setBackgroundColor(ptr, color); }

    public static float getVirtualWidth(long ptr) { return Scene.getVirtualWidth(ptr); }
    public static float getVirtualHeight(long ptr) { return Scene.getVirtualHeight(ptr); }
    public static int getMode(long ptr) { return Scene.getMode(ptr); }
    public static void setMode(long ptr, int mode) { Scene.setMode(ptr, mode); }
}