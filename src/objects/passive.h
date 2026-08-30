#ifndef OBJECTS_PASSIVE_H
#define OBJECTS_PASSIVE_H

#include <stdint.h>
#include <stddef.h>
#include "c23/constructor.h"

// objects/passive.h — Lazy-evaluated / computed Passive object wrapper.
// Ported from legacy objects/Passive.java.

typedef uint64_t (*PassiveGetter)(void *userdata);
typedef void (*PassiveSetter)(uint64_t value, void *userdata);

typedef struct Passive Passive;

// Allocate a new Passive instance with lazy getter/setter hooks
Passive *Passive_3(PassiveGetter getter, PassiveSetter setter, void *userdata);

// Free passive memory

Passive *Passive_2(const Passive *init, size_t count);

#define Passive(...) CONSTRUCTOR_DISPATCH(Passive, ##__VA_ARGS__)

void Passive_free(Passive *passive);

// Accessors invoking lazy hooks
uint64_t Passive_get(Passive *passive);
void     Passive_set(Passive *passive, uint64_t value);

#endif
