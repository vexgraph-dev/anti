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
 * Data-Oriented Design (DOD) off-heap Scripting Thread Pool Manager.
 * <p>
 * Manages multiple independent off-heap scripting worker thread handles.
 */
@Draft
@Intention("Off-heap scripting thread pool manager tracking script execution worker handles via dogfooded struct.Map")
@Volatile
public final class ScriptingThread {

    @Required
    public static final int CLASS_ID = TypeRegister.ID_SCRIPTING_THREAD;
    public static final int TYPE_SCRIPTING_THREAD = TypeRegister.SCRIPTING_THREAD_SINGLETON;

    // Central off-heap manager registry mapping workerPtr -> Thread instance
    private static final long WORKER_MAP_PTR = Map.instant(TypeRegister.ID_LONG, TypeRegister.ID_VARIABLE, 128);

    private static final long CORE_WORKER_PTR;

    static {
        CORE_WORKER_PTR = invoke(true);
    }

    private ScriptingThread() {}

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
     * Creates and registers a new off-heap scripting worker thread handle.
     */
    public static long invoke() {
        return invoke(false);
    }

    private static long invoke(boolean isCore) {
        long block = ForeignMemory.allocateNative(56);
        long workerPtr = block + 8L;

        // Write bit-packed header ID & length header
        ForeignMemory.putInt(block, TYPE_SCRIPTING_THREAD);
        ForeignMemory.putInt(block + 4L, 1);

        long workQueuePtr = RingBuffer.instant(TypeRegister.ID_LONG, 2048);

        // Off-heap worker fields:
        // workerPtr + 0: state (0 = STOPPED, 1 = RUNNING)
        // workerPtr + 4: poolSize (1 thread per worker handle)
        // workerPtr + 8: workQueuePtr (RingBuffer handle)
        // workerPtr + 16: isCore (1 = CORE, 0 = USER)
        ForeignMemory.putInt(workerPtr, 0);                 // STOPPED
        ForeignMemory.putInt(workerPtr + 4L, 1);             // 1 thread
        ForeignMemory.putLong(workerPtr + 8L, workQueuePtr);
        ForeignMemory.putInt(workerPtr + 16L, isCore ? 1 : 0);

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
     * Instantiates and launches the background scripting worker thread for the specified handle.
     */
    public static synchronized boolean run(long workerPtr) {
        if (workerPtr == 0L || !isRegistered(workerPtr)) return false;
        if (isRunning(workerPtr)) return true; // Already running

        long queuePtr = getQueue(workerPtr);

        Thread worker = Thread.ofPlatform()
                .name("Anti-ScriptWorker-0x" + Long.toHexString(workerPtr).toUpperCase())
                .daemon(true)
                .start(() -> processQueue(workerPtr, queuePtr));

        Map.putObject(WORKER_MAP_PTR, workerPtr, worker);
        ForeignMemory.putInt(workerPtr, 1); // Set state to RUNNING
        return true;
    }

    /**
     * Submits a script execution payload handle to the specified scripting worker thread instance queue.
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
     * Stops execution for the specified scripting worker thread handle.
     */
    public static synchronized void stop(long workerPtr) {
        if (workerPtr == 0L) return;
        if (isCore(workerPtr)) return; // Protected core thread
        stopInternal(workerPtr);
    }

    private static void stopInternal(long workerPtr) {
        ForeignMemory.putInt(workerPtr, 0); // Set state to STOPPED

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
     * Returns total number of registered scripting worker thread handles in the manager pool.
     */
    public static int getWorkerCount() {
        return Map.size(WORKER_MAP_PTR);
    }

    /**
     * Launches all registered scripting worker thread handles in the pool.
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
     * Stops all registered scripting worker thread handles in the pool.
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
     * Stops and frees all registered scripting worker thread handles in the pool.
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
        while (ForeignMemory.getInt(workerPtr) == 1 && !Thread.currentThread().isInterrupted()) {
            if (queuePtr != 0L && !RingBuffer.isEmpty(queuePtr)) {
                long taskPtr = RingBuffer.poll(queuePtr);
                if (taskPtr != 0L) {
                    // process scripting task handle
                }
            } else {
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
