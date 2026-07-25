package window;

import java.lang.foreign.*;

/**
 * Pure Linux/X11 FFM backend for the Window system.
 * (Skeleton ready for implementation)
 */
final class linuxWindow {

    private static final Linker LINKER = Linker.nativeLinker();
    
    // Linux APIs (X11)
    private static final SymbolLookup LIB_X11;

    static {
        SymbolLookup libX11 = null;
        try {
            String os = System.getProperty("os.name").toLowerCase();
            if (os.contains("nix") || os.contains("nux") || os.contains("aix")) {
                libX11 = SymbolLookup.libraryLookup("libX11.so.6", Arena.global());
            }
        } catch (Throwable t) {
            // Ignore if not on Linux
        }
        LIB_X11 = libX11;
    }

    public static long allocate() {
        // TODO: Implement X11 XCreateWindow or Wayland wl_display_connect via FFM
        throw new UnsupportedOperationException("Linux FFM Window not implemented yet");
    }

    public static void setTitle(long pointer, String title) {
        // TODO: Implement X11 XStoreName
    }

    public static void setSize(long pointer, int width, int height) {
        // TODO: Implement X11 XResizeWindow
    }

    public static void show(long pointer) {
        // TODO: Implement X11 XMapWindow
    }

    public static long createSurface(long pointer) {
        // Linux: The X11 Window ID is used for vkCreateXlibSurfaceKHR
        return pointer;
    }

    public static boolean shouldClose(long pointer) {
        // TODO: Implement X11 XCheckTypedEvent for DestroyNotify
        return false;
    }

    public static void pollEvents() {
        // TODO: Implement X11 XPending / XNextEvent loop
    }

    public static void free(long pointer) {
        // TODO: Implement X11 XDestroyWindow
    }
}
