#include "darling/panel.h"

#include "annotation/incomplete.h"
#include "nio/mem.h"
#include "oop/type.h"

// darling/panel.c — panel + tree + view model (Legacy: darling/Panel.java).

#define PANEL_CHILDREN_INITIAL 4

Panel *Panel_0(void) {
    Panel *p = (Panel *)Memory_alloc(TYPE_PANEL_SINGLETON, sizeof(Panel));
    if (!p)
        return nullptr;
    Container *b = Container_0();
    if (!b) {
        Memory_free(p);
        return nullptr;
    }
    // adopt the container block's contents into our prefix, then free the shell
    *(&(*p).base) = (*b);
    Memory_free(b);

    (*p).color = PANEL_COLOR_CLEAR;
    (*p).filters = nullptr;
    (*p).image = nullptr;
    (*p).renderHandler = nullptr;
    (*p).source = nullptr;
    (*p).parent = nullptr;
    (*p).children = nullptr;
    return p;
}

Panel *Panel_1(Panel *parent) {
    Panel *p = Panel_0();
    if (p && parent)
        Panel_addContainer(parent, p);
    return p;
}

uint32_t Panel_getBackgroundColor(const Panel *p) {
    return p ? (*p).color : PANEL_COLOR_CLEAR;
}

void Panel_setBackgroundColor(Panel *p, uint32_t color) {
    if (!p)
        return;
    (*p).color = color;
    Container_markDirty(&(*p).base);
}

void Panel_setBackgroundColorRGBA(Panel *p, uint8_t r, uint8_t g, uint8_t b, uint8_t a) {
    Panel_setBackgroundColor(p, ((uint32_t)a << 24) | ((uint32_t)r << 16)
        | ((uint32_t)g << 8) | (uint32_t)b);
}

// Method-slot accessors: setting a handler is the @Override; nullptr restores
// the renderer default. Marked dirty so every holder re-renders this tick.
Panel_RenderFn Panel_getRenderHandler(const Panel *p) {
    return p ? (*p).renderHandler : nullptr;
}

void Panel_setRenderHandler(Panel *p, Panel_RenderFn fn) {
    if (!p)
        return;
    (*p).renderHandler = fn;
    Container_markDirty(&(*p).base);
}

void Panel_setBackgroundColorAndMark(Panel *p, uint32_t color) {
    Panel_setBackgroundColor(p, color);
}

void *Panel_getImage(const Panel *p) {
    if (!p)
        return nullptr;
    const Panel *src = (*p).source;
    return src ? Panel_getImage(src) : (*p).image;
}

void Panel_setImage(Panel *p, void *image) {
    if (!p)
        return;
    Panel *src = (*p).source;
    if (src) {
        // write-through to canonical, fan out dirt to every holder
        (*src).image = image;
        Container_markDirty(&(*src).base);
        if ((*src).children) {
            size_t n = List_size((*src).children);
            for (size_t i = 0; i < n; i++) {
                Panel *holder = (Panel *)List_get((*src).children, i);
                Container_markDirty(&(*holder).base);
            }
        }
        return;
    }
    (*p).image = image;
    Container_markDirty(&(*p).base);
}

void *Panel_getFilters(const Panel *p) {
    if (!p)
        return nullptr;
    const Panel *src = (*p).source;
    return src ? Panel_getFilters(src) : (*p).filters;
}

void Panel_setFilters(Panel *p, void *filters) {
    if (!p)
        return;
    Panel *src = (*p).source;
    if (src)
        Panel_setFilters(src, filters);
    else
        (*p).filters = filters;
    Container_markDirty(&(*p).base);
}

const Panel *Panel_getSource(const Panel *p) {
    return p ? (*p).source : nullptr;
}

int Panel_refCount(const Panel *p) {
    // v1: ref-set tracked implicitly via source back-refs is not yet built;
    // count holders by walking? Contract keeps legacy Set — deferred until a
    // workload needs it. Reported as 0 for now.
    ;;INCOMPLETE // parent-ref set lands with the damage-rect walker
    (void)p;
    return 0;
}

