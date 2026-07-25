package window;

import java.lang.foreign.*;
import java.lang.invoke.MethodHandle;

/**
 * Pure macOS FFM backend for the Window system.
 */
final class macOSWindow {

    // --- FFI Linker ---
    private static final Linker LINKER = Linker.nativeLinker();
    
    // --- Objective-C Runtime Handles (Mac) ---
    private static final SymbolLookup OBJC_LIB;
    private static final MethodHandle OBJC_GET_CLASS;
    private static final MethodHandle SEL_REGISTER_NAME;
    private static final MethodHandle MSG_SEND_PTR;
    private static final MethodHandle MSG_SEND_PTR_PTR;
    private static final MethodHandle MSG_SEND_PTR_SIZE;
    private static final MethodHandle MSG_SEND_VOID;
    private static final MethodHandle MSG_SEND_VOID_PTR;
    private static final MethodHandle MSG_SEND_INT;
    private static final MethodHandle MSG_SEND_BOOL;
    private static final MethodHandle MSG_SEND_BOOL_RET;
    private static final MethodHandle MSG_SEND_INIT_WINDOW;
    private static final MethodHandle MSG_SEND_NEXT_EVENT;
    private static final StructLayout CG_RECT;
    private static final StructLayout CG_SIZE;

    static {
        SymbolLookup objcLib = null;
        MethodHandle getClass = null, selRegName = null, msgSendPtr = null, msgSendPtrPtr = null, msgSendPtrSize = null, msgSendVoid = null, msgSendVoidPtr = null, msgSendInt = null, msgSendBool = null, msgSendBoolRet = null, msgSendInitWindow = null, msgSendNextEvent = null;
        StructLayout cgRect = null, cgSize = null;

        try {
            objcLib = SymbolLookup.libraryLookup("libobjc.A.dylib", Arena.global());
            
            try {
                SymbolLookup.libraryLookup("/System/Library/Frameworks/QuartzCore.framework/QuartzCore", Arena.global());
            } catch (Throwable ignore) {}

            MemorySegment getClassSym = objcLib.find("objc_getClass").orElseThrow();
            MemorySegment selRegNameSym = objcLib.find("sel_registerName").orElseThrow();
            MemorySegment msgSendSym = objcLib.find("objc_msgSend").orElseThrow();

            getClass = LINKER.downcallHandle(getClassSym, FunctionDescriptor.of(ValueLayout.ADDRESS, ValueLayout.ADDRESS));
            selRegName = LINKER.downcallHandle(selRegNameSym, FunctionDescriptor.of(ValueLayout.ADDRESS, ValueLayout.ADDRESS));

            msgSendPtr = LINKER.downcallHandle(msgSendSym, FunctionDescriptor.of(ValueLayout.ADDRESS, ValueLayout.ADDRESS, ValueLayout.ADDRESS));
            msgSendPtrPtr = LINKER.downcallHandle(msgSendSym, FunctionDescriptor.of(ValueLayout.ADDRESS, ValueLayout.ADDRESS, ValueLayout.ADDRESS, ValueLayout.ADDRESS));
            
            cgSize = MemoryLayout.structLayout(
                ValueLayout.JAVA_DOUBLE.withName("width"),
                ValueLayout.JAVA_DOUBLE.withName("height")
            );
            
            msgSendPtrSize = LINKER.downcallHandle(msgSendSym, FunctionDescriptor.ofVoid(ValueLayout.ADDRESS, ValueLayout.ADDRESS, cgSize));

            msgSendVoid = LINKER.downcallHandle(msgSendSym, FunctionDescriptor.ofVoid(ValueLayout.ADDRESS, ValueLayout.ADDRESS));
            msgSendVoidPtr = LINKER.downcallHandle(msgSendSym, FunctionDescriptor.ofVoid(ValueLayout.ADDRESS, ValueLayout.ADDRESS, ValueLayout.ADDRESS));
            msgSendInt = LINKER.downcallHandle(msgSendSym, FunctionDescriptor.ofVoid(ValueLayout.ADDRESS, ValueLayout.ADDRESS, ValueLayout.JAVA_LONG));
            msgSendBool = LINKER.downcallHandle(msgSendSym, FunctionDescriptor.ofVoid(ValueLayout.ADDRESS, ValueLayout.ADDRESS, ValueLayout.JAVA_BYTE));
            msgSendBoolRet = LINKER.downcallHandle(msgSendSym, FunctionDescriptor.of(ValueLayout.JAVA_BYTE, ValueLayout.ADDRESS, ValueLayout.ADDRESS));

            msgSendNextEvent = LINKER.downcallHandle(msgSendSym, FunctionDescriptor.of(
                ValueLayout.ADDRESS,
                ValueLayout.ADDRESS, ValueLayout.ADDRESS,
                ValueLayout.JAVA_LONG, ValueLayout.ADDRESS, ValueLayout.ADDRESS, ValueLayout.JAVA_BYTE
            ));

            cgRect = MemoryLayout.structLayout(
                ValueLayout.JAVA_DOUBLE.withName("x"),
                ValueLayout.JAVA_DOUBLE.withName("y"),
                ValueLayout.JAVA_DOUBLE.withName("width"),
                ValueLayout.JAVA_DOUBLE.withName("height")
            );

            msgSendInitWindow = LINKER.downcallHandle(msgSendSym, FunctionDescriptor.of(
                ValueLayout.ADDRESS,
                ValueLayout.ADDRESS, ValueLayout.ADDRESS,
                cgRect, ValueLayout.JAVA_LONG, ValueLayout.JAVA_LONG, ValueLayout.JAVA_BYTE
            ));
        } catch (Throwable t) {
            t.printStackTrace();
        }
        
        OBJC_LIB = objcLib;
        OBJC_GET_CLASS = getClass;
        SEL_REGISTER_NAME = selRegName;
        MSG_SEND_PTR = msgSendPtr;
        MSG_SEND_PTR_PTR = msgSendPtrPtr;
        MSG_SEND_PTR_SIZE = msgSendPtrSize;
        MSG_SEND_VOID = msgSendVoid;
        MSG_SEND_VOID_PTR = msgSendVoidPtr;
        MSG_SEND_INT = msgSendInt;
        MSG_SEND_BOOL = msgSendBool;
        MSG_SEND_BOOL_RET = msgSendBoolRet;
        MSG_SEND_INIT_WINDOW = msgSendInitWindow;
        MSG_SEND_NEXT_EVENT = msgSendNextEvent;
        CG_RECT = cgRect;
        CG_SIZE = cgSize;
    }

