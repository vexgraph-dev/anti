#include "objects/future.h"

#include <stdatomic.h>
#include <string.h>

#include "nio/mem.h"
#include "oop/type.h"
#include "annotation/overview.h"

;;OVERVIEW
/**
 * ============================================================================
 * MODULE: Future (objects/future.c)
 * ============================================================================
 * Asynchronous single-assignment Future object wrapper.
 *
 * FUNCTION REGISTRY:
 * ----------------------------------------------------------------------------
 * Constructors:
 *   - Future_0(void)
 *   - Future_2(init, count)
 *
 * Core Functions:
 *   - Future_free(future)
 *
 * Setters:
 *   - Future_setDesiredValue(future, value)
 *
 * Getters:
 *   - Future_isGiven(future)
 *   - Future_get(future)
 * ============================================================================
 */


// future.c — Asynchronous single-assignment Future object wrapper implementation.
typedef struct Future {
    atomic_bool isGiven;
    uint64_t    value;
} Future;

Future *Future_0(void) {
    uint32_t type = Type_make(FORM_SINGLETON, ID_FUTURE) | WRAP2_FUTURE;
    Future *future = Memory_alloc(type, sizeof(Future));
    if (!future)
        return nullptr;
    atomic_init(&(*future).isGiven, false);
    (*future).value = 0;
    return future;
}


Future *Future_2(const Future *init, size_t count) {
    if (count == 0) return nullptr;
    Future *p = (Future*) Memory_alloc(TYPE_FUTURE_ARRAY, sizeof(Future) * count);
    if (!p) return nullptr;
    if (init) {
        for (size_t i = 0; i < count; i++) p[i] = *init;
    } else {
        memset(p, 0, sizeof(Future) * count);
    }
    return p;
}
void Future_free(Future *future) {
    if (!future) return;
    Memory_free(future);
}

bool Future_isGiven(const Future *future) {
    if (!future) return false;
    return atomic_load_explicit(&(*future).isGiven, memory_order_acquire);
}

uint64_t Future_get(const Future *future) {
    if (!future) return 0;
    return (*future).value;
}

bool Future_setDesiredValue(Future *future, uint64_t value) {
    if (!future) return false;
    bool expected = false;
    if (atomic_compare_exchange_strong_explicit(
            &(*future).isGiven, &expected, true,
            memory_order_acq_rel, memory_order_acquire)) {
        (*future).value = value;
        return true;
    }
    return false;
}
