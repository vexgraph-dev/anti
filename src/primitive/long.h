#ifndef PRIMITIVE_LONG_H
#define PRIMITIVE_LONG_H

#include <stdbool.h>
#include <stddef.h>
#include <stdint.h>

#include "bit/bit.h"

// primitive/long.h — Long primitive (Legacy: primitive/Long.java).
// Delegates to Bit64 width pool (8B stride).

extern BitPool g_longPool;

bool Long_init(void);
void Long_shutdown(void);
void *Long_alloc(void);
void *Long_allocArray(size_t count);
void Long_free(void *ptr);
int64_t Long_get(void *ptr);
void Long_set(void *ptr, int64_t value);
bool Long_compareAndSet(void *ptr, int64_t expected, int64_t value);
uint32_t Long_type(void *ptr);
size_t Long_length(void *ptr);

#endif
