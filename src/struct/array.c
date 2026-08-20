#include "struct/array.h"

#include <string.h>

#include "nio/mem.h"
#include "oop/stride.h"
#include "oop/type.h"

// array.c — Array port (Legacy: struct/Array.java).

static Collection *as_collection(Array *array) {
    return (Collection *)array;
}

Array *Array_allocate(uint32_t element_class, size_t length) {
    size_t stride = Stride_get(element_class);
    Array *array = (Array *)Memory_alloc(TYPE_ARRAY, sizeof(Array));
    if (!array)
        return NULL;

    Collection *c = as_collection(array);
    (*c).type_id = TYPE_ARRAY;
    (*c).active_count = (uint32_t)length;
    (*c).element_class = element_class;
    (*c).stride = (uint32_t)stride;
    (*c).capacity = (uint32_t)length;
    (*c).head = 0;

    size_t bytes = length * stride;
    uint32_t buf_type = Type_make(FORM_ARRAY, element_class);
    (*c).data = (uint8_t *)Memory_alloc(buf_type, bytes);
    if (!(*c).data) {
        Memory_free(array);
        return NULL;
    }
    memset((*c).data, 0, bytes);
    return array;
}

void Array_free(Array *array) {
    if (!array) return;
    Collection *c = as_collection(array);
    if ((*c).data)
        Memory_free((*c).data);
    Memory_free(array);
}

uint64_t Array_get(Array *array, size_t index) {
    if (!array) return 0;
    Collection *c = as_collection(array);
    if (index >= (*c).active_count)
        return 0;
    return Collection_readSlot(c, index);
}

void Array_set(Array *array, size_t index, uint64_t value) {
    if (!array) return;
    Collection *c = as_collection(array);
    if (index >= (*c).active_count)
        return;
    Collection_writeSlot(c, index, value);
}

uint8_t *Array_slot(Array *array, size_t index) {
    if (!array) return NULL;
    Collection *c = as_collection(array);
    if (index >= (*c).active_count)
        return NULL;
    return (*c).data + index * (*c).stride;
}

bool Array_isEmpty(Array *array) {
    return Collection_isEmpty(as_collection(array));
}

size_t Array_size(Array *array) {
    return Collection_size(as_collection(array));
}

size_t Array_length(Array *array) {
    return Collection_length(as_collection(array));
}

size_t Array_capacity(Array *array) {
    return Collection_capacity(as_collection(array));
}

uint32_t Array_elementClassId(Array *array) {
    return Collection_elementClassId(as_collection(array));
}

size_t Array_stride(Array *array) {
    return Collection_stride(as_collection(array));
}

uint8_t *Array_dataBuffer(Array *array) {
    return Collection_dataBuffer(as_collection(array));
}