package cli;

import annotation.Draft;
import annotation.Volatile;

@Draft
public class Console {

    // TODO: Implement an off-heap lock-free queue for logging string pointers
    @Volatile
    private static long logQueueHead = 0L;

    public Console() {
    }

    @Draft
    public static void log(long stringPointer) {
        // TODO: Push stringPointer to the lock-free off-heap queue safely
    }

    @Draft
    public static void log(String message) {
        // TODO: Allocate off-heap via primitive.string and pass pointer to log(long)
    }
}
