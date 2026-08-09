package vulkan;

import annotation.Draft;
import io.Log;
import io.LogKind;
import org.lwjgl.system.MemoryStack;
import org.lwjgl.vulkan.VkCommandBuffer;
import org.lwjgl.vulkan.VkCommandBufferBeginInfo;
import org.lwjgl.vulkan.VkDevice;
import org.lwjgl.vulkan.VkImageBlit;
import org.lwjgl.vulkan.VkImageMemoryBarrier;
import org.lwjgl.vulkan.VkPresentInfoKHR;
import org.lwjgl.vulkan.VkQueue;
import org.lwjgl.vulkan.VkSubmitInfo;
import org.lwjgl.vulkan.VK10;
import thread.RingBuffer;

import java.nio.IntBuffer;
import java.util.concurrent.atomic.AtomicBoolean;
import primitive.Long;

import static org.lwjgl.vulkan.KHRSwapchain.*;
import static org.lwjgl.vulkan.VK10.*;

@Draft
public final class Renderer {

    /** Monotonic origin for the per-draw animation clock. */
    private static final long START_NANO = java.lang.System.nanoTime();

    public static final int MAX_FRAMES_IN_FLIGHT = 3;

    /** Software present cadence used to pace IMMEDIATE/uncapped present modes. */
    public static final int PRESENT_FPS = 60;

    private static final AtomicBoolean QUEUE_LOCK = new AtomicBoolean(true);

    private static long commandBuffersArray;
    private static long blitCommandBuffersArray;
    private static long drawFencesArray;
    private static long releasedFencesArray;
    private static long imageAvailableSemaphoresArray;
    private static long blitFinishedSemaphoresArray;
    private static int frameCount;

    private static long completedRing;
    private static int producerSlot;
    private static boolean initialized;
    private static java.util.concurrent.Semaphore slotSemaphore;
    private static boolean[] droppedFrames;

    private static volatile long drawCount;
    private static volatile long presentCount;
    private static final java.util.concurrent.locks.ReentrantReadWriteLock PRODUCER_LOCK = 
            new java.util.concurrent.locks.ReentrantReadWriteLock();

    // Serializes presentOnce() against swapchain teardown/recreation (resize, setPresentMode).
    // presentOnce takes the read lock for one present; resize/setPresentMode take the write lock
    // so no present can race vkDestroySwapchainKHR / vkQueueSubmit on the old swapchain.
    private static final java.util.concurrent.locks.ReentrantReadWriteLock PRESENT_LOCK =
            new java.util.concurrent.locks.ReentrantReadWriteLock();

    private static volatile Thread presentThread;
    private static volatile boolean presentThreadRunning;

    public static volatile long dbgAcquireNanos;
    public static volatile long dbgReleasedWaitNanos;
    public static volatile long dbgQueueLockNanos;
    public static volatile long dbgPresentBlockNanos;
    public static volatile long dbgPresentNotReady;
    public static volatile long dbgPresentSubmitNanos;
    public static volatile long dbgPresentCallNanos;
    public static volatile long dbgPresentThreadLoops;
    public static volatile long dbgPresentThreadParkMs;

    private Renderer() {}

    public static void init(VkDevice device, long drawCommandPoolPtr, long blitCommandPoolPtr, int frameSlots) {
        if (frameCount != 0) {
            destroy(device, drawCommandPoolPtr, blitCommandPoolPtr);
        }
        frameCount = frameSlots;
        commandBuffersArray = CommandBuffer.allocateArray(frameCount);
        blitCommandBuffersArray = CommandBuffer.allocateArray(frameCount);
        drawFencesArray = Fence.allocateArray(frameCount);
        releasedFencesArray = Fence.allocateArray(frameCount);
        imageAvailableSemaphoresArray = Semaphore.allocateArray(frameCount);
        blitFinishedSemaphoresArray = Semaphore.allocateArray(frameCount);

        completedRing = RingBuffer.instant(oop.TypeRegister.ID_LONG, 1024);
        slotSemaphore = new java.util.concurrent.Semaphore(frameCount);
        droppedFrames = new boolean[frameCount];

        for (int i = 0; i < frameCount; i++) {
            CommandBuffer.set(commandBuffersArray, i, CommandBuffer.create(device, drawCommandPoolPtr));
            CommandBuffer.set(blitCommandBuffersArray, i, CommandBuffer.create(device, blitCommandPoolPtr));

            Fence.set(drawFencesArray, i, Fence.create(device, false));
            Fence.set(releasedFencesArray, i, Fence.create(device, true));

            Semaphore.set(imageAvailableSemaphoresArray, i, Semaphore.create(device));
            Semaphore.set(blitFinishedSemaphoresArray, i, Semaphore.create(device));
        }
        initialized = true;
        startPresentThread();
    }

