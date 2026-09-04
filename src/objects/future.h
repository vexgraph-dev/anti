#ifndef OBJECTS_FUTURE_H
#define OBJECTS_FUTURE_H

#include <stdbool.h>
#include <stdint.h>
#include <stddef.h>
#include "c23/constructor.h"

// objects/future.h — Asynchronous single-assignment Future object wrapper.
// Ported from legacy objects/Future.java.

typedef struct Future Future;

// Allocate a new unresolved Future instance
Future *Future_0(void);

// Free future memory

Future *Future_2(const Future *init, size_t count);

#define Future(...) CONSTRUCTOR_DISPATCH(Future, __VA_ARGS__)

void Future_free(Future *future);

// Query whether the future value has been fulfilled
bool Future_isGiven(const Future *future);

// Get the resolved value (returns 0 if not yet given)
uint64_t Future_get(const Future *future);

// Atomically fulfill the future with a value. Returns true on success, false if already fulfilled.
bool Future_setDesiredValue(Future *future, uint64_t value);

#endif
