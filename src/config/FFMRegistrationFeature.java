package config;

import annotation.Draft;
import annotation.HotCode;
import annotation.Intention;
import org.graalvm.nativeimage.hosted.Feature;
import org.graalvm.nativeimage.hosted.RuntimeForeignAccess;
import java.lang.foreign.FunctionDescriptor;
import java.lang.foreign.MemoryLayout;
import java.lang.foreign.ValueLayout;

/**
 * Programmatic FFM downcall registration feature matching the exact FunctionDescriptors in macOSWindow and ForeignMemory.
 */
@Draft
@Intention("Registers exact FFM FunctionDescriptor layouts used by window.macOSWindow and nio.ForeignMemory so GraalVM AOT generates the correct stubs.")
public final class FFMRegistrationFeature implements Feature {

    @Override
    @HotCode // this is important to be mindful, everything is very important during setup
    public void duringSetup(DuringSetupAccess access) {
        System.out.println("[FFMRegistrationFeature] Registering exact macOSWindow and ForeignMemory FFM downcall signatures...");

        // Reconstruct the exact layouts used in macOSWindow.java
        MemoryLayout cgSize = MemoryLayout.structLayout(
            ValueLayout.JAVA_DOUBLE.withName("width"),
            ValueLayout.JAVA_DOUBLE.withName("height")
        );

        MemoryLayout cgRect = MemoryLayout.structLayout(
            ValueLayout.JAVA_DOUBLE.withName("x"),
            ValueLayout.JAVA_DOUBLE.withName("y"),
            ValueLayout.JAVA_DOUBLE.withName("width"),
            ValueLayout.JAVA_DOUBLE.withName("height")
        );

        MemoryLayout cgPoint = MemoryLayout.structLayout(
            ValueLayout.JAVA_DOUBLE.withName("x"),
            ValueLayout.JAVA_DOUBLE.withName("y")
        );

        // --- nio.ForeignMemory Downcalls ---
        // malloc
        RuntimeForeignAccess.registerForDowncall(
            FunctionDescriptor.of(ValueLayout.ADDRESS, ValueLayout.JAVA_LONG)
        );
        // free
        RuntimeForeignAccess.registerForDowncall(
            FunctionDescriptor.ofVoid(ValueLayout.ADDRESS)
        );

        // --- objects.* Event Callbacks Downcalls ---
        RuntimeForeignAccess.registerForDowncall(
            FunctionDescriptor.of(ValueLayout.JAVA_LONG)
        );
        RuntimeForeignAccess.registerForDowncall(
            FunctionDescriptor.ofVoid(ValueLayout.JAVA_LONG)
        );

        // --- window.macOSWindow Downcalls ---
        // 1. getClass / selRegName
        RuntimeForeignAccess.registerForDowncall(
            FunctionDescriptor.of(ValueLayout.ADDRESS, ValueLayout.ADDRESS)
        );

        // 3. msgSendPtr
        RuntimeForeignAccess.registerForDowncall(
            FunctionDescriptor.of(ValueLayout.ADDRESS, ValueLayout.ADDRESS, ValueLayout.ADDRESS)
        );

        // 4. msgSendPtrPtr
        RuntimeForeignAccess.registerForDowncall(
            FunctionDescriptor.of(ValueLayout.ADDRESS, ValueLayout.ADDRESS, ValueLayout.ADDRESS, ValueLayout.ADDRESS)
        );

        // 5. msgSendPtrLong
        RuntimeForeignAccess.registerForDowncall(
            FunctionDescriptor.of(ValueLayout.ADDRESS, ValueLayout.ADDRESS, ValueLayout.ADDRESS, ValueLayout.JAVA_LONG)
        );

        // 6. msgSendPtrLongPtr
        RuntimeForeignAccess.registerForDowncall(
            FunctionDescriptor.of(ValueLayout.ADDRESS, ValueLayout.ADDRESS, ValueLayout.ADDRESS, ValueLayout.JAVA_LONG, ValueLayout.ADDRESS)
        );

        // 7. msgSendPtrSize
        RuntimeForeignAccess.registerForDowncall(
            FunctionDescriptor.ofVoid(ValueLayout.ADDRESS, ValueLayout.ADDRESS, cgSize)
        );

        // 8. msgSendVoid
        RuntimeForeignAccess.registerForDowncall(
            FunctionDescriptor.ofVoid(ValueLayout.ADDRESS, ValueLayout.ADDRESS)
        );

        // 9. msgSendVoidPtr
        RuntimeForeignAccess.registerForDowncall(
            FunctionDescriptor.ofVoid(ValueLayout.ADDRESS, ValueLayout.ADDRESS, ValueLayout.ADDRESS)
        );

        // 10. msgSendInt
        RuntimeForeignAccess.registerForDowncall(
            FunctionDescriptor.ofVoid(ValueLayout.ADDRESS, ValueLayout.ADDRESS, ValueLayout.JAVA_LONG)
        );

        // 11. msgSendBool
        RuntimeForeignAccess.registerForDowncall(
            FunctionDescriptor.ofVoid(ValueLayout.ADDRESS, ValueLayout.ADDRESS, ValueLayout.JAVA_BYTE)
        );

        // 12. msgSendBoolRet
        RuntimeForeignAccess.registerForDowncall(
            FunctionDescriptor.of(ValueLayout.JAVA_BYTE, ValueLayout.ADDRESS, ValueLayout.ADDRESS)
        );

        // 13. msgSendPtrDouble
        RuntimeForeignAccess.registerForDowncall(
            FunctionDescriptor.of(ValueLayout.ADDRESS, ValueLayout.ADDRESS, ValueLayout.ADDRESS, ValueLayout.JAVA_DOUBLE)
        );

        // 14. msgSendNextEvent
        RuntimeForeignAccess.registerForDowncall(
            FunctionDescriptor.of(
                ValueLayout.ADDRESS,
                ValueLayout.ADDRESS, ValueLayout.ADDRESS,
                ValueLayout.JAVA_LONG, ValueLayout.ADDRESS, ValueLayout.ADDRESS, ValueLayout.JAVA_BYTE
            )
        );

        // 15. msgSendLongRet
        RuntimeForeignAccess.registerForDowncall(
            FunctionDescriptor.of(ValueLayout.JAVA_LONG, ValueLayout.ADDRESS, ValueLayout.ADDRESS)
        );

        // 16. msgSendShortRet
        RuntimeForeignAccess.registerForDowncall(
            FunctionDescriptor.of(ValueLayout.JAVA_SHORT, ValueLayout.ADDRESS, ValueLayout.ADDRESS)
        );

        // 17. msgSendPointRet
        RuntimeForeignAccess.registerForDowncall(
            FunctionDescriptor.of(cgSize, ValueLayout.ADDRESS, ValueLayout.ADDRESS)
        );

        // 18. msgSendRectRet
        RuntimeForeignAccess.registerForDowncall(
            FunctionDescriptor.of(cgRect, ValueLayout.ADDRESS, ValueLayout.ADDRESS)
        );

        // 19. msgSendDoubleRet
        RuntimeForeignAccess.registerForDowncall(
            FunctionDescriptor.of(ValueLayout.JAVA_DOUBLE, ValueLayout.ADDRESS, ValueLayout.ADDRESS)
        );

        // 20. msgSendInitWindow
        RuntimeForeignAccess.registerForDowncall(
            FunctionDescriptor.of(
                ValueLayout.ADDRESS,
                ValueLayout.ADDRESS, ValueLayout.ADDRESS,
                cgRect, ValueLayout.JAVA_LONG, ValueLayout.JAVA_LONG, ValueLayout.JAVA_BYTE
            )
        );

        // 21. MTLCreateSystemDefaultDevice
        RuntimeForeignAccess.registerForDowncall(
            FunctionDescriptor.of(ValueLayout.ADDRESS)
        );

        // --- CoreGraphics / CoreFoundation Display & Cursor Downcalls ---
        // 22. CGMainDisplayID
        RuntimeForeignAccess.registerForDowncall(
            FunctionDescriptor.of(ValueLayout.JAVA_INT)
        );

        // 23. CGDisplayCopyAllDisplayModes
        RuntimeForeignAccess.registerForDowncall(
            FunctionDescriptor.of(ValueLayout.ADDRESS, ValueLayout.JAVA_INT, ValueLayout.ADDRESS)
        );

        // 24. CFArrayGetCount
        RuntimeForeignAccess.registerForDowncall(
            FunctionDescriptor.of(ValueLayout.JAVA_LONG, ValueLayout.ADDRESS)
        );

        // 25. CFArrayGetValueAtIndex
        RuntimeForeignAccess.registerForDowncall(
            FunctionDescriptor.of(ValueLayout.ADDRESS, ValueLayout.ADDRESS, ValueLayout.JAVA_LONG)
        );

        // 26. CGDisplayModeGetPixelWidth / CGDisplayModeGetPixelHeight
        RuntimeForeignAccess.registerForDowncall(
            FunctionDescriptor.of(ValueLayout.JAVA_LONG, ValueLayout.ADDRESS)
        );

        // 27. CGAssociateMouseAndMouseCursorPosition
        RuntimeForeignAccess.registerForDowncall(
            FunctionDescriptor.ofVoid(ValueLayout.JAVA_BYTE)
        );

        // 28. CGWarpMouseCursorPosition
        RuntimeForeignAccess.registerForDowncall(
            FunctionDescriptor.of(ValueLayout.JAVA_INT, cgPoint)
        );

        // 29. msgSendBoolLongRet
        RuntimeForeignAccess.registerForDowncall(
            FunctionDescriptor.of(ValueLayout.JAVA_BYTE, ValueLayout.ADDRESS, ValueLayout.ADDRESS, ValueLayout.JAVA_LONG)
        );

        // 30. msgSendBoolRetPtrPtr
        RuntimeForeignAccess.registerForDowncall(
            FunctionDescriptor.of(ValueLayout.JAVA_BYTE, ValueLayout.ADDRESS, ValueLayout.ADDRESS, ValueLayout.ADDRESS, ValueLayout.ADDRESS)
        );

        // --- System-wide CGEventTap key telemetry + CFRunLoop (macOSWindow) ---
        // 31. CGEventTapCreate
        RuntimeForeignAccess.registerForDowncall(
            FunctionDescriptor.of(ValueLayout.ADDRESS, ValueLayout.JAVA_INT, ValueLayout.JAVA_INT, ValueLayout.JAVA_INT, ValueLayout.JAVA_LONG, ValueLayout.ADDRESS, ValueLayout.ADDRESS)
        );

        // 32. CGEventTapEnable
        RuntimeForeignAccess.registerForDowncall(
            FunctionDescriptor.ofVoid(ValueLayout.ADDRESS, ValueLayout.JAVA_BYTE)
        );

        // 33. CGEventGetIntegerValueField
        RuntimeForeignAccess.registerForDowncall(
            FunctionDescriptor.of(ValueLayout.JAVA_LONG, ValueLayout.ADDRESS, ValueLayout.JAVA_INT)
        );

        // 34. CFMachPortCreateRunLoopSource
        RuntimeForeignAccess.registerForDowncall(
            FunctionDescriptor.of(ValueLayout.ADDRESS, ValueLayout.ADDRESS, ValueLayout.ADDRESS, ValueLayout.JAVA_LONG)
        );

        // 35. CFRunLoopRun
        RuntimeForeignAccess.registerForDowncall(
            FunctionDescriptor.ofVoid()
        );

        // 36. CFStringCreateWithCString (CFAllocatorRef, const char*, CFStringEncoding) -> CFStringRef
        RuntimeForeignAccess.registerForDowncall(
            FunctionDescriptor.of(ValueLayout.ADDRESS, ValueLayout.ADDRESS, ValueLayout.ADDRESS, ValueLayout.JAVA_INT)
        );

        // --- macOSWindow key telemetry: CGEventTapCallback upcall ---
        // (CGEventTapProxy, CGEventType, CGEventRef, void*) -> CGEventRef
        RuntimeForeignAccess.registerForUpcall(
            FunctionDescriptor.of(
                ValueLayout.ADDRESS,
                ValueLayout.ADDRESS, ValueLayout.JAVA_INT, ValueLayout.ADDRESS, ValueLayout.ADDRESS
            )
        );
    }
}
