package thread;

import annotation.Draft;
import annotation.Intention;
import annotation.Required;
import annotation.Volatile;
import nio.ForeignMemory;
import oop.TypeRegister;
import struct.Map;
import struct.Array;

/**
 * Data-Oriented Design (DOD) off-heap Draw Thread Pool Manager.
 * 
 * Manages multiple independent off-heap rendering worker thread handles.
 */
@Draft
@Intention("Off-heap draw thread pool manager tracking rendering worker handles via dogfooded struct.Map")
@Volatile
public final class DrawThread {

    @Required
    public static final int CLASS_ID = TypeRegister.ID_DRAW_THREAD;
    public static final int TYPE_DRAW_THREAD = TypeRegister.DRAW_THREAD_SINGLETON;

    // Central off-heap manager registry mapping workerPtr -> Thread instance
    private static final long WORKER_MAP_PTR = Map.instant(TypeRegister.ID_LONG, TypeRegister.ID_VARIABLE, 128);

    private static final long CORE_WORKER_PTR;

    static {
        CORE_WORKER_PTR = invoke(true);
    }

    private DrawThread() {}

    public static int classId() {
        return CLASS_ID;
    }

    public static long getCoreWorker() {
        return CORE_WORKER_PTR;
    }

    public static boolean isCore(long workerPtr) {
        if (workerPtr == 0L) return false;
        return ForeignMemory.getInt(workerPtr + 16L) == 1;
    }

    /**
     * Creates and registers a new off-heap draw worker thread handle.
     */
    public static long invoke() {
        return invoke(false);
    }

    private static long invoke(boolean isCore) {
        long block = ForeignMemory.allocateNative(56);
        long workerPtr = block + 8L;

        // Write bit-packed header ID & length header
        ForeignMemory.setInt(block, TYPE_DRAW_THREAD);
        ForeignMemory.setInt(block + 4L, 1);

        long workQueuePtr = RingBuffer.instant(TypeRegister.ID_LONG, 2048);

        // Off-heap worker fields:
        // workerPtr + 0: state (0 = STOPPED, 1 = RUNNING)
        // workerPtr + 4: poolSize (1 thread per worker handle)
        // workerPtr + 8: workQueuePtr (RingBuffer handle)
        // workerPtr + 16: isCore (1 = CORE, 0 = USER)
        // workerPtr + 24: windowPtr bound to this worker (only used by CORE)
        ForeignMemory.setInt(workerPtr, 0);                 // STOPPED
        ForeignMemory.setInt(workerPtr + 4L, 1);             // 1 thread
        ForeignMemory.setLong(workerPtr + 8L, workQueuePtr);
        ForeignMemory.setInt(workerPtr + 16L, isCore ? 1 : 0);
        ForeignMemory.setLong(workerPtr + 24L, 0L);

        // Register worker handle in central pool manager registry
        Map.put(WORKER_MAP_PTR, workerPtr, 1L);
        return workerPtr;
    }

    public static boolean isRegistered(long workerPtr) {
        if (workerPtr == 0L) return false;
        return Map.containsKey(WORKER_MAP_PTR, workerPtr);
    }

    public static boolean isRunning(long workerPtr) {
        if (workerPtr == 0L) return false;
        return ForeignMemory.getInt(workerPtr) == 1;
    }

    public static long getQueue(long workerPtr) {
        if (workerPtr == 0L) return 0L;
        return ForeignMemory.getLong(workerPtr + 8L);
    }

    /**
     * Binds an OS window handle to the worker. The CORE worker uses it to poll
     * the content size for debounced swapchain rebuilds and to detect window close.
     */
    public static void bindWindow(long workerPtr, long windowPtr) {
        if (workerPtr == 0L) return;
        ForeignMemory.setLong(workerPtr + 24L, windowPtr);
    }

