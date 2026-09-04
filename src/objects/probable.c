#include "objects/probable.h"

#include <string.h>
#include "nio/mem.h"
#include "oop/type.h"
#include "util/random.h"
#include "annotation/overview.h"

;;OVERVIEW
/**
 * ============================================================================
 * MODULE: Probable (objects/probable.c)
 * ============================================================================
 * Core subsystem implementation for Probable.
 *
 * FUNCTION REGISTRY:
 * ----------------------------------------------------------------------------
 * Constructors:
 *   - Probable_3(object, weight, total)
 *   - Probable_2(init, count)
 *
 * Core Functions:
 *   - Probable_free(p)
 *   - Probable_object(p)
 *   - Probable_weight(p)
 *   - Probable_total(p)
 *
 * Setters:
 *   - Probable_setObject(p, object)
 *   - Probable_setWeight(p, weight)
 *   - Probable_setTotal(p, total)
 *
 * Getters:
 *   - Probable_get(p)
 * ============================================================================
 */


Probable *Probable_3(uintptr_t object, uint32_t weight, uint32_t total) {
    Probable *p = (Probable*) Memory_alloc(TYPE_PROBABLE, sizeof(Probable));
    if (!p) return nullptr;
    (*p).object = object;
    (*p).weight = weight;
    (*p).total = total;
    return p;
}

Probable *Probable_2(const Probable *init, size_t count) {
    if (count == 0) return nullptr;
    Probable *p = (Probable*) Memory_alloc(TYPE_PROBABLE_ARRAY, sizeof(Probable) * count);
    if (!p) return nullptr;
    
    if (init) {
        for (size_t i = 0; i < count; i++) {
            p[i] = *init;
        }
    } else {
        memset(p, 0, sizeof(Probable) * count);
    }
    return p;
}

void Probable_free(Probable *p) {
    Memory_free(p);
}

uintptr_t Probable_object(Probable *p) {
    if (!p) return 0;
    return (*p).object;
}

uint32_t Probable_weight(Probable *p) {
    if (!p) return 0;
    return (*p).weight;
}

uint32_t Probable_total(Probable *p) {
    if (!p) return 0;
    return (*p).total;
}

void Probable_setObject(Probable *p, uintptr_t object) {
    if (!p) return;
    (*p).object = object;
}

void Probable_setWeight(Probable *p, uint32_t weight) {
    if (!p) return;
    (*p).weight = weight;
}

void Probable_setTotal(Probable *p, uint32_t total) {
    if (!p) return;
    (*p).total = total;
}

uintptr_t Probable_get(Probable *p) {
    return Random_sample(Random_system(), p);
}
