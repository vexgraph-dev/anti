#import <QuartzCore/CALayer.h>
#import <IOSurface/IOSurface.h>
#import <stdatomic.h>
#include <string.h>

#include "panel_cocoa.h"

// Forward declare to avoid any ObjC umbrella header pulling in a Collection
// typedef that collides with our struct Collection (collection.h).
struct Panel;
uint32_t Panel_getBackgroundColor(const struct Panel *p);

// Registry: maps Panel * → PanelCocoa *. Small fixed array — panel counts
// are tiny (a handful per window). Linear scan on lookup.
#define kMaxPanels 16
static struct {
    void *panel;        // Panel *
    PanelCocoa *pc;
} s_registry[kMaxPanels] = {0};

// objc/panel_cocoa.m — IOSurface-backed panel compositor.
//
// Each cocoa-backed panel owns an IOSurface (GPU buffer) + CALayer (AppKit
// composite target). The panel subtree is painted into the IOSurface; AppKit
// composites the layer. No Metal code — just IOSurface + CALayer.

struct PanelCocoa {
    void *panel;            // Panel * (opaque to ObjC side)
    IOSurfaceRef surface;   // GPU buffer backing (allocated at MAX size, never reallocates)
    CALayer *layer;         // AppKit composite target
    int width, height;      // current display size (what's shown in window)
    int maxWidth, maxHeight; // max IOSurface size (fixed allocation)
    _Atomic bool dirty;     // needs repaint
};

// Pixel format: BGRA8 — safe for both Vulkan import/export and AppKit.
static const int kBytesPerPixel = 4;

static IOSurfaceRef makeSurface(int width, int height) {
    if (width <= 0 || height <= 0) return NULL;
    CFMutableDictionaryRef props = CFDictionaryCreateMutable(
        kCFAllocatorDefault, 0,
        &kCFTypeDictionaryKeyCallBacks,
        &kCFTypeDictionaryValueCallBacks);
    if (!props) return NULL;

    // IOSurface properties
    int bpr = width * kBytesPerPixel;
    CFNumberRef w = CFNumberCreate(kCFAllocatorDefault, kCFNumberIntType, &width);
    CFNumberRef h = CFNumberCreate(kCFAllocatorDefault, kCFNumberIntType, &height);
    CFNumberRef bprNum = CFNumberCreate(kCFAllocatorDefault, kCFNumberIntType, &bpr);
    int format = 'BGRA'; // kCVPixelFormatType_32BGRA
    CFNumberRef fmt = CFNumberCreate(kCFAllocatorDefault, kCFNumberIntType, &format);

    CFDictionarySetValue(props, kIOSurfaceWidth, w);
    CFDictionarySetValue(props, kIOSurfaceHeight, h);
    CFDictionarySetValue(props, kIOSurfaceBytesPerRow, bprNum);
    
    int bpe = kBytesPerPixel;
    CFNumberRef bpeNum = CFNumberCreate(kCFAllocatorDefault, kCFNumberIntType, &bpe);
    CFDictionarySetValue(props, kIOSurfaceBytesPerElement, bpeNum);
    
    CFDictionarySetValue(props, kIOSurfacePixelFormat, fmt);
    // Allocate in VRAM so Vulkan can import without a copy
    int pool = 1; // kIOSurfaceCacheModeWriteThrough (write-combined-ish)
    CFNumberRef cache = CFNumberCreate(kCFAllocatorDefault, kCFNumberIntType, &pool);
    CFDictionarySetValue(props, kIOSurfaceCacheMode, cache);

    IOSurfaceRef surface = IOSurfaceCreate(props);

    CFRelease(w); CFRelease(h); CFRelease(bprNum);
    CFRelease(fmt); CFRelease(cache); CFRelease(props);
    return surface;
}

