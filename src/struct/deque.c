#include "struct/deque.h"

#include <string.h>

#include "nio/mem.h"
#include "oop/stride.h"
#include "oop/type.h"

// deque.c — Deque port (Legacy: struct/Deque.java). Circular buffer: logical
// index i maps to physical (head + i) % capacity.

static const size_t DEFAULT_CAPACITY = 1024;

static Collection *as_collection(Deque *deque) {
    return (Collection *)deque;
}

static Deque *instant(uint32_t element_class, size_t capacity, size_t count) {
    size_t stride = Stride_get(element_class);
    size_t cap = capacity < DEFAULT_CAPACITY ? DEFAULT_CAPACITY : capacity;
    Deque *deque = (Deque *)Memory_alloc(TYPE_DEQUE, sizeof(Deque));
    if (!deque)
        return NULL;

    Collection *c = as_collection(deque);
    (*c).type_id = TYPE_DEQUE;
    (*c).active_count = (uint32_t)count;
    (*c).element_class = element_class;
    (*c).stride = (uint32_t)stride;
    (*c).capacity = (uint32_t)cap;
    (*c).head = 0;

    size_t bytes = cap * stride;
    uint32_t buf_type = Type_make(FORM_ARRAY, element_class);
    (*c).data = (uint8_t *)Memory_alloc(buf_type, bytes);
    if (!(*c).data) {
        Memory_free(deque);
        return NULL;
    }
    memset((*c).data, 0, bytes);
    return deque;
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

Deque *Deque_allocate(uint32_t element_class, size_t capacity) {
    return instant(element_class, capacity, 0);
}

Deque *Deque_allocateCount(uint32_t element_class, size_t count) {
    return instant(element_class, count, count);
}

void Deque_free(Deque *deque) {
    if (!deque) return;
    Collection *c = as_collection(deque);
    if ((*c).data)
        Memory_free((*c).data);
    Memory_free(deque);
}

void Deque_addFirst(Deque *deque, uint64_t value_or_pointer) {
    if (!deque) return;
    Collection *c = as_collection(deque);
    if (!ensure_capacity(c))
        return;
    size_t new_head = ((*c).head - 1 + (*c).capacity) % (*c).capacity;
    uint8_t *slot = (*c).data + new_head * (*c).stride;
    switch ((*c).stride) {
        case 1:  *(uint8_t *)slot = (uint8_t)value_or_pointer;  break;
        case 2:  *(uint16_t *)slot = (uint16_t)value_or_pointer; break;
        case 4:  *(uint32_t *)slot = (uint32_t)value_or_pointer; break;
        default: *(uint64_t *)slot = value_or_pointer;          break;
    }
    (*c).head = (uint32_t)new_head;
    (*c).active_count++;
}

void Deque_addLast(Deque *deque, uint64_t value_or_pointer) {
    if (!deque) return;
    Collection *c = as_collection(deque);
    if (!ensure_capacity(c))
        return;
    size_t tail = ((*c).head + (*c).active_count) % (*c).capacity;
    Collection_writeSlot(c, tail, value_or_pointer);
    (*c).active_count++;
}

uint64_t Deque_removeFirst(Deque *deque) {
    if (!deque) return 0;
    Collection *c = as_collection(deque);
    if ((*c).active_count == 0)
        return 0;
    uint64_t value = Collection_readSlot(c, (*c).head);
    (*c).head = (uint32_t)(((*c).head + 1) % (*c).capacity);
    (*c).active_count--;
    return value;
}

uint64_t Deque_removeLast(Deque *deque) {
    if (!deque) return 0;
    Collection *c = as_collection(deque);
    if ((*c).active_count == 0)
        return 0;
    size_t tail = ((*c).head + (*c).active_count - 1) % (*c).capacity;
    uint64_t value = Collection_readSlot(c, tail);
    (*c).active_count--;
    return value;
}

uint64_t Deque_peekFirst(Deque *deque) {
    if (!deque) return 0;
    Collection *c = as_collection(deque);
    if ((*c).active_count == 0)
        return 0;
    return Collection_readSlot(c, (*c).head);
}

uint64_t Deque_peekLast(Deque *deque) {
    if (!deque) return 0;
    Collection *c = as_collection(deque);
    if ((*c).active_count == 0)
        return 0;
    size_t tail = ((*c).head + (*c).active_count - 1) % (*c).capacity;
    return Collection_readSlot(c, tail);
}

uint64_t Deque_get(Deque *deque, size_t index) {
    if (!deque) return 0;
    Collection *c = as_collection(deque);
    if (index >= (*c).active_count)
        return 0;
    size_t phys = ((*c).head + index) % (*c).capacity;
    return Collection_readSlot(c, phys);
}

uint8_t *Deque_slot(Deque *deque, size_t index) {
    if (!deque) return NULL;
    Collection *c = as_collection(deque);
    if (index >= (*c).active_count)
        return NULL;
    size_t phys = ((*c).head + index) % (*c).capacity;
    return (*c).data + phys * (*c).stride;
}

bool Deque_isEmpty(Deque *deque) {
    return Collection_isEmpty(as_collection(deque));
}

size_t Deque_size(Deque *deque) {
    return Collection_size(as_collection(deque));
}

size_t Deque_length(Deque *deque) {
    return Collection_length(as_collection(deque));
}

size_t Deque_capacity(Deque *deque) {
    return Collection_capacity(as_collection(deque));
}

uint32_t Deque_elementClassId(Deque *deque) {
    return Collection_elementClassId(as_collection(deque));
}

size_t Deque_stride(Deque *deque) {
    return Collection_stride(as_collection(deque));
}

size_t Deque_head(Deque *deque) {
    return Collection_head(as_collection(deque));
}

uint8_t *Deque_dataBuffer(Deque *deque) {
    return Collection_dataBuffer(as_collection(deque));
}