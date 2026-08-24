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

// Human-readable stop point of the last init attempt ("ok", "no loader", ...).
const char *Vk_status(void);

#endif
