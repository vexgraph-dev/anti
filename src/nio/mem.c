#include "nio/mem.h"

#include <stdlib.h>
#include <string.h>

// mem.c — ForeignMemory port. The lens of all things: every byte in the
// engine is reached through a block that knows its own type + length.
//
// Layout of one allocation (user_ptr points at the aligned payload):
//
//     [ type_id ][ length ][ pad ][ payload ... ]
//     `----------- 16-byte header ----------'
//
// Memory_alloc returns the payload pointer. Walking back 16 bytes gives the
// header, so Memory_type()/Memory_length() cost nothing — just a subtract.
//
// NOTE: this file currently wraps malloc for correctness while we stand the
// engine up. The Lesson 13 doctrine replaces it with a fixed arena carved from
// mmap once, so steady-state allocation makes zero syscalls. The API stays
// identical when we swap the backing store.

static const size_t HEADER_SIZE = 16;
static const size_t ALIGN = 8;

// Allocate a block, stamp the type, align the payload, return the payload.
// Walk-back layout:
//   raw  -> [ header 16B ][ pad ][ payload ] <- returned
void *Memory_alloc(uint32_t type_id, size_t nbytes) {
    size_t total = HEADER_SIZE + nbytes + (ALIGN - 1);
    unsigned char *raw = (unsigned char *)malloc(total);
    if (!raw) return NULL;

    // Align the payload to 8 bytes so doubles/pointers sit naturally.
    uintptr_t aligned = (uintptr_t)(raw + HEADER_SIZE);
    aligned = (aligned + ALIGN - 1) & ~(uintptr_t)(ALIGN - 1);

    Block *hdr = (Block *)(aligned - HEADER_SIZE);
    (*hdr).type_id = type_id;
    (*hdr).length = (uint32_t)nbytes;

    return (void *)aligned;
}

// Grow/shrink: allocate new, copy min(old,new) bytes, free old.
// The type id is carried over from the old block's header.
void *Memory_realloc(void *user_ptr, size_t new_nbytes) {
    if (!user_ptr) return NULL;
    uint32_t type_id = Memory_type(user_ptr);
    size_t old_len = Memory_length(user_ptr);

    void *next = Memory_alloc(type_id, new_nbytes);
    if (!next) return NULL;

    memcpy(next, user_ptr, old_len < new_nbytes ? old_len : new_nbytes);
    Memory_free(user_ptr);
    return next;
}

void Memory_free(void *user_ptr) {
    if (!user_ptr) return;
    void *hdr = (unsigned char *)user_ptr - HEADER_SIZE;
    free(hdr);
}

size_t Memory_length(void *user_ptr) {
    if (!user_ptr) return 0;
    Block *hdr = (Block *)((unsigned char *)user_ptr - HEADER_SIZE);
    return (*hdr).length;
}

uint32_t Memory_type(void *user_ptr) {
    if (!user_ptr) return 0;
    Block *hdr = (Block *)((unsigned char *)user_ptr - HEADER_SIZE);
    return (*hdr).type_id;
}