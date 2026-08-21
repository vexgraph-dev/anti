#include "cli/console.h"

#include <stdio.h>
#include <stdlib.h>

#include "primitive/string.h"
#include "thread/ring.h"

// console.c — the Console class, ported from cli/Console.java.

static RingBuffer *log_queue = NULL;
static const size_t CONSOLE_QUEUE_CAPACITY = 1024;

void Console_init(void) {
    if (log_queue)
        return;
    RingBuffer *ring = malloc(sizeof(RingBuffer));
    if (!ring)
        return;
    if (!RingBuffer_init(ring, sizeof(uint8_t *), CONSOLE_QUEUE_CAPACITY)) {
        free(ring);
        return;
    }
    log_queue = ring;
}

void Console_logString(uint8_t *_string) {
    if (!_string)
        return;
    if (log_queue) {
        if (RingBuffer_push(log_queue, &_string))
            return;
    }
    printf("%s\n", string_get(_string));
    string_free(_string);
}

void Console_log(const char *message) {
    if (!message)
        return;
    uint8_t *string_ptr = string_allocate(message);
    if (string_ptr)
        Console_logString(string_ptr);
}

void Console_drain(void) {
    if (!log_queue)
        return;
    uint8_t *string_ptr;
    while (RingBuffer_pop(log_queue, &string_ptr)) {
        printf("%s\n", string_get(string_ptr));
        string_free(string_ptr);
    }
}

void Console_shutdown(void) {
    if (!log_queue)
        return;
    Console_drain();
    RingBuffer_shutdown(log_queue);
    free(log_queue);
    log_queue = NULL;
}