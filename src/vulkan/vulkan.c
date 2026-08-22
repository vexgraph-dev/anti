#define VK_USE_PLATFORM_MACOS_MVK
#define VK_USE_PLATFORM_METAL_EXT

#include "vulkan/vk.h"
#include <vulkan/vulkan_core.h>
#include <vulkan/vulkan_macos.h>

#include <dlfcn.h>
#include <stdio.h>
#include <string.h>

#include "darling/container.h"
#include "darling/panel.h"
#include "darling/scene.h"
#include "window/window.h"

// vulkan/vulkan.c — runtime-loaded Vulkan chain (Legacy: none — the Java tree
// called Vulkan through LWJGL; the C engine speaks to it natively).
//
// Loader strategy: dlopen the Khronos loader (libvulkan.dylib) first, falling
// back straight to MoltenVK's ICD. Everything beyond vkGetInstanceProcAddr is
// fetched dynamically, instance-level then device-level.

static void *s_lib = NULL;
static PFN_vkGetInstanceProcAddr s_gpa = NULL;
static PFN_vkGetDeviceProcAddr s_gdpa = NULL;
static char s_status[64] = "not started";
#define VK_MARK(msg) do { snprintf(s_status, sizeof(s_status), msg); fprintf(stderr, "vk: %s\n", s_status); } while(0)

// Swapchain + views + framebuffers: rebuilt whenever the surface outgrows the
// chain (fullscreen, resize). Renderpass/pipeline are extent-independent.
static bool rebuildTargets(void);
static void destroyTargets(void);

static VkInstance s_instance;
static VkSurfaceKHR s_surface;
static VkPhysicalDevice s_phys;
static uint32_t s_queueFamily = 0;
static VkDevice s_device;
static VkQueue s_queue;
static VkSwapchainKHR s_swapchain;
static VkFormat s_format;
static VkExtent2D s_extent;

// hello-triangle targets (hoisted: rebuildTargets/destroyTargets need them)
static VkRenderPass s_triPass;
static VkPipelineLayout s_triLayout;
static VkPipeline s_triPipeline;
static VkImageView s_views[8];
static VkFramebuffer s_framebuffers[8];
static uint32_t s_imageCount = 0;
static bool s_triBuilt = false;

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

    // 6. swapchain + views + framebuffers (rebuilt on every surface resize)
    if (!rebuildTargets())
        return false;
    return true;
}

// --- swapchain targets: created, and re-created on fullscreen/resize ---------
// Fullscreen changes the view extent; presenting to a stale chain is the bug
// that "closed" the app. Now: proactive extent check per frame, reactive
// rebuild on OUT_OF_DATE/SUBOPTIMAL.

