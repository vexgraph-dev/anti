package thread;

import annotation.Draft;
import annotation.Intention;
import annotation.Required;
import annotation.Volatile;
import nio.ForeignMemory;
import oop.TypeRegister;
import struct.Array;
import struct.Map;

import java.util.concurrent.locks.LockSupport;

/**
 * Data-Oriented Design (DOD) off-heap Event & UI Thread Manager.
 *
 * Owns the event dispatch loop (Key, Mouse, Touch, and UI hit-testing/callbacks),
 * decoupling input processing and UI logic execution from the GPU draw loop (DrawThread).
 */
@Draft
@Intention("Off-heap event and UI dispatcher thread manager tracking worker handles via dogfooded struct.Map")
@Volatile
public final class EventThread {

    @Required
    public static final int CLASS_ID = TypeRegister.ID_EVENT_THREAD;
    public static final int TYPE_EVENT_THREAD = TypeRegister.EVENT_THREAD_SINGLETON;

    // Central off-heap manager registry mapping workerPtr -> Thread instance
    private static final long WORKER_MAP_PTR = Map.instant(TypeRegister.ID_LONG, TypeRegister.ID_VARIABLE, 64);

    private static final long MAIN_WORKER_PTR;

    static {
        MAIN_WORKER_PTR = invoke();
    }

    private EventThread() {}

    public static int classId() {
        return CLASS_ID;
    }

    public static long getMainWorker() {
        return MAIN_WORKER_PTR;
    }

    /**
     * Creates and registers a new off-heap event worker thread handle.
     */
    public static long invoke() {
        long block = ForeignMemory.allocateNative(64);
        long workerPtr = block + 8L;

        // Write bit-packed header ID & length header
        ForeignMemory.setInt(block, TYPE_EVENT_THREAD);
        ForeignMemory.setInt(block + 4L, 1);

        long workQueuePtr = RingBuffer.instant(TypeRegister.ID_LONG, 2048);

        // Off-heap worker fields:
        // workerPtr + 0: state (0 = STOPPED, 1 = RUNNING)
        // workerPtr + 4: poolSize (1 thread per worker handle)
        // workerPtr + 8: workQueuePtr (RingBuffer handle)
        // workerPtr + 16: reserved
        // workerPtr + 24: windowPtr bound to this worker
        // workerPtr + 32: role / flags
        ForeignMemory.setInt(workerPtr, 0);                 // STOPPED
        ForeignMemory.setInt(workerPtr + 4L, 1);             // 1 thread
        ForeignMemory.setLong(workerPtr + 8L, workQueuePtr);
        ForeignMemory.setInt(workerPtr + 16L, 0);
        ForeignMemory.setLong(workerPtr + 24L, 0L);
        ForeignMemory.setInt(workerPtr + 32L, 0);

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

    public static void bindWindow(long workerPtr, long windowPtr) {
        if (workerPtr == 0L) return;
        ForeignMemory.setLong(workerPtr + 24L, windowPtr);
    }

    /**
     * Starts the main background event worker thread.
     */
    public static synchronized void start() {
        run(MAIN_WORKER_PTR);
    }

    /**
     * Instantiates and launches the background event worker thread for the specified handle.
     */
    public static synchronized boolean run(long workerPtr) {
        if (workerPtr == 0L || !isRegistered(workerPtr)) return false;
        if (isRunning(workerPtr)) return true; // Already running

        long queuePtr = getQueue(workerPtr);

        ForeignMemory.setInt(workerPtr, 1); // Set state to RUNNING BEFORE starting the thread

        Thread worker = Thread.ofPlatform()
                .name("Anti-EventWorker-0x" + Long.toHexString(workerPtr).toUpperCase())
                .daemon(true)
                .start(() -> {
                    try {
                        processQueue(workerPtr, queuePtr);
                    } catch (Throwable t) {
                        System.out.println("[EventThread] Worker crashed: " + t.getMessage());
                        t.printStackTrace();
                    } finally {
                        ForeignMemory.setInt(workerPtr, 0); // Mark STOPPED
                    }
                });

        Map.putObject(WORKER_MAP_PTR, workerPtr, worker);
        return true;
    }

    /**
     * Stops the event worker thread handle.
     */
    public static synchronized void stop(long workerPtr) {
        if (workerPtr == 0L || !isRegistered(workerPtr)) return;
        ForeignMemory.setInt(workerPtr, 0); // Signal STOP
    }

    public static synchronized void stopAll() {
        long keysPtr = Map.getKeys(WORKER_MAP_PTR);
        if (keysPtr == 0L) return;
        int count = Array.length(keysPtr);
        for (int i = 0; i < count; i++) {
            long workerPtr = Array.get(keysPtr, i);
            stop(workerPtr);
        }
        Array.free(keysPtr);
    }

    public static synchronized void freeAll() {
        stopAll();
        long keysPtr = Map.getKeys(WORKER_MAP_PTR);
        if (keysPtr == 0L) return;
        int count = Array.length(keysPtr);
        for (int i = 0; i < count; i++) {
            long workerPtr = Array.get(keysPtr, i);
            if (workerPtr == MAIN_WORKER_PTR) continue;
            long queuePtr = getQueue(workerPtr);
            if (queuePtr != 0L) RingBuffer.free(queuePtr);
            ForeignMemory.freeNative(workerPtr - 8L);
        }
        Array.free(keysPtr);
    }

    public static synchronized void freeAllSystem() {
        stopAll();
        long keysPtr = Map.getKeys(WORKER_MAP_PTR);
        if (keysPtr == 0L) return;
        int count = Array.length(keysPtr);
        for (int i = 0; i < count; i++) {
            long workerPtr = Array.get(keysPtr, i);
            long queuePtr = getQueue(workerPtr);
            if (queuePtr != 0L) RingBuffer.free(queuePtr);
            ForeignMemory.freeNative(workerPtr - 8L);
        }
        Array.free(keysPtr);
        Map.free(WORKER_MAP_PTR);
    }

    private static void processQueue(long workerPtr, long queuePtr) {
        while (ForeignMemory.getInt(workerPtr) == 1 && !Thread.currentThread().isInterrupted()) {
            // 1. Drain raw OS input queues fed by Thread 0
            input.Key.dispatchEvents();
            input.Mouse.dispatchEvents();
            input.Touch.update();

            // 2. Process custom task handles if present in work queue
            if (queuePtr != 0L && !RingBuffer.isEmpty(queuePtr)) {
                long taskPtr = RingBuffer.poll(queuePtr);
                if (taskPtr != 0L) {
                    dispatch(taskPtr);
                }
            } else {
                // High-cadence adaptive sleep for sub-millisecond input response with 0% CPU burn
                LockSupport.parkNanos(250_000L); // 0.25ms
            }
        }
    }

    private static void dispatch(long taskPtr) {
        // Reserved for off-heap custom event packet dispatchers
    }
}
