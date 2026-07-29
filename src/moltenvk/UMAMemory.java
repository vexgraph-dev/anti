package moltenvk;

import annotation.PlatformExclusive;
import annotation.Volatile;
import exception.macOSWindowException;
import org.lwjgl.vulkan.VkPhysicalDevice;
import org.lwjgl.vulkan.VkPhysicalDeviceMemoryProperties;
import static org.lwjgl.vulkan.VK10.*;

/**
 * Handles Apple Silicon Unified Memory Architecture (UMA) optimizations via MoltenVK.
 */
@PlatformExclusive("Mac")
@Volatile
public final class UMAMemory {

    private UMAMemory() {}

    /**
     * Finds the absolute best memory type index on a Mac.
     * Apple Silicon uses Unified Memory, which means we can find memory that is
     * BOTH Device Local (ultra-fast for GPU) AND Host Visible/Coherent (fast for CPU mapping).
     */
    public static int findOptimalUMAMemoryType(VkPhysicalDevice physicalDevice, int typeFilter) {
        VkPhysicalDeviceMemoryProperties memProperties = VkPhysicalDeviceMemoryProperties.calloc();
        vkGetPhysicalDeviceMemoryProperties(physicalDevice, memProperties);

        // We want memory that is Device Local AND Host Visible (The Holy Grail of UMA)
        int desiredProperties = VK_MEMORY_PROPERTY_DEVICE_LOCAL_BIT | 
                                VK_MEMORY_PROPERTY_HOST_VISIBLE_BIT | 
                                VK_MEMORY_PROPERTY_HOST_COHERENT_BIT;

        for (int i = 0; i < memProperties.memoryTypeCount(); i++) {
            if ((typeFilter & (1 << i)) != 0) {
                int properties = memProperties.memoryTypes(i).propertyFlags();
                if ((properties & desiredProperties) == desiredProperties) {
                    memProperties.free();
                    return i;
                }
            }
        }
        
        memProperties.free();
        throw new macOSWindowException("Failed to find optimal UMA memory type on this Mac!");
    }
}
