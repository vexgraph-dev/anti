package cli;

import annotation.Draft;
import annotation.Volatile;
import primitive.string;
import thread.RingBuffer;

@Draft
public class Console {

    // An off-heap lock-free queue for logging string pointers
    @Volatile
    private static volatile long logQueueHead = 0L;

    private Console() {
    }

    public static long getLogQueueHead() {
        return logQueueHead;
    }

    public static void setLogQueueHead(long queueHead) {
        logQueueHead = queueHead;
    }

    @Draft
    public static void log(long stringPointer) {
        if (stringPointer == 0L) return;
        long queue = logQueueHead;
        if (queue != 0L) {
            // Push stringPointer to the lock-free off-heap queue safely
            if (!RingBuffer.offer(queue, stringPointer)) {
                // If queue is full, print directly and free the string to avoid leak
                String msg = string.get(stringPointer);
                if (msg != null) {
                    System.out.println(msg);
                }
                string.free(stringPointer);
            }
        } else {
            // If queue is not yet initialized/active, print directly and free
            String msg = string.get(stringPointer);
            if (msg != null) {
                System.out.println(msg);
            }
            string.free(stringPointer);
        }
    }

    @Draft
    public static void log(String message) {
        if (message == null) return;
        long stringPointer = string.allocate(message);
        log(stringPointer);
    }
}
