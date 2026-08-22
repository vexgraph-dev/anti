#ifndef THREAD_DRAW_H
#define THREAD_DRAW_H

#include "thread/thread.h"

// thread/draw.h — the rendering worker pool (Legacy: DrawThread.java).
//
// Workers are tagged with a ROLE at creation; tasks submitted to a role land
// on that worker. Roles mirror legacy exactly.

#define DRAW_ROLE_CORE   1 // the protected primary renderer
#define DRAW_ROLE_SCENE  2 // scene #1 placeholder (gradient rect)
#define DRAW_ROLE_UI     3 // event-driven UI renderer (not ImGui)
#define DRAW_ROLE_USER   4 // start of user-generated registers

Thread *DrawThread_invokeRole(int role);
bool DrawThread_submitTo(int role, void *task);
void DrawThread_stopAll(void);
void DrawThread_stopAll(void);
void DrawThread_freeAll(void);

#endif
