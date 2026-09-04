#ifndef OBJECTS_CHOICE_H
#define OBJECTS_CHOICE_H

#include <stddef.h>
#include <stdint.h>
#include <stddef.h>
#include "c23/constructor.h"

// objects/choice.h — Immutable deterministic choice / branch dispatcher.
// Ported from legacy objects/Choice.java.

typedef void (*ChoiceCallback)(uint64_t objectPtr, void *userdata);

typedef struct Choice Choice;

// Allocate an immutable Choice array with option object pointers and callbacks
Choice *Choice_3(const uint64_t *objectPtrs, const ChoiceCallback *callbacks, size_t count);

// Free choice memory

Choice *Choice_2(const Choice *init, size_t count);

#define Choice(...) CONSTRUCTOR_DISPATCH(Choice, __VA_ARGS__)

void Choice_free(Choice *choice);

// Inspect choices
size_t   Choice_length(const Choice *choice);
uint64_t Choice_getObject(const Choice *choice, size_t index);

// Trigger the callback associated with choice option 'index'
void Choice_trigger(const Choice *choice, size_t index, void *userdata);

#endif
