#include "struct/list.h"

#include <stdlib.h>
#include <string.h>

#include "nio/mem.h"
#include "oop/stride.h"
#include "oop/type.h"
#include "annotation/overview.h"

;;OVERVIEW
/**
 * ============================================================================
 * MODULE: List (struct/list.c)
 * LEVEL: L2 — Behavior (container behavior API)
 * ============================================================================
 * the List class, ported from struct/List.java.
 *
 * FUNCTION REGISTRY:
 * ----------------------------------------------------------------------------
 * Constructors:
 *   - List_2(element_class, capacity)
 *   - List_1(element_class)
 *
 * Core Functions:
 *   - List_2Count(element_class, count)
 *   - List_free(list)
 *   - List_add(list, value_or_pointer)
 *   - List_addSlot(list)
 *   - List_slot(list, index)
 *   - List_remove(list, index)
 *   - List_compare(a, b)
 *   - List_size(list)
 *   - List_length(list)
 *   - List_capacity(list)
 *   - List_elementClassId(list)
 *   - List_stride(list)
 *   - List_dataBuffer(list)
 *
 * Setters:
 *   - List_set(list, index, value)
 *
 * Getters:
 *   - List_get(list, index)
 *   - List_isEmpty(list)
 * ============================================================================
 */


// list.c — List port (Legacy: struct/List.java).

static const size_t DEFAULT_CAPACITY = 1024;

static Collection *asCollection(List *list) {
    return (Collection*) list;
}

static uint8_t *bufferGrow(Collection *c, size_t needed) {
    size_t newCap = (*c).capacity;
    while (newCap < needed)
        newCap += DEFAULT_CAPACITY;
    size_t bytes = newCap * (*c).stride;
    uint32_t bufType = Type_make(FORM_ARRAY, (*c).elementClass);
    uint8_t *next = (uint8_t*) Memory_alloc(bufType, bytes);
    if (!next)
        return nullptr;
    size_t oldBytes = (*c).activeCount * (*c).stride;
    memcpy(next, (*c).data, oldBytes);
    Memory_free((*c).data);
    (*c).data = next;
    (*c).capacity = (uint32_t)newCap;
    return next;
}

static List *instant(uint32_t elementClass, size_t capacity, size_t count) {
    size_t stride = Stride_get(elementClass);
    size_t cap = capacity < DEFAULT_CAPACITY ? DEFAULT_CAPACITY : capacity;
    List *list = (List*) Memory_alloc(TYPE_LIST, sizeof(List));
    if (!list)
        return nullptr;

    Collection *c = asCollection(list);
    (*c).typeId = TYPE_LIST;
    (*c).activeCount = (uint32_t)count;
    (*c).elementClass = elementClass;
    (*c).stride = (uint32_t)stride;
    (*c).capacity = (uint32_t)cap;
    (*c).head = 0;

    size_t bytes = cap * stride;
    uint32_t bufType = Type_make(FORM_ARRAY, elementClass);
    (*c).data = (uint8_t*) Memory_alloc(bufType, bytes);
    if (!(*c).data) {
        Memory_free(list);
        return nullptr;
    }
    memset((*c).data, 0, bytes);
    return list;
}

List *List_2(uint32_t elementClass, size_t capacity) {
    return instant(elementClass, capacity, 0);
}

List *List_2Count(uint32_t elementClass, size_t count) {
    return instant(elementClass, count, count);
}

void List_free(List *list) {
    if (!list) return;
    Collection *c = asCollection(list);
    if ((*c).data)
        Memory_free((*c).data);
    Memory_free(list);
}

void List_add(List *list, uint64_t valueOrPointer) {
    if (!list) return;
    Collection *c = asCollection(list);
    if ((*c).activeCount >= (*c).capacity) {
        if (!bufferGrow(c, (*c).activeCount + 1))
            return;
    }
    Collection_writeSlot(c, (*c).activeCount, valueOrPointer);
    (*c).activeCount++;
}

uint8_t *List_addSlot(List *list) {
    if (!list) return nullptr;
    Collection *c = asCollection(list);
    if ((*c).activeCount >= (*c).capacity) {
        if (!bufferGrow(c, (*c).activeCount + 1))
            return nullptr;
    }
    uint8_t *slot = (*c).data + (*c).activeCount * (*c).stride;
    memset(slot, 0, (*c).stride);
    (*c).activeCount++;
    return slot;
}

uint64_t List_get(List *list, size_t index) {
    if (!list) return 0;
    Collection *c = asCollection(list);
    if (index >= (*c).activeCount)
        return 0;
    return Collection_readSlot(c, index);
}

void List_set(List *list, size_t index, uint64_t value) {
    if (!list) return;
    Collection *c = asCollection(list);
    if (index >= (*c).activeCount)
        return;
    Collection_writeSlot(c, index, value);
}

uint8_t *List_slot(List *list, size_t index) {
    if (!list) return nullptr;
    Collection *c = asCollection(list);
    if (index >= (*c).activeCount)
        return nullptr;
    return (*c).data + index * (*c).stride;
}

void List_remove(List *list, size_t index) {
    if (!list) return;
    Collection *c = asCollection(list);
    if (index >= (*c).activeCount)
        return;
    size_t tail = (*c).activeCount - index - 1;
    if (tail > 0) {
        memmove((*c).data + index * (*c).stride,
                (*c).data + (index + 1) * (*c).stride,
                tail * (*c).stride);
    }
    (*c).activeCount--;
}

bool List_compare(List *a, List *b) {
    if (a == b) return true;
    if (!a || !b) return false;
    Collection *ca = asCollection(a);
    Collection *cb = asCollection(b);
    if ((*ca).activeCount != (*cb).activeCount)
        return false;
    if ((*ca).stride != (*cb).stride)
        return false;
    size_t bytes = (*ca).activeCount * (*ca).stride;
    return memcmp((*ca).data, (*cb).data, bytes) == 0;
}

bool List_isEmpty(List *list) {
    return Collection_isEmpty(asCollection(list));
}

size_t List_size(List *list) {
    return Collection_size(asCollection(list));
}

size_t List_length(List *list) {
    return Collection_length(asCollection(list));
}

size_t List_capacity(List *list) {
    return Collection_capacity(asCollection(list));
}

uint32_t List_elementClassId(List *list) {
    return Collection_elementClassId(asCollection(list));
}

size_t List_stride(List *list) {
    return Collection_stride(asCollection(list));
}

uint8_t *List_dataBuffer(List *list) {
    return Collection_dataBuffer(asCollection(list));
}
List *List_1(uint32_t element_class) {
    return List_2(element_class, 16);
}
