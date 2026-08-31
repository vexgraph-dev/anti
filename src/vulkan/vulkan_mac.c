#define VK_USE_PLATFORM_MACOS_MVK
#define VK_USE_PLATFORM_METAL_EXT

#include "vulkan/vulkan_mac.h"
#include "vulkan/vk_iosurface.h"

#include <vulkan/vulkan_core.h>
#include <dlfcn.h>
#include <stdatomic.h>
#include "darling/container.h"
#include "darling/panel.h"
#include "nio/mem.h"
#include "oop/type.h"
#include "time/nanotime.h"
#include "window/window.h"

// Load a Vulkan device function pointer (void return, no error check).
#define MAC_LOAD_DEVICE_VOID(name) \
    static PFN_vk##name name##_fn; \
    name##_fn = (PFN_vk##name)Vk_getGdpa()(Vk_getDevice(), "vk" #name);

// vulkan/vulkan_mac.c — macOS-specific Vulkan backend.
;;PLATFORM_EXCLUSIVE("Mac")
;;INTENTION("MoltenVK loader, CAMetalLayer surface creation, IOSurface render pass, and IOSurface child rendering for macOS.")

// Cross-platform state accessors (owned by vulkan.c).
extern VkDevice s_instanceDevice;
extern VkQueue s_instanceQueue;
extern VkCommandBuffer s_instanceCmdBuffer;
extern VkPipeline s_instanceTriPipeline;
extern VkPipelineLayout s_instanceTriLayout;
extern uint64_t s_instanceAnimStartNanos;
extern PFN_vkGetDeviceProcAddr s_instanceGdpa;

VkDevice Vk_getDevice(void) { return s_instanceDevice; }
VkQueue Vk_getQueue(void) { return s_instanceQueue; }
VkCommandBuffer Vk_getCmdBuffer(void) { return s_instanceCmdBuffer; }
VkPipeline Vk_getTriPipeline(void) { return s_instanceTriPipeline; }
VkPipelineLayout Vk_getTriLayout(void) { return s_instanceTriLayout; }
uint64_t Vk_getAnimStartNanos(void) { return s_instanceAnimStartNanos; }
PFN_vkGetDeviceProcAddr Vk_getGdpa(void) { return s_instanceGdpa; }

// IOSurface child state (owned here, accessed by vulkan.c for cleanup).
#define IOSURFACE_CHILD_MAX 16
static struct IOSurfaceChild s_iosurfaceChildren[IOSURFACE_CHILD_MAX];
static int s_iosurfaceChildCount = 0;
static VkRenderPass s_iosurfacePass = VK_NULL_HANDLE;

// Load the Vulkan loader library (MoltenVK on macOS, Khronos loader fallback).
void *VkMac_loadLib(void) {
    // MoltenVK first: the ICD exports everything itself, no loader manifest
    // needed. The Khronos loader stays as fallback for manifest setups.
    const char *candidates[] = {
        "libMoltenVK.dylib",
        "/opt/homebrew/lib/libMoltenVK.dylib",
        "/usr/local/lib/libMoltenVK.dylib",
        "libvulkan.dylib",
        "/opt/homebrew/lib/libvulkan.dylib",
        "/usr/local/lib/libvulkan.dylib",
        nullptr,
    };
    for (int i = 0; candidates[i]; i++) {
        void *lib = dlopen(candidates[i], RTLD_NOW | RTLD_LOCAL);
        if (lib)
            return lib;
    }
    return nullptr;
}

// Create a VkSurfaceKHR from the window's CAMetalLayer.
bool VkMac_createSurface(Window *window, VkInstance instance,
                         PFN_vkGetInstanceProcAddr gpa, VkSurfaceKHR *outSurface) {
    if (!window || !instance || !gpa || !outSurface)
        return false;

    PFN_vkCreateMetalSurfaceEXT CreateMetalSurfaceEXT_fn =
        (PFN_vkCreateMetalSurfaceEXT)gpa(instance, "vkCreateMetalSurfaceEXT");
    if (!CreateMetalSurfaceEXT_fn)
        return false;

    void *metalLayer = Window_metalLayer(window);
    VkMetalSurfaceCreateInfoEXT sci = { .sType = VK_STRUCTURE_TYPE_METAL_SURFACE_CREATE_INFO_EXT };
    sci.pLayer = metalLayer;
    VkResult sr = CreateMetalSurfaceEXT_fn(instance, &sci, nullptr, outSurface);
    if (!sci.pLayer || sr != VK_SUCCESS)
        return false;

    return true;
}