static bool rebuildTargets(void) {
    VK_LOAD_DEVICE(CreateSwapchainKHR)
    VK_LOAD_DEVICE(DestroySwapchainKHR)
    VK_LOAD_DEVICE(DeviceWaitIdle)
    VK_LOAD_DEVICE(GetSwapchainImagesKHR)
    VK_LOAD_DEVICE(CreateImageView)
    VK_LOAD_DEVICE(CreateFramebuffer)
    VK_LOAD_DEVICE_VOID(DestroyFramebuffer)
    VK_LOAD_DEVICE_VOID(DestroyImageView)
    VK_LOAD_INSTANCE(GetPhysicalDeviceSurfaceCapabilitiesKHR)
    VK_LOAD_INSTANCE(GetPhysicalDeviceSurfaceFormatsKHR)

    // Wait for in-flight GPU & Metal completion queue work before recreating targets
    DeviceWaitIdle_fn(s_device);

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

    VkSwapchainCreateInfoKHR swci = { .sType = VK_STRUCTURE_TYPE_SWAPCHAIN_CREATE_INFO_KHR };
    swci.surface = s_surface;
    swci.minImageCount = caps.minImageCount;
    swci.imageFormat = s_format;
    swci.imageColorSpace = formatCount ? formats[0].colorSpace : VK_COLOR_SPACE_SRGB_NONLINEAR_KHR;
    swci.imageExtent = caps.currentExtent;
    s_extent = caps.currentExtent;
    swci.imageArrayLayers = 1;
    swci.imageUsage = VK_IMAGE_USAGE_COLOR_ATTACHMENT_BIT;
    swci.imageSharingMode = VK_SHARING_MODE_EXCLUSIVE;
    swci.preTransform = caps.currentTransform;
    swci.compositeAlpha = VK_COMPOSITE_ALPHA_OPAQUE_BIT_KHR;
    swci.presentMode = VK_PRESENT_MODE_FIFO_KHR;
    swci.clipped = VK_TRUE;
    swci.oldSwapchain = oldSwapchain;

    VkSwapchainKHR newSwapchain = VK_NULL_HANDLE;
    if (CreateSwapchainKHR_fn(s_device, &swci, NULL, &newSwapchain) != VK_SUCCESS) {
        snprintf(s_status, sizeof(s_status), "swapchain failed");
        return false;
    }

    // Safely destroy previous framebuffers, views, and old swapchain
    for (uint32_t i = 0; i < s_imageCount; i++) {
        if (s_framebuffers[i] != VK_NULL_HANDLE)
            DestroyFramebuffer_fn(s_device, s_framebuffers[i], NULL);
        s_framebuffers[i] = VK_NULL_HANDLE;
        if (s_views[i] != VK_NULL_HANDLE)
            DestroyImageView_fn(s_device, s_views[i], NULL);
        s_views[i] = VK_NULL_HANDLE;
    }
    if (oldSwapchain != VK_NULL_HANDLE)
        DestroySwapchainKHR_fn(s_device, oldSwapchain, NULL);

    s_swapchain = newSwapchain;

    GetSwapchainImagesKHR_fn(s_device, s_swapchain, &s_imageCount, NULL);
    if (s_imageCount > 8)
        s_imageCount = 8;
    VkImage images[8];
    GetSwapchainImagesKHR_fn(s_device, s_swapchain, &s_imageCount, images);

    for (uint32_t i = 0; i < s_imageCount; i++) {
        VkImageViewCreateInfo vci = { .sType = VK_STRUCTURE_TYPE_IMAGE_VIEW_CREATE_INFO };
        vci.image = images[i];
        vci.viewType = VK_IMAGE_VIEW_TYPE_2D;
        vci.format = s_format;
        vci.subresourceRange.aspectMask = VK_IMAGE_ASPECT_COLOR_BIT;
        vci.subresourceRange.levelCount = 1;
        vci.subresourceRange.layerCount = 1;
        if (CreateImageView_fn(s_device, &vci, NULL, &s_views[i]) != VK_SUCCESS) {
            snprintf(s_status, sizeof(s_status), "imageview failed");
            return false;
        }
        if (s_triPass == VK_NULL_HANDLE)
            continue; // framebuffers arrive with the pipeline init
        VkFramebufferCreateInfo fci = { .sType = VK_STRUCTURE_TYPE_FRAMEBUFFER_CREATE_INFO };
        fci.renderPass = s_triPass;
        fci.attachmentCount = 1;
        fci.pAttachments = &s_views[i];
        fci.width = s_extent.width;
        fci.height = s_extent.height;
        fci.layers = 1;
        if (CreateFramebuffer_fn(s_device, &fci, NULL, &s_framebuffers[i]) != VK_SUCCESS) {
            snprintf(s_status, sizeof(s_status), "framebuffer failed");
            return false;
        }
    }

    fprintf(stderr, "vk: swapchain live %ux%u fmt=%d\n", s_extent.width, s_extent.height, (int)s_format);
    return true;
}

static void destroyTargets(void) {
    if (s_device == VK_NULL_HANDLE)
        return;
    VK_LOAD_DEVICE_VOID(DeviceWaitIdle)
    VK_LOAD_DEVICE_VOID(DestroyFramebuffer)
    VK_LOAD_DEVICE_VOID(DestroyImageView)
    VK_LOAD_DEVICE_VOID(DestroySwapchainKHR)

    if (DeviceWaitIdle_fn)
        DeviceWaitIdle_fn(s_device);

    for (uint32_t i = 0; i < s_imageCount; i++) {
        if (s_framebuffers[i] != VK_NULL_HANDLE && DestroyFramebuffer_fn)
            DestroyFramebuffer_fn(s_device, s_framebuffers[i], NULL);
        s_framebuffers[i] = VK_NULL_HANDLE;
        if (s_views[i] != VK_NULL_HANDLE && DestroyImageView_fn)
            DestroyImageView_fn(s_device, s_views[i], NULL);
        s_views[i] = VK_NULL_HANDLE;
    }
    s_imageCount = 0;
    if (s_swapchain != VK_NULL_HANDLE && DestroySwapchainKHR_fn)
        DestroySwapchainKHR_fn(s_device, s_swapchain, NULL);
    s_swapchain = VK_NULL_HANDLE;
}

static Scene3D *s_scene3D = NULL;

