#ifndef ENGINE_LOOP_H
#define ENGINE_LOOP_H

#include <stdbool.h>
#include <stdint.h>

// engine/loop.h — the engine loop API (Legacy: engine/EngineLoop.java).
//
// The loop is a fixed-timestep accumulator: your tick() runs exactly every
// frame_ms of simulated time, no matter how fast or slow the wall clock is.
// Call poll (events) + anti_loop_stop from the tick to end the run.

// A per-frame callback. userdata is whatever you passed in the loop struct.
typedef void (*anti_tick_fn)(void *userdata);

typedef struct anti_loop {
    anti_tick_fn tick;      // called once per fixed step (never NULL)
    void *userdata;         // opaque context handed to tick
    int64_t frame_ms;       // fixed timestep in milliseconds
    bool running;           // anti_loop_stop flips this to end the loop
} anti_loop_t;

// Run until anti_loop_stop is called (usually from inside the tick).
void anti_loop_run(anti_loop_t *loop);

// Request the loop to stop at the next boundary.
void anti_loop_stop(anti_loop_t *loop);

#endif