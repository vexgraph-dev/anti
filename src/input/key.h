#ifndef INPUT_KEY_H
#define INPUT_KEY_H

#include <stdbool.h>
#include <stdint.h>

#include "event/key.h"

// Window id carried by every queued event (0 = FOCUS_BROADCAST, no window).
// Events only exist on the window the OS delivered them to; dispatch routes
// on this tag, read at delivery time — never stale.
#define KEY_MAX_WINDOWS 8
#define KEY_MAX_WINDOW_LISTENERS 64

// input/key.h — keyboard state + event stream (Legacy: input/Key.java).
//
// GLFW-style cross-platform key codes, a 512-slot off-heap state table
// (press/release/hold/taps per key), and a packed 64-bit event ring between
// the producer (Thread 0, the AppKit pump) and the consumer (the game thread,
// via Key_dispatchEvents()).
//
// Event wire format (one uint64_t, identical to legacy):
//   [63:18] micros since engine start (46 bits, ~2.2 years wrap)
//   [17:14] modifier mask (1 shift | 2 ctrl | 4 alt/option | 8 cmd/super)
//   [13: 2] key code (12 bits)
//    [1: 0] action (0 up | 1 down | 2 repeat)
// Char events reuse the same carrier:
//   [63:18] micros | [17: 2] UTF-16 unit | [1:0] = 3

// --- GLFW-style standard cross-platform key codes ---
enum {
    KEY_SPACE         = 32,
    KEY_APOSTROPHE    = 39,
    KEY_COMMA         = 44,
    KEY_MINUS         = 45,
    KEY_PERIOD        = 46,
    KEY_SLASH         = 47,
    KEY_NUM_0         = 48,
    KEY_NUM_1         = 49,
    KEY_NUM_2         = 50,
    KEY_NUM_3         = 51,
    KEY_NUM_4         = 52,
    KEY_NUM_5         = 53,
    KEY_NUM_6         = 54,
    KEY_NUM_7         = 55,
    KEY_NUM_8         = 56,
    KEY_NUM_9         = 57,
    KEY_SEMICOLON     = 59,
    KEY_EQUAL         = 61,
    KEY_A             = 65,
    KEY_B             = 66,
    KEY_C             = 67,
    KEY_D             = 68,
    KEY_E             = 69,
    KEY_F             = 70,
    KEY_G             = 71,
    KEY_H             = 72,
    KEY_I             = 73,
    KEY_J             = 74,
    KEY_K             = 75,
    KEY_L             = 76,
    KEY_M             = 77,
    KEY_N             = 78,
    KEY_O             = 79,
    KEY_P             = 80,
    KEY_Q             = 81,
    KEY_R             = 82,
    KEY_S             = 83,
    KEY_T             = 84,
    KEY_U             = 85,
    KEY_V             = 86,
    KEY_W             = 87,
    KEY_X             = 88,
    KEY_Y             = 89,
    KEY_Z             = 90,
    KEY_LEFT_BRACKET  = 91,
    KEY_BACKSLASH     = 92,
    KEY_RIGHT_BRACKET = 93,
    KEY_GRAVE_ACCENT  = 96,

    KEY_ESCAPE        = 256,
    KEY_ENTER         = 257,
    KEY_TAB           = 258,
    KEY_BACKSPACE     = 259,
    KEY_INSERT        = 260,
    KEY_DELETE        = 261,
    KEY_RIGHT         = 262,
    KEY_LEFT          = 263,
    KEY_DOWN          = 264,
    KEY_UP            = 265,
    KEY_PAGE_UP       = 266,
    KEY_PAGE_DOWN     = 267,
    KEY_HOME          = 268,
    KEY_END           = 269,
    KEY_CAPS_LOCK     = 280,
    KEY_SCROLL_LOCK   = 281,
    KEY_NUM_LOCK      = 282,
    KEY_PRINT_SCREEN  = 283,
    KEY_PAUSE         = 284,

