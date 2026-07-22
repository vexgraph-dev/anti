package thread;

import annotation.Draft;
import annotation.Intention;
import annotation.Required;
import annotation.Volatile;
import net.PollRequest;
import oop.TypeRegister;

@Draft
@Intention("Static off-heap non-blocking worker thread manager executing PollRequest batches with zero instance constructors.")
@Volatile
public final class NetworkingThread {

    @Required
    public static final int CLASS_ID = TypeRegister.ID_POLL_REQUEST;

    private static volatile boolean running = false;
    private static volatile Thread workerThread = null;
    private static long workQueuePtr = 0L;

    private NetworkingThread() {}

    public static synchronized void init() {
        if (running && workerThread != null && workerThread.isAlive()) return;
        if (workQueuePtr == 0L) {
            workQueuePtr = RingBuffer.instant(TypeRegister.ID_POLL_REQUEST, 1024);
        }
        running = true;
        workerThread = Thread.ofPlatform().name("Anti-NetworkingWorkerThread").daemon(true).start(NetworkingThread::processQueue);
    }

    public static boolean isRunning() {
        return running && workerThread != null && workerThread.isAlive();
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
        if (workerThread != null) {
            workerThread.interrupt();
            workerThread = null;
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
