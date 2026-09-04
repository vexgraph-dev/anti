#include "oop/struct.h"

#include <string.h>

#include "nio/mem.h"
#include "oop/type.h"
#include "annotation/overview.h"

;;OVERVIEW
/**
 * ============================================================================
 * MODULE: Struct (oop/struct.c)
 * LEVEL: L2 — Behavior (struct allocation behavior)
 * ============================================================================
 * Dynamic Struct Instance & Allocation Engine.
 *
 * FUNCTION REGISTRY:
 * ----------------------------------------------------------------------------
 * Core Functions:
 *   - Struct_allocate(fields)
 *   - Struct_allocateSingletonRaw(generic)
 *   - Struct_allocateArrayFrom(fields, amount)
 *   - Struct_allocateArrayRaw(generic, amount)
 *   - Struct_allocateCoexistentFrom(fields, amount)
 *   - Struct_allocateCoexistentRaw(generic, amount)
 *   - Struct_allocateSOAFrom(fields, amount)
 *   - Struct_allocateSOARaw(generic, amount)
 *   - Struct_constructArray(fieldClasses, fieldCount)
 *   - Struct_construct(fieldClasses, fieldCount)
 *   - Struct_stride(generic)
 *   - Struct_fieldsCount(generic)
 *   - Struct_fieldClass(generic, fieldIndex)
 *   - Struct_free(userPtr)
 *   - Struct_field(ptr, fieldIndex)
 *   - Struct_elementField(ptr, elementIndex, fieldIndex)
 *
 * Setters:
 *   - Struct_setField(ptr, fieldIndex, value)
 *   - Struct_setElement(ptr, elementIndex, fieldIndex, value)
 *   - Struct_setInt(ptr, fieldIndex, value)
 *   - Struct_setLong(ptr, fieldIndex, value)
 *   - Struct_setFloat(ptr, fieldIndex, value)
 *   - Struct_setDouble(ptr, fieldIndex, value)
 *   - Struct_setByte(ptr, fieldIndex, value)
 *   - Struct_setShort(ptr, fieldIndex, value)
 *   - Struct_setPointerField(ptr, fieldIndex, value)
 *   - Struct_setIntElement(ptr, elementIndex, fieldIndex, value)
 *   - Struct_setLongElement(ptr, elementIndex, fieldIndex, value)
 *   - Struct_setFloatElement(ptr, elementIndex, fieldIndex, value)
 *   - Struct_setDoubleElement(ptr, elementIndex, fieldIndex, value)
 *   - Struct_setByteElement(ptr, elementIndex, fieldIndex, value)
 *   - Struct_setShortElement(ptr, elementIndex, fieldIndex, value)
 *   - Struct_setIntG(generic, ptr, fieldIndex, value)
 *   - Struct_setLongG(generic, ptr, fieldIndex, value)
 *   - Struct_setFloatG(generic, ptr, fieldIndex, value)
 *   - Struct_setDoubleG(generic, ptr, fieldIndex, value)
 *   - Struct_setByteG(generic, ptr, fieldIndex, value)
 *   - Struct_setShortG(generic, ptr, fieldIndex, value)
 *   - Struct_setNestedInt(ptr, elementIndex, fieldIndex, subFieldIndex, value)
 *   - Struct_setNestedFloat(ptr, elementIndex, fieldIndex, subFieldIndex, value)
 *   - Struct_setNestedLong(ptr, elementIndex, fieldIndex, subFieldIndex, value)
 *   - Struct_setNestedDouble(ptr, elementIndex, fieldIndex, subFieldIndex, value)
 *
 * Getters:
 *   - Struct_get(generic)
 *   - Struct_isFieldStruct(generic, fieldIndex)
 *   - Struct_getField(ptr, fieldIndex)
 *   - Struct_getElement(ptr, elementIndex, fieldIndex)
 *   - Struct_getInt(ptr, fieldIndex)
 *   - Struct_getLong(ptr, fieldIndex)
 *   - Struct_getFloat(ptr, fieldIndex)
 *   - Struct_getDouble(ptr, fieldIndex)
 *   - Struct_getByte(ptr, fieldIndex)
 *   - Struct_getShort(ptr, fieldIndex)
 *   - Struct_getPointerField(ptr, fieldIndex)
 *   - Struct_getIntElement(ptr, elementIndex, fieldIndex)
 *   - Struct_getLongElement(ptr, elementIndex, fieldIndex)
 *   - Struct_getFloatElement(ptr, elementIndex, fieldIndex)
 *   - Struct_getDoubleElement(ptr, elementIndex, fieldIndex)
 *   - Struct_getByteElement(ptr, elementIndex, fieldIndex)
 *   - Struct_getShortElement(ptr, elementIndex, fieldIndex)
 *   - Struct_getNested(ptr, elementIndex, fieldIndex)
 *   - Struct_getIntG(generic, ptr, fieldIndex)
 *   - Struct_getLongG(generic, ptr, fieldIndex)
 *   - Struct_getFloatG(generic, ptr, fieldIndex)
 *   - Struct_getDoubleG(generic, ptr, fieldIndex)
 *   - Struct_getByteG(generic, ptr, fieldIndex)
 *   - Struct_getShortG(generic, ptr, fieldIndex)
 *   - Struct_getNestedInt(ptr, elementIndex, fieldIndex, subFieldIndex)
 *   - Struct_getNestedFloat(ptr, elementIndex, fieldIndex, subFieldIndex)
 *   - Struct_getNestedLong(ptr, elementIndex, fieldIndex, subFieldIndex)
 *   - Struct_getNestedDouble(ptr, elementIndex, fieldIndex, subFieldIndex)
 * ============================================================================
 */


