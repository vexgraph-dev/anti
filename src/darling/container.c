#include "darling/container.h"

#include "nio/mem.h"
#include "oop/type.h"

// darling/container.c — layout core (Legacy: darling/Container.java).

Container *Container_0(void) {
    Container *c = (Container *)Memory_alloc(TYPE_CONTAINER_SINGLETON, sizeof(Container));
    if (!c)
        return NULL;
    (*c).x = 0.0f;
    (*c).y = 0.0f;
    (*c).w = 0.0f;
    (*c).h = 0.0f;
    (*c).scaleX = 1.0f;
    (*c).scaleY = 1.0f;
    (*c).anchors = CONTAINER_PARENT_ANCHOR_TOP_LEFT; // selfAnchor byte 0 = unset (TOP_LEFT)
    (*c).pivot = CONTAINER_PIVOT_REFERENCE_TOP_LEFT;
    (*c).percentX = CONTAINER_PERCENT_UNSET;
    (*c).percentY = CONTAINER_PERCENT_UNSET;
    (*c).z = 0;
    (*c).visible = 1;
    (*c).enabled = 1;
    (*c).dirty = 0;
    (*c).clipping = 0;
    (*c).baseW = 0.0f; // unset -> first resolve captures the reference
    (*c).baseH = 0.0f;
    return c;
}

float Container_getX(const Container *c) { return c ? (*c).x : 0.0f; }
float Container_getY(const Container *c) { return c ? (*c).y : 0.0f; }
float Container_getWidth(const Container *c) { return c ? (*c).w : 0.0f; }
float Container_getHeight(const Container *c) { return c ? (*c).h : 0.0f; }

static void layoutEdited(Container *c) {
    if (!c)
        return;
    (*c).dirty = 1;
    (*c).baseW = 0.0f; // invalidateBase: recapture on next resolve
    (*c).baseH = 0.0f;
}

void Container_setX(Container *c, float x) { if (c) { (*c).x = x; layoutEdited(c); } }
void Container_setY(Container *c, float y) { if (c) { (*c).y = y; layoutEdited(c); } }
void Container_setWidth(Container *c, float w) { if (c) { (*c).w = w; layoutEdited(c); } }
void Container_setHeight(Container *c, float h) { if (c) { (*c).h = h; layoutEdited(c); } }

void Container_setLocation(Container *c, float x, float y) {
    Container_setX(c, x);
    Container_setY(c, y);
}

void Container_setSize(Container *c, float w, float h) {
    Container_setWidth(c, w);
    Container_setHeight(c, h);
}

float Container_getScaleWidth(const Container *c) { return c ? (*c).scaleX : 1.0f; }
float Container_getScaleHeight(const Container *c) { return c ? (*c).scaleY : 1.0f; }

void Container_setScale(Container *c, float sx, float sy) {
    if (!c)
        return;
    (*c).scaleX = sx;
    (*c).scaleY = sy;
    layoutEdited(c);
}

int Container_getParentAnchor(const Container *c) {
    return c ? ((*c).anchors & 0xFFu) : 0;
}

void Container_setParentAnchor(Container *c, int anchor) {
    if (!c || anchor < CONTAINER_PARENT_ANCHOR_TOP_LEFT || anchor > CONTAINER_PARENT_ANCHOR_BOTTOM_RIGHT)
        return;
    (*c).anchors = ((*c).anchors & 0xFFFFFF00u) | ((uint32_t)anchor & 0xFFu);
    layoutEdited(c);
}

int Container_getSelfAnchor(const Container *c) {
    if (!c)
        return CONTAINER_SELF_ANCHOR_TOP_LEFT;
    uint32_t raw = ((*c).anchors >> 8) & 0xFFu;
    return raw == 0 ? CONTAINER_SELF_ANCHOR_TOP_LEFT : (int)raw - 1;
}

void Container_setSelfAnchor(Container *c, int anchor) {
    if (!c || anchor < CONTAINER_SELF_ANCHOR_TOP_LEFT || anchor > CONTAINER_SELF_ANCHOR_BOTTOM_RIGHT)
        return;
    uint32_t parent = (*c).anchors & 0xFFu;
    (*c).anchors = (parent) | (((uint32_t)anchor + 1u) << 8);
    layoutEdited(c);
}

int Container_getPivotReference(const Container *c) {
    return c ? (*c).pivot : CONTAINER_PIVOT_REFERENCE_TOP_LEFT;
}

void Container_setPivotReference(Container *c, int pivot) {
    if (!c || pivot < CONTAINER_PIVOT_REFERENCE_TOP_LEFT || pivot > CONTAINER_PIVOT_REFERENCE_CENTER)
        return;
    (*c).pivot = pivot;
    layoutEdited(c);
}

void Container_setCenter(Container *c) {
    if (!c)
        return;
    Container_setSelfAnchor(c, CONTAINER_SELF_ANCHOR_TOP_LEFT);
    Container_setPivotReference(c, CONTAINER_PIVOT_REFERENCE_CENTER);
    (*c).percentX = 0.5f;
    (*c).percentY = 0.5f;
    (*c).dirty = 1;
}

float Container_getPercentX(const Container *c) { return c ? (*c).percentX : CONTAINER_PERCENT_UNSET; }
float Container_getPercentY(const Container *c) { return c ? (*c).percentY : CONTAINER_PERCENT_UNSET; }

void Container_setPercentX(Container *c, float pct) { if (c) { (*c).percentX = pct; (*c).dirty = 1; } }
void Container_setPercentY(Container *c, float pct) { if (c) { (*c).percentY = pct; (*c).dirty = 1; } }

