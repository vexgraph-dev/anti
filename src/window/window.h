#ifndef WINDOW_WINDOW_H
#define WINDOW_WINDOW_H

#include <stdbool.h>
#include <stddef.h>
#include <stdint.h>

#include "event/key.h"
#include "event/mouse.h"
#include "event/touch.h"

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

// Constructor parameters. Every field has a default; a call site names only
// what it wants to change. Zero it for pure defaults.
typedef struct WindowDesc {
    const char *title;   // default "anti"
    int width;           // default 800
    int height;          // default 600
    int x;               // top-left, default 0
    int y;               // default 0
    bool centered;       // default false
    bool shown;          // default false — construct hidden, show() when ready
} WindowDesc;

// --- Overloaded constructors (the Vec4 chooser idiom) ---
//
//   Window()                     -> defaults, hidden
//   Window("title")              -> titled, hidden
//   Window("title", 800, 600)    -> legacy create, hidden
//   Window_new(&(WindowDesc){…}) -> every other field (x/y/centered/shown)
//
// All variants construct HIDDEN: construct -> mutate -> Window_show().
// The macro is function-like, so it never fires when `Window` is used as the
// type name — only at call sites with parentheses.

Window *Window_0(void);
Window *Window_1(const char *title);
Window *Window_3(const char *title, int width, int height);

#define WINDOW_CHOOSER(_0, _1, _2, _3, NAME, ...) NAME

#define Window(...) WINDOW_CHOOSER( \
    dummy, ##__VA_ARGS__,           \
    Window_3, Window_2, Window_1, Window_0 \
)(__VA_ARGS__)

// Parameterized constructor: Desc fields applied on top of defaults.
// Pass &(WindowDesc){ .title = "...", .centered = true } — unset fields keep
// their defaults. Returns NULL on failure.
Window *Window_new(const WindowDesc *desc);

// Legacy-style convenience constructor: titled, sized, created hidden.
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
int  Window_width(Window *window);
int  Window_height(Window *window);
void Window_setDimension(Window *window, int width, int height);
void Window_setWidth(Window *window, int width);
void Window_setHeight(Window *window, int height);
void Window_setLocation(Window *window, int x, int y);
void Window_center(Window *window);
void Window_show(Window *window);
void Window_hide(Window *window);
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

// FPS-style relative cursor: hides the pointer, decouples it from movement,
// and re-warps to the window centre each pump pass while deltas flow into the
// input/mouse stream as move-delta events. (Legacy: macOSWindow.setCursorLock.)
void Window_setCursorLocked(Window *window, bool locked);

// --- Event wiring ---
//
// The window is the registration surface for the event contracts: implement
// a KeyEvent/MouseEvent/TouchEvent vtable (with .self = your object) and
// attach it here. Every queued event carries the id of the window the OS
// delivered it to, so an attached listener only hears events for ITS window
// (broadcast-tagged synthetic events reach every window). Removal is by
// pointer identity. Destroying the window detaches its listeners.
void Window_addKeyEvent(Window *window, const KeyEvent *listener);
bool Window_removeKeyEvent(Window *window, const KeyEvent *listener);
void Window_addMouseEvent(Window *window, const MouseEvent *listener);
bool Window_removeMouseEvent(Window *window, const MouseEvent *listener);
void Window_addTouchEvent(Window *window, const TouchEvent *listener);
bool Window_removeTouchEvent(Window *window, const TouchEvent *listener);

// The running: drain all three device rings into the registered listeners.
// Call ONCE per frame from the game loop, after Window_pollEvents(). If you
// never call it, polling (Key_isDown/Mouse_x) still works but queued events
// pile up and drop once the rings fill.
void Window_dispatchEvents(Window *window);

// --- Focus (the spotlight: one focused window per machine) ---
//
// The OS owns focus; we mirror it. After every pump pass the key window's id
// lands in one atomic word any thread can read.
uint32_t Window_id(Window *window);      // 0 when window is NULL
void Window_focus(Window *window);       // ask the OS to make this key
bool Window_isFocused(Window *window);   // is THIS the spotlight right now?

// --- Resize reflection ---
//
// Monotonic counter bumped by Thread 0 whenever Window_pollEvents observes
// the content size actually changed. Renderers, layouts, and scenes compare
// their last-seen generation against this and rebuild when it moved — one
// frame of lag at worst, zero AppKit calls off Thread 0, no locks.
uint64_t Window_sizeGeneration(Window *window);

#endif