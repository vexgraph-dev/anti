package vulkan;

import annotation.Draft;
import org.lwjgl.system.MemoryStack;
import org.lwjgl.vulkan.VkDevice;
import org.lwjgl.vulkan.VkPresentInfoKHR;
import org.lwjgl.vulkan.VkSubmitInfo;
import org.lwjgl.vulkan.VK10;

import java.nio.IntBuffer;
import java.nio.LongBuffer;
import primitive.Long;

import static org.lwjgl.vulkan.KHRSwapchain.*;
import static org.lwjgl.vulkan.VK10.*;

/**
 * Handles the Triple-Buffered Frames-in-Flight render loop.
 * Operates purely on Lockless Off-Heap Engine Pointers.
 */
@Draft
public final class Renderer {

    public static final int MAX_FRAMES_IN_FLIGHT = 3;

    // These hold off-heap pointers to arrays of pointers
    private static long commandBuffersArray;
    private static long imageAvailableSemaphoresArray;
    private static long renderFinishedSemaphoresArray;
    private static long inFlightFencesArray;
    private static int commandBufferCount;

    // Off-heap tracker mapping swapchain images -> in-flight fence handles.
    private static long imagesInFlight;

    private static int currentFrame = 0;
    private static volatile long framesPresented;

    private Renderer() {}

    /**
     * Initializes the Frames in Flight synchronization structures.
     */
    public static void init(VkDevice device, long commandPoolPtr) {
        // Allocate raw off-heap arrays
        commandBufferCount = Vulkan.getSwapchainImageCount();
        commandBuffersArray = CommandBuffer.allocateArray(commandBufferCount);
        imageAvailableSemaphoresArray = Semaphore.allocateArray(MAX_FRAMES_IN_FLIGHT);
        renderFinishedSemaphoresArray = Semaphore.allocateArray(MAX_FRAMES_IN_FLIGHT);
        inFlightFencesArray = Fence.allocateArray(MAX_FRAMES_IN_FLIGHT);

        // Setup synchronization tracker
        imagesInFlight = Long.allocateArray(commandBufferCount);
        for (int i = 0; i < commandBufferCount; i++) {
            Long.set(imagesInFlight, i, VK_NULL_HANDLE);
        }

        // Populate the arrays with singleton pointers
        for (int i = 0; i < commandBufferCount; i++) {
            CommandBuffer.set(commandBuffersArray, i, CommandBuffer.create(device, commandPoolPtr));
        }
        for (int i = 0; i < MAX_FRAMES_IN_FLIGHT; i++) {
            Semaphore.set(imageAvailableSemaphoresArray, i, Semaphore.create(device));
            Semaphore.set(renderFinishedSemaphoresArray, i, Semaphore.create(device));
            // Fences are created in a SIGNALED state so the first frame doesn't wait indefinitely
            Fence.set(inFlightFencesArray, i, Fence.create(device, true));
        }
    }

