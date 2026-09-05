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
 * LEVEL: L4 — Self-Management (arena/slab memory manager)
 * ============================================================================
 * Pre-allocated Master Arena and Size-Class Slab Allocator fulfilling the
 * Anti Paradigm: zero steady-state malloc, cache-hot slot recycling, and
 * 16-byte negative pointer math.
 *
 * Phase-4 instancing: globals are the DEFAULT MemoryArena; secondaries
 * register for address-range free routing. Header layout untouched.
 *
 * STRUCT FIELDS (Mirroring nio/mem.h + local to this file):
 * ----------------------------------------------------------------------------
 *   MemoryHeader {
 *     uint32_t typeId; // block-header type id
 *     uint32_t length; // payload length
 *     uint32_t slabIndex; // slab index
 *     uint32_t magic; // block magic cookie
 *   }
 *   Block {
 *     struct Block *prev; // instance state
 *     struct Block *next; // next sibling ref
 *     uint32_t typeId; // block-header type id
 *     uint32_t length; // payload length
 *     uint64_t pad; // alignment padding
 *   }
 *   SlabClass {
 *     uint32_t slot_size;        // bytes per slot in this size class
 *     uint32_t capacity;         // total slots carved from the arena
 *     uint32_t count;            // live (checked-out) slots
 *     uint8_t *arena;            // backing store for this class
 *     FreeNode *free_head;       // lock-free recycled slot stack
 *     SpinLock lock;             // serializes alloc/free on this class
 *   }
 *   MemoryArena {
 *     SlabClass slabs[SLAB_COUNT]; // size-class slab table (64B..4K)
 *     uint8_t *masterArena;      // pre-allocated master backing store
 *     size_t masterCapacity;     // master arena byte capacity
 *     uint8_t *bumpArena;        // large/allocation bump region
 *     size_t bumpCapacity;       // bump region capacity
 *     size_t bumpOffset;         // bump cursor (monotonic)
 *     SpinLock bumpLock;         // serializes bump allocation
 *     SpinLock initLock;         // serializes lazy arena init
 *     bool live;                 // arena ready flag
 *   }
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
 *
 * Arena Functions (Phase-4):
 *   - MemoryArena_create(totalBytes)
 *   - MemoryArena_destroy(a)
 *   - MemoryArena_alloc(a, typeId, numBytes)
 *   - MemoryArena_realloc(a, userPtr, newBytes)
 *   - MemoryArena_free(a, userPtr)
 *   - MemoryArena_freeAll(a)
 *   - MemoryArena_findAll(a, typeId, outArray, maxCount)
 *   - MemoryArena_activeBytes(a)
 *   - MemoryArena_capacity(a)
 * ============================================================================
 */

#define SLAB_COUNT 7
#define SLAB_LARGE 0xFFFFFFFFu
#define SLAB_SYSTEM 0xFFFFFFFEu
#define ANTI_ARENA_DEFAULT_SIZE (64 * 1024 * 1024) // 64 MB master arena
#define ARENA_REGISTRY_MAX 4

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

struct MemoryArena {
    SlabClass slabs[SLAB_COUNT];
    uint8_t *masterArena;
    size_t masterCapacity;
    uint8_t *bumpArena;
    size_t bumpCapacity;
    size_t bumpOffset;
    SpinLock bumpLock;
    SpinLock initLock;
    bool live;
};

static uint32_t s_slabSizes[SLAB_COUNT] = { 64, 128, 256, 512, 1024, 2048, 4096 };
static uint32_t s_slabCaps[SLAB_COUNT] = { 32768, 32768, 16384, 8192, 4096, 2048, 2048 };

static MemoryArena s_default = {0};
static MemoryArena *s_registry[ARENA_REGISTRY_MAX] = { &s_default, nullptr, nullptr, nullptr };
static SpinLock s_registryLock = SPIN_LOCK_INIT;

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

static void slab_template(SlabClass *slab, uint32_t idx) {
    (*slab).slot_size = s_slabSizes[idx];
    (*slab).capacity = s_slabCaps[idx];
    (*slab).count = 0;
    (*slab).arena = nullptr;
    (*slab).free_head = nullptr;
    (*slab).lock = SPIN_LOCK_INIT;
}

