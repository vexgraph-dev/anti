// bit.c — the lockless bit-width pool, ported from bit/Bit64.java.
//
// A fixed arena of same-sized slots with a lockless free list. Each slot has
// a small header whose top field is an ABA-TAGGED pointer to the next free
// slot:
//
//     [ type_id ][ length ][ next | tag ]   (next = lower 48 bits address)
//
// Why the tag? Two threads freeing simultaneously could otherwise hit the ABA
// problem: thread A pops slot X, thread B recycles X back onto the list, then
// A's CAS succeeds against a list head that "looks the same" but changed. The
// 16-bit tag bumping on every push/pop makes that CAS fail instead of silently
// corrupting the list. This is the same trick the legacy Java used (Lesson 7
// + Lesson 13 doctrine: zero allocation, lock-free, and the tag defeats ABA).

#include "bit/bit.h"

#include <stdlib.h>
#include <string.h>

#define SLOT_SIZE(elem) (sizeof(BitSlot) + (elem) + 7u & ~7u)

// Unpack a packed (tag,ptr) word into its two halves.
static uint16_t tagOf(uint64_t packed) {
    return (uint16_t)(packed >> 48);
}

static uintptr_t ptrOf(uint64_t packed) {
    return packed & 0x0000FFFFFFFFFFFFull;
}

static uint64_t pack(uint16_t tag, uintptr_t ptr) {
    return (uint64_t)tag << 48 | (uint64_t)ptr;
}

// Carve one arena and thread every slot onto the free list.
bool BitPool_init(BitPool *pool, size_t element_size, size_t capacity) {
    if (!pool || element_size == 0 || capacity == 0) return false;

    size_t stride = SLOT_SIZE(element_size);
    (*pool).element_size = element_size;
    (*pool).capacity = capacity;
    (*pool).arena = (uint8_t *)calloc(capacity, stride);
    if (!(*pool).arena) return false;

    // build the free list, chaining every slot through its header
    uint16_t tag = 1;
    uintptr_t head = 0;
    for (size_t i = capacity; i > 0; i--) {
        BitSlot *slot = (BitSlot *)((*pool).arena + (i - 1) * stride);
        (*slot).type_id = 0;
        (*slot).length = 0;
        atomic_store_explicit(&(*slot).next, pack(tag, head), memory_order_relaxed);
        head = (uintptr_t)slot;
        tag++;
    }
    atomic_store_explicit(&(*pool).free_head, pack(tag, head), memory_order_release);
    return true;
}

void BitPool_shutdown(BitPool *pool) {
    if (pool && (*pool).arena) {
        free((*pool).arena);
        (*pool).arena = NULL;
    }
}

// Pop a slot: CAS the tagged free head forward, bumping the tag each time so
// the ABA problem can't sneak in. On success the slot is exclusively ours.
void *BitPool_alloc(BitPool *pool, uint32_t type_id) {
    if (!pool) return NULL;

    uint64_t head_packed = atomic_load_explicit(&(*pool).free_head, memory_order_acquire);
    BitSlot *slot;
    uint16_t new_tag;
    uintptr_t new_ptr;

    for (;;) {
        uintptr_t head_ptr = ptrOf(head_packed);
        if (head_ptr == 0) return NULL; // pool exhausted

        slot = (BitSlot *)head_ptr;
        uint64_t next_packed = atomic_load_explicit(&(*slot).next, memory_order_acquire);
        new_tag = (uint16_t)(tagOf(head_packed) + 1);
        new_ptr = ptrOf(next_packed);

        if (atomic_compare_exchange_weak_explicit(&(*pool).free_head, &head_packed,
                                                  pack(new_tag, new_ptr),
                                                  memory_order_release, memory_order_acquire)) {
            break;
        }
    }

    (*slot).type_id = type_id;
    return (void *)((uint8_t *)slot + sizeof(BitSlot));
}

// Push a slot back: store its next, then CAS it onto the head (tag bump
// again). If two threads free at once, one CAS loses and retries.
void BitPool_free(BitPool *pool, void *user_ptr) {
    if (!pool || !user_ptr) return;

    BitSlot *slot = (BitSlot *)((uint8_t *)user_ptr - sizeof(BitSlot));
    (*slot).type_id = 0;

    uint64_t head_packed = atomic_load_explicit(&(*pool).free_head, memory_order_acquire);
    uint16_t tag;

    for (;;) {
        tag = (uint16_t)(tagOf(head_packed) + 1);
        atomic_store_explicit(&(*slot).next, pack(tag, ptrOf(head_packed)),
                              memory_order_release);

        if (atomic_compare_exchange_weak_explicit(&(*pool).free_head, &head_packed,
                                                  pack(tag, (uintptr_t)slot),
                                                  memory_order_release, memory_order_acquire)) {
            return;
        }
    }
}