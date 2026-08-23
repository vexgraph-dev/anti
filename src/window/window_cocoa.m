// window_cocoa.m — the AppKit shim (the ".m" glue file).
//
// anti's core is pure C11; this is the ONE Objective-C file in the project.
// It exists only to talk to AppKit, because NSWindow/NSApplication are ObjC
// objects and there is no pure-C way to create them. Everything above this
// boundary stays C; everything here is "dip into the OS, hand back a handle".
//
// Design notes:
//   - sAppDelegate is created once per process (the app lifecycle delegate).
//   - Each window gets its own AntiWindowDelegate so we can learn about the
//     user clicking the red close button -> sets shouldClose -> engine loop
//     sees it and exits (see window_demo.c).
//   - setReleasedWhenClosed:NO is CRITICAL. The default (YES for programmatic
//     windows) makes NSWindow free itself the moment it closes; our destroy()
//     would then call close on freed memory = the segfault you saw. We own the
//     window object; only destroy() releases it.

#import <AppKit/AppKit.h>
#import <Foundation/Foundation.h>
#import <QuartzCore/QuartzCore.h>
#import <stdatomic.h>

#include "buffers/color_buffer.h"
#include "window/window.h"

#include "input/focus.h"
#include "input/key.h"
#include "input/mouse.h"
#include "input/touch.h"
#include "time/nanotime.h"

// Multi-tap window for double-click style counting (legacy parity: 250ms).
static const uint64_t kTapThresholdNanos = 250000000ULL;

// Carbon virtual keycode -> KEY_* code. -1 = unmapped. Same table the legacy
// macOSWindow built (physical F1-F12, not Fn-doubled media keys).
static int macKeyMap[128] = {
    [0] = KEY_A,                   [1] = KEY_S,
    [2] = KEY_D,                   [3] = KEY_F,
    [4] = KEY_H,                   [5] = KEY_G,
    [6] = KEY_Z,                   [7] = KEY_X,
    [8] = KEY_C,                   [9] = KEY_V,
    [11] = KEY_B,                  [12] = KEY_Q,
    [13] = KEY_W,                  [14] = KEY_E,
    [15] = KEY_R,                  [16] = KEY_Y,
    [17] = KEY_T,                  [18] = KEY_NUM_1,
    [19] = KEY_NUM_2,              [20] = KEY_NUM_3,
    [21] = KEY_NUM_4,              [22] = KEY_NUM_6,
    [23] = KEY_NUM_5,              [24] = KEY_EQUAL,
    [25] = KEY_NUM_9,              [26] = KEY_NUM_7,
    [27] = KEY_MINUS,              [28] = KEY_NUM_8,
    [29] = KEY_NUM_0,              [30] = KEY_RIGHT_BRACKET,
    [31] = KEY_O,                  [32] = KEY_U,
    [33] = KEY_LEFT_BRACKET,       [34] = KEY_I,
    [35] = KEY_P,                  [36] = KEY_ENTER,
    [37] = KEY_L,                  [38] = KEY_J,
    [39] = KEY_APOSTROPHE,         [40] = KEY_K,
    [41] = KEY_SEMICOLON,          [42] = KEY_BACKSLASH,
    [43] = KEY_COMMA,              [44] = KEY_SLASH,
    [45] = KEY_N,                  [46] = KEY_M,
    [47] = KEY_PERIOD,             [48] = KEY_TAB,
    [49] = KEY_SPACE,              [50] = KEY_GRAVE_ACCENT,
    [51] = KEY_BACKSPACE,          [53] = KEY_ESCAPE,
    [96] = KEY_F5,                 [97] = KEY_F6,
    [98] = KEY_F7,                 [99] = KEY_F3,
    [100] = KEY_F8,                [101] = KEY_F9,
    [103] = KEY_F11,               [109] = KEY_F10,
    [111] = KEY_F12,               [118] = KEY_F4,
    [120] = KEY_F2,                [122] = KEY_F1,
    [123] = KEY_LEFT,              [124] = KEY_RIGHT,
    [125] = KEY_DOWN,              [126] = KEY_UP,
};

// Cursor-lock state (Legacy: macOSWindow.lockWindowPtr/recenterIfLocked).
// While locked the pointer is decoupled from motion and re-warped to the
// window centre every pump pass; NSEvent deltas feed input.Mouse directly.
static bool s_cursorLocked = false;
static CGPoint s_lockCenter = {0, 0};

@class AntiWindowDelegate;

