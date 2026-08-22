// src/main/darlingtest.c — the darling-tree live demo.
//
// The enginetest vista reborn through the C pipeline: a Scene root holds a
// sky panel and a sun panel (each with its own resolution); the draw side
// paints their Surface swapchains; thread 0 composites the scissored stamps
// onto the master canvas and presents. Esc or the red button exits.

#include <stdio.h>

#include "buffers/color_buffer.h"
#include "darling/panel.h"
#include "darling/scene.h"
#include "engine/loop.h"
#include "input/key.h"
#include "render/raster.h"
#include "render/surface.h"
#include "window/window.h"

#define FB_W 800
#define FB_H 600

typedef struct {
    Window *window;
    Loop loop;
    Buffer *master;

    Scene *scene;          // darling tree root
    Panel *sky;            // darling panels (layout truth)
    Panel *sun;

    Surface *skySurface;   // render-side swapchains (1:1 geometry for now)
    Surface *sunSurface;

    int frames;
} DarlingTest;

static DarlingTest g_demo = {0};

static void paintSky(void) {
    Buffer *back = Surface_back(g_demo.skySurface);
    size_t w = Buffer_width(back);
    size_t h = Buffer_height(back);
    Raster_gradientH(back, 0, 0, (int)w, (int)h,
                     12, 20, 48, 255, 240, 120, 80, 255);
    Raster_rect(back, 0, (int)h - 30, (int)w, 30, 24, 16, 12, 255);
    Raster_line(back, 0, (int)h - 31, (int)w - 1, (int)h - 31, 255, 235, 200, 255);
    Surface_flip(g_demo.skySurface);
}

static void paintSun(void) {
    Buffer *back = Surface_back(g_demo.sunSurface);
    size_t w = Buffer_width(back);
    size_t h = Buffer_height(back);
    ColorBuffer_clearRGBA(back, 0, 0, 0, 0); // sky shows through

    Raster_triangle(back, (int)w / 2, 4,
                    (int)w / 2 - 26, (int)h - 6,
                    (int)w / 2 + 26, (int)h - 6,
                    255, 220, 90, 255);
    Surface_flip(g_demo.sunSurface);
}

static void engine_tick(void *userdata) {
    DarlingTest *t = userdata;
    Window_pollEvents();

    if (Window_shouldClose((*t).window) || Key_isDown(KEY_ESCAPE)) {
        Loop_stop(&(*t).loop);
        return;
    }

    // producers
    paintSky();
    paintSun();

    // thread 0: master <- scissored stamps -> screen
    ColorBuffer_clearRGBA((*t).master, 8, 8, 12, 255);
    Surface_composite(g_demo.skySurface, (*t).master);

    // the sun stamps where its darling rect says (compositor seam)
    Vec4 rect;
    Container_resolve(&(*(*t).sun).base, 0.0f, 0.0f, (float)FB_W, (float)FB_H, &rect);
    Surface_setScissor(g_demo.sunSurface, (int)rect.x, (int)rect.y);
    Surface_composite(g_demo.sunSurface, (*t).master);

    Window_present((*t).window, (*t).master);
    (*t).frames++;
}

int main(void) {
    Key_init();
    g_demo.window = Window();
    Window_setTitle(g_demo.window, "anti darling");
    Window_setDimension(g_demo.window, FB_W, FB_H);
    Window_setLocation(g_demo.window, 120, 120);
    Window_show(g_demo.window);

    g_demo.master = ColorBuffer_allocate(FB_W, FB_H);

    // darling tree: Scene -> sky panel + sun panel (layout truth)
    g_demo.scene = Scene((float)FB_W, (float)FB_H, SCENE_MODE_PIXEL);
    g_demo.sky = Panel_1(&(*g_demo.scene).base);
    Container_setSize(&(*g_demo.sky).base, (float)FB_W, (float)FB_H);

    g_demo.sun = Panel_1(&(*g_demo.scene).base);
    Container_setLocation(&(*g_demo.sun).base, 520.0f, 60.0f);
    Container_setSize(&(*g_demo.sun).base, 90.0f, 90.0f);

    // render-side swapchains mirroring that geometry (v1 1:1 stamps)
    g_demo.skySurface = Surface_new(FB_W, FB_H, 0, 0);
    g_demo.sunSurface = Surface_new(90, 90, 0, 0);

    printf("darling live: %dx%d; Esc or red button exits\n", FB_W, FB_H);

    g_demo.loop = (Loop){ .tick = engine_tick, .userdata = &g_demo, .frame_ms = 16, .running = false };
    Loop_run(&g_demo.loop);

    printf("darling done after %d frames\n", g_demo.frames);
    Key_shutdown();
    Window_destroy(g_demo.window);
    return 0;
}
