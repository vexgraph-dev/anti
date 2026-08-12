package window;

import java.lang.foreign.*;
import java.lang.invoke.MethodHandle;
import java.lang.invoke.MethodHandles;
import java.lang.invoke.MethodType;
import annotation.PlatformExclusive;
import annotation.Intention;
import annotation.Citatiom;
import exception.macOSWindowException;
import input.Key;
import input.Mouse;
import input.Touch;

/**
 * Pure macOS FFM backend for the Window system.
 */
// [constraints]
// macOS native window backend. macOS WindowServer composites windowed
// CAMetalLayer presentation and forces vsync to the display refresh rate
// (120Hz on ProMotion), capping windowed FPS, whereas fullscreen presentation
// bypasses the compositor and unlocks the frame rate.

@PlatformExclusive("Mac")
@Intention("[constraints]")
@Citatiom(cite = 4)
final class macOSWindow {

    // --- FFI Linker ---
    private static final Linker LINKER = Linker.nativeLinker();
    
    // --- Objective-C Runtime Handles (Mac) ---
    private static SymbolLookup OBJC_LIB;
    private static MethodHandle OBJC_GET_CLASS;
    private static MethodHandle SEL_REGISTER_NAME;
    private static MethodHandle MSG_SEND_PTR;
    private static MethodHandle MSG_SEND_PTR_PTR;
    private static MethodHandle MSG_SEND_PTR_LONG;
    private static MethodHandle MSG_SEND_PTR_LONG_PTR;
    private static MethodHandle MSG_SEND_PTR_SIZE;
    private static MethodHandle MSG_SEND_VOID;
    private static MethodHandle MSG_SEND_VOID_PTR;
    private static MethodHandle MSG_SEND_INT;
    private static MethodHandle MSG_SEND_BOOL;
    private static MethodHandle MSG_SEND_BOOL_RET;
    private static MethodHandle MSG_SEND_BOOL_RET_LONG;
    private static MethodHandle MSG_SEND_BOOL_RET_PTR_PTR;
    private static MethodHandle MSG_SEND_PTR_DOUBLE;
    private static MethodHandle MSG_SEND_INIT_WINDOW;
    private static MethodHandle MSG_SEND_NEXT_EVENT;
    private static MethodHandle MSG_SEND_LONG_RET;
    private static MethodHandle MSG_SEND_SHORT_RET;
    private static MethodHandle MSG_SEND_POINT_RET;
    private static MethodHandle MSG_SEND_RECT_RET;
    private static MethodHandle MSG_SEND_DOUBLE_RET;
    private static MethodHandle METAL_CREATE_SYSTEM_DEFAULT_DEVICE;
    private static MethodHandle CG_MAIN_DISPLAY_ID;
    private static MethodHandle CG_DISPLAY_PIXELS_WIDE;
    private static MethodHandle CG_DISPLAY_PIXELS_HIGH;
    private static MethodHandle CF_RELEASE;
    private static MethodHandle CG_ASSOCIATE_MOUSE;
    private static MethodHandle CG_WARP_MOUSE_CURSOR;
    private static MethodHandle CG_EVENT_TAP_CREATE;
    private static MethodHandle CG_EVENT_TAP_ENABLE;
    private static MethodHandle CG_EVENT_GET_INTEGER_VALUE_FIELD;
    private static MethodHandle CF_MACH_PORT_CREATE_RUN_LOOP_SOURCE;
    private static MethodHandle CF_RUN_LOOP_GET_CURRENT;
    private static MethodHandle CF_RUN_LOOP_ADD_SOURCE;
    private static MethodHandle CF_RUN_LOOP_RUN;
    private static MethodHandle CF_RUN_LOOP_STOP;
    private static MethodHandle CF_STRING_CREATE_WITH_CSTRING;
    private static StructLayout CG_RECT;
    private static StructLayout CG_SIZE;

    // --- NSWindowStyleMask bits ---
    private static final long STYLE_TITLED            = 1L; // << 0
    private static final long STYLE_CLOSABLE          = 1L << 1;
    private static final long STYLE_MINIATURIZABLE    = 1L << 2;
    private static final long STYLE_RESIZABLE         = 1L << 3;
    private static final long STYLE_FULL_SCREEN       = 1L << 14;
    private static final long STYLE_FULL_SIZE_CONTENT = 1L << 15;

    /** setUndecorated modes. */
    public static final int UNDECORATED_DECORATED  = 0; // Standard title bar: opaque, title visible
    public static final int UNDECORATED_BORDERLESS = 1; // True borderless: no title bar, no traffic lights
    public static final int UNDECORATED_NAKED      = 2; // Hidden title bar, traffic lights retained

    // Translation map for macOS virtual key codes -> cross-platform Key constants
    private static final int[] MAC_KEY_MAP = new int[128];

    // Event pump idle cadence (seconds): bounded blocking wait for AppKit events.
    // Parks Thread 0 at ~60Hz between events but wakes instantly on mouse/key/touch.
    private static final double IDLE_EVENT_TIMEOUT_SECONDS = 0.016;

