#define VK_USE_PLATFORM_MACOS_MVK
#define VK_USE_PLATFORM_METAL_EXT

#include "vulkan/vk.h"
#include "vulkan/vk_iosurface.h"
#include "vulkan/vk_scene.h"
#include "vulkan/vk_view.h"
#include "vulkan/vulkan_mac.h"

#include "annotation/platform_exclusive.h"
#include "annotation/intention.h"
#include <vulkan/vulkan_core.h>
#include <vulkan/vulkan_macos.h>
#include <dlfcn.h>
#include <stdio.h>
#include <string.h>
#include <stdatomic.h>
#include "darling/container.h"
#include "darling/panel.h"
#include "nio/mem.h"
#include "oop/type.h"
#include "time/nanotime.h"
#include "atomic/spin.h"
#include "window/window.h"

// vulkan/vulkan.c — runtime-loaded Vulkan chain over the compositor model.
//
// Loader strategy: dlopen the Khronos loader (libvulkan.dylib) first, falling
// back straight to MoltenVK's ICD. Everything beyond vkGetInstanceProcAddr is
// fetched dynamically, instance-level then device-level.
//
// RENDER MODEL: each monitor owns a giant off-screen cache (see vk_view.c).
// Every present loop clears the WHOLE cache to the background color, renders
// ONE layer — the direct children of the window's container basket — at
// absolute desktop coordinates, then blits the window's region (top-left
// anchored) into the acquired swapchain image. Windows are a scissor into
// the desktop; nothing more.

static void *s_lib = nullptr;
static PFN_vkGetInstanceProcAddr s_gpa = nullptr;
static PFN_vkGetDeviceProcAddr s_gdpa = nullptr;
static char s_status[64] = "not started";

// Shared state exported to vulkan_mac.c (macOS-specific backend).
VkDevice s_instanceDevice;
VkQueue s_instanceQueue;
VkCommandBuffer s_instanceCmdBuffer;
VkPipeline s_instanceTriPipeline;
VkPipelineLayout s_instanceTriLayout;
uint64_t s_instanceAnimStartNanos;
PFN_vkGetDeviceProcAddr s_instanceGdpa;

#define VK_MARK(msg) do { snprintf(s_status, sizeof(s_status), msg); fprintf(stderr, "vk: %s\n", s_status); } while(0)

// Swapchain: rebuilt whenever the surface outgrows the chain (fullscreen,
// resize) or render policy drift. Each chain carries its own per-image
// views + framebuffers (the drawable pass renders straight onto acquired
// images), and those die WITH the chain — never earlier.
static bool rebuildTargets(void);
static void destroyTargets(void);
static bool buildPipelines(void);
static bool presentFrameLocked(void);

// Last-applied Window_renderGeneration. A drift means presentMode or
// transparent changed on thread 0 and the swapchain wants a rebuild.
static uint64_t s_appliedRenderGen = 0;

// Last-applied Window_sizeGeneration for the container mirror. The basket
// panel's w/h are rewritten to the window's content size whenever this
// drifts — the "root mirrors its window" law.
static uint64_t s_mirroredSizeGen = UINT64_MAX;

// Animation clock anchor for scene children (u_time seconds since init).
static uint64_t s_animStartNanos = 0;

// Serializes whole-frame submission between the present worker and thread 0's
// resize-cadence bridge. Try-lock semantics: whoever is busy loses this tick.
static SpinLock s_presentLock = SPIN_LOCK_INIT;

// Swapchain graveyard: destroying a swapchain whose present-completion
// callbacks are still queued on Metal's dispatch queues is a use-after-free
// (SIGSEGV in MVKSwapchain::beginPresentation). The Vulkan fence covers
// QUEUE work only — the CAMetal callback runs later, off-queue. So old
// chains are RETIRED here and destroyed three rebuild generations later,
// when every callback they could ever own has long since fired. Each entry
// carries the chain's image views + framebuffers so the whole generation
// dies together.
#define VK_SWAP_IMAGES_MAX 8
#define VK_RETIRED_SWAPCHAINS_MAX 8

typedef struct RetiredChain {
    VkSwapchainKHR chain;
    uint32_t generation;
    uint32_t imageCount;
    VkImageView views[VK_SWAP_IMAGES_MAX];
    VkFramebuffer fbs[VK_SWAP_IMAGES_MAX];
} RetiredChain;

static RetiredChain s_retired[VK_RETIRED_SWAPCHAINS_MAX];
static uint32_t s_retiredCount = 0;
static uint32_t s_swapchainGeneration = 0;
static bool s_dumpEnabled = false;
static int s_dumpStage = 0;
static bool s_dumpEnvRead = false;

static VkInstance s_instance;
static VkSurfaceKHR s_surface;
static VkPhysicalDevice s_phys;
static uint32_t s_queueFamily = 0;
static VkDevice s_device;
static VkQueue s_queue;
static VkSwapchainKHR s_swapchain;
static VkCommandPool s_cmdPool;
static VkCommandBuffer s_cmdBuffer;

// Phase 2/3 plumbing: scene passes submit on their OWN command buffer +
// fence, decoupled from the collage submission. A slow scene stays in flight
// across present frames while the collage keeps stamping finished fronts.
#define VK_SCENE_BATCH_MAX 8
typedef struct SceneBatchEntry {
    VkSceneCanvas *canvas;
    uint32_t gen;   // canvas generation at record time; mismatched = resized
} SceneBatchEntry;
static VkCommandBuffer s_sceneBuffer;
static VkFence s_sceneFence;
static SceneBatchEntry s_sceneBatch[VK_SCENE_BATCH_MAX];
static uint32_t s_sceneBatchCount = 0;

static VkSemaphore s_semAcquire;
static VkSemaphore s_semRender;
static VkFence s_fence;

static VkFormat s_format;
static VkExtent2D s_extent;
static Window *s_window = nullptr;

// Swapchain images plus their drawable-side plumbing: a view + framebuffer
// per image lets the render pass draw straight onto the acquired image —
// the window IS the canvas now. Created per chain, retired with it.
static VkImage s_swapchainImages[VK_SWAP_IMAGES_MAX];
static VkImageView s_swapchainViews[VK_SWAP_IMAGES_MAX];
static VkFramebuffer s_swapchainFbs[VK_SWAP_IMAGES_MAX];
static uint32_t s_swapchainImageCount = 0;

// THE drawable renderpass: LOADs a pre-cleared TRANSFER_DST image (scene
// stamps survive), leaves TRANSFER_SRC for the dump path; the explicit
// final barrier walks it to PRESENT_SRC. Same format + single subpass as
// the view passes — child pipelines run here unchanged.
static VkRenderPass s_drawablePass = VK_NULL_HANDLE;
static bool ensureDrawablePass(void);

// IOSurface state is owned by vulkan_mac.c (macOS-specific).

// Child pipelines, built against the monitor view's cache renderpass:
//   triangle — legacy hello-triangle scene content (push: f32 u_time @0, VS)
//   quad     — solid panel fill (push: vec4 rectNdc @0 VS, vec4 color @16 FS)
static VkPipelineLayout s_triLayout;
static VkPipeline s_triPipeline;
static VkPipelineLayout s_quadLayout;
static VkPipeline s_quadPipeline;
static VkPipelineLayout s_texLayout;
static VkPipeline s_texPipeline;
static bool s_pipelinesBuilt = false;

static void *s_libLoad(void) {
    void *lib = VkMac_loadLib();
    s_lib = lib;
    return lib;
}

