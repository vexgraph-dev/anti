#ifndef PRIMITIVE_SHORT_H
#define PRIMITIVE_SHORT_H

#include <stdbool.h>
#include <stddef.h>
#include <stdint.h>

#include "bit/bit.h"

// primitive/short.h — Short primitive (Legacy: primitive/Short.java).
// Delegates to Bit16 width pool (2B stride).

extern BitPool g_shortPool;

bool Short_init(void);
void Short_shutdown(void);
void *Short_alloc(void);
void *Short_allocArray(size_t count);
void Short_free(void *ptr);
int16_t Short_get(void *ptr);
void Short_set(void *ptr, int16_t value);
bool Short_compareAndSet(void *ptr, int16_t expected, int16_t value);
uint32_t Short_type(void *ptr);
size_t Short_length(void *ptr);

#endif