// One opaque handle handed back to C. Holds both NS objects we must keep
// alive: the window itself and its delegate. `id` is the engine's small
// window number (1..7; 0 is the broadcast reserved id) used to tag input
// events and route them to per-window listeners. sizeGeneration is the
// resize-reflection counter: Thread 0 bumps it when the content rect moves.
struct Window {
    NSWindow *nsWindow;
    AntiWindowDelegate *delegate;
    bool shouldClose;
    uint32_t id;
    _Atomic uint64_t sizeGeneration;
    int cachedWidth;
    int cachedHeight;
};

// Window id registry: slot i holds the NSWindow currently owning id i (and
// the C handle that owns that NSWindow). Ids are scarce on purpose (8 total)
// — an engine does not open hundreds of windows. Slot 0 stays empty forever
// (FOCUS_BROADCAST).
#define WINDOW_ID_SLOTS 8
static NSWindow *s_idToWindow[WINDOW_ID_SLOTS] = { nil };
static Window *s_idToHandle[WINDOW_ID_SLOTS] = { NULL };

// Resolve an id for a newly created window, or 0 when the table is full.
static uint32_t windowIdAcquire(NSWindow *window, Window *handle) {
    for (uint32_t i = 1; i < WINDOW_ID_SLOTS; i++) {
        if (s_idToWindow[i] == nil) {
            s_idToWindow[i] = window;
            s_idToHandle[i] = handle;
            return i;
        }
    }
    return FOCUS_BROADCAST;
}

static void windowIdRelease(uint32_t id) {
    if (id != FOCUS_BROADCAST && id < WINDOW_ID_SLOTS) {
        s_idToWindow[id] = nil;
        s_idToHandle[id] = NULL;
    }
}

// Reverse lookup: which engine id does this OS window carry? 0 if unknown.
static uint32_t windowIdOf(NSWindow *window) {
    if (!window) return FOCUS_BROADCAST;
    for (uint32_t i = 1; i < WINDOW_ID_SLOTS; i++)
        if (s_idToWindow[i] == window) return i;
    return FOCUS_BROADCAST;
}

static Window *windowHandleOf(NSWindow *window) {
    if (!window) return NULL;
    for (uint32_t i = 1; i < WINDOW_ID_SLOTS; i++)
        if (s_idToWindow[i] == window) return s_idToHandle[i];
    return NULL;
}

// App-level delegate: receives lifecycle events for the whole application.
// applicationShouldTerminateAfterLastWindowClosed lets the process end when
// the last window goes away (normal for a game/engine run).
@interface AntiAppDelegate : NSObject <NSApplicationDelegate>
@property(nonatomic, assign) bool *shouldClosePtr;
@end

@implementation AntiAppDelegate
- (void)applicationWillTerminate:(NSNotification *)notification {
    (void) notification;
    if (self.shouldClosePtr) *self.shouldClosePtr = true;
}
- (BOOL)applicationShouldTerminateAfterLastWindowClosed:(NSApplication *)sender {
    (void) sender;
    return YES;
}
@end

// Window-level delegate: this is how we learn the user clicked the close
// button. windowWillClose fires as the window is being torn down; we flip the
// bool the engine loop polls. The pointer is (assign) because the delegate
// must not own our C struct.
@interface AntiWindowDelegate : NSObject <NSWindowDelegate>
@property(nonatomic, assign) bool *shouldClosePtr;
@end

@implementation AntiWindowDelegate
- (void) windowWillClose:(NSNotification *)notification {
    (void) notification;
    if (self.shouldClosePtr) *self.shouldClosePtr = true;
}
@end

static AntiAppDelegate *sAppDelegate = nil; // one app delegate for the whole process
static NSWindow *sLastWindow = nil;

// Re-centre the cursor during the pump while locked, so the warp registers
// before the next event loop exit (legacy recenterIfLocked).
static void recenterIfLocked(void) {
    if (!s_cursorLocked) return;
    CGWarpMouseCursorPosition(s_lockCenter);
}

// Content-area coordinates (top-left origin) for a mouse event. Events that
// miss every window fall back to raw screen-space values.
static void mouseLocation(NSEvent *event, double *outX, double *outY) {
    NSPoint p = [event locationInWindow];
    double x = p.x;
    double y = p.y;
    NSWindow *eventWindow = [event window];
    if (eventWindow) {
        NSRect content = [[eventWindow contentView] frame];
        y = content.size.height - y; // flip to top-left origin
    }
    *outX = x;
    *outY = y;
}

// Map an NSTouch phase onto the Touch_* action codes.
static int touchAction(NSTouchPhase phase) {
    if (phase & NSTouchPhaseBegan) return TOUCH_DOWN;
    if (phase & (NSTouchPhaseMoved | NSTouchPhaseStationary)) return TOUCH_MOVE;
    if (phase & NSTouchPhaseEnded) return TOUCH_UP;
    return TOUCH_CANCEL;
}

