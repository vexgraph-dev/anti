// input/mouse.c — mouse buttons, position, and event stream
// (Legacy: input/Mouse.java port).
//
// Wire format is byte-for-byte the legacy packing; the dispatcher classifies
// by marker byte FIRST (bits [15:8]) so button payloads can never collide
// with the motion markers (legacy read the low action nibble too late and
// silently dropped right-down / middle-up — fixed here):
//   marker 255 => move(5) / delta(9) / zoom(8)   marker 254 => scroll(6)
//   action8 == 7 => drag (marker holds the real button)
//   otherwise    => button event (action = packed & 3)
//
// Position is written on dispatch (move/drag), so Mouse_x()/Mouse_y() are
// live polls instead of the dead getters the legacy shipped.

#include "input/mouse.h"

#include <string.h>

#include "input/focus.h"
#include "input/key.h"
#include "atomic/ring.h"
#include "time/nanotime.h"
#include "annotation/overview.h"

;;OVERVIEW
/**
 * ============================================================================
 * MODULE: Mouse (input/mouse.c)
 * LEVEL: L2 — Behavior (input behavior API)
 * ============================================================================
 * mouse buttons, position, and event stream
 *
 * FUNCTION REGISTRY:
 * ----------------------------------------------------------------------------
 * Constructors:
 *   - Mouse_init(void)
 *
 * Core Functions:
 *   - Mouse_shutdown(void)
 *   - Mouse_addListener(listener)
 *   - Mouse_removeListener(listener)
 *   - Mouse_attachWindow(windowId, listener)
 *   - Mouse_detachWindow(windowId, listener)
 *   - Mouse_detachWindowAll(windowId)
 *   - Mouse_pushButtonEvent(windowId, button, action, holdThresholdNanos)
 *   - Mouse_pushMoveEvent(windowId, x, y)
 *   - Mouse_pushMoveDeltaEvent(windowId, dx, dy)
 *   - Mouse_pushDragEvent(windowId, button, x, y)
 *   - Mouse_pushScrollEvent(windowId, dx, dy)
 *   - Mouse_pushZoomEvent(windowId, magnification)
 *   - Mouse_dispatchEvents(void)
 *   - Mouse_pressTime(button)
 *   - Mouse_lastReleaseTime(button)
 *   - Mouse_lastHoldDurationNanos(button)
 *   - Mouse_currentHoldDurationNanos(button)
 *   - Mouse_taps(button)
 *   - Mouse_x(void)
 *   - Mouse_y(void)
 *   - Mouse_button(mouseEvent)
 *   - Mouse_name(button)
 *
 * Getters:
 *   - Mouse_isDown(button)
 *   - Mouse_hasShift(mouseEvent)
 *   - Mouse_hasControl(mouseEvent)
 *   - Mouse_hasOption(mouseEvent)
 *   - Mouse_hasCommand(mouseEvent)
 * ============================================================================
 */


#define BUTTON_COUNT 16
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
    uint64_t lastReleaseTime;
    int32_t taps;
    int32_t pad;
    uint64_t lastHoldDuration;
} ButtonSlot;

_Static_assert(sizeof(ButtonSlot) == 32, "button slot must stay 32 bytes");

static ButtonSlot s_slots[BUTTON_COUNT];
static double s_posX = 0.0;
static double s_posY = 0.0;
static RingBuffer s_queue;
static bool s_ready = false;

static const MouseEvent *s_listeners[64];
static int s_listenerCount = 0;

// Window-scoped listeners: slot index IS the window id (0 reserved).
static const MouseEvent *s_winListeners[MOUSE_MAX_WINDOWS][MOUSE_MAX_WINDOW_LISTENERS];
static int s_winCounts[MOUSE_MAX_WINDOWS];

void Mouse_init(void) {
    if (s_ready) return;
    NanoTime_init();
    if (!RingBuffer_init(&s_queue, sizeof(InputEvent), QUEUE_CAPACITY)) return;
    memset(s_slots, 0, sizeof(s_slots));
    s_ready = true;
}

void Mouse_shutdown(void) {
    if (!s_ready) return;
    RingBuffer_shutdown(&s_queue);
    s_ready = false;
}