    private static MemorySegment getObjcClass(Arena arena, String name) throws Throwable {
        return (MemorySegment) OBJC_GET_CLASS.invoke(arena.allocateFrom(name));
    }

    private static MemorySegment getSel(Arena arena, String name) throws Throwable {
        return (MemorySegment) SEL_REGISTER_NAME.invoke(arena.allocateFrom(name));
    }

    public static long allocate() {
        if (OBJC_GET_CLASS == null) return 0L;
        try {
            try (Arena arena = Arena.ofConfined()) {
                MemorySegment nsAppClass = getObjcClass(arena, "NSApplication");
                MemorySegment sharedAppSel = getSel(arena, "sharedApplication");
                MemorySegment app = (MemorySegment) MSG_SEND_PTR.invoke(nsAppClass, sharedAppSel);

                MemorySegment setActivationPolicySel = getSel(arena, "setActivationPolicy:");
                MSG_SEND_INT.invoke(app, setActivationPolicySel, 0L);

                MemorySegment nsWindowClass = getObjcClass(arena, "NSWindow");
                MemorySegment allocSel = getSel(arena, "alloc");
                MemorySegment windowAlloc = (MemorySegment) MSG_SEND_PTR.invoke(nsWindowClass, allocSel);

                MemorySegment rect = arena.allocate(CG_RECT);
                rect.set(ValueLayout.JAVA_DOUBLE, 0, 100);
                rect.set(ValueLayout.JAVA_DOUBLE, 8, 100);
                rect.set(ValueLayout.JAVA_DOUBLE, 16, 1280);
                rect.set(ValueLayout.JAVA_DOUBLE, 24, 720);

                // NSWindowStyleMaskTitled (1) | Closable (2) | Miniaturizable (4) | Resizable (8) | FullSizeContentView (32768)
                long styleMask = 1 | 2 | 4 | 8 | 32768;
                long backingStore = 2;

                MemorySegment initWithContentRectSel = getSel(arena, "initWithContentRect:styleMask:backing:defer:");
                MemorySegment window = (MemorySegment) MSG_SEND_INIT_WINDOW.invoke(windowAlloc, initWithContentRectSel, rect, styleMask, backingStore, (byte)0);

                MemorySegment setTitlebarAppearsTransparentSel = getSel(arena, "setTitlebarAppearsTransparent:");
                MSG_SEND_BOOL.invoke(window, setTitlebarAppearsTransparentSel, (byte)1);

                MemorySegment centerSel = getSel(arena, "center");
                MSG_SEND_VOID.invoke(window, centerSel);

                return window.address();
            }
        } catch (Throwable t) {
            t.printStackTrace();
        }
        return 0L;
    }

