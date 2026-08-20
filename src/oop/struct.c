#include "oop/struct.h"

#include <string.h>

#include "nio/mem.h"
#include "oop/stride.h"
#include "oop/type.h"

// struct.c — Struct port (Legacy: oop/Struct.java).
//
// The registry is a fixed array of layouts indexed by generic - CUSTOM_STRUCT.
// Each layout owns its field-class + offset arrays, allocated from the arena.

#define MAX_STRUCTS 65000

typedef struct StructField {
    uint32_t field_class;
    uint32_t offset;
} StructField;

typedef struct StructLayout {
    size_t stride;
    uint32_t fields_count;
    StructField *fields;
} StructLayout;

static StructLayout layouts[MAX_STRUCTS];
static uint32_t next_struct_id = 1;

static int index_of(uint32_t generic) {
    if (generic < ID_CUSTOM_STRUCT)
        return -1;
    int32_t index = (int32_t)(generic - ID_CUSTOM_STRUCT);
    if (index < 0 || index >= (int32_t)MAX_STRUCTS)
        return -1;
    return index;
}

static int define_into(StructLayout *layout, const uint32_t *field_classes, size_t field_count) {
    if (!field_classes || field_count == 0)
        return 0;
    if ((*layout).fields) {
        Memory_free((*layout).fields);
        (*layout).fields = NULL;
    }

    StructField *fields = (StructField *)Memory_alloc(
        Type_make(FORM_ARRAY, ID_STRIDE), field_count * sizeof(StructField));
    if (!fields)
        return 0;

    size_t offset = 0;
    for (size_t i = 0; i < field_count; i++) {
        fields[i].field_class = field_classes[i];
        fields[i].offset = (uint32_t)offset;
        offset += Stride_get(field_classes[i]);
    }

    (*layout).stride = offset;
    (*layout).fields_count = (uint32_t)field_count;
    (*layout).fields = fields;
    return 1;
}

uint32_t Struct_construct(const uint32_t *field_classes, size_t field_count) {
    if (next_struct_id >= MAX_STRUCTS)
        return 0;
    uint32_t generic = ID_CUSTOM_STRUCT + next_struct_id;
    int index = index_of(generic);
    if (index < 0)
        return 0;
    if (!define_into(&layouts[index], field_classes, field_count))
        return 0;
    next_struct_id++;
    return generic;
}

void Struct_define(uint32_t generic, const uint32_t *field_classes, size_t field_count) {
    int index = index_of(generic);
    if (index < 0)
        return;
    define_into(&layouts[index], field_classes, field_count);
}

size_t Struct_stride(uint32_t generic) {
    int index = index_of(generic);
    if (index < 0)
        return 0;
    return layouts[index].stride;
}

uint32_t Struct_fieldsCount(uint32_t generic) {
    int index = index_of(generic);
    if (index < 0)
        return 0;
    return layouts[index].fields_count;
}

uint32_t Struct_fieldClass(uint32_t generic, size_t field_index) {
    int index = index_of(generic);
    if (index < 0)
        return 0;
    StructLayout *layout = &layouts[index];
    if (field_index >= (*layout).fields_count)
        return 0;
    return (*layout).fields[field_index].field_class;
}

static const StructLayout *layout_of(uint32_t generic) {
    int index = index_of(generic);
    if (index < 0)
        return NULL;
    return &layouts[index];
}

static uint32_t generic_of_ptr(const void *user_ptr) {
    if (!user_ptr) return 0;
    return Type_class(Memory_type((void *)user_ptr));
}

static uint32_t field_offset(uint32_t generic, size_t field_index) {
    const StructLayout *layout = layout_of(generic);
    if (!layout || field_index >= (*layout).fields_count)
        return 0;
    return (*layout).fields[field_index].offset;
}

static int field_is_class(uint32_t generic, size_t field_index, uint32_t expected) {
    const StructLayout *layout = layout_of(generic);
    if (!layout || field_index >= (*layout).fields_count)
        return 0;
    return (*layout).fields[field_index].field_class == expected;
}

void *Struct_allocateSingleton(uint32_t generic) {
    size_t stride = Struct_stride(generic);
    if (stride == 0) return NULL;
    void *ptr = Memory_alloc(Type_make(FORM_STRUCT_SINGLETON, generic), stride);
    if (!ptr) return NULL;
    memset(ptr, 0, stride);
    return ptr;
}

