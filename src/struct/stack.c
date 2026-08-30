#include "struct/stack.h"

#include <string.h>

#include "nio/mem.h"
#include "oop/stride.h"
#include "oop/type.h"

// stack.c — Stack port (Legacy: struct/Stack.java).

static const size_t DEFAULT_CAPACITY = 1024;

static Collection *asCollection(Stack *stack) {
    return (Collection *)stack;
}

static Stack *instant(uint32_t elementClass, size_t capacity, size_t count) {
    size_t stride = Stride_get(elementClass);
    size_t cap = capacity < DEFAULT_CAPACITY ? DEFAULT_CAPACITY : capacity;
    Stack *stack = (Stack *)Memory_alloc(TYPE_STACK, sizeof(Stack));
    if (!stack)
        return nullptr;

    Collection *c = asCollection(stack);
    (*c).typeId = TYPE_STACK;
    (*c).activeCount = (uint32_t)count;
    (*c).elementClass = elementClass;
    (*c).stride = (uint32_t)stride;
    (*c).capacity = (uint32_t)cap;
    (*c).head = 0;

    size_t bytes = cap * stride;
    uint32_t bufType = Type_make(FORM_ARRAY, elementClass);
    (*c).data = (uint8_t *)Memory_alloc(bufType, bytes);
    if (!(*c).data) {
        Memory_free(stack);
        return nullptr;
    }
    memset((*c).data, 0, bytes);
    return stack;
}

Stack *Stack_allocate(uint32_t elementClass, size_t capacity) {
    return instant(elementClass, capacity, 0);
}

Stack *Stack_allocateCount(uint32_t elementClass, size_t count) {
    return instant(elementClass, count, count);
}

void Stack_free(Stack *stack) {
    if (!stack) return;
    Collection *c = asCollection(stack);
    if ((*c).data)
        Memory_free((*c).data);
    Memory_free(stack);
}

void Stack_push(Stack *stack, uint64_t valueOrPointer) {
    if (!stack) return;
    Collection *c = asCollection(stack);
    if ((*c).activeCount >= (*c).capacity) {
        size_t newCap = (*c).capacity + DEFAULT_CAPACITY;
        size_t bytes = newCap * (*c).stride;
        uint32_t bufType = Type_make(FORM_ARRAY, (*c).elementClass);
        uint8_t *next = (uint8_t *)Memory_alloc(bufType, bytes);
        if (!next)
            return;
        memcpy(next, (*c).data, (*c).activeCount * (*c).stride);
        Memory_free((*c).data);
        (*c).data = next;
        (*c).capacity = (uint32_t)newCap;
    }
    Collection_writeSlot(c, (*c).activeCount, valueOrPointer);
    (*c).activeCount++;
}

uint64_t Stack_pop(Stack *stack) {
    if (!stack) return 0;
    Collection *c = asCollection(stack);
    if ((*c).activeCount == 0)
        return 0;
    (*c).activeCount--;
    return Collection_readSlot(c, (*c).activeCount);
}

uint64_t Stack_peek(Stack *stack) {
    if (!stack) return 0;
    Collection *c = asCollection(stack);
    if ((*c).activeCount == 0)
        return 0;
    return Collection_readSlot(c, (*c).activeCount - 1);
}

uint8_t *Stack_slot(Stack *stack, size_t index) {
    if (!stack) return nullptr;
    Collection *c = asCollection(stack);
    if (index >= (*c).activeCount)
        return nullptr;
    return (*c).data + index * (*c).stride;
}

bool Stack_isEmpty(Stack *stack) {
    return Collection_isEmpty(asCollection(stack));
}

size_t Stack_size(Stack *stack) {
    return Collection_size(asCollection(stack));
}

size_t Stack_length(Stack *stack) {
    return Collection_length(asCollection(stack));
}

size_t Stack_capacity(Stack *stack) {
    return Collection_capacity(asCollection(stack));
}

uint32_t Stack_elementClassId(Stack *stack) {
    return Collection_elementClassId(asCollection(stack));
}

size_t Stack_stride(Stack *stack) {
    return Collection_stride(asCollection(stack));
}

uint8_t *Stack_dataBuffer(Stack *stack) {
    return Collection_dataBuffer(asCollection(stack));
}