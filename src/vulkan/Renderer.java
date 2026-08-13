package vulkan;

import annotation.Draft;
import annotation.Intention;
import io.Log;
import io.LogKind;
import org.lwjgl.system.MemoryStack;
import org.lwjgl.vulkan.VkCommandBuffer;
import org.lwjgl.vulkan.VkCommandBufferBeginInfo;
import org.lwjgl.vulkan.VkDevice;
import org.lwjgl.vulkan.VkImageBlit;
import org.lwjgl.vulkan.VkImageMemoryBarrier;
import org.lwjgl.vulkan.VkImageSubresourceLayers;
import org.lwjgl.vulkan.VkImageSubresourceRange;
import org.lwjgl.vulkan.VkOffset3D;
import org.lwjgl.vulkan.VkPresentInfoKHR;
import org.lwjgl.vulkan.VkQueue;
import org.lwjgl.vulkan.VkSubmitInfo;
import org.lwjgl.vulkan.VK10;
import nio.ForeignMemory;
import thread.RingBuffer;
import thread.SpinLock;

import java.nio.IntBuffer;
import java.util.concurrent.locks.LockSupport;
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

    /**
     * Set by the draw thread while the window size is changing (a live drag). While
     * true, the present thread skips its software 60Hz park and fires presents as soon
     * as a completed frame is available, so the swapchain chases the live window size
     * instead of sampling it at the capped cadence.
     */
    public static volatile boolean liveResize;

    /**
     * Packed (w&lt;&lt;32)|h requested by the draw thread when the window content size
     * changed; 0 = none. Written by the draw thread, consumed by the present thread
     * at the top of presentOnceLocked, which owns the swapchain rebuild. Volatile so
     * the write is visible without the draw thread ever blocking on swapchain work.
     */
    public static volatile long pendingResize;

    /**
     * Set when acquire or present returns OUT_OF_DATE/SUBOPTIMAL so the present
     * thread rebuilds the swapchain on its next iteration instead of dropping
     * frames against a stale chain forever (the "stuck black" bug).
     */
    private static volatile boolean swapchainInvalidated;

    /** Publishes a swapchain resize request. The present thread performs the rebuild. */
    public static void requestResize(int w, int h) {
        pendingResize = ((long) w << 32) | (h & 0xFFFFFFFFL);
    }

    private static long queueLock; // off-heap SpinLock serializing the single GPU queue

    private static long commandBuffersArray;
    private static long blitCommandBuffersArray; // (slot x swapchain image) grid, see rebuildBlitCommandBuffers
    private static long blitPoolPtr;
    private static int blitImgCount;
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

    // --- Off-heap telemetry/count block -------------------------------------
    // All counters are ATOMIC RMW on off-heap memory (see ForeignMemory.getAndAddLong),
    // never `volatile` field RMW (which is non-atomic — §14.3).
    private static final int CTR_DRAW_COUNT = 0;
    private static final int CTR_PRESENT_COUNT = 1;
    private static final int CTR_DBG_ACQUIRE_NANOS = 2;
    private static final int CTR_DBG_RELEASED_WAIT_NANOS = 3;
    private static final int CTR_DBG_QUEUE_LOCK_NANOS = 4;
    private static final int CTR_DBG_PRESENT_BLOCK_NANOS = 5;
    private static final int CTR_DBG_PRESENT_NOT_READY = 6;
    private static final int CTR_DBG_PRESENT_SUBMIT_NANOS = 7;
    private static final int CTR_DBG_PRESENT_CALL_NANOS = 8;
    private static final int CTR_DBG_PRESENT_THREAD_LOOPS = 9;
    private static final int CTR_DBG_PRESENT_THREAD_PARK_MS = 10;
    private static final int CTR_DBG_BLIT_RECORD_NANOS = 11;
    private static final int CTR_COUNT = 12;
    private static long countersArray;

    private static long garbageSemaphoresArray;
    private static long garbageCommandBuffersArray;
    private static long garbageSwapchainsArray;
    private static long garbageFrameTagsArray;
    private static int garbageHead = 0;
    private static int garbageTail = 0;
    private static final int MAX_GARBAGE = 1024;

    private static long ctrAddr(int ctr) {
        return countersArray + ctr * 8L;
    }

    /** Atomic off-heap RMW add; returns the PREVIOUS value. */
    private static long counterAdd(int ctr, long delta) {
        return ForeignMemory.getAndAddLong(ctrAddr(ctr), delta);
    }

    private static long counterGet(int ctr) {
        return ForeignMemory.getVolatileLong(ctrAddr(ctr));
    }

    private static void counterResetAll() {
        for (int i = 0; i < CTR_COUNT; i++) {
            ForeignMemory.setVolatileLong(ctrAddr(i), 0L);
        }
    }

    private static final java.util.concurrent.locks.ReentrantReadWriteLock PRODUCER_LOCK = 
            new java.util.concurrent.locks.ReentrantReadWriteLock();

    // Serializes presentOnce() against swapchain teardown/recreation (resize, setPresentMode).
    // presentOnce takes the read lock for one present; resize/setPresentMode take the write lock
    // so no present can race vkDestroySwapchainKHR / vkQueueSubmit on the old swapchain.
    private static final java.util.concurrent.locks.ReentrantReadWriteLock PRESENT_LOCK =
            new java.util.concurrent.locks.ReentrantReadWriteLock();

    private static volatile Thread presentThread;
    private static volatile boolean presentThreadRunning;

    private Renderer() {}

    public static void init(VkDevice device, long drawCommandPoolPtr, long blitCommandPoolPtr, int frameSlots) {
        if (frameCount != 0) {
            destroy(device, drawCommandPoolPtr, blitCommandPoolPtr);
        }
        frameCount = frameSlots;
        commandBuffersArray = CommandBuffer.allocateArray(frameCount);
        blitPoolPtr = blitCommandPoolPtr;
        drawFencesArray = Fence.allocateArray(frameCount);
        releasedFencesArray = Fence.allocateArray(frameCount);
        imageAvailableSemaphoresArray = Semaphore.allocateArray(frameCount);
        blitFinishedSemaphoresArray = Semaphore.allocateArray(frameCount);

        completedRing = RingBuffer.instant(oop.TypeRegister.ID_LONG, 1024);
        slotSemaphore = new java.util.concurrent.Semaphore(frameCount);
        droppedFrames = new boolean[frameCount];
        countersArray = ForeignMemory.allocateNative(CTR_COUNT * 8L);
        queueLock = SpinLock.allocate();
        counterResetAll();
        garbageSemaphoresArray = ForeignMemory.allocateNative(MAX_GARBAGE * 8L);
        garbageCommandBuffersArray = ForeignMemory.allocateNative(MAX_GARBAGE * 8L);
        garbageSwapchainsArray = ForeignMemory.allocateNative(MAX_GARBAGE * 8L);
        garbageFrameTagsArray = ForeignMemory.allocateNative(MAX_GARBAGE * 8L);
        garbageHead = 0;
        garbageTail = 0;

        for (int i = 0; i < frameCount; i++) {
            CommandBuffer.set(commandBuffersArray, i, CommandBuffer.create(device, drawCommandPoolPtr));

            Fence.set(drawFencesArray, i, Fence.create(device, false));
            Fence.set(releasedFencesArray, i, Fence.create(device, true));

            Semaphore.set(imageAvailableSemaphoresArray, i, Semaphore.create(device));
            Semaphore.set(blitFinishedSemaphoresArray, i, Semaphore.create(device));
        }
        initialized = true;
        rebuildBlitCommandBuffers(device);
        startPresentThread();
    }

    private static void pushGarbage(long sem, long cb, long retireTag) {
        if ((garbageTail + 1) % MAX_GARBAGE == garbageHead) return;
        int idx = garbageTail;
        ForeignMemory.setVolatileLong(garbageSemaphoresArray + idx * 8L, sem);
        ForeignMemory.setVolatileLong(garbageCommandBuffersArray + idx * 8L, cb);
        ForeignMemory.setVolatileLong(garbageSwapchainsArray + idx * 8L, 0L);
        ForeignMemory.setVolatileLong(garbageFrameTagsArray + idx * 8L, retireTag);
        garbageTail = (garbageTail + 1) % MAX_GARBAGE;
    }

    public static void pushGarbageSwapchain(long swapchain) {
        if ((garbageTail + 1) % MAX_GARBAGE == garbageHead) return;
        int idx = garbageTail;
        // Retire by PRESENT count: the old swapchain may still be referenced by the
        // presentation engine, and presents complete at display rate (not draw rate).
        long retireTag = counterGet(CTR_PRESENT_COUNT) + frameCount + 2;
        ForeignMemory.setVolatileLong(garbageSemaphoresArray + idx * 8L, 0L);
        ForeignMemory.setVolatileLong(garbageCommandBuffersArray + idx * 8L, 0L);
        ForeignMemory.setVolatileLong(garbageSwapchainsArray + idx * 8L, swapchain);
        ForeignMemory.setVolatileLong(garbageFrameTagsArray + idx * 8L, retireTag);
        garbageTail = (garbageTail + 1) % MAX_GARBAGE;
    }

    private static void processGarbage(VkDevice device) {
        long currentFrame = counterGet(CTR_PRESENT_COUNT);
        while (garbageHead != garbageTail) {
            long tag = ForeignMemory.getVolatileLong(garbageFrameTagsArray + garbageHead * 8L);
            if (currentFrame >= tag) {
                long sem = ForeignMemory.getVolatileLong(garbageSemaphoresArray + garbageHead * 8L);
                long cb = ForeignMemory.getVolatileLong(garbageCommandBuffersArray + garbageHead * 8L);
                long swp = ForeignMemory.getVolatileLong(garbageSwapchainsArray + garbageHead * 8L);
                if (sem != 0L) Semaphore.destroy(sem, device);
                if (cb != 0L) CommandBuffer.destroy(cb, device, blitPoolPtr);
                if (swp != 0L) vkDestroySwapchainKHR(device, swp, null);
                garbageHead = (garbageHead + 1) % MAX_GARBAGE;
            } else {
                break;
            }
        }
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
        presentThread = new Thread(() ->
        {
            long deadline = java.lang.System.nanoTime() + presentPeriod;
            boolean pwtActive = false; // CAMetalLayer.presentsWithTransaction state

            while (presentThreadRunning && initialized)
            {
                counterAdd(CTR_DBG_PRESENT_THREAD_LOOPS, 1L);
                // presentsWithTransaction must be YES only while the OS is live-resizing
                // (AppKit commits a CA transaction each resize tick, so the sync present
                // cannot miss the frame); when idle our thread parks without committing
                // transactions, so a stale YES would stall presentDrawable: -> frozen
                // animation / black window at startup until the next resize event.
                if (liveResize != pwtActive) {
                    pwtActive = liveResize;
                    if (initialized)
                        window.Window.setPresentsWithTransaction(vulkan.Vulkan.getLayerPointer(), pwtActive);
                }
                long t0 = java.lang.System.nanoTime();
                int status = presentOnce();
                if (status == PRESENT_IDLE) {
                    // Nothing to present yet: keep the 60Hz cadence, just skip the frame.
                    LockSupport.parkNanos(presentPeriod / 4);
                } else if (status == PRESENT_RETRY) {
                    // Swapchain image not free yet: sleep most of a frame period instead
                    // of hammering vkAcquireNextImageKHR.
                    LockSupport.parkNanos(2_000_000L);
                }
                // Software pace to the display cadence — UNLESS the user is actively
                // dragging the window size, in which case fire presents immediately so
                // the newest-size frame goes out the instant it is done (the OS is
                // scaling the stale surface on every compositor tick meanwhile).
                if (liveResize) {
                    deadline = java.lang.System.nanoTime() + presentPeriod; // re-anchor pacing
                } else {
                    deadline += presentPeriod;
                    long tPark = java.lang.System.nanoTime();
                    window.Window.parkUntil(deadline);
                    counterAdd(CTR_DBG_PRESENT_THREAD_PARK_MS, (java.lang.System.nanoTime() - tPark) / 1_000_000L);
                }
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
        counterAdd(CTR_DBG_ACQUIRE_NANOS, java.lang.System.nanoTime() - tA0);

        PRODUCER_LOCK.readLock().lock();
        try {
            int slot = producerSlot;
            producerSlot = (producerSlot + 1) % frameCount;

            VkDevice device = Vulkan.getDevice();
            long drawCb = getCommandBuffer(slot);
            long releasedF = Fence.get(Fence.get(releasedFencesArray, slot));
            long drawF = Fence.get(Fence.get(drawFencesArray, slot));

            try (
                MemoryStack stack = MemoryStack.stackPush();
                VkSubmitInfo.Buffer sub = VkSubmitInfo.calloc(1, stack);
                VkSubmitInfo sub0 = sub.get(0)
            ) {
                if (droppedFrames[slot]) {
                    vkResetFences(device, stack.longs(drawF));
                    droppedFrames[slot] = false;
                } else {
                    long tR0 = java.lang.System.nanoTime();
                    if (vkWaitForFences(device, stack.longs(releasedF), true, java.lang.Long.MAX_VALUE) != VK_SUCCESS) {
                        throw new IllegalStateException("produce: released fence wait failed");
                    }
                    counterAdd(CTR_DBG_RELEASED_WAIT_NANOS, java.lang.System.nanoTime() - tR0);
                    vkResetFences(device, stack.longs(releasedF, drawF));
                }

                long tL0 = java.lang.System.nanoTime();
                lockQueue();
                counterAdd(CTR_DBG_QUEUE_LOCK_NANOS, java.lang.System.nanoTime() - tL0);
                try {
                    // Re-record the draw CB with the current animation time so the visible frame
                    // advances at the DRAW rate, proving the draw thread is really running uncapped.
                    // (Under the queue lock so a main-thread resize() cannot race the re-record.)
                    float t = (float) ((java.lang.System.nanoTime() - START_NANO) / 1_000_000_000.0);
                    TriangleRenderer.recordDraw(slot, t);

                    VkQueue q = Vulkan.getGraphicsQueue();
                    sub0.sType(VK_STRUCTURE_TYPE_SUBMIT_INFO)
                            .pCommandBuffers(stack.pointers(drawCb));
                    if (VK10.vkQueueSubmit(q, sub, drawF) != VK_SUCCESS) {
                        throw new IllegalStateException("produce: draw submit failed");
                    }
                } finally {
                    unlockQueue();
                }

                RingBuffer.offer(completedRing, slot + 1L);
                long dc = counterAdd(CTR_DRAW_COUNT, 1L) + 1L;
                Log.append(LogKind.RENDER_PRODUCE, slot, dc);
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
        // Recreate-and-check: the present thread is the ONLY consumer of the swapchain
        // (images, blit CBs, acquire/present semaphores), so the swapchain rebuild can
        // happen inline here between presents with zero cross-thread coordination.
        // Consume a draw-thread resize request and/or an OUT_OF_DATE/SUBOPTIMAL flag.
        long resize = pendingResize;
        boolean invalidated = swapchainInvalidated;
        if (resize != 0L || invalidated) {
            pendingResize = 0L;
            swapchainInvalidated = false;
            int w = (int) (resize >>> 32);
            int h = (int) (resize & 0xFFFFFFFFL);
            if (resize == 0L) {
                // Invalidated without an explicit size: rebuild at the current extent.
                w = Vulkan.getSwapchainWidth();
                h = Vulkan.getSwapchainHeight();
            }
            if (w == Vulkan.getSwapchainWidth() && h == Vulkan.getSwapchainHeight() && !invalidated) {
                // Size already matches: nothing to rebuild.
                resize = 0L;
            } else {
                Vulkan.resizeSwapchain(w, h);
                resetInFlight();
            }
        }

        processGarbage(device);
        long latestSlotVal = 0L;

        /*
         Drain the completed ring to the LATEST frame, but BOUND the number of drops.
         The ring is only guaranteed to empty when the producer is slower than present;
         with an uncapped draw thread the producer keeps it non-empty forever, so an
         unbounded while(true) here livelocks the present thread inside this loop and it
         never reaches the actual blit/present below. Capping the drain lets present
         always present the newest frame while still discarding stale in-flight frames.
        */
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

        // instantiation of the autocloseables
        try (
            MemoryStack stack = MemoryStack.stackPush();
            VkSubmitInfo.Buffer submits = VkSubmitInfo.calloc(1, stack);
            VkSubmitInfo submits0 = submits.get(0);
            VkPresentInfoKHR presentInfo = VkPresentInfoKHR.calloc(stack)
        ) {
            long tW0 = java.lang.System.nanoTime();
            if (vkWaitForFences(device, stack.longs(drawF), true, java.lang.Long.MAX_VALUE) != VK_SUCCESS) {
                throw new IllegalStateException("present: draw fence wait failed");
            }
            counterAdd(CTR_DBG_PRESENT_BLOCK_NANOS, java.lang.System.nanoTime() - tW0);

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
                    // Do NOT retry this stale swapchain forever — that is the "stuck
                    // black" bug. Flag the rebuild; the next presentOnce iteration
                    // recreates the swapchain at the top of presentOnceLocked.
                    swapchainInvalidated = true;
                    return PRESENT_RETRY;
                }
                if (acquireResult == VK_NOT_READY || acquireResult == VK_TIMEOUT) {
                    // No swapchain image free yet (vsync pacing). Re-offer the frame so a later
                    // presentOnce can show it; the slot stays in flight and is NOT released here.
                    counterAdd(CTR_DBG_PRESENT_NOT_READY, 1L);
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
                long blitCb = getBlitCommandBuffer(slot, imgIndex);
                long tB0 = java.lang.System.nanoTime();
                counterAdd(CTR_DBG_BLIT_RECORD_NANOS, java.lang.System.nanoTime() - tB0);

                VkQueue q = Vulkan.getPresentQueue();
                submits0.sType(VK_STRUCTURE_TYPE_SUBMIT_INFO)
                        .waitSemaphoreCount(1)
                        .pWaitSemaphores(stack.longs(imageAvailable))
                        .pWaitDstStageMask(stack.ints(VK_PIPELINE_STAGE_TRANSFER_BIT))
                        .pCommandBuffers(stack.pointers(blitCb))
                        .pSignalSemaphores(stack.longs(blitFinished));

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
                    counterAdd(CTR_DBG_PRESENT_SUBMIT_NANOS, java.lang.System.nanoTime() - tS0);
                    long tP0 = java.lang.System.nanoTime();
                    int pres = vkQueuePresentKHR(q, presentInfo);
                    counterAdd(CTR_DBG_PRESENT_CALL_NANOS, java.lang.System.nanoTime() - tP0);
                    if (pres != VK_SUCCESS && pres != VK_SUBOPTIMAL_KHR && pres != VK_ERROR_OUT_OF_DATE_KHR) {
                        throw new IllegalStateException("present: present failed: " + pres);
                    }
                    // The frame was presented, but the surface has moved on: rebuild the
                    // swapchain on the next present iteration to stop chasing a stale chain.
                    if (pres == VK_ERROR_OUT_OF_DATE_KHR || pres == VK_SUBOPTIMAL_KHR) {
                        swapchainInvalidated = true;
                    }
                } finally {
                    unlockQueue();
                }
                slotSemaphore.release();

                long pc = counterAdd(CTR_PRESENT_COUNT, 1L) + 1L;
                Log.append(LogKind.RENDER_PRESENT, slot, pc);
                return PRESENT_DONE;
            } finally {
                window.Window.OS_NATIVE_MUTEX.set(false);
            }
        }
    }

    private static void recordBlitCommandBuffer(MemoryStack stack, VkDevice device, long blitCb,
                                                int slot, int imgIndex) {
        VkCommandBuffer command = new VkCommandBuffer(blitCb, device);
        try (
            VkCommandBufferBeginInfo beginInfo = VkCommandBufferBeginInfo.calloc(stack)
                    .sType(VK_STRUCTURE_TYPE_COMMAND_BUFFER_BEGIN_INFO);

            // The image layout barriers and blit region are stack-owned buffers that
            // implement AutoCloseable (NativeResource); they close with the try block.
            VkImageMemoryBarrier.Buffer pre = VkImageMemoryBarrier.calloc(1, stack);
            VkImageMemoryBarrier pre0 = pre.get(0);
            VkImageSubresourceRange pre0Range = pre0.subresourceRange();
            VkImageBlit.Buffer reg = VkImageBlit.calloc(1, stack);
            VkImageBlit reg0 = reg.get(0);
            VkImageSubresourceLayers reg0SrcSub = reg0.srcSubresource();
            VkImageSubresourceLayers reg0DstSub = reg0.dstSubresource();
            VkOffset3D reg0SrcOff0 = reg0.srcOffsets(0);
            VkOffset3D reg0SrcOff1 = reg0.srcOffsets(1);
            VkOffset3D reg0DstOff0 = reg0.dstOffsets(0);
            VkOffset3D reg0DstOff1 = reg0.dstOffsets(1);
            VkImageMemoryBarrier.Buffer post = VkImageMemoryBarrier.calloc(1, stack);
            VkImageMemoryBarrier post0 = post.get(0);
            VkImageSubresourceRange post0Range = post0.subresourceRange()
        ) {
            if (vkBeginCommandBuffer(command, beginInfo) != VK_SUCCESS) {
                throw new IllegalStateException("Failed to begin blit command buffer.");
            }

            long swapchainImage = Long.get(Vulkan.getSwapchainImages(), imgIndex);
            long offscreenImage = TriangleRenderer.getOffscreenImageHandle(slot);
            int srcW = Vulkan.getSwapchainWidth();
            int srcH = Vulkan.getSwapchainHeight();
            int dstW = Vulkan.getSwapchainWidth();
            int dstH = Vulkan.getSwapchainHeight();

            pre0.sType(VK_STRUCTURE_TYPE_IMAGE_MEMORY_BARRIER)
                    .oldLayout(VK_IMAGE_LAYOUT_UNDEFINED)
                    .newLayout(VK_IMAGE_LAYOUT_TRANSFER_DST_OPTIMAL)
                    .srcQueueFamilyIndex(VK_QUEUE_FAMILY_IGNORED)
                    .dstQueueFamilyIndex(VK_QUEUE_FAMILY_IGNORED)
                    .image(swapchainImage)
                    .srcAccessMask(0)
                    .dstAccessMask(VK_ACCESS_TRANSFER_WRITE_BIT);
            pre0Range.aspectMask(VK_IMAGE_ASPECT_COLOR_BIT)
                    .baseMipLevel(0)
                    .levelCount(1)
                    .baseArrayLayer(0)
                    .layerCount(1);
            vkCmdPipelineBarrier(command,
                    VK_PIPELINE_STAGE_TOP_OF_PIPE_BIT, VK_PIPELINE_STAGE_TRANSFER_BIT,
                    0, null, null, pre);

            reg0SrcSub.set(VK_IMAGE_ASPECT_COLOR_BIT, 0, 0, 1);
            reg0DstSub.set(VK_IMAGE_ASPECT_COLOR_BIT, 0, 0, 1);
            reg0SrcOff0.set(0, 0, 0);
            reg0SrcOff1.set(srcW, srcH, 1);
            reg0DstOff0.set(0, 0, 0);
            reg0DstOff1.set(dstW, dstH, 1);
            vkCmdBlitImage(command,
                    offscreenImage, VK_IMAGE_LAYOUT_TRANSFER_SRC_OPTIMAL,
                    swapchainImage, VK_IMAGE_LAYOUT_TRANSFER_DST_OPTIMAL,
                    reg, VK_FILTER_LINEAR);

            post0.sType(VK_STRUCTURE_TYPE_IMAGE_MEMORY_BARRIER)
                    .oldLayout(VK_IMAGE_LAYOUT_TRANSFER_DST_OPTIMAL)
                    .newLayout(VK_IMAGE_LAYOUT_PRESENT_SRC_KHR)
                    .srcQueueFamilyIndex(VK_QUEUE_FAMILY_IGNORED)
                    .dstQueueFamilyIndex(VK_QUEUE_FAMILY_IGNORED)
                    .image(swapchainImage)
                    .srcAccessMask(VK_ACCESS_TRANSFER_WRITE_BIT)
                    .dstAccessMask(0);
            post0Range.aspectMask(VK_IMAGE_ASPECT_COLOR_BIT)
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
    }

    public static void pauseProducer() { PRODUCER_LOCK.writeLock().lock(); }
    public static void resumeProducer() { PRODUCER_LOCK.writeLock().unlock(); }

    // Blocks until any in-flight presentOnce() finishes, then keeps the present thread
    // out while the swapchain is being torn down/recreated. Must pair with resumePresent().
    public static void pausePresent() { PRESENT_LOCK.writeLock().lock(); }
    public static void resumePresent() { PRESENT_LOCK.writeLock().unlock(); }

    private static void lockQueue() {
        SpinLock.lock(queueLock);
    }

    private static void unlockQueue() {
        SpinLock.unlock(queueLock);
    }

    public static long getCommandBuffer(int index) {
        long cbPtr = CommandBuffer.get(commandBuffersArray, index);
        return CommandBuffer.get(cbPtr);
    }

    private static long getBlitCommandBuffer(int slot, int imgIndex) {
        long blitCbPtr = CommandBuffer.get(blitCommandBuffersArray, slot * blitImgCount + imgIndex);
        return CommandBuffer.get(blitCbPtr);
    }

    @Intention("The blit is (slot offscreen image -> swapchain image) with the fixed swapchain "
            + "extent; the recorded commands change only when the swapchain is recreated, so the "
            + "per-(slot,img) command buffers are cached and re-recorded once per resize instead of "
            + "every present. Off the hot present path.")
    private static void rebuildBlitCommandBuffers(VkDevice device) {
        if (blitCommandBuffersArray != 0L) {
            long retireTag = counterGet(CTR_PRESENT_COUNT) + frameCount + 2;
            for (int i = 0; i < frameCount * blitImgCount; i++) {
                pushGarbage(0L, CommandBuffer.get(blitCommandBuffersArray, i), retireTag);
            }
            CommandBuffer.free(blitCommandBuffersArray);
            blitCommandBuffersArray = 0L;
        }
        int imgCount = Math.max(1, Vulkan.getSwapchainImageCount());
        blitImgCount = imgCount;
        blitCommandBuffersArray = CommandBuffer.allocateArray(frameCount * imgCount);
        for (int slot = 0; slot < frameCount; slot++) {
            for (int img = 0; img < imgCount; img++) {
                long cbPtr = CommandBuffer.create(device, blitPoolPtr);
                CommandBuffer.set(blitCommandBuffersArray, slot * imgCount + img, cbPtr);
                try (MemoryStack stack = MemoryStack.stackPush()) {
                    recordBlitCommandBuffer(stack, device, CommandBuffer.get(cbPtr), slot, img);
                }
            }
        }
    }

    @Intention("Tears down the cached blit command-buffer grid. Called from destroy() and from "
            + "rebuildBlitCommandBuffers() when the swapchain image count changes on resize.")
    private static void destroyBlitCommandBuffers(VkDevice device) {
        if (blitCommandBuffersArray != 0L) {
            for (int i = 0; i < frameCount * blitImgCount; i++) {
                CommandBuffer.destroy(CommandBuffer.get(blitCommandBuffersArray, i), device, blitPoolPtr);
            }
            CommandBuffer.free(blitCommandBuffersArray);
            blitCommandBuffersArray = 0L;
            blitImgCount = 0;
        }
    }

    public static int getFrameCount() { return frameCount; }
    public static long getDrawCount() { return counterGet(CTR_DRAW_COUNT); }
    public static long getPresentCount() { return counterGet(CTR_PRESENT_COUNT); }

    // 1Hz telemetry snapshot + reset, read by the draw thread's debug/FPS logging.
    public static long getDbgAcquireNanos() { return counterGet(CTR_DBG_ACQUIRE_NANOS); }
    public static long getDbgReleasedWaitNanos() { return counterGet(CTR_DBG_RELEASED_WAIT_NANOS); }
    public static long getDbgQueueLockNanos() { return counterGet(CTR_DBG_QUEUE_LOCK_NANOS); }
    public static long getDbgPresentBlockNanos() { return counterGet(CTR_DBG_PRESENT_BLOCK_NANOS); }
    public static long getDbgPresentNotReady() { return counterGet(CTR_DBG_PRESENT_NOT_READY); }
    public static long getDbgPresentSubmitNanos() { return counterGet(CTR_DBG_PRESENT_SUBMIT_NANOS); }
    public static long getDbgPresentCallNanos() { return counterGet(CTR_DBG_PRESENT_CALL_NANOS); }
    public static long getDbgPresentThreadLoops() { return counterGet(CTR_DBG_PRESENT_THREAD_LOOPS); }
    public static long getDbgPresentThreadParkMs() { return counterGet(CTR_DBG_PRESENT_THREAD_PARK_MS); }
    public static long getDbgBlitRecordNanos() { return counterGet(CTR_DBG_BLIT_RECORD_NANOS); }

    public static void resetDbgCounters() {
        for (int i = CTR_DBG_ACQUIRE_NANOS; i <= CTR_DBG_BLIT_RECORD_NANOS; i++) {
            ForeignMemory.setVolatileLong(ctrAddr(i), 0L);
        }
    }

    public static void resetInFlight() {
        if (completedRing == 0L) return;
        VkDevice device = Vulkan.getDevice();

        long retireTag = counterGet(CTR_PRESENT_COUNT) + frameCount + 2;
        // blitFinished is indexed by SWAPCHAIN IMAGE index; if a resize grew the image
        // count past the init-time array, expand before recreating entries so the
        // present thread never indexes out of bounds.
        int needBlit = Math.max(frameCount, Math.max(1, Vulkan.getSwapchainImageCount()));
        if (Semaphore.length(blitFinishedSemaphoresArray) < needBlit) {
            blitFinishedSemaphoresArray = Semaphore.expandArray(blitFinishedSemaphoresArray, needBlit);
        }
        for (int i = 0; i < frameCount; i++) {
            long oldAvail = Semaphore.get(imageAvailableSemaphoresArray, i);
            pushGarbage(oldAvail, 0L, retireTag);
            Semaphore.set(imageAvailableSemaphoresArray, i, Semaphore.create(device));
        }
        for (int i = 0; i < needBlit; i++) {
            long oldBlit = Semaphore.get(blitFinishedSemaphoresArray, i);
            pushGarbage(oldBlit, 0L, retireTag);
            Semaphore.set(blitFinishedSemaphoresArray, i, Semaphore.create(device));
        }

        // The swapchain (image handles, count, extent) and/or off-screen attachments changed,
        // so every cached blit command buffer must be re-recorded against the new targets.
        rebuildBlitCommandBuffers(device);
    }

    public static void destroy(VkDevice device, long drawCommandPoolPtr, long blitCommandPoolPtr) {
        if (commandBuffersArray == 0L) return;
        stopPresentThread();
        if (device != null && device.address() != 0L) {
            VK10.vkDeviceWaitIdle(device);
        }
        for (int i = 0; i < frameCount; i++) {
            CommandBuffer.destroy(CommandBuffer.get(commandBuffersArray, i), device, drawCommandPoolPtr);
            Fence.destroy(Fence.get(drawFencesArray, i), device);
            Fence.destroy(Fence.get(releasedFencesArray, i), device);
            Semaphore.destroy(Semaphore.get(imageAvailableSemaphoresArray, i), device);
        }
        int blitDestroyCount = Math.max(frameCount, Math.max(1, Vulkan.getSwapchainImageCount()));
        for (int i = 0; i < blitDestroyCount; i++) {
            Semaphore.destroy(Semaphore.get(blitFinishedSemaphoresArray, i), device);
        }
        destroyBlitCommandBuffers(device);
        CommandBuffer.free(commandBuffersArray);
        Fence.free(drawFencesArray);
        Fence.free(releasedFencesArray);
        Semaphore.free(imageAvailableSemaphoresArray);
        Semaphore.free(blitFinishedSemaphoresArray);
        RingBuffer.free(completedRing);
        ForeignMemory.freeNative(countersArray);
        SpinLock.free(queueLock);
        if (garbageSemaphoresArray != 0L) {
            while (garbageHead != garbageTail) {
                long sem = ForeignMemory.getVolatileLong(garbageSemaphoresArray + garbageHead * 8L);
                long cb = ForeignMemory.getVolatileLong(garbageCommandBuffersArray + garbageHead * 8L);
                long swp = ForeignMemory.getVolatileLong(garbageSwapchainsArray + garbageHead * 8L);
                if (sem != 0L) Semaphore.destroy(sem, device);
                if (cb != 0L) CommandBuffer.destroy(cb, device, blitPoolPtr);
                if (swp != 0L) vkDestroySwapchainKHR(device, swp, null);
                garbageHead = (garbageHead + 1) % MAX_GARBAGE;
            }
            ForeignMemory.freeNative(garbageSemaphoresArray);
            ForeignMemory.freeNative(garbageCommandBuffersArray);
            ForeignMemory.freeNative(garbageSwapchainsArray);
            ForeignMemory.freeNative(garbageFrameTagsArray);
            garbageSemaphoresArray = 0L;
            garbageCommandBuffersArray = 0L;
            garbageSwapchainsArray = 0L;
            garbageFrameTagsArray = 0L;
        }

        commandBuffersArray = 0L;
        blitCommandBuffersArray = 0L;
        completedRing = 0L;
        countersArray = 0L;
        queueLock = 0L;
        frameCount = 0;
        initialized = false;
    }
}