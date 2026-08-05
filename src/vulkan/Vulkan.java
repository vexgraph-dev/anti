package vulkan;


import org.lwjgl.system.MemoryStack;
import org.lwjgl.system.MemoryUtil;
import org.lwjgl.system.Configuration;
import org.lwjgl.vulkan.*;
import org.lwjgl.PointerBuffer;
import annotation.Intention;
import annotation.Citatiom;
import java.nio.LongBuffer;
import primitive.Long;

import static org.lwjgl.vulkan.EXTMetalSurface.*;
import static org.lwjgl.vulkan.EXTDebugUtils.*;
import static org.lwjgl.vulkan.KHRSurface.*;
import static org.lwjgl.vulkan.KHRSwapchain.*;
import static org.lwjgl.vulkan.VK10.*;
import static org.lwjgl.vulkan.VK12.*;

/**
 * Pure pointer-based FFM Vulkan Bootstrap via LWJGL native endpoints.
 * ZERO object allocations during runtime. MemoryStack used strictly during initialization.
 */
public final class Vulkan {

    private static VkInstance instance;
    private static VkPhysicalDevice physicalDevice;
    private static VkDevice device;
    private static VkQueue graphicsQueue;
    private static VkQueue presentQueue;
    private static long surface;
    private static long swapchain;
    private static long swapchainImages;
    private static int swapchainImageCount;
    private static int swapchainWidth;
    private static int swapchainHeight;
    private static int swapchainFormat;
    private static int presentMode;
    private static long debugMessenger;
    private static VkDebugUtilsMessengerCallbackEXT debugCallback;
    
    private static int graphicsQueueFamilyIndex = -1;
    private static int presentQueueFamilyIndex = -1;

    private Vulkan() {}

    public static void initVulkan(long caMetalLayer, int windowWidth, int windowHeight) {
        initVulkan(caMetalLayer, windowWidth, windowHeight, -1);
    }

    /**
     * @param presentModePreference -1 = auto-select (IMMEDIATE &gt; MAILBOX &gt; FIFO); otherwise the exact
     *                              present mode to use, falling back to FIFO if unsupported.
     */
    public static void initVulkan(long caMetalLayer, int windowWidth, int windowHeight, int presentModePreference) {
        System.out.println("Booting Hardcore Vulkan Subsystem...");
        configureValidationLoader();
        try (MemoryStack stack = MemoryStack.stackPush()) {
            initInstance(stack);
            initSurface(stack, caMetalLayer);
            initDevice(stack);
            initSwapchain(stack, windowWidth, windowHeight, presentModePreference);
        }
        System.out.println("Vulkan Swapchain ready.");
    }

    private static void configureValidationLoader() {
        if (!java.lang.System.getProperty("os.name").toLowerCase().contains("mac")) return;
        if (Configuration.VULKAN_LIBRARY_NAME.get() != null) return;

        String override = java.lang.System.getProperty("anti.vulkan.loader");
        if (override == null || override.isBlank()) {
            if (java.nio.file.Files.exists(java.nio.file.Path.of("/opt/homebrew/lib/libvulkan.1.dylib"))) {
                override = "/opt/homebrew/lib/libvulkan.1.dylib";
            } else if (java.nio.file.Files.exists(java.nio.file.Path.of("/usr/local/lib/libvulkan.1.dylib"))) {
                override = "/usr/local/lib/libvulkan.1.dylib";
            }
        }
        if (override != null && !override.isBlank()) {
            Configuration.VULKAN_LIBRARY_NAME.set(override);
            System.out.println("Using Vulkan loader: " + override);
        }
    }

    public static VkInstance getInstance() {
        return instance;
    }

    public static VkDevice getDevice() {
        return device;
    }

    public static VkQueue getGraphicsQueue() {
        return graphicsQueue;
    }

    public static VkQueue getPresentQueue() {
        return presentQueue;
    }

    public static int getGraphicsQueueFamilyIndex() {
        return graphicsQueueFamilyIndex;
    }

    public static long getSwapchain() {
        return swapchain;
    }

    public static long getSwapchainImages() {
        return swapchainImages;
    }

    public static int getSwapchainImageCount() {
        return swapchainImageCount;
    }

    public static int getSwapchainWidth() {
        return swapchainWidth;
    }

