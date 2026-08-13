package thread;

import annotation.Draft;
import annotation.Intention;
import annotation.Required;
import annotation.Volatile;
import nio.ForeignMemory;
import oop.TypeRegister;
import struct.Map;
import struct.Array;
import window.Window;

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

    // Built-in worker roles. The CORE worker is the scheduler: it drains the SCENE,
    // UI and user roles each frame and writes every role's recorded ops into ONE
    // command buffer, so the window receives a single merged submit per frame.
    public static final int ROLE_CORE = 1;
    public static final int ROLE_SCENE = 2;      // scene #1 placeholder (gradient rect)
    public static final int ROLE_UI = 3;        // event-driven UI renderer (not ImGui)
    public static final int ROLE_USER = 4;      // start of user-generated registers

    private static final long CORE_WORKER_PTR;
    private static final long SCENE_WORKER_PTR;
    private static final long UI_WORKER_PTR;

    static {
        CORE_WORKER_PTR = invokeRole(ROLE_CORE);
        SCENE_WORKER_PTR = invokeRole(ROLE_SCENE);
        UI_WORKER_PTR = invokeRole(ROLE_UI);
    }

    private DrawThread() {}

    public static int classId() {
        return CLASS_ID;
    }

    public static long getCoreWorker() {
        return CORE_WORKER_PTR;
    }

    public static long getSceneWorker() {
        return SCENE_WORKER_PTR;
    }

    public static long getUiWorker() {
        return UI_WORKER_PTR;
    }

    public static boolean isCore(long workerPtr) {
        return getRole(workerPtr) == ROLE_CORE;
    }

    public static int getRole(long workerPtr) {
        if (workerPtr == 0L) return 0;
        return ForeignMemory.getInt(workerPtr + 32L);
    }

    public static boolean isBuiltIn(long workerPtr) {
        int role = getRole(workerPtr);
        return role == ROLE_CORE || role == ROLE_SCENE || role == ROLE_UI;
    }

    /**
     * Creates and registers a new off-heap draw worker thread handle as a user role.
     */
    public static long invoke() {
        return invokeRole(ROLE_USER);
    }

    public static long invokeRole(int role) {
        long block = ForeignMemory.allocateNative(64);
        long workerPtr = block + 8L;

        // Write bit-packed header ID & length header
        ForeignMemory.setInt(block, TYPE_DRAW_THREAD);
        ForeignMemory.setInt(block + 4L, 1);

        long workQueuePtr = RingBuffer.instant(TypeRegister.ID_LONG, 2048);

        // Off-heap worker fields:
        // workerPtr + 0: state (0 = STOPPED, 1 = RUNNING)
        // workerPtr + 4: poolSize (1 thread per worker handle)
        // workerPtr + 8: workQueuePtr (RingBuffer handle)
        // workerPtr + 16: reserved (kept 0; role supersedes the old isCore flag)
        // workerPtr + 24: windowPtr bound to this worker (only used by CORE)
        // workerPtr + 32: role (ROLE_CORE / ROLE_SCENE / ROLE_UI / ROLE_USER + N)
        ForeignMemory.setInt(workerPtr, 0);                 // STOPPED
        ForeignMemory.setInt(workerPtr + 4L, 1);             // 1 thread
        ForeignMemory.setLong(workerPtr + 8L, workQueuePtr);
        ForeignMemory.setInt(workerPtr + 16L, 0);
        ForeignMemory.setLong(workerPtr + 24L, 0L);
        ForeignMemory.setInt(workerPtr + 32L, role);

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
     * Submits a task handle to a built-in role (SCENE / UI) or any worker. Unlike
     * {@link #submit(long, long)} this does not require the worker to be running:
     * built-in roles are drained by the CORE scheduler each frame, not by their
     * own thread, so they stay writable while their "thread" is parked.
     */
    public static long roleHandle(int role) {
        return switch (role) {
            case ROLE_CORE -> CORE_WORKER_PTR;
            case ROLE_SCENE -> SCENE_WORKER_PTR;
            case ROLE_UI -> UI_WORKER_PTR;
            default -> 0L;
        };
    }

    public static boolean submitRole(int role, long taskPtr) {
        long workerPtr = role == ROLE_CORE || role == ROLE_SCENE || role == ROLE_UI
                ? roleHandle(role) : 0L;
        if (workerPtr == 0L || !isRegistered(workerPtr)) return false;
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
        int swapW = isCore(workerPtr) ? vulkan.Vulkan.getSwapchainWidth() : 0;
        int swapH = isCore(workerPtr) ? vulkan.Vulkan.getSwapchainHeight() : 0;
        long lastContentSize = ((long)swapW << 32) | (swapH & 0xFFFFFFFFL);
        boolean lastFullscreen = windowPtr != 0L && Window.isFullscreen(windowPtr);
        int basePresentMode = isCore(workerPtr) ? vulkan.Vulkan.getPresentMode() : org.lwjgl.vulkan.KHRSurface.VK_PRESENT_MODE_FIFO_KHR;

        long fpsWindowStart = java.lang.System.nanoTime();
        long lastDraw = 0L, lastPresent = 0L;
        long frameDeadline = 0L;
        long dbgIterNanos = 0L;

        while (ForeignMemory.getInt(workerPtr) == 1 && !Thread.currentThread().isInterrupted()) {
            // 1. CORE worker: run the main engine loop.
            if (isCore(workerPtr)) {
                // Drain inputs generated by Thread 0 instantly.
                input.Key.dispatchEvents();
                input.Mouse.dispatchEvents();
                input.Touch.update();

                // Debounced resize -> swapchain rebuild (policy Window.run used to own).
                if (windowPtr != 0L) {
                    boolean fullscreen = window.Window.isFullscreen(windowPtr);
                    if (fullscreen != lastFullscreen) {
                        lastFullscreen = fullscreen;
                        var listener = window.Window.getFullscreenListener();
                        if (listener != null) {
                            try {
                                listener.accept(fullscreen);
                            } catch (Throwable t) {
                                System.out.println("[draw] fullscreen listener failed: " + t);
                            }
                        }
                    }
                    long contentSize = window.Window.getContentSize(windowPtr);
                    if (contentSize != lastContentSize && contentSize != 0L) {
                        System.out.println("[window] resize event: "
                                + ((int) (contentSize >>> 32)) + "x" + (int) (contentSize & 0xFFFFFFFFL));
                        // Publish the requested size; the present thread rebuilds the
                        // swapchain inline between presents (deferred destruction). The
                        // draw thread never blocks on resize.
                        vulkan.Renderer.requestResize((int) (contentSize >>> 32), (int) (contentSize & 0xFFFFFFFFL));
                        lastContentSize = contentSize;
                    }
                    // We consider it a live resize if the native window manager says so.
                    boolean liveResize = window.Window.isLiveResize(windowPtr);
                    vulkan.Renderer.liveResize = liveResize;

                    if (window.Window.shouldClose(windowPtr)) {
                        ForeignMemory.setInt(workerPtr, 0); // window gone: self-stop
                        break;
                    }
                }

                // Render the frame off-screen. The dedicated Core-Present thread owns
                // presentOnce() and drains the completed ring at the swapchain's FIFO
                // pace, so DRAW rate and PRESENT rate are fully decoupled here.
                //
                // FPS cap: 0 = uncapped (draw as fast as the pipeline allows).
                // A positive cap parks to hit the target DRAW rate exactly;
                // present stays decoupled and only fires when a swapchain image
                // is free, so draw:500 / present:60 is reachable on FIFO.
                int cap = window.Window.getDrawCap(windowPtr);
                if (cap > 0 && !vulkan.Renderer.liveResize) {
                    long period = 1_000_000_000L / cap;
                    long now = java.lang.System.nanoTime();
                    if (frameDeadline == 0L || frameDeadline + period < now) {
                        frameDeadline = now;   // first frame, or dropped a full frame: resync anchor
                    }
                    frameDeadline += period;
                    window.Window.parkUntil(frameDeadline);
                } else if (vulkan.Renderer.liveResize) {
                    frameDeadline = 0L;   // drag ended: next frame re-anchors the cap cleanly
                }
                long iterT0 = java.lang.System.nanoTime();
                vulkan.Renderer.produceOnce();
                dbgIterNanos += java.lang.System.nanoTime() - iterT0;

                // After recording the frame, CORE merges every role's queue into the
                // SAME command buffer so the next produceOnce submits one combined frame.
                dispatchRoleQueues();

                // 1Hz window-title FPS counter (was owned by Window.run's old loop).
                long now = java.lang.System.nanoTime();
                if (now - fpsWindowStart >= 1_000_000_000L) {
                    long currDraw = vulkan.Renderer.getDrawCount();
                    long currPresent = vulkan.Renderer.getPresentCount();
                    double elaps = (now - fpsWindowStart) / 1_000_000_000.0;
                    double drawFps = (currDraw - lastDraw) / elaps;
                    double presentFps = (currPresent - lastPresent) / elaps;
                    long totalIterNanos = dbgIterNanos;
                    
                    if (System.getProperty("anti.debug.present") != null)
                        System.out.println("[DEBUG] Draw=" + String.format("%.1f", drawFps)
                                + " Present=" + String.format("%.1f", presentFps)
                                + " iterMs=" + (totalIterNanos / 1_000_000)
                                + " acqMs=" + (vulkan.Renderer.getDbgAcquireNanos() / 1_000_000)
                                + " relWaitMs=" + (vulkan.Renderer.getDbgReleasedWaitNanos() / 1_000_000)
                                + " qLockMs=" + (vulkan.Renderer.getDbgQueueLockNanos() / 1_000_000)
                                + " presBlockMs=" + (vulkan.Renderer.getDbgPresentBlockNanos() / 1_000_000)
                                + " presSubMs=" + (vulkan.Renderer.getDbgPresentSubmitNanos() / 1_000_000)
                                + " presCallMs=" + (vulkan.Renderer.getDbgPresentCallNanos() / 1_000_000)
                                + " pLoops=" + vulkan.Renderer.getDbgPresentThreadLoops()
                                + " pParkMs=" + (vulkan.Renderer.getDbgPresentThreadParkMs())
                                + " blitRecMs=" + (vulkan.Renderer.getDbgBlitRecordNanos() / 1_000_000)
                                + " notReady=" + vulkan.Renderer.getDbgPresentNotReady());
                    dbgIterNanos = 0L;
                    vulkan.Renderer.resetDbgCounters();
                    // Publish to the title mailbox; Thread 0's pump applies it (AppKit).
                    if (windowPtr != 0L) {
                        double drawDelta = (currDraw - lastDraw);
                        double avgDrawMs = drawDelta > 0 ? (totalIterNanos / 1_000_000.0) / drawDelta : 0.0;
                        long winSize = windowPtr != 0L ? window.Window.getContentSize(windowPtr) : 0L;
                        window.Window.publishTitle(String.format(
                                "Window | Draw %.1f FPS (%.2f ms) | win=%dx%d | %s",
                                drawFps, avgDrawMs,
                                (int) (winSize >>> 32), (int) (winSize & 0xFFFFFFFFL),
                                vulkan.TriangleRenderer.dbgFbRect()));
                    }
                    fpsWindowStart = now;
                    lastDraw = currDraw;
                    lastPresent = currPresent;
                }
            }

            // 2. Process off-heap task handles submitted to this worker's queue.
            if (queuePtr != 0L && !RingBuffer.isEmpty(queuePtr)) {
                long taskPtr = RingBuffer.poll(queuePtr);
                if (taskPtr != 0L) {
                    // The CORE worker is ALSO reading the merged role queues here via
                    // drainRoleQueues() below; a worker's own loop only performs its
                    // role-specific record if no CORE scheduler is driving it.
                    dispatch(getRole(workerPtr), taskPtr);
                }
            } else if (!isCore(workerPtr)) {
                // Non-core workers park if they have no tasks.
                java.util.concurrent.locks.LockSupport.parkNanos(1_000_000L);
            }
        }
    }

    /**
     * CORE scheduling pass: drains the SCENE, UI and every registered USER role
     * after each produceOnce(), dispatching each recorded op into the CURRENT
     * frame's command buffer. The window then receives ONE merged submit.
     */
    private static void dispatchRoleQueues() {
        long keysPtr = Map.getKeys(WORKER_MAP_PTR);
        if (keysPtr == 0L) return;
        int count = Array.length(keysPtr);
        for (int i = 0; i < count; i++) {
            long workerPtr = Array.get(keysPtr, i);
            if (isCore(workerPtr)) continue; // CORE's own queue is its engine loop
            int role = getRole(workerPtr);
            if (role == ROLE_UI) continue; // UI is unused for now: keeper handle only
            long queuePtr = getQueue(workerPtr);
            while (queuePtr != 0L && !RingBuffer.isEmpty(queuePtr)) {
                long taskPtr = RingBuffer.poll(queuePtr);
                if (taskPtr != 0L) dispatch(role, taskPtr);
            }
        }
        Array.free(keysPtr);
    }

    /** Routes a task handle by role into the single merged frame the CORE will submit. */
    private static void dispatch(int role, long taskPtr) {
        switch (role) {
            case ROLE_SCENE -> scene.SceneRole.record(taskPtr);
            case ROLE_UI -> ui.UiRole.record(taskPtr);
            default -> render.UserRole.record(taskPtr); // ROLE_USER..N
        }
    }
}
