// window_cocoa.m — the AppKit shim (the ".m" glue file).
//
// anti's core is pure C11; this is the ONE Objective-C file in the project.
// It exists only to talk to AppKit, because NSWindow/NSApplication are ObjC
// objects and there is no pure-C way to create them. Everything above this
// boundary stays C; everything here is "dip into the OS, hand back a handle".
//
// Design notes:
//   - s_app_delegate is created once per process (the app lifecycle delegate).
//   - Each window gets its own AntiWindowDelegate so we can learn about the
//     user clicking the red close button -> sets should_close -> engine loop
//     sees it and exits (see window_demo.c).
//   - setReleasedWhenClosed:NO is CRITICAL. The default (YES for programmatic
//     windows) makes NSWindow free itself the moment it closes; our destroy()
//     would then call close on freed memory = the segfault you saw. We own the
//     window object; only destroy() releases it.

#import <AppKit/AppKit.h>
#import <Foundation/Foundation.h>

#include "window/window.h"

@class AntiWindowDelegate;

// One opaque handle handed back to C. Holds both NS objects we must keep
// alive: the window itself and its delegate.
struct Window {
    NSWindow *ns_window;
    AntiWindowDelegate *delegate;
    bool should_close;
};

// App-level delegate: receives lifecycle events for the whole application.
// applicationShouldTerminateAfterLastWindowClosed lets the process end when
// the last window goes away (normal for a game/engine run).
@interface AntiAppDelegate : NSObject <NSApplicationDelegate>
@property(nonatomic, assign) bool *should_close_ptr;
@end