PanelCocoa *PanelCocoa_new(void *panel, int width, int height) {
    if (!panel || width <= 0 || height <= 0) return NULL;

    PanelCocoa *pc = (PanelCocoa *)calloc(1, sizeof(PanelCocoa));
    if (!pc) return NULL;

    pc->panel = panel;
    pc->width = width;
    pc->height = height;
    pc->maxWidth = width;
    pc->maxHeight = height;
    atomic_init(&pc->dirty, true);

    // Allocate IOSurface at MAX size (fixed, never reallocates)
    pc->surface = makeSurface(width, height);
    if (!pc->surface) {
        free(pc);
        return NULL;
    }

    pc->layer = [[CALayer alloc] init];
    pc->layer.contentsGravity = kCAGravityTopLeft;
    pc->layer.geometryFlipped = YES; // Flip the layer so the Vulkan top-down IOSurface renders right-side up
    pc->layer.contents = (__bridge id)pc->surface;
    pc->layer.opaque = NO;
    pc->layer.anchorPoint = CGPointMake(0, 0);
    pc->layer.drawsAsynchronously = NO;
    pc->layer.contentsRect = CGRectMake(0, 0, 1, 1); // show full surface

    // Register in the lookup table
    for (int i = 0; i < kMaxPanels; i++) {
        if (s_registry[i].panel == NULL) {
            s_registry[i].panel = panel;
            s_registry[i].pc = pc;
            break;
        }
    }

    return pc;
}

void PanelCocoa_free(PanelCocoa *pc) {
    if (!pc) return;
    // Unregister from lookup table
    for (int i = 0; i < kMaxPanels; i++) {
        if (s_registry[i].pc == pc) {
            s_registry[i].panel = NULL;
            s_registry[i].pc = NULL;
            break;
        }
    }
    if (pc->layer) [pc->layer removeFromSuperlayer];
    if (pc->surface) CFRelease(pc->surface);
    free(pc);
}

bool PanelCocoa_setSize(PanelCocoa *pc, int width, int height) {
    if (!pc || width <= 0 || height <= 0) return false;
    if (width == pc->width && height == pc->height) return true;

    // Update display size (IOSurface stays at max size, never reallocates)
    pc->width = width;
    pc->height = height;

    // Update contentsRect to show only the current-size portion of the max-size IOSurface
    if (pc->maxWidth > 0 && pc->maxHeight > 0) {
        CGFloat rectX = 0.0f;
        CGFloat rectY = 0.0f;
        CGFloat rectW = (CGFloat)width / (CGFloat)pc->maxWidth;
        CGFloat rectH = (CGFloat)height / (CGFloat)pc->maxHeight;
        pc->layer.contentsRect = CGRectMake(rectX, rectY, rectW, rectH);
    }

    atomic_store(&pc->dirty, true);
    return true;
}

void *PanelCocoa_layer(PanelCocoa *pc) {
    return pc ? (__bridge void *)pc->layer : NULL;
}

int PanelCocoa_width(const PanelCocoa *pc) { return pc ? pc->width : 0; }
int PanelCocoa_height(const PanelCocoa *pc) { return pc ? pc->height : 0; }
void *PanelCocoa_surface(PanelCocoa *pc) { return pc ? (void *)pc->surface : NULL; }

void PanelCocoa_markDirty(PanelCocoa *pc) {
    if (pc) atomic_store(&pc->dirty, true);
}

// Lookup: retrieve the PanelCocoa backing for a Panel. Returns NULL if the
// panel has no IOSurface backing. Used by the window bridge.
void *PanelCocoa_fromPanel(void *panel) {
    if (!panel) return NULL;
    for (int i = 0; i < kMaxPanels; i++) {
        if (s_registry[i].panel == panel) return s_registry[i].pc;
    }
    return NULL;
}

