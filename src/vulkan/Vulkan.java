package vulkan;


import org.lwjgl.system.MemoryStack;
import org.lwjgl.system.MemoryUtil;
import org.lwjgl.vulkan.*;
import org.lwjgl.PointerBuffer;
import java.nio.LongBuffer;

import static org.lwjgl.vulkan.EXTMetalSurface.*;
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
    private static long graphicsQueue;
    private static long presentQueue;
    private static long surface;
    private static long swapchain;
    
    private static int graphicsQueueFamilyIndex = -1;
    private static int presentQueueFamilyIndex = -1;

    private Vulkan() {}

    public static void initVulkan(long caMetalLayer, int windowWidth, int windowHeight) {
        System.out.println("Booting Hardcore Vulkan Subsystem...");
        try (MemoryStack stack = MemoryStack.stackPush()) {
            initInstance(stack);
            initSurface(stack, caMetalLayer);
            initDevice(stack);
            initSwapchain(stack, windowWidth, windowHeight);
        }
        System.out.println("Vulkan Swapchain ready.");
    }

    private static void initInstance(MemoryStack stack) {
        VkApplicationInfo appInfo = VkApplicationInfo.calloc(stack)
                .sType(VK_STRUCTURE_TYPE_APPLICATION_INFO)
                .pApplicationName(stack.UTF8("Anti Engine"))
                .applicationVersion(VK_MAKE_VERSION(1, 0, 0))
                .pEngineName(stack.UTF8("Anti Engine"))
                .engineVersion(VK_MAKE_VERSION(1, 0, 0))
                .apiVersion(VK_API_VERSION_1_2);

        PointerBuffer extensions = stack.mallocPointer(2);
        extensions.put(stack.UTF8(VK_KHR_SURFACE_EXTENSION_NAME));
        extensions.put(stack.UTF8(VK_EXT_METAL_SURFACE_EXTENSION_NAME));
        extensions.flip();

        VkInstanceCreateInfo createInfo = VkInstanceCreateInfo.calloc(stack)
                .sType(VK_STRUCTURE_TYPE_INSTANCE_CREATE_INFO)
                .pApplicationInfo(appInfo)
                .ppEnabledExtensionNames(extensions);

        PointerBuffer pInstance = stack.mallocPointer(1);
        if (VK10.vkCreateInstance(createInfo, null, pInstance) != VK_SUCCESS) {
            throw new RuntimeException("Failed to create Vulkan Instance.");
        }
        instance = new VkInstance(pInstance.get(0), createInfo);
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

        PointerBuffer extensions = stack.mallocPointer(1);
        extensions.put(stack.UTF8(VK_KHR_SWAPCHAIN_EXTENSION_NAME)).flip();

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
        graphicsQueue = pQueue.get(0);
        presentQueue = graphicsQueue;
    }

    private static void initSwapchain(MemoryStack stack, int width, int height) {
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
                .presentMode(VK_PRESENT_MODE_FIFO_KHR)
                .clipped(true)
                .oldSwapchain(VK_NULL_HANDLE);
        
        createInfo.imageExtent().width(width).height(height);

        LongBuffer pSwapchain = stack.mallocLong(1);
        if (KHRSwapchain.vkCreateSwapchainKHR(device, createInfo, null, pSwapchain) != VK_SUCCESS) {
            throw new RuntimeException("Failed to create Swapchain.");
        }
        swapchain = pSwapchain.get(0);
    }
}
