#ifndef OBJECTS_GLOBAL_H
#define OBJECTS_GLOBAL_H

#include <stdbool.h>
#include <stdint.h>
#include <stddef.h>
#include "c23/constructor.h"

// objects/global.h — Atomic global pointer/variable object wrapper.
// Ported from legacy objects/Global.java.

typedef struct Global Global;

// Allocate a new atomic Global pointer/value
Global *Global_1(uint64_t initialValue);

// Free global memory

Global *Global_2(const Global *init, size_t count);

#define Global(...) CONSTRUCTOR_DISPATCH(Global, __VA_ARGS__)

void Global_free(Global *global);

// Atomic read/write
uint64_t Global_get(const Global *global);
void     Global_set(Global *global, uint64_t value);
bool     Global_compareAndSet(Global *global, uint64_t expected, uint64_t value);

#endif
