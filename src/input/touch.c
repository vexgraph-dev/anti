// input/touch.c — trackpad touch state + event stream (Legacy: input/Touch.java port).
//
// Ten fixed slots, each 32 bytes of state (press/release/hold/taps/action)
// plus 32 bytes of geometry (x/y/pressure). The backend resolves OS touch
// identities into slots before pushing; dispatch reconstructs timestamps and
// fans _out to listeners on the game thread.

#include "input/touch.h"

#include <string.h>

#include "input/focus.h"
#include "atomic/ring.h"
#include "time/nanotime.h"
#include "annotation/overview.h"

;;OVERVIEW
/**
 * ============================================================================
 * CLASS: Touch (input/touch.c)
 * LEVEL: L2 — Behavior (input behavior API)
 * ============================================================================
 * trackpad touch state + event stream (Legacy: input/Touch.java).
 *
 * STRUCT FIELDS (local to this file):
 * ----------------------------------------------------------------------------
 *   InputEvent {
 *     uint64_t packed;           // legacy 64-bit touch event packing
 *     uint32_t windowId;         // target window (0 = broadcast)
 *     uint32_t pad;              // alignment padding
 *   }
 *   TouchSlot {
 *     uint64_t pressTime;        // touchdown timestamp
 *     uint64_t lastHoldDuration; // active-contact duration
 *     uint64_t lastReleaseTime;  // lift timestamp
 *     int32_t taps;              // tap-count state
 *     int32_t action;            // current touch action
 *   }
 *   TouchPos {
 *     double x;                  // contact X position
 *     double y;                  // contact Y position
 *     double pressure;           // contact pressure
 *     uint64_t pad;              // alignment padding
 *   }
 *
 * FUNCTION REGISTRY:
 * ----------------------------------------------------------------------------
 * Constructors:
 *   - Touch_init(void)
 *
 * Core Functions:
 *   - Touch_shutdown(void)
 *   - Touch_addListener(listener)
 *   - Touch_removeListener(listener)
 *   - Touch_attachWindow(windowId, listener)
 *   - Touch_detachWindow(windowId, listener)
 *   - Touch_detachWindowAll(windowId)
 *   - Touch_pushTouchEvent(windowId, touchId, action, x, y, pressure, holdThresholdNanos)
 *   - Touch_dispatchEvents(void)
 *   - Touch_pressTime(touchId)
 *   - Touch_lastHoldDuration(touchId)
 *   - Touch_taps(touchId)
 *   - Touch_lastAction(touchId)
 *   - Touch_x(touchId)
 *   - Touch_y(touchId)
 *   - Touch_pressure(touchId)
 *
 * Getters:
 *   - Touch_isDown(touchId)
 * ============================================================================
 */


#define QUEUE_CAPACITY 1024

// One queued event: the legacy 64-bit packing plus the window it belongs to.
typedef struct {
    uint64_t packed;
    uint32_t windowId;
    uint32_t pad;
} InputEvent;

_Static_assert(sizeof(InputEvent) == 16, "input event must stay 16 bytes");

typedef struct {
    uint64_t pressTime;
    uint64_t lastHoldDuration;
    uint64_t lastReleaseTime;
    int32_t taps;
    int32_t action;
} TouchSlot;

_Static_assert(sizeof(TouchSlot) == 32, "touch slot must stay 32 bytes");

typedef struct {
    double x;
    double y;
    double pressure;
    uint64_t pad;
} TouchPos;

_Static_assert(sizeof(TouchPos) == 32, "touch pos must stay 32 bytes");

static TouchSlot s_slots[TOUCH_MAX];
static TouchPos s_pos[TOUCH_MAX];
static RingBuffer s_queue;
static bool s_ready = false;

static const TouchEvent *s_listeners[64];
static int s_listenerCount = 0;

// Window-scoped listeners: slot index IS the window id (0 reserved).
static const TouchEvent *s_winListeners[TOUCH_MAX_WINDOWS][TOUCH_MAX_WINDOW_LISTENERS];
static int s_winCounts[TOUCH_MAX_WINDOWS];

