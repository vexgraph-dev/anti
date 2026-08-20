#include "struct/stack.h"

#include <string.h>

#include "nio/mem.h"
#include "oop/stride.h"
#include "oop/type.h"

// stack.c — Stack port (Legacy: struct/Stack.java).

static const size_t DEFAULT_CAPACITY = 1024;

static Collection *as_collection(Stack *stack) {
    return (Collection *)stack;
}

static Stack *instant(uint32_t element_class, size_t capacity, size_t count) {
    size_t stride = Stride_get(element_class);
    size_t cap = capacity < DEFAULT_CAPACITY ? DEFAULT_CAPACITY : capacity;
    Stack *stack = (Stack *)Memory_alloc(TYPE_STACK, sizeof(Stack));
    if (!stack)
        return NULL;

    Collection *c = as_collection(stack);
    (*c).type_id = TYPE_STACK;
    (*c).active_count = (uint32_t)count;
    (*c).element_class = element_class;
    (*c).stride = (uint32_t)stride;
    (*c).capacity = (uint32_t)cap;
    (*c).head = 0;

    size_t bytes = cap * stride;
    uint32_t buf_type = Type_make(FORM_ARRAY, element_class);
    (*c).data = (uint8_t *)Memory_alloc(buf_type, bytes);
    if (!(*c).data) {
        Memory_free(stack);
        return NULL;
    }
    memset((*c).data, 0, bytes);
    return stack;
}

Stack *Stack_allocate(uint32_t element_class, size_t capacity) {
    return instant(element_class, capacity, 0);
}

Stack *Stack_allocateCount(uint32_t element_class, size_t count) {
    return instant(element_class, count, count);
}

void Stack_free(Stack *stack) {
    if (!stack) return;
    Collection *c = as_collection(stack);
    if ((*c).data)
        Memory_free((*c).data);
    Memory_free(stack);
}

void Stack_push(Stack *stack, uint64_t value_or_pointer) {
    if (!stack) return;
    Collection *c = as_collection(stack);
    if ((*c).active_count >= (*c).capacity) {
        size_t new_cap = (*c).capacity + DEFAULT_CAPACITY;
        size_t bytes = new_cap * (*c).stride;
        uint32_t buf_type = Type_make(FORM_ARRAY, (*c).element_class);
        uint8_t *next = (uint8_t *)Memory_alloc(buf_type, bytes);
        if (!next)
            return;
        memcpy(next, (*c).data, (*c).active_count * (*c).stride);
        Memory_free((*c).data);
        (*c).data = next;
        (*c).capacity = (uint32_t)new_cap;
    }
    Collection_writeSlot(c, (*c).active_count, value_or_pointer);
    (*c).active_count++;
}

uint64_t Stack_pop(Stack *stack) {
    if (!stack) return 0;
    Collection *c = as_collection(stack);
    if ((*c).active_count == 0)
        return 0;
    (*c).active_count--;
    return Collection_readSlot(c, (*c).active_count);
}

uint64_t Stack_peek(Stack *stack) {
    if (!stack) return 0;
    Collection *c = as_collection(stack);
    if ((*c).active_count == 0)
        return 0;
    return Collection_readSlot(c, (*c).active_count - 1);
}

uint8_t *Stack_slot(Stack *stack, size_t index) {
    if (!stack) return NULL;
    Collection *c = as_collection(stack);
    if (index >= (*c).active_count)
        return NULL;
    return (*c).data + index * (*c).stride;
}

bool Stack_isEmpty(Stack *stack) {
    return Collection_isEmpty(as_collection(stack));
}

size_t Stack_size(Stack *stack) {
    return Collection_size(as_collection(stack));
}

size_t Stack_length(Stack *stack) {
    return Collection_length(as_collection(stack));
}

size_t Stack_capacity(Stack *stack) {
    return Collection_capacity(as_collection(stack));
}

uint32_t Stack_elementClassId(Stack *stack) {
    return Collection_elementClassId(as_collection(stack));
}

size_t Stack_stride(Stack *stack) {
    return Collection_stride(as_collection(stack));
}

uint8_t *Stack_dataBuffer(Stack *stack) {
    return Collection_dataBuffer(as_collection(stack));
}