// struct.c — Dynamic Struct Instance & Allocation Engine.

Fields *Struct_constructArray(const uint32_t *fieldClasses, size_t fieldCount) {
    size_t *buf = Memory_alloc(Type_make(FORM_ARRAY, ID_STRIDE), fieldCount * sizeof(size_t));
    if (!buf) return nullptr;
    for (size_t i = 0; i < fieldCount; i++) buf[i] = fieldClasses[i];
    Fields *res = Fields_create(buf, fieldCount);
    Memory_free(buf);
    return res;
}

uint32_t Struct_construct(const uint32_t *fieldClasses, size_t fieldCount) {
    Fields *s = Struct_constructArray(fieldClasses, fieldCount);
    return s ? (*s).genericId : 0;
}

const Fields *Struct_get(uint32_t generic) {
    return Fields_get(generic);
}

size_t Struct_stride(uint32_t generic) {
    return Fields_stride(generic);
}

uint32_t Struct_fieldsCount(uint32_t generic) {
    return Fields_count(generic);
}

uint32_t Struct_fieldClass(uint32_t generic, size_t fieldIndex) {
    return Fields_fieldSize(generic, fieldIndex);
}

bool Struct_isFieldStruct(uint32_t generic, size_t fieldIndex) {
    return Fields_isFieldStruct(generic, fieldIndex);
}

static const Fields *layoutOf(uint32_t generic) {
    return Fields_get(generic);
}

static uint32_t genericOfPtr(const void *userPtr) {
    if (!userPtr) return 0;
    return Type_class(Memory_type((void*) userPtr));
}

// =========================================================================
// ALLOCATIONS
// =========================================================================

void *Struct_allocate(const Fields *fields) {
    if (!fields) return nullptr;
    return Struct_allocateSingletonRaw((*fields).genericId);
}

void *Struct_allocateSingletonRaw(uint32_t generic) {
    size_t stride = Fields_stride(generic);
    if (stride == 0) return nullptr;
    void *ptr = Memory_alloc(Type_make(FORM_STRUCT_SINGLETON, generic), stride);
    if (!ptr) return nullptr;
    memset(ptr, 0, stride);
    return ptr;
}