// Ensure the IOSurface render pass exists.
bool VkMac_ensureIOSurfacePass(void) {
    if (s_iosurfacePass != VK_NULL_HANDLE)
        return true;

    VkDevice dev = Vk_getDevice();
    MAC_LOAD_DEVICE(CreateRenderPass);

    VkAttachmentDescription att = {0};
    att.format = VK_FORMAT_B8G8R8A8_UNORM;
    att.samples = VK_SAMPLE_COUNT_1_BIT;
    att.loadOp = VK_ATTACHMENT_LOAD_OP_CLEAR;
    att.storeOp = VK_ATTACHMENT_STORE_OP_STORE;
    att.stencilLoadOp = VK_ATTACHMENT_LOAD_OP_DONT_CARE;
    att.stencilStoreOp = VK_ATTACHMENT_STORE_OP_DONT_CARE;
    att.initialLayout = VK_IMAGE_LAYOUT_UNDEFINED;
    att.finalLayout = VK_IMAGE_LAYOUT_COLOR_ATTACHMENT_OPTIMAL;

    VkAttachmentReference colorRef = {0};
    colorRef.attachment = 0;
    colorRef.layout = VK_IMAGE_LAYOUT_COLOR_ATTACHMENT_OPTIMAL;

    VkSubpassDescription sub = {0};
    sub.pipelineBindPoint = VK_PIPELINE_BIND_POINT_GRAPHICS;
    sub.colorAttachmentCount = 1;
    sub.pColorAttachments = &colorRef;

    VkRenderPassCreateInfo rpci = { .sType = VK_STRUCTURE_TYPE_RENDER_PASS_CREATE_INFO };
    rpci.attachmentCount = 1;
    rpci.pAttachments = &att;
    rpci.subpassCount = 1;
    rpci.pSubpasses = &sub;

    if (CreateRenderPass_fn(dev, &rpci, nullptr, &s_iosurfacePass) != VK_SUCCESS)
        return false;

    return true;
}

VkRenderPass VkMac_getIOSurfacePass(void) {
    return s_iosurfacePass;
}

// Record a panel child into an IOSurface render pass.
// Returns the child state pointer if successfully recorded, nullptr otherwise.
struct IOSurfaceChild *VkMac_recordChildToIOSurface(VkCommandBuffer cb, Panel *child, void *surface, int w, int h) {
    if (!child || !surface || w <= 0 || h <= 0) return nullptr;

    // Ensure IOSurface render pass exists
    if (!VkMac_ensureIOSurfacePass()) return nullptr;

    // Load function pointers
    VkDevice dev = Vk_getDevice();
    MAC_LOAD_DEVICE(CmdBeginRenderPass);
    MAC_LOAD_DEVICE(CmdEndRenderPass);
    MAC_LOAD_DEVICE(CmdSetViewport);
    MAC_LOAD_DEVICE(CmdSetScissor);
    MAC_LOAD_DEVICE(CmdBindPipeline);
    MAC_LOAD_DEVICE(CmdPushConstants);
    MAC_LOAD_DEVICE(CmdDraw);
    MAC_LOAD_DEVICE(DestroyFramebuffer);

    // Get panel's max size (first setSize = max, subsequent = clamped current)
    float maxW = (*child).base.maxW;
    float maxH = (*child).base.maxH;
    if (maxW <= 0.0f || maxH <= 0.0f) {
        maxW = (float)w;
        maxH = (float)h;
    }
    int canvasW = (int)(maxW + 0.5f);
    int canvasH = (int)(maxH + 0.5f);
    if (canvasW <= 0 || canvasH <= 0) return nullptr;