    /**
     * Spawns the dedicated present thread. It drains the completed-frame ring at the
     * swapchain's own pace (vblank in FIFO), while the Core Draw Worker produces
     * off-screen frames as fast as the FPS cap allows. Because draw and present share
     * a single GPU queue, this thread is what lets the draw rate decouple from vsync.
     * In IMMEDIATE mode the present call never blocks, so this thread enforces a
     * software 60Hz cadence via parkUntil — present stays display-paced while the
     * draw thread can run to its own cap.
     */
    private static void startPresentThread() {
        if (presentThread != null && presentThread.isAlive()) return;
        presentThreadRunning = true;
        final long presentPeriod = 1_000_000_000L / PRESENT_FPS;
        presentThread = new Thread(() -> {
            long deadline = java.lang.System.nanoTime() + presentPeriod;
            while (presentThreadRunning && initialized) {
                dbgPresentThreadLoops++;
                long t0 = java.lang.System.nanoTime();
                int status = presentOnce();
                if (status == PRESENT_IDLE) {
                    // Nothing to present yet: keep the 60Hz cadence, just skip the frame.
                    java.util.concurrent.locks.LockSupport.parkNanos(presentPeriod / 4);
                } else if (status == PRESENT_RETRY) {
                    // Swapchain image not free yet: sleep most of a frame period instead
                    // of hammering vkAcquireNextImageKHR.
                    java.util.concurrent.locks.LockSupport.parkNanos(2_000_000L);
                }
                // Software pace to 60Hz so IMMEDIATE presents at the display cadence.
                deadline += presentPeriod;
                long tPark = java.lang.System.nanoTime();
                window.Window.parkUntil(deadline);
                dbgPresentThreadParkMs += (java.lang.System.nanoTime() - tPark) / 1_000_000L;
            }
        }, "Core-Present");
        presentThread.setDaemon(true);
        presentThread.start();
    }

    private static void stopPresentThread() {
        presentThreadRunning = false;
        Thread t = presentThread;
        presentThread = null;
        if (t != null) {
            try {
                t.join(2_000L);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
            }
        }
    }

    public static void produceOnce() {
        if (!initialized) return;
        long tA0 = java.lang.System.nanoTime();
        try {
            slotSemaphore.acquire();
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            return;
        }
        dbgAcquireNanos += java.lang.System.nanoTime() - tA0;

        PRODUCER_LOCK.readLock().lock();
        try {
            int slot = producerSlot;
            producerSlot = (producerSlot + 1) % frameCount;

            VkDevice device = Vulkan.getDevice();
            long drawCb = getCommandBuffer(slot);
            long releasedF = Fence.get(Fence.get(releasedFencesArray, slot));
            long drawF = Fence.get(Fence.get(drawFencesArray, slot));

            try (MemoryStack stack = MemoryStack.stackPush()) {
                if (droppedFrames[slot]) {
                    vkResetFences(device, stack.longs(drawF));
                    droppedFrames[slot] = false;
                } else {
                    long tR0 = java.lang.System.nanoTime();
                    if (vkWaitForFences(device, stack.longs(releasedF), true, java.lang.Long.MAX_VALUE) != VK_SUCCESS) {
                        throw new IllegalStateException("produce: released fence wait failed");
                    }
                    dbgReleasedWaitNanos += java.lang.System.nanoTime() - tR0;
                    vkResetFences(device, stack.longs(releasedF, drawF));
                }

                long tL0 = java.lang.System.nanoTime();
                lockQueue();
                dbgQueueLockNanos += java.lang.System.nanoTime() - tL0;
                try {
                    // Re-record the draw CB with the current animation time so the visible frame
                    // advances at the DRAW rate, proving the draw thread is really running uncapped.
                    // (Under the queue lock so a main-thread resize() cannot race the re-record.)
                    float t = (float) ((java.lang.System.nanoTime() - START_NANO) / 1_000_000_000.0);
                    TriangleRenderer.recordDraw(slot, t);

                    VkQueue q = Vulkan.getGraphicsQueue();
                    VkSubmitInfo.Buffer sub = VkSubmitInfo.calloc(1, stack);
                    sub.get(0).sType(VK_STRUCTURE_TYPE_SUBMIT_INFO)
                            .pCommandBuffers(stack.pointers(drawCb));
                    if (VK10.vkQueueSubmit(q, sub, drawF) != VK_SUCCESS) {
                        throw new IllegalStateException("produce: draw submit failed");
                    }
                } finally {
                    unlockQueue();
                }

                RingBuffer.offer(completedRing, slot + 1L);
                ++drawCount;
                Log.append(LogKind.RENDER_PRODUCE, slot, drawCount);
            }
        } finally {
            PRODUCER_LOCK.readLock().unlock();
        }
    }

