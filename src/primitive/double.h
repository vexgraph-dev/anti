#ifndef PRIMITIVE_DOUBLE_H
#define PRIMITIVE_DOUBLE_H

#include <stdbool.h>
#include <stddef.h>
#include <stdint.h>

#include "bit/bit.h"

#include "c23/constructor.h"

// primitive/double.h — Double primitive (Legacy: primitive/Double.java).
// Delegates to Bit64 width pool (8B stride).

extern BitPool g_doublePool;

bool Double_init(void);
void Double_shutdown(void);
void *Double_alloc(void);
void *Double_allocArray(size_t count);
void Double_free(void *ptr);
double Double_get(void *ptr);
void Double_set(void *ptr, double value);
bool Double_compareAndSet(void *ptr, double expected, double value);
uint32_t Double_type(void *ptr);
size_t Double_length(void *ptr);


// Ergonomic constructors — Double() and Double(value)
void *Double_allocWithValue(double value);
#define Double_0(...) Double_alloc()
#define Double_1(value) Double_allocWithValue(value)
#define Double(...) CONSTRUCTOR_DISPATCH(Double, __VA_ARGS__)


#define Double_array(count) Double_allocArray(count)

#endif
