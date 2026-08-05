package window;

import engine.EngineLoop;
import annotation.Draft;

import static java.util.concurrent.locks.LockSupport.*;

/**
 * Static pointer-handler wrapper for Native Windows.
 * Follows the primitive value-class structure and routes pointers to the correct OS backend.
 */
public final class Window {

    private Window() {}

    /** Serializes AppKit events with Metal-backed Vulkan swapchain operations on macOS. */
    public static final java.util.concurrent.atomic.AtomicBoolean OS_NATIVE_MUTEX =
            new java.util.concurrent.atomic.AtomicBoolean(false);

    private static final String OS = System.getProperty("os.name").toLowerCase();
    private static final boolean IS_MAC = OS.contains("mac");
    private static final boolean IS_WIN = OS.contains("win");
    private static final boolean IS_LINUX = OS.contains("nix") || OS.contains("nux") || OS.contains("aix");

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
        final java.util.concurrent.atomic.AtomicBoolean isClosed = new java.util.concurrent.atomic.AtomicBoolean(false);

        Thread gameThread = Thread.ofPlatform().name("Anti-Engine-Loop").daemon(false).start(() -> {
            System.out.println("[Game Thread] Booting up loop...");
            while (!isClosed.get()) {
                input.Key.dispatchEvents(); // Drain DOD queue & trigger OOP callbacks
                input.Mouse.dispatchEvents(); // Same for Mouse
                input.Touch.update(); // Dispatches Touch Events
                loop.tick();
                
                // Throttle Game Thread to exactly 1000 FPS to prevent 100% CPU core burn
                parkNanos(1_000_000L);
            }
            System.out.println("[Game Thread] Shutting down...");
        });

        System.out.println("[Main Thread] Pumping window events...");
        long fpsWindowStart = java.lang.System.nanoTime();
        long fpsWindowFrames = 0L;
        while (!shouldClose(pointer)) {
            while (!OS_NATIVE_MUTEX.compareAndSet(false, true)) {
                Thread.onSpinWait();
            }
            try {
                pollEvents();
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
