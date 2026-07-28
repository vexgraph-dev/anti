package window;

import java.lang.foreign.*;
import annotation.PlatformExclusive;
import annotation.Draft;

/**
 * Pure Win32 FFM backend for the Window system.
 * (Skeleton ready for implementation)
 */
@PlatformExclusive("Windows")
final class windowsWindow {

    private static final Linker LINKER = Linker.nativeLinker();
    
    // Windows APIs (User32.dll)
    private static final SymbolLookup USER32;

    static {
        SymbolLookup user32 = null;
        try {
            if (System.getProperty("os.name").toLowerCase().contains("win")) {
                user32 = SymbolLookup.libraryLookup("user32.dll", Arena.global());
            }
        } catch (Throwable t) {
            // Ignore if not on Windows
        }
        USER32 = user32;
    }

    public static long allocate(boolean borderless) {
        // TODO: Implement Win32 window creation
        return 0L;
    }

    public static void setTitle(long pointer, String title) {
        // TODO: Implement Win32 SetWindowTextA
    }

    public static void setSize(long pointer, int width, int height) {
        // TODO: Implement Win32 SetWindowPos
    }

    public static void show(long pointer) {
        setVisible(pointer, true);
    }

    @Draft
    public static void setVisible(long pointer, boolean visible) {
        // TODO: ShowWindow(hwnd, SW_SHOW) / ShowWindow(hwnd, SW_HIDE)
    }

    @Draft
    public static void setLocation(long pointer, int x, int y) {
        // TODO: SetWindowPos(hwnd, ...)
    }

    public static long createSurface(long pointer) {
        // Win32: The HWND is typically all Vulkan needs (along with HINSTANCE)
        return pointer; 
    }

    public static boolean shouldClose(long pointer) {
        // TODO: Implement Win32 GetMessage/PeekMessage to check WM_QUIT
        return false;
    }

    public static void pollEvents() {
        // TODO: Implement Win32 PeekMessage, TranslateMessage, DispatchMessage loop
    }

    public static void free(long pointer) {
        // TODO: Implement Win32 DestroyWindow
    }
}