@implementation AntiAppDelegate
- (void)applicationWillTerminate:(NSNotification *)notification {
    (void) notification;
    if (self.should_close_ptr) *self.should_close_ptr = true;
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
@property(nonatomic, assign) bool *should_close_ptr;
@end

@implementation AntiWindowDelegate
- (void) windowWillClose:(NSNotification *)notification {
    (void) notification;
    if (self.should_close_ptr) *self.should_close_ptr = true;
}
@end

static AntiAppDelegate *s_app_delegate = nil; // one app delegate for the whole process
static NSWindow *s_last_window = nil;

// Drain the OS event queue. Called every frame from the engine loop (the
// "poll" half of poll-then-tick). Returns immediately; never blocks.
void Window_pollEvents(void) {
    @autoreleasepool {
        NSEvent *event;
        while ((event = [NSApp nextEventMatchingMask:NSEventMaskAny
                                           untilDate:[NSDate distantPast]
                                              inMode:NSDefaultRunLoopMode
                                             dequeue:YES])) {
            [NSApp sendEvent:event];
        }
    }
}

// Create and show an NSWindow. Returns an opaque handle or NULL.
// The handle must be freed with Window_destroy().
Window *Window_create(const char *title, int width, int height) {
    @autoreleasepool {
        if (!NSApp) {
            [NSApplication sharedApplication];   // bootstrap the app object once
        }
        if (!s_app_delegate) {
            s_app_delegate = [[AntiAppDelegate alloc] init];
            [NSApp setDelegate:s_app_delegate];
            [NSApp setActivationPolicy:NSApplicationActivationPolicyRegular];
            [NSApp activateIgnoringOtherApps:YES];
        }

        NSRect frame = NSMakeRect(0, 0, (CGFloat)width, (CGFloat)height);

        NSWindowStyleMask style = NSWindowStyleMaskTitled
                                | NSWindowStyleMaskClosable
                                | NSWindowStyleMaskMiniaturizable
                                | NSWindowStyleMaskResizable;

        NSWindow *window = [[NSWindow alloc]
            initWithContentRect:frame
                      styleMask:style
                        backing:NSBackingStoreBuffered
                          defer:NO];
        [window setTitle:[NSString stringWithUTF8String:title]];
        [window setReleasedWhenClosed:NO];   // we own the window object; close must not free it

        // Green traffic light enters native fullscreen (mirrors legacy allocate()).
        [window setCollectionBehavior:NSWindowCollectionBehaviorFullScreenPrimary];

        // Disable AppKit's live-resize content-preservation scale so pinned
        // content is never stretched to the new size during a drag.
        [window setPreservesContentDuringLiveResize:NO];

        [window center];

        AntiWindowDelegate *delegate = [[AntiWindowDelegate alloc] init];
        [window setDelegate:delegate];

        Window *w = (Window *)calloc(1, sizeof(Window));
        (*w).ns_window = window;
        (*w).delegate = delegate;
        (*w).should_close = false;
        delegate.should_close_ptr = &(*w).should_close;

        s_last_window = window;
        [window makeKeyAndOrderFront:nil];

        return w;
    }
}

// Tear down the window and free the handle. Safe to call whether the user
// already closed the window or not: if it's still open we close it, and we
// detach the delegate first so no callback can touch our freed memory.
void Window_destroy(Window *window) {
    if (!window) return;
    @autoreleasepool {
        [(*window).ns_window setDelegate:nil];   // detach: no callbacks into freed struct
        if (!(*window).should_close) {
            [(*window).ns_window close];
        }
    }
    free(window);
}

bool Window_shouldClose(Window *window) {
    return window ? (*window).should_close : true;
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
    return [(*window).ns_window styleMask];
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
    [(*window).ns_window setStyleMask:(mask & ~clear) | add];
}

void Window_setTitle(Window *window, const char *title) {
    if (!window || !title)
        return;
    @autoreleasepool {
        [(*window).ns_window setTitle:[NSString stringWithUTF8String:title]];

        // macOS 15+ re-reveals the native title view whenever the title string
        // changes, even when titleVisibility is hidden. Re-apply the hidden
        // state for FullSizeContentView (NAKED) windows, mirroring legacy.
        if ((styleMaskOf(window) & NSWindowStyleMaskFullSizeContentView) != 0) {
            [(*window).ns_window setTitlebarAppearsTransparent:YES];
            [(*window).ns_window setTitleVisibility:NSWindowTitleHidden];
        }
    }
}

void Window_setSize(Window *window, int width, int height) {
    if (!window)
        return;
    @autoreleasepool {
        [(*window).ns_window setContentSize:NSMakeSize((CGFloat)width, (CGFloat)height)];
    }
}

void Window_setWidth(Window *window, int width) {
    if (!window)
        return;
    @autoreleasepool {
        NSSize size = [(*window).ns_window contentRectForFrameRect:[(*window).ns_window frame]].size;
        [(*window).ns_window setContentSize:NSMakeSize((CGFloat)width, size.height)];
    }
}

void Window_setHeight(Window *window, int height) {
    if (!window)
        return;
    @autoreleasepool {
        NSSize size = [(*window).ns_window contentRectForFrameRect:[(*window).ns_window frame]].size;
        [(*window).ns_window setContentSize:NSMakeSize(size.width, (CGFloat)height)];
    }
}

void Window_setLocation(Window *window, int x, int y) {
    if (!window)
        return;
    @autoreleasepool {
        NSRect screen = [[NSScreen mainScreen] frame];
        [(*window).ns_window setFrameTopLeftPoint:NSMakePoint((CGFloat)x, screen.size.height - (CGFloat)y)];
    }
}

void Window_center(Window *window) {
    if (!window)
        return;
    @autoreleasepool {
        [(*window).ns_window center];
    }
}

void Window_setVisible(Window *window, bool visible) {
    if (!window)
        return;
    @autoreleasepool {
        if (visible) {
            // Modern activation: activateIgnoringOtherApps: is deprecated and
            // unreliable on recent macOS (leaves traffic lights greyed).
            [[NSRunningApplication currentApplication]
                activateWithOptions:NSApplicationActivateAllWindows];
            [(*window).ns_window makeKeyAndOrderFront:nil];
        } else {
            [(*window).ns_window orderOut:nil];
        }
    }
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
        NSWindowCollectionBehavior behavior = [(*window).ns_window collectionBehavior];
        if (enabled)
            behavior |= NSWindowCollectionBehaviorFullScreenPrimary;
        else
            behavior &= ~NSWindowCollectionBehaviorFullScreenPrimary;
        [(*window).ns_window setCollectionBehavior:behavior];
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

        [(*window).ns_window setStyleMask:next];

        bool transparent = (mode == WINDOW_UNDECORATED_NAKED);
        [(*window).ns_window setTitlebarAppearsTransparent:transparent];
        [(*window).ns_window setTitleVisibility:(transparent ? NSWindowTitleHidden : NSWindowTitleVisible)];
    }
}

void Window_minimize(Window *window) {
    if (!window)
        return;
    @autoreleasepool {
        [(*window).ns_window miniaturize:nil];
    }
}

void Window_restore(Window *window) {
    if (!window)
        return;
    @autoreleasepool {
        [(*window).ns_window deminiaturize:nil];
    }
}

bool Window_isMinimized(Window *window) {
    if (!window)
        return false;
    @autoreleasepool {
        return [(*window).ns_window isMiniaturized];
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
            [(*window).ns_window toggleFullScreen:nil];
    }
}

void Window_toggleFullscreen(Window *window) {
    if (!window)
        return;
    @autoreleasepool {
        [(*window).ns_window toggleFullScreen:nil];
    }
}

void Window_setDRM(Window *window, bool enabled) {
    if (!window)
        return;
    @autoreleasepool {
        // NSWindowSharingNone = 0, NSWindowSharingReadOnly = 1
        [(*window).ns_window setSharingType:(enabled ? NSWindowSharingNone : NSWindowSharingReadOnly)];
    }
}

void Window_setMinSize(Window *window, int width, int height) {
    if (!window)
        return;
    @autoreleasepool {
        [(*window).ns_window setContentMinSize:NSMakeSize((CGFloat)width, (CGFloat)height)];
    }
}

void Window_setMaxSize(Window *window, int width, int height) {
    if (!window)
        return;
    @autoreleasepool {
        [(*window).ns_window setContentMaxSize:NSMakeSize((CGFloat)width, (CGFloat)height)];
    }
}