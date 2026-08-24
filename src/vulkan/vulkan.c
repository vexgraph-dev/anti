#define VK_USE_PLATFORM_MACOS_MVK
#define VK_USE_PLATFORM_METAL_EXT

#include "vulkan/vk.h"
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
#include "vulkan/vk_view.h"
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

static void *s_lib = NULL;
static PFN_vkGetInstanceProcAddr s_gpa = NULL;
static PFN_vkGetDeviceProcAddr s_gdpa = NULL;
static char s_status[64] = "not started";
#define VK_MARK(msg) do { snprintf(s_status, sizeof(s_status), msg); fprintf(stderr, "vk: %s\n", s_status); } while(0)

// Swapchain: rebuilt whenever the surface outgrows the chain (fullscreen,
// resize) or render policy drifts. No framebuffers live on it — the blit
// writes its images directly.
static bool rebuildTargets(void);
static void destroyTargets(void);
static bool buildPipelines(void);

// Last-applied Window_renderGeneration. A drift means presentMode or
// transparent changed on thread 0 and the swapchain wants a rebuild.
static uint64_t s_appliedRenderGen = 0;

// Last-applied Window_sizeGeneration for the container mirror. The basket
// panel's w/h are rewritten to the window's content size whenever this
// drifts — the "root mirrors its window" law.
static uint64_t s_mirroredSizeGen = UINT64_MAX;

// Animation clock anchor for scene children (u_time seconds since init).
static uint64_t s_animStartNanos = 0;

static VkInstance s_instance;
static VkSurfaceKHR s_surface;
static VkPhysicalDevice s_phys;
static uint32_t s_queueFamily = 0;
static VkDevice s_device;
static VkQueue s_queue;
static VkSwapchainKHR s_swapchain;
static VkCommandPool s_cmdPool;
static VkCommandBuffer s_cmdBuffer;
static VkSemaphore s_semAcquire;
static VkSemaphore s_semRender;
static VkFence s_fence;

static VkFormat s_format;
static VkExtent2D s_extent;
static Window *s_window = NULL;

// Raw swapchain images — the blit writes these directly, no views needed.
static VkImage s_swapchainImages[8];
static uint32_t s_swapchainImageCount = 0;

// Child pipelines, built against the monitor view's cache renderpass:
//   triangle — legacy hello-triangle scene content (push: f32 u_time @0, VS)
//   quad     — solid panel fill (push: vec4 rectNdc @0 VS, vec4 color @16 FS)
static VkPipelineLayout s_triLayout;
static VkPipeline s_triPipeline;
static VkPipelineLayout s_quadLayout;
static VkPipeline s_quadPipeline;
static bool s_pipelinesBuilt = false;