#define VK_LOAD_GLOBAL(name) \
    static PFN_vk##name name##_fn; \
    name##_fn = (PFN_vk##name)s_gpa(VK_NULL_HANDLE, "vk" #name); \
    if (!name##_fn) { snprintf(s_status, sizeof(s_status), "missing vk" #name); fprintf(stderr, "vk: missing vk%s\n", #name); return false; }

#define VK_LOAD_INSTANCE(name) \
    static PFN_vk##name name##_fn; \
    name##_fn = (PFN_vk##name)s_gpa((VkInstance)s_instance, "vk" #name); \
    if (!name##_fn) { snprintf(s_status, sizeof(s_status), "missing vk" #name); fprintf(stderr, "vk: missing vk%s\n", #name); return false; }

#define VK_LOAD_DEVICE(name) \
    static PFN_vk##name name##_fn; \
    if (!name##_fn) { /* cached after first resolve: hot path must not re-query */ \
        name##_fn = s_gdpa ? (PFN_vk##name)s_gdpa(s_device, "vk" #name) : (PFN_vk##name)s_gpa(s_instance, "vk" #name); \
        if (!name##_fn) { snprintf(s_status, sizeof(s_status), "missing vk" #name); fprintf(stderr, "vk: missing vk%s\n", #name); return false; } \
    }

#define VK_LOAD_DEVICE_PTR(name) \
    static PFN_vk##name name##_fn; \
    if (!name##_fn) { \
        name##_fn = s_gdpa ? (PFN_vk##name)s_gdpa(s_device, "vk" #name) : (PFN_vk##name)s_gpa(s_instance, "vk" #name); \
        if (!name##_fn) { snprintf(s_status, sizeof(s_status), "missing vk" #name); fprintf(stderr, "vk: missing vk%s\n", #name); return nullptr; } \
    }

#define VK_LOAD_INSTANCE_VOID(name) \
    static PFN_vk##name name##_fn; \
    name##_fn = (PFN_vk##name)s_gpa((VkInstance)s_instance, "vk" #name);

#define VK_LOAD_DEVICE_VOID(name) \
    static PFN_vk##name name##_fn; \
    name##_fn = s_gdpa ? (PFN_vk##name)s_gdpa(s_device, "vk" #name) : (PFN_vk##name)s_gpa(s_instance, "vk" #name);

bool Vk_init(Window *window) {
    VK_MARK("loader+gpa");

    if (!window)
        return false;
    s_window = window;
    if (s_lib)
        return true;

    // 1. loader
    s_lib = s_libLoad();
    if (!s_lib) {
        snprintf(s_status, sizeof(s_status), "no loader dylib");
        return false;
    }
    s_gpa = (PFN_vkGetInstanceProcAddr)dlsym(s_lib, "vkGetInstanceProcAddr");
    if (!s_gpa) {
        snprintf(s_status, sizeof(s_status), "no gpa");
        return false;
    }

    VK_LOAD_GLOBAL(CreateInstance)
    // 2. instance — request only the extensions this driver actually exposes
    // ;;PLATFORM_EXCLUSIVE("Mac") — VK_EXT_metal_surface is macOS-only.
    VK_LOAD_GLOBAL(EnumerateInstanceExtensionProperties)
    uint32_t extCount = 0;
    EnumerateInstanceExtensionProperties_fn(nullptr, &extCount, nullptr);

    static char names[64][VK_MAX_EXTENSION_NAME_SIZE];
    VkExtensionProperties props[64];
    if (extCount > 64)
        extCount = 64;
    EnumerateInstanceExtensionProperties_fn(nullptr, &extCount, props);
    for (uint32_t i = 0; i < extCount; i++) {
        snprintf(names[i], VK_MAX_EXTENSION_NAME_SIZE, "%s", props[i].extensionName);
    }

    int surfaceExt = 0;
    const char *exts[2];
    uint32_t n = 0;
    for (uint32_t i = 0; i < extCount; i++) {
        if (strcmp(names[i], "VK_KHR_surface") == 0)
            exts[n++] = "VK_KHR_surface";
        else if (strcmp(names[i], "VK_EXT_metal_surface") == 0) {
            surfaceExt = 1;
            exts[n++] = "VK_EXT_metal_surface";
        }
    }
    if (n < 2 || surfaceExt == 0) {
        snprintf(s_status, sizeof(s_status), "no surface ext (%u seen)", (unsigned)extCount);
        return false;
    }

    VkApplicationInfo app = { .sType = VK_STRUCTURE_TYPE_APPLICATION_INFO };
    app.pApplicationName = "anti";
    app.apiVersion = VK_API_VERSION_1_2;

    VkInstanceCreateInfo ici = { .sType = VK_STRUCTURE_TYPE_INSTANCE_CREATE_INFO };
    ici.pApplicationInfo = &app;
    ici.enabledExtensionCount = n;
    ici.ppEnabledExtensionNames = exts;

    if (CreateInstance_fn(&ici, nullptr, &s_instance) != VK_SUCCESS) {
        VkResult r = CreateInstance_fn(&ici, nullptr, &s_instance);
        snprintf(s_status, sizeof(s_status), "instance failed r=%d", r); fprintf(stderr, "vk: %s\n", s_status);
        return false;
    }

    s_gdpa = (PFN_vkGetDeviceProcAddr)s_gpa(s_instance, "vkGetDeviceProcAddr");

    // 3. surface over the window's CAMetalLayer
    if (!VkMac_createSurface(window, s_instance, s_gpa, &s_surface)) {
        snprintf(s_status, sizeof(s_status), "surface failed");
        fprintf(stderr, "vk: %s\n", s_status);
        return false;
    }

    // 4. physical device + queue family with graphics + present support
    VK_LOAD_INSTANCE(EnumeratePhysicalDevices)
    VK_LOAD_INSTANCE(GetPhysicalDeviceQueueFamilyProperties)
    VK_LOAD_INSTANCE(GetPhysicalDeviceSurfaceSupportKHR)
    VK_LOAD_INSTANCE(GetPhysicalDeviceSurfaceCapabilitiesKHR)
    VK_LOAD_INSTANCE(GetPhysicalDeviceSurfaceFormatsKHR)
    VK_LOAD_INSTANCE(CreateDevice)

    uint32_t physCount = 0;
    if (EnumeratePhysicalDevices_fn(s_instance, &physCount, nullptr) != VK_SUCCESS || physCount == 0) {
        snprintf(s_status, sizeof(s_status), "no physical devices");
        return false;
    }
    VkPhysicalDevice phys[8];
    if (physCount > 8)
        physCount = 8;
    EnumeratePhysicalDevices_fn(s_instance, &physCount, phys);
    s_phys = phys[0]; // MoltenVK exposes exactly one

    uint32_t familyCount = 0;
    GetPhysicalDeviceQueueFamilyProperties_fn(s_phys, &familyCount, nullptr);
    VkQueueFamilyProperties families[16];
    if (familyCount > 16)
        familyCount = 16;
    GetPhysicalDeviceQueueFamilyProperties_fn(s_phys, &familyCount, families);

    VkBool32 presentOk = VK_FALSE;
    for (uint32_t f = 0; f < familyCount; f++) {
        if (!(families[f].queueFlags & VK_QUEUE_GRAPHICS_BIT))
            continue;
        if (GetPhysicalDeviceSurfaceSupportKHR_fn(s_phys, f, s_surface, &presentOk) == VK_SUCCESS
            && presentOk == VK_TRUE) {
            s_queueFamily = f;
            break;
        }
    }
    if (!presentOk) {
        snprintf(s_status, sizeof(s_status), "no graphics+present family");
        return false;
    }

    // 5. logical device with swapchain extension
    float prio = 1.0f;
    VkDeviceQueueCreateInfo qci = { .sType = VK_STRUCTURE_TYPE_DEVICE_QUEUE_CREATE_INFO };
    qci.queueFamilyIndex = s_queueFamily;
    qci.queueCount = 1;
    qci.pQueuePriorities = &prio;

    const char *devExts[] = { "VK_KHR_swapchain", "VK_EXT_metal_objects" };
    
    VkPhysicalDeviceDescriptorIndexingFeatures idxFeat = { .sType = VK_STRUCTURE_TYPE_PHYSICAL_DEVICE_DESCRIPTOR_INDEXING_FEATURES };
    idxFeat.descriptorBindingPartiallyBound = VK_TRUE;
    idxFeat.descriptorBindingVariableDescriptorCount = VK_TRUE;
    idxFeat.runtimeDescriptorArray = VK_TRUE;
    idxFeat.shaderSampledImageArrayNonUniformIndexing = VK_TRUE;

    VkDeviceCreateInfo dci = { .sType = VK_STRUCTURE_TYPE_DEVICE_CREATE_INFO };
    dci.pNext = &idxFeat;
    dci.queueCreateInfoCount = 1;
    dci.pQueueCreateInfos = &qci;
    dci.enabledExtensionCount = 2;
    dci.ppEnabledExtensionNames = devExts;

    if (CreateDevice_fn(s_phys, &dci, nullptr, &s_device) != VK_SUCCESS) {
        snprintf(s_status, sizeof(s_status), "device failed");
        return false;
    }

    VK_LOAD_DEVICE(GetDeviceQueue)
    GetDeviceQueue_fn(s_device, s_queueFamily, 0, &s_queue);

    // 6. swapchain (rebuilt on every surface resize / policy drift)
    if (!rebuildTargets())
        return false;

    // 7. per-monitor render caches + child pipelines + command plumbing
    if (!VkView_refreshAll(s_instance, s_gpa, s_phys, s_device) || VkView_count() == 0) {
        snprintf(s_status, sizeof(s_status), "no monitor views");
        return false;
    }
    if (!VkSceneCanvas_initModule(s_instance, s_gpa, s_phys, s_device)) {
        snprintf(s_status, sizeof(s_status), "scene module failed");
        return false;
    }
    extern bool VkIOSurface_initModule(VkInstance, PFN_vkGetInstanceProcAddr, VkPhysicalDevice, VkDevice);
    if (!VkIOSurface_initModule(s_instance, s_gpa, s_phys, s_device)) {
        snprintf(s_status, sizeof(s_status), "iosurface module failed");
        return false;
    }
    extern bool Texture_initModule(void*, void*, void*, void*, void*, uint32_t);
    if (!Texture_initModule(s_instance, s_gpa, s_phys, s_device, s_queue, s_queueFamily)) {
        snprintf(s_status, sizeof(s_status), "texture module failed");
        return false;
    }
    if (!buildPipelines())
        return false;

    VK_LOAD_DEVICE(CreateSemaphore)
    VK_LOAD_DEVICE(CreateFence)
    VK_LOAD_DEVICE(WaitForFences)
    VK_LOAD_DEVICE(ResetFences)

    VkSemaphoreCreateInfo sci2 = { .sType = VK_STRUCTURE_TYPE_SEMAPHORE_CREATE_INFO };
    if (CreateSemaphore_fn && CreateSemaphore_fn(s_device, &sci2, nullptr, &s_semAcquire) != VK_SUCCESS) return false;
    if (CreateSemaphore_fn && CreateSemaphore_fn(s_device, &sci2, nullptr, &s_semRender) != VK_SUCCESS) return false;

    VkFenceCreateInfo fci2 = { .sType = VK_STRUCTURE_TYPE_FENCE_CREATE_INFO };
    fci2.flags = VK_FENCE_CREATE_SIGNALED_BIT;
    if (CreateFence_fn && CreateFence_fn(s_device, &fci2, nullptr, &s_fence) != VK_SUCCESS) return false;

    // Scene-batch fence: polled non-blocking, never pre-signaled (TIMEOUT
    // simply means "nothing in flight yet").
    fci2.flags = 0;
    if (CreateFence_fn && CreateFence_fn(s_device, &fci2, nullptr, &s_sceneFence) != VK_SUCCESS) return false;

    s_animStartNanos = NanoTime_now();

    // Export shared state to vulkan_mac.c AFTER all init succeeds.
    s_instanceDevice = s_device;
    s_instanceQueue = s_queue;
    s_instanceCmdBuffer = s_cmdBuffer;
    s_instanceTriPipeline = s_triPipeline;
    s_instanceTriLayout = s_triLayout;
    s_instanceAnimStartNanos = s_animStartNanos;
    s_instanceGdpa = s_gdpa;

    // The c -> objc -> c bridge: AppKit's resize servicing drives frames at
    // the OS's own rhythm through this hook.
    Window_setResizeRenderHook(s_window, VkMac_resizeRenderTrampoline, nullptr);
    return true;
}


// --- swapchain targets: created, and re-created on fullscreen/resize ---------
// Fullscreen changes the view extent; presenting to a stale chain is the bug
// that "closed" the app. Now: proactive extent check per frame, reactive
// rebuild on OUT_OF_DATE/SUBOPTIMAL.

static bool rebuildTargets(void) {
    VK_LOAD_DEVICE(CreateSwapchainKHR)
    VK_LOAD_DEVICE(DestroySwapchainKHR)
    VK_LOAD_DEVICE(GetSwapchainImagesKHR)
    VK_LOAD_INSTANCE(GetPhysicalDeviceSurfaceCapabilitiesKHR)
    VK_LOAD_INSTANCE(GetPhysicalDeviceSurfaceFormatsKHR)

    // NO DeviceWaitIdle here: the caller retired the previous frame through
    // its fence, and the oldSwapchain handoff lets the driver settle any
    // compositor-side presentation itself. The queue never stops.

    VkSurfaceCapabilitiesKHR caps;
    memset(&caps, 0, sizeof(caps));
    if (GetPhysicalDeviceSurfaceCapabilitiesKHR_fn(s_phys, s_surface, &caps) != VK_SUCCESS) {
        snprintf(s_status, sizeof(s_status), "caps failed");
        return false;
    }

    if (caps.currentExtent.width == 0 || caps.currentExtent.height == 0) {
        return false;
    }

    uint32_t formatCount = 0;
    GetPhysicalDeviceSurfaceFormatsKHR_fn(s_phys, s_surface, &formatCount, nullptr);
    VkSurfaceFormatKHR formats[8];
    if (formatCount > 8)
        formatCount = 8;
    GetPhysicalDeviceSurfaceFormatsKHR_fn(s_phys, s_surface, &formatCount, formats);
    s_format = formatCount ? formats[0].format : VK_FORMAT_B8G8R8A8_UNORM;
    for (uint32_t i = 0; i < formatCount; i++) {
        if (formats[i].format == VK_FORMAT_B8G8R8A8_UNORM) {
            s_format = formats[i].format;
            break;
        }
    }

    VkSwapchainKHR oldSwapchain = s_swapchain;

    uint32_t imageCount = caps.minImageCount + 1;
    if (imageCount < 3) imageCount = 3;
    if (caps.maxImageCount > 0 && imageCount > caps.maxImageCount) {
        imageCount = caps.maxImageCount;
    }

    VkSwapchainCreateInfoKHR swci = { .sType = VK_STRUCTURE_TYPE_SWAPCHAIN_CREATE_INFO_KHR };
    swci.surface = s_surface;
    swci.minImageCount = imageCount; // triple-buffer bias, clamped to caps
    swci.imageFormat = s_format;
    swci.imageColorSpace = formatCount ? formats[0].colorSpace : VK_COLOR_SPACE_SRGB_NONLINEAR_KHR;
    swci.imageExtent = caps.currentExtent;
    s_extent = caps.currentExtent;
    swci.imageArrayLayers = 1;
    // The blit is the writer now: transfer-dst is a spec-mandated supported
    // usage for swapchain images, color-attachment stays for safety.
    swci.imageUsage = VK_IMAGE_USAGE_COLOR_ATTACHMENT_BIT | VK_IMAGE_USAGE_TRANSFER_DST_BIT;
    swci.imageSharingMode = VK_SHARING_MODE_EXCLUSIVE;
    swci.preTransform = caps.currentTransform;

    // Transparency is a request, not an order: pick the best non-opaque
    // composite mode the driver actually reports, fall back to opaque.
    swci.compositeAlpha = VK_COMPOSITE_ALPHA_OPAQUE_BIT_KHR;
    if (Window_isTransparent(s_window)) {
        if (caps.supportedCompositeAlpha & VK_COMPOSITE_ALPHA_POST_MULTIPLIED_BIT_KHR)
            swci.compositeAlpha = VK_COMPOSITE_ALPHA_POST_MULTIPLIED_BIT_KHR;
        else if (caps.supportedCompositeAlpha & VK_COMPOSITE_ALPHA_PRE_MULTIPLIED_BIT_KHR)
            swci.compositeAlpha = VK_COMPOSITE_ALPHA_PRE_MULTIPLIED_BIT_KHR;
        else if (caps.supportedCompositeAlpha & VK_COMPOSITE_ALPHA_INHERIT_BIT_KHR)
            swci.compositeAlpha = VK_COMPOSITE_ALPHA_INHERIT_BIT_KHR;
    }

    // Pacing policy lives on the window: FIFO paces to the display,
    // IMMEDIATE submits unthrottled.
    swci.presentMode = (Window_getPresentMode(s_window) == WINDOW_PRESENT_IMMEDIATE)
                       ? VK_PRESENT_MODE_IMMEDIATE_KHR
                       : VK_PRESENT_MODE_FIFO_KHR;
    swci.clipped = VK_TRUE;
    swci.oldSwapchain = oldSwapchain;

    VkSwapchainKHR newSwapchain = VK_NULL_HANDLE;
    if (CreateSwapchainKHR_fn(s_device, &swci, nullptr, &newSwapchain) != VK_SUCCESS) {
        snprintf(s_status, sizeof(s_status), "swapchain failed");
        return false;
    }

    Window_setGravityTopLeft(s_window);

    // Drain retirees old enough that every Metal present-callback they own
    // has fired. Three generations of margin; no stalls.
    {
        VK_LOAD_DEVICE(DestroyImageView)
        VK_LOAD_DEVICE(DestroyFramebuffer)
        uint32_t keep = 0;
        for (uint32_t i = 0; i < s_retiredCount; i++) {
            if (s_swapchainGeneration - s_retired[i].generation >= 3) {
                if (DestroySwapchainKHR_fn)
                    DestroySwapchainKHR_fn(s_device, s_retired[i].chain, nullptr);
                for (uint32_t v = 0; v < s_retired[i].imageCount; v++) {
                    if (s_retired[i].fbs[v] != VK_NULL_HANDLE && DestroyFramebuffer_fn)
                        DestroyFramebuffer_fn(s_device, s_retired[i].fbs[v], nullptr);
                    if (s_retired[i].views[v] != VK_NULL_HANDLE && DestroyImageView_fn)
                        DestroyImageView_fn(s_device, s_retired[i].views[v], nullptr);
                }
            } else {
                s_retired[keep] = s_retired[i];
                keep++;
            }
        }
        s_retiredCount = keep;
    }

    // The old chain does NOT die here: its presentation callbacks are still
    // in flight on Metal's queues. Retire it — WITH its views/framebuffers —
    // for deferred destruction.
    if (oldSwapchain != VK_NULL_HANDLE) {
        if (s_retiredCount < VK_RETIRED_SWAPCHAINS_MAX) {
            s_retired[s_retiredCount].chain = oldSwapchain;
            s_retired[s_retiredCount].generation = s_swapchainGeneration;
            s_retired[s_retiredCount].imageCount = s_swapchainImageCount;
            for (uint32_t v = 0; v < s_swapchainImageCount; v++) {
                s_retired[s_retiredCount].views[v] = s_swapchainViews[v];
                s_retired[s_retiredCount].fbs[v] = s_swapchainFbs[v];
            }
            s_retiredCount++;
        } else {
            // Graveyard full: retire the OLDEST entry (drop its chain without
            // destroying it — the OS releases MoltenVK's swapchain backing
            // when the CAMetalLayer is repurposed) and reuse its slot. An
            // immediate DestroySwapchainKHR here would tear down Metal
            // drawables whose present callbacks are still in flight — a
            // documented flicker source during rapid resize churn.
            VK_LOAD_DEVICE(DestroyImageView)
            VK_LOAD_DEVICE(DestroyFramebuffer)
            uint32_t oldest = 0;
            for (uint32_t i = 1; i < s_retiredCount; i++)
                if (s_retired[i].generation < s_retired[oldest].generation)
                    oldest = i;
            for (uint32_t v = 0; v < s_retired[oldest].imageCount; v++) {
                if (s_retired[oldest].fbs[v] != VK_NULL_HANDLE)
                    DestroyFramebuffer_fn(s_device, s_retired[oldest].fbs[v], nullptr);
                if (s_retired[oldest].views[v] != VK_NULL_HANDLE)
                    DestroyImageView_fn(s_device, s_retired[oldest].views[v], nullptr);
            }
            s_retired[oldest].chain = oldSwapchain;
            s_retired[oldest].generation = s_swapchainGeneration;
            s_retired[oldest].imageCount = s_swapchainImageCount;
            for (uint32_t v = 0; v < s_swapchainImageCount; v++) {
                s_retired[oldest].views[v] = s_swapchainViews[v];
                s_retired[oldest].fbs[v] = s_swapchainFbs[v];
            }
        }
    }
    s_swapchainGeneration++;
    s_swapchain = newSwapchain;

    // Fetch the raw image handles, then give each one a view + framebuffer
    // against the drawable pass — the render target of every present frame.
    s_swapchainImageCount = 0;
    GetSwapchainImagesKHR_fn(s_device, s_swapchain, &s_swapchainImageCount, nullptr);
    if (s_swapchainImageCount > VK_SWAP_IMAGES_MAX)
        s_swapchainImageCount = VK_SWAP_IMAGES_MAX;
    GetSwapchainImagesKHR_fn(s_device, s_swapchain, &s_swapchainImageCount, s_swapchainImages);

    if (!ensureDrawablePass())
        return false;
    {
        VK_LOAD_DEVICE(CreateImageView)
        VK_LOAD_DEVICE(CreateFramebuffer)
        memset(s_swapchainViews, 0, sizeof(s_swapchainViews));
        memset(s_swapchainFbs, 0, sizeof(s_swapchainFbs));
        for (uint32_t i = 0; i < s_swapchainImageCount; i++) {
            VkImageViewCreateInfo vci = { .sType = VK_STRUCTURE_TYPE_IMAGE_VIEW_CREATE_INFO };
            vci.image = s_swapchainImages[i];
            vci.viewType = VK_IMAGE_VIEW_TYPE_2D;
            vci.format = s_format;
            vci.subresourceRange.aspectMask = VK_IMAGE_ASPECT_COLOR_BIT;
            vci.subresourceRange.levelCount = 1;
            vci.subresourceRange.layerCount = 1;
            if (CreateImageView_fn(s_device, &vci, nullptr, &s_swapchainViews[i]) != VK_SUCCESS) {
                snprintf(s_status, sizeof(s_status), "drawable view failed");
                return false;
            }
            VkFramebufferCreateInfo fci = { .sType = VK_STRUCTURE_TYPE_FRAMEBUFFER_CREATE_INFO };
            fci.renderPass = s_drawablePass;
            fci.attachmentCount = 1;
            fci.pAttachments = &s_swapchainViews[i];
            fci.width = s_extent.width;
            fci.height = s_extent.height;
            fci.layers = 1;
            if (CreateFramebuffer_fn(s_device, &fci, nullptr, &s_swapchainFbs[i]) != VK_SUCCESS) {
                snprintf(s_status, sizeof(s_status), "drawable framebuffer failed");
                return false;
            }
        }
    }

    fprintf(stderr, "vk: swapchain live %ux%u fmt=%d present=%d\n", s_extent.width, s_extent.height, (int)s_format,
            (int)(swci.presentMode == VK_PRESENT_MODE_IMMEDIATE_KHR));
    s_appliedRenderGen = Window_renderGeneration(s_window);
    return true;
}

// The drawable renderpass, built once per format. LOAD-on-store: the frame's
// command stream clears the image to the board color and stamps scene
// canvases BEFORE this pass opens, so LOAD preserves them while procedural
// children draw on top — true child-order z-compositing for free.
static bool ensureDrawablePass(void) {
    if (s_drawablePass != VK_NULL_HANDLE)
        return true;
    VK_LOAD_DEVICE(CreateRenderPass)

    VkAttachmentDescription att = {0};
    att.format = s_format;
    att.samples = VK_SAMPLE_COUNT_1_BIT;
    att.loadOp = VK_ATTACHMENT_LOAD_OP_LOAD;
    att.storeOp = VK_ATTACHMENT_STORE_OP_STORE;
    att.stencilLoadOp = VK_ATTACHMENT_LOAD_OP_DONT_CARE;
    att.stencilStoreOp = VK_ATTACHMENT_STORE_OP_DONT_CARE;
    att.initialLayout = VK_IMAGE_LAYOUT_TRANSFER_DST_OPTIMAL;
    att.finalLayout = VK_IMAGE_LAYOUT_TRANSFER_SRC_OPTIMAL;

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
    if (CreateRenderPass_fn(s_device, &rpci, nullptr, &s_drawablePass) != VK_SUCCESS) {
        snprintf(s_status, sizeof(s_status), "drawable pass failed");
        return false;
    }
    return true;
}

// IOSurface render pass is owned by vulkan_mac.c (macOS-specific).

static void destroyTargets(void) {
    if (s_device == VK_NULL_HANDLE)
        return;
    VK_LOAD_DEVICE_VOID(DeviceWaitIdle)
    VK_LOAD_DEVICE_VOID(DestroySwapchainKHR)
    VK_LOAD_DEVICE_VOID(DestroyImageView)
    VK_LOAD_DEVICE_VOID(DestroyFramebuffer)
    VK_LOAD_DEVICE_VOID(DestroyRenderPass)
    if (DeviceWaitIdle_fn)
        DeviceWaitIdle_fn(s_device);

    // Final drain: shutdown idles, so every pending present callback has
    // fired and the graveyard can be flushed unconditionally.
    for (uint32_t i = 0; i < s_retiredCount; i++) {
        if (DestroySwapchainKHR_fn)
            DestroySwapchainKHR_fn(s_device, s_retired[i].chain, nullptr);
        for (uint32_t v = 0; v < s_retired[i].imageCount; v++) {
            if (s_retired[i].fbs[v] != VK_NULL_HANDLE && DestroyFramebuffer_fn)
                DestroyFramebuffer_fn(s_device, s_retired[i].fbs[v], nullptr);
            if (s_retired[i].views[v] != VK_NULL_HANDLE && DestroyImageView_fn)
                DestroyImageView_fn(s_device, s_retired[i].views[v], nullptr);
        }
    }
    s_retiredCount = 0;
    s_swapchainGeneration = 0;

    for (uint32_t i = 0; i < s_swapchainImageCount; i++) {
        if (s_swapchainFbs[i] != VK_NULL_HANDLE && DestroyFramebuffer_fn)
            DestroyFramebuffer_fn(s_device, s_swapchainFbs[i], nullptr);
        if (s_swapchainViews[i] != VK_NULL_HANDLE && DestroyImageView_fn)
            DestroyImageView_fn(s_device, s_swapchainViews[i], nullptr);
    }
    s_swapchainImageCount = 0;

    if (s_drawablePass != VK_NULL_HANDLE && DestroyRenderPass_fn)
        DestroyRenderPass_fn(s_device, s_drawablePass, nullptr);
    s_drawablePass = VK_NULL_HANDLE;

    // Cleanup IOSurface children (owned by vulkan_mac.c).
    VkMac_cleanupIOSurfaceState();

    if (s_swapchain != VK_NULL_HANDLE && DestroySwapchainKHR_fn)
        DestroySwapchainKHR_fn(s_device, s_swapchain, nullptr);
    s_swapchain = VK_NULL_HANDLE;
}

bool Vk_ready(void) {
    return s_lib && s_device != VK_NULL_HANDLE && s_swapchain != VK_NULL_HANDLE;
}

const char *Vk_status(void) {
    return s_status;
}

void Vk_shutdown(void) {
    if (!s_lib)
        return;
    if (s_device != VK_NULL_HANDLE) {
        destroyTargets();
        VkView_shutdown();
        VkSceneCanvas_shutdownModule();
        s_sceneBatchCount = 0;
        {
            VK_LOAD_DEVICE_VOID(DestroyFence)
            if (s_sceneFence != VK_NULL_HANDLE && DestroyFence_fn)
                DestroyFence_fn(s_device, s_sceneFence, nullptr);
            s_sceneFence = VK_NULL_HANDLE;
        }
        s_pipelinesBuilt = false;
    }
    if (s_instance != VK_NULL_HANDLE) {
        VK_LOAD_INSTANCE_VOID(DestroySurfaceKHR)
        if (DestroySurfaceKHR_fn)
            DestroySurfaceKHR_fn(s_instance, s_surface, nullptr);
        VK_LOAD_INSTANCE_VOID(DestroyInstance)
        if (DestroyInstance_fn)
            DestroyInstance_fn(s_instance, nullptr);
    }
    if (s_lib) {
        dlclose(s_lib);
        s_lib = nullptr;
    }
    s_device = VK_NULL_HANDLE;
    s_swapchain = VK_NULL_HANDLE;
    s_instance = VK_NULL_HANDLE;
    s_surface = VK_NULL_HANDLE;
    s_gpa = nullptr;
    s_gdpa = nullptr;
    snprintf(s_status, sizeof(s_status), "shutdown");
}

// Milestone-1 clear+present lands next: acquire -> renderpass(loadOp=CLEAR)
// -> submit -> present. The chain above is its prerequisite.


// --- hello triangle (Legacy: vulkan/TriangleRenderer.java + your spv blobs) --
//
// Full-screen triangle shader pair with a float push constant u_time. The
// fragment stage paints the animated gradient and the bouncing glow triangle;
// the vertex stage needs no vertex buffers at all.

#include <stdlib.h>

#include <mach-o/dyld.h>

// Bundle-aware spv lookup: inside the .app's Resources next to the executable,
// then cwd-relative for dev runs from the repo root, then the CMake-provided
// source dir (generated per machine — never hardcoded).
static unsigned char *loadSpv(const char *path, size_t *outSize);

static unsigned char *loadSpvAny(const char *name, size_t *outSize) {
    char path[512];
    uint32_t exeSize = sizeof(path);
    if (_NSGetExecutablePath(path, &exeSize) == 0) {
        char *slash = strrchr(path, '/');
        if (slash) {
            *slash = 0;
            // 1. Inside .app bundle: Contents/MacOS/.. -> Contents/Resources/spv/
            char candidate[512];
            snprintf(candidate, sizeof(candidate), "%s/../Resources/spv/%s", path, name);
            unsigned char *code = loadSpv(candidate, outSize);
            if (code)
                return code;

            // 2. Next to executable: <exe_dir>/spv/<name>
            snprintf(candidate, sizeof(candidate), "%s/spv/%s", path, name);
            code = loadSpv(candidate, outSize);
            if (code)
                return code;

            // 3. Executable in build/ or cmake-build-debug/: <exe_dir>/../src/vulkan/spv/<name>
            snprintf(candidate, sizeof(candidate), "%s/../src/vulkan/spv/%s", path, name);
            code = loadSpv(candidate, outSize);
            if (code)
                return code;
        }
    }

    // 4. Direct CWD-relative
    unsigned char *code = loadSpv(name, outSize);
    if (code)
        return code;

    // 5. CWD-relative src/vulkan/spv/
    snprintf(path, sizeof(path), "src/vulkan/spv/%s", name);
    code = loadSpv(path, outSize);
    if (code)
        return code;

    // 6. CWD-relative ../src/vulkan/spv/
    snprintf(path, sizeof(path), "../src/vulkan/spv/%s", name);
    code = loadSpv(path, outSize);
    if (code)
        return code;

#ifdef ANTI_SPV_DIR
    snprintf(path, sizeof(path), "%s/%s", ANTI_SPV_DIR, name);
    code = loadSpv(path, outSize);
    if (code)
        return code;
#endif
    return nullptr;
}


static unsigned char *loadSpv(const char *path, size_t *outSize) {
    FILE *f = fopen(path, "rb");
    if (!f)
        return nullptr;
    fseek(f, 0, SEEK_END);
    long size = ftell(f);
    fseek(f, 0, SEEK_SET);
    if (size <= 0 || size % 4 != 0) {
        fclose(f);
        return nullptr;
    }
    unsigned char *bytes = (unsigned char*) malloc((size_t)size);
    if (!bytes) {
        fclose(f);
        return nullptr;
    }
    if (fread(bytes, 1, (size_t)size, f) != (size_t)size) {
        free(bytes);
        fclose(f);
        return nullptr;
    }
    fclose(f);
    *outSize = (size_t)size;
    return bytes;
}

static VkShaderModule createShaderModule(const char *name, const char *unused) {
    (void)unused;
    VK_LOAD_DEVICE_PTR(CreateShaderModule)
    size_t size = 0;
    unsigned char *code = loadSpvAny(name, &size);
    if (!code)
        return VK_NULL_HANDLE;

    VkShaderModuleCreateInfo ci = { .sType = VK_STRUCTURE_TYPE_SHADER_MODULE_CREATE_INFO };
    ci.codeSize = size;
    ci.pCode = (const uint32_t*) code;

    VkShaderModule module = VK_NULL_HANDLE;
    if (CreateShaderModule_fn(s_device, &ci, nullptr, &module) != VK_SUCCESS) {
        free(code);
        return VK_NULL_HANDLE;
    }
    free(code); // driver may reference code until pipeline creation; safe for v1 demo
    return module;
}

static void decodeColor(uint32_t rgba, float *out) {
    out[0] = ((rgba >> 16) & 0xFF) / 255.0f;
    out[1] = ((rgba >> 8) & 0xFF) / 255.0f;
    out[2] = (rgba & 0xFF) / 255.0f;
    out[3] = ((rgba >> 24) & 0xFF) / 255.0f;
}

// --- Vk_fillRect: the default panel draw as a public primitive --------------
// The quad path extracted verbatim so Panel_RenderFn overrides can point at
// real content instead of poking renderer internals. Each call is self-
// contained: viewport spans the whole drawable (u_rectNdc places the quad in
// clip space), the scissor clips to THIS rect — stacking several fills in
// one handler just works.
void Vk_fillRect(void *cmdBuffer, float surfaceW, float surfaceH,
                 float x, float y, float w, float h,
                 float r, float g, float b, float a) {
    if (!cmdBuffer || w <= 0.0f || h <= 0.0f || surfaceW <= 0.0f || surfaceH <= 0.0f)
        return;
    if (!s_pipelinesBuilt || s_quadPipeline == VK_NULL_HANDLE)
        return;

    VK_LOAD_DEVICE_VOID(CmdBindPipeline)
    VK_LOAD_DEVICE_VOID(CmdSetViewport)
    VK_LOAD_DEVICE_VOID(CmdSetScissor)
    VK_LOAD_DEVICE_VOID(CmdPushConstants)
    VK_LOAD_DEVICE_VOID(CmdDraw)

    float drawW = surfaceW;
    float drawH = surfaceH;
    // Clip against the drawable here: handlers receive pre-clipped rects,
    // but defensive clipping keeps stacked sub-rects honest for free.
    float fx = x < 0.0f ? 0.0f : x;
    float fy = y < 0.0f ? 0.0f : y;
    if (fx + w > drawW) w = drawW - fx;
    if (fy + h > drawH) h = drawH - fy;
    if (w <= 0.0f || h <= 0.0f)
        return;

    VkViewport viewport = {0};
    viewport.width = drawW;
    viewport.height = -drawH; // Negative for top-down UI map to bottom-up frame
    viewport.y = drawH;
    viewport.maxDepth = 1.0f;
    VkRect2D scissor = {0};
    scissor.offset.x = (int32_t)fx;
    scissor.offset.y = (int32_t)(drawH - fy - h);
    scissor.extent.width = (uint32_t)w;
    scissor.extent.height = (uint32_t)h;

    float ndc[8]; // rectNdc.xyzw + color.rgba, push-constant block
    ndc[0] = fx / drawW * 2.0f - 1.0f;
    ndc[1] = fy / drawH * 2.0f - 1.0f;
    ndc[2] = w / drawW * 2.0f;
    ndc[3] = h / drawH * 2.0f;
    ndc[4] = r;
    ndc[5] = g;
    ndc[6] = b;
    ndc[7] = a;
    CmdBindPipeline_fn(cmdBuffer, VK_PIPELINE_BIND_POINT_GRAPHICS, s_quadPipeline);
    CmdSetViewport_fn(cmdBuffer, 0, 1, &viewport);
    CmdSetScissor_fn(cmdBuffer, 0, 1, &scissor);
    CmdPushConstants_fn(cmdBuffer, s_quadLayout,
                        VK_SHADER_STAGE_VERTEX_BIT | VK_SHADER_STAGE_FRAGMENT_BIT,
                        0, 32, ndc);
    CmdDraw_fn(cmdBuffer, 6, 1, 0, 0);
}

// --- collage verification: read the monitor cache back to a TGA -------------
// ANTI_VK_DUMP=1. Phase 1 records a cache->buffer copy INSIDE the present
// command buffer (correct layout by construction, GPU-ordered); phase 2,
// next frame after the fence proves it retired, maps and writes
// /tmp/vk_cache_dump.tga. No timing or layout assumptions.
static VkBuffer s_dumpBuffer;
static VkDeviceMemory s_dumpMem;
static VkDeviceSize s_dumpSize = 0;
static int32_t s_dumpW = 0, s_dumpH = 0;

static bool dumpAllocStage(uint32_t width, uint32_t height) {
    VK_LOAD_DEVICE(CreateBuffer)
    VK_LOAD_DEVICE(GetBufferMemoryRequirements)
    VK_LOAD_DEVICE(AllocateMemory)
    VK_LOAD_DEVICE(BindBufferMemory)
    VK_LOAD_INSTANCE(GetPhysicalDeviceMemoryProperties)

    s_dumpW = (int32_t)width;
    s_dumpH = (int32_t)height;
    s_dumpSize = (VkDeviceSize)s_dumpW * s_dumpH * 4;

    VkBufferCreateInfo bci = { .sType = VK_STRUCTURE_TYPE_BUFFER_CREATE_INFO };
    bci.size = s_dumpSize;
    bci.usage = VK_BUFFER_USAGE_TRANSFER_DST_BIT;
    bci.sharingMode = VK_SHARING_MODE_EXCLUSIVE;
    if (CreateBuffer_fn(s_device, &bci, nullptr, &s_dumpBuffer) != VK_SUCCESS)
        return false;

    VkMemoryRequirements req;
    GetBufferMemoryRequirements_fn(s_device, s_dumpBuffer, &req);
    VkPhysicalDeviceMemoryProperties props;
    GetPhysicalDeviceMemoryProperties_fn(s_phys, &props);
    uint32_t typeIdx = UINT32_MAX;
    for (uint32_t i = 0; i < props.memoryTypeCount; i++) {
        if (!(req.memoryTypeBits & (1u << i)))
            continue;
        if ((props.memoryTypes[i].propertyFlags & VK_MEMORY_PROPERTY_HOST_VISIBLE_BIT)
            && (props.memoryTypes[i].propertyFlags & VK_MEMORY_PROPERTY_HOST_COHERENT_BIT)) {
            typeIdx = i;
            break;
        }
    }
    if (typeIdx == UINT32_MAX)
        return false;

    VkMemoryAllocateInfo mai = { .sType = VK_STRUCTURE_TYPE_MEMORY_ALLOCATE_INFO };
    mai.allocationSize = req.size;
    mai.memoryTypeIndex = typeIdx;
    if (AllocateMemory_fn(s_device, &mai, nullptr, &s_dumpMem) != VK_SUCCESS)
        return false;
    BindBufferMemory_fn(s_device, s_dumpBuffer, s_dumpMem, 0);
    return true;
}

static bool dumpRecordCopy(VkImage image, VkCommandBuffer cb) {
    // Called mid-recording, immediately after the drawable pass: the image
    // is in TRANSFER_SRC_OPTIMAL and the barrier ordering is already correct.
    VK_LOAD_DEVICE(CmdCopyImageToBuffer)
    VkBufferImageCopy region = {0};
    region.bufferRowLength = (uint32_t)s_dumpW;
    region.bufferImageHeight = (uint32_t)s_dumpH;
    region.imageSubresource.aspectMask = VK_IMAGE_ASPECT_COLOR_BIT;
    region.imageSubresource.layerCount = 1;
    region.imageExtent.width = (uint32_t)s_dumpW;
    region.imageExtent.height = (uint32_t)s_dumpH;
    region.imageExtent.depth = 1;
    CmdCopyImageToBuffer_fn(cb, image,
                            VK_IMAGE_LAYOUT_TRANSFER_SRC_OPTIMAL,
                            s_dumpBuffer, 1, &region);
    return true;
}

static bool dumpWriteFile(void) {
    VK_LOAD_DEVICE(MapMemory)
    VK_LOAD_DEVICE(UnmapMemory)
    VK_LOAD_DEVICE(FreeMemory)
    VK_LOAD_DEVICE(DestroyBuffer)

    void *mapped = nullptr;
    if (MapMemory_fn(s_device, s_dumpMem, 0, s_dumpSize, 0, &mapped) == VK_SUCCESS) {
        FILE *f = fopen("/tmp/vk_cache_dump.tga", "wb");
        if (f) {
            uint8_t hdr[18] = {0};
            hdr[2] = 2;                 // uncompressed true-color
            hdr[12] = (uint8_t)(s_dumpW & 0xFF);  hdr[13] = (uint8_t)(s_dumpW >> 8);
            hdr[14] = (uint8_t)(s_dumpH & 0xFF);  hdr[15] = (uint8_t)(s_dumpH >> 8);
            hdr[16] = 32;               // bits per pixel
            hdr[17] = 0x20;             // top-left origin
            fwrite(hdr, 1, 18, f);
            fwrite(mapped, 1, (size_t)s_dumpSize, f);
            fclose(f);
            fprintf(stderr, "vk:dump -> /tmp/vk_cache_dump.tga (%dx%d)\n", s_dumpW, s_dumpH);
        }
        UnmapMemory_fn(s_device, s_dumpMem);
    } else {
        fprintf(stderr, "vk:dump map failed\n");
    }
    DestroyBuffer_fn(s_device, s_dumpBuffer, nullptr);
    FreeMemory_fn(s_device, s_dumpMem, nullptr);
    s_dumpBuffer = VK_NULL_HANDLE;
    s_dumpMem = VK_NULL_HANDLE;
    return true;
}


// Build both child pipelines against the monitor view's cache renderpass,
// plus the command pool and primary buffer that record each present loop.
// Runs once at init — no more lazy first-frame building, no retry leaks.
static bool buildPipelines(void) {
    VK_LOAD_DEVICE(CreatePipelineLayout)
    VK_LOAD_DEVICE(CreateGraphicsPipelines)
    VK_LOAD_DEVICE(CreateCommandPool)
    VK_LOAD_DEVICE(AllocateCommandBuffers)

    // Renderpass compatibility: the drawable pass carries the same format +
    // single-subpass shape the pipelines were designed against, so they run
    // unchanged whether targeting a scene canvas or the window itself.
    if (s_drawablePass == VK_NULL_HANDLE) {
        snprintf(s_status, sizeof(s_status), "no drawable renderpass");
        return false;
    }
    VkRenderPass pass = s_drawablePass;

    // --- shared pipeline skeleton ---------------------------------------
    VkPipelineVertexInputStateCreateInfo vi = { .sType = VK_STRUCTURE_TYPE_PIPELINE_VERTEX_INPUT_STATE_CREATE_INFO };
    VkPipelineInputAssemblyStateCreateInfo ia = { .sType = VK_STRUCTURE_TYPE_PIPELINE_INPUT_ASSEMBLY_STATE_CREATE_INFO };
    ia.topology = VK_PRIMITIVE_TOPOLOGY_TRIANGLE_LIST;
    VkPipelineViewportStateCreateInfo vp = { .sType = VK_STRUCTURE_TYPE_PIPELINE_VIEWPORT_STATE_CREATE_INFO };
    vp.viewportCount = 1;
    vp.scissorCount = 1;
    VkPipelineRasterizationStateCreateInfo rs = { .sType = VK_STRUCTURE_TYPE_PIPELINE_RASTERIZATION_STATE_CREATE_INFO };
    rs.polygonMode = VK_POLYGON_MODE_FILL;
    rs.cullMode = VK_CULL_MODE_NONE; // push-rect quads face either way
    rs.lineWidth = 1.0f;
    VkPipelineMultisampleStateCreateInfo ms = { .sType = VK_STRUCTURE_TYPE_PIPELINE_MULTISAMPLE_STATE_CREATE_INFO };
    ms.rasterizationSamples = VK_SAMPLE_COUNT_1_BIT;
    VkPipelineColorBlendAttachmentState blend = {0};
    blend.colorWriteMask = VK_COLOR_COMPONENT_R_BIT | VK_COLOR_COMPONENT_G_BIT
                         | VK_COLOR_COMPONENT_B_BIT | VK_COLOR_COMPONENT_A_BIT;
    VkPipelineColorBlendStateCreateInfo cb = { .sType = VK_STRUCTURE_TYPE_PIPELINE_COLOR_BLEND_STATE_CREATE_INFO };
    cb.attachmentCount = 1;
    cb.pAttachments = &blend;
    VkDynamicState dynStates[2] = { VK_DYNAMIC_STATE_VIEWPORT, VK_DYNAMIC_STATE_SCISSOR };
    VkPipelineDynamicStateCreateInfo ds = { .sType = VK_STRUCTURE_TYPE_PIPELINE_DYNAMIC_STATE_CREATE_INFO };
    ds.dynamicStateCount = 2;
    ds.pDynamicStates = dynStates;

    // --- triangle child (legacy hello-triangle scene content) ------------
    VkShaderModule triVert = createShaderModule("hello_triangle_vert.spv", nullptr);
    VkShaderModule triFrag = createShaderModule("hello_triangle_frag.spv", nullptr);
    if (triVert == VK_NULL_HANDLE || triFrag == VK_NULL_HANDLE) {
        snprintf(s_status, sizeof(s_status), "triangle spv not found");
        fprintf(stderr, "vk: triangle spv not found\n");
        return false;
    }

    VkPushConstantRange triPush = {0};
    triPush.stageFlags = VK_SHADER_STAGE_VERTEX_BIT;
    triPush.offset = 0;
    triPush.size = 4; // float u_time

    VkPipelineLayoutCreateInfo tlci = { .sType = VK_STRUCTURE_TYPE_PIPELINE_LAYOUT_CREATE_INFO };
    tlci.pushConstantRangeCount = 1;
    tlci.pPushConstantRanges = &triPush;
    if (CreatePipelineLayout_fn(s_device, &tlci, nullptr, &s_triLayout) != VK_SUCCESS) {
        snprintf(s_status, sizeof(s_status), "tri layout failed");
        return false;
    }

    VkPipelineShaderStageCreateInfo tstages[2] = {{0}, {0}};
    tstages[0].sType = VK_STRUCTURE_TYPE_PIPELINE_SHADER_STAGE_CREATE_INFO;
    tstages[0].stage = VK_SHADER_STAGE_VERTEX_BIT;
    tstages[0].module = triVert;
    tstages[0].pName = "main";
    tstages[1].sType = VK_STRUCTURE_TYPE_PIPELINE_SHADER_STAGE_CREATE_INFO;
    tstages[1].stage = VK_SHADER_STAGE_FRAGMENT_BIT;
    tstages[1].module = triFrag;
    tstages[1].pName = "main";

    rs.frontFace = VK_FRONT_FACE_CLOCKWISE;   // legacy triangle winding
    rs.cullMode = VK_CULL_MODE_BACK_BIT;
    VkGraphicsPipelineCreateInfo tpci = { .sType = VK_STRUCTURE_TYPE_GRAPHICS_PIPELINE_CREATE_INFO };
    tpci.stageCount = 2;
    tpci.pStages = tstages;
    tpci.pVertexInputState = &vi;
    tpci.pInputAssemblyState = &ia;
    tpci.pViewportState = &vp;
    tpci.pRasterizationState = &rs;
    tpci.pMultisampleState = &ms;
    tpci.pColorBlendState = &cb;
    tpci.pDynamicState = &ds;
    tpci.layout = s_triLayout;
    tpci.renderPass = pass;
    tpci.subpass = 0;
    if (CreateGraphicsPipelines_fn(s_device, VK_NULL_HANDLE, 1, &tpci, nullptr, &s_triPipeline) != VK_SUCCESS) {
        snprintf(s_status, sizeof(s_status), "tri pipeline failed");
        return false;
    }

    // --- quad child (solid panel fill) ------------------------------------
    VkShaderModule quadVert = createShaderModule("solid_quad_vert.spv", nullptr);
    VkShaderModule quadFrag = createShaderModule("solid_quad_frag.spv", nullptr);
    if (quadVert == VK_NULL_HANDLE || quadFrag == VK_NULL_HANDLE) {
        snprintf(s_status, sizeof(s_status), "quad spv not found");
        fprintf(stderr, "vk: quad spv not found\n");
        return false;
    }

    VkPushConstantRange quadPush[2] = {{0}, {0}};
    quadPush[0].stageFlags = VK_SHADER_STAGE_VERTEX_BIT;
    quadPush[0].offset = 0;
    quadPush[0].size = 16; // vec4 u_rectNdc
    quadPush[1].stageFlags = VK_SHADER_STAGE_FRAGMENT_BIT;
    quadPush[1].offset = 16;
    quadPush[1].size = 16; // vec4 u_color

    VkPipelineLayoutCreateInfo qlci = { .sType = VK_STRUCTURE_TYPE_PIPELINE_LAYOUT_CREATE_INFO };
    qlci.pushConstantRangeCount = 2;
    qlci.pPushConstantRanges = quadPush;
    if (CreatePipelineLayout_fn(s_device, &qlci, nullptr, &s_quadLayout) != VK_SUCCESS) {
        snprintf(s_status, sizeof(s_status), "quad layout failed");
        return false;
    }

    VkPipelineShaderStageCreateInfo qstages[2] = {{0}, {0}};
    qstages[0].sType = VK_STRUCTURE_TYPE_PIPELINE_SHADER_STAGE_CREATE_INFO;
    qstages[0].stage = VK_SHADER_STAGE_VERTEX_BIT;
    qstages[0].module = quadVert;
    qstages[0].pName = "main";
    qstages[1].sType = VK_STRUCTURE_TYPE_PIPELINE_SHADER_STAGE_CREATE_INFO;
    qstages[1].stage = VK_SHADER_STAGE_FRAGMENT_BIT;
    qstages[1].module = quadFrag;
    qstages[1].pName = "main";

    rs.frontFace = VK_FRONT_FACE_CLOCKWISE;
    rs.cullMode = VK_CULL_MODE_NONE;
    VkGraphicsPipelineCreateInfo qpci = { .sType = VK_STRUCTURE_TYPE_GRAPHICS_PIPELINE_CREATE_INFO };
    qpci.stageCount = 2;
    qpci.pStages = qstages;
    qpci.pVertexInputState = &vi;
    qpci.pInputAssemblyState = &ia;
    qpci.pViewportState = &vp;
    qpci.pRasterizationState = &rs;
    qpci.pMultisampleState = &ms;
    qpci.pColorBlendState = &cb;
    qpci.pDynamicState = &ds;
    qpci.layout = s_quadLayout;
    qpci.renderPass = pass;
    qpci.subpass = 0;
    if (CreateGraphicsPipelines_fn(s_device, VK_NULL_HANDLE, 1, &qpci, nullptr, &s_quadPipeline) != VK_SUCCESS) {
        snprintf(s_status, sizeof(s_status), "quad pipeline failed");
        return false;
    }

    // --- command plumbing --------------------------------------------------
    VkCommandPoolCreateInfo cpci = { .sType = VK_STRUCTURE_TYPE_COMMAND_POOL_CREATE_INFO };
    cpci.flags = VK_COMMAND_POOL_CREATE_RESET_COMMAND_BUFFER_BIT;
    cpci.queueFamilyIndex = s_queueFamily;
    if (CreateCommandPool_fn(s_device, &cpci, nullptr, &s_cmdPool) != VK_SUCCESS) {
        snprintf(s_status, sizeof(s_status), "cmdpool failed");
        return false;
    }
    VkCommandBufferAllocateInfo cbai = { .sType = VK_STRUCTURE_TYPE_COMMAND_BUFFER_ALLOCATE_INFO };
    cbai.commandPool = s_cmdPool;
    cbai.level = VK_COMMAND_BUFFER_LEVEL_PRIMARY;
    cbai.commandBufferCount = 2;
    {
        VkCommandBuffer cbs[2] = {VK_NULL_HANDLE, VK_NULL_HANDLE};
        AllocateCommandBuffers_fn(s_device, &cbai, cbs);
        s_cmdBuffer = cbs[0];      // collage + present
        s_sceneBuffer = cbs[1];    // scene production (phase 2)
    }

    s_pipelinesBuilt = true;
    return true;
}

bool Vk_clearPresent(void) {
    // Resize-cadence calls arrive on thread 0 while the worker may be mid-
    // frame. Try-lock: the busy side wins, the other drops this tick — the
    // regular loop always carries fresher state one tick later.
    if (!SpinLock_tryLock(&s_presentLock))
        return false;
    bool ok = presentFrameLocked();
    SpinLock_unlock(&s_presentLock);
    return ok;
}

static bool presentFrameTail(uint32_t imageIndex);

// Per-frame geometry handed from the head (presentFrameLocked) to the tail
// (presentFrameTail). Single presenter at a time holds s_presentLock, so a
// plain file-scope struct is race-free.
static struct {
    int winW, winH;   // window content size in points
    float kx, ky;     // drawable px per layout point (monitor scale)
    Panel *root;      // basket panel
    float bg[4];      // board clear color
    bool nativeContent; // content panel is IOSurface-backed (skip Vulkan render)
    int contentW, contentH; // content panel size for IOSurface resize
} s_frame;

// Forward declarations

;;PLATFORM_EXCLUSIVE("Mac")
;;INTENTION("Thin wrappers that delegate IOSurface rendering to vulkan_mac.c.")

static void renderNativeContent(Window *window, Panel *contentPanel, int winW, int winH, float kx, float ky) {
    VkMac_renderNativeContent(window, contentPanel, winW, winH, kx, ky, &s_frame.nativeContent);
}

static bool presentFrameLocked(void) {
    if (!Vk_ready() || !s_pipelinesBuilt) return false;

    // Retire the PREVIOUS frame through its fence BEFORE touching the chain.
    // This is what lets extent-driven rebuilds happen every drag tick without
    // stalling: no DeviceWaitIdle exists anywhere in this path.
    // NOTE: the fence is deliberately left SIGNALLED here — ResetFences moves
    // to just before QueueSubmit, so EVERY early-return below leaves the fence
    // in the signaled state and the next frame retires instantly instead of
    // hanging on an unsignaled wait.
    VK_LOAD_DEVICE(WaitForFences)
    VK_LOAD_DEVICE(ResetCommandBuffer)
    VK_LOAD_DEVICE(AcquireNextImageKHR)
    WaitForFences_fn(s_device, 1, &s_fence, VK_TRUE, UINT64_MAX);
    ResetCommandBuffer_fn(s_cmdBuffer, 0);

    if (!s_dumpEnvRead) {
        s_dumpEnvRead = true;
        s_dumpEnabled = getenv("ANTI_VK_DUMP") != nullptr;
    }
    if (s_dumpEnabled && s_dumpStage == 1) {
        dumpWriteFile();
        s_dumpStage = 2;
    }

    // Policy drift (presentMode / transparent changed) wants a fresh chain.
    uint64_t renderGen = Window_renderGeneration(s_window);
    if (renderGen != s_appliedRenderGen && !rebuildTargets()) return false;

    // Live caps: the surface outgrowing the chain is THE resize signal.
    // Rebuild policy: ASAP by default — every diverged tick rebuilds and
    // presents inline (the sync bridge carries them; the graveyard makes
    // per-tick churn safe). ANTI_RESIZE_HZ can re-impose a cap (e.g. 30)
    // on low-end machines. The first tick of any swing always passes
    // instantly (zero timer), and settling re-arms instant response for
    // the next swing. Skipped ticks present nothing: TopLeft gravity
    // holds the last exact-sized frame cropped while the border runs —
    // never stretched.
    VK_LOAD_INSTANCE(GetPhysicalDeviceSurfaceCapabilitiesKHR)
    static uint64_t s_lastRebuildNs = 0;
    static int s_hzInit = 0;
    static int64_t s_minRebuildGapNs = 0;
    if (!s_hzInit) {
        s_hzInit = 1;
        const char *hzEnv = getenv("ANTI_RESIZE_HZ");
        int hz = hzEnv ? atoi(hzEnv) : 0; // 0 = uncapped: rebuild every tick
        s_minRebuildGapNs = hz > 0 ? (int64_t)(1000000000LL / hz) : 0;
    }
    VkSurfaceCapabilitiesKHR live;
    memset(&live, 0, sizeof(live));
    if (GetPhysicalDeviceSurfaceCapabilitiesKHR_fn(s_phys, s_surface, &live) == VK_SUCCESS
        && (live.currentExtent.width != s_extent.width || live.currentExtent.height != s_extent.height)) {
        uint64_t nowNs = NanoTime_now();
        if (s_lastRebuildNs != 0 && s_minRebuildGapNs > 0
            && nowNs - s_lastRebuildNs < (uint64_t)s_minRebuildGapNs) {
            return false; // throttled: hold the last good frame this tick
        }
        fprintf(stderr, "vk: extent moved %ux%u -> %ux%u; rebuilding\n",
                s_extent.width, s_extent.height,
                live.currentExtent.width, live.currentExtent.height);
        if (!rebuildTargets()) return false; // failed rebuild retries ASAP
        s_lastRebuildNs = NanoTime_now();
    } else {
        s_lastRebuildNs = 0; // settled: next divergence reacts instantly
    }

    // Window geometry: content SIZE in points. Position is irrelevant BY LAW
    // now — the compositor is window-local; every child is stamped relative
    // to the drawable's own top-left corner, so moving the window costs the
    // GPU path literally nothing and nothing can go stale.
    int winW = s_window ? Window_width(s_window) : 0;
    int winH = s_window ? Window_height(s_window) : 0;
    if (winW <= 0 || winH <= 0)
        return false;

    // Monitor scale oracle: kx/ky convert layout points to drawable pixels
    // crisply (retina included). The view's CACHE is no longer read or
    // written anywhere in this file — only its point/pixel ratio matters.
    // Join by the window's mirrored display id; location sniffing and view 0
    // are fallbacks for a stale discovery list or an unmapped window.
    int locX = 0, locY = 0;
    Window_getLocation(s_window, &locX, &locY);
    VkView *view = VkView_forMonitor(Window_getMonitorId(s_window));
    if (!view)
        view = VkView_forPoint((float)locX, (float)locY);
    if (!view)
        view = VkView_at(0);
    if (!view)
        return false;

    float cacheW = (float)VkView_getWidth(view);
    float cacheH = (float)VkView_getHeight(view);
    float pointW = (float)VkView_getPointWidth(view);
    float pointH = (float)VkView_getPointHeight(view);
    if (cacheW <= 0.0f || cacheH <= 0.0f || pointW <= 0.0f || pointH <= 0.0f)
        return false;
    s_frame.kx = cacheW / pointW; // drawable px per layout point
    s_frame.ky = cacheH / pointH;


    // Basket mirror: rewrite its w/h to the window's content size on drift.
    s_frame.root = Window_getContainer(s_window);
    Panel *root = s_frame.root;

    // Content panel (IOSurface) path: each child renders via Vulkan into
    // its own IOSurface; AppKit composites the CALayers.
    // Note: contentPanel itself is purely a placeholder (its own background is ignored).
    s_frame.nativeContent = false;
    Panel *contentPanel = Window_getContentPanel(s_window);
    if (contentPanel) {
        Container_setSize(&(*contentPanel).base, (float)winW, (float)winH);
        if (Window_isNativeContainerOnRoot(s_window)) {
            s_frame.nativeContent = true;
            // Native pixel dimensions: points × scale factor
            int nativePxW = (int)(winW * s_frame.kx + 0.5f);
            int nativePxH = (int)(winH * s_frame.ky + 0.5f);
            s_frame.contentW = nativePxW;
            s_frame.contentH = nativePxH;
            renderNativeContent(s_window, contentPanel, winW, winH, s_frame.kx, s_frame.ky);
            // Composite CALayers into window's layer tree
            Window_compositeIOSurfaceChildren(s_window, contentPanel);
            // DO NOT RETURN TRUE HERE. We must continue to render the background swapchain
            // for the Scene3D and window clearing!
        }
    }
    Panel *scenePanel = Window_getScenePanel(s_window);
    if (scenePanel != nullptr) {
        Container_setSize(&(*scenePanel).base, (float)winW, (float)winH);
    }
    uint64_t sizeGen = Window_sizeGeneration(s_window);
    if (root != nullptr && sizeGen != s_mirroredSizeGen) {
        s_mirroredSizeGen = sizeGen;
        Container_setSize(&(*root).base, (float)winW, (float)winH);
    }

    // Clear law: background color inherits from scenePanel (or root).
    // contentPanel background color is ignored (contentPanel is purely a placeholder).
    s_frame.bg[0] = s_frame.bg[1] = s_frame.bg[2] = s_frame.bg[3] = 0.0f;
    if (scenePanel != nullptr) {
        uint32_t bgColor = Panel_getBackgroundColor(scenePanel);
        if (bgColor != 0)
            decodeColor(bgColor, s_frame.bg);
    } else if (root != nullptr && root != contentPanel) {
        uint32_t bgColor = Panel_getBackgroundColor(root);
        if (bgColor != 0)
            decodeColor(bgColor, s_frame.bg);
    }
    s_frame.winW = winW;
    s_frame.winH = winH;

    // --- acquire first: the blit needs its target index ------------------
    // FINITE timeout, never UINT64_MAX: during zoom/fullscreen transitions
    // Core Animation can pause drawable recycling for a few hundred ms. An
    // unbounded acquire would pin this thread — holding s_presentLock — and
    // beachball every sync-bridge caller behind it. A timed-out tick simply
    // holds the last good frame; nothing is lost. Capped at ~1 display
    // frame (16.7 ms): a longer cap lets one stalled acquire eat two frames
    // of cadence.
    uint32_t imageIndex = 0;
    VkResult ar = AcquireNextImageKHR_fn(s_device, s_swapchain, 25000000ULL /* ~1 frame */,
                                         s_semAcquire, VK_NULL_HANDLE, &imageIndex);
    if (ar == VK_ERROR_OUT_OF_DATE_KHR) {
        // Reactive repair. GAPLESS LAW: do NOT bail — the chain is dead but
        // the surface geometry is current (bg/kx/ky were just computed), so
        // rebuild and present THIS tick. Bailing here leaves the compositor
        // holding the last frame at the old size for a full extra tick: the
        // visible "unrendered panel -> rendered panel" pop during resize.
        if (!rebuildTargets()) return false;
        ar = AcquireNextImageKHR_fn(s_device, s_swapchain, 25000000ULL,
                                    s_semAcquire, VK_NULL_HANDLE, &imageIndex);
        if (ar != VK_SUCCESS && ar != VK_SUBOPTIMAL_KHR) return false;
        return presentFrameTail(imageIndex);
    }
    if (ar == VK_TIMEOUT || ar == VK_NOT_READY) {
        // No drawable freed in time (compositor busy / mid-transition).
        // Fence is still signaled — safe to bail clean.
        return false;
    }
    if (ar != VK_SUCCESS && ar != VK_SUBOPTIMAL_KHR) return false;

    return presentFrameTail(imageIndex);
}

// Present tail: records the collage into the freshly acquired drawable,
// submits, and presents. Shared by the normal tick AND the OUT_OF_DATE
// recovery path (GAPLESS LAW: rebuild+present in one tick). Reads winW/
// winH/kx/ky/root/bg computed by presentFrameLocked just above.
static bool presentFrameTail(uint32_t imageIndex) {
    // The drawable IS the window now: its true size is the chain's creation
    // extent, and every coordinate below lives in that space.
    VK_LOAD_DEVICE(WaitForFences)
    VK_LOAD_DEVICE(BeginCommandBuffer)
    VK_LOAD_DEVICE(EndCommandBuffer)
    VK_LOAD_DEVICE(CmdBeginRenderPass)
    VK_LOAD_DEVICE(CmdEndRenderPass)
    VK_LOAD_DEVICE(CmdBindPipeline)
    VK_LOAD_DEVICE(CmdPushConstants)
    VK_LOAD_DEVICE(CmdSetViewport)
    VK_LOAD_DEVICE(CmdSetScissor)
    VK_LOAD_DEVICE(CmdDraw)
    VK_LOAD_DEVICE(CmdBlitImage)
    VK_LOAD_DEVICE(CmdClearColorImage)
    VK_LOAD_DEVICE(CmdPipelineBarrier)
    VK_LOAD_DEVICE(QueueSubmit)
    VK_LOAD_DEVICE(ResetFences)
    VK_LOAD_DEVICE(QueuePresentKHR)

    VkCommandBufferBeginInfo bbi = { .sType = VK_STRUCTURE_TYPE_COMMAND_BUFFER_BEGIN_INFO };
    BeginCommandBuffer_fn(s_cmdBuffer, &bbi);

    float uTime = (float)((double)(NanoTime_now() - s_animStartNanos) / 1e9);

    static uint32_t s_frameNo = 0;
    static bool s_trace = false;
    static int s_traceInit = 0;
    if (!s_traceInit) {
        s_traceInit = 1;
        s_trace = getenv("ANTI_VK_TRACE") != nullptr;
    }

    // --- 0. scene canvases: flip-buffers + own clock (phases 2+3) ---------
    // PRODUCTION records into back images on its OWN command buffer and
    // submits under its own fence — a slow AAA pass stays in flight across
    // many present frames. CONSUMPTION stamps the newest FINISHED front
    // image every frame, whatever production is doing. ANTI_SCENE_HZ paces
    // production independently of present rate.
    struct SceneCut {
        VkSceneCanvas *canvas;
        int32_t dx, dy, dw, dh; // unclipped dest rect in drawable pixels
    };
    struct SceneCut scenes[8];
    uint32_t sceneCount = 0;

    // Harvest any completed batch: generation-matched flips land, fronts
    // advance, and the graveyard frees whatever a resize retired before it.
    bool productionBlocked = false;
    if (s_sceneBatchCount > 0) {
        if (WaitForFences_fn(s_device, 1, &s_sceneFence, VK_TRUE, 0) == VK_SUCCESS) {
            for (uint32_t i = 0; i < s_sceneBatchCount; i++) {
                SceneBatchEntry *e = &s_sceneBatch[i];
                if (VkSceneCanvas_generation((*e).canvas) == (*e).gen)
                    VkSceneCanvas_flip((*e).canvas);
            }
            s_sceneBatchCount = 0;
            VkSceneCanvas_flushRetired();
        } else {
            // Previous batch still executing: one batch in flight at a time.
            // Keeps ResetFences off a busy fence; production resumes next tick.
            productionBlocked = true;
        }
    }

    // Production pacing: ANTI_SCENE_HZ (default 60; 0 = every present tick).
    static int s_sceneHzInit = 0;
    static int64_t s_sceneGapNs = 0;
    if (!s_sceneHzInit) {
        s_sceneHzInit = 1;
        const char *sceneHzEnv = getenv("ANTI_SCENE_HZ");
        int hz = sceneHzEnv ? atoi(sceneHzEnv) : 60;
        s_sceneGapNs = hz > 0 ? (int64_t)(1000000000LL / hz) : 0;
    }
    uint64_t sceneNowNs = NanoTime_now();
    uint32_t rendered = 0;
    bool sceneCbOpen = false;

    Panel *sceneCandidates[8];
    uint32_t candidateCount = 0;

    Panel *winScene = Window_getScenePanel(s_window);
    if (winScene != nullptr) {
        uint32_t st = Memory_type(winScene);
        if (st == TYPE_SCENE3D_SINGLETON || st == TYPE_SCENE2D_SINGLETON || st == TYPE_SCENE_SINGLETON) {
            sceneCandidates[candidateCount++] = winScene;
        }
    }

    if (s_frame.root != nullptr) {
        uint32_t rt = Memory_type(s_frame.root);
        if ((rt == TYPE_SCENE3D_SINGLETON || rt == TYPE_SCENE2D_SINGLETON || rt == TYPE_SCENE_SINGLETON)
            && s_frame.root != winScene && candidateCount < 8) {
            sceneCandidates[candidateCount++] = s_frame.root;
        }
        size_t childCount = Panel_childCount(s_frame.root);
        for (size_t i = 0; i < childCount && candidateCount < 8; i++) {
            Panel *child = Panel_getChild(s_frame.root, i);
            if (!child)
                continue;
            // Skip content panel when IOSurface-backed (rendered by AppKit)
            if (s_frame.nativeContent && child == Window_getContentPanel(s_window))
                continue;
            uint32_t childType = Memory_type(child);
            if (!(childType == TYPE_SCENE3D_SINGLETON || childType == TYPE_SCENE2D_SINGLETON
                  || childType == TYPE_SCENE_SINGLETON))
                continue;
            if (child == winScene)
                continue;
            sceneCandidates[candidateCount++] = child;
        }
    }

    for (uint32_t ci = 0; ci < candidateCount && sceneCount < 8; ci++) {
        Panel *child = sceneCandidates[ci];
        Vec4 rect;
        Container_resolve(&(*child).base, 0.0f, 0.0f,
                          (float)s_frame.winW, (float)s_frame.winH, &rect);
        if (rect.z <= 0.0f || rect.w <= 0.0f)
            continue;

        // Canvas pixels = logical points scaled by THIS view's grid, so
        // canvas->drawable blits stay pixel-exact on every monitor.
        uint32_t wantW = (uint32_t)(rect.z * s_frame.kx + 0.5f);
        uint32_t wantH = (uint32_t)(rect.w * s_frame.ky + 0.5f);
        if (wantW == 0 || wantH == 0)
            continue;
        VkSceneCanvas *canvas = VkSceneCanvas_acquire((uintptr_t)child, wantW, wantH);
        if (!canvas)
            continue;

        // PRODUCTION: only when the previous batch resolved, the clock
        // says due, and no pass is in flight for THIS canvas. Otherwise
        // the finished front simply serves again.
        if (!productionBlocked && s_sceneBatchCount < VK_SCENE_BATCH_MAX
            && VkSceneCanvas_needsRender(canvas, sceneNowNs, s_sceneGapNs)) {
            if (!sceneCbOpen) {
                // Lazy open: reset+begin only when a batch is actually
                // forming AND the previous one has fully resolved —
                // resetting an executing command buffer is UB.
                VK_LOAD_DEVICE(ResetCommandBuffer)
                ResetCommandBuffer_fn(s_sceneBuffer, 0);
                VkCommandBufferBeginInfo sbi = { .sType = VK_STRUCTURE_TYPE_COMMAND_BUFFER_BEGIN_INFO };
                BeginCommandBuffer_fn(s_sceneBuffer, &sbi);
                sceneCbOpen = true;
            }
            VkSceneCanvas_beginBackPass(canvas, s_sceneBuffer, 0.0f, 0.0f, 0.0f, 1.0f);
            VkViewport cvp = {0};
            cvp.width = (float)wantW;
            cvp.height = (float)wantH;
            cvp.maxDepth = 1.0f;
            VkRect2D csc = {0};
            csc.extent.width = wantW;
            csc.extent.height = wantH;
            CmdBindPipeline_fn(s_sceneBuffer, VK_PIPELINE_BIND_POINT_GRAPHICS, s_triPipeline);
            CmdSetViewport_fn(s_sceneBuffer, 0, 1, &cvp);
            CmdSetScissor_fn(s_sceneBuffer, 0, 1, &csc);
            CmdPushConstants_fn(s_sceneBuffer, s_triLayout,
                                VK_SHADER_STAGE_VERTEX_BIT, 0, 4, &uTime);
            CmdDraw_fn(s_sceneBuffer, 3, 1, 0, 0);
            VkSceneCanvas_endBackPass(canvas, s_sceneBuffer);
            s_sceneBatch[s_sceneBatchCount].canvas = canvas;
            s_sceneBatch[s_sceneBatchCount].gen = VkSceneCanvas_generation(canvas);
            s_sceneBatchCount++;
            rendered++;
        }

        struct SceneCut *cut = &scenes[sceneCount++];
        (*cut).canvas = canvas;
        // Window-local: a child hanging off the window's own edge goes
        // negative here — the stamp clips it against the drawable.
        (*cut).dx = (int32_t)(rect.x * s_frame.kx);
        (*cut).dy = (int32_t)(rect.y * s_frame.ky);
        (*cut).dw = (int32_t)wantW;
        (*cut).dh = (int32_t)wantH;
    }

    if (rendered > 0) {
        EndCommandBuffer_fn(s_sceneBuffer);
        // Submit production independently: no swapchain contact, therefore
        // no semaphores — just the batch fence the next frame polls.
        VkSubmitInfo ssi = { .sType = VK_STRUCTURE_TYPE_SUBMIT_INFO };
        ssi.commandBufferCount = 1;
        ssi.pCommandBuffers = &s_sceneBuffer;
        ResetFences_fn(s_device, 1, &s_sceneFence);
        QueueSubmit_fn(s_queue, 1, &ssi, s_sceneFence);
        for (uint32_t i = 0; i < s_sceneBatchCount; i++)
            VkSceneCanvas_markSubmitted(s_sceneBatch[i].canvas, sceneNowNs);
        if (s_trace)
            fprintf(stderr, "vk:trace:   scene tick: %u rendered, batch=%u\n",
                    rendered, s_sceneBatchCount);
    }

    // --- 1. prep the drawable: park in board color, stamp scene canvases --
    // Acquired images arrive layout-undefined; walk to TRANSFER_DST, clear
    // unconditionally (the pass below LOADs, so first-frame garbage dies
    // here whether or not scenes exist), then paste each scene canvas as a
    // pixel-exact cut clipped to the drawable bounds.
    VkImageMemoryBarrier toPrep = { .sType = VK_STRUCTURE_TYPE_IMAGE_MEMORY_BARRIER };
    toPrep.srcAccessMask = 0;
    toPrep.dstAccessMask = VK_ACCESS_TRANSFER_WRITE_BIT;
    toPrep.oldLayout = VK_IMAGE_LAYOUT_UNDEFINED;
    toPrep.newLayout = VK_IMAGE_LAYOUT_TRANSFER_DST_OPTIMAL;
    toPrep.image = s_swapchainImages[imageIndex];
    toPrep.subresourceRange.aspectMask = VK_IMAGE_ASPECT_COLOR_BIT;
    toPrep.subresourceRange.levelCount = 1;
    toPrep.subresourceRange.layerCount = 1;
    CmdPipelineBarrier_fn(s_cmdBuffer, VK_PIPELINE_STAGE_TOP_OF_PIPE_BIT,
                          VK_PIPELINE_STAGE_TRANSFER_BIT, 0, 0, nullptr, 0, nullptr, 1, &toPrep);

    {
        VkClearValue cc = {0};
        cc.color.float32[0] = s_frame.bg[0];
        cc.color.float32[1] = s_frame.bg[1];
        cc.color.float32[2] = s_frame.bg[2];
        cc.color.float32[3] = s_frame.bg[3];
        VkImageSubresourceRange rng = {0};
        rng.aspectMask = VK_IMAGE_ASPECT_COLOR_BIT;
        rng.levelCount = 1;
        rng.layerCount = 1;
        CmdClearColorImage_fn(s_cmdBuffer, s_swapchainImages[imageIndex],
                              VK_IMAGE_LAYOUT_TRANSFER_DST_OPTIMAL,
                              &cc.color, 1, &rng);
    }

    if (sceneCount > 0) {
        int32_t drawWi = (int32_t)s_extent.width;
        int32_t drawHi = (int32_t)s_extent.height;
        for (uint32_t si = 0; si < sceneCount; si++) {
            struct SceneCut *cut = &scenes[si];
            VkImage frontImg = VkSceneCanvas_frontImage((*cut).canvas);
            uint32_t srcW = VkSceneCanvas_width((*cut).canvas);
            uint32_t srcH = VkSceneCanvas_height((*cut).canvas);
            bool stretch = false;
            if (frontImg == VK_NULL_HANDLE) {
                // RESIZE BRIDGE: the fresh pair hasn't earned its first flip
                // yet — serve the previous geometry's front STRETCHED into
                // today's cut instead of dropping to board color. Motion
                // beats a hole; one flip later this path goes quiet.
                frontImg = VkSceneCanvas_staleImage((*cut).canvas, &srcW, &srcH);
                stretch = frontImg != VK_NULL_HANDLE;
            }
            if (frontImg == VK_NULL_HANDLE)
                continue; // genuinely nothing rendered yet: board shows
            int32_t cx0 = (*cut).dx < 0 ? 0 : (*cut).dx;
            int32_t cy0 = (*cut).dy < 0 ? 0 : (*cut).dy;
            int32_t cx1 = (*cut).dx + (*cut).dw > drawWi ? drawWi : (*cut).dx + (*cut).dw;
            int32_t cy1 = (*cut).dy + (*cut).dh > drawHi ? drawHi : (*cut).dy + (*cut).dh;
            if (cx0 >= cx1 || cy0 >= cy1)
                continue; // child hangs fully off the window: pure crop

            VkImageBlit sc = {0};
            sc.srcSubresource.aspectMask = VK_IMAGE_ASPECT_COLOR_BIT;
            sc.srcSubresource.layerCount = 1;
            if (stretch) {
                // Whole stale canvas -> whole cut, scaled. No crop math:
                // the bridge is about continuity, not pixel exactness.
                sc.srcOffsets[1].x = (int32_t)srcW;
                sc.srcOffsets[1].y = (int32_t)srcH;
                sc.srcOffsets[1].z = 1;
                sc.dstOffsets[0].x = cx0;
                sc.dstOffsets[0].y = cy0;
                sc.dstOffsets[1].x = cx1;
                sc.dstOffsets[1].y = cy1;
            } else {
                // The cut geometry is THIS tick's request; a deferred resize
                // (retire table full) can leave the live canvas smaller than
                // it. Clamp the crop INSIDE the real canvas and shrink the
                // stamp by the same amount — an out-of-bounds src rect is
                // invalid blit territory, not a visual nit.
                int32_t sx0 = cx0 - (*cut).dx;
                int32_t sy0 = cy0 - (*cut).dy;
                int32_t sx1 = sx0 + (cx1 - cx0);
                int32_t sy1 = sy0 + (cy1 - cy0);
                if ((uint32_t)sx1 > srcW) sx1 = (int32_t)srcW;
                if ((uint32_t)sy1 > srcH) sy1 = (int32_t)srcH;
                if (sx1 <= sx0 || sy1 <= sy0)
                    continue; // canvas smaller than its window position here

                sc.srcOffsets[0].x = sx0;           // crop offset INSIDE the canvas
                sc.srcOffsets[0].y = sy0;
                sc.srcOffsets[1].x = sx1;
                sc.srcOffsets[1].y = sy1;
                sc.srcOffsets[1].z = 1;
                sc.dstOffsets[0].x = cx0;
                sc.dstOffsets[0].y = cy0;
                sc.dstOffsets[1].x = cx0 + (sx1 - sx0);
                sc.dstOffsets[1].y = cy0 + (sy1 - sy0);
            }
            sc.dstSubresource.aspectMask = VK_IMAGE_ASPECT_COLOR_BIT;
            sc.dstSubresource.layerCount = 1;
            // Fresh path: src cut == dst cut, pixel-for-pixel, NEAREST, zero
            // resampling. Bridge path: LINEAR — a stretched stopgap should
            // not alias while it lasts.
            CmdBlitImage_fn(s_cmdBuffer, frontImg,
                            VK_IMAGE_LAYOUT_TRANSFER_SRC_OPTIMAL,
                            s_swapchainImages[imageIndex], VK_IMAGE_LAYOUT_TRANSFER_DST_OPTIMAL,
                            1, &sc, stretch ? VK_FILTER_LINEAR : VK_FILTER_NEAREST);
        }

        // Transfer writes must be visible to the color attachment stage the
        // render pass is about to run on.
        VkImageMemoryBarrier stampsReady = { .sType = VK_STRUCTURE_TYPE_IMAGE_MEMORY_BARRIER };
        stampsReady.srcAccessMask = VK_ACCESS_TRANSFER_WRITE_BIT;
        stampsReady.dstAccessMask = VK_ACCESS_COLOR_ATTACHMENT_READ_BIT
                                  | VK_ACCESS_COLOR_ATTACHMENT_WRITE_BIT;
        stampsReady.oldLayout = VK_IMAGE_LAYOUT_TRANSFER_DST_OPTIMAL;
        stampsReady.newLayout = VK_IMAGE_LAYOUT_TRANSFER_DST_OPTIMAL;
        stampsReady.image = s_swapchainImages[imageIndex];
        stampsReady.subresourceRange = toPrep.subresourceRange;
        CmdPipelineBarrier_fn(s_cmdBuffer, VK_PIPELINE_STAGE_TRANSFER_BIT,
                              VK_PIPELINE_STAGE_COLOR_ATTACHMENT_OUTPUT_BIT,
                              0, 0, nullptr, 0, nullptr, 1, &stampsReady);
    }

    // --- 2. procedural children straight onto the drawable -----------------
    // loadOp=LOAD preserves the scene stamps above; sibling z-order is child
    // order again. Quads draw in plain window-local pixels — no desktop
    // space exists anywhere in this loop.
    {
        VkRenderPassBeginInfo rbi2 = { .sType = VK_STRUCTURE_TYPE_RENDER_PASS_BEGIN_INFO };
        rbi2.renderPass = s_drawablePass;
        rbi2.framebuffer = s_swapchainFbs[imageIndex];
        rbi2.renderArea.extent = s_extent;
        CmdBeginRenderPass_fn(s_cmdBuffer, &rbi2, VK_SUBPASS_CONTENTS_INLINE);
    }

    bool dump = s_trace && (s_frameNo++ % 60 == 0);
    if (dump)
        fprintf(stderr, "vk:trace: %dx%d pts | drawable %ux%u px | k=%.2f\n",
                s_frame.winW, s_frame.winH, s_extent.width, s_extent.height, s_frame.kx);

    if (s_frame.root != nullptr) {
        size_t childCount = Panel_childCount(s_frame.root);
        for (size_t i = 0; i < childCount; i++) {
            Panel *child = Panel_getChild(s_frame.root, i);
            if (!child)
                continue;

            // Resolve against the mirrored basket: plain window-local
            // points, straight onto the drawable's pixel grid.
            Vec4 rect;
            Container_resolve(&(*child).base, 0.0f, 0.0f,
                              (float)s_frame.winW, (float)s_frame.winH, &rect);
            if (rect.z <= 0.0f || rect.w <= 0.0f)
                continue;

            // DOUBLE-RENDER FIX: If native compositing is ON, all UI panels 
            // are already rendered into individual IOSurfaces and composited 
            // by AppKit. Do NOT draw them into the background Vulkan swapchain!
            if (s_frame.nativeContent) {
                uint32_t cType = Memory_type(child);
                if (cType != TYPE_SCENE3D_SINGLETON && cType != TYPE_SCENE2D_SINGLETON && cType != TYPE_SCENE_SINGLETON) {
                    continue; // Skip UI panels; they live in IOSurface land now.
                }
            }

            float drawW = (float)s_extent.width;
            float drawH = (float)s_extent.height;
            float px = rect.x * s_frame.kx;
            float py = rect.y * s_frame.ky;
            float pw = rect.z * s_frame.kx;
            float ph = rect.w * s_frame.ky;
            if (px < 0.0f) { pw += px; px = 0.0f; }
            if (py < 0.0f) { ph += py; py = 0.0f; }
            if (pw <= 0.0f || ph <= 0.0f) continue;
            if (px + pw > drawW) pw = drawW - px;
            if (py + ph > drawH) ph = drawH - py;
            if (pw <= 0.0f || ph <= 0.0f) continue;

            // VIEWPORT/SCISSOR SEPARATION LAW lives inside Vk_fillRect now:
            // u_rectNdc places each quad in clip space against a full-drawable
            // viewport; scissoring is per-fill. Handlers get the same law.

            uint32_t childType = Memory_type(child);
            if (dump)
                fprintf(stderr, "vk:trace:   child[%zu] type=0x%04x rect=(%.0f,%.0f %.0fx%.0f)px\n",
                        i, childType, px, py, pw, ph);

            if (childType == TYPE_SCENE3D_SINGLETON || childType == TYPE_SCENE2D_SINGLETON
                || childType == TYPE_SCENE_SINGLETON) {
                // Scene child: already rendered into its canvas pre-pass;
                // stamped onto the cache as a cut below.
                continue;
            }

            // METHOD-SLOT DISPATCH ("@Override"): a set renderHandler replaces
            // the default draw entirely — it receives an open render pass on
            // this drawable, the clipped pixel rect, and the command buffer.
            // nullptr falls through to the built-in solid quad.
            Panel_RenderFn handler = Panel_getRenderHandler(child);
            if (handler != nullptr) {
                handler(child, nullptr, s_cmdBuffer, px, py, pw, ph);
            } else {
                uint32_t color = Panel_getBackgroundColor(child);
                if (color == 0)
                    continue; // PANEL_COLOR_CLEAR draws nothing without blending
                float rgba[4];
                decodeColor(color, rgba);
                Vk_fillRect(s_cmdBuffer, drawW, drawH, px, py, pw, ph,
                            rgba[0], rgba[1], rgba[2], rgba[3]);
            }
        }
    }

    // Drawable complete; the pass leaves it in TRANSFER_SRC (finalLayout),
    // one honest step short of PRESENT so the dump can read in between.
    CmdEndRenderPass_fn(s_cmdBuffer);

    // Availability hand-off: color writes must be visible to the transfers
    // that follow (dump copy, and layout walk to present).
    VkImageMemoryBarrier drawDone = { .sType = VK_STRUCTURE_TYPE_IMAGE_MEMORY_BARRIER };
    drawDone.srcAccessMask = VK_ACCESS_COLOR_ATTACHMENT_WRITE_BIT;
    drawDone.dstAccessMask = VK_ACCESS_TRANSFER_READ_BIT;
    drawDone.oldLayout = VK_IMAGE_LAYOUT_TRANSFER_SRC_OPTIMAL;
    drawDone.newLayout = VK_IMAGE_LAYOUT_TRANSFER_SRC_OPTIMAL;
    drawDone.image = s_swapchainImages[imageIndex];
    drawDone.subresourceRange = toPrep.subresourceRange;
    CmdPipelineBarrier_fn(s_cmdBuffer, VK_PIPELINE_STAGE_COLOR_ATTACHMENT_OUTPUT_BIT,
                          VK_PIPELINE_STAGE_TRANSFER_BIT, 0, 0, nullptr, 0, nullptr, 1, &drawDone);

    if (s_dumpEnabled && s_dumpStage == 0) {
        if (dumpAllocStage(s_extent.width, s_extent.height)) {
            dumpRecordCopy(s_swapchainImages[imageIndex], s_cmdBuffer);
            s_dumpStage = 1;
        } else {
            s_dumpStage = 2;
        }
    }

    // Walk TRANSFER_SRC -> PRESENT_SRC. The desktop indirection is gone:
    // there is no window blit, no intersection law, nothing left to glue —
    // the drawable has been the only canvas since step 1.
    VkImageMemoryBarrier toPresent = { .sType = VK_STRUCTURE_TYPE_IMAGE_MEMORY_BARRIER };
    toPresent.srcAccessMask = VK_ACCESS_TRANSFER_READ_BIT;
    toPresent.dstAccessMask = 0;
    toPresent.oldLayout = VK_IMAGE_LAYOUT_TRANSFER_SRC_OPTIMAL;
    toPresent.newLayout = VK_IMAGE_LAYOUT_PRESENT_SRC_KHR;
    toPresent.image = s_swapchainImages[imageIndex];
    toPresent.subresourceRange = toPrep.subresourceRange;
    CmdPipelineBarrier_fn(s_cmdBuffer, VK_PIPELINE_STAGE_TRANSFER_BIT,
                          VK_PIPELINE_STAGE_BOTTOM_OF_PIPE_BIT, 0, 0, nullptr, 0, nullptr, 1, &toPresent);

    EndCommandBuffer_fn(s_cmdBuffer);

    VkPipelineStageFlags waitStage = VK_PIPELINE_STAGE_TRANSFER_BIT;
    VkSubmitInfo si = { .sType = VK_STRUCTURE_TYPE_SUBMIT_INFO };
    si.waitSemaphoreCount = 1;
    si.pWaitSemaphores = &s_semAcquire;
    si.pWaitDstStageMask = &waitStage;
    si.commandBufferCount = 1;
    si.pCommandBuffers = &s_cmdBuffer;
    si.signalSemaphoreCount = 1;
    si.pSignalSemaphores = &s_semRender;
    // Arm the fence for THIS frame at the last possible moment: every bail
    // path above has already returned with the fence still signaled.
    ResetFences_fn(s_device, 1, &s_fence);
    QueueSubmit_fn(s_queue, 1, &si, s_fence);

    VkPresentInfoKHR pi = { .sType = VK_STRUCTURE_TYPE_PRESENT_INFO_KHR };
    pi.waitSemaphoreCount = 1;
    pi.pWaitSemaphores = &s_semRender;
    pi.swapchainCount = 1;
    pi.pSwapchains = &s_swapchain;
    pi.pImageIndices = &imageIndex;
    VkResult pr = QueuePresentKHR_fn(s_queue, &pi);
    if (pr == VK_ERROR_OUT_OF_DATE_KHR || pr == VK_SUBOPTIMAL_KHR) {
        if (pr == VK_ERROR_OUT_OF_DATE_KHR) {
            // Present hit a dead chain (resize landed between acquire and
            // present). Force next tick through rebuildTargets even if the
            // extent check would pass — otherwise a chain killed at the END
            // of a resize never recovers: dead screen until the next resize.
            s_appliedRenderGen = 0;
        }
        return pr == VK_SUBOPTIMAL_KHR;
    }
    return pr == VK_SUCCESS;
}

void Vk_drawTexture(void *cmdBuffer, float surfaceW, float surfaceH,
                    float x, float y, float w, float h,
                    float r, float g, float b, float a,
                    int32_t textureId,
                    PictureMode mode,
                    float imgW, float imgH) {
    if (!cmdBuffer) return;

    // Lazily build the texture pipeline on first call — must use the IOSurface
    // renderpass (BGRA8), which is itself lazy and not available inside buildPipelines().
    if (s_texPipeline == VK_NULL_HANDLE) {
        extern bool VkMac_ensureIOSurfacePass(void);
        extern VkRenderPass VkMac_getIOSurfacePass(void);
        if (!VkMac_ensureIOSurfacePass()) return;
        VkRenderPass iosurfPass = VkMac_getIOSurfacePass();

        VK_LOAD_DEVICE_VOID(CreatePipelineLayout)
        VK_LOAD_DEVICE_VOID(CreateGraphicsPipelines)

        // Vertex: offset=0 size=16 (rectNdc)
        // Fragment: offset=16 size=48 (color[16] + texId[4] + pad[4] + imgSize[8] + quadSize[8] + mode[4] + fillParam[4])
        VkPushConstantRange texPush[2];
        texPush[0].stageFlags = VK_SHADER_STAGE_VERTEX_BIT;
        texPush[0].offset = 0;
        texPush[0].size = 16;
        texPush[1].stageFlags = VK_SHADER_STAGE_FRAGMENT_BIT;
        texPush[1].offset = 16;
        texPush[1].size = 48;

        extern void *Texture_getDescriptorSetLayout(void);
        VkDescriptorSetLayout bindlessLayout = (VkDescriptorSetLayout)Texture_getDescriptorSetLayout();

        VkPipelineLayoutCreateInfo tlci = { .sType = VK_STRUCTURE_TYPE_PIPELINE_LAYOUT_CREATE_INFO };
        tlci.pushConstantRangeCount = 2;
        tlci.pPushConstantRanges = texPush;
        tlci.setLayoutCount = 1;
        tlci.pSetLayouts = &bindlessLayout;
        if (CreatePipelineLayout_fn(s_device, &tlci, nullptr, &s_texLayout) != VK_SUCCESS) return;

        VkShaderModule texVert = createShaderModule("texture_quad_vert.spv", nullptr);
        VkShaderModule texFrag = createShaderModule("texture_quad_frag.spv", nullptr);
        if (texVert == VK_NULL_HANDLE || texFrag == VK_NULL_HANDLE) return;

        // Re-use the same fixed-function skeleton as the quad pipeline
        VkPipelineVertexInputStateCreateInfo vi = { .sType = VK_STRUCTURE_TYPE_PIPELINE_VERTEX_INPUT_STATE_CREATE_INFO };
        VkPipelineInputAssemblyStateCreateInfo ia = { .sType = VK_STRUCTURE_TYPE_PIPELINE_INPUT_ASSEMBLY_STATE_CREATE_INFO };
        ia.topology = VK_PRIMITIVE_TOPOLOGY_TRIANGLE_LIST;
        VkPipelineViewportStateCreateInfo vp = { .sType = VK_STRUCTURE_TYPE_PIPELINE_VIEWPORT_STATE_CREATE_INFO };
        vp.viewportCount = 1; vp.scissorCount = 1;
        VkPipelineRasterizationStateCreateInfo rs = { .sType = VK_STRUCTURE_TYPE_PIPELINE_RASTERIZATION_STATE_CREATE_INFO };
        rs.lineWidth = 1.0f; rs.cullMode = VK_CULL_MODE_NONE; rs.frontFace = VK_FRONT_FACE_CLOCKWISE;
        VkPipelineMultisampleStateCreateInfo ms = { .sType = VK_STRUCTURE_TYPE_PIPELINE_MULTISAMPLE_STATE_CREATE_INFO };
        ms.rasterizationSamples = VK_SAMPLE_COUNT_1_BIT;
        VkPipelineColorBlendAttachmentState blendAtt = {0};
        blendAtt.colorWriteMask = 0xF;
        blendAtt.blendEnable = VK_TRUE;
        blendAtt.srcColorBlendFactor = VK_BLEND_FACTOR_SRC_ALPHA;
        blendAtt.dstColorBlendFactor = VK_BLEND_FACTOR_ONE_MINUS_SRC_ALPHA;
        blendAtt.colorBlendOp = VK_BLEND_OP_ADD;
        blendAtt.srcAlphaBlendFactor = VK_BLEND_FACTOR_ONE;
        blendAtt.dstAlphaBlendFactor = VK_BLEND_FACTOR_ONE_MINUS_SRC_ALPHA;
        blendAtt.alphaBlendOp = VK_BLEND_OP_ADD;
        VkPipelineColorBlendStateCreateInfo cb2 = { .sType = VK_STRUCTURE_TYPE_PIPELINE_COLOR_BLEND_STATE_CREATE_INFO };
        cb2.attachmentCount = 1; cb2.pAttachments = &blendAtt;
        VkDynamicState dynStates[2] = { VK_DYNAMIC_STATE_VIEWPORT, VK_DYNAMIC_STATE_SCISSOR };
        VkPipelineDynamicStateCreateInfo ds = { .sType = VK_STRUCTURE_TYPE_PIPELINE_DYNAMIC_STATE_CREATE_INFO };
        ds.dynamicStateCount = 2; ds.pDynamicStates = dynStates;

        VkPipelineShaderStageCreateInfo tstages[2] = {{0},{0}};
        tstages[0].sType = VK_STRUCTURE_TYPE_PIPELINE_SHADER_STAGE_CREATE_INFO;
        tstages[0].stage = VK_SHADER_STAGE_VERTEX_BIT; tstages[0].module = texVert; tstages[0].pName = "main";
        tstages[1].sType = VK_STRUCTURE_TYPE_PIPELINE_SHADER_STAGE_CREATE_INFO;
        tstages[1].stage = VK_SHADER_STAGE_FRAGMENT_BIT; tstages[1].module = texFrag; tstages[1].pName = "main";

        VkGraphicsPipelineCreateInfo gpci = { .sType = VK_STRUCTURE_TYPE_GRAPHICS_PIPELINE_CREATE_INFO };
        gpci.stageCount = 2; gpci.pStages = tstages;
        gpci.pVertexInputState = &vi; gpci.pInputAssemblyState = &ia;
        gpci.pViewportState = &vp; gpci.pRasterizationState = &rs;
        gpci.pMultisampleState = &ms; gpci.pColorBlendState = &cb2;
        gpci.pDynamicState = &ds;
        gpci.layout = s_texLayout;
        gpci.renderPass = iosurfPass;  // BGRA8, matches IOSurface panels
        gpci.subpass = 0;

        if (CreateGraphicsPipelines_fn(s_device, VK_NULL_HANDLE, 1, &gpci, nullptr, &s_texPipeline) != VK_SUCCESS) {
            s_texPipeline = VK_NULL_HANDLE;
            return;
        }
        printf("vk: texture pipeline built (IOSurface pass)\n");
    }

    VkCommandBuffer cb = (VkCommandBuffer)cmdBuffer;

    VK_LOAD_DEVICE_VOID(CmdBindPipeline)
    VK_LOAD_DEVICE_VOID(CmdPushConstants)
    VK_LOAD_DEVICE_VOID(CmdDraw)
    VK_LOAD_DEVICE_VOID(CmdBindDescriptorSets)
    VK_LOAD_DEVICE_VOID(CmdSetViewport)
    VK_LOAD_DEVICE_VOID(CmdSetScissor)

    CmdBindPipeline_fn(cb, VK_PIPELINE_BIND_POINT_GRAPHICS, s_texPipeline);

    VkViewport viewport = { .x = 0.0f, .y = surfaceH, .width = surfaceW, .height = -surfaceH, .maxDepth = 1.0f };
    VkRect2D scissor = { .offset.x = (int32_t)x, .offset.y = (int32_t)(surfaceH - y - h),
                         .extent.width = (uint32_t)w, .extent.height = (uint32_t)h };
    CmdSetViewport_fn(cb, 0, 1, &viewport);
    CmdSetScissor_fn(cb, 0, 1, &scissor);

    extern void *Texture_getDescriptorSet(void);
    VkDescriptorSet bindlessSet = (VkDescriptorSet)Texture_getDescriptorSet();
    CmdBindDescriptorSets_fn(cb, VK_PIPELINE_BIND_POINT_GRAPHICS, s_texLayout, 0, 1, &bindlessSet, 0, nullptr);

    // Push constant layout (must match texture_quad.frag):
    //   [0..15]  vec4  rectNdc        (vertex)
    if (bindlessSet != VK_NULL_HANDLE && CmdBindDescriptorSets_fn) {
        CmdBindDescriptorSets_fn(cb, VK_PIPELINE_BIND_POINT_GRAPHICS, s_texLayout,
                                 0, 1, &bindlessSet, 0, nullptr);
    }

    if (CmdSetViewport_fn) {
        VkViewport vp = { .x = 0.0f, .y = surfaceH, .width = surfaceW, .height = -surfaceH, .maxDepth = 1.0f };
        CmdSetViewport_fn(cb, 0, 1, &vp);
    }
    if (CmdSetScissor_fn) {
        VkRect2D sc = { .offset.x = (int32_t)x, .offset.y = (int32_t)(surfaceH - y - h),
                        .extent.width = (uint32_t)w, .extent.height = (uint32_t)h };
        CmdSetScissor_fn(cb, 0, 1, &sc);
    }

    if (!CmdPushConstants_fn || !CmdDraw_fn) return;

    struct {
        float    rect[4];       // offset 0
        float    color[4];      // offset 16
        uint32_t texId;         // offset 32
        uint32_t pad;           // offset 36
        float    imgSize[2];    // offset 40
        float    quadSize[2];   // offset 48
        uint32_t mode;          // offset 56
    } push;

    push.rect[0] = (x / surfaceW) * 2.0f - 1.0f;
    push.rect[1] = (y / surfaceH) * 2.0f - 1.0f;
    push.rect[2] = (w / surfaceW) * 2.0f;
    push.rect[3] = (h / surfaceH) * 2.0f;
    push.color[0] = r; push.color[1] = g; push.color[2] = b; push.color[3] = a;
    push.texId      = (uint32_t)textureId;
    push.pad        = 0;
    push.imgSize[0] = imgW;
    push.imgSize[1] = imgH;
    push.quadSize[0] = w;
    push.quadSize[1] = h;
    push.mode       = (uint32_t)mode;

    CmdPushConstants_fn(cb, s_texLayout, VK_SHADER_STAGE_VERTEX_BIT,   0,  16, push.rect);
    CmdPushConstants_fn(cb, s_texLayout, VK_SHADER_STAGE_FRAGMENT_BIT, 16, 44, push.color);

    CmdDraw_fn(cb, 6, 1, 0, 0);
}
