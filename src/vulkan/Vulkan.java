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

import nio.StringLookup;
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
    private static long layerPointer;
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
        System.out.println(StringLookup.getJavaString(931));
        configureValidationLoader();
        try (MemoryStack stack = MemoryStack.stackPush()) {
            initInstance(stack);
            initSurface(stack, caMetalLayer);
            initDevice(stack);

            long screenSize = window.Window.getScreenBackingSize();
            int maxW = (int) (screenSize >>> 32);
            int maxH = (int) (screenSize & 0xFFFFFFFFL);
            int swapW = maxW > 0 ? Math.max(windowWidth, maxW) : windowWidth;
            int swapH = maxH > 0 ? Math.max(windowHeight, maxH) : windowHeight;

            initSwapchain(stack, swapW, swapH, presentModePreference);
        } catch (RuntimeException e) {
            shutdown();
            throw e;
        }
        applyLayerSync();
        System.out.println(StringLookup.getJavaString(932));
    }

    /** Releases every Vulkan resource created so far. Safe on partially-initialized state. */
    public static void shutdown() {
        try (MemoryStack stack = MemoryStack.stackPush()) {
            if (swapchainImages != 0L) {
                Long.free(swapchainImages);
                swapchainImages = 0L;
            }
            if (swapchain != 0L && device != null && device.address() != 0L) {
                vkDestroySwapchainKHR(device, swapchain, null);
                swapchain = 0L;
            }
            if (surface != 0L && instance != null && instance.address() != 0L) {
                vkDestroySurfaceKHR(instance, surface, null);
                surface = 0L;
            }
            if (device != null && device.address() != 0L) {
                vkDestroyDevice(device, null);
                device = null;
            }
            if (debugMessenger != 0L && instance != null && instance.address() != 0L) {
                vkDestroyDebugUtilsMessengerEXT(instance, debugMessenger, null);
                debugMessenger = 0L;
            }
            if (instance != null && instance.address() != 0L) {
                vkDestroyInstance(instance, null);
                instance = null;
            }
        }
    }

    private static void configureValidationLoader() {
        if (!java.lang.System.getProperty(StringLookup.getJavaString(143)).toLowerCase().contains(StringLookup.getJavaString(144))) return;
        if (Configuration.VULKAN_LIBRARY_NAME.get() != null) return;

        String override = java.lang.System.getProperty(StringLookup.getJavaString(933));
        if (override == null || override.isBlank()) {
            if (java.nio.file.Files.exists(java.nio.file.Path.of(StringLookup.getJavaString(934)))) {
                override = StringLookup.getJavaString(934);
            } else if (java.nio.file.Files.exists(java.nio.file.Path.of(StringLookup.getJavaString(935)))) {
                override = StringLookup.getJavaString(935);
            }
        }
        if (override != null && !override.isBlank()) {
            Configuration.VULKAN_LIBRARY_NAME.set(override);
            System.out.println(StringLookup.getJavaString(936) + override);
        }
    }

    public static VkInstance getInstance() {
        return instance;
    }

    public static VkDevice getDevice() {
        return device;
    }

    public static VkPhysicalDevice getPhysicalDevice() {
        return physicalDevice;
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

    public static int getPresentMode() {
        return presentMode;
    }

    /**
     * Recreates the swapchain with a different present mode at runtime.
     * Must be called with the device idle and after the presentation attachments
     * (image views / framebuffers) that reference the old swapchain images are freed.
     */
    public static void setPresentMode(int mode) {
        if (device == null) return;
        vkDeviceWaitIdle(device);
        try (MemoryStack stack = MemoryStack.stackPush()) {
            if (swapchainImages != 0L) {
                Long.free(swapchainImages);
                swapchainImages = 0L;
            }
            initSwapchain(stack, swapchainWidth, swapchainHeight, mode);
        }
        applyLayerSync();
        System.out.println(StringLookup.getJavaString(937) + mode);
    }

    /** Recreates the swapchain at a new size, keeping the current present mode. Must be called with the device idle and free attachments first. */
    public static void resizeSwapchain(int width, int height) {
        if (device == null) return;
        // vkDeviceWaitIdle(device); // REMOVED FOR SEAMLESS LIVE RESIZE
        try (MemoryStack stack = MemoryStack.stackPush()) {
            if (swapchainImages != 0L) {
                Long.free(swapchainImages);
                swapchainImages = 0L;
            }
            initSwapchain(stack, width, height, presentMode);
        }
        applyLayerSync();
        System.out.println(StringLookup.getJavaString(938) + width + StringLookup.getJavaString(676) + height);
    }

    /** Propagates the swapchain vsync state onto the CAMetalLayer (displaySyncEnabled). */
    private static void applyLayerSync() {
        if (layerPointer == 0L) return;
        boolean synced = presentMode == VK_PRESENT_MODE_FIFO_KHR;
        window.Window.setDisplaySyncEnabled(layerPointer, synced);
    }

    private static void initInstance(MemoryStack stack) {
        // VK_EXT_debug_utils may be advertised by the validation layer rather than
        // by the bare loader, so do not reject the layer based on global extension
        // enumeration before the layer is enabled.
        boolean validationEnabled = hasInstanceLayer(stack, StringLookup.getJavaString(939));
        boolean portabilityEnabled = hasInstanceExtension(stack, StringLookup.getJavaString(940));

        VkApplicationInfo appInfo = VkApplicationInfo.calloc(stack)
                .sType(VK_STRUCTURE_TYPE_APPLICATION_INFO)
                .pApplicationName(stack.UTF8(StringLookup.getJavaString(941)))
                .applicationVersion(VK_MAKE_VERSION(1, 0, 0))
                .pEngineName(stack.UTF8(StringLookup.getJavaString(941)))
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
            extensions.put(stack.UTF8(StringLookup.getJavaString(940)));
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
                try(VkDebugUtilsMessengerCallbackDataEXT debug = VkDebugUtilsMessengerCallbackDataEXT.create(data))
                {
                    String message = debug.pMessageString();
                    String level = (severity & VK_DEBUG_UTILS_MESSAGE_SEVERITY_ERROR_BIT_EXT) != 0 ? StringLookup.getJavaString(942)
                            : (severity & VK_DEBUG_UTILS_MESSAGE_SEVERITY_WARNING_BIT_EXT) != 0 ? StringLookup.getJavaString(943)
                              : (severity & VK_DEBUG_UTILS_MESSAGE_SEVERITY_INFO_BIT_EXT) != 0 ? StringLookup.getJavaString(944) : StringLookup.getJavaString(945);
                    System.err.println(StringLookup.getJavaString(946) + level + StringLookup.getJavaString(947) + message);
                }
                catch(Exception e)
                {
                    throw new RuntimeException(StringLookup.getJavaString(948) + e);
                }
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
            layers.put(stack.UTF8(StringLookup.getJavaString(939))).flip();
        }
        createInfo.ppEnabledLayerNames(layers);

        PointerBuffer pInstance = stack.mallocPointer(1);
        if (VK10.vkCreateInstance(createInfo, null, pInstance) != VK_SUCCESS) {
            throw new RuntimeException(StringLookup.getJavaString(949));
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
                throw new RuntimeException(StringLookup.getJavaString(950));
            }
            debugMessenger = pMessenger.get(0);
            System.out.println(StringLookup.getJavaString(951));
        } else {
            System.out.println(StringLookup.getJavaString(952));
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
        layerPointer = caMetalLayer;
        VkMetalSurfaceCreateInfoEXT createInfo = VkMetalSurfaceCreateInfoEXT.calloc(stack)
                .sType(VK_STRUCTURE_TYPE_METAL_SURFACE_CREATE_INFO_EXT);
        MemoryUtil.memPutAddress(createInfo.address() + VkMetalSurfaceCreateInfoEXT.PLAYER, caMetalLayer);

        LongBuffer pSurface = stack.mallocLong(1);
        if (EXTMetalSurface.vkCreateMetalSurfaceEXT(instance, createInfo, null, pSurface) != VK_SUCCESS) {
            throw new RuntimeException(StringLookup.getJavaString(953));
        }
        surface = pSurface.get(0);
    }

    private static void initDevice(MemoryStack stack) {
        java.nio.IntBuffer pDeviceCount = stack.mallocInt(1);
        VK10.vkEnumeratePhysicalDevices(instance, pDeviceCount, null);
        if (pDeviceCount.get(0) == 0) throw new RuntimeException(StringLookup.getJavaString(954));

        PointerBuffer pDevices = stack.mallocPointer(pDeviceCount.get(0));
        VK10.vkEnumeratePhysicalDevices(instance, pDeviceCount, pDevices);
        physicalDevice = new VkPhysicalDevice(pDevices.get(0), instance);

        // Select a queue family supporting BOTH graphics and presentation instead
        // of assuming family 0 (breaks on drivers that expose a transfer-only family 0).
        java.nio.IntBuffer qCount = stack.mallocInt(1);
        VK10.vkGetPhysicalDeviceQueueFamilyProperties(physicalDevice, qCount, null);
        VkQueueFamilyProperties.Buffer queueProps = VkQueueFamilyProperties.calloc(Math.max(qCount.get(0), 1), stack);
        VK10.vkGetPhysicalDeviceQueueFamilyProperties(physicalDevice, qCount, queueProps);

        graphicsQueueFamilyIndex = -1;
        presentQueueFamilyIndex = -1;
        for (int i = 0; i < qCount.get(0); i++) {
            System.out.println(StringLookup.getJavaString(955) + i + StringLookup.getJavaString(956) + queueProps.get(i).queueFlags()
                    + StringLookup.getJavaString(957) + queueProps.get(i).queueCount());
            if ((queueProps.get(i).queueFlags() & VK_QUEUE_GRAPHICS_BIT) == 0) continue;
            java.nio.IntBuffer supported = stack.mallocInt(1);
            vkGetPhysicalDeviceSurfaceSupportKHR(physicalDevice, i, surface, supported);
            if (supported.get(0) == VK_TRUE) {
                graphicsQueueFamilyIndex = i;
                presentQueueFamilyIndex = i;
                break;
            }
        }
        if (graphicsQueueFamilyIndex < 0) {
            throw new RuntimeException(StringLookup.getJavaString(958));
        }

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

        boolean portabilitySupported = hasDeviceExtension(physicalDevice, stack, StringLookup.getJavaString(959));
        PointerBuffer extensions = stack.mallocPointer(portabilitySupported ? 2 : 1);
        extensions.put(stack.UTF8(VK_KHR_SWAPCHAIN_EXTENSION_NAME));
        if (portabilitySupported) {
            extensions.put(stack.UTF8(StringLookup.getJavaString(959)));
        }
        extensions.flip();

        VkDeviceCreateInfo createInfo = VkDeviceCreateInfo.calloc(stack)
                .sType(VK_STRUCTURE_TYPE_DEVICE_CREATE_INFO)
                .pNext(features2.address())
                .pQueueCreateInfos(queueCreateInfo)
                .ppEnabledExtensionNames(extensions);

        PointerBuffer pDevice = stack.mallocPointer(1);
        if (VK10.vkCreateDevice(physicalDevice, createInfo, null, pDevice) != VK_SUCCESS) {
            throw new RuntimeException(StringLookup.getJavaString(960));
        }
        device = new VkDevice(pDevice.get(0), physicalDevice, createInfo);

        PointerBuffer pQueue = stack.mallocPointer(1);
        VK10.vkGetDeviceQueue(device, graphicsQueueFamilyIndex, 0, pQueue);
        graphicsQueue = new VkQueue(pQueue.get(0), device);
        presentQueue = graphicsQueue;
    }

    @Intention("Best low-latency present mode selection. On macOS this is only honored in fullscreen; " +
            "a windowed CAMetalLayer is vsync-throttled to the display refresh rate regardless of the chosen mode.")
    @Citatiom(cite = 3)
    private static void initSwapchain(MemoryStack stack, int width, int height, int preferredMode) {
        swapchainWidth = width;
        swapchainHeight = height;

        // Query supported surface formats; prefer B8G8R8A8_SRGB, else the first offered.
        java.nio.IntBuffer formatCount = stack.mallocInt(1);
        vkGetPhysicalDeviceSurfaceFormatsKHR(physicalDevice, surface, formatCount, null);
        VkSurfaceFormatKHR.Buffer formats = VkSurfaceFormatKHR.calloc(Math.max(formatCount.get(0), 1), stack);
        vkGetPhysicalDeviceSurfaceFormatsKHR(physicalDevice, surface, formatCount, formats);
        int chosenFormat = 0;
        int chosenColorSpace = VK_COLOR_SPACE_SRGB_NONLINEAR_KHR;
        for (VkSurfaceFormatKHR fmt : formats) {
            if (fmt.format() == VK_FORMAT_B8G8R8A8_SRGB && fmt.colorSpace() == VK_COLOR_SPACE_SRGB_NONLINEAR_KHR) {
                chosenFormat = VK_FORMAT_B8G8R8A8_SRGB;
                break;
            }
            if (chosenFormat == 0) {
                chosenFormat = fmt.format();
                chosenColorSpace = fmt.colorSpace();
            }
        }
        if (chosenFormat == 0) throw new RuntimeException(StringLookup.getJavaString(961));
        swapchainFormat = chosenFormat;

        // Query supported present modes to find the best low-latency/un-throttled mode
        java.nio.IntBuffer modeCount = stack.mallocInt(1);
        vkGetPhysicalDeviceSurfacePresentModesKHR(physicalDevice, surface, modeCount, null);
        java.nio.IntBuffer modes = stack.mallocInt(modeCount.get(0));
        vkGetPhysicalDeviceSurfacePresentModesKHR(physicalDevice, surface, modeCount, modes);
        StringBuilder modeList = new StringBuilder(StringLookup.getJavaString(962));
        for (int i = 0; i < modeCount.get(0); i++) modeList.append(StringLookup.getJavaString(77)).append(modes.get(i));
        System.out.println(modeList);

        int chosenMode;
        if (preferredMode >= 0) {
            chosenMode = VK_PRESENT_MODE_FIFO_KHR; // default required fallback
            boolean foundPreferred = false;
            boolean foundMailbox = false;
            for (int i = 0; i < modeCount.get(0); i++) {
                int mode = modes.get(i);
                if (mode == preferredMode) foundPreferred = true;
                if (mode == VK_PRESENT_MODE_MAILBOX_KHR) foundMailbox = true;
            }
            if (foundPreferred) {
                chosenMode = preferredMode;
            } else if (preferredMode == VK_PRESENT_MODE_IMMEDIATE_KHR && foundMailbox) {
                // If IMMEDIATE is missing (common on MoltenVK), fallback to MAILBOX for uncapped rendering
                chosenMode = VK_PRESENT_MODE_MAILBOX_KHR;
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
        String modeLabel;
        if (chosenMode == VK_PRESENT_MODE_FIFO_KHR) modeLabel = StringLookup.getJavaString(963);
        else if (chosenMode == VK_PRESENT_MODE_MAILBOX_KHR) modeLabel = StringLookup.getJavaString(964);
        else if (chosenMode == VK_PRESENT_MODE_IMMEDIATE_KHR) modeLabel = StringLookup.getJavaString(965);
        else modeLabel = StringLookup.getJavaString(966) + chosenMode + StringLookup.getJavaString(18);
        System.out.println(StringLookup.getJavaString(967) + chosenMode + modeLabel);

        // Pass the current swapchain as oldSwapchain so the driver can hand over the
        // surface safely; destroying it before the new swapchain exists leaves the
        // semaphores/present queue in a state the validation layer flags as "still in use".
        long oldSwapchain = swapchain;
        VkSwapchainCreateInfoKHR createInfo = VkSwapchainCreateInfoKHR.calloc(stack)
                .sType(VK_STRUCTURE_TYPE_SWAPCHAIN_CREATE_INFO_KHR)
                .surface(surface)
                .minImageCount(3)
                .imageFormat(swapchainFormat)
                .imageColorSpace(chosenColorSpace)
                .imageArrayLayers(1)
                .imageUsage(VK_IMAGE_USAGE_COLOR_ATTACHMENT_BIT | VK_IMAGE_USAGE_TRANSFER_DST_BIT)
                .imageSharingMode(VK_SHARING_MODE_EXCLUSIVE)
                .preTransform(VK_SURFACE_TRANSFORM_IDENTITY_BIT_KHR)
                .compositeAlpha(VK_COMPOSITE_ALPHA_OPAQUE_BIT_KHR)
                .presentMode(chosenMode)
                .clipped(true)
                .oldSwapchain(oldSwapchain);
        
        createInfo.imageExtent().width(width).height(height);

        LongBuffer pSwapchain = stack.mallocLong(1);
        if (KHRSwapchain.vkCreateSwapchainKHR(device, createInfo, null, pSwapchain) != VK_SUCCESS) {
            throw new RuntimeException(StringLookup.getJavaString(909));
        }
        swapchain = pSwapchain.get(0);
        
        if (layerPointer != 0L) {
            window.Window.setSurfaceGravityTopLeft(layerPointer);
            // Keep the CAMetalLayer drawableSize exactly matched to the swapchain extent
            // after every (re)creation; a mismatch mid-drag presents a black/blank frame
            // on macOS (MoltenVK #2226).
            window.Window.setDrawableSize(layerPointer, width, height);
        }

        // The new swapchain has taken over the surface; defer destruction of the old handle.
        if (oldSwapchain != VK_NULL_HANDLE) {
            Renderer.pushGarbageSwapchain(oldSwapchain);
        }

        java.nio.IntBuffer pImageCount = stack.mallocInt(1);
        if (KHRSwapchain.vkGetSwapchainImagesKHR(device, swapchain, pImageCount, null) != VK_SUCCESS) {
            throw new RuntimeException(StringLookup.getJavaString(968));
        }
        swapchainImageCount = pImageCount.get(0);
        LongBuffer images = stack.mallocLong(swapchainImageCount);
        if (KHRSwapchain.vkGetSwapchainImagesKHR(device, swapchain, pImageCount, images) != VK_SUCCESS) {
            throw new RuntimeException(StringLookup.getJavaString(969));
        }

        swapchainImages = Long.allocateArray(swapchainImageCount);
        for (int i = 0; i < swapchainImageCount; i++) {
            Long.set(swapchainImages, i, images.get(i));
        }
    }
}
