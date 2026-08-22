#ifndef THREAD_ATOMIC_H
#define THREAD_ATOMIC_H

#include <stdbool.h>
#include <stdint.h>
#include <stdatomic.h>

// thread/atomic.h — atomic variables & synchronization (Legacy: thread/Atomic.java).
//
// The legacy allocated off-heap 1/4/8-byte slots because the JVM heap could
// not be trusted. C needs none of that: an atomic IS a struct field, living
// inside whatever owns it — zero allocation by construction, no free path,
// and the type system keeps word sizes honest.
//
// All operations are sequentially consistent by default (the safe choice);
// relax ordering only with a profiler in hand.

typedef struct AtomicBool { _Atomic uint8_t value; } AtomicBool;
typedef struct AtomicInt  { _Atomic int32_t value; } AtomicInt;
typedef struct AtomicLong { _Atomic int64_t value; } AtomicLong;
typedef struct AtomicPtr  { _Atomic(void *) value; } AtomicPtr;

// Initializers: AtomicBool b = ATOMIC_BOOL_INIT(true);
#define ATOMIC_BOOL_INIT(v) { (v) ? 1u : 0u }
#define ATOMIC_INT_INIT(v)  { (v) }
#define ATOMIC_LONG_INIT(v) { (v) }
#define ATOMIC_PTR_INIT(p)  { (p) }

// --- Bool ---
bool  AtomicBool_get(const AtomicBool *a);
void  AtomicBool_set(AtomicBool *a, bool v);
bool  AtomicBool_cas(AtomicBool *a, bool expected, bool v);
bool  AtomicBool_exchange(AtomicBool *a, bool v);          // getAndSet
bool  AtomicBool_toggle(AtomicBool *a);                     // returns NEW value

// --- Int ---
int32_t AtomicInt_get(const AtomicInt *a);
void    AtomicInt_set(AtomicInt *a, int32_t v);
bool    AtomicInt_cas(AtomicInt *a, int32_t expected, int32_t v);
int32_t AtomicInt_exchange(AtomicInt *a, int32_t v);
int32_t AtomicInt_add(AtomicInt *a, int32_t delta);         // returns OLD value
int32_t AtomicInt_increment(AtomicInt *a);                  // returns NEW value
int32_t AtomicInt_decrement(AtomicInt *a);                  // returns NEW value

// --- Long ---
int64_t AtomicLong_get(const AtomicLong *a);
void    AtomicLong_set(AtomicLong *a, int64_t v);
bool    AtomicLong_cas(AtomicLong *a, int64_t expected, int64_t v);
int64_t AtomicLong_exchange(AtomicLong *a, int64_t v);
int64_t AtomicLong_add(AtomicLong *a, int64_t delta);       // returns OLD value

// --- Pointer (the "everything is a pointer" swap cell) ---
void  *AtomicPtr_get(const AtomicPtr *a);
void  *AtomicPtr_exchange(AtomicPtr *a, void *v);
bool   AtomicPtr_cas(AtomicPtr *a, void *expected, void *v);

// --- Spin waits (Legacy: Atomic.spinWait) ---
// Burn until the boolean reaches the target. Use for sub-microsecond handoffs;
// anything longer should park on a condition variable instead.
void AtomicBool_spinUntil(const AtomicBool *a, bool target);

// Bounded variant: true if reached before timeoutNanos, false on expiry.
bool AtomicBool_spinUntilFor(const AtomicBool *a, bool target, uint64_t timeoutNanos);

#endif
