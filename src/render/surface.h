#ifndef RENDER_SURFACE_H
#define RENDER_SURFACE_H

#include <stddef.h>
#include <stdint.h>

#include "buffer/buffer.h"
#include "buffer/color_buffer.h"

// render/surface.h — a scissored, double-buffered stamp of the master canvas.
//
// The compositor model (thread 0 presents; draw threads produce):
//
//   Surface *p = Surface_new(320, 180, 40, 30);   // own resolution + scissor pos
//   paint into Surface_back(p);                 // producer thread
//   Surface_flip(p);                            // one atomic word — published
//
//   // thread 0, once per frame:
//   Surface_composite(p, master);               // stamps front at the scissor
//   Window_present(master_window, master);
//
// Because each panel owns its resolution, UI and scene stamp at different
// scales onto the same master — seamless scaling by construction. v1 stamps
// 1:1; scaled stamps arrive via Buffer_sample.

typedef struct Surface Surface;

Surface *Surface_new(size_t width, size_t height, int x, int y);

// Producer side.
Buffer *Surface_back(Surface *panel);   // paint here — never presented directly
void Surface_flip(Surface *panel);      // publish: back becomes front atomically
const Buffer *Surface_front(Surface *panel);

// Compositor side (thread 0).
void Surface_setScissor(Surface *panel, int x, int y);
int Surface_x(Surface *panel);
int Surface_y(Surface *panel);
void Surface_composite(Surface *panel, Buffer *master);

void Surface_free(Surface *panel);

#endif
