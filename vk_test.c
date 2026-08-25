#include <stdlib.h>
#include <stdio.h>
#include <stdatomic.h>
#include <time.h>

#include "darling/container.h"
#include "darling/panel.h"
#include "darling/scene.h"
#include "input/key.h"
#include "nio/mem.h"
#include "oop/type.h"
#include "thread/thread.h"
#include "time/nanotime.h"
#include "vulkan/vk.h"
#include "window/window.h"

typedef struct {
    _Atomic bool running;

    // Honest present telemetry: one loop owns clear -> render -> blit ->
    // present for the whole compositor model, so there is exactly one FPS.
    _Atomic uint32_t presentFps;
    _Atomic uint32_t presentFrametimeUs;
} VkProbeState;

static VkProbeState g_state = {0};

// Present Worker: clears the monitor cache, renders the basket's children
// onto it, blits the window region and presents — the whole loop.
static void vk_present_job(Thread *self, void *task) {
    (void)self;
    (void)task;

    static bool init = false;
    static uint64_t lastReportNanos = 0;
    static uint32_t frameCount = 0;

    if (!init) {
        lastReportNanos = NanoTime_now();
        init = true;
    }

    while (atomic_load_explicit(&g_state.running, memory_order_relaxed)) {
        uint64_t frameStart = NanoTime_now();

        Vk_clearPresent();

        frameCount++;
        uint64_t frameEnd = NanoTime_now();
        uint64_t frameUs = (frameEnd - frameStart) / 1000;
        atomic_store_explicit(&g_state.presentFrametimeUs, (uint32_t)frameUs, memory_order_relaxed);

        uint64_t elapsed = frameEnd - lastReportNanos;
        if (elapsed >= 500000000ULL) { // update every 500ms
            uint32_t fps = (uint32_t)((frameCount * 1000000000ULL) / elapsed);
            atomic_store_explicit(&g_state.presentFps, fps, memory_order_relaxed);
            frameCount = 0;
            lastReportNanos = frameEnd;
        }
    }
}

int main(void) {
    Key_init();
    NanoTime_init();

    Window *w = Window();
    Window_setTitle(w, "anti vk probe");
    Window_setSize(w, 640, 400);
    Window_show(w);

    int vkResult = Vk_init(w);
    printf("vk init: %d (%s)\n", vkResult, Vk_status());
    if (!vkResult) {
        fprintf(stderr, "vk init failed: %s\n", Vk_status());
        Window_destroy(w);
        Key_shutdown();
        return 1;
    }

    // The basket: one empty panel on the window's container slot. Its w/h
    // mirror the window's content size (resize-reflection), and everything
    // else hangs under it as children. ONE layer renders: these children.
    Panel *root = Panel_0();
    Panel_setBackgroundColor(root,0xffffffffl);
    Window_setContainer(w, root);

    // Scene3D child: legacy animated triangle content in its bounds.
    Scene3D *scene3D = Scene3D_0();
    Container_setLocation(&(*scene3D).base.base.base, 0.0f, 0.0f);
    Container_setSize(&(*scene3D).base.base.base, 640.0f, 400.0f);
    Container_setParentAnchor(&(*scene3D).base.base.base, CONTAINER_PARENT_ANCHOR_BOTTOM_RIGHT);
    Panel_addContainer(root, &(*scene3D).base.base);

    // Plain panel child: a solid quad floating over the scene.
    Panel *hud = Panel_0();
    Container_setLocation(&(*hud).base, 40.0f, 40.0f);
    Container_setSize(&(*hud).base, 200.0f, 200.0f);
    Panel_setBackgroundColor(hud, 0xFF2E7D32);
    Panel_addContainer(root, hud);

    atomic_store(&g_state.running, true);

    // Present Worker: owns the whole cache-clear/render/blit/present loop
    Thread *presentWorker = Thread_new(TYPE_THREAD_UI_SINGLETON, vk_present_job,
                                       1024, true, false);
    if (!presentWorker || !Thread_run(presentWorker)) {
        fprintf(stderr, "failed to start Vulkan present worker thread\n");
        atomic_store(&g_state.running, false);
        Vk_shutdown();
        Memory_free(scene3D);
        Memory_free(root);
        Window_destroy(w);
        Key_shutdown();
        return 1;
    }

    uint64_t lastReport = NanoTime_now();
    char titleBuf[256];

    while (!Window_shouldClose(w) && !Key_isDown(KEY_ESCAPE)) {
        Window_pollEvents();
        int winW = Window_width(w);
        int winH = Window_height(w);

        // 1ms event sleep keeps event pump at 1000Hz with zero idle CPU load
        struct timespec tick = { 0, 1000 * 1000 };
        nanosleep(&tick, NULL);

        uint64_t now = NanoTime_now();
        uint64_t elapsed = now - lastReport;
        if (elapsed >= 250000000ULL) { // update title every 250ms
            lastReport = now;

            uint32_t pFps = atomic_load_explicit(&g_state.presentFps, memory_order_relaxed);
            float pMs = (float)atomic_load_explicit(&g_state.presentFrametimeUs, memory_order_relaxed) / 1000.0f;

            snprintf(titleBuf, sizeof(titleBuf),
                     "anti vk probe | Present FPS: %u (%.2f ms) | %dx%d",
                     pFps, pMs, winW, winH);
            Window_setTitle(w, titleBuf);
            printf("[telemetry] Present FPS: %u (%.2f ms) | %dx%d\n",
                   pFps, pMs, winW, winH);
            fflush(stdout);
        }
    }

    // Teardown
    atomic_store(&g_state.running, false);
    if (presentWorker) {
        Thread_stop(presentWorker);
        Thread_free(presentWorker);
    }

    Vk_shutdown();
    Memory_free(scene3D);
    Memory_free(hud);
    Memory_free(root);
    Window_destroy(w);
    Key_shutdown();
    return 0;
}
