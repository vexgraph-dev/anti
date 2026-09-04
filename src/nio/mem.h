#ifndef NIO_MEM_H
#define NIO_MEM_H

#include <stdbool.h>
#include <stddef.h>
#include <stdint.h>

// nio/mem.h — the ForeignMemory API (Legacy: nio/ForeignMemory.java).
//
// 16-BYTE ARENA DOCTRINE (Anti Paradigm):
// In standard C, programs malloc/free constantly. In anti, the engine carves
// out one massive master arena at startup. Memory is partitioned into
// size-class slabs for O(1) slot claiming and cache-hot recycling.
//
// Every allocation returns a payload pointer whose 16-byte prefix holds:
//
//     [ typeId (32-bit) ][ length (32-bit) ][ slabIndex (32-bit) ][ magic (32-bit) ]
//
// Negative pointer math recovers the header in 1 subtraction; Memory_type()
// and Memory_length() are free O(1) reads with zero locking.

#define MEMORY_HEADER_SIZE 16
#define MEMORY_MAGIC 0x56455821u // "VEX!"

typedef struct MemoryHeader {
    uint32_t typeId;
    uint32_t length;
    uint32_t slabIndex;
    uint32_t magic;
} MemoryHeader;
_Static_assert(sizeof(MemoryHeader) == 16, "MemoryHeader must stay 16 bytes");
// 16-byte alignment holds by construction, not by attribute (this toolchain's
// _Alignas rejects typedef application): master/slab arenas come from malloc
// (16-aligned on arm64 macOS), every slot size and bump total is a multiple
// of 16, so every payload pointer satisfies (u & 15) == 0 as mem.c guards.

// Backward-compatibility struct alias
typedef struct Block {
    struct Block *prev;
    struct Block *next;
    uint32_t typeId;
    uint32_t length;
    uint64_t pad;
} Block;

// Initialize the master arena with a specific total capacity (e.g. 64MB).
// If not called explicitly, Memory_alloc initializes a 64MB arena lazily.
bool Memory_init(size_t totalBytes);

// Allocate nbytes with the given type id stamped in the 16-byte header.
// Returns the aligned payload pointer, or nullptr on failure.
void *Memory_alloc(uint32_t typeId, size_t numBytes);

// Grow/shrink a block, preserving contents and type. Returns the new payload
// pointer (the old one is freed). nullptr on failure leaves the original intact.
void *Memory_realloc(void *userPtr, size_t newBytes);

// Free a block back to its slab pool in O(1) cache-hot time.
void Memory_free(void *userPtr);

// Free/reset all currently allocated blocks across all slabs in O(1).
void Memory_freeAll(void);

// Metadata accessors: cost a single pointer subtract in O(1) without locks.
size_t Memory_length(void *userPtr);
uint32_t Memory_type(void *userPtr);

// Search: Return the number of active blocks matching typeId.
// If outArray is not NULL, fills it with up to maxCount payload pointers.
size_t Memory_findAll(uint32_t typeId, void **outArray, size_t maxCount);

#endif