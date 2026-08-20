// window_linux.c — Linux/X11 backend for the Window API.
//
// @PlatformExclusive("Linux")
// @Draft  — first fresh write as a placeholder, not ready for production.
// @Intention("Fills the Window API seam (window.h) on Linux so the engine can
//            be built there. Mirrors the legacy linuxWindow.java.")
//
// Annotation convention (mirrors legacy-java/src/annotation):
//   @PlatformExclusive("X")  backend is exclusive to that platform
//   @Draft                   file is a fresh placeholder write, not production-ready
//   @Intention("...")        the reason this file exists / how it plugs in
//   @Incomplete              a function that is a stub; remove once implemented
//
// Every function below is an @Incomplete stub returning a safe default. The
// real implementations will use Xlib/Wayland: XCreateSimpleWindow /
// XMapWindow / XUnmapWindow / XStoreName / XResizeWindow / XMoveWindow /
// XDestroyWindow, and an XPending/XNextEvent event loop.

#include "window/window.h"

#if defined(__linux__) || defined(__unix__)

// @Incomplete XCreateSimpleWindow; returns NULL until implemented.
Window *Window_create(const char *title, int width, int height) {
    (void) title;
    (void) width;
    (void) height;
    return NULL;
}

// @Incomplete XDestroyWindow; no-op until implemented.
void Window_destroy(Window *window) {
    (void) window;
}

// @Incomplete Check DestroyNotify; true until implemented.
bool Window_shouldClose(Window *window) {
    (void) window;
    return true;
}

// @Incomplete XPending/XNextEvent loop; no-op until implemented.
void Window_pollEvents(void) {
}

// @Incomplete SwapBuffers vsync; no-op until implemented.
void Window_setVsync(Window *window, bool enabled) {
    (void) window;
    (void) enabled;
}

// @Incomplete XStoreName; no-op until implemented.
void Window_setTitle(Window *window, const char *title) {
    (void) window;
    (void) title;
}

// @Incomplete XResizeWindow; no-op until implemented.
void Window_setSize(Window *window, int width, int height) {
    (void) window;
    (void) width;
    (void) height;
}

// @Incomplete XResizeWindow; no-op until implemented.
void Window_setWidth(Window *window, int width) {
    (void) window;
    (void) width;
}

// @Incomplete XResizeWindow; no-op until implemented.
void Window_setHeight(Window *window, int height) {
    (void) window;
    (void) height;
}

// @Incomplete XMoveWindow; no-op until implemented.
void Window_setLocation(Window *window, int x, int y) {
    (void) window;
    (void) x;
    (void) y;
}

// @Incomplete Center on the screen; no-op until implemented.
void Window_center(Window *window) {
    (void) window;
}

// @Incomplete XMapWindow/XUnmapWindow; no-op until implemented.
void Window_setVisible(Window *window, bool visible) {
    (void) window;
    (void) visible;
}

// @Incomplete Motif hints; false until implemented.
bool Window_isResizable(Window *window) {
    (void) window;
    return false;
}

// @Incomplete Motif hints; no-op until implemented.
void Window_setResizable(Window *window, bool resizable) {
    (void) window;
    (void) resizable;
}

// @Incomplete WM_DELETE_WINDOW; false until implemented.
bool Window_isClosable(Window *window) {
    (void) window;
    return false;
}

// @Incomplete WM_DELETE_WINDOW; no-op until implemented.
void Window_setClosable(Window *window, bool closable) {
    (void) window;
    (void) closable;
}

// @Incomplete Motif hints; false until implemented.
bool Window_isMiniaturizable(Window *window) {
    (void) window;
    return false;
}

// @Incomplete Motif hints; no-op until implemented.
void Window_setMiniaturizable(Window *window, bool miniaturizable) {
    (void) window;
    (void) miniaturizable;
}

// @Incomplete _NET_WM_STATE_FULLSCREEN; no-op until implemented.
void Window_setFullscreenButton(Window *window, bool enabled) {
    (void) window;
    (void) enabled;
}

// @Incomplete Fullscreen/borderless chrome switch; no-op until implemented.
void Window_setUndecorated(Window *window, int mode) {
    (void) window;
    (void) mode;
}

// @Incomplete XIconifyWindow; no-op until implemented.
void Window_minimize(Window *window) {
    (void) window;
}

// @Incomplete WM_CHANGE_STATE normal; no-op until implemented.
void Window_restore(Window *window) {
    (void) window;
}

// @Incomplete Check _NET_WM_STATE_HIDDEN; false until implemented.
bool Window_isMinimized(Window *window) {
    (void) window;
    return false;
}

// @Incomplete Check _NET_WM_STATE_FULLSCREEN; false until implemented.
bool Window_isFullscreen(Window *window) {
    (void) window;
    return false;
}

// @Incomplete _NET_WM_STATE_FULLSCREEN toggle; no-op until implemented.
void Window_setFullscreen(Window *window, bool fullscreen) {
    (void) window;
    (void) fullscreen;
}

// @Incomplete _NET_WM_STATE_FULLSCREEN toggle; no-op until implemented.
void Window_toggleFullscreen(Window *window) {
    (void) window;
}

// @Incomplete WM_CHANGE_STATE / compositor support; no-op until implemented.
void Window_setDRM(Window *window, bool enabled) {
    (void) window;
    (void) enabled;
}

// @Incomplete WM_NORMAL_HINTS min size; no-op until implemented.
void Window_setMinSize(Window *window, int width, int height) {
    (void) window;
    (void) width;
    (void) height;
}

// @Incomplete WM_NORMAL_HINTS max size; no-op until implemented.
void Window_setMaxSize(Window *window, int width, int height) {
    (void) window;
    (void) width;
    (void) height;
}

#endif