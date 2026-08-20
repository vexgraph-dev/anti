#ifndef WINDOW_WINDOW_H
#define WINDOW_WINDOW_H

#include <stdbool.h>
#include <stdint.h>

// window/window.h — platform-agnostic window API.
//
// The implementation is window_cocoa.m (the one ObjC file). C callers
// never see AppKit: they get an opaque handle, create/destroy it, and poll
// the OS event queue once per frame. This is the seam where a Win32 or X11
// backend could later drop in with zero changes above this header.

// Opaque handle; contents live in the backend file.
typedef struct Window Window;

// Create and show a window. Returns NULL on failure.
Window *Window_create(const char *title, int width, int height);

// Close the window and free the handle. Safe if already closed.
void Window_destroy(Window *window);

// True once the user has asked to close (red button / Cmd+W).
bool Window_shouldClose(Window *window);

// Drain the OS event queue. Call once per frame from the engine loop.
void Window_pollEvents(void);

// Toggle vsync. No-op until the swapchain lands.
void Window_setVsync(Window *window, bool enabled);

#endif