    /** Status returned by presentOnce(). */
    public static final int PRESENT_IDLE = 0;
    public static final int PRESENT_DONE = 1;
    public static final int PRESENT_RETRY = 2;

    public static int presentOnce() {
        if (!initialized) return PRESENT_IDLE;

        long latestSlotVal = 0L;
        VkDevice device = Vulkan.getDevice();

        PRESENT_LOCK.readLock().lock();
        try {
            return presentOnceLocked(device);
        } finally {
            PRESENT_LOCK.readLock().unlock();
        }
    }

    private static int presentOnceLocked(VkDevice device) {
        long latestSlotVal = 0L;

        // Drain the completed ring to the LATEST frame, but BOUND the number of drops.
        // The ring is only guaranteed to empty when the producer is slower than present;
        // with an uncapped draw thread the producer keeps it non-empty forever, so an
        // unbounded while(true) here livelocks the present thread inside this loop and it
        // never reaches the actual blit/present below. Capping the drain lets present
        // always present the newest frame while still discarding stale in-flight frames.
        int maxDrain = Math.max(1, frameCount);
        for (int drained = 0; drained < maxDrain; drained++) {
            long s = RingBuffer.poll(completedRing);
            if (s == 0L) break;
            if (latestSlotVal != 0L) {
                int dropSlot = (int) (latestSlotVal - 1L);
                long dropDrawF = Fence.get(Fence.get(drawFencesArray, dropSlot));

                try (MemoryStack stack = MemoryStack.stackPush()) {
                    // CONSUMER strictly waits on drawF, but NEVER resets it!
                    if (VK10.vkWaitForFences(device, stack.longs(dropDrawF), true, java.lang.Long.MAX_VALUE) != VK_SUCCESS) {
                        throw new IllegalStateException("present: dropped-frame draw wait failed");
                    }
                    droppedFrames[dropSlot] = true;
                    Log.append(LogKind.RENDER_DROPPED, dropSlot, 0L);
                    slotSemaphore.release();
                }
            }
            latestSlotVal = s;
        }
        if (latestSlotVal == 0L) return PRESENT_IDLE;

        int slot = (int) (latestSlotVal - 1L);
        long drawF = Fence.get(Fence.get(drawFencesArray, slot));
        long releasedF = Fence.get(Fence.get(releasedFencesArray, slot));
        long imageAvailable = Semaphore.get(Semaphore.get(imageAvailableSemaphoresArray, slot));

        try (MemoryStack stack = MemoryStack.stackPush()) {
            long tW0 = java.lang.System.nanoTime();
            if (vkWaitForFences(device, stack.longs(drawF), true, java.lang.Long.MAX_VALUE) != VK_SUCCESS) {
                throw new IllegalStateException("present: draw fence wait failed");
            }
            dbgPresentBlockNanos += java.lang.System.nanoTime() - tW0;

            while (!window.Window.OS_NATIVE_MUTEX.compareAndSet(false, true)) {
                Thread.onSpinWait();
            }
            try {
                IntBuffer imageIndex = stack.mallocInt(1);
                long swapchain = Vulkan.getSwapchain();

                // BLOCKING acquire (up to ~one frame period): the present thread parks here
                // waiting for a swapchain image to be freed at vsync, WITHOUT holding the queue
                // lock. This is what keeps the queue lock hold time tiny so the producer can
                // keep submitting draws while the present thread waits for the display.
                // In FIFO the swapchain only frees an image at the display refresh.
                int acquireResult = vkAcquireNextImageKHR(device, swapchain, 33_000_000L,
                        imageAvailable, VK_NULL_HANDLE, imageIndex);

                if (acquireResult == VK_ERROR_OUT_OF_DATE_KHR) {
                    droppedFrames[slot] = true;
                    Log.append(LogKind.RENDER_DROPPED, slot, 1L);
                    slotSemaphore.release();
                    return PRESENT_RETRY;
                }
                if (acquireResult == VK_NOT_READY || acquireResult == VK_TIMEOUT) {
                    // No swapchain image free yet (vsync pacing). Re-offer the frame so a later
                    // presentOnce can show it; the slot stays in flight and is NOT released here.
                    dbgPresentNotReady++;
                    RingBuffer.offer(completedRing, latestSlotVal);
                    return PRESENT_RETRY;
                }
                if (acquireResult != VK_SUCCESS && acquireResult != VK_SUBOPTIMAL_KHR) {
                    throw new IllegalStateException("present: acquire failed: " + acquireResult);
                }

                int imgIndex = imageIndex.get(0);
                // Index the blit/present signal semaphore by SWAPCHAIN IMAGE index, not slot.
                // The swapchain only returns image N for re-acquire after its previous present
                // completes, so blitFinished[imgIndex] can never be re-signaled while a present
                // still waits on it. (Slot-indexed semaphores break here: with 16 slots and 3
                // images, a slot's semaphore is re-signaled before the prior present retires.)
                long blitFinished = Semaphore.get(Semaphore.get(blitFinishedSemaphoresArray, imgIndex));
                long blitCb = getBlitCommandBuffer(slot);
                recordBlitCommandBuffer(stack, device, blitCb, slot, imgIndex);

                VkQueue q = Vulkan.getPresentQueue();
                VkSubmitInfo.Buffer submits = VkSubmitInfo.calloc(1, stack);
                submits.get(0).sType(VK_STRUCTURE_TYPE_SUBMIT_INFO)
                        .waitSemaphoreCount(1)
                        .pWaitSemaphores(stack.longs(imageAvailable))
                        .pWaitDstStageMask(stack.ints(VK_PIPELINE_STAGE_TRANSFER_BIT))
                        .pCommandBuffers(stack.pointers(blitCb))
                        .pSignalSemaphores(stack.longs(blitFinished));

                VkPresentInfoKHR presentInfo = VkPresentInfoKHR.calloc(stack);
                presentInfo.sType(VK_STRUCTURE_TYPE_PRESENT_INFO_KHR)
                        .pWaitSemaphores(stack.longs(blitFinished))
                        .swapchainCount(1)
                        .pSwapchains(stack.longs(swapchain))
                        .pImageIndices(stack.ints(imgIndex));

                lockQueue();
                try {
                    long tS0 = java.lang.System.nanoTime();
                    // Pass releasedF here so the Producer slot is unlocked the exact moment the blit finishes!
                    if (VK10.vkQueueSubmit(q, submits, releasedF) != VK_SUCCESS) {
                        throw new IllegalStateException("present: blit submit failed");
                    }
                    dbgPresentSubmitNanos += java.lang.System.nanoTime() - tS0;
                    long tP0 = java.lang.System.nanoTime();
                    int pres = vkQueuePresentKHR(q, presentInfo);
                    dbgPresentCallNanos += java.lang.System.nanoTime() - tP0;
                    if (pres != VK_SUCCESS && pres != VK_SUBOPTIMAL_KHR && pres != VK_ERROR_OUT_OF_DATE_KHR) {
                        throw new IllegalStateException("present: present failed: " + pres);
                    }
                } finally {
                    unlockQueue();
                }
                slotSemaphore.release();

                presentCount++;
                Log.append(LogKind.RENDER_PRESENT, slot, presentCount);
                return PRESENT_DONE;
            } finally {
                window.Window.OS_NATIVE_MUTEX.set(false);
            }
        }
    }