// Feed one gesture/touch carrier event into input.Touch: resolve each active
// touch into its slot (identity hash % TOUCH_MAX), normalize position into
// content coords, and estimate pressure from the resting flag (legacy parity).
static void dispatchTouches(NSEvent *event, uint32_t wid) {
    NSWindow *eventWindow = [event window];
    if (!eventWindow) return;
    NSView *contentView = [eventWindow contentView];
    if (!contentView) return;

    NSSet *touches = [event touchesMatchingPhase:NSTouchPhaseAny inView:contentView];
    if (!touches || touches.count == 0) return;

    NSRect frame = [contentView frame];
    double winW = frame.size.width;
    double winH = frame.size.height;

    for (NSTouch *touch in touches) {
        NSUInteger slot = [[touch identity] hash] % TOUCH_MAX;
        NSPoint norm = [touch normalizedPosition];
        double posX = norm.x * winW;
        double posY = (1.0 - norm.y) * winH;
        double pressure = [touch isResting] ? 0.2 : 0.8;
        Touch_pushTouchEvent(wid, (int)slot, touchAction([touch phase]),
                             posX, posY, pressure, kTapThresholdNanos);
    }
}

// Intercept-and-forward: read everything the engine cares about off each OS
// event, push it into the input modules, then hand the event back to AppKit
// so the responder chain keeps working. This is the Thread-0 producer side of
// the input pipeline.
static void routeEvent(NSEvent *event) {
    NSEventType type = [event type];
    // Which engine window did the OS deliver this to? Tagged on every queued
    // event so dispatch routes it to that window's listeners only.
    uint32_t wid = windowIdOf([event window]);

    switch (type) {
        case NSEventTypeKeyDown:
        case NSEventTypeKeyUp: {
            short macCode = [event keyCode];
            if (macCode >= 0 && macCode < 128) {
                int stdKey = macKeyMap[macCode];
                if (stdKey != -1)
                    Key_pushEvent(wid, stdKey,
                                  type == NSEventTypeKeyDown ? KEY_ACTION_DOWN : KEY_ACTION_UP,
                                  kTapThresholdNanos);
                if (type == NSEventTypeKeyDown && stdKey != -1) {
                    NSString *chars = [event characters];
                    if (chars.length > 0) {
                        unsigned char c0 = (unsigned char)[chars characterAtIndex:0];
                        if (c0 > 0) Key_pushCharEvent(wid, c0);
                    }
                }
            }
            break;
        }

        case NSEventTypeScrollWheel:
            Mouse_pushScrollEvent(wid, [event scrollingDeltaX], [event scrollingDeltaY]);
            break;

        case NSEventTypeMagnify:
            Mouse_pushZoomEvent(wid, [event magnification]);
            break;

        case NSEventTypeLeftMouseDown:
        case NSEventTypeRightMouseDown:
        case NSEventTypeOtherMouseDown: {
            int button = (type == NSEventTypeLeftMouseDown) ? MOUSE_LEFT
                       : ((type == NSEventTypeRightMouseDown) ? MOUSE_RIGHT
                                                              : (int)[event buttonNumber]);
            Mouse_pushButtonEvent(wid, button, KEY_ACTION_DOWN, kTapThresholdNanos);
            // Clicks also refresh the tracked cursor position (legacy parity).
            double x, y;
            mouseLocation(event, &x, &y);
            Mouse_pushMoveEvent(wid, x, y);
            break;
        }

        case NSEventTypeLeftMouseUp:
        case NSEventTypeRightMouseUp:
        case NSEventTypeOtherMouseUp: {
            int button = (type == NSEventTypeLeftMouseUp) ? MOUSE_LEFT
                       : ((type == NSEventTypeRightMouseUp) ? MOUSE_RIGHT
                                                             : (int)[event buttonNumber]);
            Mouse_pushButtonEvent(wid, button, KEY_ACTION_UP, kTapThresholdNanos);
            double x, y;
            mouseLocation(event, &x, &y);
            Mouse_pushMoveEvent(wid, x, y);
            break;
        }

        case NSEventTypeMouseMoved:
        case NSEventTypeLeftMouseDragged:
        case NSEventTypeRightMouseDragged:
        case NSEventTypeOtherMouseDragged: {
            if (s_cursorLocked) {
                // Locked: AppKit reports a constant anchor with real deltas.
                Mouse_pushMoveDeltaEvent(wid, [event deltaX], [event deltaY]);
                break;
            }
            double x, y;
            mouseLocation(event, &x, &y);
            if (type == NSEventTypeMouseMoved) {
                Mouse_pushMoveEvent(wid, x, y);
            } else {
                int button = (type == NSEventTypeLeftMouseDragged) ? MOUSE_LEFT
                           : ((type == NSEventTypeRightMouseDragged) ? MOUSE_RIGHT
                                                                      : (int)[event buttonNumber]);
                Mouse_pushDragEvent(wid, button, x, y);
            }
            break;
        }

        case NSEventTypeGesture:
        case NSEventTypeBeginGesture:
        case NSEventTypeEndGesture:
            dispatchTouches(event, wid);
            break;

        default:
            break;
    }
}