static bool arena_init(MemoryArena *a, size_t totalBytes) {
    if (!a)
        return false;
    for (size_t s = 0; s < SLAB_COUNT; s++)
        slab_template(&(*a).slabs[s], (uint32_t) s);
    (*a).initLock = SPIN_LOCK_INIT;
    (*a).bumpLock = SPIN_LOCK_INIT;
    (*a).live = false;

    size_t cap = totalBytes > 0 ? totalBytes : ANTI_ARENA_DEFAULT_SIZE;
    uint8_t *master = (uint8_t*) malloc(cap);
    if (!master)
        return false;

    uint8_t *cur = master;
    size_t left = cap;
    for (size_t s = 0; s < SLAB_COUNT; s++) {
        SlabClass *slab = &(*a).slabs[s];
        size_t need = (size_t)(*slab).capacity * (*slab).slot_size;
        if (need > left) {
            free(master);
            return false;
        }
        (*slab).arena = cur;
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
        cur += need;
        left -= need;
    }

    (*a).masterArena = master;
    (*a).masterCapacity = cap;
    (*a).bumpArena = cur;
    (*a).bumpCapacity = left;
    (*a).bumpOffset = 0;
    (*a).live = true;
    return true;
}

static void *arena_alloc(MemoryArena *a, uint32_t typeId, size_t numBytes) {
    if (!a || !(*a).live)
        return nullptr;
    if (numBytes > UINT32_MAX)
        return nullptr;

    int s_idx = find_slab(numBytes);
    if (s_idx >= 0) {
        SlabClass *slab = &(*a).slabs[s_idx];
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

    size_t aligned_len = (numBytes + 15) & ~15ull;
    size_t total = sizeof(MemoryHeader) + aligned_len;

    SpinLock_lock(&(*a).bumpLock);
    if ((*a).bumpOffset + total <= (*a).bumpCapacity) {
        uint8_t *slot_ptr = (*a).bumpArena + (*a).bumpOffset;
        (*a).bumpOffset += total;
        SpinLock_unlock(&(*a).bumpLock);

        MemoryHeader *h = (MemoryHeader*) slot_ptr;
        (*h).typeId = typeId;
        (*h).length = (uint32_t) numBytes;
        (*h).slabIndex = SLAB_LARGE;
        (*h).magic = MEMORY_MAGIC;
        return (void*) (slot_ptr + sizeof(MemoryHeader));
    }
    SpinLock_unlock(&(*a).bumpLock);

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

static void arena_free(MemoryArena *a, void *userPtr) {
    if (!a || !userPtr)
        return;

    uintptr_t u = (uintptr_t) userPtr;
    if (u < sizeof(MemoryHeader) || (u & 15) != 0)
        return;

    MemoryHeader *h = (MemoryHeader*) ((uint8_t*) userPtr - sizeof(MemoryHeader));
    if ((*h).magic != MEMORY_MAGIC)
        return;

    uint32_t s_idx = (*h).slabIndex;
    if (s_idx < SLAB_COUNT) {
        (*h).magic = 0;
        SlabClass *slab = &(*a).slabs[s_idx];
        FreeNode *node = (FreeNode*) userPtr;

        SpinLock_lock(&(*slab).lock);
        (*node).next = (*slab).free_head;
        (*slab).free_head = node;
        if ((*slab).count > 0)
            (*slab).count--;
        SpinLock_unlock(&(*slab).lock);
    } else if (s_idx == SLAB_LARGE) {
        (*h).magic = 0;
    } else if (s_idx == SLAB_SYSTEM) {
        (*h).magic = 0;
        free((void*) h);
    }
}

static void arena_freeAll(MemoryArena *a) {
    if (!a || !(*a).live)
        return;

    for (size_t s = 0; s < SLAB_COUNT; s++) {
        SlabClass *slab = &(*a).slabs[s];
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

    SpinLock_lock(&(*a).bumpLock);
    (*a).bumpOffset = 0;
    SpinLock_unlock(&(*a).bumpLock);
}

// Free-routing: headers carry no arena tag (ABI-stable by design), so the
// owner is whoever's master range contains the header. SLAB_SYSTEM blocks
// are malloc-fallback and free directly. Registry writes happen at
// create/destroy (pre-threads); reads are lock-free.
static MemoryArena *arena_for(void *userPtr) {
    if (!userPtr)
        return &s_default;
    MemoryHeader *h = (MemoryHeader*) ((uint8_t*) userPtr - sizeof(MemoryHeader));
    if ((*h).magic != MEMORY_MAGIC)
        return nullptr;
    if ((*h).slabIndex == SLAB_SYSTEM)
        return nullptr;
    uint8_t *slot = (uint8_t*) h;
    for (size_t i = 0; i < ARENA_REGISTRY_MAX; i++) {
        MemoryArena *a = s_registry[i];
        if (a && (*a).live && slot >= (*a).masterArena && slot < (*a).masterArena + (*a).masterCapacity)
            return a;
    }
    // Valid magic but no live owner: destroyed arena or foreign block.
    // Never touch the default freelist with a foreign node pointer.
    return nullptr;
}

static inline void ensure_initialized(void) {
    if (!s_default.live)
        Memory_init(ANTI_ARENA_DEFAULT_SIZE);
}

bool Memory_init(size_t totalBytes) {
    SpinLock_lock(&s_default.initLock);
    if (s_default.live) {
        SpinLock_unlock(&s_default.initLock);
        return true;
    }
    SpinLock_unlock(&s_default.initLock);
    return arena_init(&s_default, totalBytes);
}

void *Memory_alloc(uint32_t typeId, size_t numBytes) {
    ensure_initialized();
    return arena_alloc(&s_default, typeId, numBytes);
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
    MemoryHeader *h = (MemoryHeader*) ((uint8_t*) userPtr - sizeof(MemoryHeader));
    if ((*h).magic != MEMORY_MAGIC)
        return;
    if ((*h).slabIndex == SLAB_SYSTEM) {
        (*h).magic = 0;
        free((void*) h);
        return;
    }
    MemoryArena *a = arena_for(userPtr);
    if (!a)
        return;
    arena_free(a, userPtr);
}

void Memory_freeAll(void) {
    arena_freeAll(&s_default);
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
    return MemoryArena_findAll(&s_default, typeId, outArray, maxCount);
}

MemoryArena *MemoryArena_create(size_t totalBytes) {
    MemoryArena *a = (MemoryArena*) calloc(1, sizeof(MemoryArena));
    if (!a)
        return nullptr;
    if (!arena_init(a, totalBytes)) {
        free(a);
        return nullptr;
    }
    SpinLock_lock(&s_registryLock);
    bool placed = false;
    for (size_t i = 1; i < ARENA_REGISTRY_MAX; i++) {
        if (!s_registry[i]) {
            s_registry[i] = a;
            placed = true;
            break;
        }
    }
    SpinLock_unlock(&s_registryLock);
    if (!placed) {
        free((*a).masterArena);
        free(a);
        return nullptr;
    }
    return a;
}

void MemoryArena_destroy(MemoryArena *a) {
    if (!a || a == &s_default)
        return;
    SpinLock_lock(&s_registryLock);
    for (size_t i = 1; i < ARENA_REGISTRY_MAX; i++) {
        if (s_registry[i] == a)
            s_registry[i] = nullptr;
    }
    SpinLock_unlock(&s_registryLock);
    (*a).live = false;
    free((*a).masterArena);
    (*a).masterArena = nullptr;
    free(a);
}

void *MemoryArena_alloc(MemoryArena *a, uint32_t typeId, size_t numBytes) {
    if (!a)
        return nullptr;
    return arena_alloc(a, typeId, numBytes);
}

void *MemoryArena_realloc(MemoryArena *a, void *userPtr, size_t newBytes) {
    if (!a)
        return nullptr;
    if (!userPtr)
        return arena_alloc(a, 0, newBytes);

    uint32_t typeId = Memory_type(userPtr);
    size_t oldLen = Memory_length(userPtr);
    void *next = arena_alloc(a, typeId, newBytes);
    if (!next)
        return nullptr;

    memcpy(next, userPtr, oldLen < newBytes ? oldLen : newBytes);
    Memory_free(userPtr);
    return next;
}

void MemoryArena_free(MemoryArena *a, void *userPtr) {
    if (!a)
        return;
    arena_free(a, userPtr);
}

void MemoryArena_freeAll(MemoryArena *a) {
    if (!a)
        return;
    arena_freeAll(a);
}

size_t MemoryArena_findAll(MemoryArena *a, uint32_t typeId, void **outArray, size_t maxCount) {
    size_t count = 0;
    if (!a || !(*a).live)
        return 0;

    for (size_t s = 0; s < SLAB_COUNT; s++) {
        SlabClass *slab = &(*a).slabs[s];
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

size_t MemoryArena_activeBytes(MemoryArena *a) {
    size_t total = 0;
    if (!a || !(*a).live)
        return 0;
    for (size_t s = 0; s < SLAB_COUNT; s++) {
        SlabClass *slab = &(*a).slabs[s];
        SpinLock_lock(&(*slab).lock);
        total += (size_t)(*slab).count * (*slab).slot_size;
        SpinLock_unlock(&(*slab).lock);
    }
    SpinLock_lock(&(*a).bumpLock);
    total += (*a).bumpOffset;
    SpinLock_unlock(&(*a).bumpLock);
    return total;
}

size_t MemoryArena_capacity(MemoryArena *a) {
    if (!a)
        return 0;
    return (*a).masterCapacity;
}
