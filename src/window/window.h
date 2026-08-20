#ifndef WINDOW_WINDOW_H
#define WINDOW_WINDOW_H

#include <stdbool.h>
#include <stdint.h>

// window/window.h — platform-agnostic window API.
//
// The implementation is anti_window_cocoa.m (the one ObjC file). C callers
// never see AppKit: they get an opaque handle, create/destroy it, and poll
// the OS event queue once per frame. This is the seam where a Win32 or X11
// backend could later drop in with zero changes above this header.

// Opaque handle; contents live in the backend file.
typedef struct anti_window anti_window_t;

// Create and show a window. Returns NULL on failure.
anti_window_t *anti_window_create(const char *title, int width, int height);

// Close the window and free the handle. Safe if already closed.
void anti_window_destroy(anti_window_t *window);

// True once the user has asked to close (red button / Cmd+W).
bool anti_window_should_close(anti_window_t *window);

// Drain the OS event queue. Call once per frame from the engine loop.
void anti_window_poll_events(void);

// Toggle vsync. No-op until the swapchain lands.
void anti_window_set_vsync(anti_window_t *window, bool enabled);

#endif