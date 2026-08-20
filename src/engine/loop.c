// loop.c — EngineLoop port. The heart of the engine: a fixed-timestep
// loop that calls your tick() function. accumulator keeps a constant step
// (frame_ms) regardless of wall-clock jitter, so simulation speed is stable.
//
// The "while" the lessons keep mentioning lives here:
//     while (running) { poll events; tick at fixed steps; }

#include "engine/loop.h"

#include <time.h>

static int64_t nowMs(void) {
    struct timespec ts;
    clock_gettime(CLOCK_MONOTONIC, &ts);
    return (int64_t)ts.tv_sec * 1000 + ts.tv_nsec / 1000000;
}

void Loop_run(Loop *loop) {
    if (!loop || !(*loop).tick) return;

    (*loop).running = true;
    int64_t accumulator = 0;
    int64_t prev = nowMs();

    while ((*loop).running) {
        int64_t now = nowMs();
        accumulator += now - prev;
        prev = now;

        while (accumulator >= (*loop).frame_ms) {
            (*loop).tick((*loop).userdata);
            accumulator -= (*loop).frame_ms;
        }
    }
}

void Loop_stop(Loop *loop) {
    if (loop) (*loop).running = false;
}