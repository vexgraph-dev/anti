package window;

import engine.EngineLoop;
import annotation.Draft;

import java.lang.foreign.Arena;
import java.lang.foreign.MemorySegment;
import java.lang.foreign.ValueLayout;
import java.lang.invoke.VarHandle;
import java.nio.charset.StandardCharsets;
import java.util.concurrent.atomic.AtomicBoolean;

import static java.util.concurrent.locks.LockSupport.*;

/**
 * Static pointer-handler wrapper for Native Windows.
 * Follows the primitive value-class structure and routes pointers to the correct OS backend.
 */
public final class Window {

    private Window() {}

    // Title mailbox bridging the Core Draw Worker -> Thread 0.
    // AppKit requires window geometry/title changes on the main thread, so the worker
    // only *publishes* the FPS label here; Window.run's pump *applies* it every pass.
    private static final MemorySegment TITLE_MAILBOX = Arena.global().allocate(136);
    private static final VarHandle TITLE_SEQ_VH = ValueLayout.JAVA_LONG.varHandle();
    private static long titleLastSeq;

    /** Serializes AppKit events with Metal-backed Vulkan swapchain operations on macOS. */
    public static final AtomicBoolean OS_NATIVE_MUTEX =
            new AtomicBoolean(false);

    private static final String OS = System.getProperty("os.name").toLowerCase();
    private static final boolean IS_MAC = OS.contains("mac");
    private static final boolean IS_WIN = OS.contains("win");
    private static final boolean IS_LINUX = OS.contains("nix") || OS.contains("nux") || OS.contains("aix");

    /** Explicit FPS cap (0 = auto / uncapped). Forces parking even in fullscreen IMMEDIATE mode. */
    public static volatile int TARGET_FPS;

    /** FPS limiter applied while the window is minimized (0 = inherit the normal cap). */
    public static volatile int MINIMIZED_FPS = 10;

    /** Set once per minimized violation so the "can't do that" error is raised only a single time. */
    private static volatile boolean MINIMIZED_VIOLATION_FLAGGED;

    /** Effective FPS cap, recomputed every iteration on the Main Thread (AppKit must stay on the Main Thread). */
    private static volatile int EFFECTIVE_FPS;

    public static void setTargetFps(int fps) {
        TARGET_FPS = Math.max(0, fps);
        MINIMIZED_VIOLATION_FLAGGED = false;
    }

    /** Configure the hard FPS limiter enforced while the window is minimized. */
    public static void setMinimizedFps(int fps) {
        MINIMIZED_FPS = Math.max(0, fps);
        MINIMIZED_VIOLATION_FLAGGED = false;
    }

    /**
     * Change the Vulkan present mode at runtime (e.g. VK_PRESENT_MODE_IMMEDIATE_KHR or
     * VK_PRESENT_MODE_FIFO_KHR). This recreates the swapchain and rebuilds render targets.
     * IMMEDIATE honors TARGET_FPS exactly (0 = uncapped); FIFO vsyncs to the display.
     */
    public static void setFpsMode(long pointer, int presentMode) {
        while (!OS_NATIVE_MUTEX.compareAndSet(false, true)) {
            Thread.onSpinWait();
        }
        try {
            vulkan.TriangleRenderer.setPresentMode(presentMode);
        } finally {
            OS_NATIVE_MUTEX.set(false);
        }
    }

    public static int getFpsMode() {
        return vulkan.Vulkan.getPresentMode();
    }

    /** True when the swapchain is currently vsync-locked (FIFO); false otherwise. */
    public static boolean isVsync() {
        return vulkan.Vulkan.isVsyncLocked();
    }

    /**
     * Hybrid sleep-spin until the deadline. LockSupport.parkNanos() is only a hint and on
     * macOS oversleeps by ~1-2ms of scheduler granularity, which compounds into draw jitter.
     * Park for the bulk of the wait, then busy-spin the final micro-window so the deadline
     * is met to sub-millisecond precision.
     */
    public static void parkUntil(long deadline) {
        long now = java.lang.System.nanoTime();
        long remaining = deadline - now;
        if (remaining <= 0L) return;

        // Park while more than ~1ms remains; spin the tail for precision.
        if (remaining > 1_000_000L) {
            parkNanos(remaining - 1_000_000L);
        }
        while (java.lang.System.nanoTime() < deadline) {
            Thread.onSpinWait();
        }
    }

    /**
     * Resolve the DRAW-side FPS cap for the Core Draw Worker.
     * 0 = uncapped (no parking; the worker spins as fast as the pipeline allows).
     * The vsync-lock bound does NOT apply here because present is decoupled from
     * draw — only the minimized clamp throttles the draw rate.
     */
    public static int getDrawCap(long pointer) {
        if (IS_MAC && macOSWindow.isMinimized(pointer)) return MINIMIZED_FPS;
        return TARGET_FPS;
    }

