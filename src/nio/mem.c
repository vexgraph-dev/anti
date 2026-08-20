#include "nio/mem.h"

#include <stdlib.h>
#include <string.h>

// anti_mem.c — ForeignMemory port. The lens of all things: every byte in the
// engine is reached through a block that knows its own type + length.
//
// Layout of one allocation (user_ptr points at the aligned payload):
//
//     [ type_id ][ length ][ pad ][ payload ... ]
//     `----------- 16-byte header ----------'
//
// anti_mem_alloc returns the payload pointer. Walking back 16 bytes gives the
// header, so anti_mem_type()/anti_mem_length() cost nothing — just a subtract.
//
// NOTE: this file currently wraps malloc for correctness while we stand the
// engine up. The Lesson 13 doctrine replaces it with a fixed arena carved from
// mmap once, so steady-state allocation makes zero syscalls. The API stays
// identical when we swap the backing store.

static const size_t ANTI_HEADER_SIZE = 16;
static const size_t ANTI_ALIGN = 8;

// Allocate a block, stamp the type, align the payload, return the payload.
// Walk-back layout:
//   raw  -> [ header 16B ][ pad ][ payload ] <- returned
void *anti_mem_alloc(uint32_t type_id, size_t nbytes) {
    size_t total = ANTI_HEADER_SIZE + nbytes + (ANTI_ALIGN - 1);
    unsigned char *raw = (unsigned char *)malloc(total);
    if (!raw) return NULL;

    // Align the payload to 8 bytes so doubles/pointers sit naturally.
    uintptr_t aligned = (uintptr_t)(raw + ANTI_HEADER_SIZE);
    aligned = (aligned + ANTI_ALIGN - 1) & ~(uintptr_t)(ANTI_ALIGN - 1);

    anti_block_t *hdr = (anti_block_t *)(aligned - ANTI_HEADER_SIZE);
    (*hdr).type_id = type_id;
    (*hdr).length = (uint32_t)nbytes;

    return (void *)aligned;
}

// Grow/shrink: allocate new, copy min(old,new) bytes, free old.
// The type id is carried over from the old block's header.
void *anti_mem_realloc(void *user_ptr, size_t new_nbytes) {
    if (!user_ptr) return NULL;
    uint32_t type_id = anti_mem_type(user_ptr);
    size_t old_len = anti_mem_length(user_ptr);

    void *next = anti_mem_alloc(type_id, new_nbytes);
    if (!next) return NULL;

    memcpy(next, user_ptr, old_len < new_nbytes ? old_len : new_nbytes);
    anti_mem_free(user_ptr);
    return next;
}

void anti_mem_free(void *user_ptr) {
    if (!user_ptr) return;
    void *hdr = (unsigned char *)user_ptr - ANTI_HEADER_SIZE;
    free(hdr);
}

size_t anti_mem_length(void *user_ptr) {
    if (!user_ptr) return 0;
    anti_block_t *hdr = (anti_block_t *)((unsigned char *)user_ptr - ANTI_HEADER_SIZE);
    return (*hdr).length;
}

uint32_t anti_mem_type(void *user_ptr) {
    if (!user_ptr) return 0;
    anti_block_t *hdr = (anti_block_t *)((unsigned char *)user_ptr - ANTI_HEADER_SIZE);
    return (*hdr).type_id;
}