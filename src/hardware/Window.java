package hardware;

import annotation.HotCode;

import java.lang.foreign.Arena;
import java.lang.foreign.FunctionDescriptor;
import java.lang.foreign.Linker;
import java.lang.foreign.MemorySegment;
import java.lang.foreign.SymbolLookup;
import java.lang.foreign.ValueLayout;
import java.lang.invoke.MethodHandle;

/**
 * Pure native windowing using Project Panama.
 * Zero external libraries (No GLFW, No LWJGL).
 * Directly communicates with OS Window Managers (Cocoa, Win32).
 */
@HotCode
public class Window {

    // --- FFI Linker ---
    private static final Linker LINKER = Linker.nativeLinker();
    private static final SymbolLookup STDLIB = LINKER.defaultLookup();
    
    // --- Objective-C Runtime Handles (Mac) ---
    private static final MethodHandle OBJC_MSG_SEND;
    private static final MethodHandle OBJC_GET_CLASS;
    private static final MethodHandle SEL_REGISTER_NAME;

    static {
        // Safely attempt to link the macOS Objective-C runtime for the engine
        MemorySegment msgSendSym = STDLIB.find("objc_msgSend").orElse(null);
        MemorySegment getClassSym = STDLIB.find("objc_getClass").orElse(null);
        MemorySegment selRegNameSym = STDLIB.find("sel_registerName").orElse(null);

        if (msgSendSym != null && getClassSym != null && selRegNameSym != null) {
            // (id self, SEL op) -> id
            OBJC_MSG_SEND = LINKER.downcallHandle(msgSendSym, FunctionDescriptor.of(ValueLayout.ADDRESS, ValueLayout.ADDRESS, ValueLayout.ADDRESS));
            // (const char *name) -> Class
            OBJC_GET_CLASS = LINKER.downcallHandle(getClassSym, FunctionDescriptor.of(ValueLayout.ADDRESS, ValueLayout.ADDRESS));
            // (const char *str) -> SEL
            SEL_REGISTER_NAME = LINKER.downcallHandle(selRegNameSym, FunctionDescriptor.of(ValueLayout.ADDRESS, ValueLayout.ADDRESS));
        } else {
            OBJC_MSG_SEND = null;
            OBJC_GET_CLASS = null;
            SEL_REGISTER_NAME = null;
        }
    }

    /**
     * Spawns an edge-to-edge frameless native window.
     * @return A 64-bit raw pointer to the window (NSWindow* on Mac, HWND on Win)
     */
    public static long invoke() {
        if (OBJC_MSG_SEND != null) {
            // TODO: Native Mac implementation
            // 1. Get NSWindow class: objc_getClass("NSWindow")
            // 2. alloc & initWithContentRect
            // 3. Set styleMask to FullSizeContentView (for custom engine UI) | Titled | Closable | Resizable
            // 4. Set titlebarAppearsTransparent = YES to keep native traffic lights over our custom graphics
            return 1L; // Stub pointer for the relational engine
        }
        
        // TODO: Win32 implementation (CreateWindowExA)
        return 0L;
    }

    /**
     * Resizes the native window
     * @param windowPtr The raw 64-bit window address
     */
    public static void setSize(long windowPtr, int width, int height) {
        if (windowPtr == 0L) return;
        
        if (OBJC_MSG_SEND != null) {
            // Mac: objc_msgSend(windowPtr, sel_registerName("setContentSize:"), NSMakeSize(width, height));
        }
        // Win: SetWindowPos((HWND)windowPtr, ...)
    }

    /**
     * Shows the window on the screen
     */
    public static void show(long windowPtr) {
        if (windowPtr == 0L) return;
        
        if (OBJC_MSG_SEND != null) {
            // Mac: objc_msgSend(windowPtr, sel_registerName("makeKeyAndOrderFront:"), null);
        }
        // Win: ShowWindow((HWND)windowPtr, SW_SHOW)
    }

    /**
     * Connects this window to a Metal or Vulkan rendering surface.
     */
    public static long createSurface(long windowPtr) {
        if (windowPtr == 0L) return 0L;
        
        if (OBJC_MSG_SEND != null) {
            // Mac:
            // 1. Get window.contentView
            // 2. Create CAMetalLayer
            // 3. view.setLayer(metalLayer)
            // 4. Return CAMetalLayer pointer for vkCreateMetalSurfaceEXT
            return 2L; 
        }
        
        // Win: Return windowPtr directly for vkCreateWin32SurfaceKHR
        return windowPtr;
    }

    /**
     * Destroys the window and frees resources
     */
    public static void destroy(long windowPtr) {
        if (windowPtr == 0L) return;
        
        if (OBJC_MSG_SEND != null) {
            // Mac: objc_msgSend(windowPtr, sel_registerName("release"));
        }
        // Win: DestroyWindow((HWND)windowPtr)
    }
}
