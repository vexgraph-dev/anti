#include "objects/reactive.h"

#include <string.h>

#include "nio/mem.h"
#include "oop/type.h"

// reactive.c — Event-driven Reactive object wrapper implementation.

struct Reactive {
    uint64_t                value;
    ReactiveSetCallback     onSet;
    ReactiveGetCallback     onGet;
    ReactiveChangedCallback onChanged;
    void                   *userdata;
};

Reactive *Reactive_allocate(uint64_t initialValue) {
    uint32_t type = Type_make(FORM_SINGLETON, ID_REACTIVE) | WRAP_REACTIVE;
    Reactive *reactive = (Reactive *)Memory_alloc(type, sizeof(Reactive));
    if (!reactive)
        return nullptr;
    (*reactive).value = initialValue;
    (*reactive).onSet = nullptr;
    (*reactive).onGet = nullptr;
    (*reactive).onChanged = nullptr;
    (*reactive).userdata = nullptr;
    return reactive;
}

void Reactive_free(Reactive *reactive) {
    if (!reactive) return;
    Memory_free(reactive);
}

uint64_t Reactive_get(Reactive *reactive) {
    if (!reactive) return 0;
    if ((*reactive).onGet) {
        (*reactive).onGet((*reactive).value, (*reactive).userdata);
    }
    return (*reactive).value;
}

void Reactive_set(Reactive *reactive, uint64_t value) {
    if (!reactive) return;
    uint64_t oldValue = (*reactive).value;
    (*reactive).value = value;
    if ((*reactive).onSet) {
        (*reactive).onSet(value, (*reactive).userdata);
    }
    if (oldValue != value && (*reactive).onChanged) {
        (*reactive).onChanged(oldValue, value, (*reactive).userdata);
    }
}

void Reactive_setOnSet(Reactive *reactive, ReactiveSetCallback cb, void *userdata) {
    if (!reactive) return;
    (*reactive).onSet = cb;
    (*reactive).userdata = userdata;
}

void Reactive_setOnGet(Reactive *reactive, ReactiveGetCallback cb, void *userdata) {
    if (!reactive) return;
    (*reactive).onGet = cb;
    (*reactive).userdata = userdata;
}

void Reactive_setOnChanged(Reactive *reactive, ReactiveChangedCallback cb, void *userdata) {
    if (!reactive) return;
    (*reactive).onChanged = cb;
    (*reactive).userdata = userdata;
}
