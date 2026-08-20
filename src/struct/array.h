#ifndef STRUCT_ARRAY_H
#define STRUCT_ARRAY_H

#include <stdbool.h>
#include <stddef.h>
#include <stdint.h>

#include "struct/collection.h"

// struct/array.h — the Array class, ported from struct/Array.java.
//
// Fixed-length, zero-initialized, stride-based array. Active count equals
// capacity; unlike List it never grows.

typedef struct Array {
    Collection collection;
} Array;

// Fixed array of length elements of element_class, all zero-initialized.
Array *Array_allocate(uint32_t element_class, size_t length);

// Free the array and its data buffer.
void Array_free(Array *array);

// Get/set the value or pointer at index (bounds-checked).
uint64_t Array_get(Array *array, size_t index);
void Array_set(Array *array, size_t index, uint64_t value);

// Pointer to the struct element at index (bounds-checked).
uint8_t *Array_slot(Array *array, size_t index);

bool Array_isEmpty(Array *array);
size_t Array_size(Array *array);
size_t Array_length(Array *array);
size_t Array_capacity(Array *array);
uint32_t Array_elementClassId(Array *array);
size_t Array_stride(Array *array);
uint8_t *Array_dataBuffer(Array *array);

#endif