    public static int getSwapchainHeight() {
        return swapchainHeight;
    }

    public static int getSwapchainFormat() {
        return swapchainFormat;
    }

    /** True when the swapchain is vsync-locked (FIFO). Used for frame pacing decisions. */
    public static boolean isVsyncLocked() {
        return presentMode == VK_PRESENT_MODE_FIFO_KHR;
    }

    private static void initInstance(MemoryStack stack) {
        // VK_EXT_debug_utils may be advertised by the validation layer rather than
        // by the bare loader, so do not reject the layer based on global extension
        // enumeration before the layer is enabled.
        boolean validationEnabled = hasInstanceLayer(stack, "VK_LAYER_KHRONOS_validation");
        boolean portabilityEnabled = hasInstanceExtension(stack, "VK_KHR_portability_enumeration");

        VkApplicationInfo appInfo = VkApplicationInfo.calloc(stack)
                .sType(VK_STRUCTURE_TYPE_APPLICATION_INFO)
                .pApplicationName(stack.UTF8("Anti Engine"))
                .applicationVersion(VK_MAKE_VERSION(1, 0, 0))
                .pEngineName(stack.UTF8("Anti Engine"))
                .engineVersion(VK_MAKE_VERSION(1, 0, 0))
                .apiVersion(VK_API_VERSION_1_2);

        int extCount = 2;
        if (validationEnabled) extCount++;
        if (portabilityEnabled) extCount++;

        PointerBuffer extensions = stack.mallocPointer(extCount);
        extensions.put(stack.UTF8(VK_KHR_SURFACE_EXTENSION_NAME));
        extensions.put(stack.UTF8(VK_EXT_METAL_SURFACE_EXTENSION_NAME));
        if (validationEnabled) {
            extensions.put(stack.UTF8(VK_EXT_DEBUG_UTILS_EXTENSION_NAME));
        }
        if (portabilityEnabled) {
            extensions.put(stack.UTF8("VK_KHR_portability_enumeration"));
        }
        extensions.flip();

        VkInstanceCreateInfo createInfo = VkInstanceCreateInfo.calloc(stack)
                .sType(VK_STRUCTURE_TYPE_INSTANCE_CREATE_INFO)
                .pApplicationInfo(appInfo)
                .ppEnabledExtensionNames(extensions);

        if (portabilityEnabled) {
            createInfo.flags(0x00000001); // VK_INSTANCE_CREATE_ENUMERATE_PORTABILITY_BIT_KHR
        }

        if (validationEnabled) {
            debugCallback = VkDebugUtilsMessengerCallbackEXT.create((severity, types, data, userData) -> {
                String message = VkDebugUtilsMessengerCallbackDataEXT.create(data).pMessageString();
                String level = (severity & VK_DEBUG_UTILS_MESSAGE_SEVERITY_ERROR_BIT_EXT) != 0 ? "ERROR"
                        : (severity & VK_DEBUG_UTILS_MESSAGE_SEVERITY_WARNING_BIT_EXT) != 0 ? "WARNING"
                        : (severity & VK_DEBUG_UTILS_MESSAGE_SEVERITY_INFO_BIT_EXT) != 0 ? "INFO" : "VERBOSE";
                System.err.println("[Vulkan " + level + "] " + message);
                return VK_FALSE;
            });

            VkDebugUtilsMessengerCreateInfoEXT debugCreateInfo = VkDebugUtilsMessengerCreateInfoEXT.calloc(stack)
                    .sType(VK_STRUCTURE_TYPE_DEBUG_UTILS_MESSENGER_CREATE_INFO_EXT)
                    .messageSeverity(VK_DEBUG_UTILS_MESSAGE_SEVERITY_VERBOSE_BIT_EXT
                            | VK_DEBUG_UTILS_MESSAGE_SEVERITY_INFO_BIT_EXT
                            | VK_DEBUG_UTILS_MESSAGE_SEVERITY_WARNING_BIT_EXT
                            | VK_DEBUG_UTILS_MESSAGE_SEVERITY_ERROR_BIT_EXT)
                    .messageType(VK_DEBUG_UTILS_MESSAGE_TYPE_GENERAL_BIT_EXT
                            | VK_DEBUG_UTILS_MESSAGE_TYPE_VALIDATION_BIT_EXT
                            | VK_DEBUG_UTILS_MESSAGE_TYPE_PERFORMANCE_BIT_EXT)
                    .pfnUserCallback(debugCallback);
            createInfo.pNext(debugCreateInfo.address());
        }

        PointerBuffer layers = stack.mallocPointer(validationEnabled ? 1 : 0);
        if (validationEnabled) {
            layers.put(stack.UTF8("VK_LAYER_KHRONOS_validation")).flip();
        }
        createInfo.ppEnabledLayerNames(layers);

        PointerBuffer pInstance = stack.mallocPointer(1);
        if (VK10.vkCreateInstance(createInfo, null, pInstance) != VK_SUCCESS) {
            throw new RuntimeException("Failed to create Vulkan Instance.");
        }
        instance = new VkInstance(pInstance.get(0), createInfo);

        if (validationEnabled) {
            VkDebugUtilsMessengerCreateInfoEXT debugCreateInfo = VkDebugUtilsMessengerCreateInfoEXT.calloc(stack)
                    .sType(VK_STRUCTURE_TYPE_DEBUG_UTILS_MESSENGER_CREATE_INFO_EXT)
                    .messageSeverity(VK_DEBUG_UTILS_MESSAGE_SEVERITY_VERBOSE_BIT_EXT
                            | VK_DEBUG_UTILS_MESSAGE_SEVERITY_INFO_BIT_EXT
                            | VK_DEBUG_UTILS_MESSAGE_SEVERITY_WARNING_BIT_EXT
                            | VK_DEBUG_UTILS_MESSAGE_SEVERITY_ERROR_BIT_EXT)
                    .messageType(VK_DEBUG_UTILS_MESSAGE_TYPE_GENERAL_BIT_EXT
                            | VK_DEBUG_UTILS_MESSAGE_TYPE_VALIDATION_BIT_EXT
                            | VK_DEBUG_UTILS_MESSAGE_TYPE_PERFORMANCE_BIT_EXT)
                    .pfnUserCallback(debugCallback);
            LongBuffer pMessenger = stack.mallocLong(1);
            if (vkCreateDebugUtilsMessengerEXT(instance, debugCreateInfo, null, pMessenger) != VK_SUCCESS) {
                throw new RuntimeException("Failed to create Vulkan debug messenger.");
            }
            debugMessenger = pMessenger.get(0);
            System.out.println("Vulkan validation enabled.");
        } else {
            System.out.println("Vulkan validation unavailable: VK_LAYER_KHRONOS_validation or VK_EXT_debug_utils not found.");
        }
    }