Panel *Panel_getParent(const Panel *p) {
    return p ? (*p).parent : nullptr;
}

bool Panel_hasParent(const Panel *p) {
    return p && (*p).parent != nullptr;
}

size_t Panel_childCount(const Panel *p) {
    return (p && (*p).children) ? List_size((*p).children) : 0;
}

bool Panel_hasChildren(const Panel *p) {
    return Panel_childCount(p) > 0;
}

Panel *Panel_getChild(const Panel *p, size_t index) {
    if (!p || !(*p).children || index >= List_size((*p).children))
        return nullptr;
    return (Panel *)List_get((*p).children, index);
}

bool Panel_containsChild(const Panel *p, const Panel *child) {
    if (!p || !(*p).children || !child)
        return false;
    size_t n = List_size((*p).children);
    for (size_t i = 0; i < n; i++) {
        if ((Panel *)List_get((*p).children, i) == child)
            return true;
    }
    return false;
}

// Detach from current parent so the tree stays consistent.
static void detachFromParent(Panel *child) {
    Panel *old = (*child).parent;
    if (old)
        Panel_removeChild(old, child);
}

void Panel_addContainer(Panel *p, Panel *child) {
    if (!p || !child || p == child)
        return;
    detachFromParent(child);
    (*child).parent = p;

    if (!(*p).children)
        (*p).children = List_allocate(ID_LONG, PANEL_CHILDREN_INITIAL);
    if (!(*p).children) {
        (*child).parent = nullptr;
        return;
    }
    List_add((*p).children, (uint64_t)(uintptr_t)child);
    Container_markDirty(&(*p).base);
    Container_markDirty(&(*child).base);
}

bool Panel_removeChild(Panel *p, Panel *child) {
    if (!p || !child || !(*p).children)
        return false;
    size_t n = List_size((*p).children);
    for (size_t i = 0; i < n; i++) {
        if ((Panel *)List_get((*p).children, i) == (const Panel *)child) {
            List_remove((*p).children, i);
            if ((*child).parent == p)
                (*child).parent = nullptr;
            Container_markDirty(&(*p).base);
            Container_markDirty(&(*child).base);
            return true;
        }
    }
    return false;
}

Panel *Panel_add(Panel *parent, const Panel *node) {
    if (!parent || !node || parent == node)
        return nullptr;

    Panel *copy = Panel_0();
    if (!copy)
        return nullptr;

    // structural deep copy: layout is its own
    Container *cb = &(*copy).base;
    const Container *nb = &(*((Panel *)node)).base;
    (*cb).x = (*nb).x;
    (*cb).y = (*nb).y;
    (*cb).w = (*nb).w;
    (*cb).h = (*nb).h;
    (*cb).scaleX = (*nb).scaleX;
    (*cb).scaleY = (*nb).scaleY;
    (*cb).anchors = (*nb).anchors;
    (*cb).pivot = (*nb).pivot;
    (*cb).z = (*nb).z;
    (*cb).visible = (*nb).visible;
    (*cb).enabled = (*nb).enabled;
    (*cb).clipping = (*nb).clipping;
    (*cb).percentX = (*nb).percentX;
    (*cb).percentY = (*nb).percentY;
    (*copy).color = (*node).color;
    // behavior travels with structure: a view renders exactly like its source
    (*copy).renderHandler = (*node).renderHandler;

    // payloads alias through the source slot (read/write-through above)
    (*copy).source = (Panel *)node;

    // deep-copy children
    size_t n = Panel_childCount(node);
    for (size_t i = 0; i < n; i++)
        Panel_add(copy, Panel_getChild(node, i));

    Panel_addContainer(parent, copy);
    Container_markDirty(cb);
    return copy;
}

// Note: children lists are owned by each parent; Panel_free would need the
// pool-wide walker. Deferred to the scene teardown pass.
