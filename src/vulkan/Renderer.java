package vulkan;

import annotation.Draft;
import org.lwjgl.vulkan.VkDevice;

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
    
    private static int currentFrame = 0;

    private Renderer() {}

    /**
     * Initializes the Frames in Flight synchronization structures.
     */
    public static void init(VkDevice device, long commandPoolPtr) {
        // Allocate raw off-heap arrays (Length: 3)
        commandBuffersArray = CommandBuffer.allocateArray(MAX_FRAMES_IN_FLIGHT);
        imageAvailableSemaphoresArray = Semaphore.allocateArray(MAX_FRAMES_IN_FLIGHT);
        renderFinishedSemaphoresArray = Semaphore.allocateArray(MAX_FRAMES_IN_FLIGHT);
        inFlightFencesArray = Fence.allocateArray(MAX_FRAMES_IN_FLIGHT);

        // Populate the arrays with singleton pointers
        for (int i = 0; i < MAX_FRAMES_IN_FLIGHT; i++) {
            CommandBuffer.set(commandBuffersArray, i, CommandBuffer.create(device, commandPoolPtr));
            
            Semaphore.set(imageAvailableSemaphoresArray, i, Semaphore.create(device));
            Semaphore.set(renderFinishedSemaphoresArray, i, Semaphore.create(device));
            
            // Fences are created in a SIGNALED state so the first frame doesn't wait indefinitely
            Fence.set(inFlightFencesArray, i, Fence.create(device, true)); 
        }
    }

    /**
     * The core AAA render loop. 
     * Handles waiting on Fences, acquiring Swapchain images, recording commands, and Presenting.
     */
    public static void drawFrame() {
        // We will implement this next:
        // 1. Fetch current frame's Fence pointer: Fence.get(inFlightFencesArray, currentFrame)
        // 2. Wait for it: vkWaitForFences
        // 3. Reset it: vkResetFences
        // 4. Acquire Swapchain Image
        // 5. Record Command Buffer
        // 6. Submit to Queue
        // 7. Present to Queue
        
        currentFrame = (currentFrame + 1) % MAX_FRAMES_IN_FLIGHT;
    }

    public static void destroy(VkDevice device, long commandPoolPtr) {
        if (commandBuffersArray == 0L) return;

        // Destroy inner singletons
        for (int i = 0; i < MAX_FRAMES_IN_FLIGHT; i++) {
            CommandBuffer.destroy(CommandBuffer.get(commandBuffersArray, i), device, commandPoolPtr);
            Semaphore.destroy(Semaphore.get(imageAvailableSemaphoresArray, i), device);
            Semaphore.destroy(Semaphore.get(renderFinishedSemaphoresArray, i), device);
            Fence.destroy(Fence.get(inFlightFencesArray, i), device);
        }

        // Free the arrays themselves back to the lockless pool
        CommandBuffer.free(commandBuffersArray);
        Semaphore.free(imageAvailableSemaphoresArray);
        Semaphore.free(renderFinishedSemaphoresArray);
        Fence.free(inFlightFencesArray);
        
        commandBuffersArray = 0L;
    }
}