void PanelCocoa_setAnchors(PanelCocoa *pc, int parentAnchor, int selfAnchor) {
    if (!pc || !pc->layer) return;

    // Port selfAnchor to CoreAnimation anchorPoint
    // (0,0) is top-left in flipped coordinates, (1,1) is bottom-right
    CGPoint anchorPoint = CGPointMake(0.0, 0.0);
    switch (selfAnchor) {
        case 1: anchorPoint = CGPointMake(0.5, 0.0); break; // TOP_CENTER
        case 2: anchorPoint = CGPointMake(1.0, 0.0); break; // TOP_RIGHT
        case 3: anchorPoint = CGPointMake(0.0, 0.5); break; // MIDDLE_LEFT
        case 4: anchorPoint = CGPointMake(0.5, 0.5); break; // MIDDLE_CENTER
        case 5: anchorPoint = CGPointMake(1.0, 0.5); break; // MIDDLE_RIGHT
        case 6: anchorPoint = CGPointMake(0.0, 1.0); break; // BOTTOM_LEFT
        case 7: anchorPoint = CGPointMake(0.5, 1.0); break; // BOTTOM_CENTER
        case 8: anchorPoint = CGPointMake(1.0, 1.0); break; // BOTTOM_RIGHT
        default: break; // TOP_LEFT
    }

    // Port parentAnchor to AppKit autoresizingMask
    // (Flexible margins push from the opposite side. e.g. MinXMargin pushes from left -> anchors to right)
    CAAutoresizingMask mask = kCALayerMaxXMargin | kCALayerMaxYMargin; // Default: Top-Left (Right & Bottom flexible)
    switch (parentAnchor) {
        case 0: mask = kCALayerMaxXMargin | kCALayerMaxYMargin; break; // TOP_LEFT
        case 1: mask = kCALayerMinXMargin | kCALayerMaxXMargin | kCALayerMaxYMargin; break; // TOP_CENTER
        case 2: mask = kCALayerMinXMargin | kCALayerMaxYMargin; break; // TOP_RIGHT
        case 3: mask = kCALayerMaxXMargin | kCALayerMinYMargin | kCALayerMaxYMargin; break; // MIDDLE_LEFT
        case 4: mask = kCALayerMinXMargin | kCALayerMaxXMargin | kCALayerMinYMargin | kCALayerMaxYMargin; break; // MIDDLE_CENTER
        case 5: mask = kCALayerMinXMargin | kCALayerMinYMargin | kCALayerMaxYMargin; break; // MIDDLE_RIGHT
        case 6: mask = kCALayerMaxXMargin | kCALayerMinYMargin; break; // BOTTOM_LEFT
        case 7: mask = kCALayerMinXMargin | kCALayerMaxXMargin | kCALayerMinYMargin; break; // BOTTOM_CENTER
        case 8: mask = kCALayerMinXMargin | kCALayerMinYMargin; break; // BOTTOM_RIGHT
        default: break;
    }

    pc->layer.anchorPoint = anchorPoint;
    pc->layer.autoresizingMask = mask;
}

bool PanelCocoa_isDirty(const PanelCocoa *pc) {
    return pc ? atomic_load(&pc->dirty) : false;
}

void PanelCocoa_render(PanelCocoa *pc) {
    if (!pc || !pc->surface) return;
    if (!atomic_load(&pc->dirty)) return; // nothing changed

    // CPU paint path: solid color from the panel's background color.
    // Lock the IOSurface, paint with Raster, unlock.
    IOReturn lock = IOSurfaceLock(pc->surface, 0, NULL);
    if (lock != kIOReturnSuccess) return;

    void *base = IOSurfaceGetBaseAddress(pc->surface);
    size_t row = IOSurfaceGetBytesPerRow(pc->surface);
    int w = (int)IOSurfaceGetWidth(pc->surface);
    int h = (int)IOSurfaceGetHeight(pc->surface);

    if (base && w > 0 && h > 0) {
        // Clear to transparent first
        memset(base, 0, row * h);

        // Decode panel color (0xAARRGGBB)
        struct Panel *p = (struct Panel *)pc->panel;
        uint32_t color = Panel_getBackgroundColor(p);
        if (color != 0) {
            uint8_t a = (uint8_t)(color >> 24);
            uint8_t r = (uint8_t)(color >> 16);
            uint8_t g = (uint8_t)(color >> 8);
            uint8_t b = (uint8_t)(color);
            // Paint row by row (BGRA order for IOSurface)
            for (int y = 0; y < h; y++) {
                uint8_t *rowPtr = (uint8_t *)base + y * row;
                for (int x = 0; x < w; x++) {
                    rowPtr[x * 4 + 0] = b;
                    rowPtr[x * 4 + 1] = g;
                    rowPtr[x * 4 + 2] = r;
                    rowPtr[x * 4 + 3] = a;
                }
            }
        }
    }

    IOSurfaceUnlock(pc->surface, 0, NULL);
    atomic_store(&pc->dirty, false);
}
