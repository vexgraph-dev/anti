package darling;

import annotation.Draft;
import annotation.Intention;
import annotation.Required;
import nio.ForeignMemory;
import oop.TypeRegister;

/**
 * Off-heap picture node (@Draft): a {@link Container} subclass that renders an
 * {@link image.Image} asset. The layout core lives in Container (payload starts
 * at Container.USER_STRIDE), so every Container accessor works directly on a
 * Picture pointer — see TypeRegister.getParentClass(PICTURE) == CONTAINER.
 *
 * Auto-size convention: a width/height of {@code -1} (AUTO) is resolved at
 * draw time from the bound image's intrinsic aspect — set one dimension and the
 * other -1 derives to keep the image shape; set both -1 for the image's raw
 * pixel size.
 */
@Draft
@Intention("Retained-mode off-heap picture node: Container subclass holding an Image asset with -1 auto-size from the image aspect.")
public final class Picture {

    @Required
    public static final int CLASS_ID = TypeRegister.ID_PICTURE;

    public static final int TYPE_SINGLETON = TypeRegister.PICTURE_SINGLETON; // 0x1000007A
    public static final int TYPE_ARRAY     = TypeRegister.PICTURE_ARRAY;     // 0x2000007A
    public static final int TYPE_POINTER   = TypeRegister.PICTURE_POINTER;   // 0x3000007A

    /** Sentinel: derive from the bound image. */
    public static final float AUTO = -1.0f;

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

    // --- Picture fields: Container layout prefix (0..47) then picture payload ---
    private static final int OFF_IMAGE = (int) Container.USER_STRIDE; // 48 long (image.Image enginePtr, 0 = none)

    private static final long USER_STRIDE = 56L; // bytes of user payload
    private static final long SLOT_SIZE   = 64L; // 8B header + 56B payload

    // --- Pool (lock-free free-list, ABA-tagged head, expansion flag) ---
    private static final int DEFAULT_CAPACITY = 1024;

    private static final java.lang.invoke.VarHandle FREE_HEAD_VH;
    private static final java.lang.invoke.VarHandle EXPANDING_VH;

    private static volatile long freeHead;     // top 16 = gen tag, bottom 48 = raw ptr
    private static volatile int expanding = 0;

    private static java.lang.foreign.Arena poolArena;
    private static volatile boolean active;

    static {
        try {
            java.lang.invoke.MethodHandles.Lookup lookup = java.lang.invoke.MethodHandles.lookup();
            FREE_HEAD_VH = lookup.findStaticVarHandle(Picture.class, "freeHead", long.class);
            EXPANDING_VH = lookup.findStaticVarHandle(Picture.class, "expanding", int.class);
        } catch (ReflectiveOperationException e) {
            throw new ExceptionInInitializerError(e);
        }

        poolArena = java.lang.foreign.Arena.ofShared();
        active = true;
        expandPool();
    }

    private Picture() {}

    private static void checkActive() {
        if (!active) throw new IllegalStateException("Picture subsystem is not active!");
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

    /** Allocates a Picture node (dirty by default). */
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
        setImage(ptr, 0L);
    }

    public static void free(long ptr) {
        checkActive();
        if (ptr == 0L) return;

        int type = type(ptr);
        if (type != TYPE_SINGLETON) throw new IllegalStateException("Double free or corrupt Picture pointer: 0x" + java.lang.Long.toHexString(ptr).toUpperCase());

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
        if (ptr == 0L) throw new NullPointerException("type() on NULL Picture pointer!");
        return ForeignMemory.getUnsafeInt(ptr - 8L);
    }

    public static int classId(long ptr) { return TypeRegister.getClassId(type(ptr)); }

    private static void checkPicture(long ptr) {
        if (ptr == 0L) throw new NullPointerException("Accessing NULL Picture pointer!");
        int cls = classId(ptr);
        if (cls != CLASS_ID)
            throw new IllegalArgumentException("Pointer 0x" + java.lang.Long.toHexString(ptr).toUpperCase() + " is Class ID " + cls + ", not a Picture");
    }

    // =========================================================================
    // LAYOUT (forwarded to Container)
    // =========================================================================

    public static float getX(long ptr) { return Container.getX(ptr); }
    public static float getY(long ptr) { return Container.getY(ptr); }
    public static float getWidth(long ptr) { return Container.getWidth(ptr); }
    public static float getHeight(long ptr) { return Container.getHeight(ptr); }

    public static void setX(long ptr, float x) { Container.setX(ptr, x); }
    public static void setY(long ptr, float y) { Container.setY(ptr, y); }
    public static void setLocation(long ptr, float x, float y) { Container.setX(ptr, x); Container.setY(ptr, y); }
    public static void setWidth(long ptr, float width) { Container.setWidth(ptr, width); }
    public static void setHeight(long ptr, float height) { Container.setHeight(ptr, height); }

    public static void setAnchor(long ptr, int refAnchor, int elemAnchor) {
        Container.setReferenceAnchor(ptr, refAnchor);
        Container.setElementAnchor(ptr, elemAnchor);
    }

    public static void setZ(long ptr, int z) { Container.setZ(ptr, z); }

    public static void setVisible(long ptr, boolean visible) { Container.setVisible(ptr, visible); }
    public static void setEnabled(long ptr, boolean enabled) { Container.setEnabled(ptr, enabled); }

    // =========================================================================
    // IMAGE ASSET
    // =========================================================================

    /** The bound image.Image asset, or 0 if none. */
    public static long getImage(long ptr) { checkPicture(ptr); return ForeignMemory.getLong(ptr + OFF_IMAGE); }

    public static void setImage(long ptr, long imageEnginePtr) {
        checkPicture(ptr);
        ForeignMemory.setLong(ptr + OFF_IMAGE, imageEnginePtr);
        Container.markDirty(ptr);
    }

    // =========================================================================
    // LAYOUT RESOLUTION
    // =========================================================================

    /**
     * Resolves the picture's screen rect. Dimensions set to {@link #AUTO} (-1)
     * derive from the bound image: both -1 use the image's raw pixel size; one
     * -1 derives the other side to preserve the image aspect ratio. Writes
     * [screenX, screenY, screenW, screenH] into outRect (primitive.Float array).
     */
    public static void resolve(long ptr, float parentX, float parentY, float parentW, float parentH, long outRect) {
        checkPicture(ptr);
        if (outRect == 0L) throw new NullPointerException("Picture.resolve() outRect is NULL!");

        float w = getWidth(ptr);
        float h = getHeight(ptr);
        if (w == AUTO || h == AUTO) {
            long imagePtr = getImage(ptr);
            if (imagePtr != 0L) {
                float iw = image.Image.getWidth(imagePtr);
                float ih = image.Image.getHeight(imagePtr);
                boolean autoW = (w == AUTO);
                boolean autoH = (h == AUTO);
                if (autoW && autoH) {
                    w = iw;
                    h = ih;
                } else if (autoW) {
                    w = (iw / ih) * h;
                } else {
                    h = (ih / iw) * w;
                }
            } else {
                // No image: auto sides collapse to 0.
                if (w == AUTO) w = 0f;
                if (h == AUTO) h = 0f;
            }
        }
        Container.resolveSized(ptr, w, h, parentX, parentY, parentW, parentH, outRect);
    }
}