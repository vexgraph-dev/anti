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

#define MASK_FORM      0xF0000000u
#define MASK_MODIFIER  0x0F000000u
#define MASK_WRAPPER_1 0x00F00000u
#define MASK_WRAPPER_2 0x000F0000u
#define MASK_CLASS     0x0000FFFFu

#define FORM_SINGLETON       0x10000000u
#define FORM_ARRAY           0x20000000u
#define FORM_POINTER         0x30000000u
#define FORM_STRUCT_SINGLETON 0x40000000u
#define FORM_STRUCT_ARRAY    0x50000000u
#define FORM_STRUCT_POINTER  0x60000000u
#define FORM_ARRAY_SOA       0x70000000u
#define FORM_ARRAY_AOS       0x80000000u

#define MOD_GLOBAL           0x01000000u
#define MOD_LOCALE           0x02000000u
#define MOD_TRANSIENT        0x03000000u

#define WRAP_PROACTIVE       0x00100000u
#define WRAP_REACTIVE        0x00200000u

#define WRAP2_PROBABLE       0x00010000u
#define WRAP2_PROBABLE_OBJECTS 0x00020000u
#define WRAP2_FUTURE         0x00030000u
#define WRAP2_CHOICE         0x00040000u

#define ID_INT        0x0001u
#define ID_LONG       0x0002u
#define ID_DOUBLE     0x0003u
#define ID_FIXED64    0x0004u
#define ID_FLOAT      0x0005u
#define ID_SHORT      0x0006u
#define ID_SPINLOCK   0x0007u
#define ID_RING_BUFFER 0x0008u
#define ID_BIT8       0x0009u
#define ID_BIT16      0x000Au
#define ID_BIT32      0x000Bu
#define ID_BIT64      0x000Cu
#define ID_VEC2       0x000Du
#define ID_VEC3       0x000Eu
#define ID_ENTITY     0x000Fu

// --- SCALAR / WRAPPER CLASSES (Legacy TypeRegister raw IDs) ---
#define ID_BYTE          0x0010u
#define ID_STRING        0x0011u
#define ID_INT_FLOAT     0x0012u
#define ID_INT_DOUBLE    0x0013u
#define ID_LONG_FLOAT    0x0014u
#define ID_LONG_DOUBLE   0x0015u
#define ID_VARIABLE      0x0016u
#define ID_PACK          0x0017u
#define ID_ARRAYS        0x0018u
#define ID_HASH          0x0019u
#define ID_CLASS         0x001Au
#define ID_STRIDE        0x001Bu

// --- COLLECTION CLASSES ---
#define ID_LIST          0x001Cu
#define ID_MAP           0x001Du
#define ID_SET           0x001Eu
#define ID_STACK         0x001Fu
#define ID_DEQUE         0x0020u
#define ID_MIN_HEAP      0x0021u
#define ID_SPARSE_SET    0x0022u
#define ID_RANDOM        0x0023u
#define ID_INDEX_RANDOM  0x0024u
#define ID_PROBABLE      0x0025u
#define ID_PROBABLE_OBJECTS 0x0026u
#define ID_QUEUE         0x0027u
#define ID_ARGUMENTS     0x0028u

// Base ID for runtime-defined custom structs (Legacy CUSTOM_STRUCT).
// Custom structs are CUSTOM_STRUCT + n; the Struct registry owns the stride.
#define ID_CUSTOM_STRUCT 0x4000u

// --- BUFFER FAMILY (Legacy 0x4A / 0x50..0x63) ---
#define ID_BUFFER                 0x004Au
#define ID_ACCUMULUATION_BUFFER   0x0050u
#define ID_AMBIENT_BUFFER         0x0051u
#define ID_COLOR_BUFFER           0x0052u
#define ID_DEFAULT_PIXEL_BUFFER   0x0053u
#define ID_DEPTH_BUFFER           0x0054u
#define ID_FILTER_BUFFER          0x0055u
#define ID_FRAME_BUFFER           0x0056u
#define ID_HEIGHT_BUFFER          0x0057u
#define ID_LIGHT_BUFFER           0x0058u
#define ID_MATERIAL_RESOLVE       0x0059u
#define ID_MOTION_VECTOR_BUFFER   0x005Au
#define ID_NORMAL_BUFFER          0x005Bu
#define ID_PHYSICAL_BUFFER        0x005Cu
#define ID_POST_PROCESSING_BUFFER 0x005Du
#define ID_REFLECTIVITY_BUFFER    0x005Eu
#define ID_SHADOW_BUFFER          0x005Fu
#define ID_SPECULAR_BUFFER        0x0060u
#define ID_STENCIL_BUFFER         0x0061u
#define ID_TRANSPARENCY_BUFFER    0x0062u
#define ID_VISIBILITY_BUFFER      0x0063u

// --- DARLING UI TREE (structural subclasses) ---
#define ID_CONTAINER              0x0079u
#define ID_PANEL                  0x0078u
#define ID_PICTURE                0x007Au
#define ID_SCENE                  0x007Cu
#define ID_SCENE2D                0x007Du
#define ID_SCENE3D                0x007Eu

#define TYPE_INT_SINGLETON (FORM_SINGLETON | ID_INT)
#define TYPE_INT_ARRAY     (FORM_ARRAY     | ID_INT)
#define TYPE_INT_POINTER   (FORM_POINTER   | ID_INT)
#define TYPE_LONG_ARRAY    (FORM_ARRAY     | ID_LONG)
#define TYPE_FLOAT_ARRAY   (FORM_ARRAY     | ID_FLOAT)
#define TYPE_DOUBLE_ARRAY  (FORM_ARRAY     | ID_DOUBLE)
#define TYPE_BYTE_ARRAY    (FORM_ARRAY     | ID_BYTE)
#define TYPE_STRING_ARRAY  (FORM_ARRAY     | ID_STRING)

