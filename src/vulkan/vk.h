#ifndef VULKAN_VULKAN_H
#define VULKAN_VULKAN_H

#include <stdbool.h>
#include <stdint.h>

// vulkan/vk.h — the GPU backend seam (MoltenVK on macOS, software stays).
//
// Runtime loading only: the loader dylib is dlopen'd, every entry point is
// fetched through vkGetInstanceProcAddr. No link-time dependency, so a machine
// without MoltenVK degrades gracefully to the software path.
//
// Milestone contract: init builds the chain instance -> surface -> device ->
// swapchain; clearPresent acquires an image, clears it to a solid color and
// presents. THREAD CONTRACT: init and clearPresent run on thread 0 (the
// surface wraps the window's AppKit view).
//
// Content + policy come from the Window handle itself: the renderer reads the
// root container via Window_getContainer, pacing via Window_getPresentMode,
// and watches Window_renderGeneration for swapchain rebuilds.

typedef struct Window Window;

bool Vk_init(Window *window);
void Vk_shutdown(void);
bool Vk_ready(void);

// Acquire, clear the monitor cache to the window's background color (or the
// basket panel's own color while one is set), render ONE layer — the direct
// children of the window's container basket — at absolute desktop
// coordinates, then blit the window's region (top-left anchored) from the
// monitor's giant cache into the acquired swapchain image and present.
// False when not ready or the swapchain is out of date. Present pacing
// follows Window_getPresentMode(window).
bool Vk_clearPresent(void);

// Synchronous twin for the resize cadence: BLOCKS until any in-flight frame
// completes (hard safety cap 34 ms — two display frames), then
// acquires/rebuilds/renders/presents inline on the calling thread. This is
// what lets thread-0 geometry events carry their own exactly-sized frame
// instead of dropping the tick. Thread 0 only. False when not ready or the
// safety deadline expired.
bool Vk_clearPresentSync(void);

// Human-readable stop point of the last init attempt ("ok", "no loader", ...).
const char *Vk_status(void);

// Solid-quad primitive: the default Panel draw, exposed so Panel_RenderFn
// overrides can compose real content out of it ("pointing one function at
// another"). Records an axis-aligned fill at drawable-pixel coords into an
// open render pass; sets its own viewport (whole drawable) and scissor (the
// rect), per the VIEWPORT/SCISSOR SEPARATION LAW. Safe to call several times
// per handler for layered rects.
void Vk_fillRect(void *cmdBuffer, float x, float y, float w, float h,
                 float r, float g, float b, float a);

#endif
