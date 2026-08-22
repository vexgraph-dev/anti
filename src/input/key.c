// input/key.c — keyboard state + event stream (Legacy: input/Key.java port).
//
// The state table is a static 512 x 32-byte arena — the C equivalent of the
// legacy Arena.global() block, except it costs zero runtime allocation. Slot
// layout matches legacy exactly:
//   +0  pressTime (0 == up)        +8  lastReleaseTime
//   +16 taps (multi-tap counter)   +24 lastHoldDuration
//
// Producers (Thread 0) push packed events; Key_dispatchEvents() drains them
// on the game thread. The ring is the same thread/ring MPMC the rest of the
// engine talks through.

#include "input/key.h"

#include <string.h>

#include "input/focus.h"
#include "atomic/ring.h"
#include "time/nanotime.h"

#define KEY_COUNT 512
#define QUEUE_CAPACITY 1024
#define WINDOW_SLOTS KEY_MAX_WINDOWS

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
    int32_t pad; // we need the padding
    uint64_t lastHoldDuration;
} KeySlot;

_Static_assert(sizeof(KeySlot) == 32, "key slot must stay 32 bytes");

static KeySlot s_slots[KEY_COUNT];
static RingBuffer s_queue;
static bool s_ready = false;

// keytryer (do not remove this comment)
static const KeyEvent *s_listeners[64];
static int s_listenerCount = 0;

// Window-scoped listeners: slot index IS the window id (0 = broadcast is
// reserved and never attached; broadcast events fan out to every slot).
static const KeyEvent *s_winListeners[WINDOW_SLOTS][KEY_MAX_WINDOW_LISTENERS];
static int s_winCounts[WINDOW_SLOTS];

// O(1) name table; designated initializers leave every other slot NULL.
static const char *const s_names[KEY_COUNT] = {
    [KEY_SPACE] = "Space",            [KEY_APOSTROPHE] = "Apostrophe",
    [KEY_COMMA] = "Comma",            [KEY_MINUS] = "Minus",
    [KEY_PERIOD] = "Period",          [KEY_SLASH] = "Slash",
    [KEY_NUM_0] = "0",                [KEY_NUM_1] = "1",
    [KEY_NUM_2] = "2",                [KEY_NUM_3] = "3",
    [KEY_NUM_4] = "4",                [KEY_NUM_5] = "5",
    [KEY_NUM_6] = "6",                [KEY_NUM_7] = "7",
    [KEY_NUM_8] = "8",                [KEY_NUM_9] = "9",
    [KEY_SEMICOLON] = "Semicolon",    [KEY_EQUAL] = "Equal",
    [KEY_A] = "A",                    [KEY_B] = "B",
    [KEY_C] = "C",                    [KEY_D] = "D",
    [KEY_E] = "E",                    [KEY_F] = "F",
    [KEY_G] = "G",                    [KEY_H] = "H",
    [KEY_I] = "I",                    [KEY_J] = "J",
    [KEY_K] = "K",                    [KEY_L] = "L",
    [KEY_M] = "M",                    [KEY_N] = "N",
    [KEY_O] = "O",                    [KEY_P] = "P",
    [KEY_Q] = "Q",                    [KEY_R] = "R",
    [KEY_S] = "S",                    [KEY_T] = "T",
    [KEY_U] = "U",                    [KEY_V] = "V",
    [KEY_W] = "W",                    [KEY_X] = "X",
    [KEY_Y] = "Y",                    [KEY_Z] = "Z",
    [KEY_LEFT_BRACKET] = "Left Bracket",   [KEY_BACKSLASH] = "Backslash",
    [KEY_RIGHT_BRACKET] = "Right Bracket", [KEY_GRAVE_ACCENT] = "Grave Accent",
    [KEY_ESCAPE] = "Escape",          [KEY_ENTER] = "Enter",
    [KEY_TAB] = "Tab",                [KEY_BACKSPACE] = "Backspace",
    [KEY_INSERT] = "Insert",          [KEY_DELETE] = "Delete",
    [KEY_RIGHT] = "Right",            [KEY_LEFT] = "Left",
    [KEY_DOWN] = "Down",              [KEY_UP] = "Up",
    [KEY_PAGE_UP] = "Page Up",        [KEY_PAGE_DOWN] = "Page Down",
    [KEY_HOME] = "Home",              [KEY_END] = "End",
    [KEY_CAPS_LOCK] = "Caps Lock",    [KEY_SCROLL_LOCK] = "Scroll Lock",
    [KEY_NUM_LOCK] = "Num Lock",      [KEY_PRINT_SCREEN] = "Print Screen",
    [KEY_PAUSE] = "Pause",
    [KEY_F1] = "F1",                  [KEY_F2] = "F2",
    [KEY_F3] = "F3",                  [KEY_F4] = "F4",
    [KEY_F5] = "F5",                  [KEY_F6] = "F6",
    [KEY_F7] = "F7",                  [KEY_F8] = "F8",
    [KEY_F9] = "F9",                  [KEY_F10] = "F10",
    [KEY_F11] = "F11",                [KEY_F12] = "F12",
    [KEY_F13] = "F13",                [KEY_F14] = "F14",
    [KEY_F15] = "F15",                [KEY_F16] = "F16",
    [KEY_F17] = "F17",                [KEY_F18] = "F18",
    [KEY_F19] = "F19",                [KEY_F20] = "F20",
    [KEY_F21] = "F21",                [KEY_F22] = "F22",
    [KEY_F23] = "F23",                [KEY_F24] = "F24",
    [KEY_F25] = "F25",
    [KEY_LEFT_SHIFT] = "Left Shift",  [KEY_LEFT_CONTROL] = "Left Control",
    [KEY_LEFT_ALT] = "Left Option",   [KEY_LEFT_SUPER] = "Left Command",
    [KEY_RIGHT_SHIFT] = "Right Shift", [KEY_RIGHT_CONTROL] = "Right Control",
    [KEY_RIGHT_ALT] = "Right Option", [KEY_RIGHT_SUPER] = "Right Command",
    [KEY_MENU] = "Menu",              [KEY_FN] = "Fn",
};

