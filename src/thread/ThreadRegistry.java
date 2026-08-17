package thread;

import annotation.Required;
import nio.ForeignMemory;
import oop.TypeRegister;

public final class ThreadRegistry {

    @Required
    public static final int CLASS_ID = TypeRegister.ID_THREAD_REGISTRY;

    private static final int TABLE_SIZE = 256;
    private static final int MASK = TABLE_SIZE - 1;

    // Lock-free off-heap atomic table for thread IDs mapping to dense indexes
    private static final long THREAD_TABLE_PTR;

    static {
        long block = ForeignMemory.allocateNative(TABLE_SIZE * 8L);
        ForeignMemory.setMemory(block, TABLE_SIZE * 8L, (byte) 0);
        THREAD_TABLE_PTR = block;
    }

    private ThreadRegistry() {}

    /**
     * Gets or registers a unique index [0 ... 255] for the current thread.
     * Guaranteed to be O(1) lock-free and generate zero heap garbage.
     */
    public static int getThreadIndex() {
        long tid = Thread.currentThread().threadId();
        int index = (int) (tid & MASK);

        while (true) {
            long slotAddr = THREAD_TABLE_PTR + ((long) index * 8L);
            long registered = ForeignMemory.getVolatileLong(slotAddr);

            // Hit! Found this thread's registered index
            if (registered == tid) {
                return index;
            }

            // Empty slot! Register the thread here using CAS
            if (registered == 0L) {
                if (ForeignMemory.compareAndSetLong(slotAddr, 0L, tid)) {
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
