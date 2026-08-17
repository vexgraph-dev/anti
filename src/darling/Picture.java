package darling;

import annotation.Draft;
import annotation.Intention;
import annotation.Required;
import bit.Bit64;
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
    public static final int PARENT_ANCHOR_TOP_LEFT      = Container.PARENT_ANCHOR_TOP_LEFT;
    public static final int PARENT_ANCHOR_TOP_CENTER    = Container.PARENT_ANCHOR_TOP_CENTER;
    public static final int PARENT_ANCHOR_TOP_RIGHT     = Container.PARENT_ANCHOR_TOP_RIGHT;
    public static final int PARENT_ANCHOR_MIDDLE_LEFT   = Container.PARENT_ANCHOR_MIDDLE_LEFT;
    public static final int PARENT_ANCHOR_MIDDLE_CENTER = Container.PARENT_ANCHOR_MIDDLE_CENTER;
    public static final int PARENT_ANCHOR_MIDDLE_RIGHT  = Container.PARENT_ANCHOR_MIDDLE_RIGHT;
    public static final int PARENT_ANCHOR_BOTTOM_LEFT   = Container.PARENT_ANCHOR_BOTTOM_LEFT;
    public static final int PARENT_ANCHOR_BOTTOM_CENTER = Container.PARENT_ANCHOR_BOTTOM_CENTER;
    public static final int PARENT_ANCHOR_BOTTOM_RIGHT  = Container.PARENT_ANCHOR_BOTTOM_RIGHT;

    public static final int SELF_ANCHOR_TOP_LEFT     = Container.SELF_ANCHOR_TOP_LEFT;
    public static final int SELF_ANCHOR_TOP_RIGHT    = Container.SELF_ANCHOR_TOP_RIGHT;
    public static final int SELF_ANCHOR_BOTTOM_LEFT  = Container.SELF_ANCHOR_BOTTOM_LEFT;
    public static final int SELF_ANCHOR_BOTTOM_RIGHT = Container.SELF_ANCHOR_BOTTOM_RIGHT;

    public static final int PIVOT_REFERENCE_TOP_LEFT     = Container.PIVOT_REFERENCE_TOP_LEFT;
    public static final int PIVOT_REFERENCE_TOP_RIGHT    = Container.PIVOT_REFERENCE_TOP_RIGHT;
    public static final int PIVOT_REFERENCE_BOTTOM_LEFT  = Container.PIVOT_REFERENCE_BOTTOM_LEFT;
    public static final int PIVOT_REFERENCE_BOTTOM_RIGHT = Container.PIVOT_REFERENCE_BOTTOM_RIGHT;
    public static final int PIVOT_REFERENCE_CENTER       = Container.PIVOT_REFERENCE_CENTER;

    // --- Picture fields: Container layout prefix (0..55) then picture payload ---
    private static final int OFF_IMAGE       = (int) Container.USER_STRIDE; // 56 long (image.Image enginePtr, 0 = none)
    private static final int OFF_IMAGE_SIZE_W = 64; // float (image draw width; 0 = follow picture box)
    private static final int OFF_IMAGE_SIZE_H = 68; // float (image draw height; 0 = follow picture box)
    private static final int OFF_CROP_X1     = 72; // float (crop left in image pixels)
    private static final int OFF_CROP_Y1     = 76; // float (crop top in image pixels)
    private static final int OFF_CROP_X2     = 80; // float (crop right in image pixels)
    private static final int OFF_CROP_Y2     = 84; // float (crop bottom in image pixels)
    private static final int OFF_HAS_IMAGE_SIZE = 88; // byte
    private static final int OFF_HAS_CROP    = 89; // byte

    private static final long USER_STRIDE = 96L; // bytes of user payload
    private static final long STRUCT_SIZE = USER_STRIDE; // native struct payload, stored in the Bit64 slot

    // =========================================================================
    // ALLOCATION / RECYCLING — DELEGATED TO THE BIT-WIDTH POOL (bit.Bit64)
    // =========================================================================

    public static void freeAll() {
        // No-op: Bit64.freeAll() manages the shared pool arena.
    }

    /** Allocates a Picture node (dirty by default). */
    public static long allocate() {
        long enginePtr = Bit64.allocateSingleton(TYPE_SINGLETON);
        long s = ForeignMemory.allocateNative(STRUCT_SIZE);
        ForeignMemory.setLong(enginePtr, s);
        initDefaults(enginePtr);
        return enginePtr;
    }

    private static void initDefaults(long ptr) {
        Container.initDefaults(ptr);
        ForeignMemory.setLong(struct(ptr) + OFF_IMAGE, 0L);
        ForeignMemory.setFloat(struct(ptr) + OFF_IMAGE_SIZE_W, 0f);
        ForeignMemory.setFloat(struct(ptr) + OFF_IMAGE_SIZE_H, 0f);
        ForeignMemory.setFloat(struct(ptr) + OFF_CROP_X1, 0f);
        ForeignMemory.setFloat(struct(ptr) + OFF_CROP_Y1, 0f);
        ForeignMemory.setFloat(struct(ptr) + OFF_CROP_X2, 0f);
        ForeignMemory.setFloat(struct(ptr) + OFF_CROP_Y2, 0f);
        ForeignMemory.setByte(struct(ptr) + OFF_HAS_IMAGE_SIZE, (byte) 0);
        ForeignMemory.setByte(struct(ptr) + OFF_HAS_CROP, (byte) 0);
    }

    public static void free(long ptr) {
        if (ptr == 0L) return;

        int type = type(ptr);
        if (type != TYPE_SINGLETON) throw new IllegalStateException("Double free or corrupt Picture pointer: 0x" + java.lang.Long.toHexString(ptr).toUpperCase());

        long s = struct(ptr);
        ForeignMemory.freeNative(s);
        Bit64.free(ptr);
    }

    private static long struct(long ptr) {
        if (ptr == 0L) throw new NullPointerException("Accessing NULL Picture pointer!");
        return ForeignMemory.getLong(ptr);
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

    /** Sets both the picture-box width and height. Negative AUTO (-1) is allowed on either axis. */
    public static void setSize(long ptr, float width, float height) {
        Container.setWidth(ptr, width);
        Container.setHeight(ptr, height);
    }

    public static int getParentAnchor(long ptr) { return Container.getParentAnchor(ptr); }
    public static void setParentAnchor(long ptr, int parentAnchor) { Container.setParentAnchor(ptr, parentAnchor); }

    public static int getSelfAnchor(long ptr) { return Container.getSelfAnchor(ptr); }
    public static void setSelfAnchor(long ptr, int selfAnchor) { Container.setSelfAnchor(ptr, selfAnchor); }

    public static int getPivotReference(long ptr) { return Container.getPivotReference(ptr); }
    public static void setPivotReference(long ptr, int pivotReference) { Container.setPivotReference(ptr, pivotReference); }

    public static void setParentAnchor(long ptr, int parentAnchor, int selfAnchor) {
        Container.setParentAnchor(ptr, parentAnchor, selfAnchor);
    }

    public static void setParentAnchor(long ptr, int parentAnchor, int selfAnchor, int pivotReference) {
        Container.setParentAnchor(ptr, parentAnchor, selfAnchor, pivotReference);
    }

    public static void setCenter(long ptr) { Container.setCenter(ptr); }

    public static void setZ(long ptr, int z) { Container.setZ(ptr, z); }

    public static void setVisible(long ptr, boolean visible) { Container.setVisible(ptr, visible); }
    public static void setEnabled(long ptr, boolean enabled) { Container.setEnabled(ptr, enabled); }

    // =========================================================================
    // IMAGE ASSET
    // =========================================================================

    /** The bound image.Image asset, or 0 if none. */
    public static long getImage(long ptr) { checkPicture(ptr); return ForeignMemory.getLong(struct(ptr) + OFF_IMAGE); }

    public static void setImage(long ptr, long imageEnginePtr) {
        checkPicture(ptr);
        ForeignMemory.setLong(struct(ptr) + OFF_IMAGE, imageEnginePtr);
        Container.markDirty(ptr);
    }

    // =========================================================================
    // IMAGE SIZE & CROP
    // =========================================================================

    /**
     * The image's draw size inside the picture box. {@code w <= 0 || h <= 0}
     * clears the override (the image then fills the picture's resolved size).
     * When only one side is set, the other follows the picture box so the image
     * is not distorted unpredictably (edge case: 0/positive-mixed is accepted,
     * the zero side still resolves to the picture box).
     */
    public static void setImageSize(long ptr, float w, float h) {
        checkPicture(ptr);
        boolean has = (w > 0f || h > 0f);
        ForeignMemory.setFloat(struct(ptr) + OFF_IMAGE_SIZE_W, Math.max(0f, w));
        ForeignMemory.setFloat(struct(ptr) + OFF_IMAGE_SIZE_H, Math.max(0f, h));
        ForeignMemory.setByte(struct(ptr) + OFF_HAS_IMAGE_SIZE, (byte) (has ? 1 : 0));
        Container.markDirty(ptr);
    }

    public static boolean hasImageSize(long ptr) { checkPicture(ptr); return ForeignMemory.getByte(struct(ptr) + OFF_HAS_IMAGE_SIZE) != 0; }
    public static float getImageSizeWidth(long ptr) { checkPicture(ptr); return ForeignMemory.getFloat(struct(ptr) + OFF_IMAGE_SIZE_W); }
    public static float getImageSizeHeight(long ptr) { checkPicture(ptr); return ForeignMemory.getFloat(struct(ptr) + OFF_IMAGE_SIZE_H); }

    /**
     * Sets the image crop as two corner points in image pixels: (x1, y1) is the
     * top-left, (x2, y2) the bottom-right, the crop being the rectangle between
     * them. Edge cases handled at resolve time (see {@link #getCrop}):
     *   - inverted points ([x1>x2] or [y1>y2]) are swapped to a valid rect
     *   - out-of-image values are clamped into [0, imageWidth/Height]
     *   - a degenerate (zero-area) or unset crop resolves to the FULL image
     */
    public static void setCrop(long ptr, float x1, float y1, float x2, float y2) {
        checkPicture(ptr);
        ForeignMemory.setFloat(struct(ptr) + OFF_CROP_X1, x1);
        ForeignMemory.setFloat(struct(ptr) + OFF_CROP_Y1, y1);
        ForeignMemory.setFloat(struct(ptr) + OFF_CROP_X2, x2);
        ForeignMemory.setFloat(struct(ptr) + OFF_CROP_Y2, y2);
        ForeignMemory.setByte(struct(ptr) + OFF_HAS_CROP, (byte) 1);
        Container.markDirty(ptr);
    }

    public static void clearCrop(long ptr) {
        checkPicture(ptr);
        ForeignMemory.setByte(struct(ptr) + OFF_HAS_CROP, (byte) 0);
        Container.markDirty(ptr);
    }

    public static boolean hasCrop(long ptr) { checkPicture(ptr); return ForeignMemory.getByte(struct(ptr) + OFF_HAS_CROP) != 0; }

    /**
     * Resolves the crop into a normalized UV rect [u1, v1, u2, v2] within the
     * image (for the sampler). Falls back to the full image [0,0,1,1] when the
     * crop is unset, degenerate (zero area) or the image missing. Writes 4
     * floats into outRect (primitive.Float array of length >= 4).
     */
    public static void getCrop(long ptr, long outRect) {
        checkPicture(ptr);
        if (outRect == 0L) throw new NullPointerException("Picture.getCrop() outRect is NULL!");

        if (!hasCrop(ptr)) {
            primitive.Float.set(outRect, 0, 0f);
            primitive.Float.set(outRect, 1, 0f);
            primitive.Float.set(outRect, 2, 1f);
            primitive.Float.set(outRect, 3, 1f);
            return;
        }

        long imagePtr = getImage(ptr);
        if (imagePtr == 0L) {
            primitive.Float.set(outRect, 0, 0f);
            primitive.Float.set(outRect, 1, 0f);
            primitive.Float.set(outRect, 2, 1f);
            primitive.Float.set(outRect, 3, 1f);
            return;
        }
        float iw = image.Image.getWidth(imagePtr);
        float ih = image.Image.getHeight(imagePtr);

        float x1 = ForeignMemory.getFloat(struct(ptr) + OFF_CROP_X1);
        float y1 = ForeignMemory.getFloat(struct(ptr) + OFF_CROP_Y1);
        float x2 = ForeignMemory.getFloat(struct(ptr) + OFF_CROP_X2);
        float y2 = ForeignMemory.getFloat(struct(ptr) + OFF_CROP_Y2);

        // Edge case: inverted corner points -> normalize orientation.
        if (x1 > x2) { float t = x1; x1 = x2; x2 = t; }
        if (y1 > y2) { float t = y1; y1 = y2; y2 = t; }

        // Edge case: clamp out-of-image pixels into [0, imageWidth/Height].
        if (x1 < 0f) x1 = 0f;
        if (y1 < 0f) y1 = 0f;
        if (x2 > iw) x2 = iw;
        if (y2 > ih) y2 = ih;

        // Edge case: zero/negative area -> full image (never an upside-down or
        // empty sample region).
        if (x2 - x1 <= 0f || y2 - y1 <= 0f) {
            primitive.Float.set(outRect, 0, 0f);
            primitive.Float.set(outRect, 1, 0f);
            primitive.Float.set(outRect, 2, 1f);
            primitive.Float.set(outRect, 3, 1f);
            return;
        }

        primitive.Float.set(outRect, 0, x1 / iw);
        primitive.Float.set(outRect, 1, y1 / ih);
        primitive.Float.set(outRect, 2, x2 / iw);
        primitive.Float.set(outRect, 3, y2 / ih);
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