#ifndef PRIMITIVE_BRAIN_H
#define PRIMITIVE_BRAIN_H

#include <stdbool.h>
#include <stddef.h>
#include <stdint.h>

#include "bit/bit.h"

#include "c23/constructor.h"

// primitive/brain.h — Brain primitive (Legacy: primitive/Brain.java).
// Delegates to Bit16 width pool (2B stride).

extern BitPool g_brainPool;

bool Brain_init(void);
void Brain_shutdown(void);
void *Brain_alloc(void);
void *Brain_allocArray(size_t count);
void Brain_free(void *ptr);
uint16_t Brain_get(void *ptr);
void Brain_set(void *ptr, uint16_t value);
bool Brain_compareAndSet(void *ptr, uint16_t expected, uint16_t value);
uint32_t Brain_type(void *ptr);
size_t Brain_length(void *ptr);

// bfloat16 — Brain IS bf16, helpers convert float <=> bf16 bits
uint16_t Brain_floatToBFloat16(float value);
float Brain_bFloat16ToFloat(uint16_t bits);
float Brain_getFloat(void *ptr);
void Brain_setFloat(void *ptr, float value);


// Ergonomic constructors — Brain() and Brain(value)
void *Brain_allocWithValue(uint16_t value);
#define Brain_0(...) Brain_alloc()
#define Brain_1(value) Brain_allocWithValue(value)
#define Brain(...) CONSTRUCTOR_DISPATCH(Brain, ##__VA_ARGS__)


#define Brain_array(count) Brain_allocArray(count)

#endif
