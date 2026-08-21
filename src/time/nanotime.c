// time/nanotime.c — the monotonic clock (Legacy: time/NanoTime.java).
//
// CLOCK_MONOTONIC_RAW is the raw hardware counter, unslewed by NTP — the
// closest thing to System.nanoTime(). The epoch is captured once; every
// consumer derives "time since engine start" from the same anchor so
// timestamps from different threads stay comparable.

#include "time/nanotime.h"

#include <time.h>

static uint64_t s_startNanos = 0;
static int s_started = 0;

static uint64_t readNanos(void) {
    struct timespec ts;
    clock_gettime(CLOCK_MONOTONIC_RAW, &ts);
    return (uint64_t)ts.tv_sec * 1000000000ULL + (uint64_t)ts.tv_nsec;
}

void NanoTime_init(void) {
    if (s_started) return;
    s_startNanos = readNanos();
    s_started = 1;
}

uint64_t NanoTime_now(void) {
    return readNanos();
}

uint64_t NanoTime_startNanos(void) {
    return s_startNanos;
}

uint64_t NanoTime_elapsedNanos(void) {
    if (!s_started) return 0;
    return readNanos() - s_startNanos;
}
