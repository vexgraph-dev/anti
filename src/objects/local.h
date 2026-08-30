#ifndef OBJECTS_LOCAL_H
#define OBJECTS_LOCAL_H

#include <stdint.h>
#include <stddef.h>
#include "c23/constructor.h"

// objects/local.h — Thread-local variable slot array object wrapper.
// Ported from legacy objects/Local.java.

#define LOCAL_THREAD_MAX 256

typedef struct Local Local;

// Allocate a new thread-local slot array
Local *Local_0(void);

// Free local memory

Local *Local_2(const Local *init, size_t count);

#define Local(...) CONSTRUCTOR_DISPATCH(Local, ##__VA_ARGS__)

void Local_free(Local *local);

// Slot accessors per thread id
uint64_t Local_get(const Local *local, uint32_t threadId);
void     Local_set(Local *local, uint32_t threadId, uint64_t value);

#endif
