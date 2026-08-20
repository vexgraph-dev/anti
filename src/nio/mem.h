#ifndef NIO_MEM_H
#define NIO_MEM_H

#include <stddef.h>
#include <stdint.h>

#include "oop/type.h"

// nio/mem.h — the ForeignMemory API (Legacy: nio/ForeignMemory.java).
//
// Every allocation returns a payload pointer whose 16-byte prefix holds
// [type_id][length]. Callers never see the header; the length/type accessors
// walk back to it, so metadata costs one subtract.
//
// This is the "lens of all things": with type+length on every block, debug
// validation, serialization, and the hot-swap system can trust a raw pointer.

// The header struct lives in oop/type.h (type_header_t). A block is
// that header followed by an aligned payload.
typedef struct Block {
    uint32_t type_id;
    uint32_t length;
    uint64_t _pad;
} Block;

// Allocate nbytes with the given type id stamped in the header.
// Returns the aligned payload pointer, or NULL on failure.
void *Memory_alloc(uint32_t type_id, size_t nbytes);

// Grow/shrink a block, preserving contents and type. Returns the new payload
// pointer (the old one is freed). NULL on failure leaves the original intact.
void *Memory_realloc(void *user_ptr, size_t new_nbytes);

// Free a block (user_ptr may be NULL). The header is found by walking back.
void Memory_free(void *user_ptr);

// Metadata accessors: cost a single pointer subtract.
size_t Memory_length(void *user_ptr);
uint32_t Memory_type(void *user_ptr);

#endif