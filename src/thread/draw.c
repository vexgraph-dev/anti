#include "thread/draw.h"

#include "annotation/incomplete.h"

#include "oop/type.h"
#include "annotation/overview.h"

;;OVERVIEW
/**
 * ============================================================================
 * CLASS: Draw (thread/draw.c)
 * LEVEL: L2 — Behavior (worker-thread behavior API)
 * ============================================================================
 * the rendering worker pool (Legacy: DrawThread.java).
 *
 * STRUCT FIELDS (local to this file):
 * ----------------------------------------------------------------------------
 *   RoleSlot {
 *     int role;                  // DRAW_ROLE_* tag served by this slot
 *     Thread *worker;            // backing worker thread handle
 *   }
 *
 * FUNCTION REGISTRY:
 * ----------------------------------------------------------------------------
 * Core Functions:
 *   - DrawThread_invokeRole(role)
 *   - DrawThread_submitTo(role, task)
 *   - DrawThread_stopAll(void)
 *   - DrawThread_freeAll(void)
 * ============================================================================
 */


// thread/draw.c — rendering worker pool (Legacy: DrawThread.java).

;;INCOMPLETE // Render backend port: role dispatch receives tasks but has no
;;INCOMPLETE // renderer to record into (legacy UserRole.record / gradient
;;INCOMPLETE // scene). Queue plumbing and role routing are fully live.

#define DRAW_ROLE_SLOTS 8

typedef struct RoleSlot {
    int role;
    Thread *worker;
} RoleSlot;

static RoleSlot s_roles[DRAW_ROLE_SLOTS];

static Thread *roleWorker(int role) {
    for (int i = 0; i < DRAW_ROLE_SLOTS; i++) {
        if (s_roles[i].worker && s_roles[i].role == role)
            return s_roles[i].worker;
    }
    return nullptr;
}

static void draw_job(Thread *self, void *task) {
    (void)self;
    if (!task)
        return;
    // TODO(draw): route by slot role into the renderer once it lands.
}

Thread *DrawThread_invokeRole(int role) {
    for (int i = 0; i < DRAW_ROLE_SLOTS; i++) {
        if (!s_roles[i].worker) {
            bool core = role == DRAW_ROLE_CORE;
            Thread *w = Thread_new(TYPE_THREAD_DRAW_SINGLETON, draw_job,
                                   1024, false, core);
            if (!w)
                return nullptr;
            s_roles[i].role = role;
            s_roles[i].worker = w;
            return w;
        }
    }
    return nullptr;
}

bool DrawThread_submitTo(int role, void *task) {
    Thread *w = roleWorker(role);
    return w && Thread_submit(w, task);
}

void DrawThread_stopAll(void) {
    for (int i = 0; i < DRAW_ROLE_SLOTS; i++)
        Thread_stop(s_roles[i].worker);
}

void DrawThread_freeAll(void) {
    for (int i = 0; i < DRAW_ROLE_SLOTS; i++) {
        Thread_free(s_roles[i].worker);
        s_roles[i].worker = nullptr;
        s_roles[i].role = 0;
    }
}
