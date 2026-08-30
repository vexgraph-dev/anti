#ifndef EVENT_KEY_H
#define EVENT_KEY_H

#include <stdint.h>

// event/key.h — high-level listener contract for hardware key events
// (Legacy: event/KeyEvent.java).
//
// A KeyEvent is C's version of a Java interface: a vtable of callbacks plus
// a leading `self` pointer. Any struct "implements" it by exposing one of
// these with .self pointing at itself; every callback receives that pointer
// back as its first argument (the `this` Java implied). Callbacks mirror the
// Java methods; Java's `default void` no-ops become nullptr pointers — every
// dispatcher checks before invoking, so a listener only needs the callbacks
// it cares about.
//
// Executed safely on the game thread via the lock-free input queue,
// guaranteeing zero Thread-0 blocking from event handling.
//
// keyEvent packs the code + modifier flags (see input/key.h); exactNanos is
// the reconstructed engine-epoch timestamp of the OS delivery.

typedef struct KeyEvent {
    void *self; // the implementing object, handed back to every callback
    void (*onKeyDown)(void *self, int keyEvent, uint64_t exactNanos);
    void (*onKeyUp)(void *self, int keyEvent, uint64_t exactNanos);
    void (*onKeyRepeat)(void *self, int keyEvent, uint64_t exactNanos);
    void (*onCharTyped)(void *self, uint32_t character); // default no-op in legacy
} KeyEvent;

#endif
