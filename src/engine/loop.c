// anti_loop.c — EngineLoop port. The heart of the engine: a fixed-timestep
// loop that calls your tick() function. accumulator keeps a constant step
// (frame_ms) regardless of wall-clock jitter, so simulation speed is stable.
//
// The "while" the lessons keep mentioning lives here:
//     while (running) { poll events; tick at fixed steps; }

#include "anti_loop.h"

#include <time.h>

static int64_t anti_now_ms(void) {
    struct timespec ts;
    clock_gettime(CLOCK_MONOTONIC, &ts);
    return (int64_t)ts.tv_sec * 1000 + ts.tv_nsec / 1000000;
}

void anti_loop_run(anti_loop_t *loop) {
    if (!loop || !(*loop).tick) return;

    (*loop).running = true;
    int64_t accumulator = 0;
    int64_t prev = anti_now_ms();

    while ((*loop).running) {
        int64_t now = anti_now_ms();
        accumulator += now - prev;
        prev = now;

        while (accumulator >= (*loop).frame_ms) {
            (*loop).tick((*loop).userdata);
            accumulator -= (*loop).frame_ms;
        }
    }
}

void anti_loop_stop(anti_loop_t *loop) {
    if (loop) (*loop).running = false;
}