// Drain the OS event queue. Called every frame from the engine loop (the
// "poll" half of poll-then-tick). Returns immediately; never blocks. After
// the pump, mirror the OS's key window into the focus word so the rest of
// the engine can ask "who is focused?" without touching AppKit.
void Window_pollEvents(void) {
    @autoreleasepool {
        NSEvent *event;
        while ((event = [NSApp nextEventMatchingMask:NSEventMaskAny
                                            untilDate:[NSDate distantPast]
                                               inMode:NSDefaultRunLoopMode
                                              dequeue:YES])) {
            routeEvent(event);
            [NSApp sendEvent:event];
        }
        [NSApp updateWindows];
        recenterIfLocked();
        Focus_set(windowIdOf([NSApp keyWindow]));

        // Resize reflection: compare the live content rect against the cache
        // and bump the generation only on an actual change, so a renderer
        // polling once per frame pays one int compare.
        for (uint32_t i = 1; i < WINDOW_ID_SLOTS; i++) {
            NSWindow *w = s_idToWindow[i];
            if (!w) continue;
            Window *handle = windowHandleOf(w);
            if (!handle) continue;
            NSRect content = [w contentRectForFrameRect:[w frame]];
            int cw = (int)content.size.width;
            int ch = (int)content.size.height;
            if (cw != (*handle).cachedWidth || ch != (*handle).cachedHeight) {
                (*handle).cachedWidth = cw;
                (*handle).cachedHeight = ch;
                atomic_fetch_add_explicit(&(*handle).sizeGeneration, 1, memory_order_release);
            }
        }
    }
}

// Build the NSWindow + C handle. Shared by every constructor. The window is
// created HIDDEN — visibility is an explicit Window_show() decision, so
// construct -> mutate -> show never flashes a half-configured window.
static Window *windowAlloc(const WindowDesc *desc) {
    @autoreleasepool {
        if (!NSApp) {
            [NSApplication sharedApplication];   // bootstrap the app object once
        }
        if (!sAppDelegate) {
            sAppDelegate = [[AntiAppDelegate alloc] init];
            [NSApp setDelegate:sAppDelegate];
            [NSApp setActivationPolicy:NSApplicationActivationPolicyRegular];
            [NSApp activateIgnoringOtherApps:YES];
        }

        NSRect frame = NSMakeRect(0, 0, (CGFloat)(*desc).width, (CGFloat)(*desc).height);

        NSWindowStyleMask style = NSWindowStyleMaskTitled
                                | NSWindowStyleMaskClosable
                                | NSWindowStyleMaskMiniaturizable
                                | NSWindowStyleMaskResizable;

        NSWindow *window = [[NSWindow alloc]
            initWithContentRect:frame
                      styleMask:style
                         backing:NSBackingStoreBuffered
                           defer:NO];
        [window setTitle:[NSString stringWithUTF8String:(*desc).title]];
        [window setReleasedWhenClosed:NO];   // we own the window object; close must not free it

        // Green traffic light enters native fullscreen (mirrors legacy allocate()).
        [window setCollectionBehavior:NSWindowCollectionBehaviorFullScreenPrimary];

        // Disable AppKit's live-resize content-preservation scale so pinned
        // content is never stretched to the new size during a drag.
        [window setPreservesContentDuringLiveResize:NO];

        // Trackpad touch delivery: the content view must opt in. The modern
        // allowedTouchTypes API replaces the deprecated setAcceptsTouchEvents:.
        [window.contentView setAllowedTouchTypes:NSTouchTypeMaskDirect | NSTouchTypeMaskIndirect];

        AntiWindowDelegate *delegate = [[AntiWindowDelegate alloc] init];
        [window setDelegate:delegate];

        Window *w = (Window *)calloc(1, sizeof(Window));
        (*w).nsWindow = window;
        (*w).delegate = delegate;
        (*w).shouldClose = false;
        atomic_store_explicit(&(*w).sizeGeneration, 0, memory_order_relaxed);
        NSRect initialContent = [window contentRectForFrameRect:[window frame]];
        (*w).cachedWidth = (int)initialContent.size.width;
        (*w).cachedHeight = (int)initialContent.size.height;
        (*w).id = windowIdAcquire(window, w);
        delegate.shouldClosePtr = &(*w).shouldClose;

        sLastWindow = window;

        return w;
    }
}

