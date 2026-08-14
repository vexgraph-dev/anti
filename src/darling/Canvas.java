package darling;

import annotation.Draft;
import annotation.Intention;
import lang.Mat4;
import primitive.Float;

/**
 * The flat 2D layout root for the darling UI tree (@Draft).
 *
 * Defines the one canonical coordinate space the whole UI resolves into —
 * a fixed virtual resolution (design units) that is INDEPENDENT of the
 * swapchain extent and the backing scale. Every top-level node's layout is
 * resolved inside this canvas ({@link #resolveRoot}), and the canvas is mapped
 * to the framebuffer by a lazily-built y-down orthographic projection
 * ({@link #buildProjection}). `setLocation` therefore means something stable:
 * a node at canvas (100, 60) stays at canvas (100, 60) no matter how the window
 * grows, shrinks, or changes backing scale factor — only the projection moves.
 *
 * Scale modes (how the canvas maps onto the framebuffer):
 *   - {@link #MODE_STRETCH}: the whole canvas fills the window. Asymmetric
 *     scaling, so aspect changes distort — the classic "canvas follows window".
 *   - {@link #MODE_FIT}: uniform scale that fits the canvas inside the window
 *     (letterbox). Canvas is centered; the bars are the clear color.
 *   - {@link #MODE_PIXEL}: 1 canvas unit == 1 framebuffer pixel, canvas pinned
 *     to the top-left. THIS is the "relying on the position itself pixel-wise"
 *     mode: setLocation(100,60) is literally 100,60 window px, and growing the
 *     window reveals more canvas instead of stretching the content.
 *
 * A virtual width/height <= 0 means "follow the framebuffer" — the canvas is
 * exactly the framebuffer extent, which keeps the pre-canvas behavior intact.
 */
@Draft
@Intention("One canonical flat 2D space (fixed virtual res) + y-down orthographic projection so setLocation means canvas units, decoupled from swapchain size and backing scale. PIXEL mode = pixel-wise stable positions.")
public final class Canvas {

    public static final int MODE_STRETCH = 0; // whole canvas -> whole window (asymmetric)
    public static final int MODE_FIT     = 1; // uniform scale, letterboxed + centered
    public static final int MODE_PIXEL   = 2; // 1 canvas unit == 1 window px, top-left pinned

    /** Sentinel: virtual size <= 0 makes the canvas follow the framebuffer extent. */
    private static final float FOLLOW_FRAMEBUFFER = 0.0f;

    private static volatile float virtualWidth  = FOLLOW_FRAMEBUFFER;
    private static volatile float virtualHeight = FOLLOW_FRAMEBUFFER;
    private static volatile int   mode          = MODE_PIXEL;
    private static volatile float dpiScale      = 1.0f;

    private Canvas() {}

    /** Sets the DPI scaling factor (e.g. 2.0 on Retina, 1.0 for standard DPI). */
    public static void setDpiScale(float scale) {
        dpiScale = scale > 0f ? scale : 1.0f;
    }

    public static float getDpiScale() {
        return dpiScale;
    }

    /**
     * Sets the virtual resolution the UI is authored against. Width or height
     * <= 0 falls back to follow-framebuffer on that axis.
     */
    public static void setVirtualSize(float width, float height) {
        virtualWidth = width;
        virtualHeight = height;
    }

    public static float getVirtualWidth()  { return virtualWidth; }
    public static float getVirtualHeight() { return virtualHeight; }

    public static void setMode(int scaleMode) {
        if (scaleMode < MODE_STRETCH || scaleMode > MODE_PIXEL) {
            throw new IllegalArgumentException("Invalid canvas scale mode " + scaleMode + " (must be 0-2)");
        }
        mode = scaleMode;
    }

    public static int getMode() { return mode; }

