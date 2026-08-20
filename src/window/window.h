#ifndef WINDOW_WINDOW_H
#define WINDOW_WINDOW_H

#include <stdbool.h>
#include <stddef.h>
#include <stdint.h>

// window/window.h — platform-agnostic window API.
//
// The implementation is window_cocoa.m (the one ObjC file). C callers
// never see AppKit: they get an opaque handle, create/destroy it, and poll
// the OS event queue once per frame. This is the seam where a Win32 or X11
// backend could later drop in with zero changes above this header.
//
// The method surface mirrors the legacy macOSWindow (the FFM backend that
// lived in legacy-java): title/size/position, chrome capability toggles
// (resizable/closable/miniaturizable/traffic lights), fullscreen, minimize,
// undecorated (naked) chrome, DRM (sharing) mode, and size constraints.

// Opaque handle; contents live in the backend file.
typedef struct Window Window;

// Undecorated chrome modes for Window_setUndecorated.
#define WINDOW_UNDECORATED_DECORATED  0 // standard opaque title bar, title visible
#define WINDOW_UNDECORATED_BORDERLESS 1 // no title bar and no traffic lights
#define WINDOW_UNDECORATED_NAKED      2 // transparent title bar, hidden title, traffic lights kept

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

// --- Title / size / position ---
void Window_setTitle(Window *window, const char *title);
void Window_setSize(Window *window, int width, int height);
void Window_setWidth(Window *window, int width);
void Window_setHeight(Window *window, int height);
void Window_setLocation(Window *window, int x, int y);
void Window_center(Window *window);
void Window_setVisible(Window *window, bool visible);

// --- Chrome capability toggles (style-mask API) ---
bool Window_isResizable(Window *window);
void Window_setResizable(Window *window, bool resizable);
bool Window_isClosable(Window *window);
void Window_setClosable(Window *window, bool closable);
bool Window_isMiniaturizable(Window *window);
void Window_setMiniaturizable(Window *window, bool miniaturizable);

// Green traffic light (fullscreen entry). Gated by NSWindowCollectionBehavior
// FullScreenPrimary, set at creation. Call before the window shows to remove it.
void Window_setFullscreenButton(Window *window, bool enabled);

// Switch window chrome at runtime: one of WINDOW_UNDECORATED_*.
void Window_setUndecorated(Window *window, int mode);

// --- Minimize ---
void Window_minimize(Window *window);
void Window_restore(Window *window);
bool Window_isMinimized(Window *window);

// --- Fullscreen ---
bool Window_isFullscreen(Window *window);
void Window_setFullscreen(Window *window, bool fullscreen);
void Window_toggleFullscreen(Window *window);

// --- DRM / sharing ---
void Window_setDRM(Window *window, bool enabled);

// --- Size constraints ---
void Window_setMinSize(Window *window, int width, int height);
void Window_setMaxSize(Window *window, int width, int height);

#endif