#ifndef STRUCT_LIST_H
#define STRUCT_LIST_H

#include <stdbool.h>
#include <stddef.h>
#include <stdint.h>

#include "struct/collection.h"

// struct/list.h — the List class, ported from struct/List.java.
//
// Dynamic stride-based list. Elements live in a contiguous buffer of
// stride(element_class) bytes each; the buffer grows by List grow chunks when
// full. Because the embedded Collection is the first member, a List pointer is
// also a Collection pointer — Collection_* accessors work on it directly.

typedef struct List {
    Collection collection;
} List;

// Empty list for element_class with initial capacity (min 1024, legacy default).
List *List_allocate(uint32_t element_class, size_t capacity);

// List pre-filled to count slots (legacy allocate): active_count = count.
List *List_allocateCount(uint32_t element_class, size_t count);

// Free the list and its data buffer.
void List_free(List *list);

// Append a scalar value or pointer, growing the buffer if needed.
void List_add(List *list, uint64_t value_or_pointer);

// Append a new (zeroed) struct slot and return a pointer to it.
uint8_t *List_addSlot(List *list);

// Get/set the value or pointer at index (bounds-checked).
uint64_t List_get(List *list, size_t index);
void List_set(List *list, size_t index, uint64_t value);

// Pointer to the struct element at index (bounds-checked).
uint8_t *List_slot(List *list, size_t index);

// Remove the element at index, shifting the tail left.
void List_remove(List *list, size_t index);

// Element-wise equality (stride + bytes must match).
bool List_compare(List *a, List *b);

bool List_isEmpty(List *list);
size_t List_size(List *list);
size_t List_length(List *list);
size_t List_capacity(List *list);
uint32_t List_elementClassId(List *list);
size_t List_stride(List *list);
uint8_t *List_dataBuffer(List *list);

#endif