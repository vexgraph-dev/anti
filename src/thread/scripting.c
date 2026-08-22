#include "thread/scripting.h"

#include "annotation/incomplete.h"

#include "oop/type.h"

// thread/scripting.c — scripting worker (Legacy: ScriptingThread.java).

;;INCOMPLETE // Script executor port: tasks are dequeued and dropped until the
;;INCOMPLETE // scripting surface lands. Queue and lifecycle are fully live.

static void scripting_job(Thread *self, void *task) {
    (void)self;
    (void)task; // TODO(lang): execute(task) once the script surface lands.
}

Thread *ScriptingThread_invoke(void) {
    return Thread_new(TYPE_THREAD_SCRIPTING_SINGLETON, scripting_job,
                      1024, false, false);
}

bool ScriptingThread_submit(Thread *w, void *task) {
    return Thread_submit(w, task);
}

void ScriptingThread_stop(Thread *w) {
    Thread_stop(w);
}

void ScriptingThread_free(Thread *w) {
    Thread_free(w);
}
