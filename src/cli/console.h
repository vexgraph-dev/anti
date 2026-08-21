#ifndef CLI_CONSOLE_H
#define CLI_CONSOLE_H

#include <stdint.h>

// cli/console.h — the Console class, ported from cli/Console.java.
//
// Console is a zero-allocation log sink. Every message becomes a string block
// whose pointer is pushed onto a lock-free RingBuffer; a consumer drains it
// elsewhere. If the queue is full or not yet initialized, the message prints
// directly and its block is freed on the spot.

// Set up the internal log queue (idempotent). Called lazily by Console_log.
void Console_init(void);

// Log a C string. The string is boxed and owned by the queue.
void Console_log(const char *message);

// Log an already-boxed string block. Console takes ownership.
void Console_logString(uint8_t *_string);

// Pop every queued message, print it, and free its block.
void Console_drain(void);

// Drain any remaining messages and tear the queue down.
void Console_shutdown(void);

#endif