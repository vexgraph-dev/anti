#ifndef DARLING_PANEL_H
#define DARLING_PANEL_H

#include <stdbool.h>
#include <stdint.h>

#include "c11/constructor.h"
#include "darling/container.h"
#include "struct/list.h"
#include "struct/set.h"

// darling/panel.h — the UI panel: Container layout + background color +
// the parent/child tree (Legacy: darling/Panel.java, contract-first).
//
// The VIEW model: Panel_add deep-copies STRUCTURE but aliases shared payloads
// (image/filters) BY POINTER via the source slot; dirty flags fan out through
// the parent-ref set so every holder of a view re-renders.
//
// METHOD SLOTS ("@Override" in C): Java override swaps a vtable entry at
// class load; here it is an explicit function-pointer slot per INSTANCE.
// The setter is the @Override annotation, NULL restores the built-in
// default, and callers never read slots directly — they call the dispatcher
// (Panel_render), which routes handler-or-default. Subclasses inherit the
// slots by embedding (Scene3D -> Scene -> Panel), no vtable needed.

struct Panel;

typedef void (*Panel_RenderFn)(struct Panel *panel, void *renderer,
                               void *cmdBuffer, float x, float y,
                               float w, float h);

typedef struct Panel {
    Container base;         // embedded prefix — pass &(*panel).base upward
    uint32_t color;         // 0xAARRGGBB
    void *filters;          // render-graph slot (@Draft placeholder)
    void *image;            // payload slot (shared through views)
    Panel_RenderFn renderHandler; // draw override; NULL = renderer default
    struct Panel *source;   // canonical panel this view proxies; NULL = owns
    struct Panel *parent;   // NULL = root
    List *children;
} Panel;

#define PANEL_COLOR_WHITE 0xFFFFFFFFu
#define PANEL_COLOR_BLACK 0xFF000000u
#define PANEL_COLOR_CLEAR 0x00000000u

// Constructors:
//   Panel()          — detached bare panel
//   Panel(parent)    — created and attached
Panel *Panel_0(void);
Panel *Panel_1(Panel *parent);

#define Panel(...) CONSTRUCTOR_DISPATCH(Panel, ##__VA_ARGS__)

// Background color (0xAARRGGBB).
uint32_t Panel_getBackgroundColor(const Panel *p);
void Panel_setBackgroundColor(Panel *p, uint32_t color);
void Panel_setBackgroundColorRGBA(Panel *p, uint8_t r, uint8_t g, uint8_t b, uint8_t a);

// Draw override (see METHOD SLOTS above). The renderer hands the panel a
// drawable-clipped pixel rect inside an open render pass; the handler
// records whatever it wants into cmdBuffer. NULL = built-in solid quad.
Panel_RenderFn Panel_getRenderHandler(const Panel *p);
void Panel_setRenderHandler(Panel *p, Panel_RenderFn fn);

// Layout facade — the delegation chain ends here. Every accessor below is a
// one-hop static inline to the embedded Container, so call sites never write
// &(*panel).base for common edits. Subclass levels re-export the same names
// over their embedded prefix (Scene_setLocation -> Panel_setLocation ->
// Container_setLocation), which is Java's inherited methods without a
// vtable: static binding, zero runtime cost, type-checked at each level.
static inline void Panel_setLocation(Panel *p, float x, float y)
    { if (p) Container_setLocation(&(*p).base, x, y); }
static inline void Panel_setSize(Panel *p, float w, float h)
    { if (p) Container_setSize(&(*p).base, w, h); }
static inline void Panel_setMinSize(Panel *p, float w, float h)
    { if (p) Container_setMinSize(&(*p).base, w, h); }
static inline void Panel_setMaxSize(Panel *p, float w, float h)
    { if (p) Container_setMaxSize(&(*p).base, w, h); }
static inline void Panel_setParentAnchor(Panel *p, int anchor)
    { if (p) Container_setParentAnchor(&(*p).base, anchor); }
static inline void Panel_setSelfAnchor(Panel *p, int anchor)
    { if (p) Container_setSelfAnchor(&(*p).base, anchor); }
static inline void Panel_setVisible(Panel *p, bool visible)
    { if (p) Container_setVisible(&(*p).base, visible); }
static inline bool Panel_isVisible(const Panel *p)
    { return p && Container_isVisible(&(*p).base); }
static inline void Panel_setZ(Panel *p, int z)
    { if (p) Container_setZ(&(*p).base, z); }

// Shared payload slots (read/write-through to the canonical source on views).
void *Panel_getImage(const Panel *p);
void Panel_setImage(Panel *p, void *image);
void *Panel_getFilters(const Panel *p);
void Panel_setFilters(Panel *p, void *filters);

// Tree.
Panel *Panel_getParent(const Panel *p);
bool Panel_hasParent(const Panel *p);
size_t Panel_childCount(const Panel *p);
Panel *Panel_getChild(const Panel *p, size_t index);
bool Panel_hasChildren(const Panel *p);
bool Panel_containsChild(const Panel *p, const Panel *child);
void Panel_addContainer(Panel *p, Panel *child);
bool Panel_removeChild(Panel *p, Panel *child);

// Structural deep copy with aliased payloads; the copy is attached to parent.
Panel *Panel_add(Panel *parent, const Panel *node);

// View bookkeeping.
const Panel *Panel_getSource(const Panel *p);
int Panel_refCount(const Panel *p);

// Layout passthroughs live on Container: use &(*panel).base.
// Convenience here for the two most common:
void Panel_setBackgroundColorAndMark(Panel *p, uint32_t color); // legacy parity alias

#endif