    private static void recordBlitCommandBuffer(MemoryStack stack, VkDevice device, long blitCb,
                                                int slot, int imgIndex) {
        VkCommandBuffer command = new VkCommandBuffer(blitCb, device);
        VkCommandBufferBeginInfo beginInfo = VkCommandBufferBeginInfo.calloc(stack)
                .sType(VK_STRUCTURE_TYPE_COMMAND_BUFFER_BEGIN_INFO);
        if (vkBeginCommandBuffer(command, beginInfo) != VK_SUCCESS) {
            throw new IllegalStateException("Failed to begin blit command buffer.");
        }

        long swapchainImage = Long.get(Vulkan.getSwapchainImages(), imgIndex);
        long offscreenImage = TriangleRenderer.getOffscreenImageHandle(slot);
        int srcW = Vulkan.getSwapchainWidth();
        int srcH = Vulkan.getSwapchainHeight();
        int dstW = Vulkan.getSwapchainWidth();
        int dstH = Vulkan.getSwapchainHeight();

        VkImageMemoryBarrier.Buffer pre = VkImageMemoryBarrier.calloc(1, stack);
        pre.get(0).sType(VK_STRUCTURE_TYPE_IMAGE_MEMORY_BARRIER)
                .oldLayout(VK_IMAGE_LAYOUT_UNDEFINED)
                .newLayout(VK_IMAGE_LAYOUT_TRANSFER_DST_OPTIMAL)
                .srcQueueFamilyIndex(VK_QUEUE_FAMILY_IGNORED)
                .dstQueueFamilyIndex(VK_QUEUE_FAMILY_IGNORED)
                .image(swapchainImage)
                .srcAccessMask(0)
                .dstAccessMask(VK_ACCESS_TRANSFER_WRITE_BIT);
        pre.get(0).subresourceRange()
                .aspectMask(VK_IMAGE_ASPECT_COLOR_BIT)
                .baseMipLevel(0)
                .levelCount(1)
                .baseArrayLayer(0)
                .layerCount(1);
        vkCmdPipelineBarrier(command,
                VK_PIPELINE_STAGE_TOP_OF_PIPE_BIT, VK_PIPELINE_STAGE_TRANSFER_BIT,
                0, null, null, pre);

        VkImageBlit.Buffer reg = VkImageBlit.calloc(1, stack);
        reg.get(0).srcSubresource().set(VK_IMAGE_ASPECT_COLOR_BIT, 0, 0, 1);
        reg.get(0).dstSubresource().set(VK_IMAGE_ASPECT_COLOR_BIT, 0, 0, 1);
        reg.get(0).srcOffsets(0).set(0, 0, 0);
        reg.get(0).srcOffsets(1).set(srcW, srcH, 1);
        reg.get(0).dstOffsets(0).set(0, 0, 0);
        reg.get(0).dstOffsets(1).set(dstW, dstH, 1);
        vkCmdBlitImage(command,
                offscreenImage, VK_IMAGE_LAYOUT_TRANSFER_SRC_OPTIMAL,
                swapchainImage, VK_IMAGE_LAYOUT_TRANSFER_DST_OPTIMAL,
                reg, VK_FILTER_LINEAR);

        VkImageMemoryBarrier.Buffer post = VkImageMemoryBarrier.calloc(1, stack);
        post.get(0).sType(VK_STRUCTURE_TYPE_IMAGE_MEMORY_BARRIER)
                .oldLayout(VK_IMAGE_LAYOUT_TRANSFER_DST_OPTIMAL)
                .newLayout(VK_IMAGE_LAYOUT_PRESENT_SRC_KHR)
                .srcQueueFamilyIndex(VK_QUEUE_FAMILY_IGNORED)
                .dstQueueFamilyIndex(VK_QUEUE_FAMILY_IGNORED)
                .image(swapchainImage)
                .srcAccessMask(VK_ACCESS_TRANSFER_WRITE_BIT)
                .dstAccessMask(0);
        post.get(0).subresourceRange()
                .aspectMask(VK_IMAGE_ASPECT_COLOR_BIT)
                .baseMipLevel(0)
                .levelCount(1)
                .baseArrayLayer(0)
                .layerCount(1);
        vkCmdPipelineBarrier(command,
                VK_PIPELINE_STAGE_TRANSFER_BIT, VK_PIPELINE_STAGE_BOTTOM_OF_PIPE_BIT,
                0, null, null, post);

        if (vkEndCommandBuffer(command) != VK_SUCCESS) {
            throw new IllegalStateException("Failed to end blit command buffer.");
        }
    }

