#ifndef PRIMITIVE_FLOAT_H
#define PRIMITIVE_FLOAT_H

#include <stdbool.h>
#include <stddef.h>
#include <stdint.h>

#include "bit/bit.h"

// primitive/float.h — Float primitive (Legacy: primitive/Float.java).
// Delegates to Bit32 width pool (4B stride).

extern BitPool g_floatPool;

bool Float_init(void);
void Float_shutdown(void);
void *Float_alloc(void);
void *Float_allocArray(size_t count);
void Float_free(void *ptr);
float Float_get(void *ptr);
void Float_set(void *ptr, float value);
bool Float_compareAndSet(void *ptr, float expected, float value);
uint32_t Float_type(void *ptr);
size_t Float_length(void *ptr);

#endif