// Merge a caller's Desc over the defaults. Unset (zero) fields fall back —
// this is what makes partial designated initializers behave like overloads.
static WindowDesc descResolve(const WindowDesc *desc) {
    WindowDesc d = { .title = "anti", .width = 800, .height = 600, .x = 0, .y = 0 };
    if (!desc)
        return d;
    if ((*desc).title) d.title = (*desc).title;
    if ((*desc).width > 0) d.width = (*desc).width;
    if ((*desc).height > 0) d.height = (*desc).height;
    d.x = (*desc).x;
    d.y = (*desc).y;
    d.centered = (*desc).centered;
    d.shown = (*desc).shown;
    return d;
}

// Default constructor: hidden, 800x600, "anti".
Window *Window_0(void) {
    return windowAlloc(&(WindowDesc){ .title = "anti", .width = 800, .height = 600 });
}

// One-arg overload: titled, hidden.
Window *Window_1(const char *title) {
    return windowAlloc(&(WindowDesc){ .title = title, .width = 800, .height = 600 });
}

// Parameterized constructor: Desc fields applied on top of defaults.
Window *Window_new(const WindowDesc *desc) {
    WindowDesc d = descResolve(desc);
    Window *w = windowAlloc(&d);
    if (!w)
        return NULL;
    if (d.centered)
        Window_center(w);
    else if (d.x != 0 || d.y != 0)
        Window_setLocation(w, d.x, d.y);
    if (d.shown)
        Window_show(w);
    return w;
}

// Legacy-style convenience constructor: titled + sized, still hidden.
Window *Window_create(const char *title, int width, int height) {
    return Window_new(&(WindowDesc){ .title = title, .width = width, .height = height });
}

// Tear down the window and free the handle. Safe to call whether the user
// already closed the window or not: if it's still open we close it, and we
// detach the delegate first so no callback can touch our freed memory.
void Window_destroy(Window *window) {
    if (!window) return;
    @autoreleasepool {
        [(*window).nsWindow setDelegate:nil];   // detach: no callbacks into freed struct
        if (!(*window).shouldClose) {
            [(*window).nsWindow close];
        }
        // Drop the id and any listeners still scoped to it so nothing dangles.
        Key_detachWindowAll((*window).id);
        Mouse_detachWindowAll((*window).id);
        Touch_detachWindowAll((*window).id);
        windowIdRelease((*window).id);
    }
    free(window);
}

bool Window_shouldClose(Window *window) {
    return window ? (*window).shouldClose : true;
}

void Window_setVsync(Window *window, bool enabled) {
    (void) window;
    (void) enabled;
}

// ---------------------------------------------------------------------------
// Chrome / state API. Mirrors the legacy macOSWindow method surface.
// NSWindowStyleMask bits line up 1:1 with the legacy constants (Resizable 1<<3,
// FullScreen 1<<14, FullSizeContentView 1<<15).
// ---------------------------------------------------------------------------

static NSWindowStyleMask styleMaskOf(Window *window) {
    return [(*window).nsWindow styleMask];
}

static bool hasStyleBit(Window *window, NSWindowStyleMask bit) {
    return (styleMaskOf(window) & bit) != 0;
}

// Single mask-rewrite path for all capability toggles. While native fullscreen
// AppKit owns the mask (the FullScreen bit can only change inside a transition),
// so style mutations are skipped then — mirroring the Ghostty guard. When not
// fullscreen the bit is never present, so it is never forced via setStyleMask:.
static void updateStyleMask(Window *window, NSWindowStyleMask add, NSWindowStyleMask clear) {
    NSWindowStyleMask mask = styleMaskOf(window);
    if ((mask & NSWindowStyleMaskFullScreen) != 0)
        return;
    [(*window).nsWindow setStyleMask:(mask & ~clear) | add];
}

void Window_setTitle(Window *window, const char *title) {
    if (!window || !title)
        return;
    @autoreleasepool {
        [(*window).nsWindow setTitle:[NSString stringWithUTF8String:title]];

        // macOS 15+ re-reveals the native title view whenever the title string
        // changes, even when titleVisibility is hidden. Re-apply the hidden
        // state for FullSizeContentView (NAKED) windows, mirroring legacy.
        if ((styleMaskOf(window) & NSWindowStyleMaskFullSizeContentView) != 0) {
            [(*window).nsWindow setTitlebarAppearsTransparent:YES];
            [(*window).nsWindow setTitleVisibility:NSWindowTitleHidden];
        }
    }
}

int Window_width(Window *window) {
    if (!window)
        return 0;
    return (int)[(*window).nsWindow contentRectForFrameRect:[(*window).nsWindow frame]].size.width;
}

