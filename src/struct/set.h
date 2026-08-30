#ifndef STRUCT_SET_H
#define STRUCT_SET_H

#include <stdbool.h>
#include <stddef.h>
#include <stdint.h>
#include "c23/constructor.h"

#include "struct/collection.h"
#include "struct/list.h"

// struct/set.h — the Set class, ported from struct/Set.java.
//
// Open-addressing hash set of unique elements. Each slot is 24 bytes:
// element(8) + hash(8) + state(8); state is 0=empty, 1=occupied, 2=deleted.
// element_class lives in the embedded Collection; stride is unused.

typedef struct Set {
    Collection collection;
} Set;

// Empty set for element_class with initial slot capacity (min 4, power of two).
Set *Set_2(uint32_t element_class, size_t capacity);

void Set_free(Set *set);

// Add an element; returns 1 if inserted, 0 if already present.
int Set_add(Set *set, uint64_t element);

bool Set_contains(Set *set, uint64_t element);

// Remove an element; returns 1 if removed, 0 if absent.
int Set_remove(Set *set, uint64_t element);

// Export all elements into a List of element_class.
List *Set_toList(Set *set);

// Export into a List sorted by value (stride 4/8 use Arrays sort).
List *Set_toSortedList(Set *set);

bool Set_isEmpty(Set *set);
size_t Set_size(Set *set);
size_t Set_capacity(Set *set);
uint32_t Set_elementClassId(Set *set);
uint8_t *Set_dataBuffer(Set *set);


Set *Set_1(uint32_t element_class);

#define Set(...) CONSTRUCTOR_DISPATCH(Set, ##__VA_ARGS__)

#endif