    // Find or create IOSurface child state
    struct IOSurfaceChild *ioChild = nullptr;
    for (int i = 0; i < s_iosurfaceChildCount; i++) {
        if (s_iosurfaceChildren[i].panel == child) {
            ioChild = &s_iosurfaceChildren[i];
            break;
        }
    }
    if (!ioChild) {
        if (s_iosurfaceChildCount >= IOSURFACE_CHILD_MAX) return nullptr;
        ioChild = &s_iosurfaceChildren[s_iosurfaceChildCount++];
        (*ioChild).panel = child;
        (*ioChild).surf = nullptr;
        (*ioChild).fb = VK_NULL_HANDLE;
        (*ioChild).valid = false;
    }

    // Check if max size changed (IOSurface needs realloc)
    if ((*ioChild).surf && (VkIOSurface_width((*ioChild).surf) != (uint32_t)canvasW ||
                          VkIOSurface_height((*ioChild).surf) != (uint32_t)canvasH)) {
        if ((*ioChild).fb) DestroyFramebuffer_fn(dev, (*ioChild).fb, nullptr);
        VkIOSurface_free((*ioChild).surf);
        (*ioChild).surf = nullptr;
        (*ioChild).fb = VK_NULL_HANDLE;
        (*ioChild).valid = false;
    }

    // Create IOSurface wrapper at MAX size (fixed allocation, never reallocates on resize)
    if (!(*ioChild).surf) {
        (*ioChild).surf = VkIOSurface_wrap(surface, (uint32_t)canvasW, (uint32_t)canvasH);
        if (!(*ioChild).surf) return nullptr;
    }

    // Create framebuffer if needed
    if ((*ioChild).fb == VK_NULL_HANDLE) {
        (*ioChild).fb = VkIOSurface_createFramebuffer((*ioChild).surf, s_iosurfacePass);
        if ((*ioChild).fb == VK_NULL_HANDLE) {
            VkIOSurface_free((*ioChild).surf);
            (*ioChild).surf = nullptr;
            return nullptr;
        }
    }

    // Determine child type
    uint32_t childType = Memory_type(child);
    bool isScene = (childType == TYPE_SCENE3D_SINGLETON || childType == TYPE_SCENE2D_SINGLETON
                    || childType == TYPE_SCENE_SINGLETON);

    // Begin render pass (clears to transparent black)
    VkClearValue clear = {0};
    clear.color.float32[0] = 0.0f;
    clear.color.float32[1] = 0.0f;
    clear.color.float32[2] = 0.0f;
    clear.color.float32[3] = 0.0f;

    VkRenderPassBeginInfo rpbi = {
        .sType = VK_STRUCTURE_TYPE_RENDER_PASS_BEGIN_INFO,
        .renderPass = s_iosurfacePass,
        .framebuffer = (*ioChild).fb,
        .renderArea.extent.width = (uint32_t)canvasW,
        .renderArea.extent.height = (uint32_t)canvasH,
        .clearValueCount = 1,
        .pClearValues = &clear,
    };
    CmdBeginRenderPass_fn(cb, &rpbi, VK_SUBPASS_CONTENTS_INLINE);

    // Use the panel's CURRENT size for rendering (not the max allocation size).
    // The IOSurface buffer is allocated at canvasW×canvasH (max), but the actual
    // content only occupies the panel's current w×h within that buffer.
    float curW = (*child).base.w;
    float curH = (*child).base.h;
    if (curW <= 0.0f) curW = (float)canvasW;
    if (curH <= 0.0f) curH = (float)canvasH;
    if (curW > (float)canvasW) curW = (float)canvasW;
    if (curH > (float)canvasH) curH = (float)canvasH;
    int renderW = (int)(curW + 0.5f);
    int renderH = (int)(curH + 0.5f);
    if (renderW <= 0) renderW = 1;
    if (renderH <= 0) renderH = 1;

