#ifndef NIO_MEM_H
#define NIO_MEM_H

#include <stddef.h>
#include <stdint.h>

// nio/mem.h — the ForeignMemory API (Legacy: nio/ForeignMemory.java).
//
// Every allocation returns a payload pointer whose 32-byte prefix holds
// [prev][next][typeId][length][pad]. Callers never see the header; the
// length/type accessors walk back to it, so metadata costs one subtract.
// The intrusive prev/next links the global block list for Memory_findAll /
// Memory_freeAll. See lessons/ docs for the intended 16-byte arena doctrine;
// current malloc+list impl is the stepping stone.
//
// This is the "lens of all things": with type+length on every block, debug
// validation, serialization, and the hot-swap system can trust a raw pointer.

typedef struct Block {
    struct Block *prev;
    struct Block *next;
    uint32_t typeId;
    uint32_t length;
    uint64_t pad;
} Block;

// Allocate nbytes with the given type id stamped in the header.
// Returns the aligned payload pointer, or nullptr on failure.
void *Memory_alloc(uint32_t typeId, size_t numBytes);

// Grow/shrink a block, preserving contents and type. Returns the new payload
// pointer (the old one is freed). nullptr on failure leaves the original intact.
void *Memory_realloc(void *userPtr, size_t newBytes);

// Free a block (userPtr may be nullptr). The header is found by walking back.
void Memory_free(void *userPtr);

// Free all currently allocated blocks. Catch-all for teardown.
void Memory_freeAll(void);

// Metadata accessors: cost a single pointer subtract.
size_t Memory_length(void *userPtr);
uint32_t Memory_type(void *userPtr);

// Search: Return the number of blocks matching typeId.
// If outArray is not NULL, fills it with up to maxCount payload pointers.
size_t Memory_findAll(uint32_t typeId, void **outArray, size_t maxCount);

#endif