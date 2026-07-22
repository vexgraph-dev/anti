package thread;

import annotation.Draft;
import annotation.Intention;
import annotation.Required;
import annotation.Volatile;
import net.PollRequest;
import nio.ForeignMemory;
import oop.TypeRegister;

import java.util.concurrent.ConcurrentHashMap;

/**
 * Data-Oriented Design (DOD) off-heap Networking Thread instance manager.
 */
@Draft
@Intention("Off-heap networking worker handle lifecycle supporting invoke(), run(ptr), stop(ptr), and free(ptr).")
@Volatile
public final class NetworkingThread {

    @Required
    public static final int CLASS_ID = TypeRegister.ID_NETWORKING_THREAD;
    public static final int TYPE_NETWORKING_THREAD = TypeRegister.NETWORKING_THREAD_SINGLETON;

    private static final int DEFAULT_POOL_SIZE = 4;
    private static final ConcurrentHashMap<Long, Thread[]> WORKER_MAP = new ConcurrentHashMap<>();

    private NetworkingThread() {}

    public static int classId() {
        return CLASS_ID;
    }

    /**
     * Allocates a new off-heap NetworkingThread instance handle.
     */
    public static long invoke() {
        return invoke(DEFAULT_POOL_SIZE);
    }

    public static long invoke(int poolSize) {
        long block = ForeignMemory.allocateNative(56);
        long userPtr = block + 8L;

        // Write bit-packed header ID & stride length
        ForeignMemory.putInt(block, TYPE_NETWORKING_THREAD);
        ForeignMemory.putInt(block + 4L, 1);

        int threads = Math.max(1, poolSize);
        long workQueuePtr = RingBuffer.instant(TypeRegister.ID_POLL_REQUEST, 2048);

        // Off-heap memory fields
        ForeignMemory.putInt(userPtr, 0);                 // state: 0 = STOPPED, 1 = RUNNING
        ForeignMemory.putInt(userPtr + 4L, threads);      // poolSize
        ForeignMemory.putLong(userPtr + 8L, workQueuePtr); // RingBuffer handle ptr

        return userPtr;
    }

    public static boolean isRunning(long threadPtr) {
        if (threadPtr == 0L) return false;
        return ForeignMemory.getInt(threadPtr) == 1;
    }

    public static int getPoolSize(long threadPtr) {
        if (threadPtr == 0L) return 0;
        return ForeignMemory.getInt(threadPtr + 4L);
    }

    public static long getQueue(long threadPtr) {
        if (threadPtr == 0L) return 0L;
        return ForeignMemory.getLong(threadPtr + 8L);
    }

    /**
     * Launches background worker threads for the given off-heap handle.
     */
    public static synchronized boolean run(long threadPtr) {
        if (threadPtr == 0L) return false;
        int state = ForeignMemory.getInt(threadPtr);
        if (state == 1) return true; // Already running

        int threads = getPoolSize(threadPtr);
        long queuePtr = getQueue(threadPtr);

        Thread[] pool = new Thread[threads];
        for (int i = 0; i < threads; i++) {
            final int workerIdx = i;
            pool[i] = Thread.ofPlatform()
                    .name("Anti-NetWorker-0x" + Long.toHexString(threadPtr).toUpperCase() + "-" + workerIdx)
                    .daemon(true)
                    .start(() -> processQueue(threadPtr, queuePtr));
        }

        WORKER_MAP.put(threadPtr, pool);
        ForeignMemory.putInt(threadPtr, 1); // Set state to RUNNING
        return true;
    }

    /**
     * Submits a PollRequest batch handle to the off-heap thread queue.
     */
    public static boolean submit(long threadPtr, long batchPtr) {
        if (threadPtr == 0L || batchPtr == 0L) return false;
        if (!isRunning(threadPtr)) {
            run(threadPtr);
        }
        long queuePtr = getQueue(threadPtr);
        return RingBuffer.offer(queuePtr, batchPtr);
    }

    /**
     * Stops the worker threads for the given off-heap handle.
     */
    public static synchronized void stop(long threadPtr) {
        if (threadPtr == 0L) return;
        ForeignMemory.putInt(threadPtr, 0); // Set state to STOPPED

        Thread[] pool = WORKER_MAP.remove(threadPtr);
        if (pool != null) {
            for (Thread t : pool) {
                if (t != null) {
                    t.interrupt();
                }
            }
        }
    }

    /**
     * Stops workers and frees the off-heap native memory block.
     */
    public static void free(long threadPtr) {
        if (threadPtr == 0L) return;
        stop(threadPtr);

        long queuePtr = getQueue(threadPtr);
        if (queuePtr != 0L) {
            RingBuffer.free(queuePtr);
        }

        long block = threadPtr - 8L;
        ForeignMemory.freeNative(block);
    }

    private static void processQueue(long threadPtr, long queuePtr) {
        while (ForeignMemory.getInt(threadPtr) == 1 && !Thread.currentThread().isInterrupted()) {
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
