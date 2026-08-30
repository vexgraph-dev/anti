#include "struct/minheap.h"

#include <string.h>

#include "nio/mem.h"
#include "oop/type.h"

// minheap.c — MinHeap port (Legacy: struct/MinHeap.java). 1-based array, sift
// up on push, sift down on pop, strictly lower priority rises to the root.

static void sift_up(MinHeap *heap, size_t index) {
    int32_t item = (*heap).items[index];
    float prio = (*heap).priorities[index];
    while (index > 1) {
        size_t parent = index / 2;
        float parent_prio = (*heap).priorities[parent];
        if (prio >= parent_prio)
            break;
        (*heap).items[index] = (*heap).items[parent];
        (*heap).priorities[index] = parent_prio;
        index = parent;
    }
    (*heap).items[index] = item;
    (*heap).priorities[index] = prio;
}

static void sift_down(MinHeap *heap, size_t index) {
    size_t size = (size_t)(*heap).size;
    int32_t item = (*heap).items[index];
    float prio = (*heap).priorities[index];
    size_t half = size / 2;
    while (index <= half) {
        size_t left = index * 2;
        size_t right = left + 1;

        size_t best = left;
        float best_prio = (*heap).priorities[left];
        if (right <= size) {
            float right_prio = (*heap).priorities[right];
            if (right_prio < best_prio) {
                best = right;
                best_prio = right_prio;
            }
        }
        if (prio <= best_prio)
            break;
        (*heap).items[index] = (*heap).items[best];
        (*heap).priorities[index] = best_prio;
        index = best;
    }
    (*heap).items[index] = item;
    (*heap).priorities[index] = prio;
}

MinHeap *MinHeap_allocate(size_t capacity) {
    size_t slots = capacity + 1;
    MinHeap *heap = (MinHeap *)Memory_alloc(TYPE_MIN_HEAP, sizeof(MinHeap));
    if (!heap) return nullptr;

    (*heap).size = 0;
    (*heap).capacity = (int32_t)capacity;
    (*heap).items = (int32_t *)Memory_alloc(TYPE_INT_ARRAY, slots * sizeof(int32_t));
    (*heap).priorities = (float *)Memory_alloc(TYPE_FLOAT_ARRAY, slots * sizeof(float));
    if (!(*heap).items || !(*heap).priorities) {
        Memory_free((*heap).items);
        Memory_free((*heap).priorities);
        Memory_free(heap);
        return nullptr;
    }
    return heap;
}

void MinHeap_free(MinHeap *heap) {
    if (!heap) return;
    Memory_free((*heap).items);
    Memory_free((*heap).priorities);
    Memory_free(heap);
}

size_t MinHeap_size(MinHeap *heap) {
    if (!heap) return 0;
    return (size_t)(*heap).size;
}

size_t MinHeap_capacity(MinHeap *heap) {
    if (!heap) return 0;
    return (size_t)(*heap).capacity;
}

bool MinHeap_isEmpty(MinHeap *heap) {
    return MinHeap_size(heap) == 0;
}

int MinHeap_push(MinHeap *heap, int32_t item, float priority) {
    if (!heap) return 0;
    if ((*heap).size >= (*heap).capacity)
        return 0;
    (*heap).size++;
    size_t index = (size_t)(*heap).size;
    (*heap).items[index] = item;
    (*heap).priorities[index] = priority;
    sift_up(heap, index);
    return 1;
}

int32_t MinHeap_popItem(MinHeap *heap) {
    if (!heap) return 0;
    if ((*heap).size == 0)
        return 0;
    int32_t result = (*heap).items[1];
    size_t size = (size_t)(*heap).size;
    (*heap).items[1] = (*heap).items[size];
    (*heap).priorities[1] = (*heap).priorities[size];
    (*heap).size--;
    if ((*heap).size > 0)
        sift_down(heap, 1);
    return result;
}

int32_t MinHeap_peekItem(MinHeap *heap) {
    if (!heap) return 0;
    if ((*heap).size == 0)
        return 0;
    return (*heap).items[1];
}