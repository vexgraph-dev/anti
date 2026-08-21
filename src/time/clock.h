#ifndef TIME_CLOCK_H
#define TIME_CLOCK_H

#include <stdbool.h>
#include <stdint.h>

// time/clock.h — the virtual clock (Legacy: time/Clock.java).
//
// Tracks game/simulation time separately from real time: real deltas are
// accumulated into a virtual timeline through a dynamic timeScale, and can
// be frozen entirely by pausing. The Loop keeps running while the world's
// clock stands still.
//
// Divergence from legacy, flagged for audit: the real side reads the
// MONOTONIC clock instead of wall-clock millis, so NTP adjustments can never
// make virtual time jump backwards mid-session.

typedef struct Clock {
    double timeScale;             // default 1.0
    uint64_t baseRealMillis;      // wall anchor at create/reset (informational)
    uint64_t lastTickRealMillis;  // previous tick's real reading
    uint64_t virtualTimeMillis;   // accumulated scaled time
    bool paused;                  // frozen when true
} Clock;

// New clock: scale 1.0, unpaused, virtual time zeroed at now.
Clock Clock_create(void);

// Advance: reads real elapsed since last tick and adds it scaled to the
// virtual timeline. While paused, the real reading still advances (so no
// giant jump lands on resume) but nothing accrues.
void Clock_tick(Clock *clock);

void Clock_setTimeScale(Clock *clock, double scale);
double Clock_timeScale(const Clock *clock);

void Clock_setPaused(Clock *clock, bool paused);
bool Clock_isPaused(const Clock *clock);

// Virtual milliseconds accrued since create/reset.
uint64_t Clock_virtualTimeMillis(const Clock *clock);

// Zero the virtual timeline and re-anchor the real side at now.
void Clock_reset(Clock *clock);

#endif
