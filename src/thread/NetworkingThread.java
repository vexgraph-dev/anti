package thread;

import annotation.Draft;
import annotation.Intention;
import annotation.Required;
import annotation.Volatile;
import net.PollRequest;
import oop.TypeRegister;

@Draft
@Intention("Static off-heap zero-GC dedicated worker thread pool executing PollRequest batches in parallel with zero heap allocations.")
@Volatile
public final class NetworkingThread {

    @Required
    public static final int CLASS_ID = TypeRegister.ID_POLL_REQUEST;

    private static final int DEFAULT_POOL_SIZE = 4;
    private static volatile boolean running = false;
    private static volatile Thread[] workerPool = null;
    private static long workQueuePtr = 0L;

    private NetworkingThread() {}

    public static synchronized void init() {
        init(DEFAULT_POOL_SIZE);
    }

    public static synchronized void init(int poolSize) {
        if (running && workerPool != null) return;
        if (workQueuePtr == 0L) {
            workQueuePtr = RingBuffer.instant(TypeRegister.ID_POLL_REQUEST, 2048);
        }
        int threads = Math.max(1, poolSize);
        running = true;
        workerPool = new Thread[threads];
        for (int i = 0; i < threads; i++) {
            workerPool[i] = Thread.ofPlatform()
                    .name("Anti-NetworkingWorker-" + i)
                    .daemon(true)
                    .start(NetworkingThread::processQueue);
        }
    }

    public static boolean isRunning() {
        return running && workerPool != null;
    }

    public static boolean submitBatch(long batchPtr) {
        if (batchPtr == 0L) return false;
        if (!isRunning()) {
            init();
        }
        return RingBuffer.offer(workQueuePtr, batchPtr);
    }

    public static synchronized void shutdown() {
        running = false;
        if (workerPool != null) {
            for (Thread worker : workerPool) {
                if (worker != null) {
                    worker.interrupt();
                }
            }
            workerPool = null;
        }
        if (workQueuePtr != 0L) {
            RingBuffer.free(workQueuePtr);
            workQueuePtr = 0L;
        }
    }

    private static void processQueue() {
        while (running && !Thread.currentThread().isInterrupted()) {
            if (workQueuePtr != 0L && !RingBuffer.isEmpty(workQueuePtr)) {
                long batchPtr = RingBuffer.poll(workQueuePtr);
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

    public static int classId() {
        return CLASS_ID;
    }
}
