// input/focus.c — THE spotlight (one focused window per machine).
//
// One atomic word. Thread 0 writes after reading [NSApp keyWindow] during
// the pump; any thread reads lock-free. No AppKit calls may happen here —
// this module is the firewall between the game thread and the OS.

#include "input/focus.h"

#include <stdatomic.h>

static _Atomic uint32_t s_focusId = FOCUS_BROADCAST;

void Focus_set(uint32_t windowId) {
    atomic_store_explicit(&s_focusId, windowId, memory_order_release);
}

uint32_t Focus_id(void) {
    return atomic_load_explicit(&s_focusId, memory_order_acquire);
}

bool Focus_isFocused(uint32_t windowId) {
    return Focus_id() == windowId;
}
