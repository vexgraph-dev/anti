#ifndef EVENT_MOUSE_H
#define EVENT_MOUSE_H

#include <stdint.h>

// event/mouse.h — high-level listener contract for mouse events
// (Legacy: event/MouseEvent.java).
//
// Interface vtable + `self` (see event/key.h). mouseEvent packs the button +
// modifier flags (see input/mouse.h).

typedef struct MouseEvent {
    void *self; // the implementing object, handed back to every callback
    void (*onMouseDown)(void *self, int mouseEvent, uint64_t exactNanos);
    void (*onMouseUp)(void *self, int mouseEvent, uint64_t exactNanos);
    void (*onMouseRepeat)(void *self, int mouseEvent, uint64_t exactNanos);
    void (*onMouseMove)(void *self, double x, double y);
    // Relative movement while the cursor is locked. (dx, dy) is how much the
    // mouse travelled this step; the absolute position stays at the anchor.
    void (*onMouseMoveDelta)(void *self, double dx, double dy); // default no-op in legacy
    void (*onMouseDrag)(void *self, int mouseEvent, double x, double y); // default no-op
    void (*onMouseScroll)(void *self, double dx, double dy);             // default no-op
    // Trackpad pinch-to-zoom (macOS NSEventTypeMagnify). Positive = zoom in
    // (fingers spread), negative = zoom _out (fingers pinched).
    void (*onMouseZoom)(void *self, double magnification);               // default no-op
} MouseEvent;

#endif
