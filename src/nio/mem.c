#include "nio/mem.h"
#include "annotation/overview.h"
#include "atomic/spin.h"

#include <stdbool.h>
#include <stdint.h>
#include <stdlib.h>
#include <string.h>

;;OVERVIEW
/**
 * ============================================================================
 * CLASS: ForeignMemory (nio/mem)
 * ============================================================================
 * Pre-allocated Master Arena and Size-Class Slab Allocator fulfilling the
 * Anti Paradigm: zero steady-state malloc, cache-hot slot recycling, and
 * 16-byte negative pointer math.
 *
 * FUNCTION REGISTRY:
 * ----------------------------------------------------------------------------
 * Core Functions:
 *   - Memory_init(totalBytes)
 *   - Memory_alloc(typeId, numBytes)
 *   - Memory_realloc(userPtr, newBytes)
 *   - Memory_free(userPtr)
 *   - Memory_freeAll(void)
 *   - Memory_length(userPtr)
 *   - Memory_type(userPtr)
 *   - Memory_findAll(typeId, outArray, maxCount)
 * ============================================================================
 */

#define SLAB_COUNT 7
#define SLAB_LARGE 0xFFFFFFFFu
#define SLAB_SYSTEM 0xFFFFFFFEu
#define ANTI_ARENA_DEFAULT_SIZE (64 * 1024 * 1024) // 64 MB master arena

typedef struct FreeNode {
    struct FreeNode *next;
} FreeNode;

typedef struct SlabClass {
    uint32_t slot_size;
    uint32_t capacity;
    uint32_t count;
    uint8_t *arena;
    FreeNode *free_head;
    SpinLock lock;
} SlabClass;

static SlabClass s_slabs[SLAB_COUNT] = {
    { .slot_size = 64,   .capacity = 32768, .count = 0, .arena = nullptr, .free_head = nullptr, .lock = SPIN_LOCK_INIT }, // 2 MB
    { .slot_size = 128,  .capacity = 32768, .count = 0, .arena = nullptr, .free_head = nullptr, .lock = SPIN_LOCK_INIT }, // 4 MB
    { .slot_size = 256,  .capacity = 16384, .count = 0, .arena = nullptr, .free_head = nullptr, .lock = SPIN_LOCK_INIT }, // 4 MB
    { .slot_size = 512,  .capacity = 8192,  .count = 0, .arena = nullptr, .free_head = nullptr, .lock = SPIN_LOCK_INIT }, // 4 MB
    { .slot_size = 1024, .capacity = 4096,  .count = 0, .arena = nullptr, .free_head = nullptr, .lock = SPIN_LOCK_INIT }, // 4 MB
    { .slot_size = 2048, .capacity = 2048,  .count = 0, .arena = nullptr, .free_head = nullptr, .lock = SPIN_LOCK_INIT }, // 4 MB
    { .slot_size = 4096, .capacity = 2048,  .count = 0, .arena = nullptr, .free_head = nullptr, .lock = SPIN_LOCK_INIT }, // 8 MB
};

static uint8_t *s_masterArena = nullptr;
static size_t s_masterCapacity = 0;
static uint8_t *s_bumpArena = nullptr;
static size_t s_bumpCapacity = 0;
static size_t s_bumpOffset = 0;
static SpinLock s_bumpLock = SPIN_LOCK_INIT;
static SpinLock s_initLock = SPIN_LOCK_INIT;

