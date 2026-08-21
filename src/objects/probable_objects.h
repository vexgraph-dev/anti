#ifndef OBJECTS_PROBABLE_OBJECTS_H
#define OBJECTS_PROBABLE_OBJECTS_H

#include <stddef.h>
#include <stdint.h>

#include "objects/probable.h"

// objects/probable_objects.h — the ProbableObjects class, ported from
// objects/ProbableObjects.java.
//
// A weighted pool: capacity choices, each with its own weight. The total weight
// is tracked, each slot stores a running cumulative weight, and a draw picks a
// random value in [0, total) then binary-searches the cumulative line.

typedef struct ProbableObjects {
    uint32_t count;         // active choices
    uint32_t totalWeight;   // sum of all weights
    uint32_t capacity;      // maximum choices
    uint32_t pad;
    // capacity slots of 16 bytes: object(8) + cumulativeWeight(4) + weight(4)
    uint8_t slots[];
} ProbableObjects;

// Allocate an empty pool with room for capacity choices.
ProbableObjects *ProbableObjects_allocate(size_t capacity);

void ProbableObjects_free(ProbableObjects *po);

size_t ProbableObjects_size(ProbableObjects *po);
size_t ProbableObjects_capacity(ProbableObjects *po);
uint32_t ProbableObjects_totalWeight(ProbableObjects *po);

// Append a choice. Returns 0 on overflow.
int ProbableObjects_add(ProbableObjects *po, uintptr_t object, uint32_t weight);

// Append the object/weight of an existing Probable.
int ProbableObjects_addProbable(ProbableObjects *po, const Probable *probable);

// Slot accessors (used by Random.probablePool's binary search).
uintptr_t ProbableObjects_objectAt(ProbableObjects *po, size_t index);
uint32_t ProbableObjects_cumulativeAt(ProbableObjects *po, size_t index);
uint32_t ProbableObjects_weightAt(ProbableObjects *po, size_t index);

// Draw a weighted object from the pool using the system RNG.
uintptr_t ProbableObjects_get(ProbableObjects *po);

#endif