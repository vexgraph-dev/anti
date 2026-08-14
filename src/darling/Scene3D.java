package darling;

import annotation.Draft;
import annotation.Intention;
import annotation.Required;
import nio.ForeignMemory;
import oop.TypeRegister;

import java.lang.foreign.Arena;
import java.lang.invoke.MethodHandles;
import java.lang.invoke.VarHandle;

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
    private static final long SLOT_SIZE   = USER_STRIDE + 8L;

    // --- Pool (lock-free free-list, ABA-tagged head, expansion flag) ---
    private static final int DEFAULT_CAPACITY = 1024;

    private static final VarHandle FREE_HEAD_VH;
    private static final VarHandle EXPANDING_VH;

    private static volatile long freeHead;
    private static volatile int expanding = 0;

    private static Arena poolArena;
    private static volatile boolean active;

    static {
        try {
            MethodHandles.Lookup lookup = MethodHandles.lookup();
            FREE_HEAD_VH = lookup.findStaticVarHandle(Scene3D.class, "freeHead", long.class);
            EXPANDING_VH = lookup.findStaticVarHandle(Scene3D.class, "expanding", int.class);
        } catch (ReflectiveOperationException e) {
            throw new ExceptionInInitializerError(e);
        }

        poolArena = Arena.ofShared();
        active = true;
        expandPool();
    }

    private Scene3D() {}

    private static void checkActive() {
        if (!active) throw new IllegalStateException("Scene3D subsystem is not active!");
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

    /** Allocates a Scene3D node (dirty by default). */
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
                Scene.initDefaults(rawHead);
                return rawHead;
            }
        }
    }

    public static void free(long ptr) {
        checkActive();
        if (ptr == 0L) return;

        int type = type(ptr);
        if (type != TYPE_SINGLETON) throw new IllegalStateException("Double free or corrupt Scene3D pointer: 0x" + java.lang.Long.toHexString(ptr).toUpperCase());

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

    public static int type(long ptr) {
        if (ptr == 0L) throw new NullPointerException("type() on NULL Scene3D pointer!");
        return ForeignMemory.getUnsafeInt(ptr - 8L);
    }

    public static int length(long ptr) {
        if (ptr == 0L) throw new NullPointerException("length() on NULL Scene3D pointer!");
        return ForeignMemory.getUnsafeInt(ptr - 4L);
    }

    public static int classId() { return CLASS_ID; }

    public static int classId(long ptr) { return TypeRegister.getClassId(type(ptr)); }

    private static void checkScene3D(long ptr) {
        if (ptr == 0L) throw new NullPointerException("Accessing NULL Scene3D pointer!");
        if (!TypeRegister.isA(classId(ptr), CLASS_ID))
            throw new IllegalArgumentException("Pointer 0x" + java.lang.Long.toHexString(ptr).toUpperCase() + " is Class ID " + classId(ptr) + ", expected Scene3D (or subclass)");
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