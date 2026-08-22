#include "thread/networking.h"

#include "annotation/incomplete.h"

#include "nio/mem.h"
#include "oop/type.h"

// thread/networking.c — networking worker (Legacy: NetworkingThread.java).

;;INCOMPLETE // PollRequest port: batches are received and dropped until
;;INCOMPLETE // net/PollRequest lands. The queue, lifecycle, and core guard
;;INCOMPLETE // are fully live.

static Thread *s_core = NULL;

static void networking_job(Thread *self, void *task) {
    (void)self;
    (void)task; // TODO(net): PollRequest_executeAll(batch) once ported.
}

Thread *NetworkingThread_invoke(void) {
    return Thread_new(TYPE_THREAD_NETWORKING_SINGLETON, networking_job,
                      2048, false, false);
}

Thread *NetworkingThread_core(void) {
    if (!s_core)
        s_core = Thread_new(TYPE_THREAD_NETWORKING_SINGLETON, networking_job,
                            2048, false, true);
    return s_core;
}

bool NetworkingThread_submit(Thread *w, void *batch) {
    return Thread_submit(w, batch);
}

void NetworkingThread_stop(Thread *w) {
    Thread_stop(w);
}

void NetworkingThread_free(Thread *w) {
    if (w == s_core)
        return;
    Thread_free(w);
}
