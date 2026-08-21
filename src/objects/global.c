#include "objects/global.h"

#include <stdatomic.h>

#include "nio/mem.h"
#include "oop/type.h"

// global.c — Atomic global pointer/variable object wrapper implementation.

struct Global {
    atomic_uint_least64_t value;
};

Global *Global_allocate(uint64_t initialValue) {
    uint32_t type = Type_make(FORM_SINGLETON, ID_GLOBAL) | MOD_GLOBAL;
    Global *global = (Global *)Memory_alloc(type, sizeof(Global));
    if (!global)
        return NULL;
    atomic_init(&(*global).value, initialValue);
    return global;
}

void Global_free(Global *global) {
    if (!global) return;
    Memory_free(global);
}

uint64_t Global_get(const Global *global) {
    if (!global) return 0;
    return atomic_load_explicit(&(*global).value, memory_order_acquire);
}

void Global_set(Global *global, uint64_t value) {
    if (!global) return;
    atomic_store_explicit(&(*global).value, value, memory_order_release);
}

bool Global_compareAndSet(Global *global, uint64_t expected, uint64_t value) {
    if (!global) return false;
    uint64_t exp = expected;
    return atomic_compare_exchange_strong_explicit(
        &(*global).value, &exp, value,
        memory_order_acq_rel, memory_order_acquire);
}
