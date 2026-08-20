// window.c — Win32 backend for the Window API.
//
// @PlatformExclusive("Windows")
// @Draft  — first fresh write as a placeholder, not ready for production.
// @Intention("Fills the Window API seam (window.h) on Windows so the engine
//            can be built there. Mirrors the legacy windowsWindow.java.")
//
// Annotation convention (mirrors legacy-java/src/annotation):
//   @PlatformExclusive("X")  backend is exclusive to that platform
//   @Draft                   file is a fresh placeholder write, not production-ready
//   @Intention("...")        the reason this file exists / how it plugs in
//   @Incomplete              a function that is a stub; remove once implemented
//
// Every function below is an @Incomplete stub returning a safe default. The
// real implementations will use User32: CreateWindowEx / DefWindowProc,
// ShowWindow / SetWindowTextA / SetWindowPos / SetWindowDisplayAffinity,
// and a GetMessage/PeekMessage-TranslateMessage-DispatchMessage event loop.

#include "window/window.h"

#if defined(_WIN32)

// @Incomplete CreateWindowEx; returns NULL until implemented.
Window *Window_create(const char *title, int width, int height) {
    (void) title;
    (void) width;
    (void) height;
    return NULL;
}

// @Incomplete DestroyWindow; no-op until implemented.
void Window_destroy(Window *window) {
    (void) window;
}

// @Incomplete Poll WM_QUIT; false until implemented.
bool Window_shouldClose(Window *window) {
    (void) window;
    return true;
}

// @Incomplete PeekMessage/TranslateMessage/DispatchMessage loop; no-op until implemented.
void Window_pollEvents(void) {
}

// @Incomplete SwapBuffers vsync; no-op until implemented.
void Window_setVsync(Window *window, bool enabled) {
    (void) window;
    (void) enabled;
}

// @Incomplete SetWindowTextA; no-op until implemented.
void Window_setTitle(Window *window, const char *title) {
    (void) window;
    (void) title;
}

// @Incomplete SetWindowPos; no-op until implemented.
void Window_setSize(Window *window, int width, int height) {
    (void) window;
    (void) width;
    (void) height;
}

// @Incomplete SetWindowPos; no-op until implemented.
void Window_setWidth(Window *window, int width) {
    (void) window;
    (void) width;
}

// @Incomplete SetWindowPos; no-op until implemented.
void Window_setHeight(Window *window, int height) {
    (void) window;
    (void) height;
}

// @Incomplete SetWindowPos; no-op until implemented.
void Window_setLocation(Window *window, int x, int y) {
    (void) window;
    (void) x;
    (void) y;
}

// @Incomplete CenterWindow; no-op until implemented.
void Window_center(Window *window) {
    (void) window;
}

// @Incomplete ShowWindow(SW_SHOW)/ShowWindow(SW_HIDE); no-op until implemented.
void Window_setVisible(Window *window, bool visible) {
    (void) window;
    (void) visible;
}

// @Incomplete GWL_STYLE WS_THICKFRAME; false until implemented.
bool Window_isResizable(Window *window) {
    (void) window;
    return false;
}

// @Incomplete SetWindowLong(GWL_STYLE, WS_THICKFRAME); no-op until implemented.
void Window_setResizable(Window *window, bool resizable) {
    (void) window;
    (void) resizable;
}

// @Incomplete GWL_STYLE WS_SYSMENU; false until implemented.
bool Window_isClosable(Window *window) {
    (void) window;
    return false;
}

// @Incomplete SetWindowLong(GWL_STYLE, WS_SYSMENU); no-op until implemented.
void Window_setClosable(Window *window, bool closable) {
    (void) window;
    (void) closable;
}

// @Incomplete GWL_STYLE WS_MINIMIZEBOX; false until implemented.
bool Window_isMiniaturizable(Window *window) {
    (void) window;
    return false;
}

// @Incomplete SetWindowLong(GWL_STYLE, WS_MINIMIZEBOX); no-op until implemented.
void Window_setMiniaturizable(Window *window, bool miniaturizable) {
    (void) window;
    (void) miniaturizable;
}

// @Incomplete WS_MAXIMIZEBOX semantics; no-op until implemented.
void Window_setFullscreenButton(Window *window, bool enabled) {
    (void) window;
    (void) enabled;
}

// @Incomplete Fullscreen/borderless chrome switch; no-op until implemented.
void Window_setUndecorated(Window *window, int mode) {
    (void) window;
    (void) mode;
}

// @Incomplete ShowWindow(SW_MINIMIZE); no-op until implemented.
void Window_minimize(Window *window) {
    (void) window;
}

// @Incomplete ShowWindow(SW_RESTORE); no-op until implemented.
void Window_restore(Window *window) {
    (void) window;
}

// @Incomplete IsIconic; false until implemented.
bool Window_isMinimized(Window *window) {
    (void) window;
    return false;
}

// @Incomplete GWL_STYLE WS_MAXIMIZE; false until implemented.
bool Window_isFullscreen(Window *window) {
    (void) window;
    return false;
}

// @Incomplete ShowWindow(SW_MAXIMIZE) vs SW_RESTORE; no-op until implemented.
void Window_setFullscreen(Window *window, bool fullscreen) {
    (void) window;
    (void) fullscreen;
}

// @Incomplete Window setFullscreen wrapper; no-op until implemented.
void Window_toggleFullscreen(Window *window) {
    (void) window;
}

// @Incomplete SetWindowDisplayAffinity(WDA_MONITOR/WDA_NONE); no-op until implemented.
void Window_setDRM(Window *window, bool enabled) {
    (void) window;
    (void) enabled;
}

// @Incomplete AdjustWindowRectEx + GetWindowRect; no-op until implemented.
void Window_setMinSize(Window *window, int width, int height) {
    (void) window;
    (void) width;
    (void) height;
}

// @Incomplete AdjustWindowRectEx + GetWindowRect; no-op until implemented.
void Window_setMaxSize(Window *window, int width, int height) {
    (void) window;
    (void) width;
    (void) height;
}

#endif