    private static boolean hasInstanceLayer(MemoryStack stack, String wanted) {
        java.nio.IntBuffer count = stack.mallocInt(1);
        if (VK10.vkEnumerateInstanceLayerProperties(count, null) != VK_SUCCESS) return false;
        VkLayerProperties.Buffer layers = VkLayerProperties.calloc(count.get(0), stack);
        if (VK10.vkEnumerateInstanceLayerProperties(count, layers) != VK_SUCCESS) return false;
        for (VkLayerProperties layer : layers) {
            if (wanted.equals(layer.layerNameString())) return true;
        }
        return false;
    }

    private static boolean hasInstanceExtension(MemoryStack stack, String wanted) {
        java.nio.IntBuffer count = stack.mallocInt(1);
        if (VK10.vkEnumerateInstanceExtensionProperties((CharSequence) null, count, null) != VK_SUCCESS) return false;
        VkExtensionProperties.Buffer extensions = VkExtensionProperties.calloc(count.get(0), stack);
        if (VK10.vkEnumerateInstanceExtensionProperties((CharSequence) null, count, extensions) != VK_SUCCESS) return false;
        for (VkExtensionProperties ext : extensions) {
            if (wanted.equals(ext.extensionNameString())) return true;
        }
        return false;
    }

    private static boolean hasDeviceExtension(VkPhysicalDevice physicalDevice, MemoryStack stack, String wanted) {
        java.nio.IntBuffer count = stack.mallocInt(1);
        if (VK10.vkEnumerateDeviceExtensionProperties(physicalDevice, (CharSequence) null, count, null) != VK_SUCCESS) return false;
        VkExtensionProperties.Buffer extensions = VkExtensionProperties.calloc(count.get(0), stack);
        if (VK10.vkEnumerateDeviceExtensionProperties(physicalDevice, (CharSequence) null, count, extensions) != VK_SUCCESS) return false;
        for (VkExtensionProperties ext : extensions) {
            if (wanted.equals(ext.extensionNameString())) return true;
        }
        return false;
    }

