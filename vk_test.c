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

    // Honest draw telemetry: counts only frames that actually rendered
    // (a pass where the triple buffer was full and we got skipped is not a frame)
    _Atomic uint32_t drawFps;
    _Atomic uint32_t drawFrametimeUs;
} VkProbeState;

static VkProbeState g_state = {0};

static void vk_draw_job(Thread *self, void *task) {
    (void)self;
    (void)task;

    static NanoTimer timer;
    static bool init = false;
    static uint64_t lastReportNanos = 0;
    static uint32_t frameCount = 0;

    if (!init) {
        NanoTimer_reset(&timer);
        lastReportNanos = NanoTime_now();
        init = true;
    }

    if (!atomic_load_explicit(&g_state.running, memory_order_relaxed))
        return;

    uint64_t frameStart = NanoTime_now();
    NanoTimer_tick(&timer);
    float time = (float)NanoTimer_totalTime(&timer);

    if (!Vk_helloTriangle(time))
        return; // triple buffer full or rebuild paused: no frame produced

    frameCount++;
    uint64_t frameEnd = NanoTime_now();
    uint64_t frameUs = (frameEnd - frameStart) / 1000;
    atomic_store_explicit(&g_state.drawFrametimeUs, (uint32_t)frameUs, memory_order_relaxed);

    uint64_t elapsed = frameEnd - lastReportNanos;
    if (elapsed >= 500000000ULL) { // update every 500ms
        uint32_t fps = (uint32_t)((frameCount * 1000000000ULL) / elapsed);
        atomic_store_explicit(&g_state.drawFps, fps, memory_order_relaxed);
        frameCount = 0;
        lastReportNanos = frameEnd;
    }
}

int main(void) {
    Key_init();
    NanoTime_init();

    Window *w = Window();
    Window_setTitle(w, "anti vk probe");
    Window_setDimension(w, 640, 400);
    Window_show(w);
    Window_pollEvents();

    printf("vk init: %d (%s)\n", Vk_init(w), Vk_status());
    if (!Vk_ready()) {
        fprintf(stderr, "vk init failed: %s\n", Vk_status());
        Window_destroy(w);
        Key_shutdown();
        return 1;
    }
    // Darling Scene3D: 3D Scene container node anchored to the bottom-right
    // parent edges — on resize the panel MOVES with those edges (darling
    // anchor semantics); the drawable itself stays 1:1 via kCAGravityTopLeft.
    Scene3D *scene3D = Scene3D_0();
    Container_setLocation(&(*scene3D).base.base.base, 0.0f, 0.0f);
    Container_setSize(&(*scene3D).base.base.base, 640.0f, 400.0f);
    Container_setSelfAnchor(&(*scene3D).base.base.base, CONTAINER_SELF_ANCHOR_TOP_LEFT);
    Container_setParentAnchor(&(*scene3D).base.base.base, CONTAINER_PARENT_ANCHOR_BOTTOM_RIGHT);
    Panel_setBackgroundColor(&(*scene3D).base.base, 0xFF141414);
    int winW = Window_width(w);
    int winH = Window_height(w);
    Vec4 rect;
    Container_resolve(&(*scene3D).base.base.base, 0.0f, 0.0f, (float)winW, (float)winH, &rect);
    Vk_updateLayout(rect.x, rect.y, rect.z, rect.w, winW, winH, Panel_getBackgroundColor(&(*scene3D).base.base));

    atomic_store(&g_state.running, true);

    // Core Draw Worker: owns GPU render loop on dedicated background thread
    Thread *drawWorker = Thread_new(TYPE_THREAD_DRAW_SINGLETON, vk_draw_job,
                                    1024, true, false);
    if (!drawWorker || !Thread_run(drawWorker)) {
        fprintf(stderr, "failed to start Vulkan draw worker thread\n");
    }

    uint64_t lastReport = NanoTime_now();
    uint32_t presentFrames = 0;
    uint32_t pFps = 0;
    float pFrametimeMs = 0.0f;
    char titleBuf[256];

    while (!Window_shouldClose(w) && !Key_isDown(KEY_ESCAPE)) {
        Window_pollEvents();
        winW = Window_width(w);
        winH = Window_height(w);
        Container_resolve(&(*scene3D).base.base.base, 0.0f, 0.0f, (float)winW, (float)winH, &rect);
        Vk_updateLayout(rect.x, rect.y, rect.z, rect.w, winW, winH, Panel_getBackgroundColor(&(*scene3D).base.base));

        uint64_t pStart = NanoTime_now();
        Vk_clearPresent(0, 0, 0);
        uint64_t pEnd = NanoTime_now();
        pFrametimeMs = (float)(pEnd - pStart) / 1000000.0f;
        presentFrames++;

        // 1ms event sleep keeps event pump at 1000Hz with zero idle CPU load
        struct timespec tick = { 0, 1000 * 1000 };
        nanosleep(&tick, NULL);

        uint64_t now = NanoTime_now();
        uint64_t elapsed = now - lastReport;
        if (elapsed >= 250000000ULL) { // update title every 250ms
            pFps = (uint32_t)((presentFrames * 1000000000ULL) / elapsed);
            presentFrames = 0;
            lastReport = now;

            uint32_t dFps = atomic_load_explicit(&g_state.drawFps, memory_order_relaxed);
            float dMs = (float)atomic_load_explicit(&g_state.drawFrametimeUs, memory_order_relaxed) / 1000.0f;
            int winW = Window_width(w);
            int winH = Window_height(w);

            snprintf(titleBuf, sizeof(titleBuf),
                     "anti vk probe | Present FPS: %u (%.2f ms) | Draw FPS: %u (%.2f ms) | %dx%d",
                     pFps, pFrametimeMs, dFps, dMs, winW, winH);
            Window_setTitle(w, titleBuf);
            printf("[telemetry] Present FPS: %u (%.2f ms) | Draw FPS: %u (%.2f ms) | %dx%d\n",
                   pFps, pFrametimeMs, dFps, dMs, winW, winH);
            fflush(stdout);
        }
    }

    // Teardown
    atomic_store(&g_state.running, false);
    if (drawWorker) {
        Thread_stop(drawWorker);
        Thread_free(drawWorker);
    }

    Vk_shutdown();
    Memory_free(scene3D);
    Window_destroy(w);
    Key_shutdown();
    return 0;
}