void Mouse_addListener(const MouseEvent *listener) {
    if (!listener || s_listenerCount >= (int)(sizeof(s_listeners) / sizeof(s_listeners[0]))) return;
    s_listeners[s_listenerCount++] = listener;
}

bool Mouse_removeListener(const MouseEvent *listener) {
    for (int i = 0; i < s_listenerCount; i++) {
        if (s_listeners[i] == listener) {
            s_listeners[i] = s_listeners[--s_listenerCount];
            return true;
        }
    }
    return false;
}

void Mouse_attachWindow(uint32_t windowId, const MouseEvent *listener) {
    if (!listener || windowId == 0 || windowId >= MOUSE_MAX_WINDOWS) return;
    if (s_winCounts[windowId] >= MOUSE_MAX_WINDOW_LISTENERS) return;
    s_winListeners[windowId][s_winCounts[windowId]++] = listener;
}

bool Mouse_detachWindow(uint32_t windowId, const MouseEvent *listener) {
    if (!listener || windowId == 0 || windowId >= MOUSE_MAX_WINDOWS) return false;
    for (int i = 0; i < s_winCounts[windowId]; i++) {
        if (s_winListeners[windowId][i] == listener) {
            s_winListeners[windowId][i] = s_winListeners[windowId][--s_winCounts[windowId]];
            return true;
        }
    }
    return false;
}

void Mouse_detachWindowAll(uint32_t windowId) {
    if (windowId >= MOUSE_MAX_WINDOWS) return;
    s_winCounts[windowId] = 0;
}

static int modifierMask(void) {
    int mods = 0;
    if (Key_isDown(KEY_LEFT_SHIFT) || Key_isDown(KEY_RIGHT_SHIFT)) mods |= 1;
    if (Key_isDown(KEY_LEFT_CONTROL) || Key_isDown(KEY_RIGHT_CONTROL)) mods |= 2;
    if (Key_isDown(KEY_LEFT_ALT) || Key_isDown(KEY_RIGHT_ALT)) mods |= 4;
    if (Key_isDown(KEY_LEFT_SUPER) || Key_isDown(KEY_RIGHT_SUPER)) mods |= 8;
    return mods;
}

static uint64_t epochMicros(void) {
    return (NanoTime_elapsedNanos() / 1000ULL) & 0x3FFFFFFFFFFFULL;
}

void Mouse_pushButtonEvent(uint32_t windowId, int button, int action, uint64_t holdThresholdNanos) {
    if (button < 0 || button >= BUTTON_COUNT) return;
    if (!s_ready) Mouse_init();
    if (!s_ready) return;

    ButtonSlot *slot = &s_slots[button];
    uint64_t now = NanoTime_now();

    if (action == KEY_ACTION_DOWN) {
        if ((*slot).pressTime != 0) { // OS repeat
            InputEvent ev = { .packed = (epochMicros() << 18)
                                       | (((uint64_t)modifierMask() & 0xF) << 14)
                                       | (((uint64_t)button & 0xFFF) << 2)
                                       | KEY_ACTION_REPEAT,
                              .windowId = windowId, .pad = 0 };
            RingBuffer_push(&s_queue, &ev);
            return;
        }
        uint64_t lastRelease = (*slot).lastReleaseTime;
        if (lastRelease != 0 && (now - lastRelease) < holdThresholdNanos)
            (*slot).taps++;
        else
            (*slot).taps = 1;
        (*slot).pressTime = now;
    } else if (action == KEY_ACTION_UP) {
        if ((*slot).pressTime != 0)
            (*slot).lastHoldDuration = now - (*slot).pressTime;
        (*slot).lastReleaseTime = now;
        (*slot).pressTime = 0;
    }

    InputEvent ev = { .packed = (epochMicros() << 18)
                               | (((uint64_t)modifierMask() & 0xF) << 14)
                               | (((uint64_t)button & 0xFFF) << 2)
                               | ((uint64_t)action & 0x3),
                      .windowId = windowId, .pad = 0 };
    RingBuffer_push(&s_queue, &ev);
}

// Coordinates ride as int16 halves exactly like legacy (wrapping truncation).
static uint64_t packCoords(uint64_t head, double x, double y) {
    uint64_t packed = head;
    packed |= (((uint64_t)(uint16_t)(int16_t)(int)x) << 16);
    packed |= (((uint64_t)(uint16_t)(int16_t)(int)y) << 32);
    return packed;
}