    /**
     * Instantiates and launches the background draw worker thread for the specified handle.
     */
    public static synchronized boolean run(long workerPtr) {
        if (workerPtr == 0L || !isRegistered(workerPtr)) return false;
        if (isRunning(workerPtr)) return true; // Already running

        long queuePtr = getQueue(workerPtr);

        Thread worker = Thread.ofPlatform()
                .name("Anti-DrawWorker-0x" + Long.toHexString(workerPtr).toUpperCase())
                .daemon(true)
                .start(() -> processQueue(workerPtr, queuePtr));

        Map.putObject(WORKER_MAP_PTR, workerPtr, worker);
        ForeignMemory.setInt(workerPtr, 1); // Set state to RUNNING
        return true;
    }

    /**
     * Submits a render task handle to the specified draw worker thread instance queue.
     */
    public static boolean submit(long workerPtr, long taskPtr) {
        if (workerPtr == 0L || taskPtr == 0L) return false;
        if (!isRunning(workerPtr)) {
            return false;
        }
        long queuePtr = getQueue(workerPtr);
        return RingBuffer.offer(queuePtr, taskPtr);
    }

    /**
     * Stops execution for the specified draw worker thread handle.
     */
    public static synchronized void stop(long workerPtr) {
        if (workerPtr == 0L) return;
        if (isCore(workerPtr)) return; // Protected core thread
        stopInternal(workerPtr);
    }

    private static void stopInternal(long workerPtr) {
        ForeignMemory.setInt(workerPtr, 0); // Set state to STOPPED

        Thread worker = (Thread) Map.getObject(WORKER_MAP_PTR, workerPtr);
        if (worker != null) {
            worker.interrupt();
            try {
                worker.join();
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
            }
        }
        Map.put(WORKER_MAP_PTR, workerPtr, 1L);
    }

    /**
     * Stops the worker thread and frees its off-heap native memory block, unregistering from manager pool.
     */
    public static synchronized void free(long workerPtr) {
        if (workerPtr == 0L) return;
        if (isCore(workerPtr)) return; // Protected core thread
        freeInternal(workerPtr);
    }

    private static void freeInternal(long workerPtr) {
        stopInternal(workerPtr);

        long queuePtr = getQueue(workerPtr);
        if (queuePtr != 0L) {
            RingBuffer.free(queuePtr);
        }

        Map.remove(WORKER_MAP_PTR, workerPtr);

        long block = workerPtr - 8L;
        ForeignMemory.freeNative(block);
    }

    /**
     * Returns total number of registered draw worker thread handles in the manager pool.
     */
    public static int getWorkerCount() {
        return Map.size(WORKER_MAP_PTR);
    }

    /**
     * Launches all registered draw worker thread handles in the pool.
     */
    public static synchronized void runAll() {
        long keysPtr = Map.getKeys(WORKER_MAP_PTR);
        if (keysPtr == 0L) return;
        int count = Array.length(keysPtr);
        for (int i = 0; i < count; i++) {
            long workerPtr = Array.get(keysPtr, i);
            run(workerPtr);
        }
        Array.free(keysPtr);
    }

    /**
     * Stops all registered draw worker thread handles in the pool.
     */
    public static synchronized void stopAll() {
        long keysPtr = Map.getKeys(WORKER_MAP_PTR);
        if (keysPtr == 0L) return;
        int count = Array.length(keysPtr);
        for (int i = 0; i < count; i++) {
            long workerPtr = Array.get(keysPtr, i);
            if (!isCore(workerPtr)) {
                stopInternal(workerPtr);
            }
        }
        Array.free(keysPtr);
    }

    /**
     * Stops and frees all registered draw worker thread handles in the pool.
     */
    public static synchronized void freeAll() {
        long keysPtr = Map.getKeys(WORKER_MAP_PTR);
        if (keysPtr == 0L) return;
        int count = Array.length(keysPtr);
        for (int i = 0; i < count; i++) {
            long workerPtr = Array.get(keysPtr, i);
            if (!isCore(workerPtr)) {
                freeInternal(workerPtr);
            }
        }
        Array.free(keysPtr);
    }

    /**
     * Completely shuts down and frees all resources, including the core worker.
     * Call this only during full system shutdown.
     */
    public static synchronized void freeAllSystem() {
        long keysPtr = Map.getKeys(WORKER_MAP_PTR);
        if (keysPtr == 0L) return;
        int count = Array.length(keysPtr);
        for (int i = 0; i < count; i++) {
            long workerPtr = Array.get(keysPtr, i);
            freeInternal(workerPtr);
        }
        Array.free(keysPtr);
    }