int Window_height(Window *window) {
    if (!window)
        return 0;
    return (int)[(*window).nsWindow contentRectForFrameRect:[(*window).nsWindow frame]].size.height;
}

void Window_setDimension(Window *window, int width, int height) {
    if (!window)
        return;
    @autoreleasepool {
        [(*window).nsWindow setContentSize:NSMakeSize((CGFloat)width, (CGFloat)height)];
    }
}

void Window_setWidth(Window *window, int width) {
    if (!window)
        return;
    @autoreleasepool {
        NSSize size = [(*window).nsWindow contentRectForFrameRect:[(*window).nsWindow frame]].size;
        [(*window).nsWindow setContentSize:NSMakeSize((CGFloat)width, size.height)];
    }
}

void Window_setHeight(Window *window, int height) {
    if (!window)
        return;
    @autoreleasepool {
        NSSize size = [(*window).nsWindow contentRectForFrameRect:[(*window).nsWindow frame]].size;
        [(*window).nsWindow setContentSize:NSMakeSize(size.width, (CGFloat)height)];
    }
}

void Window_setLocation(Window *window, int x, int y) {
    if (!window)
        return;
    @autoreleasepool {
        NSRect screen = [[NSScreen mainScreen] frame];
        [(*window).nsWindow setFrameTopLeftPoint:NSMakePoint((CGFloat)x, screen.size.height - (CGFloat)y)];
    }
}

void Window_center(Window *window) {
    if (!window)
        return;
    @autoreleasepool {
        [(*window).nsWindow center];
    }
}

void Window_show(Window *window) {
    if (!window)
        return;
    @autoreleasepool {
        // Modern activation: activateIgnoringOtherApps: is deprecated and
        // unreliable on recent macOS (leaves traffic lights greyed).
        [[NSRunningApplication currentApplication]
            activateWithOptions:NSApplicationActivateAllWindows];
        [(*window).nsWindow makeKeyAndOrderFront:nil];
    }
}

void Window_hide(Window *window) {
    if (!window)
        return;
    @autoreleasepool {
        [(*window).nsWindow orderOut:nil];
    }
}

void Window_setVisible(Window *window, bool visible) {
    if (visible)
        Window_show(window);
    else
        Window_hide(window);
}

bool Window_isResizable(Window *window) {
    if (!window)
        return false;
    return hasStyleBit(window, NSWindowStyleMaskResizable);
}

void Window_setResizable(Window *window, bool resizable) {
    if (!window)
        return;
    updateStyleMask(window, resizable ? NSWindowStyleMaskResizable : 0, resizable ? 0 : NSWindowStyleMaskResizable);
}

bool Window_isClosable(Window *window) {
    if (!window)
        return false;
    return hasStyleBit(window, NSWindowStyleMaskClosable);
}

void Window_setClosable(Window *window, bool closable) {
    if (!window)
        return;
    updateStyleMask(window, closable ? NSWindowStyleMaskClosable : 0, closable ? 0 : NSWindowStyleMaskClosable);
}

bool Window_isMiniaturizable(Window *window) {
    if (!window)
        return false;
    return hasStyleBit(window, NSWindowStyleMaskMiniaturizable);
}

void Window_setMiniaturizable(Window *window, bool miniaturizable) {
    if (!window)
        return;
    updateStyleMask(window, miniaturizable ? NSWindowStyleMaskMiniaturizable : 0, miniaturizable ? 0 : NSWindowStyleMaskMiniaturizable);
}

void Window_setFullscreenButton(Window *window, bool enabled) {
    if (!window)
        return;
    @autoreleasepool {
        NSWindowCollectionBehavior behavior = [(*window).nsWindow collectionBehavior];
        if (enabled)
            behavior |= NSWindowCollectionBehaviorFullScreenPrimary;
        else
            behavior &= ~NSWindowCollectionBehaviorFullScreenPrimary;
        [(*window).nsWindow setCollectionBehavior:behavior];
    }
}

void Window_setUndecorated(Window *window, int mode) {
    if (!window)
        return;
    @autoreleasepool {
        NSWindowStyleMask mask = styleMaskOf(window);
        if ((mask & NSWindowStyleMaskFullScreen) != 0)
            return; // AppKit owns the mask in fullscreen

        NSWindowStyleMask next;
        if (mode == WINDOW_UNDECORATED_BORDERLESS)
            next = 0;
        else if (mode == WINDOW_UNDECORATED_NAKED)
            next = NSWindowStyleMaskTitled | NSWindowStyleMaskClosable
                 | NSWindowStyleMaskMiniaturizable | NSWindowStyleMaskResizable
                 | NSWindowStyleMaskFullSizeContentView;
        else
            next = NSWindowStyleMaskTitled | NSWindowStyleMaskClosable
                 | NSWindowStyleMaskMiniaturizable | NSWindowStyleMaskResizable;

        [(*window).nsWindow setStyleMask:next];

        bool transparent = (mode == WINDOW_UNDECORATED_NAKED);
        [(*window).nsWindow setTitlebarAppearsTransparent:transparent];
        [(*window).nsWindow setTitleVisibility:(transparent ? NSWindowTitleHidden : NSWindowTitleVisible)];
    }
}

