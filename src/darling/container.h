#ifndef DARLING_CONTAINER_H
#define DARLING_CONTAINER_H

#include <stdbool.h>
#include <stdint.h>

#include "c23/constructor.h"
#include "lang/vec4.h"

// darling/container.h — layout base of every darling node
// (Legacy: darling/Container.java, translated under contract-first).
//
// Owns position, size, scale, the two-anchor system, percentage placement,
// z-order and the visible/enabled/dirty/clipping flags. Subclasses EMBED this
// struct as their first member, so a subclass pointer's prefix lines up:
// pass &(*panel).base to any Container accessor.
//
// The two-anchor system (legacy semantics preserved exactly):
//   SELF anchor   (4 corners): initial margin placement against the BASE size.
//   PARENT anchor (9-grid):    resize tracking — the element follows the delta
//                              of this anchor point since its base layout.
//   PIVOT reference (5 points): source-of-truth pivot for placement.
//
// The subtle law: invalidateBase fires ONLY on layout edits. State edits
// (color, hover) must never recapture the base or anchored panels jump
// mid-resize.

typedef struct Container {
    float x, y, w, h;
    float scaleX, scaleY;
    uint32_t anchors;       // low byte: parentAnchor 0..8; high byte: selfAnchor+1 (0 = unset/TOP_LEFT)
    int32_t pivot;          // PIVOT_REFERENCE_*
    float percentX, percentY;
    int32_t z;
    uint8_t visible;
    uint8_t enabled;
    uint8_t dirty;
    uint8_t clipping;
    float baseW, baseH;     // parent size at last layout (resize-delta reference)
    float minW, minH;       // size constraints (default 0,0)
    float maxW, maxH;       // size constraints (default 0 = unset)
} Container;

// Parent anchor: where on the parent the element tracks during resize.
#define CONTAINER_PARENT_ANCHOR_TOP_LEFT      0
#define CONTAINER_PARENT_ANCHOR_TOP_CENTER    1
#define CONTAINER_PARENT_ANCHOR_TOP_RIGHT     2
#define CONTAINER_PARENT_ANCHOR_MIDDLE_LEFT   3
#define CONTAINER_PARENT_ANCHOR_MIDDLE_CENTER 4
#define CONTAINER_PARENT_ANCHOR_MIDDLE_RIGHT  5
#define CONTAINER_PARENT_ANCHOR_BOTTOM_LEFT   6
#define CONTAINER_PARENT_ANCHOR_BOTTOM_CENTER 7
#define CONTAINER_PARENT_ANCHOR_BOTTOM_RIGHT  8

#define CONTAINER_SELF_ANCHOR_TOP_LEFT      0
#define CONTAINER_SELF_ANCHOR_TOP_CENTER    1
#define CONTAINER_SELF_ANCHOR_TOP_RIGHT     2
#define CONTAINER_SELF_ANCHOR_MIDDLE_LEFT   3
#define CONTAINER_SELF_ANCHOR_MIDDLE_CENTER 4
#define CONTAINER_SELF_ANCHOR_MIDDLE_RIGHT  5
#define CONTAINER_SELF_ANCHOR_BOTTOM_LEFT   6
#define CONTAINER_SELF_ANCHOR_BOTTOM_CENTER 7
#define CONTAINER_SELF_ANCHOR_BOTTOM_RIGHT  8

// Pivot reference: the element's source-of-truth point.
#define CONTAINER_PIVOT_REFERENCE_TOP_LEFT      0
#define CONTAINER_PIVOT_REFERENCE_TOP_RIGHT     1
#define CONTAINER_PIVOT_REFERENCE_BOTTOM_LEFT   2
#define CONTAINER_PIVOT_REFERENCE_BOTTOM_RIGHT  3
#define CONTAINER_PIVOT_REFERENCE_CENTER        4

#define CONTAINER_PERCENT_UNSET (-1.0f)

// Constructor: Container() — defaults at origin, TOP_LEFT everything.
Container *Container_0(void);

// Position / size / scale
float Container_getX(const Container *c);
float Container_getY(const Container *c);
float Container_getWidth(const Container *c);
float Container_getHeight(const Container *c);
void Container_setX(Container *c, float x);
void Container_setY(Container *c, float y);
void Container_setWidth(Container *c, float w);
void Container_setHeight(Container *c, float h);
void Container_setLocation(Container *c, float x, float y);
void Container_setSize(Container *c, float w, float h);
void Container_setMinSize(Container *c, float w, float h);
void Container_setMaxSize(Container *c, float w, float h);
float Container_getScaleWidth(const Container *c);
float Container_getScaleHeight(const Container *c);
void Container_setScale(Container *c, float sx, float sy);

// Anchors / pivot / percent
int Container_getParentAnchor(const Container *c);
void Container_setParentAnchor(Container *c, int anchor);
int Container_getSelfAnchor(const Container *c);
void Container_setSelfAnchor(Container *c, int anchor);
int Container_getPivotReference(const Container *c);
void Container_setPivotReference(Container *c, int pivot);
void Container_setCenter(Container *c);
float Container_getPercentX(const Container *c);
float Container_getPercentY(const Container *c);
void Container_setPercentX(Container *c, float pct);
void Container_setPercentY(Container *c, float pct);
bool Container_hasPercentX(const Container *c);
bool Container_hasPercentY(const Container *c);

// Z / state flags
int Container_getZ(const Container *c);
void Container_setZ(Container *c, int z);
bool Container_isVisible(const Container *c);
void Container_setVisible(Container *c, bool visible);
bool Container_isEnabled(const Container *c);
void Container_setEnabled(Container *c, bool enabled);
bool Container_isClipChildren(const Container *c);
void Container_setClipChildren(Container *c, bool clip);
bool Container_isDirty(const Container *c);
void Container_markDirty(Container *c);
void Container_clearDirty(Container *c);

// Resolve layout into a screen rect [x, y, w, h] — dest LAST.
void Container_resolve(Container *c, float parentX, float parentY,
                       float parentW, float parentH, Vec4 *outRect);

// Point-in-resolved-rect test (visible nodes only).
bool Container_hitTest(Container *c, float parentX, float parentY,
                       float parentW, float parentH, float pointX, float pointY);

#endif
