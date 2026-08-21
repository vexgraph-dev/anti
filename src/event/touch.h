#ifndef EVENT_TOUCH_H
#define EVENT_TOUCH_H

#include <stdint.h>

// event/touch.h — high-level listener contract for trackpad touch events
// (Legacy: event/TouchEvent.java). Interface vtable + `self` (see event/key.h).

typedef struct TouchEvent {
    void *self; // the implementing object, handed back to every callback
    void (*onTouchDown)(void *self, int touchId, double x, double y, double pressure, uint64_t exactNanos);
    void (*onTouchUp)(void *self, int touchId, double x, double y, double pressure, uint64_t exactNanos);
    void (*onTouchMove)(void *self, int touchId, double x, double y, double pressure, uint64_t exactNanos);
    void (*onTouchCancel)(void *self, int touchId, uint64_t exactNanos); // default no-op in legacy
} TouchEvent;

#endif
