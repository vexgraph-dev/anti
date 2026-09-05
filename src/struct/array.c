#include "struct/array.h"

#include <string.h>

#include "nio/mem.h"
#include "oop/stride.h"
#include "oop/type.h"
#include "annotation/overview.h"

;;OVERVIEW
/**
 * ============================================================================
 * CLASS: Array (struct/array.c)
 * LEVEL: L2 — Behavior (container behavior API)
 * ============================================================================
 * the Array class, ported from struct/Array.java.
 *
 * STRUCT FIELDS (Mirroring struct/array.h):
 * ----------------------------------------------------------------------------
 *   Array {
 *     Collection collection; // instance state
 *   }
 *
 * FUNCTION REGISTRY:
 * ----------------------------------------------------------------------------
 * Constructors:
 *   - Array_2(element_class, length)
 *
 * Core Functions:
 *   - Array_free(array)
 *   - Array_slot(array, index)
 *   - Array_size(array)
 *   - Array_length(array)
 *   - Array_capacity(array)
 *   - Array_elementClassId(array)
 *   - Array_stride(array)
 *   - Array_dataBuffer(array)
 *
 * Setters:
 *   - Array_set(array, index, value)
 *
 * Getters:
 *   - Array_get(array, index)
 *   - Array_isEmpty(array)
 * ============================================================================
 */


// array.c — Array port (Legacy: struct/Array.java).

static Collection *asCollection(Array *array) {
    return (Collection*) array;
}

Array *Array_2(uint32_t elementClass, size_t length) {
    size_t stride = Stride_get(elementClass);
    Array *array = (Array*) Memory_alloc(TYPE_ARRAY, sizeof(Array));
    if (!array)
        return nullptr;

    Collection *c = asCollection(array);
    (*c).typeId = TYPE_ARRAY;
    (*c).activeCount = (uint32_t)length;
    (*c).elementClass = elementClass;
    (*c).stride = (uint32_t)stride;
    (*c).capacity = (uint32_t)length;
    (*c).head = 0;

    size_t bytes = length * stride;
    uint32_t bufType = Type_make(FORM_ARRAY, elementClass);
    (*c).data = (uint8_t*) Memory_alloc(bufType, bytes);
    if (!(*c).data) {
        Memory_free(array);
        return nullptr;
    }
    memset((*c).data, 0, bytes);
    return array;
}

void Array_free(Array *array) {
    if (!array) return;
    Collection *c = asCollection(array);
    if ((*c).data)
        Memory_free((*c).data);
    Memory_free(array);
}

uint64_t Array_get(Array *array, size_t index) {
    if (!array) return 0;
    Collection *c = asCollection(array);
    if (index >= (*c).activeCount)
        return 0;
    return Collection_readSlot(c, index);
}

void Array_set(Array *array, size_t index, uint64_t value) {
    if (!array) return;
    Collection *c = asCollection(array);
    if (index >= (*c).activeCount)
        return;
    Collection_writeSlot(c, index, value);
}

uint8_t *Array_slot(Array *array, size_t index) {
    if (!array) return nullptr;
    Collection *c = asCollection(array);
    if (index >= (*c).activeCount)
        return nullptr;
    return (*c).data + index * (*c).stride;
}

bool Array_isEmpty(Array *array) {
    return Collection_isEmpty(asCollection(array));
}

size_t Array_size(Array *array) {
    return Collection_size(asCollection(array));
}

size_t Array_length(Array *array) {
    return Collection_length(asCollection(array));
}

size_t Array_capacity(Array *array) {
    return Collection_capacity(asCollection(array));
}

uint32_t Array_elementClassId(Array *array) {
    return Collection_elementClassId(asCollection(array));
}

size_t Array_stride(Array *array) {
    return Collection_stride(asCollection(array));
}

uint8_t *Array_dataBuffer(Array *array) {
    return Collection_dataBuffer(asCollection(array));
}