bool Container_hasPercentX(const Container *c) { return Container_getPercentX(c) >= 0.0f; }
bool Container_hasPercentY(const Container *c) { return Container_getPercentY(c) >= 0.0f; }

int Container_getZ(const Container *c) { return c ? (*c).z : 0; }
void Container_setZ(Container *c, int z) { if (c) { (*c).z = z; (*c).dirty = 1; } }

bool Container_isVisible(const Container *c) { return c && (*c).visible != 0; }
bool Container_isEnabled(const Container *c) { return c && (*c).enabled != 0; }
bool Container_isClipChildren(const Container *c) { return c && (*c).clipping != 0; }
bool Container_isDirty(const Container *c) { return c && (*c).dirty != 0; }

void Container_setVisible(Container *c, bool visible) { if (c) { (*c).visible = visible ? 1 : 0; (*c).dirty = 1; } }
void Container_setEnabled(Container *c, bool enabled) { if (c) { (*c).enabled = enabled ? 1 : 0; } }
void Container_setClipChildren(Container *c, bool clip) { if (c) { (*c).clipping = clip ? 1 : 0; (*c).dirty = 1; } }

void Container_markDirty(Container *c) {
    if (c)
        (*c).dirty = 1;
}

void Container_clearDirty(Container *c) {
    if (c)
        (*c).dirty = 0;
}

void Container_resolve(Container *c, float parentX, float parentY,
                       float parentW, float parentH, Vec4 *outRect) {
    if (!c || !outRect)
        return;

    float sw = (*c).w * (*c).scaleX;
    float sh = (*c).h * (*c).scaleY;

    // Resize-delta reference: captured once, and again only after layout edits.
    float baseW = (*c).baseW;
    float baseH = (*c).baseH;
    if (baseW <= 0.0f || baseH <= 0.0f) {
        baseW = parentW;
        baseH = parentH;
        (*c).baseW = baseW;
        (*c).baseH = baseH;
    }
    (*c).dirty = 0;

    float x = (*c).x;
    float y = (*c).y;

    // Self anchor: initial margin placement against the BASE size.
    float selfX = x;
    float selfY = y;
    switch (Container_getSelfAnchor(c)) {
        case CONTAINER_SELF_ANCHOR_TOP_RIGHT:
            selfX = baseW - sw - x;
            break;
        case CONTAINER_SELF_ANCHOR_BOTTOM_LEFT:
            selfY = baseH - sh - y;
            break;
        case CONTAINER_SELF_ANCHOR_BOTTOM_RIGHT:
            selfX = baseW - sw - x;
            selfY = baseH - sh - y;
            break;
        default:
            break;
    }

    // Parent anchor: follow this point's movement since the base layout.
    float dW = parentW - baseW;
    float dH = parentH - baseH;
    float dx = 0.0f;
    float dy = 0.0f;
    switch (Container_getParentAnchor(c)) {
        case CONTAINER_PARENT_ANCHOR_TOP_CENTER:    dx = dW * 0.5f; break;
        case CONTAINER_PARENT_ANCHOR_TOP_RIGHT:     dx = dW; break;
        case CONTAINER_PARENT_ANCHOR_MIDDLE_LEFT:   dy = dH * 0.5f; break;
        case CONTAINER_PARENT_ANCHOR_MIDDLE_CENTER: dx = dW * 0.5f; dy = dH * 0.5f; break;
        case CONTAINER_PARENT_ANCHOR_MIDDLE_RIGHT:  dx = dW; dy = dH * 0.5f; break;
        case CONTAINER_PARENT_ANCHOR_BOTTOM_LEFT:   dy = dH; break;
        case CONTAINER_PARENT_ANCHOR_BOTTOM_CENTER: dx = dW * 0.5f; dy = dH; break;
        case CONTAINER_PARENT_ANCHOR_BOTTOM_RIGHT:  dx = dW; dy = dH; break;
        default:
            break;
    }

    float screenX = selfX + dx + parentX;
    float screenY = selfY + dy + parentY;

    // Percent overrides placement against the LIVE parent size.
    if (Container_hasPercentX(c))
        screenX = parentX + (*c).percentX * parentW;
    if (Container_hasPercentY(c))
        screenY = parentY + (*c).percentY * parentH;

    // Pivot shift: the pivot point lands at the resolved target.
    float offX = 0.0f;
    float offY = 0.0f;
    switch ((*c).pivot) {
        case CONTAINER_PIVOT_REFERENCE_TOP_RIGHT:    offX = sw; break;
        case CONTAINER_PIVOT_REFERENCE_BOTTOM_LEFT:  offY = sh; break;
        case CONTAINER_PIVOT_REFERENCE_BOTTOM_RIGHT: offX = sw; offY = sh; break;
        case CONTAINER_PIVOT_REFERENCE_CENTER:       offX = sw * 0.5f; offY = sh * 0.5f; break;
        default:
            break;
    }
    screenX -= offX;
    screenY -= offY;

    Vec4_set(outRect, screenX, screenY, sw, sh);
}

bool Container_hitTest(Container *c, float parentX, float parentY,
                       float parentW, float parentH, float pointX, float pointY) {
    if (!Container_isVisible(c))
        return false;
    Vec4 rect;
    Container_resolve(c, parentX, parentY, parentW, parentH, &rect);
    // legacy stores rects as [x, y, w, h] in Vec4 slots -> width=.z height=.w
    return pointX >= rect.x && pointX < rect.x + rect.z
        && pointY >= rect.y && pointY < rect.y + rect.w;
}
