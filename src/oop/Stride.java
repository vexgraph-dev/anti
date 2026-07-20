package oop;

import annotation.Required;
import annotation.Volatile;

/**
 * Off-heap stride registry and byte size lookup utility.
 */
@Volatile
public final class Stride {

    @Required
    public static final int CLASS_ID = TypeRegister.ID_STRIDE;

    private Stride() {}

    public static int classId() {
        return CLASS_ID;
    }

    // get byte stride for class ID
    @Volatile
    public static int get(int classId) {
        return switch (classId) {
            case TypeRegister.ID_BYTE       -> 1;
            case TypeRegister.ID_SHORT      -> 2;
            case TypeRegister.ID_INT32,
                 TypeRegister.ID_FLOAT32    -> 4;
            case TypeRegister.ID_INT64,
                 TypeRegister.ID_FLOAT64,
                 TypeRegister.ID_STRING,
                 TypeRegister.ID_INT32_FP32 -> 8;
            case TypeRegister.ID_INT32_FP64,
                 TypeRegister.ID_INT64_FP32,
                 TypeRegister.ID_INT64_FP64 -> 16;
            case TypeRegister.ID_VARIABLE   -> 40;
            default                         -> 8;
        };
    }
}