    public static void setTitle(long pointer, String title) {
        if (pointer == 0L || OBJC_GET_CLASS == null) return;
        try (Arena arena = Arena.ofConfined()) {
            MemorySegment window = MemorySegment.ofAddress(pointer);
            MemorySegment nsStringClass = getObjcClass(arena, "NSString");
            MemorySegment allocSel = getSel(arena, "alloc");
            MemorySegment initWithUTF8StringSel = getSel(arena, "initWithUTF8String:");
            
            MemorySegment strAlloc = (MemorySegment) MSG_SEND_PTR.invoke(nsStringClass, allocSel);
            MemorySegment titleStr = (MemorySegment) MSG_SEND_PTR_PTR.invoke(strAlloc, initWithUTF8StringSel, arena.allocateFrom(title));
            
            MemorySegment setTitleSel = getSel(arena, "setTitle:");
            MSG_SEND_VOID_PTR.invoke(window, setTitleSel, titleStr);
        } catch (Throwable t) {
            t.printStackTrace();
        }
    }

    public static void setSize(long pointer, int width, int height) {
        if (pointer == 0L || OBJC_GET_CLASS == null) return;
        try (Arena arena = Arena.ofConfined()) {
            MemorySegment window = MemorySegment.ofAddress(pointer);
            MemorySegment setContentSizeSel = getSel(arena, "setContentSize:");
            
            MemorySegment size = arena.allocate(CG_SIZE);
            size.set(ValueLayout.JAVA_DOUBLE, 0, width);
            size.set(ValueLayout.JAVA_DOUBLE, 8, height);
            
            MSG_SEND_PTR_SIZE.invoke(window, setContentSizeSel, size);
        } catch (Throwable t) {
            t.printStackTrace();
        }
    }

    public static void show(long pointer) {
        if (pointer == 0L || OBJC_GET_CLASS == null) return;
        try (Arena arena = Arena.ofConfined()) {
            MemorySegment window = MemorySegment.ofAddress(pointer);
            MemorySegment makeKeyAndOrderFrontSel = getSel(arena, "makeKeyAndOrderFront:");
            MSG_SEND_PTR_PTR.invoke(window, makeKeyAndOrderFrontSel, MemorySegment.NULL);
            
            MemorySegment nsAppClass = getObjcClass(arena, "NSApplication");
            MemorySegment sharedAppSel = getSel(arena, "sharedApplication");
            MemorySegment app = (MemorySegment) MSG_SEND_PTR.invoke(nsAppClass, sharedAppSel);
            MemorySegment activateSel = getSel(arena, "activateIgnoringOtherApps:");
            MSG_SEND_BOOL.invoke(app, activateSel, (byte)1);
        } catch (Throwable t) {
            t.printStackTrace();
        }
    }