    private static void initSurface(MemoryStack stack, long caMetalLayer) {
        VkMetalSurfaceCreateInfoEXT createInfo = VkMetalSurfaceCreateInfoEXT.calloc(stack)
                .sType(VK_STRUCTURE_TYPE_METAL_SURFACE_CREATE_INFO_EXT);
        MemoryUtil.memPutAddress(createInfo.address() + VkMetalSurfaceCreateInfoEXT.PLAYER, caMetalLayer);

        LongBuffer pSurface = stack.mallocLong(1);
        if (EXTMetalSurface.vkCreateMetalSurfaceEXT(instance, createInfo, null, pSurface) != VK_SUCCESS) {
            throw new RuntimeException("Failed to create Metal Surface.");
        }
        surface = pSurface.get(0);
    }

    private static void initDevice(MemoryStack stack) {
        java.nio.IntBuffer pDeviceCount = stack.mallocInt(1);
        VK10.vkEnumeratePhysicalDevices(instance, pDeviceCount, null);
        if (pDeviceCount.get(0) == 0) throw new RuntimeException("No Vulkan physical devices found!");

        PointerBuffer pDevices = stack.mallocPointer(pDeviceCount.get(0));
        VK10.vkEnumeratePhysicalDevices(instance, pDeviceCount, pDevices);
        physicalDevice = new VkPhysicalDevice(pDevices.get(0), instance);

        graphicsQueueFamilyIndex = 0;
        presentQueueFamilyIndex = 0;

        VkDeviceQueueCreateInfo.Buffer queueCreateInfo = VkDeviceQueueCreateInfo.calloc(1, stack)
                .sType(VK_STRUCTURE_TYPE_DEVICE_QUEUE_CREATE_INFO)
                .queueFamilyIndex(graphicsQueueFamilyIndex)
                .pQueuePriorities(stack.floats(1.0f));

        VkPhysicalDeviceVulkan12Features bindlessFeatures = VkPhysicalDeviceVulkan12Features.calloc(stack)
                .sType(VK_STRUCTURE_TYPE_PHYSICAL_DEVICE_VULKAN_1_2_FEATURES)
                .descriptorBindingPartiallyBound(true)
                .runtimeDescriptorArray(true)
                .shaderSampledImageArrayNonUniformIndexing(true)
                .descriptorBindingSampledImageUpdateAfterBind(true)
                .descriptorBindingStorageImageUpdateAfterBind(true)
                .shaderStorageImageArrayNonUniformIndexing(true);

        VkPhysicalDeviceFeatures2 features2 = VkPhysicalDeviceFeatures2.calloc(stack)
                .sType(VK_STRUCTURE_TYPE_PHYSICAL_DEVICE_FEATURES_2)
                .pNext(bindlessFeatures.address());

        boolean portabilitySupported = hasDeviceExtension(physicalDevice, stack, "VK_KHR_portability_subset");
        PointerBuffer extensions = stack.mallocPointer(portabilitySupported ? 2 : 1);
        extensions.put(stack.UTF8(VK_KHR_SWAPCHAIN_EXTENSION_NAME));
        if (portabilitySupported) {
            extensions.put(stack.UTF8("VK_KHR_portability_subset"));
        }
        extensions.flip();

        VkDeviceCreateInfo createInfo = VkDeviceCreateInfo.calloc(stack)
                .sType(VK_STRUCTURE_TYPE_DEVICE_CREATE_INFO)
                .pNext(features2.address())
                .pQueueCreateInfos(queueCreateInfo)
                .ppEnabledExtensionNames(extensions);

        PointerBuffer pDevice = stack.mallocPointer(1);
        if (VK10.vkCreateDevice(physicalDevice, createInfo, null, pDevice) != VK_SUCCESS) {
            throw new RuntimeException("Failed to create Logical Device.");
        }
        device = new VkDevice(pDevice.get(0), physicalDevice, createInfo);

        PointerBuffer pQueue = stack.mallocPointer(1);
        VK10.vkGetDeviceQueue(device, graphicsQueueFamilyIndex, 0, pQueue);
        graphicsQueue = new VkQueue(pQueue.get(0), device);
        presentQueue = graphicsQueue;
    }