void *Struct_allocateArrayFrom(const Fields *fields, size_t amount) {
    if (!fields) return nullptr;
    return Struct_allocateArrayRaw((*fields).genericId, amount);
}

void *Struct_allocateArrayRaw(uint32_t generic, size_t amount) {
    size_t stride = Fields_stride(generic);
    if (stride == 0 || amount == 0) return nullptr;
    void *ptr = Memory_alloc(Type_make(FORM_STRUCT_ARRAY, generic), amount * stride);
    if (!ptr) return nullptr;
    memset(ptr, 0, amount * stride);
    return ptr;
}

void *Struct_allocateCoexistentFrom(const Fields *fields, size_t amount) {
    if (!fields) return nullptr;
    return Struct_allocateCoexistentRaw((*fields).genericId, amount);
}

void *Struct_allocateCoexistentRaw(uint32_t generic, size_t amount) {
    const Fields *s = Fields_get(generic);
    if (!s || (*s).stride == 0 || amount == 0)
        return nullptr;

    if ((*s).stream2Stride == 0) {
        return Struct_allocateArrayRaw(generic, amount);
    }

    size_t s1Size = (amount * (*s).stream1Stride + 7) & ~7;
    size_t s2Size = amount * (*s).stream2Stride;
    size_t totalPayload = s1Size + s2Size;

    void *ptr = Memory_alloc(Type_make(FORM_STRUCT_COEXISTENT, generic), totalPayload);
    if (!ptr) return nullptr;
    memset(ptr, 0, totalPayload);
    return ptr;
}

void *Struct_allocateSOAFrom(const Fields *fields, size_t amount) {
    if (!fields) return nullptr;
    return Struct_allocateSOARaw((*fields).genericId, amount);
}

void *Struct_allocateSOARaw(uint32_t generic, size_t amount) {
    size_t stride = Fields_stride(generic);
    if (stride == 0 || amount == 0) return nullptr;
    void *ptr = Memory_alloc(Type_make(FORM_ARRAY_SOA, generic), amount * stride);
    if (!ptr) return nullptr;
    memset(ptr, 0, amount * stride);
    return ptr;
}

void Struct_free(void *userPtr) {
    Memory_free(userPtr);
}

// =========================================================================
// FIELD ACCESSORS
// =========================================================================

void *Struct_field(void *ptr, size_t fieldIndex) {
    if (!ptr) return nullptr;
    uint32_t generic = genericOfPtr(ptr);
    const Fields *layout = layoutOf(generic);
    if (!layout || fieldIndex >= (*layout).count)
        return nullptr;
    return (uint8_t*) ptr + (*layout).items[fieldIndex].offset;
}