    public static void pauseProducer() { PRODUCER_LOCK.writeLock().lock(); }
    public static void resumeProducer() { PRODUCER_LOCK.writeLock().unlock(); }

    // Blocks until any in-flight presentOnce() finishes, then keeps the present thread
    // out while the swapchain is being torn down/recreated. Must pair with resumePresent().
    public static void pausePresent() { PRESENT_LOCK.writeLock().lock(); }
    public static void resumePresent() { PRESENT_LOCK.writeLock().unlock(); }

    private static void lockQueue() {
        while (!QUEUE_LOCK.compareAndSet(true, false)) {
            Thread.onSpinWait();
        }
    }

    private static void unlockQueue() {
        QUEUE_LOCK.set(true);
    }

    public static long getCommandBuffer(int index) {
        long cbPtr = CommandBuffer.get(commandBuffersArray, index);
        return CommandBuffer.get(cbPtr);
    }

    private static long getBlitCommandBuffer(int index) {
        long blitCbPtr = CommandBuffer.get(blitCommandBuffersArray, index);
        return CommandBuffer.get(blitCbPtr);
    }

    public static int getFrameCount() { return frameCount; }
    public static long getDrawCount() { return drawCount; }
    public static long getPresentCount() { return presentCount; }

    public static void resetInFlight() {
        if (completedRing == 0L) return;
        VkDevice device = Vulkan.getDevice();

        for (int i = 0; i < frameCount; i++) {
            long oldAvail = Semaphore.get(imageAvailableSemaphoresArray, i);
            long oldBlit = Semaphore.get(blitFinishedSemaphoresArray, i);
            Semaphore.destroy(oldAvail, device);
            Semaphore.destroy(oldBlit, device);
            Semaphore.set(imageAvailableSemaphoresArray, i, Semaphore.create(device));
            Semaphore.set(blitFinishedSemaphoresArray, i, Semaphore.create(device));
        }

        while (completedRing != 0L) {
            long s = RingBuffer.poll(completedRing);
            if (s == 0L) break;
            int dropSlot = (int) (s - 1L);
            long dropDrawF = Fence.get(Fence.get(drawFencesArray, dropSlot));
            try (MemoryStack stack = MemoryStack.stackPush()) {
                if (VK10.vkWaitForFences(device, stack.longs(dropDrawF), true, java.lang.Long.MAX_VALUE) != VK_SUCCESS) {
                    throw new IllegalStateException("resetInFlight: dropped-frame draw wait failed");
                }
                droppedFrames[dropSlot] = true;
                slotSemaphore.release();
            }
        }
    }