    // Set viewport and scissor to the panel's CURRENT size
    VkViewport vp = { .width = (float)renderW, .height = (float)renderH, .maxDepth = 1.0f };
    VkRect2D sc = { .extent.width = (uint32_t)renderW, .extent.height = (uint32_t)renderH };
    CmdSetViewport_fn(cb, 0, 1, &vp);
    CmdSetScissor_fn(cb, 0, 1, &sc);

    if (isScene) {
        // Render triangle scene at current size
        float uTime = (float)((double)(NanoTime_now() - Vk_getAnimStartNanos()) / 1e9);
        CmdBindPipeline_fn(cb, VK_PIPELINE_BIND_POINT_GRAPHICS, Vk_getTriPipeline());
        CmdPushConstants_fn(cb, Vk_getTriLayout(), VK_SHADER_STAGE_VERTEX_BIT, 0, 4, &uTime);
        CmdDraw_fn(cb, 3, 1, 0, 0);
    } else {
        // Call panel's render handler at the panel's CURRENT size
        extern Panel_RenderFn Panel_getRenderHandler(const Panel *p);
        Panel_RenderFn handler = Panel_getRenderHandler(child);
        if (handler) {
            handler(child, nullptr, cb, 0.0f, 0.0f, (float)renderW, (float)renderH);
        } else {
            uint32_t color = Panel_getBackgroundColor(child);
            if (color != 0) {
                float r = (float)((color >> 16) & 0xFF) / 255.0f;
                float g = (float)((color >> 8)  & 0xFF) / 255.0f;
                float b = (float)( color        & 0xFF) / 255.0f;
                float a = (float)((color >> 24) & 0xFF) / 255.0f;
                
                extern void Vk_fillRect(void *cmdBuffer, float surfaceW, float surfaceH, float x, float y, float w, float h,
                                        float r, float g, float b, float a);
                Vk_fillRect(cb, (float)renderW, (float)renderH, 0.0f, 0.0f, (float)renderW, (float)renderH, r, g, b, a);
            }
        }
    }

    CmdEndRenderPass_fn(cb);

    return ioChild;
}