bool Memory_init(size_t totalBytes) {
    SpinLock_lock(&s_initLock);
    if (s_masterArena) {
        SpinLock_unlock(&s_initLock);
        return true;
    }

    size_t cap = totalBytes > 0 ? totalBytes : ANTI_ARENA_DEFAULT_SIZE;
    s_masterArena = (uint8_t*) malloc(cap);
    if (!s_masterArena) {
        SpinLock_unlock(&s_initLock);
        return false;
    }
    s_masterCapacity = cap;

    uint8_t *cur = s_masterArena;
    for (size_t s = 0; s < SLAB_COUNT; s++) {
        SlabClass *slab = &s_slabs[s];
        (*slab).arena = cur;
        (*slab).free_head = nullptr;
        (*slab).count = 0;
        (*slab).lock = SPIN_LOCK_INIT;

        uint32_t sz = (*slab).slot_size;
        for (size_t i = (*slab).capacity; i > 0; i--) {
            uint8_t *slot_ptr = (*slab).arena + (i - 1) * sz;
            MemoryHeader *h = (MemoryHeader*) slot_ptr;
            (*h).magic = 0;
            FreeNode *node = (FreeNode*) (slot_ptr + sizeof(MemoryHeader));
            (*node).next = (*slab).free_head;
            (*slab).free_head = node;
        }
        cur += (size_t)(*slab).capacity * sz;
    }

    s_bumpArena = cur;
    s_bumpCapacity = s_masterCapacity > (size_t)(cur - s_masterArena) ? (s_masterCapacity - (size_t)(cur - s_masterArena)) : 0;
    s_bumpOffset = 0;
    s_bumpLock = SPIN_LOCK_INIT;

    SpinLock_unlock(&s_initLock);
    return true;
}

static inline void ensure_initialized(void) {
    if (!s_masterArena) {
        Memory_init(ANTI_ARENA_DEFAULT_SIZE);
    }
}

static inline int find_slab(size_t payload_bytes) {
    size_t needed = payload_bytes + sizeof(MemoryHeader);
    if (needed <= 64)   return 0;
    if (needed <= 128)  return 1;
    if (needed <= 256)  return 2;
    if (needed <= 512)  return 3;
    if (needed <= 1024) return 4;
    if (needed <= 2048) return 5;
    if (needed <= 4096) return 6;
    return -1;
}

void *Memory_alloc(uint32_t typeId, size_t numBytes) {
    if (numBytes > UINT32_MAX)
        return nullptr;
    ensure_initialized();

    int s_idx = find_slab(numBytes);
    if (s_idx >= 0) {
        SlabClass *slab = &s_slabs[s_idx];
        SpinLock_lock(&(*slab).lock);
        FreeNode *node = (*slab).free_head;
        if (node) {
            (*slab).free_head = (*node).next;
            (*slab).count++;
        }
        SpinLock_unlock(&(*slab).lock);

        if (node) {
            uint8_t *slot_ptr = (uint8_t*) node - sizeof(MemoryHeader);
            MemoryHeader *h = (MemoryHeader*) slot_ptr;
            (*h).typeId = typeId;
            (*h).length = (uint32_t) numBytes;
            (*h).slabIndex = (uint32_t) s_idx;
            (*h).magic = MEMORY_MAGIC;
            return (void*) node;
        }
    }

    // Large allocation from bump arena (or slab fallback)
    size_t aligned_len = (numBytes + 15) & ~15ull;
    size_t total = sizeof(MemoryHeader) + aligned_len;

    SpinLock_lock(&s_bumpLock);
    if (s_bumpOffset + total <= s_bumpCapacity) {
        uint8_t *slot_ptr = s_bumpArena + s_bumpOffset;
        s_bumpOffset += total;
        SpinLock_unlock(&s_bumpLock);

        MemoryHeader *h = (MemoryHeader*) slot_ptr;
        (*h).typeId = typeId;
        (*h).length = (uint32_t) numBytes;
        (*h).slabIndex = SLAB_LARGE;
        (*h).magic = MEMORY_MAGIC;
        return (void*) (slot_ptr + sizeof(MemoryHeader));
    }
    SpinLock_unlock(&s_bumpLock);

    // Overflow fallback for assets exceeding master arena
    uint8_t *raw = (uint8_t*) malloc(total);
    if (!raw)
        return nullptr;

    MemoryHeader *h = (MemoryHeader*) raw;
    (*h).typeId = typeId;
    (*h).length = (uint32_t) numBytes;
    (*h).slabIndex = SLAB_SYSTEM;
    (*h).magic = MEMORY_MAGIC;
    return (void*) (raw + sizeof(MemoryHeader));
}

void *Memory_realloc(void *userPtr, size_t newBytes) {
    if (!userPtr)
        return Memory_alloc(0, newBytes);

    uint32_t typeId = Memory_type(userPtr);
    size_t oldLen = Memory_length(userPtr);
    void *next = Memory_alloc(typeId, newBytes);
    if (!next)
        return nullptr;

    memcpy(next, userPtr, oldLen < newBytes ? oldLen : newBytes);
    Memory_free(userPtr);
    return next;
}

