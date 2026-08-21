#ifndef INPUT_TOUCH_H
#define INPUT_TOUCH_H

#include <stdbool.h>
#include <stdint.h>

#include "event/touch.h"

// Window id carried by every queued event (0 = FOCUS_BROADCAST).
#define TOUCH_MAX_WINDOWS 8
#define TOUCH_MAX_WINDOW_LISTENERS 64

// input/touch.h — trackpad touch state + event stream (Legacy: input/Touch.java).
//
// Ten fixed touch slots (state + position/pressure), a packed 64-bit ring,
// and listener callbacks. The backend maps OS touch identities into slots
// (hash % TOUCH_MAX); all per-touch queries index those slots.
//
// Wire format (identical to legacy):
//   [63:18] micros since engine start | [17: 6] touch slot | [5:0] action

enum {
    TOUCH_DOWN   = 0,
    TOUCH_UP     = 1,
    TOUCH_MOVE   = 2,
    TOUCH_CANCEL = 3,
};

#define TOUCH_MAX 10

void Touch_init(void);
void Touch_shutdown(void);
void Touch_addListener(const TouchEvent *listener);
bool Touch_removeListener(const TouchEvent *listener);

// Window-scoped registration (see input/key.h for the routing rules).
void Touch_attachWindow(uint32_t windowId, const TouchEvent *listener);
bool Touch_detachWindow(uint32_t windowId, const TouchEvent *listener);
void Touch_detachWindowAll(uint32_t windowId);

// Producer: Thread 0 only. touchId is the slot (0..TOUCH_MAX-1); windowId
// tags the receiving window.
void Touch_pushTouchEvent(uint32_t windowId, int touchId, int action, double x, double y,
                          double pressure, uint64_t holdThresholdNanos);

// Consumer: drain the ring into the listeners. Game thread, once per frame.
void Touch_dispatchEvents(void);

// --- State queries ---
bool    Touch_isDown(int touchId);
uint64_t Touch_pressTime(int touchId);
uint64_t Touch_lastHoldDuration(int touchId);
int     Touch_taps(int touchId);
int     Touch_lastAction(int touchId);
double  Touch_x(int touchId);
double  Touch_y(int touchId);
double  Touch_pressure(int touchId);

#endif
