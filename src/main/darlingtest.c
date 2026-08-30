// src/main/darlingtest.c — the darling-tree live demo.
//
// Demonstrates the two-thread decoupled architecture:
//   - Draw Worker Thread (DRAW_ROLE_CORE): paints scene, top-left triangle & UI
//     into offscreen double-buffered Surfaces at unthrottled draw speed.
//   - Thread 0 (Cocoa / AppKit): drains OS events, resolves Darling container
//     anchors (Self & Parent anchor resize tracking), stamps surfaces onto master
//     frame, presents to screen, and reports live Draw/Present FPS in window title.

#include <stdio.h>
#include <stdatomic.h>
#include <math.h>

#include "buffers/color_buffer.h"
#include "darling/panel.h"
#include "darling/scene.h"
#include "input/key.h"
#include "oop/type.h"
#include "render/raster.h"
#include "render/surface.h"
#include "thread/thread.h"
#include "time/nanotime.h"
#include "window/window.h"

#define DEFAULT_W 800
#define DEFAULT_H 600

typedef struct {
    Window *window;
    Buffer *master;
    size_t masterW;
    size_t masterH;

    Scene *scene;
    Panel *skyPanel;
    Panel *triPanel;
    Panel *uiPanel;

    Surface *skySurface;
    Surface *triSurface;
    Surface *uiSurface;

    Thread *drawWorker;
    _Atomic bool running;

    // Live telemetry (atomics updated by draw worker & thread 0)
    _Atomic uint32_t drawFps;
    _Atomic uint32_t drawFrametimeUs;
    _Atomic uint32_t presentFps;
    _Atomic uint32_t presentFrametimeUs;
} DarlingDemo;

static DarlingDemo g_demo = {0};

// --- Draw Worker Thread Jobs (paints into back buffers & flips lock-free) ----

static void paintSky(Surface *surface, float t) {
    (void)t;
    Buffer *back = Surface_back(surface);
    if (!back) return;
    size_t w = Buffer_width(back);
    size_t h = Buffer_height(back);

    // Atmospheric horizon gradient
    Raster_gradientH(back, 0, 0, (int)w, (int)h,
                     15, 23, 42, 255, 249, 115, 22, 255);
    Raster_rect(back, 0, (int)h - 40, (int)w, 40, 15, 23, 42, 255);
    Raster_line(back, 0, (int)h - 41, (int)w - 1, (int)h - 41, 253, 186, 116, 255);
    Surface_flip(surface);
}

static void paintTriangle(Surface *surface, float t) {
    Buffer *back = Surface_back(surface);
    if (!back) return;
    size_t w = Buffer_width(back);
    size_t h = Buffer_height(back);

    ColorBuffer_clearRGBA(back, 0, 0, 0, 0); // Transparent back so sky shows through

    // Top-left gravity triangle with smooth oscillation inside its 220x220 bounding box
    float bounceX = sinf(t * 2.2f) * 16.0f;
    float bounceY = cosf(t * 1.8f) * 12.0f;

    // Triangle vertices (top-left anchored coordinates)
    int x0 = (int)(w * 0.5f + bounceX);
    int y0 = (int)(18.0f + bounceY);
    int x1 = (int)(24.0f + bounceX);
    int y1 = (int)(h - 24.0f + bounceY);
    int x2 = (int)(w - 24.0f + bounceX);
    int y2 = (int)(h - 24.0f + bounceY);

    // Glowing outline
    Raster_line(back, x0 - 1, y0, x1 - 1, y1, 239, 68, 68, 120);
    Raster_line(back, x1, y1 + 1, x2, y2 + 1, 239, 68, 68, 120);
    Raster_line(back, x2 + 1, y2, x0 + 1, y0, 239, 68, 68, 120);

    // Main vibrant triangle fill
    Raster_triangle(back, x0, y0, x1, y1, x2, y2, 251, 191, 36, 255);

    // Inner highlight
    Raster_line(back, x0, y0 + 6, (x0 + x1) / 2 + 3, (y0 + y1) / 2, 255, 255, 255, 200);

    Surface_flip(surface);
}

static void paintUI(Surface *surface, float t) {
    Buffer *back = Surface_back(surface);
    if (!back) return;
    size_t w = Buffer_width(back);
    size_t h = Buffer_height(back);

    // Frosted glass UI card background (semi-transparent slate)
    ColorBuffer_clearRGBA(back, 30, 41, 59, 210);

    // Card border
    Raster_line(back, 0, 0, (int)w - 1, 0, 148, 163, 184, 255);
    Raster_line(back, 0, (int)h - 1, (int)w - 1, (int)h - 1, 71, 85, 105, 255);
    Raster_line(back, 0, 0, 0, (int)h - 1, 148, 163, 184, 255);
    Raster_line(back, (int)w - 1, 0, (int)w - 1, (int)h - 1, 71, 85, 105, 255);

    // Header bar
    Raster_rect(back, 8, 8, (int)w - 16, 22, 15, 23, 42, 230);

    // Pulsing live status indicator dot
    int pulseAlpha = (int)(180 + 75 * sinf(t * 6.0f));
    Raster_rect(back, 16, 15, 8, 8, 16, 185, 129, pulseAlpha);

    // Activity bar meters
    int bar1 = (int)(40 + 30 * sinf(t * 3.0f));
    int bar2 = (int)(60 + 25 * cosf(t * 2.5f));
    Raster_rect(back, 16, 42, bar1, 10, 56, 189, 248, 240);
    Raster_rect(back, 16, 62, bar2, 10, 168, 85, 247, 240);

    Surface_flip(surface);
}

