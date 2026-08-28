#ifndef IO_MMAP_H
#define IO_MMAP_H

#include <stddef.h>
#include <stdbool.h>

// io/mmap.h — Zero-copy read-only memory mapped file utility.
// 
// Allocates address space without allocating physical RAM, paging
// blocks in from the disk purely on-demand via CPU/GPU page faults.

typedef struct MemoryMap {
    void *data;
    size_t size;
    bool valid;
} MemoryMap;

// Maps a file into read-only virtual memory. 
// Returns a valid struct if successful.
MemoryMap MemoryMap_open(const char *path);

// Unmaps the memory and releases OS resources.
void MemoryMap_close(MemoryMap *map);

#endif
