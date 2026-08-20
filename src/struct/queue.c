#include "struct/queue.h"

#include <string.h>

#include "nio/mem.h"
#include "oop/stride.h"
#include "oop/type.h"

// queue.c — Queue port (Legacy: struct/Queue.java).

static const size_t DEFAULT_CAPACITY = 1024;

static Collection *as_collection(Queue *queue) {
    return (Collection *)queue;
}

static Queue *instant(uint32_t element_class, size_t capacity, size_t count) {
    size_t stride = Stride_get(element_class);
    size_t cap = capacity < DEFAULT_CAPACITY ? DEFAULT_CAPACITY : capacity;
    Queue *queue = (Queue *)Memory_alloc(TYPE_QUEUE, sizeof(Queue));
    if (!queue)
        return NULL;

    Collection *c = as_collection(queue);
    (*c).type_id = TYPE_QUEUE;
    (*c).active_count = (uint32_t)count;
    (*c).element_class = element_class;
    (*c).stride = (uint32_t)stride;
    (*c).capacity = (uint32_t)cap;
    (*c).head = 0;

    size_t bytes = cap * stride;
    uint32_t buf_type = Type_make(FORM_ARRAY, element_class);
    (*c).data = (uint8_t *)Memory_alloc(buf_type, bytes);
    if (!(*c).data) {
        Memory_free(queue);
        return NULL;
    }
    memset((*c).data, 0, bytes);
    return queue;
}

static int ensure_capacity(Collection *c) {
    if ((*c).active_count < (*c).capacity)
        return 1;
    size_t new_cap = (*c).capacity + DEFAULT_CAPACITY;
    size_t bytes = new_cap * (*c).stride;
    uint32_t buf_type = Type_make(FORM_ARRAY, (*c).element_class);
    uint8_t *next = (uint8_t *)Memory_alloc(buf_type, bytes);
    if (!next)
        return 0;

    size_t count = (*c).active_count;
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
    (*c).capacity = (uint32_t)new_cap;
    (*c).head = 0;
    return 1;
}

Queue *Queue_allocate(uint32_t element_class, size_t capacity) {
    return instant(element_class, capacity, 0);
}

Queue *Queue_allocateCount(uint32_t element_class, size_t count) {
    return instant(element_class, count, count);
}

void Queue_free(Queue *queue) {
    if (!queue) return;
    Collection *c = as_collection(queue);
    if ((*c).data)
        Memory_free((*c).data);
    Memory_free(queue);
}

void Queue_push(Queue *queue, uint64_t value_or_pointer) {
    if (!queue) return;
    Collection *c = as_collection(queue);
    if (!ensure_capacity(c))
        return;
    size_t tail = ((*c).head + (*c).active_count) % (*c).capacity;
    Collection_writeSlot(c, tail, value_or_pointer);
    (*c).active_count++;
}

uint64_t Queue_pop(Queue *queue) {
    if (!queue) return 0;
    Collection *c = as_collection(queue);
    if ((*c).active_count == 0)
        return 0;
    uint64_t value = Collection_readSlot(c, (*c).head);
    (*c).head = (uint32_t)(((*c).head + 1) % (*c).capacity);
    (*c).active_count--;
    return value;
}

uint64_t Queue_peek(Queue *queue) {
    if (!queue) return 0;
    Collection *c = as_collection(queue);
    if ((*c).active_count == 0)
        return 0;
    return Collection_readSlot(c, (*c).head);
}

uint8_t *Queue_slot(Queue *queue, size_t index) {
    if (!queue) return NULL;
    Collection *c = as_collection(queue);
    if (index >= (*c).active_count)
        return NULL;
    size_t phys = ((*c).head + index) % (*c).capacity;
    return (*c).data + phys * (*c).stride;
}

bool Queue_isEmpty(Queue *queue) {
    return Collection_isEmpty(as_collection(queue));
}

size_t Queue_size(Queue *queue) {
    return Collection_size(as_collection(queue));
}

size_t Queue_length(Queue *queue) {
    return Collection_length(as_collection(queue));
}

size_t Queue_capacity(Queue *queue) {
    return Collection_capacity(as_collection(queue));
}

uint32_t Queue_elementClassId(Queue *queue) {
    return Collection_elementClassId(as_collection(queue));
}

size_t Queue_stride(Queue *queue) {
    return Collection_stride(as_collection(queue));
}

size_t Queue_head(Queue *queue) {
    return Collection_head(as_collection(queue));
}

uint8_t *Queue_dataBuffer(Queue *queue) {
    return Collection_dataBuffer(as_collection(queue));
}