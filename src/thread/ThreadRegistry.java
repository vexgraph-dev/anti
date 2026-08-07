package thread;

import java.lang.invoke.MethodHandles;
import java.lang.invoke.VarHandle;
import annotation.Required;
import oop.TypeRegister;

public final class ThreadRegistry {

    @Required
    public static final int CLASS_ID = TypeRegister.ID_THREAD_REGISTRY;

    private static final int TABLE_SIZE = 256;
    private static final int MASK = TABLE_SIZE - 1;

    // Lock-free atomic long array for thread IDs mapping to dense indexes
    private static final long[] THREAD_IDS = new long[TABLE_SIZE];
    private static final VarHandle ARRAY_VH = MethodHandles.arrayElementVarHandle(long[].class);

    private ThreadRegistry() {}

    /**
     * Gets or registers a unique index [0 ... 255] for the current thread.
     * Guaranteed to be O(1) lock-free and generate zero heap garbage.
     */
    public static int getThreadIndex() {
        long tid = Thread.currentThread().threadId();
        int index = (int) (tid & MASK);

        while (true) {
            long registered = (long) ARRAY_VH.getVolatile(THREAD_IDS, index);

            // Hit! Found this thread's registered index
            if (registered == tid) {
                return index;
            }

            // Empty slot! Register the thread here using CAS
            if (registered == 0L) {
                if (ARRAY_VH.compareAndSet(THREAD_IDS, index, 0L, tid)) {
                    return index;
                }
                // CAS failed, retry probing
                continue;
            }

            // Collision: Probe next slot
            index = (index + 1) & MASK;
        }
    }
}
