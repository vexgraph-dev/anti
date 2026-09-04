#include "objects/passive.h"

#include <string.h>

#include "nio/mem.h"
#include "oop/type.h"
#include "annotation/overview.h"

;;OVERVIEW
/**
 * ============================================================================
 * MODULE: Passive (objects/passive.c)
 * ============================================================================
 * Lazy-evaluated / computed Passive object wrapper.
 *
 * FUNCTION REGISTRY:
 * ----------------------------------------------------------------------------
 * Constructors:
 *   - Passive_3(getter, setter, userdata)
 *   - Passive_2(init, count)
 *
 * Core Functions:
 *   - Passive_free(passive)
 *
 * Setters:
 *   - Passive_set(passive, value)
 *
 * Getters:
 *   - Passive_get(passive)
 * ============================================================================
 */


// passive.c — Lazy-evaluated / computed Passive object wrapper implementation.

struct Passive {
    uint64_t      cachedValue;
    PassiveGetter getter;
    PassiveSetter setter;
    void         *userdata;
};

Passive *Passive_3(PassiveGetter getter, PassiveSetter setter, void *userdata) {
    uint32_t type = Type_make(FORM_SINGLETON, ID_PASSIVE) | WRAP_PROACTIVE;
    Passive *passive = (Passive*) Memory_alloc(type, sizeof(Passive));
    if (!passive)
        return nullptr;
    (*passive).cachedValue = 0;
    (*passive).getter = getter;
    (*passive).setter = setter;
    (*passive).userdata = userdata;
    return passive;
}


Passive *Passive_2(const Passive *init, size_t count) {
    if (count == 0) return nullptr;
    Passive *p = (Passive*) Memory_alloc(TYPE_PASSIVE_ARRAY, sizeof(Passive) * count);
    if (!p) return nullptr;
    if (init) {
        for (size_t i = 0; i < count; i++) p[i] = *init;
    } else {
        memset(p, 0, sizeof(Passive) * count);
    }
    return p;
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
