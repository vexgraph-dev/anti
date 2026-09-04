#include "thread/ui.h"

#include "annotation/incomplete.h"

#include "oop/type.h"
#include "annotation/overview.h"

;;OVERVIEW
/**
 * ============================================================================
 * MODULE: Ui (thread/ui.c)
 * ============================================================================
 * the UI worker (Legacy: UIThread.java).
 *
 * FUNCTION REGISTRY:
 * ----------------------------------------------------------------------------
 * Core Functions:
 *   - UIThread_invoke(void)
 *   - UIThread_submit(w, packet)
 *   - UIThread_stop(w)
 *   - UIThread_free(w)
 * ============================================================================
 */


// thread/ui.c — UI worker (Legacy: UIThread.java).

;;INCOMPLETE // Component ticks port: idle passes are received but nothing to
;;INCOMPLETE // tick yet (legacy tickComponents — caret blink, scroll inertia).
;;INCOMPLETE // Packet drain and lifecycle are fully live.

static void ui_job(Thread *self, void *task) {
    (void)self;
    if (!task)
        return; // TODO(ui): tickComponents(self) once UI components land.
    // TODO(ui): dispatch(packet) for queued UI packets.
}

Thread *UIThread_invoke(void) {
    return Thread_new(TYPE_THREAD_UI_SINGLETON, ui_job, 1024, true, false);
}

bool UIThread_submit(Thread *w, void *packet) {
    return Thread_submit(w, packet);
}

void UIThread_stop(Thread *w) {
    Thread_stop(w);
}

void UIThread_free(Thread *w) {
    Thread_free(w);
}
