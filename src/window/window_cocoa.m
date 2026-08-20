// anti_window_cocoa.m — the AppKit shim (the ".m" glue file).
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
struct anti_window {
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
    (void)notification;
    if (self.should_close_ptr) *self.should_close_ptr = true;
}
- (BOOL)applicationShouldTerminateAfterLastWindowClosed:(NSApplication *)sender {
    (void)sender;
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
- (void)windowWillClose:(NSNotification *)notification {
    (void)notification;
    if (self.should_close_ptr) *self.should_close_ptr = true;
}
@end

static AntiAppDelegate *s_app_delegate = nil; // one app delegate for the whole process
static NSWindow *s_last_window = nil;

// Drain the OS event queue. Called every frame from the engine loop (the
// "poll" half of poll-then-tick). Returns immediately; never blocks.
void anti_window_poll_events(void) {
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
// The handle must be freed with anti_window_destroy().
anti_window_t *anti_window_create(const char *title, int width, int height) {
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
        [window center];

        AntiWindowDelegate *delegate = [[AntiWindowDelegate alloc] init];
        [window setDelegate:delegate];

        anti_window_t *w = (anti_window_t *)calloc(1, sizeof(anti_window_t));
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
void anti_window_destroy(anti_window_t *window) {
    if (!window) return;
    @autoreleasepool {
        [(*window).ns_window setDelegate:nil];   // detach: no callbacks into freed struct
        if (!(*window).should_close) {
            [(*window).ns_window close];
        }
    }
    free(window);
}

bool anti_window_should_close(anti_window_t *window) {
    return window ? (*window).should_close : true;
}

void anti_window_set_vsync(anti_window_t *window, bool enabled) {
    (void)window;
    (void)enabled;
}