    private static void processQueue(long workerPtr, long queuePtr) {
        // The CORE worker owns the whole engine loop: it drains the input
        // RingBuffers the event pump just fed, rebuilds the swapchain on resize,
        // then draws and presents. FIFO present mode makes vkAcquireNextImageKHR
        // sleep on the WindowServer refresh (60/120Hz) right here, never on Thread 0.
        long windowPtr = isCore(workerPtr) ? ForeignMemory.getLong(workerPtr + 24L) : 0L;
        long lastContentSize = windowPtr != 0L ? window.Window.getContentSize(windowPtr) : 0L;
        long pendingResizeSize = 0L;
        long lastResizeEventTime = 0L;

        long fpsWindowStart = java.lang.System.nanoTime();
        long lastDraw = 0L, lastPresent = 0L;

        while (ForeignMemory.getInt(workerPtr) == 1 && !Thread.currentThread().isInterrupted()) {
            // 1. CORE worker: run the main engine loop.
            if (isCore(workerPtr)) {
                // Drain inputs generated by Thread 0 instantly.
                input.Key.dispatchEvents();
                input.Mouse.dispatchEvents();
                input.Touch.update();

                // Debounced resize -> swapchain rebuild (policy Window.run used to own).
                if (windowPtr != 0L) {
                    long contentSize = window.Window.getContentSize(windowPtr);
                    if (contentSize != lastContentSize && contentSize != 0L) {
                        pendingResizeSize = contentSize;
                        lastContentSize = contentSize;
                        lastResizeEventTime = java.lang.System.nanoTime();
                    }
                    if (pendingResizeSize != 0L
                            && (java.lang.System.nanoTime() - lastResizeEventTime > 200_000_000L)) {
                        int w = (int) (pendingResizeSize >>> 32);
                        int h = (int) (pendingResizeSize & 0xFFFFFFFFL);
                        vulkan.TriangleRenderer.resize(w, h);
                        pendingResizeSize = 0L;
                    }

                    if (window.Window.shouldClose(windowPtr)) {
                        ForeignMemory.setInt(workerPtr, 0); // window gone: self-stop
                        break;
                    }
                }

                // Render the frame (produce off-screen, then present at the
                // display's FIFO pace — this sleeps to match 60/120Hz).
                vulkan.Renderer.produceOnce();
                vulkan.Renderer.presentOnce();

                // 1Hz window-title FPS counter (was owned by Window.run's old loop).
                long now = java.lang.System.nanoTime();
                if (now - fpsWindowStart >= 1_000_000_000L) {
                    long currDraw = vulkan.Renderer.getDrawCount();
                    long currPresent = vulkan.Renderer.getPresentCount();
                    double elaps = (now - fpsWindowStart) / 1_000_000_000.0;
                    double drawFps = (currDraw - lastDraw) / elaps;
                    double presentFps = (currPresent - lastPresent) / elaps;
                    if (System.getProperty("anti.debug.present") != null)
                        System.out.println("[DEBUG] Draw=" + String.format("%.1f", drawFps)
                                + " Present=" + String.format("%.1f", presentFps));
                    // Publish to the title mailbox; Thread 0's pump applies it (AppKit).
                    if (windowPtr != 0L)
                        window.Window.publishTitle(String.format(
                                "Anti Engine | Draw %.1f FPS | Present %.1f FPS", drawFps, presentFps));
                    fpsWindowStart = now;
                    lastDraw = currDraw;
                    lastPresent = currPresent;
                }
            }

            // 2. Process off-heap task handles submitted to this worker's queue.
            if (queuePtr != 0L && !RingBuffer.isEmpty(queuePtr)) {
                long taskPtr = RingBuffer.poll(queuePtr);
                if (taskPtr != 0L) {
                    // process render task handle
                }
            } else if (!isCore(workerPtr)) {
                // Non-core workers sleep if they have no tasks.
                try {
                    Thread.sleep(1);
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                    break;
                }
            }
        }
    }
}
