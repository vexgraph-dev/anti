// ring.c — MPMC ring buffer (thread/RingBuffer.java port).
//
// A fixed-capacity FIFO with power-of-two capacity so the slot index is a
// cheap mask instead of a modulo. One spinlock guards the whole structure
// (the legacy used the same idea: "spinlock-coordinated"). head/tail are
// atomic size_t counters; the lock serializes the actual push/pop so the
// reader never sees torn data.
//
// Full/empty are detected by head==tail (empty) vs. tail-head==capacity (full),
// which is why capacity is *at least* as large as any burst you push.
// Lesson 18 replaces the spinlock with per-thread slots later; this is the
// correct, simple version that works today.

#include "atomic/ring.h"

#include <stdlib.h>
#include <string.h>

// Round up to the next power of two (clamp: 1..2^64). Classic bit trick —
// same nextPowerOfTwo the Java version had.
static size_t nextPow2(size_t value) {
    if (value <= 0) return 1;
    value--;
    value |= value >> 1;
    value |= value >> 2;
    value |= value >> 4;
    value |= value >> 8;
    value |= value >> 16;
    value |= value >> 32;
    return value + 1;
}

bool RingBuffer_init(RingBuffer *ring, size_t elem_size, size_t capacity) {
    if (!ring || elem_size == 0) return false;

    // Round the request up to a power of two; slot = idx & mask.
    size_t cap = nextPow2(capacity);
    (*ring).capacity = cap;
    (*ring).mask = cap - 1;
    (*ring).elem_size = elem_size;
    (*ring).lock = SPIN_LOCK_INIT;
    atomic_store_explicit(&(*ring).head, 0, memory_order_relaxed);
    atomic_store_explicit(&(*ring).tail, 0, memory_order_relaxed);

    (*ring).slots = (uint8_t*) calloc(cap, elem_size);
    return (*ring).slots != nullptr;
}

void RingBuffer_shutdown(RingBuffer *ring) {
    if (ring && (*ring).slots) {
        free((*ring).slots);
        (*ring).slots = nullptr;
    }
}

// Push one item. Locked: the whole copy in must be atomic to readers.
bool RingBuffer_push(RingBuffer *ring, const void *item) {
    if (!ring || !item) return false;

    SpinLock_lock(&(*ring).lock);

    size_t tail = atomic_load_explicit(&(*ring).tail, memory_order_relaxed);
    size_t head = atomic_load_explicit(&(*ring).head, memory_order_relaxed);
    if (tail - head >= (*ring).capacity) {
        SpinLock_unlock(&(*ring).lock);
        return false; // full
    }

    size_t slot = tail & (*ring).mask;
    memcpy((*ring).slots + slot * (*ring).elem_size, item, (*ring).elem_size);
    atomic_store_explicit(&(*ring).tail, tail + 1, memory_order_release);

    SpinLock_unlock(&(*ring).lock);
    return true;
}

// Pop the oldest item. Locked: we can't let a reader see a half-written slot.
bool RingBuffer_pop(RingBuffer *ring, void *out) {
    if (!ring || !out) return false;

    SpinLock_lock(&(*ring).lock);

    size_t head = atomic_load_explicit(&(*ring).head, memory_order_relaxed);
    size_t tail = atomic_load_explicit(&(*ring).tail, memory_order_relaxed);
    if (head == tail) {
        SpinLock_unlock(&(*ring).lock);
        return false; // empty
    }

    size_t slot = head & (*ring).mask;
    memcpy(out, (*ring).slots + slot * (*ring).elem_size, (*ring).elem_size);
    atomic_store_explicit(&(*ring).head, head + 1, memory_order_release);

    SpinLock_unlock(&(*ring).lock);
    return true;
}