#ifndef STRUCT_DEQUE_H
#define STRUCT_DEQUE_H

#include <stdbool.h>
#include <stddef.h>
#include <stdint.h>
#include "c23/constructor.h"

#include "struct/collection.h"

// struct/deque.h — the Deque class, ported from struct/Deque.java.
// Double-ended circular buffer over a stride-based buffer; the head field
// tracks the front so addFirst/removeFirst wrap around the ring.

typedef struct Deque {
    Collection collection;
} Deque;

Deque *Deque_2(uint32_t element_class, size_t capacity);

// Deque pre-filled to count slots (legacy allocate): active_count = count.
Deque *Deque_2Count(uint32_t element_class, size_t count);

void Deque_free(Deque *deque);

void Deque_addFirst(Deque *deque, uint64_t value_or_pointer);
void Deque_addLast(Deque *deque, uint64_t value_or_pointer);
uint64_t Deque_removeFirst(Deque *deque);
uint64_t Deque_removeLast(Deque *deque);
uint64_t Deque_peekFirst(Deque *deque);
uint64_t Deque_peekLast(Deque *deque);
uint64_t Deque_get(Deque *deque, size_t index);
uint8_t *Deque_slot(Deque *deque, size_t index);

bool Deque_isEmpty(Deque *deque);
size_t Deque_size(Deque *deque);
size_t Deque_length(Deque *deque);
size_t Deque_capacity(Deque *deque);
uint32_t Deque_elementClassId(Deque *deque);
size_t Deque_stride(Deque *deque);
size_t Deque_head(Deque *deque);
uint8_t *Deque_dataBuffer(Deque *deque);


Deque *Deque_1(uint32_t element_class);

#define Deque(...) CONSTRUCTOR_DISPATCH(Deque, __VA_ARGS__)

#endif