void *Struct_elementField(void *ptr, size_t elementIndex, size_t fieldIndex) {
    if (!ptr) return nullptr;
    uint32_t generic = genericOfPtr(ptr);
    const Fields *layout = layoutOf(generic);
    if (!layout || fieldIndex >= (*layout).count)
        return nullptr;

    uint32_t type = Memory_type(ptr);
    size_t length = Memory_length(ptr);

    // length is bytes, not element count — derive count from stride.
    // Coexistent has two streams and an aligned split, so we must solve for N.
    if (Type_isStructCoexistent(type)) {
        size_t s1 = (*layout).stream1Stride;
        size_t s2 = (*layout).stream2Stride;
        // Coexistent with empty secondary stream falls back to Array layout
        if (s2 == 0) {
            size_t stride = (*layout).stride;
            size_t count = stride ? length / stride : 0;
            if (elementIndex >= count) return nullptr;
            return (uint8_t*) ptr + elementIndex * stride + (*layout).items[fieldIndex].offset;
        }
        // Solve for N: length == ((N*s1+7)&~7) + N*s2 ; length uniquely determines N
        size_t count = 0;
        if (s1 == 0) {
            count = s2 ? length / s2 : 0;
        } else {
            // estimate then adjust by at most a few steps (padding <8)
            size_t est = length / (s1 + s2);
            // helper to compute required bytes for n elements
            // avoid overflow for huge n — saturate
            size_t need_est = ((est * s1 + 7) & ~ (size_t)7) + est * s2;
            while (need_est > length && est > 0) {
                est--;
                need_est = ((est * s1 + 7) & ~ (size_t)7) + est * s2;
            }
            while (true) {
                size_t need_next = (((est + 1) * s1 + 7) & ~ (size_t)7) + (est + 1) * s2;
                if (need_next <= length) { est++; } else break;
            }
            count = est;
        }
        if (elementIndex >= count) return nullptr;
        const Field *f = &(*layout).items[fieldIndex];
        if (!(*f).isStruct) {
            return (uint8_t*) ptr + (elementIndex * s1) + (*f).stream1Offset;
        } else {
            size_t s1Size = ((count * s1 + 7) & ~ (size_t)7);
            uint8_t *stream2Base = (uint8_t*) ptr + s1Size;
            return stream2Base + (elementIndex * s2) + (*f).stream2Offset;
        }
    }

    if (Type_isStructSOA(type)) {
        bool dummy = false;
        size_t fieldStride = Fields_resolveSize((*layout).items[fieldIndex].size, &dummy);
        size_t stride = (*layout).stride;
        size_t count = stride ? length / stride : 0;
        if (elementIndex >= count) return nullptr;
        return (uint8_t*) ptr + count * (*layout).items[fieldIndex].offset
            + elementIndex * fieldStride;
    }
    size_t stride = (*layout).stride;
    size_t count = stride ? length / stride : 0;
    if (elementIndex >= count) return nullptr;
    return (uint8_t*) ptr + elementIndex * stride
        + (*layout).items[fieldIndex].offset;
}

uint64_t Struct_getField(void *ptr, size_t fieldIndex) {
    void *addr = Struct_field(ptr, fieldIndex);
    if (!addr) return 0;
    uint32_t generic = genericOfPtr(ptr);
    const Fields *layout = layoutOf(generic);
    bool dummy = false;
    size_t sz = Fields_resolveSize((*layout).items[fieldIndex].size, &dummy);
    switch (sz) {
        case 1:  return *(uint8_t*) addr;
        case 2:  return *(uint16_t*) addr;
        case 4:  return *(uint32_t*) addr;
        default: return *(uint64_t*) addr;
    }
}

void Struct_setField(void *ptr, size_t fieldIndex, uint64_t value) {
    void *addr = Struct_field(ptr, fieldIndex);
    if (!addr) return;
    uint32_t generic = genericOfPtr(ptr);
    const Fields *layout = layoutOf(generic);
    bool dummy = false;
    size_t sz = Fields_resolveSize((*layout).items[fieldIndex].size, &dummy);
    switch (sz) {
        case 1:  *(uint8_t*) addr = (uint8_t)value;   break;
        case 2:  *(uint16_t*) addr = (uint16_t)value; break;
        case 4:  *(uint32_t*) addr = (uint32_t)value; break;
        default: *(uint64_t*) addr = value;           break;
    }
}

uint64_t Struct_getElement(void *ptr, size_t elementIndex, size_t fieldIndex) {
    void *addr = Struct_elementField(ptr, elementIndex, fieldIndex);
    if (!addr) return 0;
    uint32_t generic = genericOfPtr(ptr);
    const Fields *layout = layoutOf(generic);
    bool dummy = false;
    size_t sz = Fields_resolveSize((*layout).items[fieldIndex].size, &dummy);
    switch (sz) {
        case 1:  return (uint64_t)*(uint8_t*) addr;
        case 2:  return (uint64_t)*(uint16_t*) addr;
        case 4:  return (uint64_t)*(uint32_t*) addr;
        default: return *(uint64_t*) addr;
    }
}

