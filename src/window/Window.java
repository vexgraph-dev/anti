package window;

import engine.EngineLoop;
import annotation.Draft;

import java.util.concurrent.atomic.AtomicBoolean;

import static java.util.concurrent.locks.LockSupport.*;

/**
 * Static pointer-handler wrapper for Native Windows.
 * Follows the primitive value-class structure and routes pointers to the correct OS backend.
 */
public final class Window {

    private Window() {}

    /** Serializes AppKit events with Metal-backed Vulkan swapchain operations on macOS. */
    public static final AtomicBoolean OS_NATIVE_MUTEX =
            new AtomicBoolean(false);

    private static final String OS = System.getProperty("os.name").toLowerCase();
    private static final boolean IS_MAC = OS.contains("mac");
    private static final boolean IS_WIN = OS.contains("win");
    private static final boolean IS_LINUX = OS.contains("nix") || OS.contains("nux") || OS.contains("aix");

    /** Explicit FPS cap (0 = auto / uncapped). Forces parking even in fullscreen IMMEDIATE mode. */
    public static volatile int TARGET_FPS;

    /** Effective FPS cap, recomputed every iteration on the Main Thread (AppKit must stay on the Main Thread). */
    private static volatile int EFFECTIVE_FPS;

    public static void setTargetFps(int fps) {
        TARGET_FPS = Math.max(0, fps);
    }

    /**
     * Pure park until the deadline. parkNanos is only a hint, so re-park if woken early.
     * Never busy-spins; each iteration sleeps on the OS scheduler.
     */
    private static void parkUntil(long deadline) {
        long now;
        while ((now = java.lang.System.nanoTime()) < deadline) {
            parkNanos(deadline - now);
        }
    }

    /**
     * Resolve the effective FPS cap on the Main Thread.
     * 0 means no cap (busy wait). FIFO is vsync-locked, so it can never exceed the display refresh.
     */
    private static int resolveFps(long pointer) {
        int cap = TARGET_FPS;

        if (IS_MAC) {
            boolean vsyncLocked = vulkan.Vulkan.isVsyncLocked();
            int display = macOSWindow.getDisplayRefreshRate();

            if (cap > 0) {
                // Explicit cap: park regardless; FIFO is still bounded by the display refresh.
                return vsyncLocked ? Math.min(cap, display) : cap;
            }
            if (vsyncLocked) return display; // FIFO: park at the display refresh, can't present faster anyway
            if (!macOSWindow.isFullscreen(pointer)) return display; // windowed IMMEDIATE: WindowServer caps it anyway, park instead of spin
            return 0; // fullscreen IMMEDIATE: busy wait
        }

        return cap; // non-macOS: only an explicit cap parks, otherwise busy wait
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

    public static void run(long pointer, EngineLoop loop) {
        // Shared flag so we only evaluate the heavy FFI shouldClose() on the Main Thread
        final AtomicBoolean isClosed = new AtomicBoolean(false);

        // Resolve the initial cap before the loops spin up (Main Thread only)
        EFFECTIVE_FPS = resolveFps(pointer);

        Thread gameThread = Thread.ofPlatform().name("Anti-Engine-Loop").daemon(false).start(() -> {
            System.out.println("[Game Thread] Booting up loop...");
            long nextFrame = java.lang.System.nanoTime();
            while (!isClosed.get()) {
                input.Key.dispatchEvents(); // Drain DOD queue & trigger OOP callbacks
                input.Mouse.dispatchEvents(); // Same for Mouse
                input.Touch.update(); // Dispatches Touch Events
                loop.tick();

                // Frame pacing: park until the next frame deadline instead of busy-waiting.
                int fps = EFFECTIVE_FPS;
                if (fps > 0) {
                    long budget = 1_000_000_000L / fps;
                    if (nextFrame < java.lang.System.nanoTime()) nextFrame = java.lang.System.nanoTime() + budget;
                    parkUntil(nextFrame);
                    nextFrame += budget;
                }
            }
            System.out.println("[Game Thread] Shutting down...");
        });

        System.out.println("[Main Thread] Pumping window events...");
        long fpsWindowStart = java.lang.System.nanoTime();
        long fpsWindowFrames = 0L;
        long nextPoll = java.lang.System.nanoTime();
        while (!shouldClose(pointer)) {
            while (!OS_NATIVE_MUTEX.compareAndSet(false, true)) {
                Thread.onSpinWait();
            }
            try {
                pollEvents();
                // Recompute the cap each iteration; fullscreen/refresh state must be read on the Main Thread.
                EFFECTIVE_FPS = resolveFps(pointer);
            } finally {
                OS_NATIVE_MUTEX.set(false);
            }

            long now = java.lang.System.nanoTime();
            long presented = vulkan.Renderer.getFramesPresented();
            if (now - fpsWindowStart >= 1_000_000_000L) {
                long elapsed = now - fpsWindowStart;
                long deltaFrames = presented - fpsWindowFrames;
                double fps = deltaFrames * 1_000_000_000.0 / elapsed;
                setTitle(pointer, String.format("Anti Engine | %.1f FPS", fps));
                fpsWindowStart = now;
                fpsWindowFrames = presented;
            }

            int fps = EFFECTIVE_FPS;
            if (fps > 0) {
                long budget = 1_000_000_000L / fps;
                if (nextPoll < now) nextPoll = now + budget;
                parkUntil(nextPoll);
                nextPoll += budget;
            }
        }
        
        // Window was closed; notify the Game Thread to stop
        isClosed.set(true);
        try {
            gameThread.join();
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }
    }
}