void *Struct_allocateArray(uint32_t generic, size_t length) {
    size_t stride = Struct_stride(generic);
    if (stride == 0 || length == 0) return NULL;
    void *ptr = Memory_alloc(Type_make(FORM_STRUCT_ARRAY, generic), length * stride);
    if (!ptr) return NULL;
    memset(ptr, 0, length * stride);
    return ptr;
}

void *Struct_allocateSOA(uint32_t generic, size_t length) {
    size_t stride = Struct_stride(generic);
    if (stride == 0 || length == 0) return NULL;
    void *ptr = Memory_alloc(Type_make(FORM_ARRAY_SOA, generic), length * stride);
    if (!ptr) return NULL;
    memset(ptr, 0, length * stride);
    return ptr;
}

void *Struct_allocateMatrix(uint32_t generic, size_t length) {
    if (length == 0) return NULL;
    void *ptr = Memory_alloc(Type_make(FORM_POINTER, generic), length * sizeof(uintptr_t));
    if (!ptr) return NULL;
    memset(ptr, 0, length * sizeof(uintptr_t));
    return ptr;
}

uintptr_t Struct_getPointer(void *user_ptr, size_t index) {
    if (!user_ptr) return 0;
    uint32_t type = Memory_type(user_ptr);
    if (!Type_isPointer(type)) return 0;
    uintptr_t *slots = (uintptr_t *)user_ptr;
    return slots[index];
}

void Struct_setPointer(void *user_ptr, size_t index, uintptr_t target) {
    if (!user_ptr) return;
    uint32_t type = Memory_type(user_ptr);
    if (!Type_isPointer(type)) return;
    uintptr_t *slots = (uintptr_t *)user_ptr;
    slots[index] = target;
}

void Struct_free(void *user_ptr) {
    Memory_free(user_ptr);
}

// --- SINGLETON ACCESSORS ---

static uint8_t *singleton_field_addr(void *ptr, size_t field_index, uint32_t expected) {
    uint32_t generic = generic_of_ptr(ptr);
    if (!field_is_class(generic, field_index, expected))
        return NULL;
    return (uint8_t *)ptr + field_offset(generic, field_index);
}

void Struct_setInt(void *ptr, size_t field_index, int32_t value) {
    uint8_t *addr = singleton_field_addr(ptr, field_index, ID_INT);
    if (addr) *(int32_t *)addr = value;
}

int32_t Struct_getInt(void *ptr, size_t field_index) {
    uint8_t *addr = singleton_field_addr(ptr, field_index, ID_INT);
    return addr ? *(int32_t *)addr : 0;
}

void Struct_setLong(void *ptr, size_t field_index, int64_t value) {
    uint8_t *addr = singleton_field_addr(ptr, field_index, ID_LONG);
    if (addr) *(int64_t *)addr = value;
}

int64_t Struct_getLong(void *ptr, size_t field_index) {
    uint8_t *addr = singleton_field_addr(ptr, field_index, ID_LONG);
    return addr ? *(int64_t *)addr : 0;
}

void Struct_setFloat(void *ptr, size_t field_index, float value) {
    uint8_t *addr = singleton_field_addr(ptr, field_index, ID_FLOAT);
    if (addr) *(float *)addr = value;
}

float Struct_getFloat(void *ptr, size_t field_index) {
    uint8_t *addr = singleton_field_addr(ptr, field_index, ID_FLOAT);
    return addr ? *(float *)addr : 0.0f;
}

void Struct_setDouble(void *ptr, size_t field_index, double value) {
    uint8_t *addr = singleton_field_addr(ptr, field_index, ID_DOUBLE);
    if (addr) *(double *)addr = value;
}

double Struct_getDouble(void *ptr, size_t field_index) {
    uint8_t *addr = singleton_field_addr(ptr, field_index, ID_DOUBLE);
    return addr ? *(double *)addr : 0.0;
}

void Struct_setByte(void *ptr, size_t field_index, int8_t value) {
    uint8_t *addr = singleton_field_addr(ptr, field_index, ID_BYTE);
    if (addr) *(int8_t *)addr = value;
}

int8_t Struct_getByte(void *ptr, size_t field_index) {
    uint8_t *addr = singleton_field_addr(ptr, field_index, ID_BYTE);
    return addr ? *(int8_t *)addr : 0;
}

void Struct_setShort(void *ptr, size_t field_index, int16_t value) {
    uint8_t *addr = singleton_field_addr(ptr, field_index, ID_SHORT);
    if (addr) *(int16_t *)addr = value;
}

