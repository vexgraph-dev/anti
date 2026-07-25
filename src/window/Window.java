package hardware;

/**
 * Static pointer-handler wrapper for Native Windows.
 * Follows the primitive value-class structure.
 */
public final class Window {

    private Window() {}

    public static long allocate() {
        if (System.getProperty("os.name").toLowerCase().contains("mac")) {
            return macOSWindow.allocate();
        }
        throw new UnsupportedOperationException("Only Mac supported right now!");
    }

    public static void free(long pointer) {
        if (System.getProperty("os.name").toLowerCase().contains("mac")) {
            macOSWindow.free(pointer);
        }
    }

    public static void setSize(long pointer, int width, int height) {
        if (System.getProperty("os.name").toLowerCase().contains("mac")) {
            macOSWindow.setSize(pointer, width, height);
        }
    }

    public static void show(long pointer) {
        if (System.getProperty("os.name").toLowerCase().contains("mac")) {
            macOSWindow.show(pointer);
        }
    }

    public static long createSurface(long pointer) {
        if (System.getProperty("os.name").toLowerCase().contains("mac")) {
            return macOSWindow.createSurface(pointer);
        }
        return 0L;
    }

    public static boolean shouldClose(long pointer) {
        if (System.getProperty("os.name").toLowerCase().contains("mac")) {
            return macOSWindow.shouldClose(pointer);
        }
        return true;
    }

    public static void pollEvents() {
        if (System.getProperty("os.name").toLowerCase().contains("mac")) {
            macOSWindow.pollEvents();
        }
    }
}
