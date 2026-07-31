package cli;

import annotation.Draft;
import annotation.Required;
import annotation.Intention;
import nio.ForeignMemory;
import oop.TypeRegister;
import primitive.string;

@Draft
@Intention("Off-heap zero-allocation command structure representing a parsed command and its arguments")
public class Command {

    @Required
    public static final int CLASS_ID = TypeRegister.ID_COMMAND;

    @Draft
    public static long allocate(long nameStringPointer, long[] argumentStringPointers) {
        int argsLen = argumentStringPointers != null ? argumentStringPointers.length : 0;
        long totalUserSize = 8L + 4L + 4L + (8L * argsLen);
        long block = ForeignMemory.allocateNative(8L + totalUserSize);
        long userPtr = block + 8L;

        // Write header: type (FORM_SINGLETON | ID_COMMAND), length (1)
        int type = TypeRegister.FORM_SINGLETON | CLASS_ID;
        ForeignMemory.putInt(block, type);
        ForeignMemory.putInt(block + 4L, 1);

        // Fields:
        // userPtr + 0: nameStringPointer
        // userPtr + 8: argumentsCount
        // userPtr + 12: padding
        // userPtr + 16: arguments array of pointers
        ForeignMemory.putLong(userPtr, nameStringPointer);
        ForeignMemory.putInt(userPtr + 8L, argsLen);
        ForeignMemory.putInt(userPtr + 12L, 0); // padding
        for (int i = 0; i < argsLen; i++) {
            ForeignMemory.putLong(userPtr + 16L + (i * 8L), argumentStringPointers[i]);
        }
        return userPtr;
    }

    public static int type(long commandPointer) {
        if (commandPointer == 0L) return 0;
        return ForeignMemory.getInt(commandPointer - 8L);
    }

    private static void checkType(long commandPointer) {
        if (commandPointer == 0L) throw new NullPointerException("Accessing NULL command pointer!");
        int type = type(commandPointer);
        int expected = TypeRegister.FORM_SINGLETON | CLASS_ID;
        if (type != expected) {
            throw new IllegalArgumentException("Expected Command pointer, but got Type: 0x" + Integer.toHexString(type).toUpperCase());
        }
    }

    @Draft
    public static long getName(long commandPointer) {
        checkType(commandPointer);
        return ForeignMemory.getLong(commandPointer);
    }

    @Draft
    public static int getArgumentCount(long commandPointer) {
        checkType(commandPointer);
        return ForeignMemory.getInt(commandPointer + 8L);
    }

    @Draft
    public static long getArgument(long commandPointer, int index) {
        checkType(commandPointer);
        int len = ForeignMemory.getInt(commandPointer + 8L);
        if (index < 0 || index >= len) {
            throw new IndexOutOfBoundsException("Argument index " + index + " out of bounds for size " + len);
        }
        return ForeignMemory.getLong(commandPointer + 16L + (index * 8L));
    }

    @Draft
    public static void free(long commandPointer) {
        if (commandPointer == 0L) return;
        checkType(commandPointer);

        long namePtr = getName(commandPointer);
        if (namePtr != 0L) {
            string.free(namePtr);
        }

        int len = getArgumentCount(commandPointer);
        for (int i = 0; i < len; i++) {
            long argPtr = getArgument(commandPointer, i);
            if (argPtr != 0L) {
                string.free(argPtr);
            }
        }

        long block = commandPointer - 8L;
        // Zero header for safety
        ForeignMemory.putInt(block, 0);
        ForeignMemory.putInt(block + 4L, -1);
        ForeignMemory.freeNative(block);
    }
}
