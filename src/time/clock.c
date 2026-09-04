// time/clock.c — the virtual clock (Legacy: time/Clock.java port).
//
// tick() is the whole idea: elapsedReal * timeScale accrues into the virtual
// timeline, and pause freezes accrual while still advancing the real reading
// — so unpausing never dumps a backlog of phantom milliseconds into the
// simulation.

#include "time/clock.h"

#include <time.h>
#include "annotation/overview.h"

;;OVERVIEW
/**
 * ============================================================================
 * MODULE: Clock (time/clock.c)
 * LEVEL: L2 — Behavior (time behavior API)
 * ============================================================================
 * the virtual clock (Legacy: time/Clock.java).
 *
 * FUNCTION REGISTRY:
 * ----------------------------------------------------------------------------
 * Constructors:
 *   - Clock_create(void)
 *
 * Core Functions:
 *   - Clock_tick(clock)
 *   - Clock_timeScale(clock)
 *   - Clock_virtualTimeMillis(clock)
 *   - Clock_reset(clock)
 *
 * Setters:
 *   - Clock_setTimeScale(clock, scale)
 *   - Clock_setPaused(clock, paused)
 *
 * Getters:
 *   - Clock_isPaused(clock)
 * ============================================================================
 */


// Wall-clock millis (legacy used System.currentTimeMillis(); kept for the
// informational base anchor).
static uint64_t currentTimeMillis(void) {
    struct timespec ts;
    clock_gettime(CLOCK_REALTIME, &ts);
    return (uint64_t)ts.tv_sec * 1000ULL + (uint64_t)ts.tv_nsec / 1000000ULL;
}

// Monotonic millis for the accrual side.
static uint64_t monoMillis(void) {
    struct timespec ts;
    clock_gettime(CLOCK_MONOTONIC_RAW, &ts);
    return (uint64_t)ts.tv_sec * 1000ULL + (uint64_t)ts.tv_nsec / 1000000ULL;
}

Clock Clock_create(void) {
    Clock c;
    c.timeScale = 1.0;
    c.baseRealMillis = currentTimeMillis();
    c.lastTickRealMillis = monoMillis();
    c.virtualTimeMillis = 0;
    c.paused = false;
    return c;
}

void Clock_tick(Clock *clock) {
    uint64_t now = monoMillis();
    uint64_t lastReal = (*clock).lastTickRealMillis;
    uint64_t elapsedReal = now - lastReal;
    (*clock).lastTickRealMillis = now;

    if (!(*clock).paused)
        (*clock).virtualTimeMillis += (uint64_t)((double)elapsedReal * (*clock).timeScale);
}

void Clock_setTimeScale(Clock *clock, double scale) {
    (*clock).timeScale = scale;
}

double Clock_timeScale(const Clock *clock) {
    return (*clock).timeScale;
}

void Clock_setPaused(Clock *clock, bool paused) {
    (*clock).paused = paused;
}

bool Clock_isPaused(const Clock *clock) {
    return (*clock).paused;
}

uint64_t Clock_virtualTimeMillis(const Clock *clock) {
    return (*clock).virtualTimeMillis;
}

void Clock_reset(Clock *clock) {
    (*clock).baseRealMillis = currentTimeMillis();
    (*clock).lastTickRealMillis = monoMillis();
    (*clock).virtualTimeMillis = 0;
}
