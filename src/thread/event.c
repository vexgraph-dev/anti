#include "thread/event.h"

#include "input/key.h"
#include "input/mouse.h"
#include "input/touch.h"
#include "oop/type.h"
#include "annotation/overview.h"

;;OVERVIEW
/**
 * ============================================================================
 * MODULE: Event (thread/event.c)
 * LEVEL: L2 — Behavior (worker-thread behavior API)
 * ============================================================================
 * the event dispatcher worker (Legacy: EventThread.java).
 *
 * FUNCTION REGISTRY:
 * ----------------------------------------------------------------------------
 * Core Functions:
 *   - EventThread_invoke(void)
 *   - EventThread_submit(w, packet)
 *   - EventThread_stop(w)
 *   - EventThread_free(w)
 * ============================================================================
 */


// thread/event.c — event dispatcher worker (Legacy: EventThread.java).

static void event_job(Thread *self, void *task) {
    (void)self;
    if (!task) {
        Key_dispatchEvents();
        Mouse_dispatchEvents();
        Touch_dispatchEvents();
        return;
    }
    // Reserved for off-heap custom event packet dispatchers (legacy dispatch()).
}

Thread *EventThread_invoke(void) {
    return Thread_new(TYPE_THREAD_EVENT_SINGLETON, event_job, 1024, true, false);
}

bool EventThread_submit(Thread *w, void *packet) {
    return Thread_submit(w, packet);
}

void EventThread_stop(Thread *w) {
    Thread_stop(w);
}

void EventThread_free(Thread *w) {
    Thread_free(w);
}