    static {
        SymbolLookup objcLib;
        MethodHandle getClass, selRegName, msgSendPtr, msgSendPtrPtr, msgSendPtrLong, msgSendPtrLongPtr, msgSendPtrSize, msgSendVoid, msgSendVoidPtr, msgSendInt, msgSendBool, msgSendBoolRet, msgSendBoolLongRet, msgSendBoolRetPtrPtr, msgSendInitWindow, msgSendNextEvent, msgSendLongRet, msgSendShortRet, msgSendPointRet, msgSendPtrDouble, msgSendRectRet, msgSendDoubleRet, metalCreateSystemDefaultDevice;
        StructLayout cgRect, cgSize;

        try {
            objcLib = SymbolLookup.libraryLookup("libobjc.A.dylib", Arena.global());
            
            try {
                SymbolLookup.libraryLookup("/System/Library/Frameworks/AppKit.framework/AppKit", Arena.global());
                SymbolLookup.libraryLookup("/System/Library/Frameworks/QuartzCore.framework/QuartzCore", Arena.global());
                SymbolLookup metalLib = SymbolLookup.libraryLookup("/System/Library/Frameworks/Metal.framework/Metal", Arena.global());
                SymbolLookup coreGraphics = SymbolLookup.libraryLookup("/System/Library/Frameworks/CoreGraphics.framework/CoreGraphics", Arena.global());
                SymbolLookup coreFoundation = SymbolLookup.libraryLookup("/System/Library/Frameworks/CoreFoundation.framework/CoreFoundation", Arena.global());

                CG_MAIN_DISPLAY_ID = LINKER.downcallHandle(coreGraphics.find("CGMainDisplayID").orElseThrow(), FunctionDescriptor.of(ValueLayout.JAVA_INT));
                CG_DISPLAY_PIXELS_WIDE = LINKER.downcallHandle(coreGraphics.find("CGDisplayPixelsWide").orElseThrow(), FunctionDescriptor.of(ValueLayout.JAVA_LONG, ValueLayout.JAVA_INT));
                CG_DISPLAY_PIXELS_HIGH = LINKER.downcallHandle(coreGraphics.find("CGDisplayPixelsHigh").orElseThrow(), FunctionDescriptor.of(ValueLayout.JAVA_LONG, ValueLayout.JAVA_INT));
                StructLayout cgPoint = MemoryLayout.structLayout(
                    ValueLayout.JAVA_DOUBLE.withName("x"),
                    ValueLayout.JAVA_DOUBLE.withName("y")
                );
                CG_ASSOCIATE_MOUSE = LINKER.downcallHandle(coreGraphics.find("CGAssociateMouseAndMouseCursorPosition").orElseThrow(), FunctionDescriptor.ofVoid(ValueLayout.JAVA_BYTE));
                CG_WARP_MOUSE_CURSOR = LINKER.downcallHandle(coreGraphics.find("CGWarpMouseCursorPosition").orElseThrow(), FunctionDescriptor.of(ValueLayout.JAVA_INT, cgPoint));
                CG_EVENT_TAP_CREATE = LINKER.downcallHandle(coreGraphics.find("CGEventTapCreate").orElseThrow(), FunctionDescriptor.of(ValueLayout.ADDRESS, ValueLayout.JAVA_INT, ValueLayout.JAVA_INT, ValueLayout.JAVA_INT, ValueLayout.JAVA_LONG, ValueLayout.ADDRESS, ValueLayout.ADDRESS));
                CG_EVENT_TAP_ENABLE = LINKER.downcallHandle(coreGraphics.find("CGEventTapEnable").orElseThrow(), FunctionDescriptor.ofVoid(ValueLayout.ADDRESS, ValueLayout.JAVA_BYTE));
                CG_EVENT_GET_INTEGER_VALUE_FIELD = LINKER.downcallHandle(coreGraphics.find("CGEventGetIntegerValueField").orElseThrow(), FunctionDescriptor.of(ValueLayout.JAVA_LONG, ValueLayout.ADDRESS, ValueLayout.JAVA_INT));
                CF_MACH_PORT_CREATE_RUN_LOOP_SOURCE = LINKER.downcallHandle(coreFoundation.find("CFMachPortCreateRunLoopSource").orElseThrow(), FunctionDescriptor.of(ValueLayout.ADDRESS, ValueLayout.ADDRESS, ValueLayout.ADDRESS, ValueLayout.JAVA_LONG));
                CF_RUN_LOOP_GET_CURRENT = LINKER.downcallHandle(coreFoundation.find("CFRunLoopGetCurrent").orElseThrow(), FunctionDescriptor.of(ValueLayout.ADDRESS));
                CF_RUN_LOOP_ADD_SOURCE = LINKER.downcallHandle(coreFoundation.find("CFRunLoopAddSource").orElseThrow(), FunctionDescriptor.ofVoid(ValueLayout.ADDRESS, ValueLayout.ADDRESS, ValueLayout.ADDRESS));
                CF_RUN_LOOP_RUN = LINKER.downcallHandle(coreFoundation.find("CFRunLoopRun").orElseThrow(), FunctionDescriptor.ofVoid());
                CF_RUN_LOOP_STOP = LINKER.downcallHandle(coreFoundation.find("CFRunLoopStop").orElseThrow(), FunctionDescriptor.ofVoid(ValueLayout.ADDRESS));
                CF_STRING_CREATE_WITH_CSTRING = LINKER.downcallHandle(coreFoundation.find("CFStringCreateWithCString").orElseThrow(), FunctionDescriptor.of(ValueLayout.ADDRESS, ValueLayout.ADDRESS, ValueLayout.ADDRESS, ValueLayout.JAVA_INT));
                CF_RELEASE = LINKER.downcallHandle(coreFoundation.find("CFRelease").orElseThrow(), FunctionDescriptor.ofVoid(ValueLayout.ADDRESS));
                metalCreateSystemDefaultDevice = LINKER.downcallHandle(
                    metalLib.find("MTLCreateSystemDefaultDevice").orElseThrow(),
                    FunctionDescriptor.of(ValueLayout.ADDRESS)
                );
            } catch (Throwable t) {
                throw new macOSWindowException("CRITICAL: macOSWindow FFM Exception", t);
            }

            MemorySegment getClassSym = objcLib.find("objc_getClass").orElseThrow();
            MemorySegment selRegNameSym = objcLib.find("sel_registerName").orElseThrow();
            MemorySegment msgSendSym = objcLib.find("objc_msgSend").orElseThrow();

            getClass = LINKER.downcallHandle(getClassSym, FunctionDescriptor.of(ValueLayout.ADDRESS, ValueLayout.ADDRESS));
            selRegName = LINKER.downcallHandle(selRegNameSym, FunctionDescriptor.of(ValueLayout.ADDRESS, ValueLayout.ADDRESS));

            msgSendPtr = LINKER.downcallHandle(msgSendSym, FunctionDescriptor.of(ValueLayout.ADDRESS, ValueLayout.ADDRESS, ValueLayout.ADDRESS));
            msgSendPtrPtr = LINKER.downcallHandle(msgSendSym, FunctionDescriptor.of(ValueLayout.ADDRESS, ValueLayout.ADDRESS, ValueLayout.ADDRESS, ValueLayout.ADDRESS));
            
            msgSendPtrLong = LINKER.downcallHandle(msgSendSym, FunctionDescriptor.of(ValueLayout.ADDRESS, ValueLayout.ADDRESS, ValueLayout.ADDRESS, ValueLayout.JAVA_LONG));
            msgSendPtrLongPtr = LINKER.downcallHandle(msgSendSym, FunctionDescriptor.of(ValueLayout.ADDRESS, ValueLayout.ADDRESS, ValueLayout.ADDRESS, ValueLayout.JAVA_LONG, ValueLayout.ADDRESS));
            
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
            msgSendBoolLongRet = LINKER.downcallHandle(msgSendSym, FunctionDescriptor.of(ValueLayout.JAVA_BYTE, ValueLayout.ADDRESS, ValueLayout.ADDRESS, ValueLayout.JAVA_LONG));
            msgSendBoolRetPtrPtr = LINKER.downcallHandle(msgSendSym, FunctionDescriptor.of(ValueLayout.JAVA_BYTE, ValueLayout.ADDRESS, ValueLayout.ADDRESS, ValueLayout.ADDRESS, ValueLayout.ADDRESS));
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
            msgSendRectRet = LINKER.downcallHandle(msgSendSym, FunctionDescriptor.of(cgRect, ValueLayout.ADDRESS, ValueLayout.ADDRESS));
            msgSendDoubleRet = LINKER.downcallHandle(msgSendSym, FunctionDescriptor.of(ValueLayout.JAVA_DOUBLE, ValueLayout.ADDRESS, ValueLayout.ADDRESS));

            msgSendInitWindow = LINKER.downcallHandle(msgSendSym, FunctionDescriptor.of(
                ValueLayout.ADDRESS,
                ValueLayout.ADDRESS, ValueLayout.ADDRESS,
                cgRect, ValueLayout.JAVA_LONG, ValueLayout.JAVA_LONG, ValueLayout.JAVA_BYTE
            ));
        } catch (Throwable t) {
            throw new macOSWindowException("CRITICAL: macOSWindow FFM Exception", t);
        }
        
        OBJC_LIB = objcLib;
        OBJC_GET_CLASS = getClass;
        SEL_REGISTER_NAME = selRegName;
        MSG_SEND_PTR = msgSendPtr;
        MSG_SEND_PTR_PTR = msgSendPtrPtr;
        MSG_SEND_PTR_LONG = msgSendPtrLong;
        MSG_SEND_PTR_LONG_PTR = msgSendPtrLongPtr;
        MSG_SEND_PTR_SIZE = msgSendPtrSize;
        MSG_SEND_VOID = msgSendVoid;
        MSG_SEND_VOID_PTR = msgSendVoidPtr;
        MSG_SEND_INT = msgSendInt;
        MSG_SEND_BOOL = msgSendBool;
        MSG_SEND_BOOL_RET = msgSendBoolRet;
        MSG_SEND_BOOL_RET_LONG = msgSendBoolLongRet;
        MSG_SEND_BOOL_RET_PTR_PTR = msgSendBoolRetPtrPtr;
        MSG_SEND_PTR_DOUBLE = msgSendPtrDouble;
        MSG_SEND_INIT_WINDOW = msgSendInitWindow;
        MSG_SEND_NEXT_EVENT = msgSendNextEvent;
        MSG_SEND_LONG_RET = msgSendLongRet;
        MSG_SEND_SHORT_RET = msgSendShortRet;
        MSG_SEND_POINT_RET = msgSendPointRet;
        MSG_SEND_RECT_RET = msgSendRectRet;
        MSG_SEND_DOUBLE_RET = msgSendDoubleRet;
        METAL_CREATE_SYSTEM_DEFAULT_DEVICE = metalCreateSystemDefaultDevice;
        CG_RECT = cgRect;
        CG_SIZE = cgSize;

        for(int i = 0; i < 128; i++)
            MAC_KEY_MAP[i] = -1;

        MAC_KEY_MAP[0] = Key.A; MAC_KEY_MAP[1] = Key.S; MAC_KEY_MAP[2] = Key.D;
        MAC_KEY_MAP[3] = Key.F; MAC_KEY_MAP[4] = Key.H; MAC_KEY_MAP[5] = Key.G;
        MAC_KEY_MAP[6] = Key.Z; MAC_KEY_MAP[7] = Key.X; MAC_KEY_MAP[8] = Key.C;
        MAC_KEY_MAP[9] = Key.V; MAC_KEY_MAP[11] = Key.B; MAC_KEY_MAP[12] = Key.Q;
        MAC_KEY_MAP[13] = Key.W; MAC_KEY_MAP[14] = Key.E; MAC_KEY_MAP[15] = Key.R;
        MAC_KEY_MAP[16] = Key.Y; MAC_KEY_MAP[17] = Key.T; MAC_KEY_MAP[18] = Key.NUM_1;
        MAC_KEY_MAP[19] = Key.NUM_2; MAC_KEY_MAP[20] = Key.NUM_3; MAC_KEY_MAP[21] = Key.NUM_4;
        MAC_KEY_MAP[22] = Key.NUM_6; MAC_KEY_MAP[23] = Key.NUM_5; MAC_KEY_MAP[24] = Key.EQUAL;
        MAC_KEY_MAP[25] = Key.NUM_9; MAC_KEY_MAP[26] = Key.NUM_7; MAC_KEY_MAP[27] = Key.MINUS;
        MAC_KEY_MAP[28] = Key.NUM_8; MAC_KEY_MAP[29] = Key.NUM_0; MAC_KEY_MAP[30] = Key.RIGHT_BRACKET;
        MAC_KEY_MAP[31] = Key.O; MAC_KEY_MAP[32] = Key.U; MAC_KEY_MAP[33] = Key.LEFT_BRACKET;
        MAC_KEY_MAP[34] = Key.I; MAC_KEY_MAP[35] = Key.P; MAC_KEY_MAP[36] = Key.ENTER;
        MAC_KEY_MAP[37] = Key.L; MAC_KEY_MAP[38] = Key.J; MAC_KEY_MAP[39] = Key.APOSTROPHE;
        MAC_KEY_MAP[40] = Key.K; MAC_KEY_MAP[41] = Key.SEMICOLON; MAC_KEY_MAP[42] = Key.BACKSLASH;
        MAC_KEY_MAP[43] = Key.COMMA; MAC_KEY_MAP[44] = Key.SLASH; MAC_KEY_MAP[45] = Key.N;
        MAC_KEY_MAP[46] = Key.M; MAC_KEY_MAP[47] = Key.PERIOD; MAC_KEY_MAP[48] = Key.TAB;
        MAC_KEY_MAP[49] = Key.SPACE; MAC_KEY_MAP[50] = Key.GRAVE_ACCENT; MAC_KEY_MAP[51] = Key.BACKSPACE;
        MAC_KEY_MAP[53] = Key.ESCAPE; MAC_KEY_MAP[123] = Key.LEFT; MAC_KEY_MAP[124] = Key.RIGHT;
        MAC_KEY_MAP[125] = Key.DOWN; MAC_KEY_MAP[126] = Key.UP;

        // macOS F-key virtual key codes (physical F1-F12, not the Fn-doubled media keys).
        MAC_KEY_MAP[122] = Key.F1; MAC_KEY_MAP[120] = Key.F2; MAC_KEY_MAP[99] = Key.F3;
        MAC_KEY_MAP[118] = Key.F4; MAC_KEY_MAP[96] = Key.F5; MAC_KEY_MAP[97] = Key.F6;
        MAC_KEY_MAP[98] = Key.F7; MAC_KEY_MAP[100] = Key.F8; MAC_KEY_MAP[101] = Key.F9;
        MAC_KEY_MAP[109] = Key.F10; MAC_KEY_MAP[103] = Key.F11; MAC_KEY_MAP[111] = Key.F12;
    }

