#include "oop/stride.h"

#include "oop/struct.h"
#include "oop/type.h"

// stride.c — Stride port (Legacy: oop/Stride.java). Byte width of a class.

size_t Stride_get(uint32_t class_id) {
    uint32_t id = class_id & MASK_CLASS;

    // Runtime-defined custom structs first: the Struct registry is the source
    // of truth for their stride.
    if (id >= ID_CUSTOM_STRUCT) {
        size_t custom = Struct_stride(id);
        if (custom != 0)
            return custom;
    }

    switch (id) {
        case ID_BYTE:       return 1;
        case ID_SHORT:      return 2;
        case ID_INT:
        case ID_FLOAT:
        case ID_BIT8:
        case ID_BIT16:
        case ID_BIT32:      return 4;
        case ID_LONG:
        case ID_DOUBLE:
        case ID_FIXED64:
        case ID_BIT64:
        case ID_STRING:
        case ID_INT_FLOAT:
        case ID_ARGUMENTS:  return 8;
        case ID_INT_DOUBLE:
        case ID_LONG_FLOAT:
        case ID_LONG_DOUBLE: return 16;

        // Variable and the time outliers.
        case ID_VARIABLE:   return 40;
        // Math / geometry blocks.
        case ID_VEC2:       return 8;
        case ID_VEC3:       return 12;
        case ID_VEC4:       return 16;
        case ID_MAT3:       return 36;
        case ID_MAT4:       return 64;
        case ID_QUATERNION: return 16;
        case ID_ENTITY:     return 8;

        // Wrappers of a single pointer/struct.
        case ID_RANDOM:     return 16;
        case ID_LIST:
        case ID_MAP:
        case ID_SET:
        case ID_STACK:
        case ID_DEQUE:
        case ID_QUEUE:
        case ID_MIN_HEAP:
        case ID_SPARSE_SET:
        case ID_SPINLOCK:
        case ID_RING_BUFFER:
        case ID_INDEX_RANDOM: return 8;
        default:            return 8;
    }
}