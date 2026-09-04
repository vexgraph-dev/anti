// time/nanotime.c — the monotonic clock (Legacy: time/NanoTime.java).
//
// CLOCK_MONOTONIC_RAW is the raw hardware counter, unslewed by NTP — the
// closest thing to System.nanoTime(). The epoch is captured once; every
// consumer derives "time since engine start" from the same anchor so
// timestamps from different threads stay comparable.

#include "time/nanotime.h"

#include <time.h>
#include "annotation/overview.h"

;;OVERVIEW
/**
 * ============================================================================
 * MODULE: Nanotime (time/nanotime.c)
 * LEVEL: L2 — Behavior (time behavior API)
 * ============================================================================
 * the monotonic clock (Legacy: time/NanoTime.java).
 *
 * FUNCTION REGISTRY:
 * ----------------------------------------------------------------------------
 * Constructors:
 *   - NanoTime_init(void)
 *
 * Core Functions:
 *   - NanoTime_now(void)
 *   - NanoTime_startNanos(void)
 *   - NanoTime_elapsedNanos(void)
 *   - NanoTimer_reset(timer)
 *   - NanoTimer_tick(timer)
 *   - NanoTimer_tickWithClock(timer, clock)
 *   - NanoTimer_deltaTime(timer)
 *   - NanoTimer_totalTime(timer)
 *   - NanoTimer_deltaNanos(timer)
 *   - NanoTimer_elapsedNanosOf(timer)
 * ============================================================================
 */


// little conflicted.. would need this to be localthread variable to ensure thread safety
static uint64_t s_startNanos = 0;
static int s_started = 0;

static uint64_t nanoTime(void) {
    struct timespec ts;
    clock_gettime(CLOCK_MONOTONIC_RAW, &ts);
    return (uint64_t)ts.tv_sec * 1000000000ULL + (uint64_t)ts.tv_nsec;
}

void NanoTime_init(void) {
    if (s_started) return;
    s_startNanos = nanoTime();
    s_started = 1;
}

uint64_t NanoTime_startNanos(void) {
    return s_startNanos;
}

// Raw reading only — pure function of the clock, no shared state involved
// (the init-race concern lives in the epoch accessors below, not here).
uint64_t NanoTime_now(void) {
    return nanoTime();
}

uint64_t NanoTime_elapsedNanos(void) {
    if (!s_started)
        return 0;
    return nanoTime() - s_startNanos;
}

// --- Tickable timer (Legacy: NanoTime.allocate/tick) ---

void NanoTimer_reset(NanoTimer *timer) {
    uint64_t now = nanoTime();
    (*timer).startNanos = now;
    (*timer).lastNanos = now;
    (*timer).currentNanos = now;
    (*timer).deltaTime = 0.0;
    (*timer).totalTime = 0.0;
}

void NanoTimer_tickWithClock(NanoTimer *timer, const Clock *clock) {
    uint64_t now = nanoTime();
    uint64_t current = (*timer).currentNanos;

    (*timer).lastNanos = current;
    (*timer).currentNanos = now;

    double realDeltaSec = (double)(now - current) / 1000000000.0;

    double scale = 1.0;
    if (clock) {
        if (Clock_isPaused(clock))
            scale = 0.0;
        else
            scale = Clock_timeScale(clock);
    }

    double scaledDeltaSec = realDeltaSec * scale;
    (*timer).deltaTime = scaledDeltaSec;
    (*timer).totalTime += scaledDeltaSec;
}

void NanoTimer_tick(NanoTimer *timer) {
    NanoTimer_tickWithClock(timer, nullptr);
}

double NanoTimer_deltaTime(const NanoTimer *timer) {
    return (*timer).deltaTime;
}

double NanoTimer_totalTime(const NanoTimer *timer) {
    return (*timer).totalTime;
}

uint64_t NanoTimer_deltaNanos(const NanoTimer *timer) {
    return (*timer).currentNanos - (*timer).lastNanos;
}

uint64_t NanoTimer_elapsedNanosOf(const NanoTimer *timer) {
    return (*timer).currentNanos - (*timer).startNanos;
}
