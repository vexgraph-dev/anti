#ifndef THREAD_SPIN_H
#define THREAD_SPIN_H

#include <stdatomic.h>
#include <stdbool.h>
#include <stdint.h>

// thread/spin.h — spinlock API (Legacy: thread/SpinLock.java).
//
// A single atomic word: 0 = free, else (threadId<<1)|1 (owner-encoded). One
// word means the lock can be embedded inside other structs (ring buffers,
// pool headers) with no separate allocation.
//
// Ordering: the successful CAS is memory_order_acquire, unlock is
// memory_order_release. On ARM the hardware reorders, so relaxed would be
// wrong (see Lesson 20).

typedef struct anti_spin {
    atomic_uint word;
} anti_spin_t;

// Zero-initialized lock usable as a field initializer / global.
#define ANTI_SPIN_INIT ((anti_spin_t){ .word = 0 })

// Block until acquired. Unbounded — prefer the timeout variant in long paths.
void anti_spin_lock(anti_spin_t *lock);

// Try once: returns true if acquired, false if held by someone else.
bool anti_spin_try_lock(anti_spin_t *lock);

// Try with a deadline in nanoseconds. Returns false on timeout (never spins
// forever); pass -1 for "spin forever".
bool anti_spin_try_lock_timeout(anti_spin_t *lock, int64_t timeout_nanos);

// Release. Caller must own the lock.
void anti_spin_unlock(anti_spin_t *lock);

// Non-destructive check: is anyone holding this lock right now?
bool anti_spin_is_locked(const anti_spin_t *lock);

#endif