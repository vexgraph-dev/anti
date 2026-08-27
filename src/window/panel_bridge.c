// Include our headers BEFORE any ObjC to avoid CarbonCore's `Collection`
// typedef colliding with our struct Collection (collection.h).
#include <stddef.h>
#include <stdatomic.h>
#include "oop/type.h"
#include "nio/mem.h"
#include "lang/vec4.h"
#include "darling/container.h"
#include "darling/panel.h"
#include "window/window.h"

// src/window/panel_bridge.c — pure-C bridge for IOSurface panel operations.
//
// Each content panel child gets an IOSurface. Vulkan renders into each
// IOSurface independently (async). AppKit composites the CALayers.
// This file iterates children and calls into ObjC PanelCocoa for the
// IOSurface/CALayer plumbing.

// Attach IOSurface backing to ALL children of a content panel.
// Returns the number of IOSurface backings attached.
int anti_AttachPanelIOSurfaceChildren(Window *window, Panel *contentPanel, int width, int height) {
    if (!window || !contentPanel) return 0;
    (void)window;
    int attached = 0;
    size_t childCount = Panel_childCount(contentPanel);
    for (size_t i = 0; i < childCount; i++) {
        Panel *child = Panel_getChild(contentPanel, i);
        if (!child) continue;

        // Get the child's MAX size for IOSurface allocation (fixed, never reallocates)
        int maxW = 0, maxH = 0;
        extern void anti_GetPanelMaxSize(Panel *p, int *outMaxW, int *outMaxH);
        anti_GetPanelMaxSize(child, &maxW, &maxH);
        if (maxW <= 0 || maxH <= 0) {
            // Fallback: use layout rect
            Vec4 rect;
            Container_resolve(&(*child).base, 0.0f, 0.0f, (float)width, (float)height, &rect);
            maxW = (int)(rect.z + 0.5f);
            maxH = (int)(rect.w + 0.5f);
        }
        if (maxW <= 0 || maxH <= 0) continue;

        // Check if already attached
        extern void *PanelCocoa_fromPanel(void *panel);
        if (PanelCocoa_fromPanel(child)) {
            extern bool PanelCocoa_setSize(void *pc, int w, int h);
            PanelCocoa_setSize(PanelCocoa_fromPanel(child), maxW, maxH);
        } else {
            extern void *PanelCocoa_new(void *panel, int w, int h);
            if (PanelCocoa_new(child, maxW, maxH)) attached++;
        }
    }
    return attached;
}

// Resize IOSurface backing for ALL children of a content panel.
// Returns the number of IOSurface backings resized.
int anti_ResizePanelIOSurfaceChildren(Window *window, Panel *contentPanel, int width, int height) {
    if (!window || !contentPanel) return 0;
    (void)window;
    int resized = 0;
    size_t childCount = Panel_childCount(contentPanel);
    for (size_t i = 0; i < childCount; i++) {
        Panel *child = Panel_getChild(contentPanel, i);
        if (!child) continue;
        extern void *PanelCocoa_fromPanel(void *panel);
        void *pc = PanelCocoa_fromPanel(child);
        if (pc) {
            Vec4 rect;
            Container_resolve(&(*child).base, 0.0f, 0.0f, (float)width, (float)height, &rect);
            int w = (int)(rect.z + 0.5f);
            int h = (int)(rect.w + 0.5f);
            if (w > 0 && h > 0) {
                extern bool PanelCocoa_setSize(void *pc, int w, int h);
                PanelCocoa_setSize(pc, w, h);
                resized++;
            }
        }
    }
    return resized;
}

// Render IOSurface backing for ALL children of a content panel.
// The Vulkan renderer calls this to get the IOSurface for each child,
// then renders into it via a framebuffer.
void anti_RenderPanelIOSurfaceChildren(Window *window, Panel *contentPanel) {
    if (!window || !contentPanel) return;
    (void)window;
    size_t childCount = Panel_childCount(contentPanel);
    for (size_t i = 0; i < childCount; i++) {
        Panel *child = Panel_getChild(contentPanel, i);
        if (!child) continue;
        extern void *PanelCocoa_fromPanel(void *panel);
        void *pc = PanelCocoa_fromPanel(child);
        if (pc) {
            extern void PanelCocoa_render(void *pc);
            PanelCocoa_render(pc);
        }
    }
}

// Composite IOSurface-backed children into the window's layer tree.
void anti_CompositeIOSurfaceChildren(Window *window, Panel *contentPanel) {
    (void)window;
    (void)contentPanel;
    // Real implementation is in window_cocoa.m (ObjC)
}

// Layout helper for ObjC side (avoids pulling darling/panel.h into ObjC).
void anti_GetChildLayout(Panel *child, float winW, float winH, float *outX, float *outY, float *outW, float *outH) {
    if (!child || !outX || !outY || !outW || !outH) return;
    Vec4 rect;
    Container_resolve(&(*child).base, 0.0f, 0.0f, winW, winH, &rect);
    *outX = rect.x;
    *outY = rect.y;
    *outW = rect.z;
    *outH = rect.w;
}

// Child iteration helpers for ObjC side (avoids pulling panel.h into ObjC).
int anti_GetChildCount(Panel *contentPanel) {
    if (!contentPanel) return 0;
    return (int)Panel_childCount(contentPanel);
}

Panel *anti_GetChildAt(Panel *contentPanel, int index) {
    if (!contentPanel) return NULL;
    return Panel_getChild(contentPanel, index);
}

// Get panel's max size (first setSize = max, subsequent = clamped current).
void anti_GetPanelMaxSize(Panel *p, int *outMaxW, int *outMaxH) {
    if (!p || !outMaxW || !outMaxH) return;
    *outMaxW = (int)((*p).base.maxW + 0.5f);
    *outMaxH = (int)((*p).base.maxH + 0.5f);
}

// Get panel's current display size.
void anti_GetPanelSize(Panel *p, int *outW, int *outH) {
    if (!p || !outW || !outH) return;
    *outW = (int)((*p).base.w + 0.5f);
    *outH = (int)((*p).base.h + 0.5f);
}

int anti_GetChildParentAnchor(Panel *child) {
    if (!child) return CONTAINER_PARENT_ANCHOR_TOP_LEFT;
    return Container_getParentAnchor(&(*child).base);
}

int anti_GetChildSelfAnchor(Panel *child) {
    if (!child) return CONTAINER_SELF_ANCHOR_TOP_LEFT;
    return Container_getSelfAnchor(&(*child).base);
}
