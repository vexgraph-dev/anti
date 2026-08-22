#ifndef DARLING_SCENE_H
#define DARLING_SCENE_H

#include <stdbool.h>
#include <stdint.h>

#include "c11/constructor.h"
#include "darling/panel.h"

// darling/scene.h — the scene root (Legacy: darling/Scene.java).
//
// Panel + mapping mode. The scene's virtual size IS its Container w/h: the
// scene never re-renders on resize — the present pass scales it into whatever
// the window occupies. Scene2D/3D are dispatch tags with no extra payload.

#define SCENE_MODE_STRETCH 0 // whole scene -> whole window (asymmetric)
#define SCENE_MODE_FIT     1 // uniform scale, letterboxed + centered
#define SCENE_MODE_PIXEL   2 // 1 scene unit == 1 window px, top-left pinned

typedef struct Scene {
    Panel base;
    int32_t mode;
} Scene;

typedef struct Scene2D {
    Scene base;
} Scene2D;

typedef struct Scene3D {
    Scene base;
} Scene3D;

// Constructors:
//   Scene()               — defaults: PIXEL mode, zero virtual size
//   Scene(w, h)           — fixed virtual size
//   Scene(w, h, mode)     — fixed virtual size + mapping
Scene *Scene_0(void);
Scene *Scene_2(float width, float height);
Scene *Scene_3(float width, float height, int mode);

#define Scene(...) CONSTRUCTOR_DISPATCH(Scene, ##__VA_ARGS__)

Scene2D *Scene2D_0(void);
Scene3D *Scene3D_0(void);

int Scene_getMode(const Scene *s);
void Scene_setMode(Scene *s, int mode);
float Scene_getVirtualWidth(const Scene *s);   // legacy parity: reads Container w/h
float Scene_getVirtualHeight(const Scene *s);

#endif
