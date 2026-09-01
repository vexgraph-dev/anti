#ifndef PRIMITIVE_LONG_DOUBLE_H
#define PRIMITIVE_LONG_DOUBLE_H

#include <stdbool.h>
#include <stddef.h>
#include <stdint.h>

#include "bit/bit.h"

// primitive/long_double.h — LongDouble primitive (Legacy: primitive/LongDouble.java).
// Delegates to Bit128 width pool (16B stride).

extern BitPool g_long_doublePool;

bool LongDouble_init(void);
void LongDouble_shutdown(void);
void *LongDouble_alloc(void);
void *LongDouble_allocArray(size_t count);
void LongDouble_free(void *ptr);
int64_t LongDouble_get(void *ptr);
void LongDouble_set(void *ptr, int64_t value);
bool LongDouble_compareAndSet(void *ptr, int64_t expected, int64_t value);
uint32_t LongDouble_type(void *ptr);
size_t LongDouble_length(void *ptr);

#endif
