package window;

import java.lang.foreign.*;
import java.lang.invoke.MethodHandle;
import annotation.PlatformExclusive;

/**
 * Pure macOS FFM backend for the Window system.
 */
@PlatformExclusive("Mac")
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
    private static final MethodHandle MSG_SEND_PTR_DOUBLE;
    private static final MethodHandle MSG_SEND_INIT_WINDOW;
    private static final MethodHandle MSG_SEND_NEXT_EVENT;
    private static final MethodHandle MSG_SEND_LONG_RET;
    private static final MethodHandle MSG_SEND_SHORT_RET;
    private static final MethodHandle MSG_SEND_POINT_RET;
    private static final StructLayout CG_RECT;
    private static final StructLayout CG_SIZE;

    static {
        SymbolLookup objcLib = null;
        MethodHandle getClass = null, selRegName = null, msgSendPtr = null, msgSendPtrPtr = null, msgSendPtrSize = null, msgSendVoid = null, msgSendVoidPtr = null, msgSendInt = null, msgSendBool = null, msgSendBoolRet = null, msgSendInitWindow = null, msgSendNextEvent = null, msgSendLongRet = null, msgSendShortRet = null, msgSendPointRet = null, msgSendPtrDouble = null;
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
            msgSendPtrDouble = LINKER.downcallHandle(msgSendSym, FunctionDescriptor.of(ValueLayout.ADDRESS, ValueLayout.ADDRESS, ValueLayout.ADDRESS, ValueLayout.JAVA_DOUBLE));

            msgSendNextEvent = LINKER.downcallHandle(msgSendSym, FunctionDescriptor.of(
                ValueLayout.ADDRESS,
                ValueLayout.ADDRESS, ValueLayout.ADDRESS,
                ValueLayout.JAVA_LONG, ValueLayout.ADDRESS, ValueLayout.ADDRESS, ValueLayout.JAVA_BYTE
            ));
            
            msgSendLongRet = LINKER.downcallHandle(msgSendSym, FunctionDescriptor.of(ValueLayout.JAVA_LONG, ValueLayout.ADDRESS, ValueLayout.ADDRESS));
            msgSendShortRet = LINKER.downcallHandle(msgSendSym, FunctionDescriptor.of(ValueLayout.JAVA_SHORT, ValueLayout.ADDRESS, ValueLayout.ADDRESS));
            msgSendPointRet = LINKER.downcallHandle(msgSendSym, FunctionDescriptor.of(cgSize, ValueLayout.ADDRESS, ValueLayout.ADDRESS));

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
        MSG_SEND_PTR_DOUBLE = msgSendPtrDouble;
        MSG_SEND_INIT_WINDOW = msgSendInitWindow;
        MSG_SEND_NEXT_EVENT = msgSendNextEvent;
        MSG_SEND_LONG_RET = msgSendLongRet;
        MSG_SEND_SHORT_RET = msgSendShortRet;
        MSG_SEND_POINT_RET = msgSendPointRet;
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

                MemorySegment nsProcessInfoClass = getObjcClass(arena, "NSProcessInfo");
                MemorySegment processInfoSel = getSel(arena, "processInfo");
                MemorySegment processInfo = (MemorySegment) MSG_SEND_PTR.invoke(nsProcessInfoClass, processInfoSel);

                MemorySegment nsStringClass = getObjcClass(arena, "NSString");
                MemorySegment allocSelStr = getSel(arena, "alloc");
                MemorySegment initWithUTF8StringSel = getSel(arena, "initWithUTF8String:");
                MemorySegment strAlloc = (MemorySegment) MSG_SEND_PTR.invoke(nsStringClass, allocSelStr);
                MemorySegment nameStr = (MemorySegment) MSG_SEND_PTR_PTR.invoke(strAlloc, initWithUTF8StringSel, arena.allocateFrom("Anti Engine"));

                MemorySegment setProcessNameSel = getSel(arena, "setProcessName:");
                MSG_SEND_VOID_PTR.invoke(processInfo, setProcessNameSel, nameStr);

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

    public static void waitEvents() {
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

            MemorySegment nsDateClass = getObjcClass(arena, "NSDate");
            MemorySegment dateWithTimeIntervalSel = getSel(arena, "dateWithTimeIntervalSinceNow:");

            MemorySegment nextEventSel = getSel(arena, "nextEventMatchingMask:untilDate:inMode:dequeue:");
            MemorySegment sendEventSel = getSel(arena, "sendEvent:");
            MemorySegment updateWindowsSel = getSel(arena, "updateWindows");
            MemorySegment typeSel = getSel(arena, "type");
            MemorySegment keyCodeSel = getSel(arena, "keyCode");

            long NSAnyEventMask = -1L;
            boolean first = true;

            while (true) {
                MemorySegment timeout = MemorySegment.NULL;
                if (first) {
                    timeout = (MemorySegment) MSG_SEND_PTR_DOUBLE.invoke(nsDateClass, dateWithTimeIntervalSel, 0.016);
                }
                
                MemorySegment event = (MemorySegment) MSG_SEND_NEXT_EVENT.invoke(app, nextEventSel, NSAnyEventMask, timeout, runLoopMode, (byte)1);
                if (event.address() == 0L) break;
                first = false;
                
                long eventType = (long) MSG_SEND_LONG_RET.invoke(event, typeSel);
                
                // 10 = KeyDown, 11 = KeyUp
                if (eventType == 10 || eventType == 11) {
                    short macKeyCode = (short) MSG_SEND_SHORT_RET.invoke(event, keyCodeSel);
                    if (macKeyCode >= 0 && macKeyCode < 128) {
                        int stdKey = MAC_KEY_MAP[macKeyCode];
                        if (stdKey != -1) {
                            input.Key.pushEvent(stdKey, eventType == 10 ? 1 : 0, 250_000_000L); // 250ms multi-tap window
                        }
                    }
                } else if (eventType == 1 || eventType == 3 || eventType == 25) { // Mouse Down
                    int button = (eventType == 1) ? input.Mouse.LEFT : ((eventType == 3) ? input.Mouse.RIGHT : -1);
                    if (eventType == 25) {
                        try {
                            long btnNum = (long) MSG_SEND_LONG_RET.invoke(event, getSel(arena, "buttonNumber"));
                            button = (int) btnNum;
                        } catch (Throwable ignore) {}
                    }
                    if (button != -1) input.Mouse.pushButtonEvent(button, 1, 250_000_000L);
                } else if (eventType == 2 || eventType == 4 || eventType == 26) { // Mouse Up
                    int button = (eventType == 2) ? input.Mouse.LEFT : ((eventType == 4) ? input.Mouse.RIGHT : -1);
                    if (eventType == 26) {
                        try {
                            long btnNum = (long) MSG_SEND_LONG_RET.invoke(event, getSel(arena, "buttonNumber"));
                            button = (int) btnNum;
                        } catch (Throwable ignore) {}
                    }
                    if (button != -1) input.Mouse.pushButtonEvent(button, 0, 250_000_000L);
                } else if (eventType == 5 || eventType == 6 || eventType == 7 || eventType == 27) { // Mouse Move/Drag
                    try {
                        MemorySegment locationSel = getSel(arena, "locationInWindow");
                        MemorySegment point = (MemorySegment) MSG_SEND_POINT_RET.invoke(event, locationSel);
                        double x = point.get(ValueLayout.JAVA_DOUBLE, 0);
                        double y = point.get(ValueLayout.JAVA_DOUBLE, 8);
                        input.Mouse.pushMoveEvent(x, y);
                    } catch (Throwable ignore) {}
                }
                
                // For Mouse clicks, we can also extract the coordinate so the state has it
                if (eventType == 1 || eventType == 2 || eventType == 3 || eventType == 4 || eventType == 25 || eventType == 26) {
                    try {
                        MemorySegment locationSel = getSel(arena, "locationInWindow");
                        MemorySegment point = (MemorySegment) MSG_SEND_POINT_RET.invoke(event, locationSel);
                        input.Mouse.pushMoveEvent(point.get(ValueLayout.JAVA_DOUBLE, 0), point.get(ValueLayout.JAVA_DOUBLE, 8));
                    } catch (Throwable ignore) {}
                }

                MSG_SEND_VOID_PTR.invoke(app, sendEventSel, event);
            }
            
            MSG_SEND_VOID.invoke(app, updateWindowsSel);
        } catch (Throwable t) {
            t.printStackTrace();
        }
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
            MemorySegment typeSel = getSel(arena, "type");
            MemorySegment keyCodeSel = getSel(arena, "keyCode");

            long NSAnyEventMask = -1L;

            while (true) {
                MemorySegment event = (MemorySegment) MSG_SEND_NEXT_EVENT.invoke(app, nextEventSel, NSAnyEventMask, MemorySegment.NULL, runLoopMode, (byte)1);
                if (event.address() == 0L) break;
                
                long eventType = (long) MSG_SEND_LONG_RET.invoke(event, typeSel);
                
                // 10 = KeyDown, 11 = KeyUp
                if (eventType == 10 || eventType == 11) {
                    short macKeyCode = (short) MSG_SEND_SHORT_RET.invoke(event, keyCodeSel);
                    if (macKeyCode >= 0 && macKeyCode < 128) {
                        int stdKey = MAC_KEY_MAP[macKeyCode];
                        if (stdKey != -1) {
                            input.Key.pushEvent(stdKey, eventType == 10 ? 1 : 0, 250_000_000L); // 250ms multi-tap window
                        }
                    }
                } else if (eventType == 1 || eventType == 3 || eventType == 25) { // Mouse Down (Left=1, Right=3, Other=25)
                    int button = (eventType == 1) ? input.Mouse.LEFT : ((eventType == 3) ? input.Mouse.RIGHT : -1);
                    if (eventType == 25) {
                        try {
                            long btnNum = (long) MSG_SEND_LONG_RET.invoke(event, getSel(arena, "buttonNumber"));
                            button = (int) btnNum;
                        } catch (Throwable ignore) {}
                    }
                    if (button != -1) input.Mouse.pushButtonEvent(button, 1, 250_000_000L);
                } else if (eventType == 2 || eventType == 4 || eventType == 26) { // Mouse Up (Left=2, Right=4, Other=26)
                    int button = (eventType == 2) ? input.Mouse.LEFT : ((eventType == 4) ? input.Mouse.RIGHT : -1);
                    if (eventType == 26) {
                        try {
                            long btnNum = (long) MSG_SEND_LONG_RET.invoke(event, getSel(arena, "buttonNumber"));
                            button = (int) btnNum;
                        } catch (Throwable ignore) {}
                    }
                    if (button != -1) input.Mouse.pushButtonEvent(button, 0, 250_000_000L);
                } else if (eventType == 5 || eventType == 6 || eventType == 7 || eventType == 27) { // Mouse Move/Drag
                    try {
                        MemorySegment locationSel = getSel(arena, "locationInWindow");
                        MemorySegment point = (MemorySegment) MSG_SEND_POINT_RET.invoke(event, locationSel);
                        double x = point.get(ValueLayout.JAVA_DOUBLE, 0);
                        double y = point.get(ValueLayout.JAVA_DOUBLE, 8);
                        
                        // Convert bottom-left origin to top-left origin if desired, or just pass raw
                        input.Mouse.pushMoveEvent(x, y);
                    } catch (Throwable ignore) {}
                }
                
                // For Mouse clicks, we can also extract the coordinate so the state has it
                if (eventType == 1 || eventType == 2 || eventType == 3 || eventType == 4 || eventType == 25 || eventType == 26) {
                    try {
                        MemorySegment locationSel = getSel(arena, "locationInWindow");
                        MemorySegment point = (MemorySegment) MSG_SEND_POINT_RET.invoke(event, locationSel);
                        input.Mouse.pushMoveEvent(point.get(ValueLayout.JAVA_DOUBLE, 0), point.get(ValueLayout.JAVA_DOUBLE, 8));
                    } catch (Throwable ignore) {}
                }

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
    
    // Translation map for macOS virtual key codes -> cross-platform Key constants
    private static final int[] MAC_KEY_MAP = new int[128];
    static {
        for(int i = 0; i < 128; i++) MAC_KEY_MAP[i] = -1;
        MAC_KEY_MAP[0] = input.Key.A; MAC_KEY_MAP[1] = input.Key.S; MAC_KEY_MAP[2] = input.Key.D;
        MAC_KEY_MAP[3] = input.Key.F; MAC_KEY_MAP[4] = input.Key.H; MAC_KEY_MAP[5] = input.Key.G;
        MAC_KEY_MAP[6] = input.Key.Z; MAC_KEY_MAP[7] = input.Key.X; MAC_KEY_MAP[8] = input.Key.C;
        MAC_KEY_MAP[9] = input.Key.V; MAC_KEY_MAP[11] = input.Key.B; MAC_KEY_MAP[12] = input.Key.Q;
        MAC_KEY_MAP[13] = input.Key.W; MAC_KEY_MAP[14] = input.Key.E; MAC_KEY_MAP[15] = input.Key.R;
        MAC_KEY_MAP[16] = input.Key.Y; MAC_KEY_MAP[17] = input.Key.T; MAC_KEY_MAP[18] = input.Key.NUM_1;
        MAC_KEY_MAP[19] = input.Key.NUM_2; MAC_KEY_MAP[20] = input.Key.NUM_3; MAC_KEY_MAP[21] = input.Key.NUM_4;
        MAC_KEY_MAP[22] = input.Key.NUM_6; MAC_KEY_MAP[23] = input.Key.NUM_5; MAC_KEY_MAP[24] = input.Key.EQUAL;
        MAC_KEY_MAP[25] = input.Key.NUM_9; MAC_KEY_MAP[26] = input.Key.NUM_7; MAC_KEY_MAP[27] = input.Key.MINUS;
        MAC_KEY_MAP[28] = input.Key.NUM_8; MAC_KEY_MAP[29] = input.Key.NUM_0; MAC_KEY_MAP[30] = input.Key.RIGHT_BRACKET;
        MAC_KEY_MAP[31] = input.Key.O; MAC_KEY_MAP[32] = input.Key.U; MAC_KEY_MAP[33] = input.Key.LEFT_BRACKET;
        MAC_KEY_MAP[34] = input.Key.I; MAC_KEY_MAP[35] = input.Key.P; MAC_KEY_MAP[36] = input.Key.ENTER;
        MAC_KEY_MAP[37] = input.Key.L; MAC_KEY_MAP[38] = input.Key.J; MAC_KEY_MAP[39] = input.Key.APOSTROPHE;
        MAC_KEY_MAP[40] = input.Key.K; MAC_KEY_MAP[41] = input.Key.SEMICOLON; MAC_KEY_MAP[42] = input.Key.BACKSLASH;
        MAC_KEY_MAP[43] = input.Key.COMMA; MAC_KEY_MAP[44] = input.Key.SLASH; MAC_KEY_MAP[45] = input.Key.N;
        MAC_KEY_MAP[46] = input.Key.M; MAC_KEY_MAP[47] = input.Key.PERIOD; MAC_KEY_MAP[48] = input.Key.TAB;
        MAC_KEY_MAP[49] = input.Key.SPACE; MAC_KEY_MAP[50] = input.Key.GRAVE_ACCENT; MAC_KEY_MAP[51] = input.Key.BACKSPACE;
        MAC_KEY_MAP[53] = input.Key.ESCAPE; MAC_KEY_MAP[123] = input.Key.LEFT; MAC_KEY_MAP[124] = input.Key.RIGHT;
        MAC_KEY_MAP[125] = input.Key.DOWN; MAC_KEY_MAP[126] = input.Key.UP;
    }
}
