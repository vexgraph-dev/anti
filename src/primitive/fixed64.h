#ifndef PRIMITIVE_FIXED64_H
#define PRIMITIVE_FIXED64_H

#include <stdbool.h>
#include <stddef.h>
#include <stdint.h>

#include "bit/bit.h"

#include "c23/constructor.h"

// primitive/fixed64.h — Fixed64 primitive (Legacy: primitive/Fixed64.java).
// Delegates to Bit64 width pool (8B stride).

extern BitPool g_fixed64Pool;

bool Fixed64_init(void);
void Fixed64_shutdown(void);
void *Fixed64_alloc(void);
void *Fixed64_allocArray(size_t count);
void Fixed64_free(void *ptr);
int64_t Fixed64_get(void *ptr);
void Fixed64_set(void *ptr, int64_t value);
bool Fixed64_compareAndSet(void *ptr, int64_t expected, int64_t value);
uint32_t Fixed64_type(void *ptr);
size_t Fixed64_length(void *ptr);


// Ergonomic constructors — Fixed64() and Fixed64(value)
void *Fixed64_allocWithValue(int64_t value);
#define Fixed64_0(...) Fixed64_alloc()
#define Fixed64_1(value) Fixed64_allocWithValue(value)
#define Fixed64(...) CONSTRUCTOR_DISPATCH(Fixed64, __VA_ARGS__)


#define Fixed64_array(count) Fixed64_allocArray(count)

#endif
