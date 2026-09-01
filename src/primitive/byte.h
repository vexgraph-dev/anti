#ifndef PRIMITIVE_BYTE_H
#define PRIMITIVE_BYTE_H

#include <stdbool.h>
#include <stddef.h>
#include <stdint.h>

#include "bit/bit.h"

#include "c23/constructor.h"

// primitive/byte.h — Byte primitive (Legacy: primitive/Byte.java).
// Delegates to Bit8 width pool (1B stride).

extern BitPool g_bytePool;

bool Byte_init(void);
void Byte_shutdown(void);
void *Byte_alloc(void);
void *Byte_allocArray(size_t count);
void Byte_free(void *ptr);
int8_t Byte_get(void *ptr);
void Byte_set(void *ptr, int8_t value);
bool Byte_compareAndSet(void *ptr, int8_t expected, int8_t value);
uint32_t Byte_type(void *ptr);
size_t Byte_length(void *ptr);


// Ergonomic constructors — Byte() and Byte(value)
void *Byte_allocWithValue(int8_t value);
#define Byte_0(...) Byte_alloc()
#define Byte_1(value) Byte_allocWithValue(value)
#define Byte(...) CONSTRUCTOR_DISPATCH(Byte, ##__VA_ARGS__)


#define Byte_array(count) Byte_allocArray(count)

#endif
