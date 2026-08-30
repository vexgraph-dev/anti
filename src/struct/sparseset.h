#ifndef STRUCT_SPARSESET_H
#define STRUCT_SPARSESET_H

#include <stdbool.h>
#include <stddef.h>
#include <stdint.h>
#include "c23/constructor.h"

// struct/sparseset.h — the SparseSet class, ported from struct/SparseSet.java.
//
// ECS sparse set: maps sparse entity ids (int) to a tightly packed dense index.
// Optionally carries a contiguous component data block (stride bytes per entry)
// so each add returns the data pointer to write into.

typedef struct SparseSet {
    int32_t capacity;      // dense/data capacity
    int32_t maxEntities;   // sparse array length
    int32_t count;         // live entries
    int32_t stride;        // component stride (0 = set only)
    int32_t *dense;        // dense[i] = entity id
    int32_t *sparse;       // sparse[entity] = dense index, -1 = absent
    uint8_t *data;         // component data, capacity * stride bytes
} SparseSet;

// Set over entity ids [0, maxEntities) with optional component stride.
SparseSet *SparseSet_3(size_t capacity, size_t maxEntities, size_t stride);

void SparseSet_free(SparseSet *set);

size_t SparseSet_count(SparseSet *set);
size_t SparseSet_capacity(SparseSet *set);
size_t SparseSet_maxEntities(SparseSet *set);

bool SparseSet_contains(SparseSet *set, int32_t entityId);

// Add entity (if absent) and return a pointer to its component data (or nullptr
// when the set carries no data block).
uint8_t *SparseSet_add(SparseSet *set, int32_t entityId);

// Remove the entity if present; data is swap-removed to keep density.
void SparseSet_remove(SparseSet *set, int32_t entityId);

// Component data pointer for the entity, or nullptr if absent (or stride == 0,
// in which case a non-nullptr sentinel signals presence).
uint8_t *SparseSet_get(SparseSet *set, int32_t entityId);

// Tightly packed entity ids, dense[0..count).
const int32_t *SparseSet_denseEntities(SparseSet *set);

// Tightly packed component data, first count * stride bytes are live.
const uint8_t *SparseSet_denseData(SparseSet *set);


#define SparseSet(...) CONSTRUCTOR_DISPATCH(SparseSet, ##__VA_ARGS__)

#endif