void Window_minimize(Window *window) {
    if (!window)
        return;
    @autoreleasepool {
        [(*window).nsWindow miniaturize:nil];
    }
}

void Window_restore(Window *window) {
    if (!window)
        return;
    @autoreleasepool {
        [(*window).nsWindow deminiaturize:nil];
    }
}

bool Window_isMinimized(Window *window) {
    if (!window)
        return false;
    @autoreleasepool {
        return [(*window).nsWindow isMiniaturized];
    }
}

bool Window_isFullscreen(Window *window) {
    if (!window)
        return false;
    @autoreleasepool {
        return (styleMaskOf(window) & NSWindowStyleMaskFullScreen) != 0;
    }
}

void Window_setFullscreen(Window *window, bool fullscreen) {
    if (!window)
        return;
    @autoreleasepool {
        if (fullscreen != Window_isFullscreen(window))
            [(*window).nsWindow toggleFullScreen:nil];
    }
}

void Window_toggleFullscreen(Window *window) {
    if (!window)
        return;
    @autoreleasepool {
        [(*window).nsWindow toggleFullScreen:nil];
    }
}

void Window_setDRM(Window *window, bool enabled) {
    if (!window)
        return;
    @autoreleasepool {
        // NSWindowSharingNone = 0, NSWindowSharingReadOnly = 1
        [(*window).nsWindow setSharingType:(enabled ? NSWindowSharingNone : NSWindowSharingReadOnly)];
    }
}

void Window_setMinSize(Window *window, int width, int height) {
    if (!window)
        return;
    @autoreleasepool {
        [(*window).nsWindow setContentMinSize:NSMakeSize((CGFloat)width, (CGFloat)height)];
    }
}

void Window_setMaxSize(Window *window, int width, int height) {
    if (!window)
        return;
    @autoreleasepool {
        [(*window).nsWindow setContentMaxSize:NSMakeSize((CGFloat)width, (CGFloat)height)];
    }
}

void Window_setCursorLocked(Window *window, bool locked) {
    (void) window; // the lock warps in global screen space; any window works
    if (locked == s_cursorLocked)
        return;

    if (locked) {
        // Decouple cursor from motion so deltas arrive without drift between
        // warp passes; anchor is the current key window's centre in global
        // (bottom-left origin) screen coordinates.
        NSWindow *anchor = sLastWindow;
        NSRect frame = anchor ? [anchor frame] : [[NSScreen mainScreen] frame];
        s_lockCenter = NSMakePoint(frame.origin.x + frame.size.width / 2.0,
                                   frame.origin.y + frame.size.height / 2.0);
        CGAssociateMouseAndMouseCursorPosition(NO);
        CGWarpMouseCursorPosition(s_lockCenter);
        CGDisplayHideCursor(kCGDirectMainDisplay);
        s_cursorLocked = true;
    } else {
        s_cursorLocked = false;
        CGDisplayShowCursor(kCGDirectMainDisplay);
        CGAssociateMouseAndMouseCursorPosition(YES);
    }
}

// ---------------------------------------------------------------------------
// Event wiring. Listeners attach to THIS window's id; dispatch routes events
// by the window tag they carried when the OS delivered them. Global device
// taps (Key_addListener etc.) still hear everything, engine-wide.
// ---------------------------------------------------------------------------

void Window_addKeyEvent(Window *window, const KeyEvent *listener) {
    if (!window) return;
    Key_attachWindow((*window).id, listener);
}

bool Window_removeKeyEvent(Window *window, const KeyEvent *listener) {
    if (!window) return false;
    return Key_detachWindow((*window).id, listener);
}

void Window_addMouseEvent(Window *window, const MouseEvent *listener) {
    if (!window) return;
    Mouse_attachWindow((*window).id, listener);
}

bool Window_removeMouseEvent(Window *window, const MouseEvent *listener) {
    if (!window) return false;
    return Mouse_detachWindow((*window).id, listener);
}

void Window_addTouchEvent(Window *window, const TouchEvent *listener) {
    if (!window) return;
    Touch_attachWindow((*window).id, listener);
}

bool Window_removeTouchEvent(Window *window, const TouchEvent *listener) {
    if (!window) return false;
    return Touch_detachWindow((*window).id, listener);
}