static double coordA(uint64_t packed) {
    return (double)(int16_t)((packed >> 16) & 0xFFFF);
}

static double coordB(uint64_t packed) {
    return (double)(int16_t)((packed >> 32) & 0xFFFF);
}

void Mouse_pushMoveEvent(uint32_t windowId, double x, double y) {
    if (!s_ready) Mouse_init();
    if (!s_ready) return;
    InputEvent ev = { .packed = packCoords((255ULL << 8) | 5ULL, x, y), .windowId = windowId, .pad = 0 };
    RingBuffer_push(&s_queue, &ev);
}

void Mouse_pushMoveDeltaEvent(uint32_t windowId, double dx, double dy) {
    if (!s_ready) Mouse_init();
    if (!s_ready) return;
    InputEvent ev = { .packed = packCoords((255ULL << 8) | 9ULL, dx, dy), .windowId = windowId, .pad = 0 };
    RingBuffer_push(&s_queue, &ev);
}

void Mouse_pushDragEvent(uint32_t windowId, int button, double x, double y) {
    if (!s_ready) Mouse_init();
    if (!s_ready) return;
    InputEvent ev = { .packed = packCoords(((uint64_t)button << 8) | 7ULL, x, y), .windowId = windowId, .pad = 0 };
    RingBuffer_push(&s_queue, &ev);
}

void Mouse_pushScrollEvent(uint32_t windowId, double dx, double dy) {
    if (!s_ready) Mouse_init();
    if (!s_ready) return;

    // Scroll rides at 100x resolution inside an int16 per axis.
    double sx = dx * 100.0;
    double sy = dy * 100.0;
    if (sx > 32767.0) sx = 32767.0;
    if (sx < -32768.0) sx = -32768.0;
    if (sy > 32767.0) sy = 32767.0;
    if (sy < -32768.0) sy = -32768.0;

    uint64_t packed = (254ULL << 8) | 6ULL;
    packed |= ((uint64_t)(uint16_t)(int16_t)sx) << 16;
    packed |= ((uint64_t)(uint16_t)(int16_t)sy) << 32;
    InputEvent ev = { .packed = packed, .windowId = windowId, .pad = 0 };
    RingBuffer_push(&s_queue, &ev);
}

void Mouse_pushZoomEvent(uint32_t windowId, double magnification) {
    if (!s_ready) Mouse_init();
    if (!s_ready) return;

    float f = (float)magnification;
    uint32_t bits;
    memcpy(&bits, &f, sizeof(bits));

    uint64_t packed = (255ULL << 8) | 8ULL;
    packed |= ((uint64_t)bits & 0xFFFFFFFFULL) << 16;
    InputEvent ev = { .packed = packed, .windowId = windowId, .pad = 0 };
    RingBuffer_push(&s_queue, &ev);
}

// Deliver one motion-class event (move/delta/zoom/scroll/drag) to one
// window's scoped list.
static void deliverMotion(uint32_t windowId, int action8, int button,
                          double a, double b) {
    if (windowId == 0 || windowId >= MOUSE_MAX_WINDOWS) return;
    for (int i = 0; i < s_winCounts[windowId]; i++) {
        const MouseEvent *l = s_winListeners[windowId][i];
        void *self = (*l).self;
        if (action8 == 5 && (*l).onMouseMove)
            (*l).onMouseMove(self, a, b);
        else if (action8 == 9 && (*l).onMouseMoveDelta)
            (*l).onMouseMoveDelta(self, a, b);
        else if (action8 == 6 && (*l).onMouseScroll)
            (*l).onMouseScroll(self, a, b);
        else if (action8 == 7 && (*l).onMouseDrag)
            (*l).onMouseDrag(self, button, a, b);
    }
}

// Zoom rides the same routing but carries float bits instead of coords.
static void deliverZoom(uint32_t windowId, double magnification) {
    if (windowId == 0 || windowId >= MOUSE_MAX_WINDOWS) return;
    for (int i = 0; i < s_winCounts[windowId]; i++) {
        const MouseEvent *l = s_winListeners[windowId][i];
        if ((*l).onMouseZoom)
            (*l).onMouseZoom((*l).self, magnification);
    }
}

