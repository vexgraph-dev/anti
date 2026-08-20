#include "struct/list.h"

#include <stdlib.h>
#include <string.h>

#include "nio/mem.h"
#include "oop/stride.h"
#include "oop/type.h"

// list.c — List port (Legacy: struct/List.java).

static const size_t DEFAULT_CAPACITY = 1024;

static Collection *as_collection(List *list) {
    return (Collection *)list;
}

static uint8_t *buffer_grow(Collection *c, size_t needed) {
    size_t new_cap = (*c).capacity;
    while (new_cap < needed)
        new_cap += DEFAULT_CAPACITY;
    size_t bytes = new_cap * (*c).stride;
    uint32_t buf_type = Type_make(FORM_ARRAY, (*c).element_class);
    uint8_t *next = (uint8_t *)Memory_alloc(buf_type, bytes);
    if (!next)
        return NULL;
    size_t old_bytes = (*c).active_count * (*c).stride;
    memcpy(next, (*c).data, old_bytes);
    Memory_free((*c).data);
    (*c).data = next;
    (*c).capacity = (uint32_t)new_cap;
    return next;
}

static List *instant(uint32_t element_class, size_t capacity, size_t count) {
    size_t stride = Stride_get(element_class);
    size_t cap = capacity < DEFAULT_CAPACITY ? DEFAULT_CAPACITY : capacity;
    List *list = (List *)Memory_alloc(TYPE_LIST, sizeof(List));
    if (!list)
        return NULL;

    Collection *c = as_collection(list);
    (*c).type_id = TYPE_LIST;
    (*c).active_count = (uint32_t)count;
    (*c).element_class = element_class;
    (*c).stride = (uint32_t)stride;
    (*c).capacity = (uint32_t)cap;
    (*c).head = 0;

    size_t bytes = cap * stride;
    uint32_t buf_type = Type_make(FORM_ARRAY, element_class);
    (*c).data = (uint8_t *)Memory_alloc(buf_type, bytes);
    if (!(*c).data) {
        Memory_free(list);
        return NULL;
    }
    memset((*c).data, 0, bytes);
    return list;
}

List *List_allocate(uint32_t element_class, size_t capacity) {
    return instant(element_class, capacity, 0);
}

List *List_allocateCount(uint32_t element_class, size_t count) {
    return instant(element_class, count, count);
}

void List_free(List *list) {
    if (!list) return;
    Collection *c = as_collection(list);
    if ((*c).data)
        Memory_free((*c).data);
    Memory_free(list);
}

void List_add(List *list, uint64_t value_or_pointer) {
    if (!list) return;
    Collection *c = as_collection(list);
    if ((*c).active_count >= (*c).capacity) {
        if (!buffer_grow(c, (*c).active_count + 1))
            return;
    }
    Collection_writeSlot(c, (*c).active_count, value_or_pointer);
    (*c).active_count++;
}

uint8_t *List_addSlot(List *list) {
    if (!list) return NULL;
    Collection *c = as_collection(list);
    if ((*c).active_count >= (*c).capacity) {
        if (!buffer_grow(c, (*c).active_count + 1))
            return NULL;
    }
    uint8_t *slot = (*c).data + (*c).active_count * (*c).stride;
    memset(slot, 0, (*c).stride);
    (*c).active_count++;
    return slot;
}

uint64_t List_get(List *list, size_t index) {
    if (!list) return 0;
    Collection *c = as_collection(list);
    if (index >= (*c).active_count)
        return 0;
    return Collection_readSlot(c, index);
}

void List_set(List *list, size_t index, uint64_t value) {
    if (!list) return;
    Collection *c = as_collection(list);
    if (index >= (*c).active_count)
        return;
    Collection_writeSlot(c, index, value);
}

uint8_t *List_slot(List *list, size_t index) {
    if (!list) return NULL;
    Collection *c = as_collection(list);
    if (index >= (*c).active_count)
        return NULL;
    return (*c).data + index * (*c).stride;
}

void List_remove(List *list, size_t index) {
    if (!list) return;
    Collection *c = as_collection(list);
    if (index >= (*c).active_count)
        return;
    size_t tail = (*c).active_count - index - 1;
    if (tail > 0) {
        memmove((*c).data + index * (*c).stride,
                (*c).data + (index + 1) * (*c).stride,
                tail * (*c).stride);
    }
    (*c).active_count--;
}

bool List_compare(List *a, List *b) {
    if (a == b) return true;
    if (!a || !b) return false;
    Collection *ca = as_collection(a);
    Collection *cb = as_collection(b);
    if ((*ca).active_count != (*cb).active_count)
        return false;
    if ((*ca).stride != (*cb).stride)
        return false;
    size_t bytes = (*ca).active_count * (*ca).stride;
    return memcmp((*ca).data, (*cb).data, bytes) == 0;
}

bool List_isEmpty(List *list) {
    return Collection_isEmpty(as_collection(list));
}

size_t List_size(List *list) {
    return Collection_size(as_collection(list));
}

size_t List_length(List *list) {
    return Collection_length(as_collection(list));
}

size_t List_capacity(List *list) {
    return Collection_capacity(as_collection(list));
}

uint32_t List_elementClassId(List *list) {
    return Collection_elementClassId(as_collection(list));
}

size_t List_stride(List *list) {
    return Collection_stride(as_collection(list));
}

uint8_t *List_dataBuffer(List *list) {
    return Collection_dataBuffer(as_collection(list));
}