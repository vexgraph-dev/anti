#include "objects/probable.h"

#include "nio/mem.h"
#include "oop/type.h"
#include "util/random.h"

// probable.c — Probable port (Legacy: objects/Probable.java).

Probable *Probable_allocate(uintptr_t object, uint32_t weight, uint32_t total) {
    Probable *p = (Probable *)Memory_alloc(TYPE_PROBABLE, sizeof(Probable));
    if (!p) return NULL;
    (*p).object = object;
    (*p).weight = weight;
    (*p).total = total;
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