    public static void destroy(VkDevice device, long drawCommandPoolPtr, long blitCommandPoolPtr) {
        if (commandBuffersArray == 0L) return;
        stopPresentThread();
        if (device != null && device.address() != 0L) {
            VK10.vkDeviceWaitIdle(device);
        }
        for (int i = 0; i < frameCount; i++) {
            CommandBuffer.destroy(CommandBuffer.get(commandBuffersArray, i), device, drawCommandPoolPtr);
            CommandBuffer.destroy(CommandBuffer.get(blitCommandBuffersArray, i), device, blitCommandPoolPtr);
            Fence.destroy(Fence.get(drawFencesArray, i), device);
            Fence.destroy(Fence.get(releasedFencesArray, i), device);
            Semaphore.destroy(Semaphore.get(imageAvailableSemaphoresArray, i), device);
            Semaphore.destroy(Semaphore.get(blitFinishedSemaphoresArray, i), device);
        }
        CommandBuffer.free(commandBuffersArray);
        CommandBuffer.free(blitCommandBuffersArray);
        Fence.free(drawFencesArray);
        Fence.free(releasedFencesArray);
        Semaphore.free(imageAvailableSemaphoresArray);
        Semaphore.free(blitFinishedSemaphoresArray);
        RingBuffer.free(completedRing);

        commandBuffersArray = 0L;
        blitCommandBuffersArray = 0L;
        completedRing = 0L;
        frameCount = 0;
        initialized = false;
    }
}