    /**
     * The visible canvas rect within the framebuffer, in framebuffer pixels.
     * Writes 4 floats into outRect (Float array >= 4):
     * [screenX, screenY, canvasW, canvasH] — the top-left corner and the on-panel
     * size of the canvas. Letterbox bars live outside this rect.
     */
    public static void visibleRect(float fbW, float fbH, long outRect) {
        float vw = resolveSize(virtualWidth, fbW);
        float vh = resolveSize(virtualHeight, fbH);

        float scaleX, scaleY, ox, oy;
        switch (mode) {
            case MODE_STRETCH -> {
                scaleX = fbW / vw;
                scaleY = fbH / vh;
                ox = 0f;
                oy = 0f;
            }
            case MODE_FIT -> {
                float s = Math.min(fbW / vw, fbH / vh);
                scaleX = s;
                scaleY = s;
                ox = (fbW - vw * s) / 2f;
                oy = (fbH - vh * s) / 2f;
            }
            default -> { // MODE_PIXEL
                scaleX = dpiScale;
                scaleY = dpiScale;
                ox = 0f;
                oy = 0f;
            }
        }

        Float.set(outRect, 0, ox);
        Float.set(outRect, 1, oy);
        Float.set(outRect, 2, vw * scaleX);
        Float.set(outRect, 3, vh * scaleY);
    }

    /**
     * Builds the y-down orthographic projection mapping canvas units -> NDC,
     * writing the 16 column-major floats into the caller's Mat4. Use the same
     * fbW/fbH every frame so the matrix stays valid for the given framebuffer.
     *
     * Math: a canvas unit c maps to framebuffer px (c*scale + offset), and
     * framebuffer px p map to NDC (p/fb*2 - 1). NDC.y here is y-DOWN (Vulkan),
     * so +canvasY goes straight down — matching Container.resolve's top-left
     * origin.
     */
    public static void buildProjection(long destMat, float fbW, float fbH) {
        float vw = resolveSize(virtualWidth, fbW);
        float vh = resolveSize(virtualHeight, fbH);

        float scaleX, scaleY, ox, oy;
        switch (mode) {
            case MODE_STRETCH -> {
                scaleX = fbW / vw;
                scaleY = fbH / vh;
                ox = 0f;
                oy = 0f;
            }
            case MODE_FIT -> {
                float s = Math.min(fbW / vw, fbH / vh);
                scaleX = s;
                scaleY = s;
                ox = (fbW - vw * s) / 2f;
                oy = (fbH - vh * s) / 2f;
            }
            default -> { // MODE_PIXEL
                scaleX = dpiScale;
                scaleY = dpiScale;
                ox = 0f;
                oy = 0f;
            }
        }

        Mat4.zero(destMat);
        Mat4.set(destMat, 0, 0, 2f * scaleX / fbW);
        Mat4.set(destMat, 1, 1, 2f * scaleY / fbH);
        Mat4.set(destMat, 0, 3, 2f * ox / fbW - 1f);
        Mat4.set(destMat, 1, 3, 2f * oy / fbH - 1f);
        Mat4.set(destMat, 2, 2, 1f);
        Mat4.set(destMat, 3, 3, 1f);
    }

    /**
     * Resolves a top-level layout node against the canvas. The node resolves
     * inside the canvas's visible rect, so its coordinates are stable canvas
     * units (PIXEL mode: literally framebuffer pixels) no matter the window.
     * The resolver is dispatched on the node's runtime class so subclass AUTO
     * sizing (e.g. darling.Picture) keeps working. Writes [x, y, w, h].
     */
    public static void resolveRoot(long nodePtr, float fbW, float fbH, long outRect) {
        if (nodePtr == 0L) throw new NullPointerException("Canvas.resolveRoot() node is NULL!");
        if (outRect == 0L) throw new NullPointerException("Canvas.resolveRoot() outRect is NULL!");

        long visible = Float.allocateArray(4);
        try {
            visibleRect(fbW, fbH, visible);
            float cx = Float.get(visible, 0);
            float cy = Float.get(visible, 1);
            float cw = Float.get(visible, 2);
            float ch = Float.get(visible, 3);

            int cls = Container.classId(nodePtr);
            if (cls == Picture.CLASS_ID) {
                Picture.resolve(nodePtr, cx, cy, cw, ch, outRect);
            } else {
                Container.resolve(nodePtr, cx, cy, cw, ch, outRect);
            }
        } finally {
            Float.free(visible);
        }
    }

    private static float resolveSize(float virtual, float fb) {
        if (virtual <= 0f) return fb;
        return virtual;
    }
}