package thread;

import annotation.Draft;
import annotation.Intention;
import annotation.Required;
import annotation.Volatile;
import net.PollRequest;
import nio.ForeignMemory;
import oop.TypeRegister;
import struct.Map;
import struct.Array;

/**
 * Data-Oriented Design (DOD) off-heap Networking Thread Pool Manager.
 * <p>
 * Manages multiple independent off-heap worker thread handles (e.g. networking, pollingrequest, apirequests).
 */
@Draft
@Intention("Off-heap thread pool manager tracking worker handles via dogfooded struct.Map")
@Volatile
public final class NetworkingThread {

    @Required
    public static final int CLASS_ID = TypeRegister.ID_NETWORKING_THREAD;
    public static final int TYPE_NETWORKING_THREAD = TypeRegister.NETWORKING_THREAD_SINGLETON;

    // Central off-heap manager registry mapping workerPtr -> Thread instance
    private static final long WORKER_MAP_PTR = Map.instant(TypeRegister.ID_LONG, TypeRegister.ID_VARIABLE, 128);

    private NetworkingThread() {}

    public static int classId() {
        return CLASS_ID;
    }

    /**
     * Creates and registers a new off-heap worker thread handle.
     * Example usage:
     *   long networking = NetworkingThread.invoke();
     *   long pollingrequest = NetworkingThread.invoke();
     *   long apirequests = NetworkingThread.invoke();
     */
    public static long invoke() {
        long block = ForeignMemory.allocateNative(56);
        long workerPtr = block + 8L;

        // Write bit-packed header ID & length header
        ForeignMemory.putInt(block, TYPE_NETWORKING_THREAD);
        ForeignMemory.putInt(block + 4L, 1);

        long workQueuePtr = RingBuffer.instant(TypeRegister.ID_POLL_REQUEST, 2048);

        // Off-heap worker fields:
        // workerPtr + 0: state (0 = STOPPED, 1 = RUNNING)
        // workerPtr + 4: poolSize (1 thread per worker handle)
        // workerPtr + 8: workQueuePtr (RingBuffer handle)
        ForeignMemory.putInt(workerPtr, 0);                 // STOPPED
        ForeignMemory.putInt(workerPtr + 4L, 1);             // 1 thread
        ForeignMemory.putLong(workerPtr + 8L, workQueuePtr);

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
     * Instantiates and launches the background worker thread for the specified worker handle.
     */
    public static synchronized boolean run(long workerPtr) {
        if (workerPtr == 0L || !isRegistered(workerPtr)) return false;
        if (isRunning(workerPtr)) return true; // Already running

        long queuePtr = getQueue(workerPtr);

        Thread worker = Thread.ofPlatform()
                .name("Anti-NetWorker-0x" + Long.toHexString(workerPtr).toUpperCase())
                .daemon(true)
                .start(() -> processQueue(workerPtr, queuePtr));

        Map.putObject(WORKER_MAP_PTR, workerPtr, worker);
        ForeignMemory.putInt(workerPtr, 1); // Set state to RUNNING
        return true;
    }

    /**
     * Submits a PollRequest batch handle to the specified worker thread instance queue.
     * Returns false if worker thread is not running.
     */
    public static boolean submit(long workerPtr, long batchPtr) {
        if (workerPtr == 0L || batchPtr == 0L) return false;
        if (!isRunning(workerPtr)) {
            return false;
        }
        long queuePtr = getQueue(workerPtr);
        return RingBuffer.offer(queuePtr, batchPtr);
    }

    /**
     * Stops execution for the specified worker thread handle.
     */
    public static synchronized void stop(long workerPtr) {
        if (workerPtr == 0L) return;
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
        stop(workerPtr);

        long queuePtr = getQueue(workerPtr);
        if (queuePtr != 0L) {
            RingBuffer.free(queuePtr);
        }

        Map.remove(WORKER_MAP_PTR, workerPtr);

        long block = workerPtr - 8L;
        ForeignMemory.freeNative(block);
    }

    /**
     * Returns total number of registered worker thread handles in the manager pool.
     */
    public static int getWorkerCount() {
        return Map.size(WORKER_MAP_PTR);
    }

    /**
     * Launches all registered worker thread handles in the pool.
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
     * Stops all registered worker thread handles in the pool.
     */
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

    /**
     * Stops and frees all registered worker thread handles in the pool.
     */
    public static synchronized void freeAll() {
        long keysPtr = Map.getKeys(WORKER_MAP_PTR);
        if (keysPtr == 0L) return;
        int count = Array.length(keysPtr);
        for (int i = 0; i < count; i++) {
            long workerPtr = Array.get(keysPtr, i);
            free(workerPtr);
        }
        Array.free(keysPtr);
    }

    private static void processQueue(long workerPtr, long queuePtr) {
        while (ForeignMemory.getInt(workerPtr) == 1 && !Thread.currentThread().isInterrupted()) {
            if (queuePtr != 0L && !RingBuffer.isEmpty(queuePtr)) {
                long batchPtr = RingBuffer.poll(queuePtr);
                if (batchPtr != 0L) {
                    PollRequest.executeAll(batchPtr);
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
