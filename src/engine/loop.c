// loop.c — EngineLoop port. The heart of the engine: a fixed-timestep
// loop that calls your tick() function. accumulator keeps a constant step
// (frame_ms) regardless of wall-clock jitter, so simulation speed is stable.
//
// The "while" the lessons keep mentioning lives here:
//     while (running) { poll events; tick at fixed steps; }

#include "engine/loop.h"

#include <time.h>
#include "annotation/overview.h"

;;OVERVIEW
/**
 * ============================================================================
 * MODULE: Loop (engine/loop.c)
 * ============================================================================
 * the engine loop API (Legacy: engine/EngineLoop.java).
 *
 * FUNCTION REGISTRY:
 * ----------------------------------------------------------------------------
 * Core Functions:
 *   - Loop_run(loop)
 *   - Loop_stop(loop)
 * ============================================================================
 */


static int64_t nowMs(void) {
    struct timespec ts;
    clock_gettime(CLOCK_MONOTONIC, &ts);
    return (int64_t) ts.tv_sec * 1000 + ts.tv_nsec / 1000000;
}

void Loop_run(Loop *loop) {
    if (!loop || !(*loop).tick)
        return;
    if ((*loop).frame_ms <= 0)
        return;

    atomic_store_explicit(&(*loop).running, true, memory_order_release);
    int64_t accumulator = 0;
    int64_t prev = nowMs();

    while (atomic_load_explicit(&(*loop).running, memory_order_acquire)) {
        int64_t now = nowMs();
        accumulator += now - prev;
        prev = now;

        // Clamp catch-up: at most 4 steps per frame, then drop backlog.
        // Prevents spiral-of-death after a stall and bounds frame time.
        int steps = 0;
        while (accumulator >= (*loop).frame_ms && steps < 4) {
            (*loop).tick((*loop).userdata);
            accumulator -= (*loop).frame_ms;
            steps++;
            if (!atomic_load_explicit(&(*loop).running, memory_order_acquire))
                break;
        }
        if (steps == 4)
            accumulator = 0;
    }
}

void Loop_stop(Loop *loop) {
    if (loop)
        atomic_store_explicit(&(*loop).running, false, memory_order_release);
}