void Memory_free(void *userPtr) {
    if (!userPtr)
        return;

    uintptr_t u = (uintptr_t) userPtr;
    if (u < sizeof(MemoryHeader) || (u & 15) != 0)
        return;

    MemoryHeader *h = (MemoryHeader*) ((uint8_t*) userPtr - sizeof(MemoryHeader));
    if ((*h).magic != MEMORY_MAGIC)
        return;

    uint32_t s_idx = (*h).slabIndex;
    if (s_idx < SLAB_COUNT) {
        (*h).magic = 0; // Poison magic immediately to guard against double-free
        SlabClass *slab = &s_slabs[s_idx];
        FreeNode *node = (FreeNode*) userPtr;

        SpinLock_lock(&(*slab).lock);
        (*node).next = (*slab).free_head;
        (*slab).free_head = node;
        if ((*slab).count > 0)
            (*slab).count--;
        SpinLock_unlock(&(*slab).lock);
    } else if (s_idx == SLAB_LARGE) {
        (*h).magic = 0; // Reclaimed during Memory_freeAll
    } else if (s_idx == SLAB_SYSTEM) {
        (*h).magic = 0;
        free((void*) h);
    }
}

void Memory_freeAll(void) {
    if (!s_masterArena)
        return;

    for (size_t s = 0; s < SLAB_COUNT; s++) {
        SlabClass *slab = &s_slabs[s];
        SpinLock_lock(&(*slab).lock);
        (*slab).free_head = nullptr;
        (*slab).count = 0;

        uint32_t sz = (*slab).slot_size;
        for (size_t i = (*slab).capacity; i > 0; i--) {
            uint8_t *slot_ptr = (*slab).arena + (i - 1) * sz;
            MemoryHeader *h = (MemoryHeader*) slot_ptr;
            (*h).magic = 0;
            FreeNode *node = (FreeNode*) (slot_ptr + sizeof(MemoryHeader));
            (*node).next = (*slab).free_head;
            (*slab).free_head = node;
        }
        SpinLock_unlock(&(*slab).lock);
    }

    SpinLock_lock(&s_bumpLock);
    s_bumpOffset = 0;
    SpinLock_unlock(&s_bumpLock);
}

size_t Memory_length(void *userPtr) {
    if (!userPtr)
        return 0;

    uintptr_t u = (uintptr_t) userPtr;
    if (u < sizeof(MemoryHeader) || (u & 15) != 0)
        return 0;

    const MemoryHeader *h = (const MemoryHeader*) ((const uint8_t*) userPtr - sizeof(MemoryHeader));
    if ((*h).magic == MEMORY_MAGIC) {
        return (size_t) (*h).length;
    }
    return 0;
}

uint32_t Memory_type(void *userPtr) {
    if (!userPtr)
        return 0;

    uintptr_t u = (uintptr_t) userPtr;
    if (u < sizeof(MemoryHeader) || (u & 15) != 0)
        return 0;

    const MemoryHeader *h = (const MemoryHeader*) ((const uint8_t*) userPtr - sizeof(MemoryHeader));
    if ((*h).magic == MEMORY_MAGIC) {
        return (*h).typeId;
    }
    return 0;
}

size_t Memory_findAll(uint32_t typeId, void **outArray, size_t maxCount) {
    size_t count = 0;
    if (!s_masterArena)
        return 0;

    for (size_t s = 0; s < SLAB_COUNT; s++) {
        SlabClass *slab = &s_slabs[s];
        SpinLock_lock(&(*slab).lock);
        uint32_t sz = (*slab).slot_size;
        for (size_t i = 0; i < (*slab).capacity; i++) {
            uint8_t *slot_ptr = (*slab).arena + i * sz;
            MemoryHeader *h = (MemoryHeader*) slot_ptr;
            if ((*h).magic == MEMORY_MAGIC) {
                if (typeId == 0 || (*h).typeId == typeId) {
                    if (outArray && count < maxCount) {
                        outArray[count] = (void*) (slot_ptr + sizeof(MemoryHeader));
                    }
                    count++;
                }
            }
        }
        SpinLock_unlock(&(*slab).lock);
    }
    return count;
}