void Struct_setElement(void *ptr, size_t elementIndex, size_t fieldIndex, uint64_t value) {
    void *addr = Struct_elementField(ptr, elementIndex, fieldIndex);
    if (!addr) return;
    uint32_t generic = genericOfPtr(ptr);
    const Fields *layout = layoutOf(generic);
    bool dummy = false;
    size_t sz = Fields_resolveSize((*layout).items[fieldIndex].size, &dummy);
    switch (sz) {
        case 1:  *(uint8_t*) addr = (uint8_t)value;   break;
        case 2:  *(uint16_t*) addr = (uint16_t)value; break;
        case 4:  *(uint32_t*) addr = (uint32_t)value; break;
        default: *(uint64_t*) addr = value;           break;
    }
}

// --- SINGLETON ACCESSORS ---

void Struct_setInt(void *ptr, size_t fieldIndex, int32_t value) {
    void *addr = Struct_field(ptr, fieldIndex);
    if (addr) *(int32_t*) addr = value;
}

int32_t Struct_getInt(void *ptr, size_t fieldIndex) {
    void *addr = Struct_field(ptr, fieldIndex);
    return addr ? *(int32_t*) addr : 0;
}

void Struct_setLong(void *ptr, size_t fieldIndex, int64_t value) {
    void *addr = Struct_field(ptr, fieldIndex);
    if (addr) *(int64_t*) addr = value;
}

int64_t Struct_getLong(void *ptr, size_t fieldIndex) {
    void *addr = Struct_field(ptr, fieldIndex);
    return addr ? *(int64_t*) addr : 0;
}

void Struct_setFloat(void *ptr, size_t fieldIndex, float value) {
    void *addr = Struct_field(ptr, fieldIndex);
    if (addr) *(float*) addr = value;
}

float Struct_getFloat(void *ptr, size_t fieldIndex) {
    void *addr = Struct_field(ptr, fieldIndex);
    return addr ? *(float*) addr : 0.0f;
}

void Struct_setDouble(void *ptr, size_t fieldIndex, double value) {
    void *addr = Struct_field(ptr, fieldIndex);
    if (addr) *(double*) addr = value;
}

double Struct_getDouble(void *ptr, size_t fieldIndex) {
    void *addr = Struct_field(ptr, fieldIndex);
    return addr ? *(double*) addr : 0.0;
}

void Struct_setByte(void *ptr, size_t fieldIndex, int8_t value) {
    void *addr = Struct_field(ptr, fieldIndex);
    if (addr) *(int8_t*) addr = value;
}

int8_t Struct_getByte(void *ptr, size_t fieldIndex) {
    void *addr = Struct_field(ptr, fieldIndex);
    return addr ? *(int8_t*) addr : 0;
}

void Struct_setShort(void *ptr, size_t fieldIndex, int16_t value) {
    void *addr = Struct_field(ptr, fieldIndex);
    if (addr) *(int16_t*) addr = value;
}

int16_t Struct_getShort(void *ptr, size_t fieldIndex) {
    void *addr = Struct_field(ptr, fieldIndex);
    return addr ? *(int16_t*) addr : 0;
}

void Struct_setPointerField(void *ptr, size_t fieldIndex, uintptr_t value) {
    void *addr = Struct_field(ptr, fieldIndex);
    if (addr) *(uintptr_t*) addr = value;
}

uintptr_t Struct_getPointerField(void *ptr, size_t fieldIndex) {
    void *addr = Struct_field(ptr, fieldIndex);
    return addr ? *(uintptr_t*) addr : 0;
}

// --- ARRAY ACCESSORS ---

void Struct_setIntElement(void *ptr, size_t elementIndex, size_t fieldIndex, int32_t value) {
    void *addr = Struct_elementField(ptr, elementIndex, fieldIndex);
    if (addr) *(int32_t*) addr = value;
}