int16_t Struct_getShort(void *ptr, size_t field_index) {
    uint8_t *addr = singleton_field_addr(ptr, field_index, ID_SHORT);
    return addr ? *(int16_t *)addr : 0;
}

void Struct_setPointerField(void *ptr, size_t field_index, uintptr_t value) {
    uint8_t *addr = singleton_field_addr(ptr, field_index, ID_LONG);
    if (addr) *(uintptr_t *)addr = value;
}

uintptr_t Struct_getPointerField(void *ptr, size_t field_index) {
    uint8_t *addr = singleton_field_addr(ptr, field_index, ID_LONG);
    return addr ? *(uintptr_t *)addr : 0;
}

// --- ARRAY ACCESSORS ---

static uint8_t *element_field_addr(void *ptr, size_t element_index, size_t field_index, uint32_t expected) {
    uint32_t generic = generic_of_ptr(ptr);
    const StructLayout *layout = layout_of(generic);
    if (!layout || field_index >= (*layout).fields_count)
        return NULL;
    if ((*layout).fields[field_index].field_class != expected)
        return NULL;

    uint32_t type = Memory_type(ptr);
    size_t length = Memory_length(ptr);
    if (element_index >= length)
        return NULL;

    if (Type_isStructSOA(type)) {
        size_t field_stride = Stride_get(expected);
        return (uint8_t *)ptr + length * (*layout).fields[field_index].offset
            + element_index * field_stride;
    }
    return (uint8_t *)ptr + element_index * (*layout).stride
        + (*layout).fields[field_index].offset;
}

void Struct_setIntElement(void *ptr, size_t element_index, size_t field_index, int32_t value) {
    uint8_t *addr = element_field_addr(ptr, element_index, field_index, ID_INT);
    if (addr) *(int32_t *)addr = value;
}

int32_t Struct_getIntElement(void *ptr, size_t element_index, size_t field_index) {
    uint8_t *addr = element_field_addr(ptr, element_index, field_index, ID_INT);
    return addr ? *(int32_t *)addr : 0;
}

void Struct_setLongElement(void *ptr, size_t element_index, size_t field_index, int64_t value) {
    uint8_t *addr = element_field_addr(ptr, element_index, field_index, ID_LONG);
    if (addr) *(int64_t *)addr = value;
}

int64_t Struct_getLongElement(void *ptr, size_t element_index, size_t field_index) {
    uint8_t *addr = element_field_addr(ptr, element_index, field_index, ID_LONG);
    return addr ? *(int64_t *)addr : 0;
}

void Struct_setFloatElement(void *ptr, size_t element_index, size_t field_index, float value) {
    uint8_t *addr = element_field_addr(ptr, element_index, field_index, ID_FLOAT);
    if (addr) *(float *)addr = value;
}

float Struct_getFloatElement(void *ptr, size_t element_index, size_t field_index) {
    uint8_t *addr = element_field_addr(ptr, element_index, field_index, ID_FLOAT);
    return addr ? *(float *)addr : 0.0f;
}

void Struct_setDoubleElement(void *ptr, size_t element_index, size_t field_index, double value) {
    uint8_t *addr = element_field_addr(ptr, element_index, field_index, ID_DOUBLE);
    if (addr) *(double *)addr = value;
}

double Struct_getDoubleElement(void *ptr, size_t element_index, size_t field_index) {
    uint8_t *addr = element_field_addr(ptr, element_index, field_index, ID_DOUBLE);
    return addr ? *(double *)addr : 0.0;
}

void Struct_setByteElement(void *ptr, size_t element_index, size_t field_index, int8_t value) {
    uint8_t *addr = element_field_addr(ptr, element_index, field_index, ID_BYTE);
    if (addr) *(int8_t *)addr = value;
}

int8_t Struct_getByteElement(void *ptr, size_t element_index, size_t field_index) {
    uint8_t *addr = element_field_addr(ptr, element_index, field_index, ID_BYTE);
    return addr ? *(int8_t *)addr : 0;
}

void Struct_setShortElement(void *ptr, size_t element_index, size_t field_index, int16_t value) {
    uint8_t *addr = element_field_addr(ptr, element_index, field_index, ID_SHORT);
    if (addr) *(int16_t *)addr = value;
}

int16_t Struct_getShortElement(void *ptr, size_t element_index, size_t field_index) {
    uint8_t *addr = element_field_addr(ptr, element_index, field_index, ID_SHORT);
    return addr ? *(int16_t *)addr : 0;
}