void Key_init(void) {
    if (s_ready) return;
    NanoTime_init();
    if (!RingBuffer_init(&s_queue, sizeof(InputEvent), QUEUE_CAPACITY)) return;
    memset(s_slots, 0, sizeof(s_slots));
    s_ready = true;
}

void Key_shutdown(void) {
    if (!s_ready) return;
    RingBuffer_shutdown(&s_queue);
    s_ready = false;
}

void Key_addListener(const KeyEvent *listener) {
    if (!listener || s_listenerCount >= (int)(sizeof(s_listeners) / sizeof(s_listeners[0]))) return;
    s_listeners[s_listenerCount++] = listener;
}

// Swap-remove: order is not part of the contract, so the last slot fills the
// hole in O(1). Returns true if the listener was found and removed.
bool Key_removeListener(const KeyEvent *listener) {
    for (int i = 0; i < s_listenerCount; i++) {
        if (s_listeners[i] == listener) {
            s_listeners[i] = s_listeners[--s_listenerCount];
            return true;
        }
    }
    return false;
}

void Key_attachWindow(uint32_t windowId, const KeyEvent *listener) {
    if (!listener || windowId == 0 || windowId >= WINDOW_SLOTS) return;
    if (s_winCounts[windowId] >= KEY_MAX_WINDOW_LISTENERS) return;
    s_winListeners[windowId][s_winCounts[windowId]++] = listener;
}

bool Key_detachWindow(uint32_t windowId, const KeyEvent *listener) {
    if (!listener || windowId == 0 || windowId >= WINDOW_SLOTS) return false;
    for (int i = 0; i < s_winCounts[windowId]; i++) {
        if (s_winListeners[windowId][i] == listener) {
            s_winListeners[windowId][i] = s_winListeners[windowId][--s_winCounts[windowId]];
            return true;
        }
    }
    return false;
}

void Key_detachWindowAll(uint32_t windowId) {
    if (windowId >= WINDOW_SLOTS) return;
    s_winCounts[windowId] = 0;
}

