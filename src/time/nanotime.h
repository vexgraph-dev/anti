#ifndef TIME_NANOTIME_H
#define TIME_NANOTIME_H

#include <stdbool.h>
#include <stdint.h>

#include "time/clock.h"

// time/nanotime.h — the monotonic clock (Legacy: time/NanoTime.java).
//
// One process-wide engine epoch plus a raw monotonic reader. Everything that
// timestamps (input events, the Loop, telemetry) goes through here so all
// nanos are comparable. The legacy called clock_gettime_nsec_np through FFM;
// in C we just call clock_gettime(CLOCK_MONOTONIC_RAW) directly.
//
// Not thread-safe until NanoTime_init() has returned once (Thread 0 bootstraps
// it before any other thread exists — same assumption the Java static init made).

// Capture the engine epoch. Idempotent: later calls are no-ops.
void NanoTime_init(void);

// Raw monotonic nanos (unrelated to the epoch; never goes backwards).
uint64_t NanoTime_now(void);

// The engine epoch captured at init (0 before init).
uint64_t NanoTime_startNanos(void);

// Nanos since the engine epoch (0 before init).
uint64_t NanoTime_elapsedNanos(void);

// --- Tickable timer (Legacy: NanoTime.allocate/tick) ---
//
// A frame-timer accumulator: tick() rolls current into last, computes the
// real delta, and (optionally) scales it through a Clock so pause/slow-mo
// shape deltaTime and totalTime exactly like the simulation experienced them.

typedef struct NanoTimer {
    uint64_t startNanos;
    uint64_t lastNanos;
    uint64_t currentNanos;
    double deltaTime;  // seconds, Clock-scaled
    double totalTime;  // seconds, accumulated scaled
} NanoTimer;

// Anchor all fields at now; deltas zero.
void NanoTimer_reset(NanoTimer *timer);

// Roll one frame. With a clock, delta is multiplied by its timeScale and
// freezes while paused (real reading still advances — no resume jump).
void NanoTimer_tick(NanoTimer *timer);
void NanoTimer_tickWithClock(NanoTimer *timer, const Clock *clock);

double NanoTimer_deltaTime(const NanoTimer *timer);
double NanoTimer_totalTime(const NanoTimer *timer);
uint64_t NanoTimer_deltaNanos(const NanoTimer *timer);
uint64_t NanoTimer_elapsedNanosOf(const NanoTimer *timer); // since start

#endif
