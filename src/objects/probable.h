#ifndef OBJECTS_PROBABLE_H
#define OBJECTS_PROBABLE_H

#include <stdint.h>
#include <stddef.h>
#include "annotation/draft.h"
#include "c23/constructor.h"

typedef struct Probable {
    uintptr_t object;
    uint32_t weight;
    uint32_t total;
} Probable;

;;DRAFT
Probable *Probable_3(uintptr_t object, uint32_t weight, uint32_t total);
Probable *Probable_2(const Probable *init, size_t count);

#define Probable(...) CONSTRUCTOR_DISPATCH(Probable, ##__VA_ARGS__)

void Probable_free(Probable *p);

uintptr_t Probable_object(Probable *p);
uint32_t Probable_weight(Probable *p);
uint32_t Probable_total(Probable *p);

void Probable_setObject(Probable *p, uintptr_t object);
void Probable_setWeight(Probable *p, uint32_t weight);
void Probable_setTotal(Probable *p, uint32_t total);
uintptr_t Probable_get(Probable *p);

#endif