    /**
     * Resolve the effective FPS cap on the Main Thread.
     * 0 means no cap (busy wait). FIFO is vsync-locked, so it can never exceed the display refresh.
     * While minimized the frame rate is clamped to MINIMIZED_FPS; asking for a higher target cap than
     * that limiter allows is a hard violation, so we raise an error instead of silently lying.
     */
    private static int resolveFps(long pointer) {
        int cap = TARGET_FPS;

        if (IS_MAC) {
            if (macOSWindow.isMinimized(pointer)) {
                // Minimized: the window is hidden, never waste cycles presenting faster than the limiter.
                if (cap > MINIMIZED_FPS) {
                    if (!MINIMIZED_VIOLATION_FLAGGED) {
                        MINIMIZED_VIOLATION_FLAGGED = true;
                        throw new IllegalStateException(
                            "Can't run at " + cap + " FPS while minimized: the minimized limiter "
                            + "caps at " + MINIMIZED_FPS + " FPS. Lower TARGET_FPS or raise MINIMIZED_FPS."
                        );
                    }
                }
                return MINIMIZED_FPS;
            }
            MINIMIZED_VIOLATION_FLAGGED = false; // un-minimized restores normal caping

            if (vulkan.Vulkan.isVsyncLocked()) {
                // FIFO: vsync-locked, so the effective cap is bounded by the display refresh.
                int display = macOSWindow.getDisplayRefreshRate();
                return cap > 0 ? Math.min(cap, display) : display;
            }
            // IMMEDIATE: honor the requested cap exactly; 0 means uncapped (busy wait).
            return cap;
        }

        return cap;
    }

    public static long allocate(boolean borderless) {
        if (IS_MAC) return macOSWindow.allocate(borderless);
        if (IS_WIN) return windowsWindow.allocate(borderless);
        if (IS_LINUX) return linuxWindow.allocate(borderless);
        throw new UnsupportedOperationException("Unsupported OS: " + OS);
    }

    public static void free(long pointer) {
        if (IS_MAC) macOSWindow.free(pointer);
        else if (IS_WIN) windowsWindow.free(pointer);
        else if (IS_LINUX) linuxWindow.free(pointer);
    }

    public static void setTitle(long pointer, String title) {
        if (IS_MAC) macOSWindow.setTitle(pointer, title);
        else if (IS_WIN) windowsWindow.setTitle(pointer, title);
        else if (IS_LINUX) linuxWindow.setTitle(pointer, title);
    }

    /**
     * CORE worker side: publish an FPS label without touching AppKit. Thread 0's
     * pump picks it up in applyPendingTitle and applies it on the main thread.
     */
    public static void publishTitle(String title) {
        byte[] b = title.getBytes(StandardCharsets.UTF_8);
        int len = Math.min(b.length, 127);
        for (int i = 0; i < len; i++) {
            TITLE_MAILBOX.set(ValueLayout.JAVA_BYTE, i, b[i]);
        }
        TITLE_MAILBOX.set(ValueLayout.JAVA_BYTE, len, (byte) 0);
        long seq = (long) TITLE_SEQ_VH.getVolatile(TITLE_MAILBOX, 128L) + 1L;
        TITLE_SEQ_VH.setVolatile(TITLE_MAILBOX, 128L, seq); // release fence after the bytes
    }

    /** Thread 0 side: apply the latest published label (called from the event pump). */
    private static void applyPendingTitle(long pointer) {
        long seq = (long) TITLE_SEQ_VH.getVolatile(TITLE_MAILBOX, 128L);
        if (seq == titleLastSeq) return;
        titleLastSeq = seq;

        int len = 0;
        while (TITLE_MAILBOX.get(ValueLayout.JAVA_BYTE, len) != 0) len++;
        byte[] buf = new byte[len];
        for (int i = 0; i < len; i++) {
            buf[i] = TITLE_MAILBOX.get(ValueLayout.JAVA_BYTE, i);
        }
        setTitle(pointer, new String(buf, StandardCharsets.UTF_8));
    }

    /** CORE worker side: request a fullscreen toggle. AppKit animation is main-thread
     *  only, so Thread 0 applies it in applyPendingFullscreen. */
    public static void requestFullscreenToggle() {
        FULLSCREEN_TOGGLE_REQUEST.set(true);
    }
    private static final AtomicBoolean FULLSCREEN_TOGGLE_REQUEST = new AtomicBoolean(false);

    /** Thread 0 side: apply a pending fullscreen toggle (called from the event pump). */
    private static void applyPendingFullscreen(long pointer) {
        if (FULLSCREEN_TOGGLE_REQUEST.compareAndSet(true, false)) {
            toggleFullscreen(pointer);
        }
    }

    public static void setSize(long pointer, int width, int height) {
        if (IS_MAC) macOSWindow.setSize(pointer, width, height);
        else if (IS_WIN) windowsWindow.setSize(pointer, width, height);
        else if (IS_LINUX) linuxWindow.setSize(pointer, width, height);
    }

    public static void show(long pointer) {
        if (IS_MAC) macOSWindow.show(pointer);
        else if (IS_WIN) windowsWindow.show(pointer);
        else if (IS_LINUX) linuxWindow.show(pointer);
    }

