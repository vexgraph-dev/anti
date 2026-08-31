#include "darling/scene.h"

#include "nio/mem.h"
#include "oop/type.h"

// darling/scene.c — scene root (Legacy: darling/Scene.java).

static Scene *allocScene(uint32_t typeId) {
    Scene *s = (Scene*) Memory_alloc(typeId, sizeof(Scene));
    if (!s)
        return nullptr;

    Panel *p = Panel_0();
    if (!p) {
        Memory_free(s);
        return nullptr;
    }
    (*s).base = (*p);
    Memory_free(p);

    (*s).mode = SCENE_MODE_PIXEL; // legacy default: resize reveals more canvas
    return s;
}

// The scene's Container sits two prefixes deep; rule 10 wants it hoisted.
static Container *sceneLayout(const Scene *s) {
    Panel *p = s ? (Panel*) &(*s).base : nullptr;
    return p ? &(*p).base : nullptr;
}

Scene *Scene_0(void) {
    return allocScene(TYPE_SCENE_SINGLETON);
}

Scene *Scene_2(float width, float height) {
    Scene *s = Scene_0();
    if (s)
        Container_setSize(sceneLayout(s), width, height);
    return s;
}

Scene *Scene_3(float width, float height, int mode) {
    Scene *s = Scene_2(width, height);
    if (s && mode >= SCENE_MODE_STRETCH && mode <= SCENE_MODE_PIXEL)
        (*s).mode = mode;
    return s;
}

Scene2D *Scene2D_0(void) {
    return (Scene2D*) allocScene(TYPE_SCENE2D_SINGLETON);
}

Scene3D *Scene3D_0(void) {
    return (Scene3D*) allocScene(TYPE_SCENE3D_SINGLETON);
}

int Scene_getMode(const Scene *s) {
    return s ? (*s).mode : SCENE_MODE_PIXEL;
}

void Scene_setMode(Scene *s, int mode) {
    if (!s || mode < SCENE_MODE_STRETCH || mode > SCENE_MODE_PIXEL)
        return;
    (*s).mode = mode;
    Container_markDirty(sceneLayout(s));
}

float Scene_getVirtualWidth(const Scene *s) {
    return Container_getWidth(sceneLayout(s));
}

float Scene_getVirtualHeight(const Scene *s) {
    return Container_getHeight(sceneLayout(s));
}
