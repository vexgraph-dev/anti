#include "struct/deque.h"

#include <string.h>

#include "nio/mem.h"
#include "oop/stride.h"
#include "oop/type.h"

// deque.c — Deque port (Legacy: struct/Deque.java). Circular buffer: logical
// index i maps to physical (head + i) % capacity.

static const size_t DEFAULT_CAPACITY = 1024;

static Collection *asCollection(Deque *deque) {
    return (Collection *)deque;
}

static Deque *instant(uint32_t elementClass, size_t capacity, size_t count) {
    size_t stride = Stride_get(elementClass);
    size_t cap = capacity < DEFAULT_CAPACITY ? DEFAULT_CAPACITY : capacity;
    Deque *deque = (Deque *)Memory_alloc(TYPE_DEQUE, sizeof(Deque));
    if (!deque)
        return nullptr;

    Collection *c = asCollection(deque);
    (*c).typeId = TYPE_DEQUE;
    (*c).activeCount = (uint32_t)count;
    (*c).elementClass = elementClass;
    (*c).stride = (uint32_t)stride;
    (*c).capacity = (uint32_t)cap;
    (*c).head = 0;

    size_t bytes = cap * stride;
    uint32_t bufType = Type_make(FORM_ARRAY, elementClass);
    (*c).data = (uint8_t *)Memory_alloc(bufType, bytes);
    if (!(*c).data) {
        Memory_free(deque);
        return nullptr;
    }
    memset((*c).data, 0, bytes);
    return deque;
}

static int ensureCapacity(Collection *c) {
    if ((*c).activeCount < (*c).capacity)
        return 1;
    size_t newCap = (*c).capacity + DEFAULT_CAPACITY;
    size_t bytes = newCap * (*c).stride;
    uint32_t bufType = Type_make(FORM_ARRAY, (*c).elementClass);
    uint8_t *next = (uint8_t *)Memory_alloc(bufType, bytes);
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

Deque *Deque_allocate(uint32_t elementClass, size_t capacity) {
    return instant(elementClass, capacity, 0);
}

Deque *Deque_allocateCount(uint32_t elementClass, size_t count) {
    return instant(elementClass, count, count);
}

void Deque_free(Deque *deque) {
    if (!deque) return;
    Collection *c = asCollection(deque);
    if ((*c).data)
        Memory_free((*c).data);
    Memory_free(deque);
}

void Deque_addFirst(Deque *deque, uint64_t valueOrPointer) {
    if (!deque) return;
    Collection *c = asCollection(deque);
    if (!ensureCapacity(c))
        return;
    size_t newHead = ((*c).head - 1 + (*c).capacity) % (*c).capacity;
    uint8_t *slot = (*c).data + newHead * (*c).stride;
    switch ((*c).stride) {
        case 1:  *(uint8_t *)slot = (uint8_t)valueOrPointer;  break;
        case 2:  *(uint16_t *)slot = (uint16_t)valueOrPointer; break;
        case 4:  *(uint32_t *)slot = (uint32_t)valueOrPointer; break;
        default: *(uint64_t *)slot = valueOrPointer;          break;
    }
    (*c).head = (uint32_t)newHead;
    (*c).activeCount++;
}

void Deque_addLast(Deque *deque, uint64_t valueOrPointer) {
    if (!deque) return;
    Collection *c = asCollection(deque);
    if (!ensureCapacity(c))
        return;
    size_t tail = ((*c).head + (*c).activeCount) % (*c).capacity;
    Collection_writeSlot(c, tail, valueOrPointer);
    (*c).activeCount++;
}

uint64_t Deque_removeFirst(Deque *deque) {
    if (!deque) return 0;
    Collection *c = asCollection(deque);
    if ((*c).activeCount == 0)
        return 0;
    uint64_t value = Collection_readSlot(c, (*c).head);
    (*c).head = (uint32_t)(((*c).head + 1) % (*c).capacity);
    (*c).activeCount--;
    return value;
}

uint64_t Deque_removeLast(Deque *deque) {
    if (!deque) return 0;
    Collection *c = asCollection(deque);
    if ((*c).activeCount == 0)
        return 0;
    size_t tail = ((*c).head + (*c).activeCount - 1) % (*c).capacity;
    uint64_t value = Collection_readSlot(c, tail);
    (*c).activeCount--;
    return value;
}

uint64_t Deque_peekFirst(Deque *deque) {
    if (!deque) return 0;
    Collection *c = asCollection(deque);
    if ((*c).activeCount == 0)
        return 0;
    return Collection_readSlot(c, (*c).head);
}

uint64_t Deque_peekLast(Deque *deque) {
    if (!deque) return 0;
    Collection *c = asCollection(deque);
    if ((*c).activeCount == 0)
        return 0;
    size_t tail = ((*c).head + (*c).activeCount - 1) % (*c).capacity;
    return Collection_readSlot(c, tail);
}

uint64_t Deque_get(Deque *deque, size_t index) {
    if (!deque) return 0;
    Collection *c = asCollection(deque);
    if (index >= (*c).activeCount)
        return 0;
    size_t phys = ((*c).head + index) % (*c).capacity;
    return Collection_readSlot(c, phys);
}

uint8_t *Deque_slot(Deque *deque, size_t index) {
    if (!deque) return nullptr;
    Collection *c = asCollection(deque);
    if (index >= (*c).activeCount)
        return nullptr;
    size_t phys = ((*c).head + index) % (*c).capacity;
    return (*c).data + phys * (*c).stride;
}

bool Deque_isEmpty(Deque *deque) {
    return Collection_isEmpty(asCollection(deque));
}

size_t Deque_size(Deque *deque) {
    return Collection_size(asCollection(deque));
}

size_t Deque_length(Deque *deque) {
    return Collection_length(asCollection(deque));
}

size_t Deque_capacity(Deque *deque) {
    return Collection_capacity(asCollection(deque));
}

uint32_t Deque_elementClassId(Deque *deque) {
    return Collection_elementClassId(asCollection(deque));
}

size_t Deque_stride(Deque *deque) {
    return Collection_stride(asCollection(deque));
}

size_t Deque_head(Deque *deque) {
    return Collection_head(asCollection(deque));
}

uint8_t *Deque_dataBuffer(Deque *deque) {
    return Collection_dataBuffer(asCollection(deque));
}