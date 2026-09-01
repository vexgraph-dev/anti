#ifndef PRIMITIVE_INT_DOUBLE_H
#define PRIMITIVE_INT_DOUBLE_H

#include <stdbool.h>
#include <stddef.h>
#include <stdint.h>

#include "bit/bit.h"

// primitive/int_double.h — IntDouble primitive (Legacy: primitive/IntDouble.java).
// Delegates to Bit128 width pool (16B stride).

extern BitPool g_int_doublePool;

bool IntDouble_init(void);
void IntDouble_shutdown(void);
void *IntDouble_alloc(void);
void *IntDouble_allocArray(size_t count);
void IntDouble_free(void *ptr);
int64_t IntDouble_get(void *ptr);
void IntDouble_set(void *ptr, int64_t value);
bool IntDouble_compareAndSet(void *ptr, int64_t expected, int64_t value);
uint32_t IntDouble_type(void *ptr);
size_t IntDouble_length(void *ptr);

#endif
