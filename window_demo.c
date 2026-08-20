// window_demo.c — the engine loop driving the AppKit shim.
//
// The poll/tick cycle (Lesson 8): every frame we drain OS events, then check
// whether the user asked to close. The loop stops the moment the window's
// close button flips should_close — that's the whole AppKit integration.

#include <stdio.h>

#include "engine/loop.h"
#include "window/window.h"

typedef struct {
    anti_window_t *window;
    anti_loop_t loop;
    int frames;
} win_ctx_t;

static void win_tick(void *userdata) {
    win_ctx_t *ctx = userdata;
    anti_window_poll_events();

    if (anti_window_should_close((*ctx).window)) {
        anti_loop_stop(&(*ctx).loop);
        return;
    }

    (*ctx).frames++;
    printf("frame=%d\n", (*ctx).frames);
}

int main(void) {
    win_ctx_t ctx = { .frames = 0 };
    ctx.window = anti_window_create("anti", 640, 480);
    ctx.loop = (anti_loop_t){ .tick = win_tick, .userdata = &ctx, .frame_ms = 16, .running = false };

    anti_loop_run(&ctx.loop);

    printf("window closed after %d frames\n", ctx.frames);
    anti_window_destroy(ctx.window);
    return 0;
}