void Touch_init(void) {
    if (s_ready) return;
    NanoTime_init();
    if (!RingBuffer_init(&s_queue, sizeof(InputEvent), QUEUE_CAPACITY)) return;
    memset(s_slots, 0, sizeof(s_slots));
    memset(s_pos, 0, sizeof(s_pos));
    s_ready = true;
}

void Touch_shutdown(void) {
    if (!s_ready) return;
    RingBuffer_shutdown(&s_queue);
    s_ready = false;
}

void Touch_addListener(const TouchEvent *listener) {
    if (!listener || s_listenerCount >= (int)(sizeof(s_listeners) / sizeof(s_listeners[0]))) return;
    s_listeners[s_listenerCount++] = listener;
}

bool Touch_removeListener(const TouchEvent *listener) {
    for (int i = 0; i < s_listenerCount; i++) {
        if (s_listeners[i] == listener) {
            s_listeners[i] = s_listeners[--s_listenerCount];
            return true;
        }
    }
    return false;
}

void Touch_attachWindow(uint32_t windowId, const TouchEvent *listener) {
    if (!listener || windowId == 0 || windowId >= TOUCH_MAX_WINDOWS) return;
    if (s_winCounts[windowId] >= TOUCH_MAX_WINDOW_LISTENERS) return;
    s_winListeners[windowId][s_winCounts[windowId]++] = listener;
}

bool Touch_detachWindow(uint32_t windowId, const TouchEvent *listener) {
    if (!listener || windowId == 0 || windowId >= TOUCH_MAX_WINDOWS) return false;
    for (int i = 0; i < s_winCounts[windowId]; i++) {
        if (s_winListeners[windowId][i] == listener) {
            s_winListeners[windowId][i] = s_winListeners[windowId][--s_winCounts[windowId]];
            return true;
        }
    }
    return false;
}

void Touch_detachWindowAll(uint32_t windowId) {
    if (windowId >= TOUCH_MAX_WINDOWS) return;
    s_winCounts[windowId] = 0;
}

void Touch_pushTouchEvent(uint32_t windowId, int touchId, int action, double x, double y,
                          double pressure, uint64_t holdThresholdNanos) {
    if (touchId < 0 || touchId >= TOUCH_MAX) return;
    if (!s_ready) Touch_init();
    if (!s_ready) return;

    TouchSlot *slot = &s_slots[touchId];
    TouchPos *pos = &s_pos[touchId];
    uint64_t now = NanoTime_now();

    (*pos).x = x;
    (*pos).y = y;
    (*pos).pressure = pressure;

    if (action == TOUCH_DOWN) {
        uint64_t lastRelease = (*slot).lastReleaseTime;
        if (lastRelease != 0 && (now - lastRelease) < holdThresholdNanos)
            (*slot).taps++;
        else
            (*slot).taps = 1;
        (*slot).pressTime = now;
    } else if (action == TOUCH_UP) {
        if ((*slot).pressTime != 0)
            (*slot).lastHoldDuration = now - (*slot).pressTime;
        (*slot).lastReleaseTime = now;
        (*slot).pressTime = 0;
    } else if (action == TOUCH_CANCEL) {
        (*slot).pressTime = 0;
    }
    (*slot).action = action;

    uint64_t micros = (NanoTime_elapsedNanos() / 1000ULL) & 0x3FFFFFFFFFFFULL;
    InputEvent ev = { .packed = (micros << 18)
                              | (((uint64_t)touchId & 0xFFF) << 6)
                              | ((uint64_t)action & 0x3F),
                      .windowId = windowId, .pad = 0 };
    RingBuffer_push(&s_queue, &ev);
}