int32_t Struct_getIntElement(void *ptr, size_t elementIndex, size_t fieldIndex) {
    void *addr = Struct_elementField(ptr, elementIndex, fieldIndex);
    return addr ? *(int32_t*) addr : 0;
}

void Struct_setLongElement(void *ptr, size_t elementIndex, size_t fieldIndex, int64_t value) {
    void *addr = Struct_elementField(ptr, elementIndex, fieldIndex);
    if (addr) *(int64_t*) addr = value;
}

int64_t Struct_getLongElement(void *ptr, size_t elementIndex, size_t fieldIndex) {
    void *addr = Struct_elementField(ptr, elementIndex, fieldIndex);
    return addr ? *(int64_t*) addr : 0;
}

void Struct_setFloatElement(void *ptr, size_t elementIndex, size_t fieldIndex, float value) {
    void *addr = Struct_elementField(ptr, elementIndex, fieldIndex);
    if (addr) *(float*) addr = value;
}

float Struct_getFloatElement(void *ptr, size_t elementIndex, size_t fieldIndex) {
    void *addr = Struct_elementField(ptr, elementIndex, fieldIndex);
    return addr ? *(float*) addr : 0.0f;
}

void Struct_setDoubleElement(void *ptr, size_t elementIndex, size_t fieldIndex, double value) {
    void *addr = Struct_elementField(ptr, elementIndex, fieldIndex);
    if (addr) *(double*) addr = value;
}

double Struct_getDoubleElement(void *ptr, size_t elementIndex, size_t fieldIndex) {
    void *addr = Struct_elementField(ptr, elementIndex, fieldIndex);
    return addr ? *(double*) addr : 0.0;
}

void Struct_setByteElement(void *ptr, size_t elementIndex, size_t fieldIndex, int8_t value) {
    void *addr = Struct_elementField(ptr, elementIndex, fieldIndex);
    if (addr) *(int8_t*) addr = value;
}

int8_t Struct_getByteElement(void *ptr, size_t elementIndex, size_t fieldIndex) {
    void *addr = Struct_elementField(ptr, elementIndex, fieldIndex);
    return addr ? *(int8_t*) addr : 0;
}

void Struct_setShortElement(void *ptr, size_t elementIndex, size_t fieldIndex, int16_t value) {
    void *addr = Struct_elementField(ptr, elementIndex, fieldIndex);
    if (addr) *(int16_t*) addr = value;
}

int16_t Struct_getShortElement(void *ptr, size_t elementIndex, size_t fieldIndex) {
    void *addr = Struct_elementField(ptr, elementIndex, fieldIndex);
    return addr ? *(int16_t*) addr : 0;
}

void *Struct_getNested(void *ptr, size_t elementIndex, size_t fieldIndex) {
    if (!ptr) return nullptr;
    uint32_t generic = genericOfPtr(ptr);
    const Fields *layout = layoutOf(generic);
    if (!layout || fieldIndex >= (*layout).count)
        return nullptr;
    if (!(*layout).items[fieldIndex].isStruct)
        return nullptr;

    uint32_t type = Memory_type(ptr);
    if (Type_isSingleton(type) || Type_isStructSingleton(type)) {
        return (uint8_t*) ptr + (*layout).items[fieldIndex].offset;
    }

    size_t length = Memory_length(ptr);
    size_t stride = (*layout).stride;
    size_t count = 0;
    if (Type_isStructCoexistent(type)) {
        size_t s1 = (*layout).stream1Stride;
        size_t s2 = (*layout).stream2Stride;
        if (s2 == 0) {
            count = stride ? length / stride : 0;
            if (elementIndex >= count) return nullptr;
            return (uint8_t*) ptr + elementIndex * stride + (*layout).items[fieldIndex].offset;
        }
        if (s1 == 0) {
            count = s2 ? length / s2 : 0;
            if (elementIndex >= count) return nullptr;
            // only stream2 exists
            return (uint8_t*) ptr + elementIndex * s2 + (*layout).items[fieldIndex].stream2Offset;
        }
        size_t est = length / (s1 + s2);
        size_t need_est = ((est * s1 + 7) & ~ (size_t)7) + est * s2;
        while (need_est > length && est > 0) { est--; need_est = ((est * s1 + 7) & ~ (size_t)7) + est * s2; }
        while (true) {
            size_t need_next = (((est + 1) * s1 + 7) & ~ (size_t)7) + (est + 1) * s2;
            if (need_next <= length) est++; else break;
        }
        count = est;
        if (elementIndex >= count) return nullptr;
        size_t s1Size = ((count * s1 + 7) & ~ (size_t)7);
        uint8_t *stream2Base = (uint8_t*) ptr + s1Size;
        return stream2Base + (elementIndex * s2) + (*layout).items[fieldIndex].stream2Offset;
    }
    count = stride ? length / stride : 0;
    if (elementIndex >= count) return nullptr;
    return (uint8_t*) ptr + elementIndex * stride + (*layout).items[fieldIndex].offset;
}

