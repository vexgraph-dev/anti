#ifndef VULKAN_MAC_H
#define VULKAN_MAC_H

#include <stdbool.h>
#include <stdint.h>
#include <vulkan/vulkan.h>
#include "annotation/platform_exclusive.h"
#include "annotation/intention.h"
#include "vulkan/vk_iosurface.h"
#include "window/window.h"
#include "darling/panel.h"

// Forward declaration for VkIOSurface (defined in vk_iosurface.c).
typedef struct VkIOSurface VkIOSurface;

// vulkan/vulkan_mac.h — macOS-specific Vulkan backend functions.
;;PLATFORM_EXCLUSIVE("Mac")
;;INTENTION("MoltenVK loader, CAMetalLayer surface creation, IOSurface render pass, and IOSurface child rendering for macOS.")

// IOSurface child state: wraps a PanelCocoa's IOSurface for Vulkan rendering.
struct IOSurfaceChild {
    Panel *panel;
    VkIOSurface *surf; // VkImage = IOSurfaceRef
    VkFramebuffer fb;
    bool valid;
};

// Accessors for cross-platform state owned by vulkan.c.
extern VkDevice Vk_getDevice(void);
extern VkQueue Vk_getQueue(void);
extern VkCommandBuffer Vk_getCmdBuffer(void);
extern VkPipeline Vk_getTriPipeline(void);
extern VkPipelineLayout Vk_getTriLayout(void);
extern uint64_t Vk_getAnimStartNanos(void);
extern PFN_vkGetDeviceProcAddr Vk_getGdpa(void);

// Load a Vulkan device function pointer dynamically.
#define MAC_LOAD_DEVICE(name) \
    static PFN_vk##name name##_fn; \
    if (!name##_fn) { \
        name##_fn = (PFN_vk##name)Vk_getGdpa()(Vk_getDevice(), "vk" #name); \
    }

// Load the Vulkan loader library (MoltenVK on macOS, Khronos loader fallback).
void *VkMac_loadLib(void);

// Create a VkSurfaceKHR from the window's CAMetalLayer.
bool VkMac_createSurface(Window *window, VkInstance instance,
                         PFN_vkGetInstanceProcAddr gpa, VkSurfaceKHR *outSurface);

// Ensure the IOSurface render pass exists.
bool VkMac_ensureIOSurfacePass(void);

// Render a panel child into an IOSurface.
void VkMac_renderChildToIOSurface(Panel *child, void *surface, int w, int h);

// Render content panel children into IOSurfaces.
void VkMac_renderNativeContent(Window *window, Panel *contentPanel,
                               int winW, int winH, float kx, float ky,
                               bool *outNativeContent);

// Resize render trampoline (no-op on macOS; Vulkan has its own worker thread).
void VkMac_resizeRenderTrampoline(void *userdata);

// Cleanup IOSurface state. Called from vulkan.c destroyTargets().
void VkMac_cleanupIOSurfaceState(void);

#endif
