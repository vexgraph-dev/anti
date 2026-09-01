#ifndef OBJC_PANEL_COCOA_H
#define OBJC_PANEL_COCOA_H

#include <stdbool.h>

// objc/panel_cocoa.h — IOSurface-backed panel compositor.
//
// Each cocoa-backed panel owns:
//   - an IOSurfaceRef  : the GPU buffer (shared with AppKit for compositing)
//   - a CALayer        : AppKit composites this; contents = the IOSurface
//
// Rendering model (retained-mode UI):
//   - IOSurface panels are NOT immediate. They store a buffer and composite
//     their whole subtree into it as one stream. Cheap when nothing changes;
//     the "tax" is one extra blit per nested Vulkan child.
//   - Vulkan children render into their OWN IOSurface on demand (immediate),
//     and the parent composites that IOSurface into its buffer.
//
// Lifecycle:
//   Panel *p = Panel();                    // existing darling API, unchanged
//   PanelCocoa *pc = PanelCocoa_new(p, 640, 400);  // attach IOSurface backing
//   PanelCocoa_setSize(pc, 800, 600);     // reallocates IOSurface
//   CALayer *layer = PanelCocoa_layer(pc); // add to window's layer tree
//
// The Panel handle is what darling code already holds; PanelCocoa is the
// platform-specific backing that makes it display natively.

typedef struct PanelCocoa PanelCocoa;

// Attach IOSurface backing to an existing Panel. Size is the initial backing
// size in pixels. Returns nullptr on failure.
PanelCocoa *PanelCocoa_new(void *panel, int width, int height);

// Free the IOSurface backing. The Panel itself is owned by the caller.
void PanelCocoa_free(PanelCocoa *pc);

// Reallocate the IOSurface at a new size. Preserves the Panel pointer.
// Returns false if allocation failed (old backing survives).
bool PanelCocoa_setSize(PanelCocoa *pc, int width, int height);

// The CALayer for this panel. Add it to the window's content view layer
// (or a parent panel's layer). AppKit composites the IOSurface automatically
// when layer.contents is set — no Metal code needed.
void *PanelCocoa_layer(PanelCocoa *pc);  // CALayer * (void * to avoid ObjC in C headers)

// Current backing size.
int PanelCocoa_width(const PanelCocoa *pc);
int PanelCocoa_height(const PanelCocoa *pc);

// The IOSurface backing. For Vulkan import/export or direct CPU access.
void *PanelCocoa_surface(PanelCocoa *pc);  // IOSurfaceRef

// Render the panel's subtree into the IOSurface. For now: CPU paint via
// Raster (solid color from panel->color). Later: Vulkan render path.
void PanelCocoa_markDirty(PanelCocoa *pc);
bool PanelCocoa_isDirty(const PanelCocoa *pc);

// Set darling anchor settings (parent anchor and self anchor) on the CALayer.
// Updates layer.contentsGravity and layer.autoresizingMask accordingly.
void PanelCocoa_setAnchors(PanelCocoa *pc, int parentAnchor, int selfAnchor);

// Lookup: retrieve the PanelCocoa backing for a Panel. Returns nullptr if the
// panel has no IOSurface backing. Used by the window bridge.
void *PanelCocoa_fromPanel(void *panel); // Panel * → PanelCocoa *

#endif
