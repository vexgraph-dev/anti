#ifndef PRIMITIVE_LONG_FLOAT_H
#define PRIMITIVE_LONG_FLOAT_H

#include <stdbool.h>
#include <stddef.h>
#include <stdint.h>

#include "bit/bit.h"

#include "c23/constructor.h"

// primitive/long_float.h — LongFloat primitive (Legacy: primitive/LongFloat.java).
// Delegates to Bit128 width pool (16B stride).

extern BitPool g_long_floatPool;

bool LongFloat_init(void);
void LongFloat_shutdown(void);
void *LongFloat_alloc(void);
void *LongFloat_allocArray(size_t count);
void LongFloat_free(void *ptr);
int64_t LongFloat_get(void *ptr);
void LongFloat_set(void *ptr, int64_t value);
bool LongFloat_compareAndSet(void *ptr, int64_t expected, int64_t value);
uint32_t LongFloat_type(void *ptr);
size_t LongFloat_length(void *ptr);


// Ergonomic constructors — LongFloat() and LongFloat(v1, v2)
void *LongFloat_allocWithValues(int64_t v1, float v2);
#define LongFloat_0(...) LongFloat_alloc()
#define LongFloat_2(v1, v2) LongFloat_allocWithValues(v1, v2)
#define LongFloat(...) CONSTRUCTOR_DISPATCH(LongFloat, ##__VA_ARGS__)


#define LongFloat_array(count) LongFloat_allocArray(count)

#endif