static void draw_worker_job(Thread *self, void *task) {
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

    uint64_t frameStart = NanoTime_now();
    NanoTimer_tick(&timer);
    float t = (float)NanoTimer_totalTime(&timer);

    // Render all layer surfaces
    if (g_demo.skySurface)
        paintSky(g_demo.skySurface, t);
    if (g_demo.triSurface)
        paintTriangle(g_demo.triSurface, t);
    if (g_demo.uiSurface)
        paintUI(g_demo.uiSurface, t);

    frameCount++;
    uint64_t frameEnd = NanoTime_now();
    uint64_t frameUs = (frameEnd - frameStart) / 1000;
    atomic_store_explicit(&g_demo.drawFrametimeUs, (uint32_t)frameUs, memory_order_relaxed);

    uint64_t elapsed = frameEnd - lastReportNanos;
    if (elapsed >= 500000000ULL) { // update every 500ms
        uint32_t fps = (uint32_t)((frameCount * 1000000000ULL) / elapsed);
        atomic_store_explicit(&g_demo.drawFps, fps, memory_order_relaxed);
        frameCount = 0;
        lastReportNanos = frameEnd;
    }
}

int main(void) {
    Key_init();
    NanoTime_init();

    g_demo.window = Window();
    Window_setTitle(g_demo.window, "anti darling");
    Window_setSize(g_demo.window, DEFAULT_W, DEFAULT_H);
    Window_setLocation(g_demo.window, 120, 120);
    Window_show(g_demo.window);

    g_demo.masterW = DEFAULT_W;
    g_demo.masterH = DEFAULT_H;
    g_demo.master = ColorBuffer(g_demo.masterW, g_demo.masterH);

    // --- Darling Scene Tree (Layout Truth) ---
    g_demo.scene = Scene((float)DEFAULT_W, (float)DEFAULT_H, SCENE_MODE_PIXEL);

    // 1. Sky panel: full canvas coverage, tracking resize to bottom-right
    g_demo.skyPanel = Panel_1(&(*g_demo.scene).base);
    Container_setSize(&(*g_demo.skyPanel).base, (float)DEFAULT_W, (float)DEFAULT_H);
    Container_setSelfAnchor(&(*g_demo.skyPanel).base, CONTAINER_SELF_ANCHOR_TOP_LEFT);
    Container_setParentAnchor(&(*g_demo.skyPanel).base, CONTAINER_PARENT_ANCHOR_BOTTOM_RIGHT);

    // 2. Triangle panel: Top-Left gravity anchor (stays at (40, 40) on resize)
    g_demo.triPanel = Panel_1(&(*g_demo.scene).base);
    Container_setLocation(&(*g_demo.triPanel).base, 40.0f, 40.0f);
    Container_setSize(&(*g_demo.triPanel).base, 220.0f, 220.0f);
    Container_setSelfAnchor(&(*g_demo.triPanel).base, CONTAINER_SELF_ANCHOR_TOP_LEFT);
    Container_setParentAnchor(&(*g_demo.triPanel).base, CONTAINER_PARENT_ANCHOR_TOP_LEFT);

    // 3. UI Panel: Bottom-Right parent anchor tracking (moves with window delta!)
    g_demo.uiPanel = Panel_1(&(*g_demo.scene).base);
    Container_setLocation(&(*g_demo.uiPanel).base, 30.0f, 30.0f);
    Container_setSize(&(*g_demo.uiPanel).base, 220.0f, 120.0f);
    Container_setSelfAnchor(&(*g_demo.uiPanel).base, CONTAINER_SELF_ANCHOR_TOP_LEFT);
    Container_setParentAnchor(&(*g_demo.uiPanel).base, CONTAINER_PARENT_ANCHOR_BOTTOM_RIGHT);

    // --- Double-Buffered Surfaces (independent resolution per panel) ---
    g_demo.skySurface = Surface_new(DEFAULT_W, DEFAULT_H, 0, 0);
    g_demo.triSurface = Surface_new(220, 220, 0, 0);
    g_demo.uiSurface = Surface_new(220, 120, 0, 0);

    atomic_store(&g_demo.running, true);

    // --- Spawn Core Draw Worker on its own thread (tickWhenIdle = true) ---
    g_demo.drawWorker = Thread_new(TYPE_THREAD_DRAW_SINGLETON, draw_worker_job,
                                   1024, true, false);
    if (!g_demo.drawWorker || !Thread_run(g_demo.drawWorker)) {
        fprintf(stderr, "failed to start draw worker thread\n");
    }

    printf("darling engine live: Dual-thread Decoupled Render Active\n");
    printf("  Thread 0    : OS Pump + Layout + Compositor + Present\n");
    printf("  Draw Worker : Unthrottled Scene + Top-Left Triangle + UI\n");
    printf("  Controls    : Esc or close button to exit\n");

    // --- Thread 0 Compositor & Event Loop ---
    uint64_t presentLastReport = NanoTime_now();
    uint32_t presentFrames = 0;
    char titleBuf[256];

    while (!Window_shouldClose(g_demo.window) && !Key_isDown(KEY_ESCAPE)) {
        uint64_t pStart = NanoTime_now();
        Window_pollEvents();

        int winW = Window_width(g_demo.window);
        int winH = Window_height(g_demo.window);
        if (winW <= 0) winW = DEFAULT_W;
        if (winH <= 0) winH = DEFAULT_H;

        // Dynamic resize adaptation of master framebuffer
        if ((size_t)winW != g_demo.masterW || (size_t)winH != g_demo.masterH) {
            Buffer_free(g_demo.master);
            g_demo.masterW = (size_t)winW;
            g_demo.masterH = (size_t)winH;
            g_demo.master = ColorBuffer(g_demo.masterW, g_demo.masterH);
        }

        // Clear master canvas
        ColorBuffer_clearRGBA(g_demo.master, 10, 10, 15, 255);

        // 1. Resolve Sky Panel
        Vec4 skyRect;
        Container_resolve(&(*g_demo.skyPanel).base, 0.0f, 0.0f, (float)winW, (float)winH, &skyRect);
        Surface_setScissor(g_demo.skySurface, (int)skyRect.x, (int)skyRect.y);
        Surface_composite(g_demo.skySurface, g_demo.master);

        // 2. Resolve Top-Left Triangle Panel
        Vec4 triRect;
        Container_resolve(&(*g_demo.triPanel).base, 0.0f, 0.0f, (float)winW, (float)winH, &triRect);
        Surface_setScissor(g_demo.triSurface, (int)triRect.x, (int)triRect.y);
        Surface_composite(g_demo.triSurface, g_demo.master);

        // 3. Resolve Bottom-Right Anchored UI Panel
        Vec4 uiRect;
        Container_resolve(&(*g_demo.uiPanel).base, 0.0f, 0.0f, (float)winW, (float)winH, &uiRect);
        Surface_setScissor(g_demo.uiSurface, (int)uiRect.x, (int)uiRect.y);
        Surface_composite(g_demo.uiSurface, g_demo.master);

        // Present to screen (AppKit / WindowServer)
        Window_present(g_demo.window, g_demo.master);

        presentFrames++;
        uint64_t pEnd = NanoTime_now();
        uint64_t pUs = (pEnd - pStart) / 1000;
        atomic_store_explicit(&g_demo.presentFrametimeUs, (uint32_t)pUs, memory_order_relaxed);

        uint64_t pElapsed = pEnd - presentLastReport;
        if (pElapsed >= 250000000ULL) { // update title every 250ms
            uint32_t pFps = (uint32_t)((presentFrames * 1000000000ULL) / pElapsed);
            atomic_store_explicit(&g_demo.presentFps, pFps, memory_order_relaxed);
            presentFrames = 0;
            presentLastReport = pEnd;

            uint32_t dFps = atomic_load_explicit(&g_demo.drawFps, memory_order_relaxed);
            float dMs = (float)atomic_load_explicit(&g_demo.drawFrametimeUs, memory_order_relaxed) / 1000.0f;
            float pMs = (float)atomic_load_explicit(&g_demo.presentFrametimeUs, memory_order_relaxed) / 1000.0f;

            snprintf(titleBuf, sizeof(titleBuf),
                     "anti | Draw: %u FPS (%.2f ms) | Present: %u FPS (%.2f ms) | %dx%d",
                     dFps, dMs, pFps, pMs, winW, winH);
            Window_setTitle(g_demo.window, titleBuf);
        }
    }

    // --- Shutdown ---
    atomic_store(&g_demo.running, false);
    if (g_demo.drawWorker) {
        Thread_stop(g_demo.drawWorker);
        Thread_free(g_demo.drawWorker);
    }

    Surface_free(g_demo.skySurface);
    Surface_free(g_demo.triSurface);
    Surface_free(g_demo.uiSurface);
    Buffer_free(g_demo.master);

    Window_destroy(g_demo.window);
    Key_shutdown();
    return 0;
}