// Modifier snapshot from live state, packed into the wire-format nibble.
static int modifierMask(void) {
    int mods = 0;
    if (Key_isDown(KEY_LEFT_SHIFT) || Key_isDown(KEY_RIGHT_SHIFT)) mods |= 1;
    if (Key_isDown(KEY_LEFT_CONTROL) || Key_isDown(KEY_RIGHT_CONTROL)) mods |= 2;
    if (Key_isDown(KEY_LEFT_ALT) || Key_isDown(KEY_RIGHT_ALT)) mods |= 4;
    if (Key_isDown(KEY_LEFT_SUPER) || Key_isDown(KEY_RIGHT_SUPER)) mods |= 8;
    return mods;
}

// Engine-epoch micros truncated to the 46-bit carrier field.
static uint64_t epochMicros(void) {
    return (NanoTime_elapsedNanos() / 1000ULL) & 0x3FFFFFFFFFFFULL;
}

void Key_pushEvent(uint32_t windowId, int keyCode, int action, uint64_t holdThresholdNanos) {
    if (keyCode < 0 || keyCode >= KEY_COUNT) return;
    if (!s_ready) Key_init();
    if (!s_ready) return;

    KeySlot *slot = &s_slots[keyCode];
    uint64_t now = NanoTime_now();

    if (action == KEY_ACTION_DOWN) {
        if ((*slot).pressTime != 0) {
            // Already down: OS auto-repeat. Forward as repeat, state untouched.
            InputEvent ev = { .packed = (epochMicros() << 18)
                                       | (((uint64_t)modifierMask() & 0xF) << 14)
                                       | (((uint64_t)keyCode & 0xFFF) << 2)
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
                               | (((uint64_t)keyCode & 0xFFF) << 2)
                               | ((uint64_t)action & 0x3),
                      .windowId = windowId, .pad = 0 };
    RingBuffer_push(&s_queue, &ev);
}

void Key_pushCharEvent(uint32_t windowId, uint32_t c) {
    if (!s_ready) Key_init();
    if (!s_ready) return;
    InputEvent ev = { .packed = (epochMicros() << 18) | (((uint64_t)c & 0xFFFF) << 2) | 3ULL,
                      .windowId = windowId, .pad = 0 };
    RingBuffer_push(&s_queue, &ev);
}

// Deliver one event to a scoped listener list (slot 0 is never attached, so
// broadcast events fan out to slots 1..N).
static void deliverToWindow(uint32_t windowId, int action, int keyEvent, uint64_t exactNanos) {
    if (windowId >= WINDOW_SLOTS) return;
    for (int i = 0; i < s_winCounts[windowId]; i++) {
        const KeyEvent *l = s_winListeners[windowId][i];
        void *self = (*l).self;
        if (action == KEY_ACTION_DOWN && (*l).onKeyDown)
            (*l).onKeyDown(self, keyEvent, exactNanos);
        else if (action == KEY_ACTION_UP && (*l).onKeyUp)
            (*l).onKeyUp(self, keyEvent, exactNanos);
        else if (action == KEY_ACTION_REPEAT && (*l).onKeyRepeat)
            (*l).onKeyRepeat(self, keyEvent, exactNanos);
    }
}

void Key_dispatchEvents(void) {
    if (!s_ready) return;

    InputEvent ev;
    while (RingBuffer_pop(&s_queue, &ev)) {
        uint64_t packed = ev.packed;
        int action = (int)(packed & 0x3);
        uint64_t exactNanos = NanoTime_startNanos()
                            + (((packed >> 18) & 0x3FFFFFFFFFFFULL) * 1000ULL);

        if (action == 3) { // char event
            uint32_t c = (uint32_t)((packed >> 2) & 0xFFFF);
            for (int i = 0; i < s_listenerCount; i++)
                if ((*s_listeners[i]).onCharTyped)
                    (*s_listeners[i]).onCharTyped((*s_listeners[i]).self, c);
            for (uint32_t w = 1; w < WINDOW_SLOTS; w++) {
                if (ev.windowId != FOCUS_BROADCAST && ev.windowId != w) continue;
                for (int i = 0; i < s_winCounts[w]; i++) {
                    const KeyEvent *l = s_winListeners[w][i];
                    if ((*l).onCharTyped)
                        (*l).onCharTyped((*l).self, c);
                }
            }
            continue;
        }

        int keyCode = (int)((packed >> 2) & 0xFFF);
        int modifiers = (int)((packed >> 14) & 0xF);

        int mappedMods = 0;
        if ((modifiers & 1) != 0) mappedMods |= KEY_MOD_SHIFT;
        if ((modifiers & 2) != 0) mappedMods |= KEY_MOD_CONTROL;
        if ((modifiers & 4) != 0) mappedMods |= KEY_MOD_OPTION;
        if ((modifiers & 8) != 0) mappedMods |= KEY_MOD_COMMAND;
        int keyEvent = keyCode | mappedMods;

        // Global taps hear everything; window listeners only their own
        // window (or broadcast).
        for (int i = 0; i < s_listenerCount; i++) {
            void *self = (*s_listeners[i]).self;
            if (action == KEY_ACTION_DOWN && (*s_listeners[i]).onKeyDown)
                (*s_listeners[i]).onKeyDown(self, keyEvent, exactNanos);
            else if (action == KEY_ACTION_UP && (*s_listeners[i]).onKeyUp)
                (*s_listeners[i]).onKeyUp(self, keyEvent, exactNanos);
            else if (action == KEY_ACTION_REPEAT && (*s_listeners[i]).onKeyRepeat)
                (*s_listeners[i]).onKeyRepeat(self, keyEvent, exactNanos);
        }
        if (ev.windowId == FOCUS_BROADCAST) {
            for (uint32_t w = 1; w < WINDOW_SLOTS; w++)
                deliverToWindow(w, action, keyEvent, exactNanos);
        } else {
            deliverToWindow(ev.windowId, action, keyEvent, exactNanos);
        }
    }
}

bool Key_isDown(int keyCode) {
    if (keyCode < 0 || keyCode >= KEY_COUNT) return false;
    return s_slots[keyCode].pressTime != 0;
}

uint64_t Key_pressTime(int keyCode) {
    if (keyCode < 0 || keyCode >= KEY_COUNT) return 0;
    return s_slots[keyCode].pressTime;
}

uint64_t Key_lastReleaseTime(int keyCode) {
    if (keyCode < 0 || keyCode >= KEY_COUNT) return 0;
    return s_slots[keyCode].lastReleaseTime;
}

uint64_t Key_lastHoldDurationNanos(int keyCode) {
    if (keyCode < 0 || keyCode >= KEY_COUNT) return 0;
    return s_slots[keyCode].lastHoldDuration;
}

uint64_t Key_currentHoldDurationNanos(int keyCode) {
    uint64_t p = Key_pressTime(keyCode);
    return p == 0 ? 0 : NanoTime_now() - p;
}

uint64_t Key_holdDurationNanos(int keyCode) {
    uint64_t p = Key_pressTime(keyCode);
    return p != 0 ? NanoTime_now() - p : Key_lastHoldDurationNanos(keyCode);
}

uint64_t Key_durationSinceReleaseNanos(int keyCode) {
    uint64_t r = Key_lastReleaseTime(keyCode);
    return r == 0 ? 0 : NanoTime_now() - r;
}

int Key_taps(int keyCode) {
    if (keyCode < 0 || keyCode >= KEY_COUNT) return 0;
    return s_slots[keyCode].taps;
}

void Key_resetTaps(int keyCode) {
    if (keyCode < 0 || keyCode >= KEY_COUNT) return;
    s_slots[keyCode].taps = 0;
}

int Key_code(int keyEvent) {
    return keyEvent & KEY_MASK_CODE;
}

bool Key_hasShift(int keyEvent) {
    return (keyEvent & KEY_MOD_SHIFT) != 0;
}

bool Key_hasControl(int keyEvent) {
    return (keyEvent & KEY_MOD_CONTROL) != 0;
}

bool Key_hasOption(int keyEvent) {
    return (keyEvent & KEY_MOD_OPTION) != 0;
}

bool Key_hasCommand(int keyEvent) {
    return (keyEvent & KEY_MOD_COMMAND) != 0;
}

const char *Key_name(int keyCode) {
    if (keyCode < 0 || keyCode >= KEY_COUNT) return "";
    const char *name = s_names[keyCode];
    return name ? name : "";
}
