#ifndef OBJECTS_CHOICE_H
#define OBJECTS_CHOICE_H

#include <stddef.h>
#include <stdint.h>

// objects/choice.h — Immutable deterministic choice / branch dispatcher.
// Ported from legacy objects/Choice.java.

typedef void (*ChoiceCallback)(uint64_t objectPtr, void *userdata);

typedef struct Choice Choice;

// Allocate an immutable Choice array with option object pointers and callbacks
Choice *Choice_allocate(const uint64_t *objectPtrs, const ChoiceCallback *callbacks, size_t count);

// Free choice memory
void Choice_free(Choice *choice);

// Inspect choices
size_t   Choice_length(const Choice *choice);
uint64_t Choice_getObject(const Choice *choice, size_t index);

// Trigger the callback associated with choice option 'index'
void Choice_trigger(const Choice *choice, size_t index, void *userdata);

#endif