static void *s_libLoad(void) {
    // MoltenVK first: the ICD exports everything itself, no loader manifest
    // needed. The Khronos loader stays as fallback for manifest setups.
    const char *candidates[] = {
        "libMoltenVK.dylib",
        "/opt/homebrew/lib/libMoltenVK.dylib",
        "/usr/local/lib/libMoltenVK.dylib",
        "libvulkan.dylib",
        "/opt/homebrew/lib/libvulkan.dylib",
        "/usr/local/lib/libvulkan.dylib",
        NULL,
    };
    for (int i = 0; candidates[i]; i++) {
        void *lib = dlopen(candidates[i], RTLD_NOW | RTLD_LOCAL);
        if (lib)
            return lib;
    }
    return NULL;
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
    name##_fn = s_gdpa ? (PFN_vk##name)s_gdpa(s_device, "vk" #name) : (PFN_vk##name)s_gpa(s_instance, "vk" #name); \
    if (!name##_fn) { snprintf(s_status, sizeof(s_status), "missing vk" #name); fprintf(stderr, "vk: missing vk%s\n", #name); return false; }

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
    VK_LOAD_GLOBAL(EnumerateInstanceExtensionProperties)
    uint32_t extCount = 0;
    EnumerateInstanceExtensionProperties_fn(NULL, &extCount, NULL);

    static char names[64][VK_MAX_EXTENSION_NAME_SIZE];
    VkExtensionProperties props[64];
    if (extCount > 64)
        extCount = 64;
    EnumerateInstanceExtensionProperties_fn(NULL, &extCount, props);
    for (uint32_t i = 0; i < extCount; i++) {
        snprintf(names[i], VK_MAX_EXTENSION_NAME_SIZE, "%s", props[i].extensionName);
    }

    int surfaceExt = 0;
    const char *exts[2];
    uint32_t n = 0;
    for (uint32_t i = 0; i < extCount; i++) {
        if (strcmp(names[i], "VK_KHR_surface") == 0)
            exts[n++] = "VK_KHR_surface";
        else if (strcmp(names[i], "VK_MVK_macos_surface") == 0) {
            surfaceExt = 1;
            exts[n++] = "VK_MVK_macos_surface";
        }
    }
    if (n < 2 || surfaceExt == 0) {
        snprintf(s_status, sizeof(s_status), "no surface ext (%u seen)", (unsigned)extCount);
        return false;
    }

    VkApplicationInfo app = { .sType = VK_STRUCTURE_TYPE_APPLICATION_INFO };
    app.pApplicationName = "anti";
    app.apiVersion = VK_API_VERSION_1_1;

    VkInstanceCreateInfo ici = { .sType = VK_STRUCTURE_TYPE_INSTANCE_CREATE_INFO };
    ici.pApplicationInfo = &app;
    ici.enabledExtensionCount = n;
    ici.ppEnabledExtensionNames = exts;

    if (CreateInstance_fn(&ici, NULL, &s_instance) != VK_SUCCESS) {
        VkResult r = CreateInstance_fn(&ici, NULL, &s_instance);
        snprintf(s_status, sizeof(s_status), "instance failed r=%d", r); fprintf(stderr, "vk: %s\n", s_status);
        return false;
    }

    s_gdpa = (PFN_vkGetDeviceProcAddr)s_gpa(s_instance, "vkGetDeviceProcAddr");

    // 3. surface over the window's AppKit view (MoltenVK wraps it in metal)
    VK_LOAD_INSTANCE(CreateMacOSSurfaceMVK)
    Window_metalLayer(window); // install CAMetalLayer first — MVK refuses bare views
    VkMacOSSurfaceCreateInfoMVK sci = { .sType = VK_STRUCTURE_TYPE_MACOS_SURFACE_CREATE_INFO_MVK };
    sci.pView = Window_contentView(window);
    VkResult sr = CreateMacOSSurfaceMVK_fn(s_instance, &sci, NULL, &s_surface);
    if (!sci.pView || sr != VK_SUCCESS) {
        snprintf(s_status, sizeof(s_status), "surface failed r=%d", sr); fprintf(stderr, "vk: %s\n", s_status);
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
    if (EnumeratePhysicalDevices_fn(s_instance, &physCount, NULL) != VK_SUCCESS || physCount == 0) {
        snprintf(s_status, sizeof(s_status), "no physical devices");
        return false;
    }
    VkPhysicalDevice phys[8];
    if (physCount > 8)
        physCount = 8;
    EnumeratePhysicalDevices_fn(s_instance, &physCount, phys);
    s_phys = phys[0]; // MoltenVK exposes exactly one

    uint32_t familyCount = 0;
    GetPhysicalDeviceQueueFamilyProperties_fn(s_phys, &familyCount, NULL);
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

    const char *devExts[] = { "VK_KHR_swapchain" };
    VkDeviceCreateInfo dci = { .sType = VK_STRUCTURE_TYPE_DEVICE_CREATE_INFO };
    dci.queueCreateInfoCount = 1;
    dci.pQueueCreateInfos = &qci;
    dci.enabledExtensionCount = 1;
    dci.ppEnabledExtensionNames = devExts;

    if (CreateDevice_fn(s_phys, &dci, NULL, &s_device) != VK_SUCCESS) {
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
    if (!buildPipelines())
        return false;

    VK_LOAD_DEVICE(CreateSemaphore)
    VK_LOAD_DEVICE(CreateFence)
    VK_LOAD_DEVICE(WaitForFences)
    VK_LOAD_DEVICE(ResetFences)

    VkSemaphoreCreateInfo sci2 = { .sType = VK_STRUCTURE_TYPE_SEMAPHORE_CREATE_INFO };
    if (CreateSemaphore_fn && CreateSemaphore_fn(s_device, &sci2, NULL, &s_semAcquire) != VK_SUCCESS) return false;
    if (CreateSemaphore_fn && CreateSemaphore_fn(s_device, &sci2, NULL, &s_semRender) != VK_SUCCESS) return false;

    VkFenceCreateInfo fci2 = { .sType = VK_STRUCTURE_TYPE_FENCE_CREATE_INFO };
    fci2.flags = VK_FENCE_CREATE_SIGNALED_BIT;
    if (CreateFence_fn && CreateFence_fn(s_device, &fci2, NULL, &s_fence) != VK_SUCCESS) return false;

    s_animStartNanos = NanoTime_now();
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
    GetPhysicalDeviceSurfaceFormatsKHR_fn(s_phys, s_surface, &formatCount, NULL);
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
    if (CreateSwapchainKHR_fn(s_device, &swci, NULL, &newSwapchain) != VK_SUCCESS) {
        snprintf(s_status, sizeof(s_status), "swapchain failed");
        return false;
    }

    Window_setGravityTopLeft(s_window);

    // The old chain dies after the new one exists (oldSwapchain retirement).
    if (oldSwapchain != VK_NULL_HANDLE)
        DestroySwapchainKHR_fn(s_device, oldSwapchain, NULL);
    s_swapchain = newSwapchain;

    // Fetch the raw image handles for the blit path.
    s_swapchainImageCount = 0;
    GetSwapchainImagesKHR_fn(s_device, s_swapchain, &s_swapchainImageCount, NULL);
    if (s_swapchainImageCount > 8)
        s_swapchainImageCount = 8;
    GetSwapchainImagesKHR_fn(s_device, s_swapchain, &s_swapchainImageCount, s_swapchainImages);

    fprintf(stderr, "vk: swapchain live %ux%u fmt=%d present=%d\n", s_extent.width, s_extent.height, (int)s_format,
            (int)(swci.presentMode == VK_PRESENT_MODE_IMMEDIATE_KHR));
    s_appliedRenderGen = Window_renderGeneration(s_window);
    return true;
}

static void destroyTargets(void) {
    if (s_device == VK_NULL_HANDLE)
        return;
    VK_LOAD_DEVICE_VOID(DeviceWaitIdle)
    VK_LOAD_DEVICE_VOID(DestroySwapchainKHR)

    if (DeviceWaitIdle_fn)
        DeviceWaitIdle_fn(s_device);
    if (s_swapchain != VK_NULL_HANDLE && DestroySwapchainKHR_fn)
        DestroySwapchainKHR_fn(s_device, s_swapchain, NULL);
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
        s_pipelinesBuilt = false;
    }
    if (s_instance != VK_NULL_HANDLE) {
        VK_LOAD_INSTANCE_VOID(DestroySurfaceKHR)
        if (DestroySurfaceKHR_fn)
            DestroySurfaceKHR_fn(s_instance, s_surface, NULL);
        VK_LOAD_INSTANCE_VOID(DestroyInstance)
        if (DestroyInstance_fn)
            DestroyInstance_fn(s_instance, NULL);
    }
    if (s_lib) {
        dlclose(s_lib);
        s_lib = NULL;
    }
    s_device = VK_NULL_HANDLE;
    s_swapchain = VK_NULL_HANDLE;
    s_instance = VK_NULL_HANDLE;
    s_surface = VK_NULL_HANDLE;
    s_gpa = NULL;
    s_gdpa = NULL;
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
    return NULL;
}


static unsigned char *loadSpv(const char *path, size_t *outSize) {
    FILE *f = fopen(path, "rb");
    if (!f)
        return NULL;
    fseek(f, 0, SEEK_END);
    long size = ftell(f);
    fseek(f, 0, SEEK_SET);
    if (size <= 0 || size % 4 != 0) {
        fclose(f);
        return NULL;
    }
    unsigned char *bytes = (unsigned char *)malloc((size_t)size);
    if (!bytes) {
        fclose(f);
        return NULL;
    }
    if (fread(bytes, 1, (size_t)size, f) != (size_t)size) {
        free(bytes);
        fclose(f);
        return NULL;
    }
    fclose(f);
    *outSize = (size_t)size;
    return bytes;
}

static VkShaderModule createShaderModule(const char *name, const char *unused) {
    (void)unused;
    VK_LOAD_DEVICE(CreateShaderModule)
    size_t size = 0;
    unsigned char *code = loadSpvAny(name, &size);
    if (!code)
        return VK_NULL_HANDLE;

    VkShaderModuleCreateInfo ci = { .sType = VK_STRUCTURE_TYPE_SHADER_MODULE_CREATE_INFO };
    ci.codeSize = size;
    ci.pCode = (const uint32_t *)code;

    VkShaderModule module = VK_NULL_HANDLE;
    if (CreateShaderModule_fn(s_device, &ci, NULL, &module) != VK_SUCCESS) {
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

// Build both child pipelines against the monitor view's cache renderpass,
// plus the command pool and primary buffer that record each present loop.
// Runs once at init — no more lazy first-frame building, no retry leaks.
static bool buildPipelines(void) {
    VK_LOAD_DEVICE(CreatePipelineLayout)
    VK_LOAD_DEVICE(CreateGraphicsPipelines)
    VK_LOAD_DEVICE(CreateCommandPool)
    VK_LOAD_DEVICE(AllocateCommandBuffers)

    // Renderpass compatibility: every view shares format B8G8R8A8 + one
    // subpass, so pass[0] works for all of them.
    VkView *view0 = VkView_at(0);
    if (!view0 || VkView_renderPass(view0) == VK_NULL_HANDLE) {
        snprintf(s_status, sizeof(s_status), "no view renderpass");
        return false;
    }
    VkRenderPass pass = VkView_renderPass(view0);

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
    VkShaderModule triVert = createShaderModule("hello_triangle_vert.spv", NULL);
    VkShaderModule triFrag = createShaderModule("hello_triangle_frag.spv", NULL);
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
    if (CreatePipelineLayout_fn(s_device, &tlci, NULL, &s_triLayout) != VK_SUCCESS) {
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
    if (CreateGraphicsPipelines_fn(s_device, VK_NULL_HANDLE, 1, &tpci, NULL, &s_triPipeline) != VK_SUCCESS) {
        snprintf(s_status, sizeof(s_status), "tri pipeline failed");
        return false;
    }

    // --- quad child (solid panel fill) ------------------------------------
    VkShaderModule quadVert = createShaderModule("solid_quad_vert.spv", NULL);
    VkShaderModule quadFrag = createShaderModule("solid_quad_frag.spv", NULL);
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
    if (CreatePipelineLayout_fn(s_device, &qlci, NULL, &s_quadLayout) != VK_SUCCESS) {
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
    if (CreateGraphicsPipelines_fn(s_device, VK_NULL_HANDLE, 1, &qpci, NULL, &s_quadPipeline) != VK_SUCCESS) {
        snprintf(s_status, sizeof(s_status), "quad pipeline failed");
        return false;
    }

    // --- command plumbing --------------------------------------------------
    VkCommandPoolCreateInfo cpci = { .sType = VK_STRUCTURE_TYPE_COMMAND_POOL_CREATE_INFO };
    cpci.flags = VK_COMMAND_POOL_CREATE_RESET_COMMAND_BUFFER_BIT;
    cpci.queueFamilyIndex = s_queueFamily;
    if (CreateCommandPool_fn(s_device, &cpci, NULL, &s_cmdPool) != VK_SUCCESS) {
        snprintf(s_status, sizeof(s_status), "cmdpool failed");
        return false;
    }
    VkCommandBufferAllocateInfo cbai = { .sType = VK_STRUCTURE_TYPE_COMMAND_BUFFER_ALLOCATE_INFO };
    cbai.commandPool = s_cmdPool;
    cbai.level = VK_COMMAND_BUFFER_LEVEL_PRIMARY;
    cbai.commandBufferCount = 1;
    AllocateCommandBuffers_fn(s_device, &cbai, &s_cmdBuffer);

    s_pipelinesBuilt = true;
    return true;
}

bool Vk_clearPresent(void) {
    if (!Vk_ready() || !s_pipelinesBuilt) return false;

    // Retire the PREVIOUS frame through its fence BEFORE touching the chain.
    // This is what lets extent-driven rebuilds happen every drag tick without
    // stalling: no DeviceWaitIdle exists anywhere in this path.
    VK_LOAD_DEVICE(WaitForFences)
    VK_LOAD_DEVICE(ResetFences)
    VK_LOAD_DEVICE(ResetCommandBuffer)
    WaitForFences_fn(s_device, 1, &s_fence, VK_TRUE, UINT64_MAX);
    ResetFences_fn(s_device, 1, &s_fence);
    ResetCommandBuffer_fn(s_cmdBuffer, 0);

    // Policy drift (presentMode / transparent changed) wants a fresh chain.
    uint64_t renderGen = Window_renderGeneration(s_window);
    if (renderGen != s_appliedRenderGen && !rebuildTargets()) return false;

    VK_LOAD_INSTANCE(GetPhysicalDeviceSurfaceCapabilitiesKHR)
    VkSurfaceCapabilitiesKHR live;
    memset(&live, 0, sizeof(live));
    if (GetPhysicalDeviceSurfaceCapabilitiesKHR_fn(s_phys, s_surface, &live) == VK_SUCCESS
        && (live.currentExtent.width != s_extent.width || live.currentExtent.height != s_extent.height)) {
        fprintf(stderr, "vk: extent moved %ux%u -> %ux%u; rebuilding\n",
                s_extent.width, s_extent.height,
                live.currentExtent.width, live.currentExtent.height);
        if (!rebuildTargets()) return false;
    }

    // Window geometry: CONTENT top-left (below the title bar) + content
    // size, in desktop points. Children live in this space; the blit source
    // must start here too, or everything shifts by the chrome height.
    int winX = 0, winY = 0;
    int winW = s_window ? Window_width(s_window) : 0;
    int winH = s_window ? Window_height(s_window) : 0;
    Window_getContentOrigin(s_window, &winX, &winY);
    if (winW <= 0 || winH <= 0)
        return false;

    // The monitor this window lives on owns the cache it blits from. Join by
    // the window's mirrored display id first; coordinate sniffing and view 0
    // are fallbacks for a stale discovery list (fresh hotplug) or an
    // unmapped window.
    VkView *view = VkView_forMonitor(Window_getMonitorId(s_window));
    if (!view)
        view = VkView_forPoint((float)winX, (float)winY);
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
    float kx = cacheW / pointW; // cache px per desktop point
    float ky = cacheH / pointH;

    // Basket mirror: rewrite its w/h to the window's content size on drift.
    Panel *root = Window_getContainer(s_window);
    uint64_t sizeGen = Window_sizeGeneration(s_window);
    if (root != NULL && sizeGen != s_mirroredSizeGen) {
        s_mirroredSizeGen = sizeGen;
        Container_setSize(&(*root).base, (float)winW, (float)winH);
    }

    // Clear law: the basket's own color IS the board's color. No container,
    // or PANEL_COLOR_CLEAR, means transparent across the whole cache.
    float bg[4] = { 0.0f, 0.0f, 0.0f, 0.0f };
    if (root != NULL) {
        uint32_t bgColor = Panel_getBackgroundColor(root);
        if (bgColor != 0)
            decodeColor(bgColor, bg);
    }

    // --- acquire first: the blit needs its target index ------------------
    uint32_t imageIndex = 0;
    VK_LOAD_DEVICE(AcquireNextImageKHR)
    VkResult ar = AcquireNextImageKHR_fn(s_device, s_swapchain, UINT64_MAX,
                                         s_semAcquire, VK_NULL_HANDLE, &imageIndex);
    if (ar == VK_ERROR_OUT_OF_DATE_KHR) {
        if (!rebuildTargets()) return false;
        return Vk_clearPresent();
    }
    if (ar != VK_SUCCESS && ar != VK_SUBOPTIMAL_KHR) return false;

    // The blit destination is ALWAYS the acquired image's true size — the
    // chain's creation extent. After the rebuild above they agree, so this
    // stays a 1:1 NEAREST copy: crisp during drags, never stretched.
    VkExtent2D dstExtent = s_extent;

    VK_LOAD_DEVICE(BeginCommandBuffer)
    VK_LOAD_DEVICE(EndCommandBuffer)
    VK_LOAD_DEVICE(CmdBindPipeline)
    VK_LOAD_DEVICE(CmdPushConstants)
    VK_LOAD_DEVICE(CmdSetViewport)
    VK_LOAD_DEVICE(CmdSetScissor)
    VK_LOAD_DEVICE(CmdDraw)
    VK_LOAD_DEVICE(CmdBlitImage)
    VK_LOAD_DEVICE(CmdPipelineBarrier)
    VK_LOAD_DEVICE(QueueSubmit)
    VK_LOAD_DEVICE(QueuePresentKHR)

    VkCommandBufferBeginInfo bbi = { .sType = VK_STRUCTURE_TYPE_COMMAND_BUFFER_BEGIN_INFO };
    BeginCommandBuffer_fn(s_cmdBuffer, &bbi);

    // --- 1. clear + draw ONE layer of children onto the desktop cache ----
    VkView_beginPass(view, s_cmdBuffer, bg[0], bg[1], bg[2], bg[3]);

    float uTime = (float)((double)(NanoTime_now() - s_animStartNanos) / 1e9);

    static uint32_t s_frameNo = 0;
    static bool s_trace = false;
    static int s_traceInit = 0;
    if (!s_traceInit) {
        s_traceInit = 1;
        s_trace = getenv("ANTI_VK_TRACE") != NULL;
    }
    bool dump = s_trace && (s_frameNo++ % 60 == 0);
    if (dump)
        fprintf(stderr, "vk:trace: win=(%d,%d) %dx%d pts | view cache %.0fx%.0f px | k=%.2f\n",
                winX, winY, winW, winH, cacheW, cacheH, kx);

    if (root != NULL) {
        size_t childCount = Panel_childCount(root);
        for (size_t i = 0; i < childCount; i++) {
            Panel *child = Panel_getChild(root, i);
            if (!child)
                continue;

            // Resolve against the mirrored basket (window-local points),
            // then lift into absolute desktop points and onto this view's
            // cache pixels.
            Vec4 rect;
            Container_resolve(&(*child).base, 0.0f, 0.0f,
                              (float)winW, (float)winH, &rect);
            if (rect.z <= 0.0f || rect.w <= 0.0f)
                continue;

            float px = ((float)winX + rect.x - VkView_getOriginX(view)) * kx;
            float py = ((float)winY + rect.y - VkView_getOriginY(view)) * ky;
            float pw = rect.z * kx;
            float ph = rect.w * ky;
            if (px < 0.0f) { pw += px; px = 0.0f; }
            if (py < 0.0f) { ph += py; py = 0.0f; }
            if (pw <= 0.0f || ph <= 0.0f) continue;
            if (px + pw > cacheW) pw = cacheW - px;
            if (py + ph > cacheH) ph = cacheH - py;
            if (pw <= 0.0f || ph <= 0.0f) continue;

            VkViewport viewport = {0};
            viewport.x = px;
            viewport.y = py;
            viewport.width = pw;
            viewport.height = ph;
            viewport.maxDepth = 1.0f;
            VkRect2D scissor = {0};
            scissor.offset.x = (int32_t)px;
            scissor.offset.y = (int32_t)py;
            scissor.extent.width = (uint32_t)pw;
            scissor.extent.height = (uint32_t)ph;

            uint32_t childType = Memory_type(child);
            if (dump)
                fprintf(stderr, "vk:trace:   child[%zu] type=0x%04x rect=(%.0f,%.0f %.0fx%.0f)px\n",
                        i, childType, px, py, pw, ph);

            if (childType == TYPE_SCENE3D_SINGLETON || childType == TYPE_SCENE2D_SINGLETON
                || childType == TYPE_SCENE_SINGLETON) {
                // Scene child: legacy animated triangle inside its bounds.
                CmdBindPipeline_fn(s_cmdBuffer, VK_PIPELINE_BIND_POINT_GRAPHICS, s_triPipeline);
                CmdSetViewport_fn(s_cmdBuffer, 0, 1, &viewport);
                CmdSetScissor_fn(s_cmdBuffer, 0, 1, &scissor);
                CmdPushConstants_fn(s_cmdBuffer, s_triLayout,
                                    VK_SHADER_STAGE_VERTEX_BIT, 0, 4, &uTime);
                CmdDraw_fn(s_cmdBuffer, 3, 1, 0, 0);
            } else {
                // Plain panel: solid quad in its own color.
                uint32_t color = Panel_getBackgroundColor(child);
                if (color == 0)
                    continue; // PANEL_COLOR_CLEAR draws nothing without blending
                float rgba[4];
                decodeColor(color, rgba);
                float ndc[8]; // rectNdc.xyzw + color.rgba, push-constant block
                ndc[0] = px / cacheW * 2.0f - 1.0f;
                ndc[1] = py / cacheH * 2.0f - 1.0f;
                ndc[2] = pw / cacheW * 2.0f;
                ndc[3] = ph / cacheH * 2.0f;
                ndc[4] = rgba[0];
                ndc[5] = rgba[1];
                ndc[6] = rgba[2];
                ndc[7] = rgba[3];
                CmdBindPipeline_fn(s_cmdBuffer, VK_PIPELINE_BIND_POINT_GRAPHICS, s_quadPipeline);
                CmdSetViewport_fn(s_cmdBuffer, 0, 1, &viewport);
                CmdSetScissor_fn(s_cmdBuffer, 0, 1, &scissor);
                CmdPushConstants_fn(s_cmdBuffer, s_quadLayout,
                                    VK_SHADER_STAGE_VERTEX_BIT | VK_SHADER_STAGE_FRAGMENT_BIT,
                                    0, 32, ndc);
                CmdDraw_fn(s_cmdBuffer, 6, 1, 0, 0);
            }
        }
    }

    // Cache is complete by construction; hand it to the blit.
    VkView_endPass(view, s_cmdBuffer);

    // --- 2. blit the window's region, top-left anchored -------------------
    VkImageMemoryBarrier toDst = { .sType = VK_STRUCTURE_TYPE_IMAGE_MEMORY_BARRIER };
    toDst.srcAccessMask = 0;
    toDst.dstAccessMask = VK_ACCESS_TRANSFER_WRITE_BIT;
    toDst.oldLayout = VK_IMAGE_LAYOUT_UNDEFINED;
    toDst.newLayout = VK_IMAGE_LAYOUT_TRANSFER_DST_OPTIMAL;
    toDst.image = s_swapchainImages[imageIndex];
    toDst.subresourceRange.aspectMask = VK_IMAGE_ASPECT_COLOR_BIT;
    toDst.subresourceRange.levelCount = 1;
    toDst.subresourceRange.layerCount = 1;
    CmdPipelineBarrier_fn(s_cmdBuffer, VK_PIPELINE_STAGE_TOP_OF_PIPE_BIT,
                          VK_PIPELINE_STAGE_TRANSFER_BIT, 0, 0, NULL, 0, NULL, 1, &toDst);

    // Source region: the window's rect in cache pixels, clamped to the cache.
    int32_t sx = (int32_t)(((float)winX - VkView_getOriginX(view)) * kx);
    int32_t sy = (int32_t)(((float)winY - VkView_getOriginY(view)) * ky);
    if (sx < 0) sx = 0;
    if (sy < 0) sy = 0;
    uint32_t sw = (uint32_t)((float)winW * kx);
    uint32_t sh = (uint32_t)((float)winH * ky);
    int32_t cacheWi = (int32_t)cacheW;
    int32_t cacheHi = (int32_t)cacheH;
    if (sx >= cacheWi || sy >= cacheHi) { EndCommandBuffer_fn(s_cmdBuffer); return false; }
    if (sx + (int32_t)sw > cacheWi) sw = (uint32_t)(cacheWi - sx);
    if (sy + (int32_t)sh > cacheHi) sh = (uint32_t)(cacheHi - sy);

    VkImageBlit region = {0};
    region.srcSubresource.aspectMask = VK_IMAGE_ASPECT_COLOR_BIT;
    region.srcSubresource.layerCount = 1;
    region.srcOffsets[0].x = sx;
    region.srcOffsets[0].y = sy;
    region.srcOffsets[1].x = sx + (int32_t)sw;
    region.srcOffsets[1].y = sy + (int32_t)sh;
    region.srcOffsets[1].z = 1;
    region.dstSubresource.aspectMask = VK_IMAGE_ASPECT_COLOR_BIT;
    region.dstSubresource.layerCount = 1;
    region.dstOffsets[1].x = (int32_t)dstExtent.width;
    region.dstOffsets[1].y = (int32_t)dstExtent.height;
    region.dstOffsets[1].z = 1;
    VkFilter filter = (sw == dstExtent.width && sh == dstExtent.height)
                      ? VK_FILTER_NEAREST : VK_FILTER_LINEAR;
    CmdBlitImage_fn(s_cmdBuffer, VkView_image(view), VK_IMAGE_LAYOUT_TRANSFER_SRC_OPTIMAL,
                    s_swapchainImages[imageIndex], VK_IMAGE_LAYOUT_TRANSFER_DST_OPTIMAL,
                    1, &region, filter);

    VkImageMemoryBarrier toPresent = { .sType = VK_STRUCTURE_TYPE_IMAGE_MEMORY_BARRIER };
    toPresent.srcAccessMask = VK_ACCESS_TRANSFER_WRITE_BIT;
    toPresent.dstAccessMask = 0;
    toPresent.oldLayout = VK_IMAGE_LAYOUT_TRANSFER_DST_OPTIMAL;
    toPresent.newLayout = VK_IMAGE_LAYOUT_PRESENT_SRC_KHR;
    toPresent.image = s_swapchainImages[imageIndex];
    toPresent.subresourceRange = toDst.subresourceRange;
    CmdPipelineBarrier_fn(s_cmdBuffer, VK_PIPELINE_STAGE_TRANSFER_BIT,
                          VK_PIPELINE_STAGE_BOTTOM_OF_PIPE_BIT, 0, 0, NULL, 0, NULL, 1, &toPresent);

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
    QueueSubmit_fn(s_queue, 1, &si, s_fence);

    VkPresentInfoKHR pi = { .sType = VK_STRUCTURE_TYPE_PRESENT_INFO_KHR };
    pi.waitSemaphoreCount = 1;
    pi.pWaitSemaphores = &s_semRender;
    pi.swapchainCount = 1;
    pi.pSwapchains = &s_swapchain;
    pi.pImageIndices = &imageIndex;
    VkResult pr = QueuePresentKHR_fn(s_queue, &pi);
    if (pr == VK_ERROR_OUT_OF_DATE_KHR || pr == VK_SUBOPTIMAL_KHR) {
        return pr == VK_SUBOPTIMAL_KHR;
    }
    return pr == VK_SUCCESS;
}