    private static MemorySegment getObjcClass(Arena arena, String name) throws Throwable {
        return (MemorySegment) OBJC_GET_CLASS.invoke(arena.allocateFrom(name));
    }

    private static MemorySegment getSel(Arena arena, String name) throws Throwable {
        return (MemorySegment) SEL_REGISTER_NAME.invoke(arena.allocateFrom(name));
    }

    static boolean isMetalDeviceAvailable() {
        if (METAL_CREATE_SYSTEM_DEFAULT_DEVICE == null) return false;
        try {
            MemorySegment device = (MemorySegment) METAL_CREATE_SYSTEM_DEFAULT_DEVICE.invoke();
            return device != null && device.address() != 0L;
        } catch (Throwable t) {
            return false;
        }
    }

    public static long allocate(int width, int height) {
        if (OBJC_GET_CLASS == null) return 0L;
        try {
            try (Arena arena = Arena.ofConfined()) {
                MemorySegment nsAppClass = getObjcClass(arena, "NSApplication");
                MemorySegment sharedAppSel = getSel(arena, "sharedApplication");
                MemorySegment app = (MemorySegment) MSG_SEND_PTR.invoke(nsAppClass, sharedAppSel);

                MemorySegment setActivationPolicySel = getSel(arena, "setActivationPolicy:");
                MSG_SEND_INT.invoke(app, setActivationPolicySel, 0L);
                
                MemorySegment finishLaunchingSel = getSel(arena, "finishLaunching");
                MSG_SEND_VOID.invoke(app, finishLaunchingSel);

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
                rect.set(ValueLayout.JAVA_DOUBLE, 16, width);
                rect.set(ValueLayout.JAVA_DOUBLE, 24, height);

                long styleMask = STYLE_TITLED | STYLE_CLOSABLE | STYLE_MINIATURIZABLE | STYLE_RESIZABLE;
                
                long backingStore = 2;

                MemorySegment initWithContentRectSel = getSel(arena, "initWithContentRect:styleMask:backing:defer:");
                MemorySegment window = (MemorySegment) MSG_SEND_INIT_WINDOW.invoke(windowAlloc, initWithContentRectSel, rect, styleMask, backingStore, (byte)0);

                // NSWindowCollectionBehaviorFullScreenPrimary = 1 << 7: the green zoom button
                // enters native fullscreen, keeping it in sync with toggleFullscreen.
                MemorySegment setCollectionBehaviorSel = getSel(arena, "setCollectionBehavior:");
                MSG_SEND_INT.invoke(window, setCollectionBehaviorSel, 128L);

                MemorySegment centerSel = getSel(arena, "center");
                MSG_SEND_VOID.invoke(window, centerSel);
                
                // Enable trackpad touch events on the window's content view
                MemorySegment contentView = (MemorySegment) MSG_SEND_PTR.invoke(window, getSel(arena, "contentView"));
                MemorySegment setAcceptsTouchEventsSel = getSel(arena, "setAcceptsTouchEvents:");
                MSG_SEND_BOOL.invoke(contentView, setAcceptsTouchEventsSel, (byte)1);

                // Explicitly allow indirect (trackpad) touch event types (NSTouchTypeMaskIndirect = 2)
                MemorySegment setAllowedTouchTypesSel = getSel(arena, "setAllowedTouchTypes:");
                MSG_SEND_INT.invoke(contentView, setAllowedTouchTypesSel, 2L);

                return window.address();
            }
        } catch (Throwable t) {
            throw new macOSWindowException("CRITICAL: macOSWindow FFM Exception", t);
        }
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

            // macOS 15+ re-reveals the native title view whenever the title string
            // changes, even when titleVisibility is hidden. Re-apply the hidden state
            // for FullSizeContentView (NAKED/borderless) windows, mirroring the
            // Ghostty didSet guard.
            long mask = (long) MSG_SEND_LONG_RET.invoke(window, getSel(arena, "styleMask"));
            if ((mask & STYLE_FULL_SIZE_CONTENT) != 0) {
                MemorySegment setTitlebarAppearsTransparentSel = getSel(arena, "setTitlebarAppearsTransparent:");
                MSG_SEND_BOOL.invoke(window, setTitlebarAppearsTransparentSel, (byte)1);
                MemorySegment setTitleVisibilitySel = getSel(arena, "setTitleVisibility:");
                MSG_SEND_INT.invoke(window, setTitleVisibilitySel, 1L); // NSWindowTitleHidden
            }
        } catch (Throwable t) {
            throw new macOSWindowException("CRITICAL: macOSWindow FFM Exception", t);
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
            throw new macOSWindowException("CRITICAL: macOSWindow FFM Exception", t);
        }
    }

    public static void show(long pointer) {
        setVisible(pointer, true);
    }

    /**
     * Modern app activation. activateIgnoringOtherApps: is deprecated and unreliable
     * on recent macOS (leaves traffic lights greyed = window never becomes key).
     * NSApplicationActivateIgnoringOtherApps = 1&lt;&lt;1, NSApplicationActivateAllWindows = 1&lt;&lt;0.
     */
    private static void activateApp() {
        try (Arena arena = Arena.ofConfined()) {
            MemorySegment nsRunningAppClass = getObjcClass(arena, "NSRunningApplication");
            MemorySegment currentApplicationSel = getSel(arena, "currentApplication");
            MemorySegment runningApp = (MemorySegment) MSG_SEND_PTR.invoke(nsRunningAppClass, currentApplicationSel);
            if (runningApp == null || runningApp.address() == 0L) return;
            MemorySegment activateWithOptionsSel = getSel(arena, "activateWithOptions:");
            MSG_SEND_BOOL_RET_LONG.invoke(runningApp, activateWithOptionsSel, 3L);
        } catch (Throwable t) {
            throw new macOSWindowException("CRITICAL: macOSWindow FFM Exception", t);
        }
    }

    public static void setVisible(long pointer, boolean visible) {
        if (pointer == 0L || OBJC_GET_CLASS == null) return;
        try (Arena arena = Arena.ofConfined()) {
            MemorySegment window = MemorySegment.ofAddress(pointer);
            if (visible) {
                activateApp(); // before makeKeyAndOrderFront so the key window sticks
                MemorySegment makeKeyAndOrderFrontSel = getSel(arena, "makeKeyAndOrderFront:");
                MSG_SEND_PTR_PTR.invoke(window, makeKeyAndOrderFrontSel, MemorySegment.NULL);
            } else {
                MemorySegment orderOutSel = getSel(arena, "orderOut:");
                MSG_SEND_PTR_PTR.invoke(window, orderOutSel, MemorySegment.NULL);
            }
        } catch (Throwable t) {
            throw new macOSWindowException("CRITICAL: macOSWindow FFM Exception", t);
        }
    }

    public static void setLocation(long pointer, int x, int y) {
        if (pointer == 0L || OBJC_GET_CLASS == null) return;
        try (Arena arena = Arena.ofConfined()) {
            MemorySegment window = MemorySegment.ofAddress(pointer);
            
            // Get screen height to invert Y axis to top-left origin
            MemorySegment nsScreenClass = getObjcClass(arena, "NSScreen");
            MemorySegment mainScreenSel = getSel(arena, "mainScreen");
            MemorySegment mainScreen = (MemorySegment) MSG_SEND_PTR.invoke(nsScreenClass, mainScreenSel);
            MemorySegment screenRect = (MemorySegment) MSG_SEND_RECT_RET.invoke(arena, mainScreen, getSel(arena, "frame"));
            double screenHeight = screenRect.get(ValueLayout.JAVA_DOUBLE, 24);
            
            MemorySegment setFrameTopLeftPointSel = getSel(arena, "setFrameTopLeftPoint:");
            
            MemorySegment point = arena.allocate(CG_SIZE); // Reusing 2-double layout
            point.set(ValueLayout.JAVA_DOUBLE, 0, x);
            point.set(ValueLayout.JAVA_DOUBLE, 8, screenHeight - y);
            
            MSG_SEND_PTR_SIZE.invoke(window, setFrameTopLeftPointSel, point);
        } catch (Throwable t) {
            throw new macOSWindowException("CRITICAL: macOSWindow FFM Exception", t);
        }
    }

    public static void setDRM(long pointer, boolean enabled) {
        if (pointer == 0L || OBJC_GET_CLASS == null) return;
        try (Arena arena = Arena.ofConfined()) {
            MemorySegment window = MemorySegment.ofAddress(pointer);
            MemorySegment setSharingTypeSel = getSel(arena, "setSharingType:");
            // NSWindowSharingNone = 0, NSWindowSharingReadOnly = 1
            long sharingType = enabled ? 0L : 1L;
            MSG_SEND_INT.invoke(window, setSharingTypeSel, sharingType);
        } catch (Throwable t) {
            throw new macOSWindowException("CRITICAL: macOSWindow FFM Exception", t);
        }
    }

    public static long createSurface(long pointer) {
        if (pointer == 0L || OBJC_GET_CLASS == null) return 0L;
        try (Arena arena = Arena.ofConfined()) {
            MemorySegment window = MemorySegment.ofAddress(pointer);
            
            MemorySegment view = (MemorySegment) MSG_SEND_PTR.invoke(window, getSel(arena, "contentView"));
            
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
            throw new macOSWindowException("CRITICAL: macOSWindow FFM Exception", t);
        }
    }

    public static void setFullscreen(long pointer, boolean fullscreen) {
        if (pointer == 0L || OBJC_GET_CLASS == null) return;
        try (Arena arena = Arena.ofConfined()) {
            MemorySegment window = MemorySegment.ofAddress(pointer);
            long currentStyle = (long) MSG_SEND_LONG_RET.invoke(window, getSel(arena, "styleMask"));
            boolean isCurrentlyFullscreen = (currentStyle & STYLE_FULL_SCREEN) != 0;
            
            if (fullscreen != isCurrentlyFullscreen) {
                MemorySegment toggleFullScreenSel = getSel(arena, "toggleFullScreen:");
                MSG_SEND_VOID_PTR.invoke(window, toggleFullScreenSel, MemorySegment.NULL);
            }
        } catch (Throwable t) {
            throw new macOSWindowException("CRITICAL: macOSWindow FFM Exception", t);
        }
    }

    public static void center(long pointer) {
        if (pointer == 0L || OBJC_GET_CLASS == null) return;
        try (Arena arena = Arena.ofConfined()) {
            MemorySegment window = MemorySegment.ofAddress(pointer);
            MemorySegment centerSel = getSel(arena, "center");
            MSG_SEND_VOID.invoke(window, centerSel);
        } catch (Throwable t) {
            throw new macOSWindowException("CRITICAL: macOSWindow FFM Exception", t);
        }
    }

    private static boolean cursorVisible = true;
    public static void setCursorVisible(boolean visible) {
        if (OBJC_GET_CLASS == null) return;
        if (cursorVisible == visible) return;
        try (Arena arena = Arena.ofConfined()) {
            MemorySegment nsCursorClass = getObjcClass(arena, "NSCursor");
            if (visible) {
                MSG_SEND_VOID.invoke(nsCursorClass, getSel(arena, "unhide"));
            } else {
                MSG_SEND_VOID.invoke(nsCursorClass, getSel(arena, "hide"));
            }
            cursorVisible = visible;
        } catch (Throwable t) {
            throw new macOSWindowException("CRITICAL: macOSWindow FFM Exception", t);
        }
    }

    // Cursor lock state: when locked the OS cursor is hidden and warped back to the
    // window centre during the event pump, while raw mouse deltas keep flowing to
    // input.Mouse. FPS-style "relative mouse" mode.
    private static long lockWindowPtr = 0L;
    private static int lockCenterX = 0, lockCenterY = 0;

    /**
     * Locks (or unlocks) the cursor to the centre of the given window.
     * While locked, AppKit hides the cursor and each pump pass re-wraps it to the
     * window centre, so the pointer never leaves the middle of the content area.
     */
    public static void setCursorLock(long pointer, boolean lock) {
        if (pointer == 0L || OBJC_GET_CLASS == null) return;
        try (Arena arena = Arena.ofConfined()) {
            MemorySegment window = MemorySegment.ofAddress(pointer);
            if (lock) {
                if (CG_ASSOCIATE_MOUSE != null) {
                    // Decouple the cursor from mouse movement so deltas arrive without
                    // the pointer drifting away between warp passes.
                    CG_ASSOCIATE_MOUSE.invoke((byte) 0);
                }

                // Window frame is already in global screen coordinates (points,
                // bottom-left origin); CGWarpMouseCursorPosition wants the same global
                // space. Center of the window is what the lock keeps alive.
                MemorySegment rect = (MemorySegment) MSG_SEND_RECT_RET.invoke(arena, window, getSel(arena, "frame"));
                double x = rect.get(ValueLayout.JAVA_DOUBLE, 0);
                double y = rect.get(ValueLayout.JAVA_DOUBLE, 8);
                double w = rect.get(ValueLayout.JAVA_DOUBLE, 16);
                double h = rect.get(ValueLayout.JAVA_DOUBLE, 24);
                lockCenterX = (int) (x + w / 2.0);
                lockCenterY = (int) (y + h / 2.0);

                MemorySegment lockPoint = arena.allocate(CG_SIZE);
                lockPoint.set(ValueLayout.JAVA_DOUBLE, 0, lockCenterX);
                lockPoint.set(ValueLayout.JAVA_DOUBLE, 8, lockCenterY);
                if (CG_WARP_MOUSE_CURSOR != null) {
                    CG_WARP_MOUSE_CURSOR.invoke(lockPoint);
                }
                lockWindowPtr = pointer;
                setCursorVisible(false);
            } else {
                if (CG_ASSOCIATE_MOUSE != null) {
                    CG_ASSOCIATE_MOUSE.invoke((byte) 1);
                }
                lockWindowPtr = 0L;
                setCursorVisible(true);
            }
        } catch (Throwable t) {
            throw new macOSWindowException("CRITICAL: macOSWindow cursor lock Exception", t);
        }
    }

    /**
     * Re-centres the cursor during the pump if cursor lock is active. Called from
     * waitEvents/pollEvents so the warp registers before the next event loop exits.
     */
    public static void recenterIfLocked() {
        if (lockWindowPtr == 0L || CG_WARP_MOUSE_CURSOR == null) return;
        try (Arena arena = Arena.ofConfined()) {
            MemorySegment lockPoint = arena.allocate(CG_SIZE);
            lockPoint.set(ValueLayout.JAVA_DOUBLE, 0, lockCenterX);
            lockPoint.set(ValueLayout.JAVA_DOUBLE, 8, lockCenterY);
            CG_WARP_MOUSE_CURSOR.invoke(lockPoint);
        } catch (Throwable t) {
            throw new macOSWindowException("CRITICAL: macOSWindow cursor recenter Exception", t);
        }
    }

    // ============================================================================
    // System-wide key telemetry (CGEventTap, listen-only)
    // Captures key presses from ANY application while the engine app is running.
    // Requires the macOS "Input Monitoring" privacy permission (first run prompt).
    // ============================================================================

    // kCGEventTapOptionListenOnly = 1, kCGHeadInsertEventTap = 0
    private static final int TAP_OPTION_LISTEN_ONLY = 1;
    private static final int TAP_PLACE_HEAD = 0;
    // kCGEventKeyDown = 10, kCGEventKeyUp = 11
    private static final long TAP_KEY_MASK = (1L << 10) | (1L << 11);
    // kCGKeyboardEventKeycode = 9
    private static final int FIELD_KEYCODE = 9;

    private static volatile MemorySegment keyTapPort;
    private static volatile MemorySegment keyTapSource;
    private static volatile MemorySegment keyTapRunLoop;
    private static volatile MemorySegment keyTapMode;
    private static volatile Thread keyTapThread;
    private static volatile MemorySegment keyTapStub;
    private static volatile boolean keyTapRunning;

    /**
     * Starts the system-wide key telemetry tap. Listen-only: it observes key
     * events from every application but never alters them. Events feed the
     * off-heap telemetry.KeyLog. Starts a private CFRunLoop thread so Thread 0
     * (the AppKit pump) never blocks on the tap.
     */
    public static void startKeyTelemetry() {
        if (CG_EVENT_TAP_CREATE == null || keyTapRunning) return;
        try {
            MethodHandle callbackHandle = MethodHandles.lookup().findStatic(
                    macOSWindow.class, "keyTapCallback",
                    MethodType.methodType(MemorySegment.class, MemorySegment.class, int.class, MemorySegment.class, MemorySegment.class));
            FunctionDescriptor callbackDesc = FunctionDescriptor.of(
                    ValueLayout.ADDRESS, // returns CGEventRef
                    ValueLayout.ADDRESS, // CFMachPortRef proxy
                    ValueLayout.JAVA_INT, // CGEventType
                    ValueLayout.ADDRESS, // CGEventRef
                    ValueLayout.ADDRESS  // void *userInfo
            );
            keyTapStub = LINKER.upcallStub(callbackHandle, callbackDesc, Arena.global());

            MemorySegment tap = (MemorySegment) CG_EVENT_TAP_CREATE.invoke(
                    TAP_PLACE_HEAD, TAP_PLACE_HEAD, TAP_OPTION_LISTEN_ONLY, TAP_KEY_MASK, keyTapStub, MemorySegment.NULL);
            if (tap == null || tap.address() == 0L) {
                System.out.println("[telemetry] CGEventTapCreate failed (check Input Monitoring permission under System Settings -> Privacy & Security -> Input Monitoring).");
                return;
            }
            keyTapPort = tap;

            MemorySegment source = (MemorySegment) CF_MACH_PORT_CREATE_RUN_LOOP_SOURCE.invoke(MemorySegment.NULL, tap, 0L);
            keyTapSource = source;

            // CFRunLoopAddSource needs a real CFStringRef mode; NULL crashes with
            // "CFHash() called with NULL" inside __CFRunLoopCopyMode.
            MemorySegment modeCStr = Arena.global().allocateFrom("kCFRunLoopDefaultMode");
            MemorySegment mode = (MemorySegment) CF_STRING_CREATE_WITH_CSTRING.invoke(
                    MemorySegment.NULL, modeCStr, 0x08000100);
            keyTapMode = mode;

            // Private run loop: drains the tap without touching Thread 0.
            keyTapThread = Thread.ofPlatform().name("Anti-KeyTap").daemon(true).start(() -> {
                try {
                    MemorySegment rl = (MemorySegment) CF_RUN_LOOP_GET_CURRENT.invoke();
                    keyTapRunLoop = rl;
                    CF_RUN_LOOP_ADD_SOURCE.invoke(rl, source, mode);
                    CG_EVENT_TAP_ENABLE.invoke(tap, (byte) 1);
                    CF_RUN_LOOP_RUN.invoke();
                } catch (Throwable t) {
                    System.out.println("[telemetry] key tap run loop failed: " + t);
                }
            });

            keyTapRunning = true;
            System.out.println("[telemetry] System-wide key recording active (listen-only, Input Monitoring permission). Press keys anywhere.");
        } catch (Throwable t) {
            throw new macOSWindowException("CRITICAL: macOSWindow key telemetry Exception", t);
        }
    }

    /** Stops the system-wide key telemetry tap (called at teardown). */
    public static void stopKeyTelemetry() {
        if (!keyTapRunning) return;
        try {
            if (CG_EVENT_TAP_ENABLE != null && keyTapPort != null) {
                CG_EVENT_TAP_ENABLE.invoke(keyTapPort, (byte) 0);
            }
            if (CF_RUN_LOOP_STOP != null && keyTapRunLoop != null) {
                CF_RUN_LOOP_STOP.invoke(keyTapRunLoop);
            }
            if (keyTapThread != null) {
                keyTapThread.join(500L);
            }
            if (CF_RELEASE != null) {
                if (keyTapSource != null) CF_RELEASE.invoke(keyTapSource);
                if (keyTapPort != null) CF_RELEASE.invoke(keyTapPort);
                if (keyTapMode != null) CF_RELEASE.invoke(keyTapMode);
            }
        } catch (Throwable t) {
            throw new macOSWindowException("CRITICAL: macOSWindow key telemetry stop Exception", t);
        } finally {
            keyTapRunning = false;
            keyTapPort = null;
            keyTapSource = null;
            keyTapRunLoop = null;
            keyTapMode = null;
            keyTapThread = null;
        }
    }

    public static boolean isKeyTelemetryActive() {
        return keyTapRunning;
    }

    /** CGEventTap callback: fired off-thread for every matching system event. */
    private static MemorySegment keyTapCallback(MemorySegment proxy, int type, MemorySegment event, MemorySegment userInfo) {
        if (event == null || event.address() == 0L) return proxy;
        try {
            long keycode = (long) CG_EVENT_GET_INTEGER_VALUE_FIELD.invoke(event, FIELD_KEYCODE);
            boolean down = (type == 10);
            telemetry.KeyLog.record((int) keycode, down ? 1 : 0);
        } catch (Throwable ignored) {
            // Never let a tap callback exception reach the native run loop.
        }
        // Listen-only: return the event unchanged so other apps are unaffected.
        return event;
    }

    public static void setClipboardString(String text) {
        if (OBJC_GET_CLASS == null || text == null) return;
        try (Arena arena = Arena.ofConfined()) {
            MemorySegment nsPasteboardClass = getObjcClass(arena, "NSPasteboard");
            MemorySegment pasteboard = (MemorySegment) MSG_SEND_PTR.invoke(nsPasteboardClass, getSel(arena, "generalPasteboard"));
            MSG_SEND_LONG_RET.invoke(pasteboard, getSel(arena, "clearContents"));
            
            MemorySegment nsStringClass = getObjcClass(arena, "NSString");
            MemorySegment strAlloc = (MemorySegment) MSG_SEND_PTR.invoke(nsStringClass, getSel(arena, "alloc"));
            MemorySegment nameStr = (MemorySegment) MSG_SEND_PTR_PTR.invoke(strAlloc, getSel(arena, "initWithUTF8String:"), arena.allocateFrom(text));
            
            MemorySegment typeStr = arena.allocateFrom("public.utf8-plain-text");
            MSG_SEND_BOOL_RET_PTR_PTR.invoke(pasteboard, getSel(arena, "setString:forType:"), nameStr, typeStr);
        } catch (Throwable t) {
            throw new macOSWindowException("CRITICAL: macOSWindow FFM Exception", t);
        }
    }

    public static String getClipboardString() {
        if (OBJC_GET_CLASS == null) return "";
        try (Arena arena = Arena.ofConfined()) {
            MemorySegment nsPasteboardClass = getObjcClass(arena, "NSPasteboard");
            MemorySegment pasteboard = (MemorySegment) MSG_SEND_PTR.invoke(nsPasteboardClass, getSel(arena, "generalPasteboard"));
            
            MemorySegment typeStr = arena.allocateFrom("public.utf8-plain-text");
            MemorySegment nsStr = (MemorySegment) MSG_SEND_PTR_PTR.invoke(pasteboard, getSel(arena, "stringForType:"), typeStr);
            
            if (nsStr != null && nsStr.address() != 0) {
                MemorySegment utf8 = (MemorySegment) MSG_SEND_PTR.invoke(nsStr, getSel(arena, "UTF8String"));
                if (utf8 != null && utf8.address() != 0) {
                    return utf8.getString(0);
                }
            }
            return "";
        } catch (Throwable t) {
            return "";
        }
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
                return minimized == 0;
            }
            return false;
        } catch (Throwable t) {
            throw new macOSWindowException("CRITICAL: macOSWindow FFM Exception", t);
        }
    }

    public static boolean isMinimized(long pointer) {
        if (pointer == 0L || OBJC_GET_CLASS == null) return false;
        try (Arena arena = Arena.ofConfined()) {
            MemorySegment window = MemorySegment.ofAddress(pointer);
            MemorySegment isMiniaturizedSel = getSel(arena, "isMiniaturized");
            byte minimized = (byte) MSG_SEND_BOOL_RET.invoke(window, isMiniaturizedSel);
            return minimized != 0;
        } catch (Throwable t) {
            throw new macOSWindowException("CRITICAL: macOSWindow FFM Exception", t);
        }
    }

    public static boolean isFullscreen(long pointer) {
        if (pointer == 0L || OBJC_GET_CLASS == null) return false;
        try (Arena arena = Arena.ofConfined()) {
            MemorySegment window = MemorySegment.ofAddress(pointer);
            // NSWindowStyleMaskFullScreen = 1 << 14
            MemorySegment styleMaskSel = getSel(arena, "styleMask");
            long mask = (long) MSG_SEND_LONG_RET.invoke(window, styleMaskSel);
            return (mask & (1L << 14)) != 0;
        } catch (Throwable t) {
            throw new macOSWindowException("CRITICAL: macOSWindow FFM Exception", t);
        }
    }

    public static void toggleFullscreen(long pointer) {
        if (pointer == 0L || OBJC_GET_CLASS == null) return;
        try (Arena arena = Arena.ofConfined()) {
            MemorySegment window = MemorySegment.ofAddress(pointer);
            MemorySegment toggleFullScreenSel = getSel(arena, "toggleFullScreen:");
            MSG_SEND_VOID_PTR.invoke(window, toggleFullScreenSel, MemorySegment.NULL);
        } catch (Throwable t) {
            throw new macOSWindowException("CRITICAL: macOSWindow FFM Exception", t);
        }
    }

    // --- Style-mask state API (NSWindowStyleMask) ---

    private static long getStyleMask(long pointer) {
        try (Arena arena = Arena.ofConfined()) {
            MemorySegment window = MemorySegment.ofAddress(pointer);
            return (long) MSG_SEND_LONG_RET.invoke(window, getSel(arena, "styleMask"));
        } catch (Throwable t) {
            throw new macOSWindowException("CRITICAL: macOSWindow FFM Exception", t);
        }
    }

    /**
     * Single mask-rewrite path for all capability toggles. While native fullscreen
     * AppKit owns the mask (the FullScreen bit can only change inside a transition),
     * so style mutations are skipped then — mirroring the Ghostty guard. When not
     * fullscreen the bit is simply never present, so it is never forced via setStyleMask:.
     */
    private static void updateStyleMask(long pointer, long addBits, long clearBits) {
        if (pointer == 0L || OBJC_GET_CLASS == null) return;
        try (Arena arena = Arena.ofConfined()) {
            MemorySegment window = MemorySegment.ofAddress(pointer);
            long mask = getStyleMask(pointer);
            if ((mask & STYLE_FULL_SCREEN) != 0) return; // AppKit owns the mask in fullscreen
            long next = (mask & ~clearBits) | addBits;
            MemorySegment setStyleMaskSel = getSel(arena, "setStyleMask:");
            MSG_SEND_INT.invoke(window, setStyleMaskSel, next);
        } catch (Throwable t) {
            throw new macOSWindowException("CRITICAL: macOSWindow FFM Exception", t);
        }
    }

    private static boolean hasStyleBit(long pointer, long bit) {
        return (getStyleMask(pointer) & bit) != 0;
    }

    public static boolean isResizable(long pointer)       { return hasStyleBit(pointer, STYLE_RESIZABLE); }
    public static boolean isClosable(long pointer)        { return hasStyleBit(pointer, STYLE_CLOSABLE); }
    public static boolean isMiniaturizable(long pointer)  { return hasStyleBit(pointer, STYLE_MINIATURIZABLE); }

    public static void setResizable(long pointer, boolean resizable) {
        updateStyleMask(pointer, resizable ? STYLE_RESIZABLE : 0L, resizable ? 0L : STYLE_RESIZABLE);
    }

    public static void setClosable(long pointer, boolean closable) {
        updateStyleMask(pointer, closable ? STYLE_CLOSABLE : 0L, closable ? 0L : STYLE_CLOSABLE);
    }

    public static void setMiniaturizable(long pointer, boolean miniaturizable) {
        updateStyleMask(pointer, miniaturizable ? STYLE_MINIATURIZABLE : 0L, miniaturizable ? 0L : STYLE_MINIATURIZABLE);
    }

    /**
     * Green traffic light (zoom/fullscreen entry). It is gated by
     * NSWindowCollectionBehaviorFullScreenPrimary (1 &lt;&lt; 7), set in allocate(); clear it
     * before show() to remove the button. Call on Thread 0 before the window shows.
     */
    public static void setFullscreenButton(long pointer, boolean enabled) {
        if (pointer == 0L || OBJC_GET_CLASS == null) return;
        try (Arena arena = Arena.ofConfined()) {
            MemorySegment window = MemorySegment.ofAddress(pointer);
            long behavior = (long) MSG_SEND_LONG_RET.invoke(window, getSel(arena, "collectionBehavior"));
            if (enabled) behavior |= 128L;
            else behavior &= ~128L;
            MemorySegment setCollectionBehaviorSel = getSel(arena, "setCollectionBehavior:");
            MSG_SEND_INT.invoke(window, setCollectionBehaviorSel, behavior);
        } catch (Throwable t) {
            throw new macOSWindowException("CRITICAL: macOSWindow FFM Exception", t);
        }
    }

    /**
     * Switch window chrome at runtime.
     * UNDECORATED_DECORATED: standard opaque title bar, title visible.
     * UNDECORATED_BORDERLESS: no title bar and no traffic lights (styleMask = 0).
     * UNDECORATED_NAKED: transparent title bar with hidden title; the traffic
     * lights stay because the Closable/Miniaturizable bits are retained.
     */
    public static void setUndecorated(long pointer, int mode) {
        if (pointer == 0L || OBJC_GET_CLASS == null) return;
        try (Arena arena = Arena.ofConfined()) {
            MemorySegment window = MemorySegment.ofAddress(pointer);
            long mask = getStyleMask(pointer);
            if ((mask & STYLE_FULL_SCREEN) != 0) return; // AppKit owns the mask in fullscreen

            long next = switch(mode) {
                case UNDECORATED_BORDERLESS -> 0L;
                case UNDECORATED_NAKED ->
                        STYLE_TITLED | STYLE_CLOSABLE | STYLE_MINIATURIZABLE | STYLE_RESIZABLE | STYLE_FULL_SIZE_CONTENT;
                default -> // UNDECORATED_DECORATED
                        STYLE_TITLED | STYLE_CLOSABLE | STYLE_MINIATURIZABLE | STYLE_RESIZABLE;
            };

            MemorySegment setStyleMaskSel = getSel(arena, "setStyleMask:");
            MSG_SEND_INT.invoke(window, setStyleMaskSel, next);

            boolean transparent = mode == UNDECORATED_NAKED;
            MemorySegment setTitlebarAppearsTransparentSel = getSel(arena, "setTitlebarAppearsTransparent:");
            MSG_SEND_BOOL.invoke(window, setTitlebarAppearsTransparentSel, (byte) (transparent ? 1 : 0));

            MemorySegment setTitleVisibilitySel = getSel(arena, "setTitleVisibility:");
            MSG_SEND_INT.invoke(window, setTitleVisibilitySel, transparent ? 1L : 0L); // NSWindowTitleHidden / Visible
        } catch (Throwable t) {
            throw new macOSWindowException("CRITICAL: macOSWindow FFM Exception", t);
        }
    }

    /** Hard minimum content size for resizable windows. */
    public static void setMinSize(long pointer, int width, int height) {
        setSizeSelector(pointer, "setMinSize:", width, height);
    }

    /** Hard maximum content size for resizable windows. */
    public static void setMaxSize(long pointer, int width, int height) {
        setSizeSelector(pointer, "setMaxSize:", width, height);
    }

    private static void setSizeSelector(long pointer, String selector, int width, int height) {
        if (pointer == 0L || OBJC_GET_CLASS == null) return;
        try (Arena arena = Arena.ofConfined()) {
            MemorySegment window = MemorySegment.ofAddress(pointer);
            MemorySegment size = arena.allocate(CG_SIZE);
            size.set(ValueLayout.JAVA_DOUBLE, 0, width);
            size.set(ValueLayout.JAVA_DOUBLE, 8, height);
            MSG_SEND_PTR_SIZE.invoke(window, getSel(arena, selector), size);
        } catch (Throwable t) {
            throw new macOSWindowException("CRITICAL: macOSWindow FFM Exception", t);
        }
    }

    public static long getMinSize(long pointer) {
        return getSizeSelector(pointer, "minSize");
    }

    public static long getMaxSize(long pointer) {
        return getSizeSelector(pointer, "maxSize");
    }

    private static long getSizeSelector(long pointer, String selector) {
        if (pointer == 0L || OBJC_GET_CLASS == null) return 0L;
        try (Arena arena = Arena.ofConfined()) {
            MemorySegment window = MemorySegment.ofAddress(pointer);
            MemorySegment size = (MemorySegment) MSG_SEND_POINT_RET.invoke(arena, window, getSel(arena, selector));
            int w = (int) Math.round(size.get(ValueLayout.JAVA_DOUBLE, 0));
            int h = (int) Math.round(size.get(ValueLayout.JAVA_DOUBLE, 8));
            return ((long) w << 32) | (h & 0xFFFFFFFFL);
        } catch (Throwable t) {
            throw new macOSWindowException("CRITICAL: macOSWindow FFM Exception", t);
        }
    }

    /** Sets CAMetalLayer displaySyncEnabled (YES = vsync, NO = uncapped presentation). */
    public static void setDisplaySyncEnabled(long layerPointer, boolean enabled) {
        if (layerPointer == 0L || OBJC_GET_CLASS == null) return;
        try (Arena arena = Arena.ofConfined()) {
            MemorySegment layer = MemorySegment.ofAddress(layerPointer);
            MemorySegment setDisplaySyncSel = getSel(arena, "setDisplaySyncEnabled:");
            MSG_SEND_BOOL.invoke(layer, setDisplaySyncSel, (byte) (enabled ? 1 : 0));
        } catch (Throwable t) {
            throw new macOSWindowException("CRITICAL: macOSWindow FFM Exception", t);
        }
    }

    /** Content view size in backing pixels, packed as (width << 32) | height. 0 if unavailable. */
    public static long getContentSize(long pointer) {
        if (pointer == 0L || OBJC_GET_CLASS == null) return 0L;
        try (Arena arena = Arena.ofConfined()) {
            MemorySegment window = MemorySegment.ofAddress(pointer);

            MemorySegment backingScaleFactorSel = getSel(arena, "backingScaleFactor");
            double scale = (double) MSG_SEND_DOUBLE_RET.invoke(window, backingScaleFactorSel);
            if (scale <= 0.0) scale = 1.0;

            long nativeScreen = getScreenBackingSize();
            if (nativeScreen != 0L) {
                int nativeW = (int) (nativeScreen >>> 32);
                int nativeH = (int) nativeScreen;
                MemorySegment screen = (MemorySegment) MSG_SEND_PTR.invoke(window, getSel(arena, "screen"));
                if (screen != null && screen.address() != 0L) {
                    MemorySegment screenRect = (MemorySegment) MSG_SEND_RECT_RET.invoke(arena, screen, getSel(arena, "frame"));
                    double screenPtsW = screenRect.get(ValueLayout.JAVA_DOUBLE, 16);
                    double screenPtsH = screenRect.get(ValueLayout.JAVA_DOUBLE, 24);
                    if (screenPtsW > 0 && screenPtsH > 0) {
                        double virtualW = screenPtsW * scale;
                        double virtualH = screenPtsH * scale;
                        if (virtualW > nativeW || virtualH > nativeH) {
                            double minRatio = Math.min(nativeW / virtualW, nativeH / virtualH);
                            scale *= minRatio;
                        }
                    }
                }
            }

            MemorySegment view = (MemorySegment) MSG_SEND_PTR.invoke(window, getSel(arena, "contentView"));
            if (view == null || view.address() == 0L) return 0L;

            MemorySegment rect = (MemorySegment) MSG_SEND_RECT_RET.invoke(arena, view, getSel(arena, "frame"));
            int w = (int) Math.round(rect.get(ValueLayout.JAVA_DOUBLE, 16) * scale);
            int h = (int) Math.round(rect.get(ValueLayout.JAVA_DOUBLE, 24) * scale);
            if (w <= 0 || h <= 0) return 0L;
            return ((long) w << 32) | (h & 0xFFFFFFFFL);
        } catch (Throwable t) {
            throw new macOSWindowException("CRITICAL: macOSWindow FFM Exception", t);
        }
    }

    /** Main screen current physical hardware pixels, packed (width << 32) | height. 0 if unavailable. */
    @annotation.Intention("Reads the display's CURRENT pixel dimensions via CGDisplayPixelsWide/High — "
            + "NOT the maximum-capable mode. The old path enumerated every CGDisplayMode and took the max, "
            + "which overshoots on scaled Retina panels (3274x2048 on a 2408x1506 MacBook) and bloated the "
            + "offscreen render targets. §12.4 known bug, fixed.")
    public static long getScreenBackingSize() {
        if (CG_MAIN_DISPLAY_ID == null || CG_DISPLAY_PIXELS_WIDE == null || CG_DISPLAY_PIXELS_HIGH == null) return 0L;
        try {
            int mainDisplayId = (int) CG_MAIN_DISPLAY_ID.invoke();
            long w = (long) CG_DISPLAY_PIXELS_WIDE.invoke(mainDisplayId);
            long h = (long) CG_DISPLAY_PIXELS_HIGH.invoke(mainDisplayId);
            if (w > 0 && h > 0) {
                return (w << 32) | (h & 0xFFFFFFFFL);
            }
            return 0L;
        } catch (Throwable t) {
            throw new macOSWindowException("CRITICAL: macOSWindow FFM Exception", t);
        }
    }

    public static int getDisplayRefreshRate() {
        if (OBJC_GET_CLASS == null) return 60;
        try (Arena arena = Arena.ofConfined()) {
            MemorySegment nsScreenClass = getObjcClass(arena, "NSScreen");
            MemorySegment mainScreenSel = getSel(arena, "mainScreen");
            MemorySegment mainScreen = (MemorySegment) MSG_SEND_PTR.invoke(nsScreenClass, mainScreenSel);
            if (mainScreen == null || mainScreen.address() == 0L) return 60;
            MemorySegment maxFpsSel = getSel(arena, "maximumFramesPerSecond");
            long rate = (long) MSG_SEND_LONG_RET.invoke(mainScreen, maxFpsSel);
            return rate > 0 ? (int) rate : 60;
        } catch (Throwable t) {
            return 60;
        }
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
            MemorySegment scrollingDeltaXSel = getSel(arena, "scrollingDeltaX");
            MemorySegment scrollingDeltaYSel = getSel(arena, "scrollingDeltaY");
            MemorySegment charactersSel = getSel(arena, "characters");
            MemorySegment magnificationSel = getSel(arena, "magnification");
            MemorySegment utf8StringSel = getSel(arena, "UTF8String");

            // Touch selectors
            MemorySegment touchesMatchingPhaseSel = getSel(arena, "touchesMatchingPhase:inView:");
            MemorySegment countSel = getSel(arena, "count");
            MemorySegment allObjectsSel = getSel(arena, "allObjects");
            MemorySegment objectAtIndexSel = getSel(arena, "objectAtIndex:");
            MemorySegment identitySel = getSel(arena, "identity");
            MemorySegment phaseSel = getSel(arena, "phase");
            MemorySegment normalizedPositionSel = getSel(arena, "normalizedPosition");
            MemorySegment isRestingSel = getSel(arena, "isResting");

            // Selectors used per-event; hoisted out of the hot loop so no segment
            // is allocated while draining a burst of input events.
            MemorySegment buttonNumberSel = getSel(arena, "buttonNumber");
            MemorySegment locationInWindowSel = getSel(arena, "locationInWindow");
            MemorySegment windowSel = getSel(arena, "window");
            MemorySegment contentViewSel = getSel(arena, "contentView");
            MemorySegment frameSel = getSel(arena, "frame");

            long NSAnyEventMask = -1L;

            while (true) {
                MemorySegment timeout = (MemorySegment) MSG_SEND_PTR_DOUBLE.invoke(nsDateClass, dateWithTimeIntervalSel, IDLE_EVENT_TIMEOUT_SECONDS);

                MemorySegment event = (MemorySegment) MSG_SEND_NEXT_EVENT.invoke(app, nextEventSel, NSAnyEventMask, timeout, runLoopMode, (byte)1);
                if (event.address() == 0L) {
                    recenterIfLocked();
                    break;
                }
                
                long eventType = (long) MSG_SEND_LONG_RET.invoke(event, typeSel);
                
                // 10 = KeyDown, 11 = KeyUp
                if (eventType == 10 || eventType == 11) {
                    short macKeyCode = (short) MSG_SEND_SHORT_RET.invoke(event, keyCodeSel);
                    if (macKeyCode >= 0 && macKeyCode < 128) {
                        int stdKey = MAC_KEY_MAP[macKeyCode];
                        if (stdKey != -1) {
                            Key.pushEvent(stdKey, eventType == 10 ? 1 : 0, 250_000_000L); // 250ms multi-tap window
                        }
                        if (eventType == 10) {
                            MemorySegment nsString = (MemorySegment) MSG_SEND_PTR.invoke(event, charactersSel);
                            if (nsString != null && !nsString.equals(MemorySegment.NULL)) {
                                MemorySegment cStr = (MemorySegment) MSG_SEND_PTR.invoke(nsString, utf8StringSel);
                                if (cStr != null && !cStr.equals(MemorySegment.NULL)) {
                                    byte b0 = cStr.reinterpret(1).get(ValueLayout.JAVA_BYTE, 0);
                                    if (b0 > 0) Key.pushCharEvent((char) b0);
                                }
                            }
                        }
                    }
                } else if (eventType == 22) {
                    double dx = (double) MSG_SEND_DOUBLE_RET.invoke(event, scrollingDeltaXSel);
                    double dy = (double) MSG_SEND_DOUBLE_RET.invoke(event, scrollingDeltaYSel);
                    Mouse.pushScrollEvent(dx, dy);
                } else if (eventType == 30) {
                    double magnification = (double) MSG_SEND_DOUBLE_RET.invoke(event, magnificationSel);
                    Mouse.pushZoomEvent(magnification);
                } else if (eventType == 1 || eventType == 3 || eventType == 25) { // Mouse Down
                    int button = (eventType == 1) ? Mouse.LEFT : ((eventType == 3) ? Mouse.RIGHT : -1);
                    if (eventType == 25) {
                        try {
                            long btnNum = (long) MSG_SEND_LONG_RET.invoke(event, buttonNumberSel);
                            button = (int) btnNum;
                        } catch (Throwable t) {
                            throw new macOSWindowException("CRITICAL: macOSWindow FFM Exception", t);
                        }
                    }
                    if (button != -1) Mouse.pushButtonEvent(button, 1, 250_000_000L);
                } else if (eventType == 2 || eventType == 4 || eventType == 26) { // Mouse Up
                    int button = (eventType == 2) ? Mouse.LEFT : ((eventType == 4) ? Mouse.RIGHT : -1);
                    if (eventType == 26) {
                        try {
                            long btnNum = (long) MSG_SEND_LONG_RET.invoke(event, buttonNumberSel);
                            button = (int) btnNum;
                        } catch (Throwable t) {
                            throw new macOSWindowException("CRITICAL: macOSWindow FFM Exception", t);
                        }
                    }
                    if (button != -1) Mouse.pushButtonEvent(button, 0, 250_000_000L);
                } else if (eventType == 5 || eventType == 6 || eventType == 7 || eventType == 27) { // Mouse Move/Drag
                    if (lockWindowPtr != 0L) {
                        // Cursor locked: AppKit reports a constant location with actual
                        // deltas. Feed the relative movement so cameras track direction.
                        try {
                            double ddx = (double) MSG_SEND_DOUBLE_RET.invoke(event, getSel(arena, "deltaX"));
                            double ddy = (double) MSG_SEND_DOUBLE_RET.invoke(event, getSel(arena, "deltaY"));
                            Mouse.pushMoveDeltaEvent(ddx, ddy);
                        } catch (Throwable t) {
                            throw new macOSWindowException("CRITICAL: macOSWindow FFM Exception", t);
                        }
                        continue;
                    }
                    try {
                        MemorySegment point = (MemorySegment) MSG_SEND_POINT_RET.invoke(arena, event, locationInWindowSel);
                        double x = point.get(ValueLayout.JAVA_DOUBLE, 0);
                        double y = point.get(ValueLayout.JAVA_DOUBLE, 8);
                        
                        MemorySegment eventWindow = (MemorySegment) MSG_SEND_PTR.invoke(event, windowSel);
                        if (eventWindow.address() != 0L) {
                            MemorySegment contentView = (MemorySegment) MSG_SEND_PTR.invoke(eventWindow, contentViewSel);
                            if (contentView.address() != 0L) {
                                MemorySegment rect = (MemorySegment) MSG_SEND_RECT_RET.invoke(arena, contentView, frameSel);
                                double height = rect.get(ValueLayout.JAVA_DOUBLE, 24);
                                y = height - y;
                            }
                        }
                        
                        if (eventType == 5) {
                            Mouse.pushMoveEvent(x, y);
                        } else {
                            int button;

                            // different event types
                            if (eventType == 6)
                                button = Mouse.LEFT;
                            else if (eventType == 7)
                                button = Mouse.RIGHT;
                            else // defauls to 27, unless will be added smth, will be added an arg
                            {
                                long btn = (long) MSG_SEND_LONG_RET.invoke(event, buttonNumberSel);
                                button = (int) btn;
                            }

                            Mouse.pushDragEvent(button, x, y);
                        }
                    } catch (Throwable t) {
                        throw new macOSWindowException("CRITICAL: macOSWindow FFM Exception", t);
                    }
                } else if (eventType == 29 || eventType == 19 || eventType == 20) { // Touch events
                    try {
                        MemorySegment eventWindow = (MemorySegment) MSG_SEND_PTR.invoke(event, windowSel);
                        if (eventWindow.address() != 0L) {
                            MemorySegment contentView = (MemorySegment) MSG_SEND_PTR.invoke(eventWindow, contentViewSel);
                            if (contentView.address() != 0L) {
                                MemorySegment touchesSet = (MemorySegment) MSG_SEND_PTR_LONG_PTR.invoke(event, touchesMatchingPhaseSel, -1L, contentView);
                                if (touchesSet != null && touchesSet.address() != 0L) {
                                    long count = (long) MSG_SEND_LONG_RET.invoke(touchesSet, countSel);
                                    if (count > 0) {
                                        MemorySegment allObjs = (MemorySegment) MSG_SEND_PTR.invoke(touchesSet, allObjectsSel);
                                        for (int j = 0; j < count; j++) {
                                            MemorySegment touch = (MemorySegment) MSG_SEND_PTR_LONG.invoke(allObjs, objectAtIndexSel, (long)j);
                                            MemorySegment touchIdObj = (MemorySegment) MSG_SEND_PTR.invoke(touch, identitySel);
                                            long touchId = touchIdObj.address();
                                            long touchPhase = (long) MSG_SEND_LONG_RET.invoke(touch, phaseSel);
                                            
                                            MemorySegment pt = (MemorySegment) MSG_SEND_POINT_RET.invoke(arena, touch, normalizedPositionSel);
                                            double normX = pt.get(ValueLayout.JAVA_DOUBLE, 0);
                                            double normY = pt.get(ValueLayout.JAVA_DOUBLE, 8);
                                            
                                            byte isResting = (byte) MSG_SEND_BOOL_RET.invoke(touch, isRestingSel);
                                            
                                            MemorySegment rect = (MemorySegment) MSG_SEND_RECT_RET.invoke(arena, contentView, frameSel);
                                            double winW = rect.get(ValueLayout.JAVA_DOUBLE, 16);
                                            double winH = rect.get(ValueLayout.JAVA_DOUBLE, 24);
                                            
                                            double posX = normX * winW;
                                            double posY = (1.0 - normY) * winH;

                                            int action = getTouchAction(touchPhase);

                                            double pressure = isResting == 1 ? 0.2 : 0.8;
                                            
                                            Touch.pushTouchEvent((int)(touchId & 0x7FFFFFFF) % 10, action, posX, posY, pressure, 250_000_000L);
                                        }
                                    }
                                }
                            }
                        }
                    } catch (Throwable ignored) {}
                }
                
                // For Mouse clicks, we can also extract the coordinate so the state has it
                if (eventType == 1 || eventType == 2 || eventType == 3 || eventType == 4 || eventType == 25 || eventType == 26) {
                    try {
                        MemorySegment point = (MemorySegment) MSG_SEND_POINT_RET.invoke(arena, event, locationInWindowSel);
                        double x = point.get(ValueLayout.JAVA_DOUBLE, 0);
                        double y = point.get(ValueLayout.JAVA_DOUBLE, 8);
                        
                        MemorySegment eventWindow = (MemorySegment) MSG_SEND_PTR.invoke(event, windowSel);
                        if (eventWindow.address() != 0L) {
                            MemorySegment contentView = (MemorySegment) MSG_SEND_PTR.invoke(eventWindow, contentViewSel);
                            if (contentView.address() != 0L) {
                                MemorySegment rect = (MemorySegment) MSG_SEND_RECT_RET.invoke(arena, contentView, frameSel);
                                double height = rect.get(ValueLayout.JAVA_DOUBLE, 24);
                                y = height - y;
                            }
                        }
                        
                        Mouse.pushMoveEvent(x, y);
                    } catch (Throwable t) {
                        throw new macOSWindowException("CRITICAL: macOSWindow FFM Exception", t);
                    }
                }

                MSG_SEND_VOID_PTR.invoke(app, sendEventSel, event);
            }
            
            MSG_SEND_VOID.invoke(app, updateWindowsSel);
        } catch (Throwable t) {
            throw new macOSWindowException("CRITICAL: macOSWindow FFM Exception", t);
        }
    }

    private static int getTouchAction(long touchPhase)
    {
        int action = Touch.CANCEL;
        if (touchPhase == 1) action = Touch.DOWN;      // Began
        else if (touchPhase == 2 || touchPhase == 4) action = Touch.MOVE; // Moved/Stationary
        else if (touchPhase == 8) action = Touch.UP;        // Ended
        return action; // cancelled anyway
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
            MemorySegment scrollingDeltaXSel = getSel(arena, "scrollingDeltaX");
            MemorySegment scrollingDeltaYSel = getSel(arena, "scrollingDeltaY");
            MemorySegment charactersSel = getSel(arena, "characters");
            MemorySegment magnificationSel = getSel(arena, "magnification");
            MemorySegment utf8StringSel = getSel(arena, "UTF8String");

            long NSAnyEventMask = -1L;

            MemorySegment nsDateClass = getObjcClass(arena, "NSDate");
            MemorySegment distantPastSel = getSel(arena, "distantPast");
            MemorySegment distantPast = (MemorySegment) MSG_SEND_PTR.invoke(nsDateClass, distantPastSel);

            MemorySegment buttonNumberSel = getSel(arena, "buttonNumber");
            MemorySegment locationInWindowSel = getSel(arena, "locationInWindow");
            MemorySegment windowSel = getSel(arena, "window");
            MemorySegment contentViewSel = getSel(arena, "contentView");
            MemorySegment frameSel = getSel(arena, "frame");

            while (true) {
                int button = 0;
                MemorySegment event = (MemorySegment) MSG_SEND_NEXT_EVENT.invoke(app, nextEventSel, NSAnyEventMask, distantPast, runLoopMode, (byte)1);
                if (event.address() == 0L) {
                    recenterIfLocked();
                    break;
                }
                
                long eventType = (long) MSG_SEND_LONG_RET.invoke(event, typeSel);
                
                // 10 = KeyDown, 11 = KeyUp
                if (eventType == 10 || eventType == 11) {
                    short macKeyCode = (short) MSG_SEND_SHORT_RET.invoke(event, keyCodeSel);
                    if (macKeyCode >= 0 && macKeyCode < 128) {
                        int stdKey = MAC_KEY_MAP[macKeyCode];
                        if (stdKey != -1) {
                            Key.pushEvent(stdKey, eventType == 10 ? 1 : 0, 250_000_000L); // 250ms multi-tap window
                        }
                        if (eventType == 10) {
                            MemorySegment nsString = (MemorySegment) MSG_SEND_PTR.invoke(event, charactersSel);
                            if (nsString != null && !nsString.equals(MemorySegment.NULL)) {
                                MemorySegment cStr = (MemorySegment) MSG_SEND_PTR.invoke(nsString, utf8StringSel);
                                if (cStr != null && !cStr.equals(MemorySegment.NULL)) {
                                    byte b0 = cStr.reinterpret(1).get(ValueLayout.JAVA_BYTE, 0);
                                    if (b0 > 0) Key.pushCharEvent((char) b0);
                                }
                            }
                        }
                    }
                } else if (eventType == 22) {
                    double dx = (double) MSG_SEND_DOUBLE_RET.invoke(event, scrollingDeltaXSel);
                    double dy = (double) MSG_SEND_DOUBLE_RET.invoke(event, scrollingDeltaYSel);
                    Mouse.pushScrollEvent(dx, dy);
                } else if (eventType == 30) {
                    double magnification = (double) MSG_SEND_DOUBLE_RET.invoke(event, magnificationSel);
                    Mouse.pushZoomEvent(magnification);
                } else if (eventType == 1 || eventType == 3 || eventType == 25) { // Mouse Down (Left=1, Right=3, Other=25)
                    button = (eventType == 1) ? Mouse.LEFT : ((eventType == 3) ? Mouse.RIGHT : -1);
                    if (eventType == 25) {
                        try {
                            long btnNum = (long) MSG_SEND_LONG_RET.invoke(event, buttonNumberSel);
                            button = (int) btnNum;
                        } catch (Throwable t) {
                            throw new macOSWindowException("CRITICAL: macOSWindow FFM Exception", t);
                        }
                    }
                    if (button != -1) Mouse.pushButtonEvent(button, 1, 250_000_000L);
                } else if (eventType == 2 || eventType == 4 || eventType == 26) { // Mouse Up (Left=2, Right=4, Other=26)
                    button = (eventType == 2) ? Mouse.LEFT : ((eventType == 4) ? Mouse.RIGHT : -1);
                    if (eventType == 26) {
                        try {
                            long btnNum = (long) MSG_SEND_LONG_RET.invoke(event, buttonNumberSel);
                            button = (int) btnNum;
                        } catch (Throwable t) {
                            throw new macOSWindowException("CRITICAL: macOSWindow FFM Exception", t);
                        }
                    }
                    if (button != -1) Mouse.pushButtonEvent(button, 0, 250_000_000L);
                } else if (eventType == 5 || eventType == 6 || eventType == 7 || eventType == 27) { // Mouse Move/Drag
                    if (lockWindowPtr != 0L) {
                        // Cursor locked: AppKit reports a constant location with actual
                        // deltas. Feed the relative movement so cameras track direction.
                        try {
                            double ddx = (double) MSG_SEND_DOUBLE_RET.invoke(event, getSel(arena, "deltaX"));
                            double ddy = (double) MSG_SEND_DOUBLE_RET.invoke(event, getSel(arena, "deltaY"));
                            Mouse.pushMoveDeltaEvent(ddx, ddy);
                        } catch (Throwable t) {
                            throw new macOSWindowException("CRITICAL: macOSWindow FFM Exception", t);
                        }
                        continue;
                    }
                    try {
                        MemorySegment point = (MemorySegment) MSG_SEND_POINT_RET.invoke(arena, event, locationInWindowSel);
                        double x = point.get(ValueLayout.JAVA_DOUBLE, 0);
                        double y = point.get(ValueLayout.JAVA_DOUBLE, 8);

                        // Standardize top-left origin inversion, mirroring waitEvents()
                        MemorySegment eventWindow = (MemorySegment) MSG_SEND_PTR.invoke(event, windowSel);
                        if (eventWindow.address() != 0L) {
                            MemorySegment contentView = (MemorySegment) MSG_SEND_PTR.invoke(eventWindow, contentViewSel);
                            if (contentView.address() != 0L) {
                                MemorySegment rect = (MemorySegment) MSG_SEND_RECT_RET.invoke(arena, contentView, frameSel);
                                double height = rect.get(ValueLayout.JAVA_DOUBLE, 24);
                                y = height - y;
                            }
                        }

                        // Differentiate between Move and Drag
                        if (eventType == 5) {
                            Mouse.pushMoveEvent(x, y);
                        } else {
                            int dragButton;
                            if (eventType == 6)
                                dragButton = Mouse.LEFT;
                            else if (eventType == 7)
                                dragButton = Mouse.RIGHT;
                            else // defaults to 27, unless more buttons are added
                            {
                                long btn = (long) MSG_SEND_LONG_RET.invoke(event, buttonNumberSel);
                                dragButton = (int) btn;
                            }
                            Mouse.pushDragEvent(dragButton, x, y);
                        }
                    } catch (Throwable t) {
                        throw new macOSWindowException("CRITICAL: macOSWindow FFM Exception", t);
                    }
                }
                
                // For Mouse clicks, we can also extract the coordinate so the state has it
                if (eventType == 1 || eventType == 2 || eventType == 3 || eventType == 4 || eventType == 25 || eventType == 26) {
                    try {
                        MemorySegment point = (MemorySegment) MSG_SEND_POINT_RET.invoke(arena, event, locationInWindowSel);
                        double x = point.get(ValueLayout.JAVA_DOUBLE, 0);
                        double y = point.get(ValueLayout.JAVA_DOUBLE, 8);
                        
                        MemorySegment eventWindow = (MemorySegment) MSG_SEND_PTR.invoke(event, windowSel);
                        if (eventWindow.address() != 0L) {
                            MemorySegment contentView = (MemorySegment) MSG_SEND_PTR.invoke(eventWindow, contentViewSel);
                            if (contentView.address() != 0L) {
                                MemorySegment rect = (MemorySegment) MSG_SEND_RECT_RET.invoke(arena, contentView, frameSel);
                                double height = rect.get(ValueLayout.JAVA_DOUBLE, 24);
                                y = height - y;
                            }
                        }
                        Mouse.pushDragEvent(button, x, y);
                    } catch (Throwable t) {
                        throw new macOSWindowException("CRITICAL: macOSWindow FFM Exception", t);
                    }
                }

                MSG_SEND_VOID_PTR.invoke(app, sendEventSel, event);
            }
            
            MSG_SEND_VOID.invoke(app, updateWindowsSel);
        } catch (Throwable t) {
            throw new macOSWindowException("CRITICAL: macOSWindow FFM Exception", t);
        }
    }

    public static void free(long pointer) {
        if (pointer == 0L || OBJC_GET_CLASS == null) return;
        try (Arena arena = Arena.ofConfined()) {
            MemorySegment window = MemorySegment.ofAddress(pointer);
            MemorySegment closeSel = getSel(arena, "close");
            MSG_SEND_VOID.invoke(window, closeSel);
        } catch (Throwable t) {
            throw new macOSWindowException("CRITICAL: macOSWindow FFM Exception", t);
        }
    }
}
