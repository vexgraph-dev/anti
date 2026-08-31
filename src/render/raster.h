#ifndef RENDER_RASTER_H
#define RENDER_RASTER_H

#include <stdbool.h>
#include <stdint.h>

#include "buffer/buffer.h"
#include "buffer/color_buffer.h"

// render/raster.h — the software rasterizer (pixels before Vulkan).
//
// Pure painters over any 4-channel Buffer (ColorBuffer layout: R,G,B,A per
// pixel). No allocations, no state: every function takes its target buffer
// first, geometry/colors after — dest-last is reserved for outputs, and here
// the buffer IS both canvas and destination of record.
//
// All geometry is clipped to the buffer bounds; nothing scribbles outside.

void Raster_rect(Buffer *buf, int x, int y, int w, int h,
                 uint8_t r, uint8_t g, uint8_t b, uint8_t a);

// Linear gradient across the rect: color at (x,y) lerps from (r0..a0) at the
// left edge to (r1..a1) at the right edge.
void Raster_gradientH(Buffer *buf, int x, int y, int w, int h,
                      uint8_t r0, uint8_t g0, uint8_t b0, uint8_t a0,
                      uint8_t r1, uint8_t g1, uint8_t b1, uint8_t a1);

void Raster_line(Buffer *buf, int x0, int y0, int x1, int y1,
                 uint8_t r, uint8_t g, uint8_t b, uint8_t a);

// Solid triangle fill via edge functions (barycentric coverage).
void Raster_triangle(Buffer *buf,
                     int x0, int y0, int x1, int y1, int x2, int y2,
                     uint8_t r, uint8_t g, uint8_t b, uint8_t a);

// Debug sink: writes the RGB channels as binary P6 PPM. True on success.
bool Raster_dumpPPM(const Buffer *buf, const char *path);

#endif