// Deliver one button-class event to one window's scoped list.
static void deliverButton(uint32_t windowId, int action, int mouseEvent, uint64_t exactNanos) {
    if (windowId == 0 || windowId >= MOUSE_MAX_WINDOWS) return;
    for (int i = 0; i < s_winCounts[windowId]; i++) {
        const MouseEvent *l = s_winListeners[windowId][i];
        void *self = (*l).self;
        if (action == KEY_ACTION_DOWN && (*l).onMouseDown)
            (*l).onMouseDown(self, mouseEvent, exactNanos);
        else if (action == KEY_ACTION_UP && (*l).onMouseUp)
            (*l).onMouseUp(self, mouseEvent, exactNanos);
        else if (action == KEY_ACTION_REPEAT && (*l).onMouseRepeat)
            (*l).onMouseRepeat(self, mouseEvent, exactNanos);
    }
}

void Mouse_dispatchEvents(void) {
    if (!s_ready) return;

    InputEvent ev;
    while (RingBuffer_pop(&s_queue, &ev)) {
        uint64_t packed = ev.packed;
        int action8 = (int)(packed & 0xFF);
        int marker = (int)((packed >> 8) & 0xFF);

        // --- Motion class: marker bytes 254/255 ---
        if (marker == 255) {
            if (action8 == 5) { // move
                s_posX = coordA(packed);
                s_posY = coordB(packed);
                for (int i = 0; i < s_listenerCount; i++)
                    if ((*s_listeners[i]).onMouseMove)
                        (*s_listeners[i]).onMouseMove((*s_listeners[i]).self, s_posX, s_posY);
                if (ev.windowId == FOCUS_BROADCAST) {
                    for (uint32_t w = 1; w < MOUSE_MAX_WINDOWS; w++)
                        deliverMotion(w, 5, 0, s_posX, s_posY);
                } else {
                    deliverMotion(ev.windowId, 5, 0, s_posX, s_posY);
                }
            } else if (action8 == 9) { // locked-cursor delta
                double dx = coordA(packed);
                double dy = coordB(packed);
                for (int i = 0; i < s_listenerCount; i++)
                    if ((*s_listeners[i]).onMouseMoveDelta)
                        (*s_listeners[i]).onMouseMoveDelta((*s_listeners[i]).self, dx, dy);
                if (ev.windowId == FOCUS_BROADCAST) {
                    for (uint32_t w = 1; w < MOUSE_MAX_WINDOWS; w++)
                        deliverMotion(w, 9, 0, dx, dy);
                } else {
                    deliverMotion(ev.windowId, 9, 0, dx, dy);
                }
            } else if (action8 == 8) { // pinch zoom
                uint32_t bits = (uint32_t)((packed >> 16) & 0xFFFFFFFFULL);
                float f;
                memcpy(&f, &bits, sizeof(f));
                double mag = (double)f;
                for (int i = 0; i < s_listenerCount; i++)
                    if ((*s_listeners[i]).onMouseZoom)
                        (*s_listeners[i]).onMouseZoom((*s_listeners[i]).self, mag);
                if (ev.windowId == FOCUS_BROADCAST) {
                    for (uint32_t w = 1; w < MOUSE_MAX_WINDOWS; w++)
                        deliverZoom(w, mag);
                } else {
                    deliverZoom(ev.windowId, mag);
                }
            }
            continue;
        }
        if (marker == 254 && action8 == 6) { // scroll
            double dx = coordA(packed) / 100.0;
            double dy = coordB(packed) / 100.0;
            for (int i = 0; i < s_listenerCount; i++)
                if ((*s_listeners[i]).onMouseScroll)
                    (*s_listeners[i]).onMouseScroll((*s_listeners[i]).self, dx, dy);
            if (ev.windowId == FOCUS_BROADCAST) {
                for (uint32_t w = 1; w < MOUSE_MAX_WINDOWS; w++)
                    deliverMotion(w, 6, 0, dx, dy);
            } else {
                deliverMotion(ev.windowId, 6, 0, dx, dy);
            }
            continue;
        }

        // --- Drag: real button in the marker byte, action 7 ---
        if (action8 == 7) {
            s_posX = coordA(packed);
            s_posY = coordB(packed);
            int button = marker;
            for (int i = 0; i < s_listenerCount; i++)
                if ((*s_listeners[i]).onMouseDrag)
                    (*s_listeners[i]).onMouseDrag((*s_listeners[i]).self, button, s_posX, s_posY);
            if (ev.windowId == FOCUS_BROADCAST) {
                for (uint32_t w = 1; w < MOUSE_MAX_WINDOWS; w++)
                    deliverMotion(w, 7, button, s_posX, s_posY);
            } else {
                deliverMotion(ev.windowId, 7, button, s_posX, s_posY);
            }
            continue;
        }

        // --- Button class: timestamped, modifier-tagged ---
        int action = (int)(packed & 0x3);
        int button = (int)((packed >> 2) & 0xFFF);
        int modifiers = (int)((packed >> 14) & 0xF);
        uint64_t exactNanos = NanoTime_startNanos()
                            + (((packed >> 18) & 0x3FFFFFFFFFFFULL) * 1000ULL);

        int mappedMods = 0;
        if ((modifiers & 1) != 0) mappedMods |= KEY_MOD_SHIFT;
        if ((modifiers & 2) != 0) mappedMods |= KEY_MOD_CONTROL;
        if ((modifiers & 4) != 0) mappedMods |= KEY_MOD_OPTION;
        if ((modifiers & 8) != 0) mappedMods |= KEY_MOD_COMMAND;
        int mouseEvent = button | mappedMods;

        for (int i = 0; i < s_listenerCount; i++) {
            void *self = (*s_listeners[i]).self;
            if (action == KEY_ACTION_DOWN && (*s_listeners[i]).onMouseDown)
                (*s_listeners[i]).onMouseDown(self, mouseEvent, exactNanos);
            else if (action == KEY_ACTION_UP && (*s_listeners[i]).onMouseUp)
                (*s_listeners[i]).onMouseUp(self, mouseEvent, exactNanos);
            else if (action == KEY_ACTION_REPEAT && (*s_listeners[i]).onMouseRepeat)
                (*s_listeners[i]).onMouseRepeat(self, mouseEvent, exactNanos);
        }
        if (ev.windowId == FOCUS_BROADCAST) {
            for (uint32_t w = 1; w < MOUSE_MAX_WINDOWS; w++)
                deliverButton(w, action, mouseEvent, exactNanos);
        } else {
            deliverButton(ev.windowId, action, mouseEvent, exactNanos);
        }
    }
}

