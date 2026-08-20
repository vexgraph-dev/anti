#ifndef UTIL_ARRAYS_H
#define UTIL_ARRAYS_H

#include <stddef.h>
#include <stdint.h>

// util/arrays.h — the Arrays utility, ported from util/Arrays.java.
//
// Draft-level off-heap array operations: quicksort, binary search, fill, copy.
// In C these operate on plain int64/int32 buffers owned by an Array/List.

// In-place quicksort of an int32 buffer.
void Arrays_sortInt(int32_t *data, size_t length);

// In-place quicksort of an int64 buffer.
void Arrays_sortLong(int64_t *data, size_t length);

// Binary search over a sorted int32 buffer. Returns the index, or
// -(insertion point + 1) when not found (legacy semantics).
intptr_t Arrays_binarySearchInt(const int32_t *data, size_t length, int32_t key);

// Binary search over a sorted int64 buffer. Returns the index, or
// -(insertion point + 1) when not found.
intptr_t Arrays_binarySearchLong(const int64_t *data, size_t length, int64_t key);

// Fill a byte block with value.
void Arrays_fill(uint8_t *data, size_t length, uint8_t value);

// Copy a byte block (non-overlapping, like memcpy).
void Arrays_copy(const uint8_t *src, uint8_t *dest, size_t bytes);

#endif