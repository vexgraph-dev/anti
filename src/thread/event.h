#ifndef THREAD_EVENT_H
#define THREAD_EVENT_H

#include "thread/thread.h"

// thread/event.h — the event dispatcher worker (Legacy: EventThread.java).
//
// Pumps the input contracts (Key/Mouse/Touch) every pass and drains custom
// event packets from its queue. tickWhenIdle is on: the pump runs even when
// no packets arrive.

Thread *EventThread_invoke(void);
bool EventThread_submit(Thread *w, void *packet);
void EventThread_stop(Thread *w);
void EventThread_free(Thread *w);

#endif