    public static void setVisible(long pointer, boolean visible) {
        if (IS_MAC) macOSWindow.setVisible(pointer, visible);
        else if (IS_WIN) windowsWindow.setVisible(pointer, visible);
        else if (IS_LINUX) linuxWindow.setVisible(pointer, visible);
    }

    public static void setLocation(long pointer, int x, int y) {
        if (IS_MAC) macOSWindow.setLocation(pointer, x, y);
        else if (IS_WIN) windowsWindow.setLocation(pointer, x, y);
        else if (IS_LINUX) linuxWindow.setLocation(pointer, x, y);
    }

    public static long createSurface(long pointer) {
        if (IS_MAC) return macOSWindow.createSurface(pointer);
        if (IS_WIN) return windowsWindow.createSurface(pointer);
        if (IS_LINUX) return linuxWindow.createSurface(pointer);
        return 0L;
    }

    public static boolean isMetalDeviceAvailable() {
        return IS_MAC && macOSWindow.isMetalDeviceAvailable();
    }

    public static boolean shouldClose(long pointer) {
        if (IS_MAC) return macOSWindow.shouldClose(pointer);
        if (IS_WIN) return windowsWindow.shouldClose(pointer);
        if (IS_LINUX) return linuxWindow.shouldClose(pointer);
        return true;
    }

    public static boolean isMinimized(long pointer) {
        if (IS_MAC) return macOSWindow.isMinimized(pointer);
        // Win/Linux backends are still stubs; no minimized detection yet.
        return false;
    }

    public static boolean isFullscreen(long pointer) {
        if (IS_MAC) return macOSWindow.isFullscreen(pointer);
        return false;
    }

    public static void toggleFullscreen(long pointer) {
        if (IS_MAC) macOSWindow.toggleFullscreen(pointer);
    }

    /** CAMetalLayer vsync: YES = synced to display, NO = uncapped presentation. */
    public static void setDisplaySyncEnabled(long layerPointer, boolean enabled) {
        if (IS_MAC) macOSWindow.setDisplaySyncEnabled(layerPointer, enabled);
    }

    /** Content view size in backing pixels, packed (width &lt;&lt; 32) | height. 0 if unavailable. */
    public static long getContentSize(long pointer) {
        if (IS_MAC) return macOSWindow.getContentSize(pointer);
        return 0L;
    }

    /** Main screen size in backing pixels, packed (width &lt;&lt; 32) | height. 0 if unavailable. */
    public static long getScreenBackingSize() {
        if (IS_MAC) return macOSWindow.getScreenBackingSize();
        return 0L;
    }

    public static void pollEvents() {
        if (IS_MAC) macOSWindow.pollEvents();
        else if (IS_WIN) windowsWindow.pollEvents();
        else if (IS_LINUX) linuxWindow.pollEvents();
    }

    public static void waitEvents() {
        if (IS_MAC)
            macOSWindow.waitEvents();
        else if (IS_WIN) {
            windowsWindow.pollEvents();
            parkNanos(16_000_000L); // Fallback timeout polling
        }
        else if (IS_LINUX) {
            linuxWindow.pollEvents();
            parkNanos(16_000_000L); // Fallback timeout polling
        }
    }

    public static void setKeyEvent(long pointer, event.KeyEvent listener) {
        if (pointer == 0L) return;
        input.Key.addKeyEvent(listener);
    }

    public static void setMouseEvent(long pointer, event.MouseEvent listener) {
        if (pointer == 0L) return;
        input.Mouse.addMouseEvent(listener);
    }

    /** Fired by the Core Draw Worker whenever the window enters or exits fullscreen (argument = now fullscreen). */
    public static void setFullscreenListener(java.util.function.Consumer<Boolean> listener) {
        FULLSCREEN_LISTENER = listener;
    }
    public static java.util.function.Consumer<Boolean> getFullscreenListener() {
        return FULLSCREEN_LISTENER;
    }
    private static volatile java.util.function.Consumer<Boolean> FULLSCREEN_LISTENER;

    public static void run(long pointer, EngineLoop loop) {
        // Thread 0 = pure AppKit event pump. The Core Draw Worker owns the render
        // loop: it drains the input RingBuffers and drives Renderer.produceOnce()/
        // presentOnce(), so the FIFO vblank sleep happens on the worker, never here.
        // This thread spins as fast as the CPU allows, shoving high-precision
        // keyboard/mouse timestamps into the lock-free input RingBuffers.
        System.out.println("[Main Thread] Event pump engaged: Thread 0 parked, woken by AppKit events.");
        while (!shouldClose(pointer)) {
            applyPendingTitle(pointer); // CORE worker's FPS label -> AppKit (main thread only)
            applyPendingFullscreen(pointer); // CORE worker's fullscreen toggle -> AppKit (main thread only)
            waitEvents(); // bounded block: ~16ms cadence when idle, instant wake on input
        }
        System.out.println("[Main Thread] Window closed. Releasing Thread 0.");
    }
}
