#ifndef STRUCT_MINHEAP_H
#define STRUCT_MINHEAP_H

#include <stdbool.h>
#include <stddef.h>
#include <stdint.h>

// struct/minheap.h — the MinHeap class, ported from struct/MinHeap.java.
//
// Priority queue over (int item, float priority) pairs. 1-based indexing for
// cheap heap math; index 0 is unused. Pops the lowest-priority item first.

typedef struct MinHeap {
    int32_t size;       // number of live entries
    int32_t capacity;   // max entries (1-based slots are capacity + 1)
    int32_t *items;     // item at heap index i (i in [1, size])
    float *priorities;  // priority at heap index i
} MinHeap;

// Heap with room for capacity entries.
MinHeap *MinHeap_allocate(size_t capacity);

void MinHeap_free(MinHeap *heap);

size_t MinHeap_size(MinHeap *heap);
size_t MinHeap_capacity(MinHeap *heap);
bool MinHeap_isEmpty(MinHeap *heap);

// Push (item, priority). Returns 0 if the heap is full.
int MinHeap_push(MinHeap *heap, int32_t item, float priority);

// Pop and return the lowest-priority item. Returns 0 if empty.
int32_t MinHeap_popItem(MinHeap *heap);

// Peek the lowest-priority item without removing it. Returns 0 if empty.
int32_t MinHeap_peekItem(MinHeap *heap);

#endif