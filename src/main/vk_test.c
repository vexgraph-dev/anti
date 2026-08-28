#include <math.h>
#include <stdlib.h>
#include <stdio.h>
#include <stdatomic.h>
#include <time.h>

#include "darling/container.h"
#include "darling/panel.h"
#include "darling/scene.h"
#include "darling/picture.h"
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

// METHOD-SLOT OVERRIDE DEMO: this function is pointed at the HUD panel via
// Panel_setRenderHandler — the C equivalent of @Override. It replaces the
// built-in solid quad entirely and composes out of the public Vk_fillRect
// primitive instead: dark glass backing plus a sweeping accent bar whose
// width breathes with a 1-second sine.

extern int32_t Texture_load(const char *vfsPath);
extern void Vk_drawTexture(void *cmdBuffer, float surfaceW, float surfaceH, float x, float y, float w, float h,
                           float r, float g, float b, float a, int32_t textureId);

static int32_t s_sunflowerId = -1;

static void pic_render(Panel *p, void *data, void *cmdBuffer, float x, float y, float w, float h) { (void)p; (void)x; (void)y;
    (void)data;
    if (s_sunflowerId >= 0) {
        Vk_drawTexture(cmdBuffer, w, h, 0, 0, w, h, 1.0f, 1.0f, 1.0f, 1.0f, s_sunflowerId);
    } else {
        // Fallback to yellow background
        extern void Vk_fillRect(void *, float, float, float, float, float, float, float, float, float, float);
        Vk_fillRect(cmdBuffer, w, h, 0, 0, w, h, 1.0f, 0.8f, 0.0f, 1.0f);
    }
}

static void hud_pulse(Panel *panel, void *renderer, void *cmdBuffer,
                      float x, float y, float w, float h) {
    (void)panel;
    (void)renderer;

    // Monotonic seconds: no 1s wrap, so the pulse never stutters on
    // frametimes that don't divide the second evenly.
    double t = (double)NanoTime_now() / 1e9;
    float pulse = 0.5f + 0.5f * sinf((float)(t * 6.28318530718));

    float barH = h * 0.08f;
    Vk_fillRect(cmdBuffer, w, h, x, y, w, h - barH, 0.07f, 0.09f, 0.11f, 0.92f);
    Vk_fillRect(cmdBuffer, w, h, x, y + h - barH, w * pulse, barH, 0.18f, 0.80f, 0.44f, 1.0f);
}

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
    extern void System_initializeAll(void);
    System_initializeAll();

    Window *w = Window();
    Window_setTitle(w, "anti vk probe");
    Window_setSize(w, 640, 400);
    Window_setUndecorated(w, WINDOW_UNDECORATED_NAKED);
    Window_show(w);

    int vkResult = Vk_init(w);
    printf("vk init: %d (%s)\n", vkResult, Vk_status());
    if (!vkResult) {
        fprintf(stderr, "vk init failed: %s\n", Vk_status());
        Window_destroy(w);
        Key_shutdown();
        return 1;
    }

    Window_setBlur(w, 1);
    Window_setScenePanel(w, NULL);

    // 2. Content panel: logical placeholder for UI & floating layers
    // Note: its background color (e.g. black) is ignored and transparent.
    Panel *contentPanel = Panel();
    Panel_setLocation(contentPanel, 0.0f, 0.0f);
    Panel_setSize(contentPanel, 8192.0f, 8192.0f); // max ceiling
    Panel_setSize(contentPanel, 640.0f, 400.0f);   // actual size
    Panel_setBackgroundColor(contentPanel, 0xFF000000u); // ignored placeholder color
    Window_setContentPanel(w, contentPanel);

    // 3. HUD: child in contentPanel, IOSurface-backed, renders via Vulkan into its own buffer
    Panel *hud = Panel();
    Panel_setLocation(hud, 40.0f, 40.0f);
    Panel_setSize(hud, 640.0f, 400.0f); // first call = max buffer allocation size
    Panel_setSize(hud, 220.0f, 100.0f); // second call = actual size (clamped)
    Panel_setBackgroundColor(hud, 0xFF2E7D32u);
    Panel_setRenderHandler(hud, hud_pulse);
    Panel_addContainer(contentPanel, hud);

    // 4. Mini-3D viewport: floating 3D scene inside contentPanel (IOSurface-backed)
    Scene3D *mini3D = Scene3D_0();
    Scene3D_setLocation(mini3D, 0.0f, 0.0f);
    Scene3D_setSize(mini3D, 1000.0f, 1000.0f); // max buffer allocation size
    Scene3D_setParentAnchor(mini3D, CONTAINER_PARENT_ANCHOR_BOTTOM_RIGHT);
    Scene3D_setSelfAnchor(mini3D, CONTAINER_SELF_ANCHOR_BOTTOM_RIGHT);
    Panel_addContainer(contentPanel, &(*mini3D).base.base);

    // 5. Picture testing node (Middle Center anchoring)
    Picture *pic = Picture_0();
    Picture_setSize(pic, 1024.0f, 1024.0f); // first call allocates max bounds
    Picture_setSize(pic, 300.0f, 300.0f);   // Make it bigger to see the flower!
    
    s_sunflowerId = Texture_load("/Users/vexgraph/Downloads/sunflower.png");
    Panel_setRenderHandler(&(*pic).base, pic_render);

    Picture_setParentAnchor(pic, CONTAINER_PARENT_ANCHOR_MIDDLE_CENTER);
    // Set self anchor to its own middle (pivot point)
    Picture_setSelfAnchor(pic, CONTAINER_SELF_ANCHOR_MIDDLE_CENTER);
    
    // x=0, y=0 offset from the anchor point (which is now exactly in the center)
    Picture_setLocation(pic, 0.0f, 0.0f);
    Panel_addContainer(contentPanel, &(*pic).base);

    // Enable native IOSurface backing on content panel
    Window_forceNativeContainerOnRoot(w, true);
    printf("native IOSurface: ON (background=Vulkan swapchain, overlays=Vulkan->IOSurface)\n");

    atomic_store(&g_state.running, true);

    // Present Worker: owns the whole cache-clear/render/blit/present loop
    Thread *presentWorker = Thread_new(TYPE_THREAD_UI_SINGLETON, vk_present_job,
                                       1024, true, false);
    if (!presentWorker || !Thread_run(presentWorker)) {
        fprintf(stderr, "failed to start Vulkan present worker thread\n");
        atomic_store(&g_state.running, false);
        Vk_shutdown();
        Memory_free(pic);
        Memory_free(mini3D);
        Memory_free(hud);
        Memory_free(contentPanel);
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
    Memory_free(pic);
    Memory_free(mini3D);
    Memory_free(hud);
    Memory_free(contentPanel);
    Window_destroy(w);
    Key_shutdown();

    Memory_freeAll();
    return 0;
}
