#ifndef THREAD_SCRIPTING_H
#define THREAD_SCRIPTING_H

#include "thread/thread.h"

// thread/scripting.h — the scripting worker (Legacy: ScriptingThread.java).
//
// Drains script/task handles off the main thread. Pure queue consumer:
// no idle ticking.

Thread *ScriptingThread_invoke(void);
bool ScriptingThread_submit(Thread *w, void *task);
void ScriptingThread_stop(Thread *w);
void ScriptingThread_free(Thread *w);

#endif