void Window_dispatchEvents(Window *window) {
    (void) window; // rings are engine-global; any window handle may drain them
    Key_dispatchEvents();
    Mouse_dispatchEvents();
    Touch_dispatchEvents();
}

// --- Focus ---

uint32_t Window_id(Window *window) {
    return window ? (*window).id : FOCUS_BROADCAST;
}

// Ask the OS to make this the key window (the spotlight).
void Window_focus(Window *window) {
    if (!window) return;
    @autoreleasepool {
        [[NSRunningApplication currentApplication]
            activateWithOptions:NSApplicationActivateAllWindows];
        [(*window).nsWindow makeKeyAndOrderFront:nil];
        Focus_set((*window).id);
    }
}

bool Window_isFocused(Window *window) {
    return window && Focus_isFocused((*window).id);
}

// --- Resize reflection ---

uint64_t Window_sizeGeneration(Window *window) {
    if (!window) return 0;
    return atomic_load_explicit(&(*window).sizeGeneration, memory_order_acquire);
}
// --- Software frame presentation ---------------------------------------------
// Packs the planar RGBA raster into an interleaved RGBX bitmap and stamps it
// into the content view's layer, aspect-fit. The scratch pack buffer is cached
// per size: steady-state presents are allocation-free.

bool Window_present(Window *window, const Buffer *frame) {
    if (!window || !frame)
        return false;
    size_t w = Buffer_width(frame);
    size_t h = Buffer_height(frame);
    if (w == 0 || h == 0)
        return false;

    static uint8_t *s_pixels = NULL;
    static size_t s_cap = 0;
    size_t bytes = w * h * 4;
    if (bytes > s_cap) {
        uint8_t *grown = (uint8_t *)realloc(s_pixels, bytes);
        if (!grown)
            return false;
        s_pixels = grown;
        s_cap = bytes;
    }

    for (size_t y = 0; y < h; y++) {
        uint8_t *row = s_pixels + y * w * 4;
        for (size_t x = 0; x < w; x++) {
            uint8_t r = 0, g = 0, b = 0, a = 0;
            ColorBuffer_getRGBA(frame, x, y, &r, &g, &b, &a);
            row[x * 4 + 0] = r;
            row[x * 4 + 1] = g;
            row[x * 4 + 2] = b;
            row[x * 4 + 3] = 255; // opaque present; alpha channel reserved
        }
    }

    @autoreleasepool {
        NSWindow *nsw = (*window).nsWindow;
        NSView *view = [nsw contentView];
        [view setWantsLayer:YES];

        CGColorSpaceRef cs = CGColorSpaceCreateDeviceRGB();
        CGContextRef ctx = CGBitmapContextCreate(
            s_pixels, w, h, 8, w * 4, cs,
            kCGImageAlphaNoneSkipLast | kCGBitmapByteOrder32Big);
        if (!ctx) {
            CGColorSpaceRelease(cs);
            return false;
        }
        CGImageRef img = CGBitmapContextCreateImage(ctx);

        CALayer *layer = view.layer;
        [CATransaction begin];
        [CATransaction setDisableActions:YES]; // no implicit fade between frames
        layer.contents = (__bridge id)img;
        layer.contentsGravity = kCAGravityResizeAspect;
        [CATransaction commit];

        CFRelease(img);
        CFRelease(ctx);
        CGColorSpaceRelease(cs);
        return true;
    }
}

void *Window_contentView(Window *window) {
    if (!window || !(*window).nsWindow)
        return NULL;
    return (__bridge void *)[(*window).nsWindow contentView];
}

void *Window_metalLayer(Window *window) {
    if (!window || !(*window).nsWindow)
        return NULL;
    @autoreleasepool {
        NSView *view = [(*window).nsWindow contentView];
        [view setWantsLayer:YES];
        if (![view.layer isKindOfClass:[CAMetalLayer class]]) {
            // pin for C callers: static strong ref keeps the layer immortal
            static CAMetalLayer *s_pinnedLayer = NULL;
            s_pinnedLayer = [[CAMetalLayer alloc] init];
            s_pinnedLayer.contentsGravity = kCAGravityTopLeft;
            s_pinnedLayer.contentsScale = [(*window).nsWindow backingScaleFactor];
            view.layer = s_pinnedLayer;
        }
        return (__bridge void *)view.layer;
    }
}

void Window_setGravityTopLeft(Window *window) {
    if (!window || !(*window).nsWindow)
        return;
    @autoreleasepool {
        NSView *view = [(*window).nsWindow contentView];
        if (view.layer) {
            view.layer.contentsGravity = kCAGravityTopLeft;
        }
    }
}