    public static long createSurface(long pointer) {
        if (pointer == 0L || OBJC_GET_CLASS == null) return 0L;
        try (Arena arena = Arena.ofConfined()) {
            MemorySegment window = MemorySegment.ofAddress(pointer);
            
            MemorySegment contentViewSel = getSel(arena, "contentView");
            MemorySegment view = (MemorySegment) MSG_SEND_PTR.invoke(window, contentViewSel);
            
            MemorySegment caMetalLayerClass = getObjcClass(arena, "CAMetalLayer");
            MemorySegment allocSel = getSel(arena, "alloc");
            MemorySegment initSel = getSel(arena, "init");
            MemorySegment layerAlloc = (MemorySegment) MSG_SEND_PTR.invoke(caMetalLayerClass, allocSel);
            MemorySegment metalLayer = (MemorySegment) MSG_SEND_PTR.invoke(layerAlloc, initSel);
            
            MemorySegment setWantsLayerSel = getSel(arena, "setWantsLayer:");
            MSG_SEND_BOOL.invoke(view, setWantsLayerSel, (byte)1);
            
            MemorySegment setLayerSel = getSel(arena, "setLayer:");
            MSG_SEND_PTR_PTR.invoke(view, setLayerSel, metalLayer);
            
            return metalLayer.address();
        } catch (Throwable t) {
            t.printStackTrace();
        }
        return 0L;
    }

    public static boolean shouldClose(long pointer) {
        if (pointer == 0L || OBJC_GET_CLASS == null) return true;
        try (Arena arena = Arena.ofConfined()) {
            MemorySegment window = MemorySegment.ofAddress(pointer);
            MemorySegment isVisibleSel = getSel(arena, "isVisible");
            byte visible = (byte) MSG_SEND_BOOL_RET.invoke(window, isVisibleSel);
            
            if (visible == 0) {
                MemorySegment isMiniaturizedSel = getSel(arena, "isMiniaturized");
                byte minimized = (byte) MSG_SEND_BOOL_RET.invoke(window, isMiniaturizedSel);
                if (minimized != 0) {
                    return false; 
                }
                return true; 
            }
            return false;
        } catch (Throwable t) {
            t.printStackTrace();
        }
        return false;
    }

    public static void pollEvents() {
        if (OBJC_GET_CLASS == null) return;
        try (Arena arena = Arena.ofConfined()) {
            MemorySegment nsAppClass = getObjcClass(arena, "NSApplication");
            MemorySegment sharedAppSel = getSel(arena, "sharedApplication");
            MemorySegment app = (MemorySegment) MSG_SEND_PTR.invoke(nsAppClass, sharedAppSel);

            MemorySegment nsStringClass = getObjcClass(arena, "NSString");
            MemorySegment allocSel = getSel(arena, "alloc");
            MemorySegment initWithUTF8StringSel = getSel(arena, "initWithUTF8String:");
            
            MemorySegment modeAlloc = (MemorySegment) MSG_SEND_PTR.invoke(nsStringClass, allocSel);
            MemorySegment runLoopMode = (MemorySegment) MSG_SEND_PTR_PTR.invoke(modeAlloc, initWithUTF8StringSel, arena.allocateFrom("kCFRunLoopDefaultMode"));

            MemorySegment nextEventSel = getSel(arena, "nextEventMatchingMask:untilDate:inMode:dequeue:");
            MemorySegment sendEventSel = getSel(arena, "sendEvent:");
            MemorySegment updateWindowsSel = getSel(arena, "updateWindows");

            long NSAnyEventMask = -1L;

            while (true) {
                MemorySegment event = (MemorySegment) MSG_SEND_NEXT_EVENT.invoke(app, nextEventSel, NSAnyEventMask, MemorySegment.NULL, runLoopMode, (byte)1);
                if (event.address() == 0L) break;
                MSG_SEND_VOID_PTR.invoke(app, sendEventSel, event);
            }
            
            MSG_SEND_VOID.invoke(app, updateWindowsSel);
        } catch (Throwable t) {
            t.printStackTrace();
        }
    }

    public static void free(long pointer) {
        if (pointer == 0L || OBJC_GET_CLASS == null) return;
        try (Arena arena = Arena.ofConfined()) {
            MemorySegment window = MemorySegment.ofAddress(pointer);
            MemorySegment closeSel = getSel(arena, "close");
            MSG_SEND_VOID.invoke(window, closeSel);
        } catch (Throwable t) {
            t.printStackTrace();
        }
    }
}