bool Mouse_isDown(int button) {
    if (button < 0 || button >= BUTTON_COUNT) return false;
    return s_slots[button].pressTime != 0;
}

uint64_t Mouse_pressTime(int button) {
    if (button < 0 || button >= BUTTON_COUNT) return 0;
    return s_slots[button].pressTime;
}

uint64_t Mouse_lastReleaseTime(int button) {
    if (button < 0 || button >= BUTTON_COUNT) return 0;
    return s_slots[button].lastReleaseTime;
}

uint64_t Mouse_lastHoldDurationNanos(int button) {
    if (button < 0 || button >= BUTTON_COUNT) return 0;
    return s_slots[button].lastHoldDuration;
}

uint64_t Mouse_currentHoldDurationNanos(int button) {
    uint64_t p = Mouse_pressTime(button);
    return p == 0 ? 0 : NanoTime_now() - p;
}

int Mouse_taps(int button) {
    if (button < 0 || button >= BUTTON_COUNT) return 0;
    return s_slots[button].taps;
}

double Mouse_x(void) {
    return s_posX;
}

double Mouse_y(void) {
    return s_posY;
}

int Mouse_button(int mouseEvent) {
    return mouseEvent & 0xFFFF;
}

bool Mouse_hasShift(int mouseEvent) {
    return (mouseEvent & KEY_MOD_SHIFT) != 0;
}

bool Mouse_hasControl(int mouseEvent) {
    return (mouseEvent & KEY_MOD_CONTROL) != 0;
}

bool Mouse_hasOption(int mouseEvent) {
    return (mouseEvent & KEY_MOD_OPTION) != 0;
}

bool Mouse_hasCommand(int mouseEvent) {
    return (mouseEvent & KEY_MOD_COMMAND) != 0;
}

const char *Mouse_name(int button) {
    switch (button) {
        case MOUSE_LEFT: return "Left";
        case MOUSE_RIGHT: return "Right";
        case MOUSE_MIDDLE: return "Middle";
        default: return "Button";
    }
}