void Vk_setScene3D(Scene3D *scene) {
    s_scene3D = scene;
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
bool Vk_clearPresent(float r, float g, float b) {
    (void)r;
    (void)g;
    (void)b;
    return Vk_ready();
}

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

static VkCommandPool s_cmdPool;
static VkCommandBuffer s_cmdBuffer;
static VkSemaphore s_semAcquire;
static VkSemaphore s_semRender;
static VkFence s_fence;

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

bool Vk_helloTriangle(float timeSeconds) {
    if (!Vk_ready())
        return false;

    if (!s_triBuilt) {
        // --- renderpass + pipeline + sync (targets live in rebuildTargets)
        VK_LOAD_DEVICE(CreateFramebuffer)
        VK_LOAD_DEVICE(CreateRenderPass)
        VK_LOAD_DEVICE(CreatePipelineLayout)
        VK_LOAD_DEVICE(CreateGraphicsPipelines)
        VK_LOAD_DEVICE(CreateCommandPool)
        VK_LOAD_DEVICE(AllocateCommandBuffers)
        VK_LOAD_DEVICE(CreateSemaphore)
        VK_LOAD_DEVICE(CreateFence)

        VkAttachmentDescription att = {0};
        att.format = s_format;
        att.samples = VK_SAMPLE_COUNT_1_BIT;
        att.loadOp = VK_ATTACHMENT_LOAD_OP_CLEAR;   // legacy TriangleRenderer
        att.storeOp = VK_ATTACHMENT_STORE_OP_STORE;
        att.stencilLoadOp = VK_ATTACHMENT_LOAD_OP_DONT_CARE;
        att.stencilStoreOp = VK_ATTACHMENT_STORE_OP_DONT_CARE;
        att.initialLayout = VK_IMAGE_LAYOUT_UNDEFINED;
        att.finalLayout = VK_IMAGE_LAYOUT_PRESENT_SRC_KHR;

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

        if (CreateRenderPass_fn(s_device, &rpci, NULL, &s_triPass) != VK_SUCCESS) {
            snprintf(s_status, sizeof(s_status), "renderpass failed");
            return false;
        }

        // render pass now exists: build the framebuffers that were deferred
        for (uint32_t i = 0; i < s_imageCount; i++) {
            VkFramebufferCreateInfo fci = { .sType = VK_STRUCTURE_TYPE_FRAMEBUFFER_CREATE_INFO };
            fci.renderPass = s_triPass;
            fci.attachmentCount = 1;
            fci.pAttachments = &s_views[i];
            fci.width = s_extent.width;
            fci.height = s_extent.height;
            fci.layers = 1;
            if (CreateFramebuffer_fn(s_device, &fci, NULL, &s_framebuffers[i]) != VK_SUCCESS) {
                snprintf(s_status, sizeof(s_status), "framebuffer failed");
                return false;
            }
        }

        // --- pipeline: fullscreen triangle, no vertex input, push u_time
        VkShaderModule vert = createShaderModule(
            "hello_triangle_vert.spv", NULL);
        VkShaderModule frag = createShaderModule(
            "hello_triangle_frag.spv", NULL);
        if (vert == VK_NULL_HANDLE || frag == VK_NULL_HANDLE) {
            snprintf(s_status, sizeof(s_status), "shader spv not found");
            fprintf(stderr, "vk: shader spv not found (vert=%p frag=%p)\n", (void *)vert, (void *)frag);
            return false;
        }

        VkPushConstantRange push = {0};
        push.stageFlags = VK_SHADER_STAGE_VERTEX_BIT;
        push.offset = 0;
        push.size = 4; // float u_time

        VkPipelineLayoutCreateInfo plci = { .sType = VK_STRUCTURE_TYPE_PIPELINE_LAYOUT_CREATE_INFO };
        plci.pushConstantRangeCount = 1;
        plci.pPushConstantRanges = &push;
        if (CreatePipelineLayout_fn(s_device, &plci, NULL, &s_triLayout) != VK_SUCCESS) {
            snprintf(s_status, sizeof(s_status), "layout failed");
            return false;
        }

        VkPipelineShaderStageCreateInfo stages[2] = {{0}, {0}};
        stages[0].sType = VK_STRUCTURE_TYPE_PIPELINE_SHADER_STAGE_CREATE_INFO;
        stages[0].stage = VK_SHADER_STAGE_VERTEX_BIT;
        stages[0].module = vert;
        stages[0].pName = "main";
        stages[1].sType = VK_STRUCTURE_TYPE_PIPELINE_SHADER_STAGE_CREATE_INFO;
        stages[1].stage = VK_SHADER_STAGE_FRAGMENT_BIT;
        stages[1].module = frag;
        stages[1].pName = "main";

        VkPipelineVertexInputStateCreateInfo vi = { .sType = VK_STRUCTURE_TYPE_PIPELINE_VERTEX_INPUT_STATE_CREATE_INFO };
        VkPipelineInputAssemblyStateCreateInfo ia = { .sType = VK_STRUCTURE_TYPE_PIPELINE_INPUT_ASSEMBLY_STATE_CREATE_INFO };
        ia.topology = VK_PRIMITIVE_TOPOLOGY_TRIANGLE_LIST;
        VkPipelineViewportStateCreateInfo vp = { .sType = VK_STRUCTURE_TYPE_PIPELINE_VIEWPORT_STATE_CREATE_INFO };
        vp.viewportCount = 1;
        vp.scissorCount = 1;
        VkPipelineRasterizationStateCreateInfo rs = { .sType = VK_STRUCTURE_TYPE_PIPELINE_RASTERIZATION_STATE_CREATE_INFO };
        rs.polygonMode = VK_POLYGON_MODE_FILL;
        rs.cullMode = VK_CULL_MODE_BACK_BIT;       // legacy: CLOCKWISE front face
        rs.frontFace = VK_FRONT_FACE_CLOCKWISE;
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

        VkGraphicsPipelineCreateInfo pci = { .sType = VK_STRUCTURE_TYPE_GRAPHICS_PIPELINE_CREATE_INFO };
        pci.stageCount = 2;
        pci.pStages = stages;
        pci.pVertexInputState = &vi;
        pci.pInputAssemblyState = &ia;
        pci.pViewportState = &vp;
        pci.pRasterizationState = &rs;
        pci.pMultisampleState = &ms;
        pci.pColorBlendState = &cb;
        pci.pDynamicState = &ds;
        pci.layout = s_triLayout;
        pci.renderPass = s_triPass;
        pci.subpass = 0;

        if (CreateGraphicsPipelines_fn(s_device, VK_NULL_HANDLE, 1, &pci, NULL, &s_triPipeline) != VK_SUCCESS) {
            snprintf(s_status, sizeof(s_status), "pipeline failed");
            return false;
        }

        // --- command pool/buffer + sync objects
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

        VkSemaphoreCreateInfo sci2 = { .sType = VK_STRUCTURE_TYPE_SEMAPHORE_CREATE_INFO };
        CreateSemaphore_fn(s_device, &sci2, NULL, &s_semAcquire);
        CreateSemaphore_fn(s_device, &sci2, NULL, &s_semRender);

        VkFenceCreateInfo fci2 = { .sType = VK_STRUCTURE_TYPE_FENCE_CREATE_INFO };
        fci2.flags = VK_FENCE_CREATE_SIGNALED_BIT;
        CreateFence_fn(s_device, &fci2, NULL, &s_fence);

        s_triBuilt = true;
    }

    // --- per-frame: acquire -> record -> submit -> present
    VK_LOAD_DEVICE(AcquireNextImageKHR)
    VK_LOAD_DEVICE(QueuePresentKHR)
    VK_LOAD_DEVICE(QueueSubmit)
    VK_LOAD_DEVICE(WaitForFences)
    VK_LOAD_DEVICE(ResetFences)
    VK_LOAD_DEVICE(ResetCommandBuffer)
    VK_LOAD_DEVICE(BeginCommandBuffer)
    VK_LOAD_DEVICE(EndCommandBuffer)
    VK_LOAD_DEVICE(CmdBeginRenderPass)
    VK_LOAD_DEVICE(CmdEndRenderPass)
    VK_LOAD_DEVICE(CmdBindPipeline)
    VK_LOAD_DEVICE(CmdPushConstants)
    VK_LOAD_DEVICE(CmdSetViewport)
    VK_LOAD_DEVICE(CmdSetScissor)
    VK_LOAD_DEVICE(CmdDraw)

    // resize sentry: fullscreen/resize changes the surface extent behind our
    // back; presenting to a stale chain is what "closed" the app before.
    VK_LOAD_INSTANCE(GetPhysicalDeviceSurfaceCapabilitiesKHR)

    VkSurfaceCapabilitiesKHR live;
    memset(&live, 0, sizeof(live));
    if (GetPhysicalDeviceSurfaceCapabilitiesKHR_fn(s_phys, s_surface, &live) == VK_SUCCESS
        && (live.currentExtent.width != s_extent.width
            || live.currentExtent.height != s_extent.height)) {
        fprintf(stderr, "vk: extent moved %ux%u -> %ux%u; rebuilding\n",
                s_extent.width, s_extent.height,
                live.currentExtent.width, live.currentExtent.height);
        if (!rebuildTargets())
            return false;
    }

    uint32_t imageIndex = 0;
    VkResult ar = AcquireNextImageKHR_fn(s_device, s_swapchain, UINT64_MAX,
                                         s_semAcquire, VK_NULL_HANDLE, &imageIndex);
    if (ar == VK_ERROR_OUT_OF_DATE_KHR) {
        if (!rebuildTargets())
            return false;
        return Vk_helloTriangle(timeSeconds); // one retry on fresh targets
    }
    if (ar != VK_SUCCESS && ar != VK_SUBOPTIMAL_KHR)
        return false;

    WaitForFences_fn(s_device, 1, &s_fence, VK_TRUE, UINT64_MAX);
    ResetFences_fn(s_device, 1, &s_fence);
    ResetCommandBuffer_fn(s_cmdBuffer, 0);

    VkCommandBufferBeginInfo bbi = { .sType = VK_STRUCTURE_TYPE_COMMAND_BUFFER_BEGIN_INFO };
    BeginCommandBuffer_fn(s_cmdBuffer, &bbi);

    VkClearValue clear = {0};
    clear.color.float32[3] = 1.0f;

    VkViewport viewport = {0};
    viewport.width = (float)s_extent.width;
    viewport.height = (float)s_extent.height;
    viewport.maxDepth = 1.0f;

    VkRect2D scissor = {0};
    scissor.extent = s_extent;

    if (s_scene3D != NULL) {
        Vec4 rect;
        Container_resolve(&(*s_scene3D).base.base.base, 0.0f, 0.0f, (float)s_extent.width, (float)s_extent.height, &rect);

        uint32_t bg = Panel_getBackgroundColor(&(*s_scene3D).base.base);
        if (bg != 0) {
            clear.color.float32[0] = ((bg >> 16) & 0xFF) / 255.0f;
            clear.color.float32[1] = ((bg >> 8) & 0xFF) / 255.0f;
            clear.color.float32[2] = (bg & 0xFF) / 255.0f;
            clear.color.float32[3] = ((bg >> 24) & 0xFF) / 255.0f;
        }

        if (rect.z > 0.0f && rect.w > 0.0f) {
            viewport.x = rect.x;
            viewport.y = rect.y;
            viewport.width = rect.z;
            viewport.height = rect.w;

            int32_t sx = (int32_t)rect.x > 0 ? (int32_t)rect.x : 0;
            int32_t sy = (int32_t)rect.y > 0 ? (int32_t)rect.y : 0;
            uint32_t sw = (uint32_t)rect.z;
            uint32_t sh = (uint32_t)rect.w;

            if ((uint32_t)sx + sw > s_extent.width)
                sw = s_extent.width > (uint32_t)sx ? s_extent.width - (uint32_t)sx : 0;
            if ((uint32_t)sy + sh > s_extent.height)
                sh = s_extent.height > (uint32_t)sy ? s_extent.height - (uint32_t)sy : 0;

            scissor.offset.x = sx;
            scissor.offset.y = sy;
            scissor.extent.width = sw;
            scissor.extent.height = sh;
        }
    }

    VkRenderPassBeginInfo rbi = { .sType = VK_STRUCTURE_TYPE_RENDER_PASS_BEGIN_INFO };
    rbi.renderPass = s_triPass;
    rbi.framebuffer = s_framebuffers[imageIndex];
    rbi.renderArea.extent = s_extent;
    rbi.clearValueCount = 1;
    rbi.pClearValues = &clear;
    CmdBeginRenderPass_fn(s_cmdBuffer, &rbi, VK_SUBPASS_CONTENTS_INLINE);

    CmdBindPipeline_fn(s_cmdBuffer, VK_PIPELINE_BIND_POINT_GRAPHICS, s_triPipeline);
    CmdSetViewport_fn(s_cmdBuffer, 0, 1, &viewport);
    CmdSetScissor_fn(s_cmdBuffer, 0, 1, &scissor);

    CmdPushConstants_fn(s_cmdBuffer, s_triLayout, VK_SHADER_STAGE_VERTEX_BIT, 0, 4,
                        (const void *)&timeSeconds);
    CmdDraw_fn(s_cmdBuffer, 3, 1, 0, 0);
    CmdEndRenderPass_fn(s_cmdBuffer);
    EndCommandBuffer_fn(s_cmdBuffer);

    VkPipelineStageFlags waitStage = VK_PIPELINE_STAGE_COLOR_ATTACHMENT_OUTPUT_BIT;
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
        // targets rebuilt on the next call's sentry
        return pr == VK_SUBOPTIMAL_KHR;
    }
    return pr == VK_SUCCESS;
}
