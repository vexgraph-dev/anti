#ifndef PRIMITIVE_INT_FLOAT_H
#define PRIMITIVE_INT_FLOAT_H

#include <stdbool.h>
#include <stddef.h>
#include <stdint.h>

#include "bit/bit.h"

// primitive/int_float.h — IntFloat primitive (Legacy: primitive/IntFloat.java).
// Delegates to Bit64 width pool (8B stride).

extern BitPool g_int_floatPool;

bool IntFloat_init(void);
void IntFloat_shutdown(void);
void *IntFloat_alloc(void);
void *IntFloat_allocArray(size_t count);
void IntFloat_free(void *ptr);
int64_t IntFloat_get(void *ptr);
void IntFloat_set(void *ptr, int64_t value);
bool IntFloat_compareAndSet(void *ptr, int64_t expected, int64_t value);
uint32_t IntFloat_type(void *ptr);
size_t IntFloat_length(void *ptr);

#endif