// Render content panel children via Vulkan into IOSurfaces, then
// composite via AppKit. Each child renders independently; resize =
// IOSurface realloc + framebuffer rebuild, anchor pins the corner.
void VkMac_renderNativeContent(Window *window, Panel *contentPanel,
                               int winW, int winH, float kx, float ky,
                               bool *outNativeContent) {
    if (!window || !contentPanel) return;

    Panel *scenePanel = Window_getScenePanel(window);

    // Resize IOSurface backings at native pixel resolution.
    int nativePxW = (int)(winW * kx + 0.5f);
    int nativePxH = (int)(winH * ky + 0.5f);
    Window_resizePanelIOSurface(window, contentPanel, nativePxW, nativePxH);

    size_t childCount = Panel_childCount(contentPanel);
    if (childCount == 0) return;

    // Load functions for batch submission
    VkDevice dev = Vk_getDevice();
    VkQueue queue = Vk_getQueue();
    VkCommandBuffer cb = Vk_getCmdBuffer();
    MAC_LOAD_DEVICE(ResetCommandBuffer);
    MAC_LOAD_DEVICE(BeginCommandBuffer);
    MAC_LOAD_DEVICE(EndCommandBuffer);
    MAC_LOAD_DEVICE(QueueSubmit);
    MAC_LOAD_DEVICE(WaitForFences);
    MAC_LOAD_DEVICE(ResetFences);
    MAC_LOAD_DEVICE(CreateFence);

    // Start a single batched command buffer
    ResetCommandBuffer_fn(cb, 0);
    VkCommandBufferBeginInfo bi = { .sType = VK_STRUCTURE_TYPE_COMMAND_BUFFER_BEGIN_INFO };
    BeginCommandBuffer_fn(cb, &bi);

    // Track which children were actually recorded this frame
    struct IOSurfaceChild *recorded[IOSURFACE_CHILD_MAX];
    int recordedCount = 0;

    for (size_t i = 0; i < childCount; i++) {
        Panel *child = Panel_getChild(contentPanel, i);
        if (!child) continue;

        // Skip the scene panel — it renders directly to the swapchain
        if (child == scenePanel) continue;

        // Get the IOSurface for this child
        extern void *PanelCocoa_fromPanel(void *panel);
        void *pc = PanelCocoa_fromPanel(child);
        if (!pc) continue;
        extern void *PanelCocoa_surface(void *pc);
        void *surface = PanelCocoa_surface(pc);
        if (!surface) continue;

        // Resolve layout in logical POINTS, then scale to native PIXELS
        Vec4 rect;
        Container_resolve(&(*child).base, 0.0f, 0.0f, (float) winW, (float) winH, &rect);
        const int pxW = (int)(rect.z * kx + 0.5f);
        const int pxH = (int)(rect.w * ky + 0.5f);
        if (pxW <= 0 || pxH <= 0) continue;

        // Record into the shared command buffer
        struct IOSurfaceChild *ioChild = VkMac_recordChildToIOSurface(cb, child, surface, pxW, pxH);
        if (ioChild && recordedCount < IOSURFACE_CHILD_MAX) {
            recorded[recordedCount++] = ioChild;
        }
    }

    // End recording
    EndCommandBuffer_fn(cb);

    // If we recorded anything, submit and wait exactly ONCE
    if (recordedCount > 0) {
        static VkFence s_batchFence = VK_NULL_HANDLE;
        if (s_batchFence == VK_NULL_HANDLE) {
            VkFenceCreateInfo fi = { .sType = VK_STRUCTURE_TYPE_FENCE_CREATE_INFO };
            CreateFence_fn(dev, &fi, nullptr, &s_batchFence);
        }

        VkSubmitInfo si = { .sType = VK_STRUCTURE_TYPE_SUBMIT_INFO };
        si.commandBufferCount = 1;
        si.pCommandBuffers = &cb;
        
        ResetFences_fn(dev, 1, &s_batchFence);
        QueueSubmit_fn(queue, 1, &si, s_batchFence);
        WaitForFences_fn(dev, 1, &s_batchFence, VK_TRUE, UINT64_MAX);

        // Export only the updated IOSurfaces for AppKit compositing
        for (int i = 0; i < recordedCount; i++) {
            VkIOSurface_export((*recorded[i]).surf);
            (*recorded[i]).valid = true;
        }
    }

    if (outNativeContent)
        *outNativeContent = true;
}

// Resize render trampoline (no-op on macOS; Vulkan has its own worker thread).
void VkMac_resizeRenderTrampoline(void *userdata) {
    (void)userdata;
    // Do absolutely nothing on the main thread!
    // Vulkan has its own dedicated worker thread (vk_present_job) spinning.
    // If we call Vk_clearPresentSync() here, we block the AppKit resize loop
    // (windowDidResize) waiting for the GPU, causing severe UI stutter.
}

// Cleanup IOSurface state. Called from vulkan.c destroyTargets().
void VkMac_cleanupIOSurfaceState(void) {
    VkDevice dev = Vk_getDevice();

    MAC_LOAD_DEVICE_VOID(DestroyRenderPass);
    MAC_LOAD_DEVICE_VOID(DestroyFramebuffer);

    for (int i = 0; i < s_iosurfaceChildCount; i++) {
        struct IOSurfaceChild *ioChild = &s_iosurfaceChildren[i];
        if ((*ioChild).fb != VK_NULL_HANDLE && DestroyFramebuffer_fn)
            DestroyFramebuffer_fn(dev, (*ioChild).fb, nullptr);
        if ((*ioChild).surf)
            VkIOSurface_free((*ioChild).surf);
        (*ioChild).panel = nullptr;
        (*ioChild).surf = nullptr;
        (*ioChild).fb = VK_NULL_HANDLE;
        (*ioChild).valid = false;
    }
    s_iosurfaceChildCount = 0;

    if (s_iosurfacePass != VK_NULL_HANDLE && DestroyRenderPass_fn)
        DestroyRenderPass_fn(dev, s_iosurfacePass, nullptr);
    s_iosurfacePass = VK_NULL_HANDLE;
}
