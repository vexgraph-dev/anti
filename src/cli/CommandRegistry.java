package cli;

import annotation.Draft;
import oop.TypeRegister;
import primitive.string;
import struct.Map;

import java.lang.foreign.FunctionDescriptor;
import java.lang.foreign.Linker;
import java.lang.foreign.MemorySegment;
import java.lang.foreign.ValueLayout;
import java.lang.invoke.MethodHandle;

import nio.StringLookup;
@Draft
public class CommandRegistry {

    private static final long registryMapPtr = Map.instant(TypeRegister.ID_STRING, TypeRegister.ID_LONG);

    private static final MethodHandle INVOKER;
    static {
        Linker linker = Linker.nativeLinker();
        FunctionDescriptor descriptor = FunctionDescriptor.ofVoid(
            ValueLayout.JAVA_LONG
        );
        INVOKER = linker.downcallHandle(descriptor);
    }

    private CommandRegistry() {
    }

    @Draft
    public static void register(long commandNameStringPointer, long functionTargetPointer) {
        if (commandNameStringPointer == 0L || functionTargetPointer == 0L) return;
        Map.put(registryMapPtr, commandNameStringPointer, functionTargetPointer);
    }

    @Draft
    public static void execute(long parsedCommandPointer) {
        if (parsedCommandPointer == 0L) return;
        long namePtr = Command.getName(parsedCommandPointer);
        long targetPtr = Map.get(registryMapPtr, namePtr);
        if (targetPtr != 0L) {
            try {
                MemorySegment targetSegment = MemorySegment.ofAddress(targetPtr);
                INVOKER.bindTo(targetSegment).invokeExact(parsedCommandPointer);
            } catch (Throwable t) {
                System.err.println(StringLookup.getJavaString(377) + t.getMessage());
            }
        } else {
            String name = string.get(namePtr);
            System.out.println(StringLookup.getJavaString(378) + name);
        }
    }

    public static void free() {
        Map.free(registryMapPtr);
    }
}