    KEY_F1            = 290,
    KEY_F2            = 291,
    KEY_F3            = 292,
    KEY_F4            = 293,
    KEY_F5            = 294,
    KEY_F6            = 295,
    KEY_F7            = 296,
    KEY_F8            = 297,
    KEY_F9            = 298,
    KEY_F10           = 299,
    KEY_F11           = 300,
    KEY_F12           = 301,
    KEY_F13           = 302,
    KEY_F14           = 303,
    KEY_F15           = 304,
    KEY_F16           = 305,
    KEY_F17           = 306,
    KEY_F18           = 307,
    KEY_F19           = 308,
    KEY_F20           = 309,
    KEY_F21           = 311,
    KEY_F22           = 312,
    KEY_F23           = 313,
    KEY_F24           = 314,
    KEY_F25           = 315,

    KEY_LEFT_SHIFT    = 340,
    KEY_LEFT_CONTROL  = 341,
    KEY_LEFT_ALT      = 342,
    KEY_LEFT_SUPER    = 343,
    KEY_RIGHT_SHIFT   = 344,
    KEY_RIGHT_CONTROL = 345,
    KEY_RIGHT_ALT     = 346,
    KEY_RIGHT_SUPER   = 347,
    KEY_MENU          = 348,
    KEY_FN            = 349, // Mac Fn / Globe key
};

// Platform-exclusive aliases (Legacy @PlatformExclusive).
#define KEY_MAC_COMMAND  KEY_LEFT_SUPER
#define KEY_MAC_OPTION   KEY_LEFT_ALT
#define KEY_MAC_CONTROL  KEY_LEFT_CONTROL
#define KEY_MAC_FN       KEY_FN

// --- Packed-event modifier bits and the listener-facing event flags ---
#define KEY_MOD_SHIFT    0x01000000
#define KEY_MOD_CONTROL  0x02000000 // Mac: control
#define KEY_MOD_OPTION   0x04000000 // Mac: option (Win/Linux: alt)
#define KEY_MOD_COMMAND  0x08000000 // Mac: command (Win: windows, Linux: super)
#define KEY_MASK_CODE    0x0000FFFF

// Actions pushed through Key_pushEvent.
#define KEY_ACTION_UP     0
#define KEY_ACTION_DOWN   1
#define KEY_ACTION_REPEAT 2

// Bootstrap the state table + event ring. Idempotent; called automatically on
// first push if the backend forgot.
void Key_init(void);
void Key_shutdown(void);

// Register a listener (fixed slots, silently dropped when full — legacy parity).
void Key_addListener(const KeyEvent *listener);
bool Key_removeListener(const KeyEvent *listener);

// Window-scoped registration: the listener only receives events whose
// windowId tag matches (broadcast-tagged events reach every window).
void Key_attachWindow(uint32_t windowId, const KeyEvent *listener);
bool Key_detachWindow(uint32_t windowId, const KeyEvent *listener);
void Key_detachWindowAll(uint32_t windowId);

// Producer: Thread 0 only. Updates the state table (with multi-tap counting
// inside holdThresholdNanos) and offers one packed event to the ring. An
// already-down key arriving as "down" is an OS repeat and is forwarded as
// KEY_ACTION_REPEAT without touching tap counts. windowId tags which window
// the OS delivered the event to.
void Key_pushEvent(uint32_t windowId, int keyCode, int action, uint64_t holdThresholdNanos);

// Producer: text input, fired alongside down events that carry a character.
void Key_pushCharEvent(uint32_t windowId, uint32_t c);

// Consumer: drain the ring into the listeners. Game thread, once per frame.
// Global taps get every event; window-scoped listeners only get events
// tagged with their window (or broadcast).
void Key_dispatchEvents(void);

// --- State queries (lock-free reads of the state table) ---
bool     Key_isDown(int keyCode);
uint64_t Key_pressTime(int keyCode);
uint64_t Key_lastReleaseTime(int keyCode);
uint64_t Key_lastHoldDurationNanos(int keyCode);
uint64_t Key_currentHoldDurationNanos(int keyCode);
uint64_t Key_holdDurationNanos(int keyCode); // current if held, else last
uint64_t Key_durationSinceReleaseNanos(int keyCode);
int      Key_taps(int keyCode);
void     Key_resetTaps(int keyCode);

// --- Packed keyEvent helpers ---
int  Key_code(int keyEvent);
bool Key_hasShift(int keyEvent);
bool Key_hasControl(int keyEvent);
bool Key_hasOption(int keyEvent);
bool Key_hasCommand(int keyEvent);

// O(1) name lookup ("Space", "Left Shift", ...). "" for unknown codes.
const char *Key_name(int keyCode);

#endif