// --- GENERIC-EXPLICIT ACCESSORS ---

static uint8_t *genericFieldAddr(uint32_t generic, void *ptr, size_t fieldIndex) {
    if (!ptr) return nullptr;
    const Fields *layout = layoutOf(generic);
    if (!layout || fieldIndex >= (*layout).count)
        return nullptr;
    return (uint8_t*) ptr + (*layout).items[fieldIndex].offset;
}

void Struct_setIntG(uint32_t generic, void *ptr, size_t fieldIndex, int32_t value) {
    uint8_t *addr = genericFieldAddr(generic, ptr, fieldIndex);
    if (addr) *(int32_t*) addr = value;
}

int32_t Struct_getIntG(uint32_t generic, void *ptr, size_t fieldIndex) {
    uint8_t *addr = genericFieldAddr(generic, ptr, fieldIndex);
    return addr ? *(int32_t*) addr : 0;
}

void Struct_setLongG(uint32_t generic, void *ptr, size_t fieldIndex, int64_t value) {
    uint8_t *addr = genericFieldAddr(generic, ptr, fieldIndex);
    if (addr) *(int64_t*) addr = value;
}

int64_t Struct_getLongG(uint32_t generic, void *ptr, size_t fieldIndex) {
    uint8_t *addr = genericFieldAddr(generic, ptr, fieldIndex);
    return addr ? *(int64_t*) addr : 0;
}

void Struct_setFloatG(uint32_t generic, void *ptr, size_t fieldIndex, float value) {
    uint8_t *addr = genericFieldAddr(generic, ptr, fieldIndex);
    if (addr) *(float*) addr = value;
}

float Struct_getFloatG(uint32_t generic, void *ptr, size_t fieldIndex) {
    uint8_t *addr = genericFieldAddr(generic, ptr, fieldIndex);
    return addr ? *(float*) addr : 0.0f;
}

void Struct_setDoubleG(uint32_t generic, void *ptr, size_t fieldIndex, double value) {
    uint8_t *addr = genericFieldAddr(generic, ptr, fieldIndex);
    if (addr) *(double*) addr = value;
}

double Struct_getDoubleG(uint32_t generic, void *ptr, size_t fieldIndex) {
    uint8_t *addr = genericFieldAddr(generic, ptr, fieldIndex);
    return addr ? *(double*) addr : 0.0;
}

void Struct_setByteG(uint32_t generic, void *ptr, size_t fieldIndex, int8_t value) {
    uint8_t *addr = genericFieldAddr(generic, ptr, fieldIndex);
    if (addr) *(int8_t*) addr = value;
}

int8_t Struct_getByteG(uint32_t generic, void *ptr, size_t fieldIndex) {
    uint8_t *addr = genericFieldAddr(generic, ptr, fieldIndex);
    return addr ? *(int8_t*) addr : 0;
}