#define TYPE_SPIN_LOCK     (FORM_SINGLETON | ID_SPINLOCK)
#define TYPE_RING_BUFFER   (FORM_ARRAY     | ID_RING_BUFFER)
#define TYPE_LIST          (FORM_ARRAY     | ID_LIST)
#define TYPE_ARRAY         (FORM_ARRAY     | ID_ARRAYS)
#define TYPE_STACK         (FORM_ARRAY     | ID_STACK)
#define TYPE_DEQUE         (FORM_ARRAY     | ID_DEQUE)
#define TYPE_QUEUE         (FORM_ARRAY     | ID_QUEUE)
#define TYPE_MAP           (FORM_POINTER   | ID_MAP)
#define TYPE_SET           (FORM_POINTER   | ID_SET)

#define TYPE_INT_SINGLETON (FORM_SINGLETON | ID_INT)
#define TYPE_INT_ARRAY     (FORM_ARRAY     | ID_INT)
#define TYPE_INT_POINTER   (FORM_POINTER   | ID_INT)

#define TYPE_SPIN_LOCK     (FORM_SINGLETON | ID_SPINLOCK)
#define TYPE_RING_BUFFER   (FORM_ARRAY     | ID_RING_BUFFER)

// The header prefixing every allocated block: [typeId][length].
// 16 bytes keeps payloads 8-byte aligned, so doubles/pointers sit naturally.
typedef struct TypeHeader {
    uint32_t type_id;
    uint32_t length;
} TypeHeader;

// Compose a full type id from a form + class id. Shape in the high nibble,
// identity in the low 16 bits.
static inline uint32_t Type_make(uint32_t form, uint32_t class_id) {
    return (form & MASK_FORM) | (class_id & MASK_CLASS);
}

static inline uint32_t Type_class(uint32_t type_id) {
    return type_id & MASK_CLASS;
}

static inline uint32_t Type_form(uint32_t type_id) {
    return type_id & MASK_FORM;
}

static inline int Type_isStruct(uint32_t form) {
    return form == FORM_STRUCT_SINGLETON || form == FORM_STRUCT_ARRAY
        || form == FORM_STRUCT_POINTER;
}

static inline int Type_isSingleton(uint32_t type_id) {
    return (type_id & MASK_FORM) == FORM_SINGLETON;
}

static inline int Type_isArray(uint32_t type_id) {
    uint32_t form = type_id & MASK_FORM;
    return form == FORM_ARRAY || form == FORM_ARRAY_SOA || form == FORM_ARRAY_AOS;
}

static inline int Type_isPointer(uint32_t type_id) {
    return (type_id & MASK_FORM) == FORM_POINTER;
}

static inline int Type_isStructSingleton(uint32_t type_id) {
    return (type_id & MASK_FORM) == FORM_STRUCT_SINGLETON;
}

static inline int Type_isStructArray(uint32_t type_id) {
    uint32_t form = type_id & MASK_FORM;
    return form == FORM_STRUCT_ARRAY || form == FORM_ARRAY_SOA || form == FORM_ARRAY_AOS;
}

static inline int Type_isStructSOA(uint32_t type_id) {
    return (type_id & MASK_FORM) == FORM_ARRAY_SOA;
}

static inline int Type_isStructAOS(uint32_t type_id) {
    return (type_id & MASK_FORM) == FORM_ARRAY_AOS;
}

static inline int Type_isStructPointer(uint32_t type_id) {
    return (type_id & MASK_FORM) == FORM_STRUCT_POINTER;
}

static inline int Type_isPrimitive(uint32_t type_id) {
    uint32_t form = type_id & MASK_FORM;
    return form == FORM_SINGLETON || form == FORM_ARRAY || form == FORM_POINTER;
}

static inline int Type_isGlobal(uint32_t type_id) {
    return (type_id & MASK_MODIFIER) == MOD_GLOBAL;
}

static inline int Type_isLocale(uint32_t type_id) {
    return (type_id & MASK_MODIFIER) == MOD_LOCALE;
}

static inline int Type_isTransient(uint32_t type_id) {
    return (type_id & MASK_MODIFIER) == MOD_TRANSIENT;
}

static inline int Type_isProactive(uint32_t type_id) {
    return (type_id & MASK_WRAPPER_1) == WRAP_PROACTIVE;
}

static inline int Type_isReactive(uint32_t type_id) {
    return (type_id & MASK_WRAPPER_1) == WRAP_REACTIVE;
}

static inline int Type_isProbable(uint32_t type_id) {
    return (type_id & MASK_WRAPPER_2) == WRAP2_PROBABLE;
}

static inline int Type_isProbableObjects(uint32_t type_id) {
    return (type_id & MASK_WRAPPER_2) == WRAP2_PROBABLE_OBJECTS;
}

static inline int Type_isFuture(uint32_t type_id) {
    return (type_id & MASK_WRAPPER_2) == WRAP2_FUTURE;
}

static inline int Type_isChoice(uint32_t type_id) {
    return (type_id & MASK_WRAPPER_2) == WRAP2_CHOICE;
}

// Parent-class walk (Legacy getParentClass). Returns the parent class id,
// or the class id itself when it is a root. Used by Type_isA.
uint32_t Type_getParentClass(uint32_t class_id);

// True if class_id is ancestor_id or any descendant of it (walks parents).
int Type_isA(uint32_t class_id, uint32_t ancestor_id);

#endif