package cli;

import annotation.Draft;
import oop.TypeRegister;
import primitive.string;
import struct.Map;

@Draft
public class CommandRegistry {

    private static final long registryMapPtr = Map.instant(TypeRegister.ID_STRING, TypeRegister.ID_LONG);

    private static final java.lang.invoke.MethodHandle INVOKER;
    static {
        java.lang.foreign.Linker linker = java.lang.foreign.Linker.nativeLinker();
        java.lang.foreign.FunctionDescriptor descriptor = java.lang.foreign.FunctionDescriptor.ofVoid(
            java.lang.foreign.ValueLayout.JAVA_LONG
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
                java.lang.foreign.MemorySegment targetSegment = java.lang.foreign.MemorySegment.ofAddress(targetPtr);
                INVOKER.bindTo(targetSegment).invokeExact(parsedCommandPointer);
            } catch (Throwable t) {
                System.err.println("Failed to execute command target pointer: " + t.getMessage());
            }
        } else {
            String name = string.get(namePtr);
            System.out.println("Unknown command: " + name);
        }
    }

    public static void free() {
        Map.free(registryMapPtr);
    }
}
