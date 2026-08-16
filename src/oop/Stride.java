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
    @SuppressWarnings("all") // idc if its redundant. the compiler shall treeshake regardless
    public static int get(int generic) {
        int classId = generic & TypeRegister.MASK_CLASS;
        int customStride = Struct.stride(classId);

        if (customStride != 0)
            return customStride;

        return switch (classId) {

            // primtives
            case TypeRegister.ID_BYTE          -> 1;
            case TypeRegister.ID_SHORT         -> 2;
            case TypeRegister.ID_INT,       
                 TypeRegister.ID_FLOAT         -> 4;
            case TypeRegister.ID_LONG,      
                 TypeRegister.ID_DOUBLE,    
                 TypeRegister.ID_STRING,     
                 TypeRegister.ID_INT_FLOAT,
                 TypeRegister.ID_ARGUMENTS     -> 8;
            case TypeRegister.ID_INT_DOUBLE,
                 TypeRegister.ID_LONG_FLOAT,
                 TypeRegister.ID_LONG_DOUBLE   -> 16;

            // variable class, and other outliers
            case TypeRegister.ID_VARIABLE      -> 40;
            case TypeRegister.ID_CLOCK         -> 48;
            case TypeRegister.ID_DATETIME,  
                 TypeRegister.ID_NANOTIME      -> 40;
            case TypeRegister.ID_RANDOM        -> 16;

            // these are just basially wrappers of ones obejct/struct
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
            default -> 8;
        };
    }
}
