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

typedef struct Panel {
    Container base;         // embedded prefix — pass &(*panel).base upward
    uint32_t color;         // 0xAARRGGBB
    void *filters;          // render-graph slot (@Draft placeholder)
    void *image;            // payload slot (shared through views)
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