// Deliver one touch event to one window's scoped list.
static void deliverTouch(uint32_t windowId, int action, int touchId,
                         double x, double y, double pressure, uint64_t exactNanos) {
    if (windowId == 0 || windowId >= TOUCH_MAX_WINDOWS) return;
    for (int i = 0; i < s_winCounts[windowId]; i++) {
        const TouchEvent *l = s_winListeners[windowId][i];
        void *self = (*l).self;
        if (action == TOUCH_DOWN && (*l).onTouchDown)
            (*l).onTouchDown(self, touchId, x, y, pressure, exactNanos);
        else if (action == TOUCH_UP && (*l).onTouchUp)
            (*l).onTouchUp(self, touchId, x, y, pressure, exactNanos);
        else if (action == TOUCH_MOVE && (*l).onTouchMove)
            (*l).onTouchMove(self, touchId, x, y, pressure, exactNanos);
        else if (action == TOUCH_CANCEL && (*l).onTouchCancel)
            (*l).onTouchCancel(self, touchId, exactNanos);
    }
}

void Touch_dispatchEvents(void) {
    if (!s_ready) return;

    InputEvent ev;
    while (RingBuffer_pop(&s_queue, &ev)) {
        uint64_t packed = ev.packed;
        uint64_t micros = (packed >> 18) & 0x3FFFFFFFFFFFULL;
        uint64_t exactNanos = NanoTime_startNanos() + (micros * 1000ULL);
        int touchId = (int)((packed >> 6) & 0xFFF);
        int action = (int)(packed & 0x3F);

        const TouchPos *pos = &s_pos[touchId];
        double x = (*pos).x;
        double y = (*pos).y;
        double pressure = (*pos).pressure;

        for (int i = 0; i < s_listenerCount; i++) {
            void *self = (*s_listeners[i]).self;
            if (action == TOUCH_DOWN && (*s_listeners[i]).onTouchDown)
                (*s_listeners[i]).onTouchDown(self, touchId, x, y, pressure, exactNanos);
            else if (action == TOUCH_UP && (*s_listeners[i]).onTouchUp)
                (*s_listeners[i]).onTouchUp(self, touchId, x, y, pressure, exactNanos);
            else if (action == TOUCH_MOVE && (*s_listeners[i]).onTouchMove)
                (*s_listeners[i]).onTouchMove(self, touchId, x, y, pressure, exactNanos);
            else if (action == TOUCH_CANCEL && (*s_listeners[i]).onTouchCancel)
                (*s_listeners[i]).onTouchCancel(self, touchId, exactNanos);
        }
        if (ev.windowId == FOCUS_BROADCAST) {
            for (uint32_t w = 1; w < TOUCH_MAX_WINDOWS; w++)
                deliverTouch(w, action, touchId, x, y, pressure, exactNanos);
        } else {
            deliverTouch(ev.windowId, action, touchId, x, y, pressure, exactNanos);
        }
    }
}

bool Touch_isDown(int touchId) {
    if (touchId < 0 || touchId >= TOUCH_MAX) return false;
    return s_slots[touchId].pressTime != 0;
}

uint64_t Touch_pressTime(int touchId) {
    if (touchId < 0 || touchId >= TOUCH_MAX) return 0;
    return s_slots[touchId].pressTime;
}

uint64_t Touch_lastHoldDuration(int touchId) {
    if (touchId < 0 || touchId >= TOUCH_MAX) return 0;
    return s_slots[touchId].lastHoldDuration;
}

int Touch_taps(int touchId) {
    if (touchId < 0 || touchId >= TOUCH_MAX) return 0;
    return s_slots[touchId].taps;
}

int Touch_lastAction(int touchId) {
    if (touchId < 0 || touchId >= TOUCH_MAX) return TOUCH_CANCEL;
    return s_slots[touchId].action;
}

double Touch_x(int touchId) {
    if (touchId < 0 || touchId >= TOUCH_MAX) return 0.0;
    return s_pos[touchId].x;
}

double Touch_y(int touchId) {
    if (touchId < 0 || touchId >= TOUCH_MAX) return 0.0;
    return s_pos[touchId].y;
}

double Touch_pressure(int touchId) {
    if (touchId < 0 || touchId >= TOUCH_MAX) return 0.0;
    return s_pos[touchId].pressure;
}
