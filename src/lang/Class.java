package lang;

import annotation.Required;
import annotation.Volatile;
import nio.ForeignMemory;
import oop.TypeRegister;

/**
 * Off-heap class and type inspection subsystem.
 */
@Volatile
public final class Class {

    @Required
    public static final int CLASS_ID = TypeRegister.ID_CLASS;

    private Class() {}

    public static int classId() {
        return CLASS_ID;
    }

    // extract 24-bit raw class ID 8 bytes behind off-heap pointer
    @Volatile
    public static int getClass(long pointer) {
        if (pointer == 0L) return 0;
        return TypeRegister.getClassId(ForeignMemory.getInt(pointer - 8L));
    }

    // extract 32-bit bit-packed type ID 8 bytes behind off-heap pointer
    @Volatile
    public static int getType(long pointer) {
        if (pointer == 0L) return 0;
        return ForeignMemory.getInt(pointer - 8L);
    }

    // extract 32-bit length 4 bytes behind off-heap pointer
    @Volatile
    public static int getLength(long pointer) {
        if (pointer == 0L) return 0;
        return ForeignMemory.getInt(pointer - 4L);
    }

    // check if off-heap pointer is singleton form
    @Volatile
    public static boolean isSingleton(long pointer) {
        if (pointer == 0L) return false;
        return TypeRegister.isSingleton(ForeignMemory.getInt(pointer - 8L));
    }

    // check if off-heap pointer is array form
    @Volatile
    public static boolean isArray(long pointer) {
        if (pointer == 0L) return false;
        return TypeRegister.isArray(ForeignMemory.getInt(pointer - 8L));
    }

    // check if off-heap pointer is matrix or pointer array form
    @Volatile
    public static boolean isPointer(long pointer) {
        if (pointer == 0L) return false;
        return TypeRegister.isPointer(ForeignMemory.getInt(pointer - 8L));
    }
}
