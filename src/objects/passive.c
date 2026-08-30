#include "objects/passive.h"

#include <string.h>

#include "nio/mem.h"
#include "oop/type.h"

// passive.c — Lazy-evaluated / computed Passive object wrapper implementation.

struct Passive {
    uint64_t      cachedValue;
    PassiveGetter getter;
    PassiveSetter setter;
    void         *userdata;
};

Passive *Passive_allocate(PassiveGetter getter, PassiveSetter setter, void *userdata) {
    uint32_t type = Type_make(FORM_SINGLETON, ID_PASSIVE) | WRAP_PROACTIVE;
    Passive *passive = (Passive *)Memory_alloc(type, sizeof(Passive));
    if (!passive)
        return nullptr;
    (*passive).cachedValue = 0;
    (*passive).getter = getter;
    (*passive).setter = setter;
    (*passive).userdata = userdata;
    return passive;
}

void Passive_free(Passive *passive) {
    if (!passive) return;
    Memory_free(passive);
}

uint64_t Passive_get(Passive *passive) {
    if (!passive) return 0;
    if ((*passive).getter) {
        return (*passive).getter((*passive).userdata);
    }
    return (*passive).cachedValue;
}

void Passive_set(Passive *passive, uint64_t value) {
    if (!passive) return;
    (*passive).cachedValue = value;
    if ((*passive).setter) {
        (*passive).setter(value, (*passive).userdata);
    }
}
