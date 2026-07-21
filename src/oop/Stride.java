package oop;

import annotation.Required;
import annotation.HotCode;

/**
 * Off-heap stride registry and byte size lookup utility.
 */
@HotCode
public final class Stride {

    @Required
    public static final int CLASS_ID = TypeRegister.ID_STRIDE;

    private Stride() {}

    public static int classId() {
        return CLASS_ID;
    }

    // get byte stride for class ID
    @HotCode
    public static int get(int generic) {
        int customStride = Struct.stride(generic);
        if (customStride != 0) {
            return customStride;
        }
        return switch (generic) {
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
            case TypeRegister.ID_CLOCK      -> 48;
            case TypeRegister.ID_DATETIME,
                 TypeRegister.ID_NANOTIME   -> 40;
            case TypeRegister.ID_RANDOM     -> 16;
            case TypeRegister.ID_LIST,
                 TypeRegister.ID_MAP,
                 TypeRegister.ID_SET,
                 TypeRegister.ID_STACK,
                 TypeRegister.ID_DEQUE,
                 TypeRegister.ID_SLAB_ALLOCATOR,
                 TypeRegister.ID_STRING_ENGINE,
                 TypeRegister.ID_SPIN_LOCK,
                 TypeRegister.ID_RING_BUFFER,
                 TypeRegister.ID_MEMORY_MAP_MANAGER,
                 TypeRegister.ID_TRIE,
                 TypeRegister.ID_SEARCH_VARIABLE,
                 TypeRegister.ID_INDEX_RANDOM,
                 TypeRegister.ID_CALENDAR,
                 TypeRegister.ID_GRID_ARRAY,
                 TypeRegister.ID_OCTREE,
                 TypeRegister.ID_CUBE_ARRAY,
                 TypeRegister.ID_SPHERE_ARRAY,
                 TypeRegister.ID_CIRCULAR_ARRAY -> 8; // pointer sizes
            default                         -> 8;
        };
    }
}
