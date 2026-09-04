#include "nio/mem.h"

#include <stdlib.h>
#include <string.h>
#include "atomic/spin.h"

// mem.c — ForeignMemory port. The lens of all things: every byte in the
// engine is reached through a block that knows its own type + length.
//
// Layout of one allocation (userPtr points at the aligned payload):
//
//     [ prev ][ next ][ typeId ][ length ][ pad ][ payload ... ]
//     `----------------- 32-byte header -----------------'
//
// Memory_alloc returns the payload pointer. Walking back 32 bytes gives the
// header, so Memory_type()/Memory_length() cost nothing — just a subtract.

static const size_t HEADER_SIZE = 32;
static const size_t ALIGN = 8;
#define MEMORY_BLOCK_MAGIC 0x5645585F4D454D21ULL

static Block *s_head = nullptr;
static SpinLock s_lock = SPIN_LOCK_INIT;

static inline bool is_valid_pointer(const void *userPtr) {
    if (!userPtr)
        return false;
    uintptr_t u = (uintptr_t) userPtr;
    if (u < HEADER_SIZE)
        return false;
    if ((u & (ALIGN - 1)) != 0)
        return false;
    return true;
}

static bool is_block_owned_locked(const Block *target) {
    for (Block *curr = s_head; curr; curr = (*curr).next) {
        if (curr == target)
            return true;
    }
    return false;
}

// Allocate a block, stamp the type, align the payload, return the payload.
void *Memory_alloc(const uint32_t typeId, const size_t numBytes) {
    // Fail closed: Block.length is uint32_t, so reject anything that truncates.
    if (numBytes > UINT32_MAX)
        return nullptr;
    // Fail closed on size_t overflow.
    size_t total;
    if (__builtin_add_overflow(HEADER_SIZE, numBytes, &total))
        return nullptr;
    if (__builtin_add_overflow(total, (ALIGN - 1), &total))
        return nullptr;
    // stamp the payload
    unsigned char *raw = malloc(total);

    if (!raw)
        return nullptr;

    // Align the payload to 8 bytes so doubles/pointers sit naturally.
    // Assuming malloc returns >= 8-byte alignment, hdr will exactly equal raw.
    uintptr_t aligned = (uintptr_t) (raw + HEADER_SIZE);
    aligned = (aligned + ALIGN - 1) & ~(ALIGN - 1);

    Block *hdr = (Block*) (aligned - HEADER_SIZE);
    (*hdr).typeId = typeId;
    (*hdr).length = (uint32_t) numBytes;
    (*hdr).pad = MEMORY_BLOCK_MAGIC;

    SpinLock_lock(&s_lock);
    (*hdr).prev = nullptr;
    (*hdr).next = s_head;
    if (s_head)
        (*s_head).prev = hdr;
    s_head = hdr;
    SpinLock_unlock(&s_lock);

    // return the payload, its like a pointer of that allocated memory
    return (void*) aligned;
}

// Grow/shrink: allocate new, copy min(old,new) bytes, free old.
void *Memory_realloc(void *userPtr, size_t newBytes) {
    if (!userPtr)
        return nullptr;
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
    if (!is_valid_pointer(userPtr))
        return;
    Block *hdr = (Block*) ((unsigned char*) userPtr - HEADER_SIZE);

    SpinLock_lock(&s_lock);
    // Validate membership before touching hdr: a foreign/stack/BitPool
    // pointer must not unlink arbitrary memory or reach free().
    if (!is_block_owned_locked(hdr)) {
        SpinLock_unlock(&s_lock);
        return;
    }
    if ((*hdr).prev) {
        Block *prev = (*hdr).prev;
        (*prev).next = (*hdr).next;
    } else {
        s_head = (*hdr).next;
    }
        
    if ((*hdr).next) {
        Block *next = (*hdr).next;
        (*next).prev = (*hdr).prev;
    }
    (*hdr).pad = 0;
    SpinLock_unlock(&s_lock);

    free(hdr);
}

void Memory_freeAll(void) {
    SpinLock_lock(&s_lock);
    Block *curr = s_head;
    s_head = nullptr;
    SpinLock_unlock(&s_lock);
    
    while (curr) {
        Block *next = (*curr).next;
        (*curr).pad = 0;
        free(curr);
        curr = next;
    }
}

size_t Memory_length(void *userPtr) {
    if (!is_valid_pointer(userPtr))
        return 0;
    Block *hdr = (Block*) ((unsigned char*) userPtr - HEADER_SIZE);
    SpinLock_lock(&s_lock);
    if (!is_block_owned_locked(hdr)) {
        SpinLock_unlock(&s_lock);
        return 0;
    }
    size_t len = (size_t) (*hdr).length;
    SpinLock_unlock(&s_lock);
    return len;
}

uint32_t Memory_type(void *userPtr) {
    if (!is_valid_pointer(userPtr))
        return 0;
    Block *hdr = (Block*) ((unsigned char*) userPtr - HEADER_SIZE);
    SpinLock_lock(&s_lock);
    if (!is_block_owned_locked(hdr)) {
        SpinLock_unlock(&s_lock);
        return 0;
    }
    uint32_t tid = (*hdr).typeId;
    SpinLock_unlock(&s_lock);
    return tid;
}
size_t Memory_findAll(uint32_t typeId, void **outArray, size_t maxCount) {
    size_t count = 0;
    SpinLock_lock(&s_lock);
    Block *curr = s_head;
    while (curr) {
        if (typeId == 0 || (*curr).typeId == typeId) {
            if (outArray && count < maxCount) {
                outArray[count] = (void*) ((unsigned char*) curr + HEADER_SIZE);
            }
            count++;
        }
        curr = (*curr).next;
    }
    SpinLock_unlock(&s_lock);
    return count;
}
