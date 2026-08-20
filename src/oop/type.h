#ifndef OOP_TYPE_H
#define OOP_TYPE_H

#include <stdint.h>

// oop/type.h — the TypeRegister, ported from oop/TypeRegister.java.
//
// Every allocated object in anti carries a 32-bit type id in its header. The
// id is bit-packed, mirroring the legacy hex layout:
//
//     0x F M W1 W2 CCCC
//        | | |  |   `---- class id     (which subsystem/struct this is)
//        | | |  `-------- wrapper 2    (probable/future/choice)
//        | | `----------- wrapper 1    (proactive/reactive)
//        | `------------- modifier     (global/locale/transient)
//        `--------------- form         (singleton/array/pointer/struct...)
//
// Reading the nibbles lets code decide *shape* without a switch: is it an
// array? a struct? The class id then says *what kind*. This is what lets one
// allocator serve every type (see anti_bit.c / anti_mem.c).

#define ANTI_MASK_FORM      0xF0000000u
#define ANTI_MASK_MODIFIER  0x0F000000u
#define ANTI_MASK_WRAPPER_1 0x00F00000u
#define ANTI_MASK_WRAPPER_2 0x000F0000u
#define ANTI_MASK_CLASS     0x0000FFFFu

#define ANTI_FORM_SINGLETON       0x10000000u
#define ANTI_FORM_ARRAY           0x20000000u
#define ANTI_FORM_POINTER         0x30000000u
#define ANTI_FORM_STRUCT_SINGLETON 0x40000000u
#define ANTI_FORM_STRUCT_ARRAY    0x50000000u
#define ANTI_FORM_STRUCT_POINTER  0x60000000u
#define ANTI_FORM_ARRAY_SOA       0x70000000u
#define ANTI_FORM_ARRAY_AOS       0x80000000u

#define ANTI_MOD_GLOBAL           0x01000000u
#define ANTI_MOD_LOCALE           0x02000000u
#define ANTI_MOD_TRANSIENT        0x03000000u

#define ANTI_WRAP_PROACTIVE       0x00100000u
#define ANTI_WRAP_REACTIVE        0x00200000u

#define ANTI_WRAP2_PROBABLE       0x00010000u
#define ANTI_WRAP2_PROBABLE_OBJECTS 0x00020000u
#define ANTI_WRAP2_FUTURE         0x00030000u
#define ANTI_WRAP2_CHOICE         0x00040000u

#define ANTI_ID_INT        0x0001u
#define ANTI_ID_LONG       0x0002u
#define ANTI_ID_DOUBLE     0x0003u
#define ANTI_ID_FIXED64    0x0004u
#define ANTI_ID_FLOAT      0x0005u
#define ANTI_ID_SHORT      0x0006u
#define ANTI_ID_SPINLOCK   0x0007u
#define ANTI_ID_RING_BUFFER 0x0008u
#define ANTI_ID_BIT8       0x0009u
#define ANTI_ID_BIT16      0x000Au
#define ANTI_ID_BIT32      0x000Bu
#define ANTI_ID_BIT64      0x000Cu
#define ANTI_ID_VEC2       0x000Du
#define ANTI_ID_VEC3       0x000Eu
#define ANTI_ID_ENTITY     0x000Fu

#define ANTI_TYPE_INT_SINGLETON (ANTI_FORM_SINGLETON | ANTI_ID_INT)
#define ANTI_TYPE_INT_ARRAY     (ANTI_FORM_ARRAY     | ANTI_ID_INT)
#define ANTI_TYPE_INT_POINTER   (ANTI_FORM_POINTER   | ANTI_ID_INT)

#define ANTI_TYPE_SPIN_LOCK     (ANTI_FORM_SINGLETON | ANTI_ID_SPINLOCK)
#define ANTI_TYPE_RING_BUFFER   (ANTI_FORM_ARRAY     | ANTI_ID_RING_BUFFER)

// The header prefixing every allocated block: [typeId][length].
// 16 bytes keeps payloads 8-byte aligned, so doubles/pointers sit naturally.
typedef struct anti_type_header {
    uint32_t type_id;
    uint32_t length;
} anti_type_header_t;

// Compose a full type id from a form + class id. Shape in the high nibble,
// identity in the low 16 bits.
static inline uint32_t anti_type_make(uint32_t form, uint32_t class_id) {
    return (form & ANTI_MASK_FORM) | (class_id & ANTI_MASK_CLASS);
}

static inline uint32_t anti_type_class(uint32_t type_id) {
    return type_id & ANTI_MASK_CLASS;
}

static inline uint32_t anti_type_form(uint32_t type_id) {
    return type_id & ANTI_MASK_FORM;
}

static inline int anti_type_is_struct(uint32_t form) {
    return form == ANTI_FORM_STRUCT_SINGLETON || form == ANTI_FORM_STRUCT_ARRAY
        || form == ANTI_FORM_STRUCT_POINTER;
}

#endif