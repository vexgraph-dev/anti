package moltenvk;

import annotation.PlatformExclusive;
import annotation.Volatile;
import annotation.Required;

/**
 * Handles presenting a rendered Vulkan buffer directly to the OS window surface.
 * Specifically routes Vulkan calls through MoltenVK onto CAMetalLayer.
 */
@PlatformExclusive("Mac")
@Volatile
public final class WindowPresenter {
    
    private WindowPresenter() {}

    @Required
    public static void present(long commandBufferPtr, long windowPtr, long surfacePtr) {
        // Here we will do the Vulkan magic:
        // 1. vkEndCommandBuffer
        // 2. vkQueueSubmit
        // 3. vkQueuePresentKHR (using the swapchain tied to the CAMetalLayer surface)
    }
}
