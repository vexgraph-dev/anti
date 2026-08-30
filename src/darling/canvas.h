#ifndef DARLING_CANVAS_H
#define DARLING_CANVAS_H

#include <stdbool.h>
#include <stdint.h>

#include "c23/constructor.h"
#include "darling/container.h"
#include "lang/mat4.h"
#include "lang/vec2.h"

// darling/canvas.h — the flat 2D layout root (Legacy: darling/Canvas.java).
//
// One canonical coordinate space the whole UI resolves into: a fixed virtual
// resolution independent of the framebuffer and backing scale. setLocation
// therefore means something stable — a node at canvas (100, 60) stays there
// no matter how the window changes; only the projection moves.
//
// Deviation from legacy (flagged): Java kept these as volatile statics; the
// C port makes Canvas an explicit handle the compositor owns and passes.

typedef struct Canvas {
    float virtualWidth;   // <= 0 = follow the framebuffer on that axis
    float virtualHeight;
    int mode;             // CANVAS_MODE_*
    float dpiScale;
} Canvas;

#define CANVAS_MODE_STRETCH 0 // whole canvas -> whole window (asymmetric)
#define CANVAS_MODE_FIT     1 // uniform scale, letterboxed + centered
#define CANVAS_MODE_PIXEL   2 // 1 canvas unit == 1 window px, top-left pinned

Canvas *Canvas_0(void);   // defaults: follow-framebuffer, PIXEL, dpi 1.0

void Canvas_setVirtualSize(Canvas *c, float width, float height);
float Canvas_getVirtualWidth(const Canvas *c);
float Canvas_getVirtualHeight(const Canvas *c);
void Canvas_setMode(Canvas *c, int mode);
int Canvas_getMode(const Canvas *c);
void Canvas_setDpiScale(Canvas *c, float scale);
float Canvas_getDpiScale(const Canvas *c);

// Visible canvas rect within the framebuffer [screenX, screenY, w, h] — dest last.
void Canvas_visibleRect(const Canvas *c, float fbW, float fbH, Vec4 *outRect);

// Y-down orthographic projection, canvas units -> NDC — dest LAST.
bool Canvas_buildProjection(const Canvas *c, float fbW, float fbH, Mat4 *dest);

// Resolve a root node against the canvas's visible rect — dest LAST.
// Dispatches on the node's own block-header class (Picture vs Container),
// exactly like legacy's runtime-class dispatch.
void Canvas_resolveRoot(const Canvas *c, void *node, float fbW, float fbH, Vec4 *outRect);

// Window-space point -> canvas units [x, y]; true when inside the canvas.
bool Canvas_windowToCanvas(const Canvas *c, float winX, float winY,
                           float fbW, float fbH, Vec2 *outPoint);

#endif
