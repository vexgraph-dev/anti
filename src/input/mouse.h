#ifndef INPUT_MOUSE_H
#define INPUT_MOUSE_H

#include <stdbool.h>
#include <stdint.h>

#include "event/mouse.h"

// Window id carried by every queued event (0 = FOCUS_BROADCAST).
#define MOUSE_MAX_WINDOWS 8
#define MOUSE_MAX_WINDOW_LISTENERS 64

// input/mouse.h — mouse buttons, position, and event stream
// (Legacy: input/Mouse.java).
//
// Same shape as key.h: a small off-heap state table (16 buttons), a packed
// 64-bit ring between Thread 0 producers and the game-thread dispatcher, and
// listener callbacks. Motion events carry coordinates instead of timestamps;
// button events carry timestamp + modifiers instead of coordinates.
//
// Button-event wire format (identical to legacy):
//   [63:18] micros since engine start | [17:14] modifiers
//   [13: 2] button | [1:0] action (0 up | 1 down | 2 repeat)
// Motion events use the legacy marker bytes in bits [15:8]:
//   move: 255/5, delta: 255/9, zoom: 255/8, scroll: 254/6 — coords/float in
//   bits [47:16]; drag: real button in [15:8] with action 7.

enum {
    MOUSE_LEFT     = 0,
    MOUSE_RIGHT    = 1,
    MOUSE_MIDDLE   = 2,
    MOUSE_BUTTON_4 = 3,
    MOUSE_BUTTON_5 = 4,
    MOUSE_BUTTON_6 = 5,
    MOUSE_BUTTON_7 = 6,
    MOUSE_BUTTON_8 = 7,
};

void Mouse_init(void);
void Mouse_shutdown(void);
void Mouse_addListener(const MouseEvent *listener);
bool Mouse_removeListener(const MouseEvent *listener);

// Window-scoped registration (see input/key.h for the routing rules).
void Mouse_attachWindow(uint32_t windowId, const MouseEvent *listener);
bool Mouse_detachWindow(uint32_t windowId, const MouseEvent *listener);
void Mouse_detachWindowAll(uint32_t windowId);

// Producers: Thread 0 only. windowId tags the receiving window.
void Mouse_pushButtonEvent(uint32_t windowId, int button, int action, uint64_t holdThresholdNanos);
void Mouse_pushMoveEvent(uint32_t windowId, double x, double y);
void Mouse_pushMoveDeltaEvent(uint32_t windowId, double dx, double dy);
void Mouse_pushDragEvent(uint32_t windowId, int button, double x, double y);
void Mouse_pushScrollEvent(uint32_t windowId, double dx, double dy);
void Mouse_pushZoomEvent(uint32_t windowId, double magnification);

// Consumer: drain the ring into the listeners. Game thread, once per frame.
void Mouse_dispatchEvents(void);

// --- State queries ---
bool     Mouse_isDown(int button);
uint64_t Mouse_pressTime(int button);
uint64_t Mouse_lastReleaseTime(int button);
uint64_t Mouse_lastHoldDurationNanos(int button);
uint64_t Mouse_currentHoldDurationNanos(int button);
int      Mouse_taps(int button);

// Last dispatched cursor position in content coordinates (top-left origin).
// Written on dispatch of move/drag events — legacy left these dead; here they
// are fed so cameras/UI can poll without a listener.
double Mouse_x(void);
double Mouse_y(void);

// --- Packed mouseEvent helpers ---
int  Mouse_button(int mouseEvent);
bool Mouse_hasShift(int mouseEvent);
bool Mouse_hasControl(int mouseEvent);
bool Mouse_hasOption(int mouseEvent);
bool Mouse_hasCommand(int mouseEvent);

const char *Mouse_name(int button);

#endif
