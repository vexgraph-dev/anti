#ifndef STRUCT_QUEUE_H
#define STRUCT_QUEUE_H

#include <stdbool.h>
#include <stddef.h>
#include <stdint.h>
#include "c23/constructor.h"

#include "struct/collection.h"

// struct/queue.h — the Queue class, ported from struct/Queue.java.
// FIFO circular buffer; push appends at the tail, pop removes from the head.

typedef struct Queue {
    Collection collection;
} Queue;

Queue *Queue_2(uint32_t element_class, size_t capacity);

// Queue pre-filled to count slots (legacy allocate): active_count = count.
Queue *Queue_2Count(uint32_t element_class, size_t count);

void Queue_free(Queue *queue);

void Queue_push(Queue *queue, uint64_t value_or_pointer);
uint64_t Queue_pop(Queue *queue);
uint64_t Queue_peek(Queue *queue);
uint8_t *Queue_slot(Queue *queue, size_t index);

bool Queue_isEmpty(Queue *queue);
size_t Queue_size(Queue *queue);
size_t Queue_length(Queue *queue);
size_t Queue_capacity(Queue *queue);
uint32_t Queue_elementClassId(Queue *queue);
size_t Queue_stride(Queue *queue);
size_t Queue_head(Queue *queue);
uint8_t *Queue_dataBuffer(Queue *queue);


Queue *Queue_1(uint32_t element_class);

#define Queue(...) CONSTRUCTOR_DISPATCH(Queue, ##__VA_ARGS__)

#endif