    @Intention("Best low-latency present mode selection. On macOS this is only honored in fullscreen; a windowed CAMetalLayer is vsync-throttled to the display refresh rate regardless of the chosen mode.")
    @Citatiom(cite = 3)
    private static void initSwapchain(MemoryStack stack, int width, int height, int preferredMode) {
        swapchainWidth = width;
        swapchainHeight = height;
        swapchainFormat = VK_FORMAT_B8G8R8A8_SRGB;

        // Query supported present modes to find the best low-latency/un-throttled mode
        java.nio.IntBuffer modeCount = stack.mallocInt(1);
        vkGetPhysicalDeviceSurfacePresentModesKHR(physicalDevice, surface, modeCount, null);
        java.nio.IntBuffer modes = stack.mallocInt(modeCount.get(0));
        vkGetPhysicalDeviceSurfacePresentModesKHR(physicalDevice, surface, modeCount, modes);

        int chosenMode;
        if (preferredMode >= 0) {
            chosenMode = VK_PRESENT_MODE_FIFO_KHR; // default required fallback
            for (int i = 0; i < modeCount.get(0); i++) {
                if (modes.get(i) == preferredMode) {
                    chosenMode = preferredMode;
                    break;
                }
            }
        } else {
            chosenMode = VK_PRESENT_MODE_FIFO_KHR; // default required fallback
            for (int i = 0; i < modeCount.get(0); i++) {
                int mode = modes.get(i);
                if (mode == VK_PRESENT_MODE_IMMEDIATE_KHR) {
                    chosenMode = VK_PRESENT_MODE_IMMEDIATE_KHR;
                    break; // immediate is first choice
                } else if (mode == VK_PRESENT_MODE_MAILBOX_KHR) {
                    chosenMode = VK_PRESENT_MODE_MAILBOX_KHR; // mailbox is second choice
                }
            }
        }
        presentMode = chosenMode;

        VkSwapchainCreateInfoKHR createInfo = VkSwapchainCreateInfoKHR.calloc(stack)
                .sType(VK_STRUCTURE_TYPE_SWAPCHAIN_CREATE_INFO_KHR)
                .surface(surface)
                .minImageCount(3)
                .imageFormat(VK_FORMAT_B8G8R8A8_SRGB)
                .imageColorSpace(VK_COLOR_SPACE_SRGB_NONLINEAR_KHR)
                .imageArrayLayers(1)
                .imageUsage(VK_IMAGE_USAGE_COLOR_ATTACHMENT_BIT)
                .imageSharingMode(VK_SHARING_MODE_EXCLUSIVE)
                .preTransform(VK_SURFACE_TRANSFORM_IDENTITY_BIT_KHR)
                .compositeAlpha(VK_COMPOSITE_ALPHA_OPAQUE_BIT_KHR)
                .presentMode(chosenMode)
                .clipped(true)
                .oldSwapchain(VK_NULL_HANDLE);
        
        createInfo.imageExtent().width(width).height(height);

        LongBuffer pSwapchain = stack.mallocLong(1);
        if (KHRSwapchain.vkCreateSwapchainKHR(device, createInfo, null, pSwapchain) != VK_SUCCESS) {
            throw new RuntimeException("Failed to create Swapchain.");
        }
        swapchain = pSwapchain.get(0);

        java.nio.IntBuffer pImageCount = stack.mallocInt(1);
        if (KHRSwapchain.vkGetSwapchainImagesKHR(device, swapchain, pImageCount, null) != VK_SUCCESS) {
            throw new RuntimeException("Failed to query Vulkan swapchain image count.");
        }
        swapchainImageCount = pImageCount.get(0);
        LongBuffer images = stack.mallocLong(swapchainImageCount);
        if (KHRSwapchain.vkGetSwapchainImagesKHR(device, swapchain, pImageCount, images) != VK_SUCCESS) {
            throw new RuntimeException("Failed to retrieve Vulkan swapchain images.");
        }

        swapchainImages = Long.allocateArray(swapchainImageCount);
        for (int i = 0; i < swapchainImageCount; i++) {
            Long.set(swapchainImages, i, images.get(i));
        }
    }
}
