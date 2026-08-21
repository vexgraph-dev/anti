#ifndef OBJECTS_PASSIVE_H
#define OBJECTS_PASSIVE_H

#include <stdint.h>

// objects/passive.h — Lazy-evaluated / computed Passive object wrapper.
// Ported from legacy objects/Passive.java.

typedef uint64_t (*PassiveGetter)(void *userdata);
typedef void (*PassiveSetter)(uint64_t value, void *userdata);

typedef struct Passive Passive;

// Allocate a new Passive instance with lazy getter/setter hooks
Passive *Passive_allocate(PassiveGetter getter, PassiveSetter setter, void *userdata);

// Free passive memory
void Passive_free(Passive *passive);

// Accessors invoking lazy hooks
uint64_t Passive_get(Passive *passive);
void     Passive_set(Passive *passive, uint64_t value);

#endif
