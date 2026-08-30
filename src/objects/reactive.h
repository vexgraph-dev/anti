#ifndef OBJECTS_REACTIVE_H
#define OBJECTS_REACTIVE_H

#include <stdint.h>
#include <stddef.h>
#include "c23/constructor.h"

// objects/reactive.h — Event-driven Reactive object wrapper.
// Ported from legacy objects/Reactive.java.

typedef void (*ReactiveSetCallback)(uint64_t newValue, void *userdata);
typedef void (*ReactiveGetCallback)(uint64_t currentValue, void *userdata);
typedef void (*ReactiveChangedCallback)(uint64_t oldValue, uint64_t newValue, void *userdata);

typedef struct Reactive Reactive;

// Allocate a new Reactive instance with initial value
Reactive *Reactive_1(uint64_t initialValue);

// Free reactive memory

Reactive *Reactive_2(const Reactive *init, size_t count);

#define Reactive(...) CONSTRUCTOR_DISPATCH(Reactive, ##__VA_ARGS__)

void Reactive_free(Reactive *reactive);

// Accessors with observer event triggers
uint64_t Reactive_get(Reactive *reactive);
void     Reactive_set(Reactive *reactive, uint64_t value);

// Attach observer callbacks
void Reactive_setOnSet(Reactive *reactive, ReactiveSetCallback cb, void *userdata);
void Reactive_setOnGet(Reactive *reactive, ReactiveGetCallback cb, void *userdata);
void Reactive_setOnChanged(Reactive *reactive, ReactiveChangedCallback cb, void *userdata);

#endif
