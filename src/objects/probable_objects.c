#include "objects/probable_objects.h"

#include <string.h>

#include "nio/mem.h"
#include "oop/type.h"
#include "util/random.h"
#include "annotation/overview.h"

;;OVERVIEW
/**
 * ============================================================================
 * MODULE: Probable_objects (objects/probable_objects.c)
 * ============================================================================
 * the ProbableObjects class, ported from
 *
 * FUNCTION REGISTRY:
 * ----------------------------------------------------------------------------
 * Constructors:
 *   - ProbableObjects_1(capacity)
 *   - ProbableObjects_2(init, count)
 *
 * Core Functions:
 *   - ProbableObjects_free(po)
 *   - ProbableObjects_size(po)
 *   - ProbableObjects_capacity(po)
 *   - ProbableObjects_totalWeight(po)
 *   - ProbableObjects_add(po, object, weight)
 *   - ProbableObjects_addProbable(po, probable)
 *   - ProbableObjects_objectAt(po, index)
 *   - ProbableObjects_cumulativeAt(po, index)
 *   - ProbableObjects_weightAt(po, index)
 *
 * Getters:
 *   - ProbableObjects_get(po)
 * ============================================================================
 */


// probable_objects.c — ProbableObjects port (Legacy: objects/ProbableObjects.java).

static const size_t SLOT_SIZE = 16;

ProbableObjects *ProbableObjects_1(size_t capacity) {
    size_t bytes = sizeof(ProbableObjects) + capacity * SLOT_SIZE;
    ProbableObjects *po = (ProbableObjects*) Memory_alloc(TYPE_PROBABLE_OBJECTS, bytes);
    if (!po) return nullptr;
    memset(po, 0, bytes);
    (*po).capacity = (uint32_t)capacity;
    return po;
}


ProbableObjects *ProbableObjects_2(const ProbableObjects *init, size_t count) {
    if (count == 0) return nullptr;
    ProbableObjects *p = (ProbableObjects*) Memory_alloc(TYPE_PROBABLE_OBJECTS_ARRAY, sizeof(ProbableObjects) * count);
    if (!p) return nullptr;
    if (init) {
        for (size_t i = 0; i < count; i++) p[i] = *init;
    } else {
        memset(p, 0, sizeof(ProbableObjects) * count);
    }
    return p;
}
void ProbableObjects_free(ProbableObjects *po) {
    Memory_free(po);
}

size_t ProbableObjects_size(ProbableObjects *po) {
    if (!po) return 0;
    return (*po).count;
}

size_t ProbableObjects_capacity(ProbableObjects *po) {
    if (!po) return 0;
    return (*po).capacity;
}

uint32_t ProbableObjects_totalWeight(ProbableObjects *po) {
    if (!po) return 0;
    return (*po).totalWeight;
}

static uint8_t *slotAt(ProbableObjects *po, size_t index) {
    return (*po).slots + index * SLOT_SIZE;
}

int ProbableObjects_add(ProbableObjects *po, uintptr_t object, uint32_t weight) {
    if (!po) return 0;
    if ((*po).count >= (*po).capacity)
        return 0;

    uint8_t *slot = slotAt(po, (*po).count);
    *(uintptr_t*) slot = object;

    uint32_t running = (*po).totalWeight + weight;
    *(uint32_t*) (slot + 8) = running;
    *(uint32_t*) (slot + 12) = weight;

    (*po).totalWeight = running;
    (*po).count++;
    return 1;
}

int ProbableObjects_addProbable(ProbableObjects *po, const Probable *probable) {
    if (!probable) return 0;
    return ProbableObjects_add(po, (*probable).object, (*probable).weight);
}

uintptr_t ProbableObjects_objectAt(ProbableObjects *po, size_t index) {
    if (!po || index >= (*po).count) return 0;
    return *(uintptr_t*) slotAt(po, index);
}

uint32_t ProbableObjects_cumulativeAt(ProbableObjects *po, size_t index) {
    if (!po || index >= (*po).count) return 0;
    return *(uint32_t*) (slotAt(po, index) + 8);
}

uint32_t ProbableObjects_weightAt(ProbableObjects *po, size_t index) {
    if (!po || index >= (*po).count) return 0;
    return *(uint32_t*) (slotAt(po, index) + 12);
}

uintptr_t ProbableObjects_get(ProbableObjects *po) {
    return Random_probablePool(Random_system(), po);
}
