#ifndef OBJECTS_PROBABLE_H
#define OBJECTS_PROBABLE_H

#include <stdint.h>

// objects/probable.h — the Probable class, ported from objects/Probable.java.
//
// One weighted choice: an object pointer plus its chance weight and the total
// pool weight it participates in. Random.sample flips the weight/total odds and
// returns the object on a hit, 0 on a miss.

typedef struct Probable {
    uintptr_t object;   // target object choice
    uint32_t weight;    // relative chance of this choice
    uint32_t total;     // cumulative total weight of its pool
} Probable;

// Allocate a weighted choice (object, weight, pool total).
Probable *Probable_allocate(uintptr_t object, uint32_t weight, uint32_t total);

void Probable_free(Probable *p);

uintptr_t Probable_object(Probable *p);
uint32_t Probable_weight(Probable *p);
uint32_t Probable_total(Probable *p);
void Probable_setObject(Probable *p, uintptr_t object);
void Probable_setWeight(Probable *p, uint32_t weight);
void Probable_setTotal(Probable *p, uint32_t total);

// Roll the system RNG against this choice; object on hit, 0 on miss.
uintptr_t Probable_get(Probable *p);

#endif