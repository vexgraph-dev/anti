#ifndef BIT_BIT_H
#define BIT_BIT_H

#include <stdatomic.h>
#include <stdbool.h>
#include <stdint.h>
#include <stddef.h>

// bit/bit.h — lockless bit-width pool API (Legacy: bit/Bit64.java).
//
// A pool owns one fixed arena of equal-sized slots. Alloc/free are lock-free
// pushes and pops on a tagged free list. Use one pool per element width
// (Bit8, Bit32, Bit64, ...) so slots never waste space.
//
// Slot layout:
//   [ type_id ][ length ][ next|tag ]   <- header
//   [ payload ... ]                     <- what callers get
// The caller's pointer points at the payload; walking back over the header
// finds next. Upper 16 bits of `next` are an ABA-defeating tag.

typedef struct BitSlot {
    uint32_t type_id;
    uint32_t length;
    _Atomic uint64_t next;   // ABA-tagged freelist next (lower 48 bits ptr, upper 16 tag)
} BitSlot;

typedef struct BitPool {
    size_t element_size;     // bytes per payload slot
    size_t capacity;         // slot count
    uint8_t *arena;          // one calloc'd arena, never grows
    _Atomic uint64_t free_head;  // tagged head of the free list
} BitPool;

// Carve capacity slots of element_size bytes. Returns false on bad args/OOM.
bool BitPool_init(BitPool *pool, size_t element_size, size_t capacity);
void BitPool_shutdown(BitPool *pool);

// Pop a slot from the free list and stamp type_id into its header.
// Returns NULL when the pool is exhausted.
void *BitPool_alloc(BitPool *pool, uint32_t type_id);

// Push a slot back onto the free list. user_ptr must come from this pool.
void BitPool_free(BitPool *pool, void *user_ptr);

#endif