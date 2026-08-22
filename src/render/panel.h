#ifndef RENDER_PANEL_H
#define RENDER_PANEL_H

#include <stddef.h>
#include <stdint.h>

#include "buffers/buffer.h"
#include "buffers/color_buffer.h"

// render/panel.h — a scissored, double-buffered stamp of the master canvas.
//
// The compositor model (thread 0 presents; draw threads produce):
//
//   Panel *p = Panel_new(320, 180, 40, 30);   // own resolution + scissor pos
//   paint into Panel_back(p);                 // producer thread
//   Panel_flip(p);                            // one atomic word — published
//
//   // thread 0, once per frame:
//   Panel_composite(p, master);               // stamps front at the scissor
//   Window_present(master_window, master);
//
// Because each panel owns its resolution, UI and scene stamp at different
// scales onto the same master — seamless scaling by construction. v1 stamps
// 1:1; scaled stamps arrive via Buffer_sample.

typedef struct Panel Panel;

Panel *Panel_new(size_t width, size_t height, int x, int y);

// Producer side.
Buffer *Panel_back(Panel *panel);   // paint here — never presented directly
void Panel_flip(Panel *panel);      // publish: back becomes front atomically
const Buffer *Panel_front(Panel *panel);

// Compositor side (thread 0).
void Panel_setScissor(Panel *panel, int x, int y);
int Panel_x(Panel *panel);
int Panel_y(Panel *panel);
void Panel_composite(Panel *panel, Buffer *master);

void Panel_free(Panel *panel);

#endif
