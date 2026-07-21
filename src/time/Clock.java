package time;

import annotation.Draft;
import annotation.Required;
import annotation.Intention;
import annotation.Volatile;
import nio.ForeignMemory;
import oop.TypeRegister;

/**
 * Off-Heap Clock Subsystem.
 * Tracks game/virtual simulation time with support for scaling and pausing.
 */
@Draft
@Intention("Zero-GC off-heap clock representing virtual time accumulators scaled by dynamic timeScale factors.")
public final class Clock {

    @Required
    public static final int CLASS_ID = TypeRegister.ID_CLOCK;

    private Clock() {}

    public static int classId() {
        return CLASS_ID;
    }

    /**
     * Allocates a new Clock instance off-heap.
     * Memory Layout:
     *   [userPtr - 8]: 32-bit packed Type ID
     *   [userPtr - 4]: 32-bit length (1)
     *   [userPtr + 0]: 64-bit double timeScale (default 1.0)
     *   [userPtr + 8]: 64-bit long baseRealTimeMillis
     *   [userPtr + 16]: 64-bit long lastTickRealTimeMillis
     *   [userPtr + 24]: 64-bit long virtualTimeMillis
     *   [userPtr + 32]: 64-bit long paused (0L = active, 1L = paused)
     *   [userPtr + 40]: 64-bit long padding
     */
    @Draft
    public static long allocate() {
        long block = ForeignMemory.allocateNative(56);
        long userPtr = block + 8L;

        // Write headers
        ForeignMemory.putInt(block, TypeRegister.CLOCK_SINGLETON);
        ForeignMemory.putInt(block + 4L, 1);

        // Initialize state
        long now = System.currentTimeMillis();
        ForeignMemory.putDouble(userPtr, 1.0); // timeScale
        ForeignMemory.putLong(userPtr + 8L, now); // baseRealTimeMillis
        ForeignMemory.putLong(userPtr + 16L, now); // lastTickRealTimeMillis
        ForeignMemory.putLong(userPtr + 24L, 0L); // virtualTimeMillis
        ForeignMemory.putLong(userPtr + 32L, 0L); // paused
        ForeignMemory.putLong(userPtr + 40L, 0L); // padding

        return userPtr;
    }

    @Draft
    public static void free(long ptr) {
        if (ptr == 0L) return;
        ForeignMemory.freeNative(ptr - 8L);
    }

    @Draft
    public static void tick(long ptr) {
        if (ptr == 0L) throw new NullPointerException("Accessing NULL off-heap pointer!");
        
        long now = System.currentTimeMillis();
        long lastReal = ForeignMemory.getLong(ptr + 16L);
        long elapsedReal = now - lastReal;
        
        // Update last real time
        ForeignMemory.putLong(ptr + 16L, now);

        long isPaused = ForeignMemory.getLong(ptr + 32L);
        if (isPaused == 0L) {
            double scale = ForeignMemory.getDouble(ptr);
            long virtualElapsed = (long) (elapsedReal * scale);
            long currentVirtual = ForeignMemory.getLong(ptr + 24L);
            ForeignMemory.putLong(ptr + 24L, currentVirtual + virtualElapsed);
        }
    }

    @Draft
    public static void setTimeScale(long ptr, double scale) {
        if (ptr == 0L) throw new NullPointerException("Accessing NULL off-heap pointer!");
        ForeignMemory.putDouble(ptr, scale);
    }

    @Draft
    public static double getTimeScale(long ptr) {
        if (ptr == 0L) throw new NullPointerException("Accessing NULL off-heap pointer!");
        return ForeignMemory.getDouble(ptr);
    }

    @Draft
    public static void setPaused(long ptr, boolean paused) {
        if (ptr == 0L) throw new NullPointerException("Accessing NULL off-heap pointer!");
        ForeignMemory.putLong(ptr + 32L, paused ? 1L : 0L);
    }

    @Draft
    public static boolean isPaused(long ptr) {
        if (ptr == 0L) throw new NullPointerException("Accessing NULL off-heap pointer!");
        return ForeignMemory.getLong(ptr + 32L) != 0L;
    }

    @Draft
    public static long getVirtualTimeMillis(long ptr) {
        if (ptr == 0L) throw new NullPointerException("Accessing NULL off-heap pointer!");
        return ForeignMemory.getLong(ptr + 24L);
    }

    @Draft
    public static void reset(long ptr) {
        if (ptr == 0L) throw new NullPointerException("Accessing NULL off-heap pointer!");
        long now = System.currentTimeMillis();
        ForeignMemory.putLong(ptr + 8L, now);
        ForeignMemory.putLong(ptr + 16L, now);
        ForeignMemory.putLong(ptr + 24L, 0L);
    }
}
