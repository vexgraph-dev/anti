#ifndef PRIMITIVE_INT_H
#define PRIMITIVE_INT_H

#include <stdbool.h>
#include <stddef.h>
#include <stdint.h>

#include "bit/bit.h"

#include "c23/constructor.h"

// primitive/int.h — Int primitive (Legacy: primitive/Int.java).
// Delegates to Bit32 width pool (4B stride).

extern BitPool g_intPool;

bool Int_init(void);
void Int_shutdown(void);
void *Int_alloc(void);
void *Int_allocArray(size_t count);
void Int_free(void *ptr);
int32_t Int_get(void *ptr);
void Int_set(void *ptr, int32_t value);
bool Int_compareAndSet(void *ptr, int32_t expected, int32_t value);
uint32_t Int_type(void *ptr);
size_t Int_length(void *ptr);


// Ergonomic constructors — Int() and Int(value)
void *Int_allocWithValue(int32_t value);
#define Int_0(...) Int_alloc()
#define Int_1(value) Int_allocWithValue(value)
#define Int(...) CONSTRUCTOR_DISPATCH(Int, ##__VA_ARGS__)


#define Int_array(count) Int_allocArray(count)

#endif
