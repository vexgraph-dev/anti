#ifndef TIME_NANOTIME_H
#define TIME_NANOTIME_H

#include <stdint.h>

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

#endif
