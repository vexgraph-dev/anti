package window;

import engine.EngineLoop;

/**
 * Static pointer-handler wrapper for Native Windows.
 * Follows the primitive value-class structure and routes pointers to the correct OS backend.
 */
public final class Window {

    private Window() {}

    private static final String OS = System.getProperty("os.name").toLowerCase();
    private static final boolean IS_MAC = OS.contains("mac");
    private static final boolean IS_WIN = OS.contains("win");
    private static final boolean IS_LINUX = OS.contains("nix") || OS.contains("nux") || OS.contains("aix");

    public static long allocate() {
        if (IS_MAC) return macOSWindow.allocate();
        if (IS_WIN) return windowsWindow.allocate();
        if (IS_LINUX) return linuxWindow.allocate();
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

    public static long createSurface(long pointer) {
        if (IS_MAC) return macOSWindow.createSurface(pointer);
        if (IS_WIN) return windowsWindow.createSurface(pointer);
        if (IS_LINUX) return linuxWindow.createSurface(pointer);
        return 0L;
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

    public static void setKeyEvent(long pointer, event.KeyEvent listener) {
        if (pointer == 0L) return;
        input.Key.addKeyEvent(listener);
    }

    public static void run(long pointer, EngineLoop loop) {
        Thread.ofPlatform().name("Anti-Engine-Loop").daemon(false).start(() -> {
            System.out.println("[Game Thread] Booting up loop...");
            while (!shouldClose(pointer)) {
                input.Key.dispatchEvents(); // Drain DOD queue & trigger OOP callbacks
                loop.tick();
            }
            System.out.println("[Game Thread] Shutting down...");
        });

        System.out.println("[Main Thread] Pumping window events...");
        while (!shouldClose(pointer)) {
            pollEvents();
            try {
                Thread.sleep(1); // 1000 hz way
            } catch (InterruptedException e) {
                break;
            }
        }
    }
}
