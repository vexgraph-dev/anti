#include "struct/queue.h"

#include <string.h>

#include "nio/mem.h"
#include "oop/stride.h"
#include "oop/type.h"
#include "annotation/overview.h"

;;OVERVIEW
/**
 * ============================================================================
 * MODULE: Queue (struct/queue.c)
 * LEVEL: L2 — Behavior (container behavior API)
 * ============================================================================
 * the Queue class, ported from struct/Queue.java.
 *
 * FUNCTION REGISTRY:
 * ----------------------------------------------------------------------------
 * Constructors:
 *   - Queue_2(element_class, capacity)
 *   - Queue_1(element_class)
 *
 * Core Functions:
 *   - Queue_2Count(element_class, count)
 *   - Queue_free(queue)
 *   - Queue_push(queue, value_or_pointer)
 *   - Queue_pop(queue)
 *   - Queue_peek(queue)
 *   - Queue_slot(queue, index)
 *   - Queue_size(queue)
 *   - Queue_length(queue)
 *   - Queue_capacity(queue)
 *   - Queue_elementClassId(queue)
 *   - Queue_stride(queue)
 *   - Queue_head(queue)
 *   - Queue_dataBuffer(queue)
 *
 * Getters:
 *   - Queue_isEmpty(queue)
 * ============================================================================
 */


// queue.c — Queue port (Legacy: struct/Queue.java).

static const size_t DEFAULT_CAPACITY = 1024;

static Collection *asCollection(Queue *queue) {
    return (Collection*) queue;
}

static Queue *instant(uint32_t elementClass, size_t capacity, size_t count) {
    size_t stride = Stride_get(elementClass);
    size_t cap = capacity < DEFAULT_CAPACITY ? DEFAULT_CAPACITY : capacity;
    Queue *queue = (Queue*) Memory_alloc(TYPE_QUEUE, sizeof(Queue));
    if (!queue)
        return nullptr;

    Collection *c = asCollection(queue);
    (*c).typeId = TYPE_QUEUE;
    (*c).activeCount = (uint32_t)count;
    (*c).elementClass = elementClass;
    (*c).stride = (uint32_t)stride;
    (*c).capacity = (uint32_t)cap;
    (*c).head = 0;

    size_t bytes = cap * stride;
    uint32_t bufType = Type_make(FORM_ARRAY, elementClass);
    (*c).data = (uint8_t*) Memory_alloc(bufType, bytes);
    if (!(*c).data) {
        Memory_free(queue);
        return nullptr;
    }
    memset((*c).data, 0, bytes);
    return queue;
}

static int ensureCapacity(Collection *c) {
    if ((*c).activeCount < (*c).capacity)
        return 1;
    size_t newCap = (*c).capacity + DEFAULT_CAPACITY;
    size_t bytes = newCap * (*c).stride;
    uint32_t bufType = Type_make(FORM_ARRAY, (*c).elementClass);
    uint8_t *next = (uint8_t*) Memory_alloc(bufType, bytes);
    if (!next)
        return 0;

    size_t count = (*c).activeCount;
    if (count > 0) {
        if ((*c).head == 0) {
            memcpy(next, (*c).data, count * (*c).stride);
        } else {
            size_t len1 = (*c).capacity - (*c).head;
            size_t len2 = (*c).head;
            memcpy(next, (*c).data + (*c).head * (*c).stride, len1 * (*c).stride);
            memcpy(next + len1 * (*c).stride, (*c).data, len2 * (*c).stride);
        }
    }
    Memory_free((*c).data);
    (*c).data = next;
    (*c).capacity = (uint32_t)newCap;
    (*c).head = 0;
    return 1;
}

Queue *Queue_2(uint32_t elementClass, size_t capacity) {
    return instant(elementClass, capacity, 0);
}

Queue *Queue_2Count(uint32_t elementClass, size_t count) {
    return instant(elementClass, count, count);
}

void Queue_free(Queue *queue) {
    if (!queue) return;
    Collection *c = asCollection(queue);
    if ((*c).data)
        Memory_free((*c).data);
    Memory_free(queue);
}

void Queue_push(Queue *queue, uint64_t valueOrPointer) {
    if (!queue) return;
    Collection *c = asCollection(queue);
    if (!ensureCapacity(c))
        return;
    size_t tail = ((*c).head + (*c).activeCount) % (*c).capacity;
    Collection_writeSlot(c, tail, valueOrPointer);
    (*c).activeCount++;
}

uint64_t Queue_pop(Queue *queue) {
    if (!queue) return 0;
    Collection *c = asCollection(queue);
    if ((*c).activeCount == 0)
        return 0;
    uint64_t value = Collection_readSlot(c, (*c).head);
    (*c).head = (uint32_t)(((*c).head + 1) % (*c).capacity);
    (*c).activeCount--;
    return value;
}

uint64_t Queue_peek(Queue *queue) {
    if (!queue) return 0;
    Collection *c = asCollection(queue);
    if ((*c).activeCount == 0)
        return 0;
    return Collection_readSlot(c, (*c).head);
}

uint8_t *Queue_slot(Queue *queue, size_t index) {
    if (!queue) return nullptr;
    Collection *c = asCollection(queue);
    if (index >= (*c).activeCount)
        return nullptr;
    size_t phys = ((*c).head + index) % (*c).capacity;
    return (*c).data + phys * (*c).stride;
}

bool Queue_isEmpty(Queue *queue) {
    return Collection_isEmpty(asCollection(queue));
}

size_t Queue_size(Queue *queue) {
    return Collection_size(asCollection(queue));
}

size_t Queue_length(Queue *queue) {
    return Collection_length(asCollection(queue));
}

size_t Queue_capacity(Queue *queue) {
    return Collection_capacity(asCollection(queue));
}

uint32_t Queue_elementClassId(Queue *queue) {
    return Collection_elementClassId(asCollection(queue));
}

size_t Queue_stride(Queue *queue) {
    return Collection_stride(asCollection(queue));
}

size_t Queue_head(Queue *queue) {
    return Collection_head(asCollection(queue));
}

uint8_t *Queue_dataBuffer(Queue *queue) {
    return Collection_dataBuffer(asCollection(queue));
}
Queue *Queue_1(uint32_t element_class) {
    return Queue_2(element_class, 16);
}
