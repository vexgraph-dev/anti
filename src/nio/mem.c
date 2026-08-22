#include "nio/mem.h"

#include <stdlib.h>
#include <string.h>

// mem.c — ForeignMemory port. The lens of all things: every byte in the
// engine is reached through a block that knows its own type + length.
//
// Layout of one allocation (userPtr points at the aligned payload):
//
//     [ typeId ][ length ][ pad ][ payload ... ]
//     `----------- 16-byte header ----------'
//
// Memory_alloc returns the payload pointer. Walking back 16 bytes gives the
// header, so Memory_type()/Memory_length() cost nothing — just a subtract.

static const size_t HEADER_SIZE = 16;
static const size_t ALIGN = 8;

// Allocate a block, stamp the type, align the payload, return the payload.
void* Memory_alloc(const uint32_t typeId, const size_t numBytes) {
    // stamp the payload
    const size_t total = HEADER_SIZE + numBytes + (ALIGN - 1);
    unsigned char *raw = malloc(total);

    if (!raw)
        return NULL;

    // Align the payload to 8 bytes so doubles/pointers sit naturally.
    uintptr_t aligned = (uintptr_t)(raw + HEADER_SIZE);
    aligned = (aligned + ALIGN - 1) & ~(ALIGN - 1);

    Block *hdr = (Block *)(aligned - HEADER_SIZE);
    (*hdr).typeId = typeId;
    (*hdr).length = (uint32_t)numBytes;
    (*hdr).pad = 0;

    // return the payload, its like a pointer of that allocated memory
    return (void*) aligned;
}

// Grow/shrink: allocate new, copy min(old,new) bytes, free old.
void* Memory_realloc(void *userPtr, size_t newBytes) {
    if (!userPtr) return NULL;
    uint32_t typeId = Memory_type(userPtr);
    size_t oldLen = Memory_length(userPtr);

    void *next = Memory_alloc(typeId, newBytes);

    if (!next)
        return NULL;

    memcpy(next, userPtr, oldLen < newBytes ? oldLen : newBytes);
    Memory_free(userPtr);
    return next;
}

void Memory_free(void *userPtr) {
    if (!userPtr)
        return;
    void *hdr = (unsigned char *)userPtr - HEADER_SIZE;
    free(hdr);
}

size_t Memory_length(void *userPtr) {
    if (!userPtr) return 0;
    Block *hdr = (Block *)((unsigned char *)userPtr - HEADER_SIZE);
    return (*hdr).length;
}

uint32_t Memory_type(void *userPtr) {
    if (!userPtr) return 0;
    Block *hdr = (Block *)((unsigned char *)userPtr - HEADER_SIZE);
    return (*hdr).typeId;
}