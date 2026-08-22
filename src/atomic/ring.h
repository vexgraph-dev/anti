#ifndef THREAD_RING_H
#define THREAD_RING_H

#include <stdbool.h>
#include <stdint.h>

#include "thread/spin.h"

// thread/ring.h — MPMC ring buffer API (Legacy: thread/RingBuffer.java).
//
// A fixed-capacity FIFO whose capacity is rounded to a power of two, so slot
// indexing is `idx & mask` (no modulo). One embedded spinlock serializes
// push/pop; head/tail are atomic counters. Producers push, one or more
// consumers pop, all at zero allocation.

typedef struct RingBuffer {
    SpinLock lock;       // serializes the actual copy in/out
    size_t capacity;        // power of two
    size_t mask;            // capacity - 1, for slot masking
    size_t elem_size;       // bytes per element
    _Atomic size_t head;    // consumer index (monotonic, never wraps back)
    _Atomic size_t tail;    // producer index (monotonic, never wraps back)
    uint8_t *slots;         // capacity * elem_size arena
} RingBuffer;

// Set up a ring for elem_size-byte elements, capacity slots (rounded up to
// pow2). Returns false on bad args/OOM.
bool RingBuffer_init(RingBuffer *ring, size_t elem_size, size_t capacity);
void RingBuffer_shutdown(RingBuffer *ring);

// Copy item into the ring. Returns false if full (caller retries/spins).
bool RingBuffer_push(RingBuffer *ring, const void *item);

// Copy oldest item out into out. Returns false if empty.
bool RingBuffer_pop(RingBuffer *ring, void *out);

#endif