// thread/atomic.c — atomic variables & synchronization (Legacy: thread/Atomic.java port).
//
// Every operation is one C23 stdatomic intrinsic; the module exists so the
// engine reads like the legacy map (Atomic_cas / AtomicInt_add / spin waits)
// instead of scattering raw stdatomic builtins across subsystems.

#include "atomic/atomic.h"

#include <sched.h>

#if defined(__SSE2__)
#include <immintrin.h>
#endif

#include "time/nanotime.h"
#include "annotation/overview.h"

;;OVERVIEW
/**
 * ============================================================================
 * CLASS: Atomic (atomic/atomic.c)
 * LEVEL: L4 — Self-Management (atomic variables/synchronization)
 * ============================================================================
 * atomic variables & synchronization (Legacy: thread/Atomic.java).
 *
 * STRUCT FIELDS (Mirroring atomic/atomic.h):
 * ----------------------------------------------------------------------------
 *   AtomicBool {
 *     _Atomic uint8_t value; // payload value
 *   }
 *   AtomicInt {
 *     _Atomic int32_t value; // payload value
 *   }
 *   AtomicLong {
 *     _Atomic int64_t value; // payload value
 *   }
 *   AtomicPtr {
 *     _Atomic(void*) value; // payload value
 *   }
 *
 * FUNCTION REGISTRY:
 * ----------------------------------------------------------------------------
 * Core Functions:
 *   - AtomicBool_cas(a, expected, v)
 *   - AtomicBool_exchange(a, v)
 *   - AtomicBool_toggle(a)
 *   - AtomicInt_cas(a, expected, v)
 *   - AtomicInt_exchange(a, v)
 *   - AtomicInt_add(a, delta)
 *   - AtomicInt_increment(a)
 *   - AtomicInt_decrement(a)
 *   - AtomicLong_cas(a, expected, v)
 *   - AtomicLong_exchange(a, v)
 *   - AtomicLong_add(a, delta)
 *   - AtomicPtr_exchange(a, v)
 *   - AtomicPtr_cas(a, expected, v)
 *   - AtomicBool_spinUntil(a, target)
 *   - AtomicBool_spinUntilFor(a, target, timeoutNanos)
 *
 * Setters:
 *   - AtomicBool_set(a, v)
 *   - AtomicInt_set(a, v)
 *   - AtomicLong_set(a, v)
 *
 * Getters:
 *   - AtomicBool_get(a)
 *   - AtomicInt_get(a)
 *   - AtomicLong_get(a)
 *   - AtomicPtr_get(a)
 * ============================================================================
 */


// --- Bool ---

bool AtomicBool_get(const AtomicBool *a) {
    return atomic_load(&(*a).value) != 0;
}

void AtomicBool_set(AtomicBool *a, bool v) {
    atomic_store(&(*a).value, v ? 1u : 0u);
}

bool AtomicBool_cas(AtomicBool *a, bool expected, bool v) {
    uint8_t e = expected ? 1u : 0u;
    return atomic_compare_exchange_strong(&(*a).value, &e, v ? 1u : 0u);
}

bool AtomicBool_exchange(AtomicBool *a, bool v) {
    return atomic_exchange(&(*a).value, v ? 1u : 0u) != 0;
}

bool AtomicBool_toggle(AtomicBool *a) {
    while (true) {
        uint8_t cur = atomic_load(&(*a).value);
        uint8_t next = cur ^ 1u;
        if (atomic_compare_exchange_weak(&(*a).value, &cur, next))
            return next != 0;
    }
}

// --- Int ---

int32_t AtomicInt_get(const AtomicInt *a) {
    return atomic_load(&(*a).value);
}

void AtomicInt_set(AtomicInt *a, int32_t v) {
    atomic_store(&(*a).value, v);
}

bool AtomicInt_cas(AtomicInt *a, int32_t expected, int32_t v) {
    int32_t e = expected;
    return atomic_compare_exchange_strong(&(*a).value, &e, v);
}

int32_t AtomicInt_exchange(AtomicInt *a, int32_t v) {
    return atomic_exchange(&(*a).value, v);
}

int32_t AtomicInt_add(AtomicInt *a, int32_t delta) {
    return atomic_fetch_add(&(*a).value, delta);
}

int32_t AtomicInt_increment(AtomicInt *a) {
    return atomic_fetch_add(&(*a).value, 1) + 1;
}

int32_t AtomicInt_decrement(AtomicInt *a) {
    return atomic_fetch_sub(&(*a).value, 1) - 1;
}

// --- Long ---

int64_t AtomicLong_get(const AtomicLong *a) {
    return atomic_load(&(*a).value);
}

void AtomicLong_set(AtomicLong *a, int64_t v) {
    atomic_store(&(*a).value, v);
}

bool AtomicLong_cas(AtomicLong *a, int64_t expected, int64_t v) {
    int64_t e = expected;
    return atomic_compare_exchange_strong(&(*a).value, &e, v);
}

int64_t AtomicLong_exchange(AtomicLong *a, int64_t v) {
    return atomic_exchange(&(*a).value, v);
}

int64_t AtomicLong_add(AtomicLong *a, int64_t delta) {
    return atomic_fetch_add(&(*a).value, delta);
}

// --- Pointer ---

void *AtomicPtr_get(const AtomicPtr *a) {
    return atomic_load(&(*a).value);
}

void *AtomicPtr_exchange(AtomicPtr *a, void *v) {
    return atomic_exchange(&(*a).value, v);
}

bool AtomicPtr_cas(AtomicPtr *a, void *expected, void *v) {
    void *e = expected;
    return atomic_compare_exchange_strong(&(*a).value, &e, v);
}

// --- Spin waits ---

void AtomicBool_spinUntil(const AtomicBool *a, bool target) {
    while (AtomicBool_get(a) != target) {
        // Yield the core politely; on arm64 this lowers to a `yield`.
#if defined(__SSE2__)
        _mm_pause();
#elif defined(__aarch64__) || defined(__arm__)
        __asm__ __volatile__("yield");
#else
        sched_yield();
#endif
    }
}

bool AtomicBool_spinUntilFor(const AtomicBool *a, bool target, uint64_t timeoutNanos) {
    uint64_t deadline = NanoTime_now() + timeoutNanos;
    while (AtomicBool_get(a) != target) {
        if (NanoTime_now() >= deadline) return false;
#if defined(__SSE2__)
        _mm_pause();
#elif defined(__aarch64__) || defined(__arm__)
        __asm__ __volatile__("yield");
#else
        sched_yield();
#endif
    }
    return true;
}
