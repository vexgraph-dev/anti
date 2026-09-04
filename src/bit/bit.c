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
    // Mask to low 48 bits: the freelist design only stores 48-bit addresses.
    // Callers on platforms with tagged/high-bit pointers must fail earlier;
    // masking here keeps the tag bits from being corrupted.
    return (uint64_t) tag << 48 | ((uint64_t) ptr & 0x0000FFFFFFFFFFFFull);
}

// Carve one arena and thread every slot onto the free list.
bool BitPool_init(BitPool *pool, size_t element_size, size_t capacity) {
    if (!pool || element_size == 0 || capacity == 0) return false;

    size_t stride = SLOT_SIZE(element_size);
    (*pool).element_size = element_size;
    (*pool).capacity = capacity;
    (*pool).arena = (uint8_t*) calloc(capacity, stride);
    if (!(*pool).arena) return false;

    // build the free list, chaining every slot through its header
    uint16_t tag = 1;
    uintptr_t head = 0;
    for (size_t i = capacity; i > 0; i--) {
        BitSlot *slot = (BitSlot*) ((*pool).arena + (i - 1) * stride);
        (*slot).type_id = 0;
        (*slot).length = 0;
        atomic_store_explicit(&(*slot).next, pack(tag, head), memory_order_relaxed);
        head = (uintptr_t) slot;
        tag++;
    }
    atomic_store_explicit(&(*pool).free_head, pack(tag, head), memory_order_release);
    return true;
}

void BitPool_shutdown(BitPool *pool) {
    if (pool && (*pool).arena) {
        free((*pool).arena);
        (*pool).arena = nullptr;
    }
}

// Pop a slot: CAS the tagged free head forward, bumping the tag each time so
// the ABA problem can't sneak in. On success the slot is exclusively ours.
void *BitPool_alloc(BitPool *pool, uint32_t type_id) {
    if (!pool) return nullptr;

    uint64_t head_packed = atomic_load_explicit(&(*pool).free_head, memory_order_acquire);
    BitSlot *slot;
    uint16_t new_tag;
    uintptr_t new_ptr;

    for (;;) {
        uintptr_t head_ptr = ptrOf(head_packed);
        if (head_ptr == 0) return nullptr; // pool exhausted

        slot = (BitSlot*) head_ptr;
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
    (*slot).length = (uint32_t) (*pool).element_size;
    return (void*) ((uint8_t*) slot + sizeof(BitSlot));
}

// Push a slot back: store its next, then CAS it onto the head (tag bump
// again). If two threads free at once, one CAS loses and retries.
void BitPool_free(BitPool *pool, void *user_ptr) {
    if (!pool || !user_ptr)
        return;
    // Fail closed: only slots from this arena may be freed.
    if (!BitPool_contains(pool, user_ptr))
        return;

    BitSlot *slot = (BitSlot*) ((uint8_t*) user_ptr - sizeof(BitSlot));
    // Best-effort double-free guard: free slots have type_id==0 and already
    // sit on the free list. Scan the list; if present, refuse the second push
    // that would otherwise create a freelist cycle (same address to 2 owners).
    // Racy under concurrent free, but turns the common single-threaded
    // double-free from corruption into a no-op.
    if ((*slot).type_id == 0) {
        // Full walk (bounded by capacity+1 to avoid spinning on a corrupt list).
        size_t steps = 0;
        uint64_t head_now = atomic_load_explicit(&(*pool).free_head, memory_order_acquire);
        uintptr_t p = ptrOf(head_now);
        while (p != 0 && steps <= (*pool).capacity + 1) {
            if (p == (uintptr_t) slot)
                return;
            const BitSlot *curr = (const BitSlot*) p;
            uint64_t nxt = atomic_load_explicit(&(*curr).next, memory_order_acquire);
            p = ptrOf(nxt);
            steps++;
        }
    }
    (*slot).type_id = 0;

    uint64_t head_packed = atomic_load_explicit(&(*pool).free_head, memory_order_acquire);
    uint16_t tag;

    for (;;) {
        tag = (uint16_t) (tagOf(head_packed) + 1);
        atomic_store_explicit(&(*slot).next, pack(tag, ptrOf(head_packed)),
                              memory_order_release);

        if (atomic_compare_exchange_weak_explicit(&(*pool).free_head, &head_packed,
                                                  pack(tag, (uintptr_t) slot),
                                                  memory_order_release, memory_order_acquire)) {
            return;
        }
    }
}

bool BitPool_contains(const BitPool *pool, const void *user_ptr) {
    if (!pool || !user_ptr || !(*pool).arena)
        return false;
    const uint8_t *p = (const uint8_t*) user_ptr;
    size_t stride = SLOT_SIZE((*pool).element_size);
    const uint8_t *arena = (*pool).arena;
    const uint8_t *end = arena + stride * (*pool).capacity;
    // payload is after BitSlot header
    if (p < arena + sizeof(BitSlot) || p >= end) return false;
    // check alignment to slot payload: payload should be at slot base + header
    if ((p - arena) % stride != sizeof(BitSlot)) {
        // primitives always at base; any interior pointer still considered contained
        // for dispatch we check range only — enough to route to BitPool
        return p >= arena && p < end;
    }
    return true;
}

uint32_t BitPool_type(const BitPool *pool, const void *user_ptr) {
    if (!BitPool_contains(pool, user_ptr))
        return 0;
    const BitSlot *slot = (const BitSlot*) ((const uint8_t*) user_ptr - sizeof(BitSlot));
    return (*slot).type_id;
}

size_t BitPool_length(const BitPool *pool, const void *user_ptr) {
    if (!BitPool_contains(pool, user_ptr))
        return 0;
    const BitSlot *slot = (const BitSlot*) ((const uint8_t*) user_ptr - sizeof(BitSlot));
    return (*slot).length;
}