#ifndef DARLING_SCENE_H
#define DARLING_SCENE_H

#include <stdbool.h>
#include <stdint.h>

#include "c23/constructor.h"
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

// Layout facade — the delegation chain's middle link:
//   Scene3D_setLocation -> Scene_setLocation -> Panel_setLocation
//   -> Container_setLocation(&(*panel).base)
// Each level re-exports the common accessors over ITS OWN embedded prefix,
// so callers never spell .base.base.base. Static inline: Java-style
// inherited methods with static binding and zero runtime cost.
static inline void Scene_setLocation(Scene *s, float x, float y)
    { if (s) Panel_setLocation(&(*s).base, x, y); }
static inline void Scene_setSize(Scene *s, float w, float h)
    { if (s) Panel_setSize(&(*s).base, w, h); }
static inline void Scene_setParentAnchor(Scene *s, int anchor)
    { if (s) Panel_setParentAnchor(&(*s).base, anchor); }
static inline void Scene_setSelfAnchor(Scene *s, int anchor)
    { if (s) Panel_setSelfAnchor(&(*s).base, anchor); }
static inline void Scene_setBackgroundColor(Scene *s, uint32_t color) {
    if (s) Panel_setBackgroundColor(&(*s).base, color);
}

static inline void Scene2D_setLocation(Scene2D *s, float x, float y)
    { if (s) Scene_setLocation(&(*s).base, x, y); }
static inline void Scene2D_setSize(Scene2D *s, float w, float h)
    { if (s) Scene_setSize(&(*s).base, w, h); }
static inline void Scene2D_setParentAnchor(Scene2D *s, int anchor)
    { if (s) Scene_setParentAnchor(&(*s).base, anchor); }
static inline void Scene2D_setSelfAnchor(Scene2D *s, int anchor)
    { if (s) Scene_setSelfAnchor(&(*s).base, anchor); }
static inline void Scene2D_setBackgroundColor(Scene2D *s, uint32_t color)
    { if (s) Scene_setBackgroundColor(&(*s).base, color); }

static inline void Scene3D_setLocation(Scene3D *s, float x, float y)
    { if (s) Scene_setLocation(&(*s).base, x, y); }
static inline void Scene3D_setSize(Scene3D *s, float w, float h)
    { if (s) Scene_setSize(&(*s).base, w, h); }
static inline void Scene3D_setParentAnchor(Scene3D *s, int anchor)
    { if (s) Scene_setParentAnchor(&(*s).base, anchor); }
static inline void Scene3D_setSelfAnchor(Scene3D *s, int anchor)
    { if (s) Scene_setSelfAnchor(&(*s).base, anchor); }
static inline void Scene3D_setBackgroundColor(Scene3D *s, uint32_t color)
    { if (s) Scene_setBackgroundColor(&(*s).base, color); }

#endif