    /**
     * The core AAA render loop.
     */
    public static void drawFrame() {
        VkDevice device = Vulkan.getDevice();
        long fencePtr    = Fence.get(inFlightFencesArray, currentFrame);
        long fence       = Fence.get(fencePtr);
        long imageAvailable = Semaphore.get(Semaphore.get(imageAvailableSemaphoresArray, currentFrame));
        long renderFinished = Semaphore.get(Semaphore.get(renderFinishedSemaphoresArray, currentFrame));

        try (MemoryStack stack = MemoryStack.stackPush()) {
            LongBuffer pFence = stack.longs(fence);

            // Step 1: Wait for the previous frame using this slot's fence.
            int waitResult = vkWaitForFences(device, pFence, true, java.lang.Long.MAX_VALUE);
            if (waitResult != VK_SUCCESS) {
                throw new IllegalStateException("Failed waiting for frame fence: " + waitResult);
            }

            // Step 2: Acquire under the same native gate as AppKit event polling.
            IntBuffer imageIndex = stack.mallocInt(1);
            long swapchain;
            int acquireResult;
            while (!window.Window.OS_NATIVE_MUTEX.compareAndSet(false, true)) {
                Thread.onSpinWait();
            }
            try {
                swapchain = Vulkan.getSwapchain();
                acquireResult = vkAcquireNextImageKHR(device, swapchain, Long.MAX_VALUE,
                        imageAvailable, VK_NULL_HANDLE, imageIndex);
            } finally {
                window.Window.OS_NATIVE_MUTEX.set(false);
            }
            if (acquireResult != VK_SUCCESS && acquireResult != VK_SUBOPTIMAL_KHR) {
                throw new IllegalStateException("Failed acquiring swapchain image: " + acquireResult);
            }

            int imgIndex = imageIndex.get(0);

            // Step 3: Wait if a previous frame is still using this specific swapchain image
            long imageFence = Long.get(imagesInFlight, imgIndex);
            if (imageFence != VK_NULL_HANDLE && imageFence != fence) {
                int imageWaitResult = vkWaitForFences(
                        device, stack.longs(imageFence), true, java.lang.Long.MAX_VALUE);
                if (imageWaitResult != VK_SUCCESS) {
                    throw new IllegalStateException("Failed waiting for swapchain image fence: " + imageWaitResult);
                }
            }
            // Map the image to the current frame's fence
            Long.set(imagesInFlight, imgIndex, fence);

            // Step 4: Reset the fence AFTER all wait checks to prevent engine deadlock
            int resetResult = vkResetFences(device, pFence);
            if (resetResult != VK_SUCCESS) {
                throw new IllegalStateException("Failed resetting frame fence: " + resetResult);
            }

            // Step 5: Populate VkSubmitInfo and submit
            long commandBuffer = getCommandBuffer(imgIndex);
            org.lwjgl.vulkan.VkQueue graphicsQueue = Vulkan.getGraphicsQueue();

            VkSubmitInfo.Buffer submitInfos = VkSubmitInfo.calloc(1, stack);
            VkSubmitInfo submitInfo = submitInfos.get(0);
            submitInfo.sType(VK_STRUCTURE_TYPE_SUBMIT_INFO);
            // LWJGL won't auto-size these counts because each one dictates the length of multiple
            // arrays (waitSemaphoreCount drives pWaitSemaphores AND pWaitDstStageMask). After calloc
            // they are 0, which makes Vulkan silently ignore the acquire wait below.
            submitInfo.waitSemaphoreCount(1); // not auto-sized: dictates pWaitSemaphores AND pWaitDstStageMask
            submitInfo.pWaitSemaphores(stack.longs(imageAvailable));
            submitInfo.pWaitDstStageMask(stack.ints(VK_PIPELINE_STAGE_COLOR_ATTACHMENT_OUTPUT_BIT));
            submitInfo.pCommandBuffers(stack.pointers(commandBuffer));
            submitInfo.pSignalSemaphores(stack.longs(renderFinished));

            while (!window.Window.OS_NATIVE_MUTEX.compareAndSet(false, true)) {
                Thread.onSpinWait();
            }
            try {
                int submitResult = VK10.vkQueueSubmit(graphicsQueue, submitInfos, fence);
                if (submitResult != VK_SUCCESS) {
                    throw new IllegalStateException("Failed submitting command buffer: " + submitResult);
                }

                // Step 6: Present
                VkPresentInfoKHR presentInfo = VkPresentInfoKHR.calloc(stack);
presentInfo.sType(VK_STRUCTURE_TYPE_PRESENT_INFO_KHR);
                presentInfo.pWaitSemaphores(stack.longs(renderFinished));
                presentInfo.swapchainCount(1); // not auto-dimensional: dictates pSwapchains + pImageIndices + pResults
                presentInfo.pSwapchains(stack.longs(swapchain));
                presentInfo.pImageIndices(imageIndex);

                org.lwjgl.vulkan.VkQueue presentQueue = Vulkan.getPresentQueue();
                int presentResult = vkQueuePresentKHR(presentQueue, presentInfo);
                if (presentResult != VK_SUCCESS && presentResult != VK_SUBOPTIMAL_KHR) {
                    throw new IllegalStateException("Failed presenting image: " + presentResult);
                }
            } finally {
                window.Window.OS_NATIVE_MUTEX.set(false);
            }

            framesPresented++;
        }

        currentFrame = (currentFrame + 1) % MAX_FRAMES_IN_FLIGHT;
    }

    public static long getFramesPresented() {
        return framesPresented;
    }

    /**
     * Clears the per-swapchain-image fence bookkeeping and resets the frame counter.
     * Called after a swapchain recreation so stale image->fence mappings from the
     * previous swapchain don't collide with the newly rebuilt attachments.
     */
    public static void resetInFlight() {
        if (imagesInFlight == 0L) return;
        for (int i = 0; i < commandBufferCount; i++) {
            Long.set(imagesInFlight, i, VK_NULL_HANDLE);
        }
        currentFrame = 0;
    }

    public static long getCommandBuffer(int index) {
        long commandBufferPtr = CommandBuffer.get(commandBuffersArray, index);
        return CommandBuffer.get(commandBufferPtr);
    }

    public static void destroy(VkDevice device, long commandPoolPtr) {
        if (commandBuffersArray == 0L) return;

        for (int i = 0; i < commandBufferCount; i++) {
            CommandBuffer.destroy(CommandBuffer.get(commandBuffersArray, i), device, commandPoolPtr);
        }
        for (int i = 0; i < MAX_FRAMES_IN_FLIGHT; i++) {
            Semaphore.destroy(Semaphore.get(imageAvailableSemaphoresArray, i), device);
            Semaphore.destroy(Semaphore.get(renderFinishedSemaphoresArray, i), device);
            Fence.destroy(Fence.get(inFlightFencesArray, i), device);
        }

        CommandBuffer.free(commandBuffersArray);
        Semaphore.free(imageAvailableSemaphoresArray);
        Semaphore.free(renderFinishedSemaphoresArray);
        Fence.free(inFlightFencesArray);
        Long.free(imagesInFlight);

        commandBuffersArray = 0L;
        imagesInFlight = 0L;
        commandBufferCount = 0;
    }
}