void Struct_setShortG(uint32_t generic, void *ptr, size_t fieldIndex, int16_t value) {
    uint8_t *addr = genericFieldAddr(generic, ptr, fieldIndex);
    if (addr) *(int16_t*) addr = value;
}

int16_t Struct_getShortG(uint32_t generic, void *ptr, size_t fieldIndex) {
    uint8_t *addr = genericFieldAddr(generic, ptr, fieldIndex);
    return addr ? *(int16_t*) addr : 0;
}

// --- DIRECT NESTED SUBFIELD ACCESSORS ---

static uint8_t *nestedSubfieldAddr(void *ptr, size_t elementIndex, size_t fieldIndex, size_t subFieldIndex, size_t subFieldSize) {
    void *nestedPtr = Struct_getNested(ptr, elementIndex, fieldIndex);
    if (!nestedPtr) return nullptr;
    uint32_t parentGeneric = genericOfPtr(ptr);
    uint32_t subGeneric = Struct_fieldClass(parentGeneric, fieldIndex);
    if (subGeneric >= ID_CUSTOM_STRUCT) {
        return genericFieldAddr(subGeneric, nestedPtr, subFieldIndex);
    }
    return (uint8_t*) nestedPtr + subFieldIndex * subFieldSize;
}

void Struct_setNestedInt(void *ptr, size_t elementIndex, size_t fieldIndex, size_t subFieldIndex, int32_t value) {
    uint8_t *addr = nestedSubfieldAddr(ptr, elementIndex, fieldIndex, subFieldIndex, sizeof(int32_t));
    if (addr) *(int32_t*) addr = value;
}

int32_t Struct_getNestedInt(void *ptr, size_t elementIndex, size_t fieldIndex, size_t subFieldIndex) {
    uint8_t *addr = nestedSubfieldAddr(ptr, elementIndex, fieldIndex, subFieldIndex, sizeof(int32_t));
    return addr ? *(int32_t*) addr : 0;
}

void Struct_setNestedFloat(void *ptr, size_t elementIndex, size_t fieldIndex, size_t subFieldIndex, float value) {
    uint8_t *addr = nestedSubfieldAddr(ptr, elementIndex, fieldIndex, subFieldIndex, sizeof(float));
    if (addr) *(float*) addr = value;
}

float Struct_getNestedFloat(void *ptr, size_t elementIndex, size_t fieldIndex, size_t subFieldIndex) {
    uint8_t *addr = nestedSubfieldAddr(ptr, elementIndex, fieldIndex, subFieldIndex, sizeof(float));
    return addr ? *(float*) addr : 0.0f;
}

void Struct_setNestedLong(void *ptr, size_t elementIndex, size_t fieldIndex, size_t subFieldIndex, int64_t value) {
    uint8_t *addr = nestedSubfieldAddr(ptr, elementIndex, fieldIndex, subFieldIndex, sizeof(int64_t));
    if (addr) *(int64_t*) addr = value;
}

int64_t Struct_getNestedLong(void *ptr, size_t elementIndex, size_t fieldIndex, size_t subFieldIndex) {
    uint8_t *addr = nestedSubfieldAddr(ptr, elementIndex, fieldIndex, subFieldIndex, sizeof(int64_t));
    return addr ? *(int64_t*) addr : 0;
}

void Struct_setNestedDouble(void *ptr, size_t elementIndex, size_t fieldIndex, size_t subFieldIndex, double value) {
    uint8_t *addr = nestedSubfieldAddr(ptr, elementIndex, fieldIndex, subFieldIndex, sizeof(double));
    if (addr) *(double*) addr = value;
}

double Struct_getNestedDouble(void *ptr, size_t elementIndex, size_t fieldIndex, size_t subFieldIndex) {
    uint8_t *addr = nestedSubfieldAddr(ptr, elementIndex, fieldIndex, subFieldIndex, sizeof(double));
    return addr ? *(double*) addr : 0.0;
}
