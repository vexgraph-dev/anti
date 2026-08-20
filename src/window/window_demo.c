// window_demo.c — the engine loop driving the AppKit shim.
//
// The poll/tick cycle (Lesson 8): every frame we drain OS events, then check
// whether the user asked to close. The loop stops the moment the window's
// close button flips should_close — that's the whole AppKit integration.

#include <stdio.h>

#include "engine/loop.h"
#include "window/window.h"

typedef struct {
    Window *window;
    Loop loop;
    int frames;
} win_ctx_t;

static void win_tick(void *userdata) {
    win_ctx_t *ctx = userdata;
    Window_pollEvents();

    if (Window_shouldClose((*ctx).window)) {
        Loop_stop(&(*ctx).loop);
        return;
    }

    (*ctx).frames++;
    printf("frame=%d\n", (*ctx).frames);
}

int main(void) {
    win_ctx_t ctx = { .frames = 0 };
    ctx.window = Window_create("anti", 640, 480);
    ctx.loop = (Loop){ .tick = win_tick, .userdata = &ctx, .frame_ms = 16, .running = false };

    Loop_run(&ctx.loop);

    printf("window closed after %d frames\n", ctx.frames);
    Window_destroy(ctx.window);
    return 0;
}