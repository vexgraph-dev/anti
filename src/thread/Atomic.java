package thread;

import annotation.Draft;
import annotation.Intention;
import annotation.Required;
import annotation.Unsafe;
import annotation.Volatile;
import nio.ForeignMemory;
import oop.TypeRegister;

/**
 * Zero-GC Off-Heap Atomic Variables & Synchronization Utility.
 * Provides atomic operations (CAS, getAndSet, getAndAdd, spin-waits) on off-heap
 * memory addresses without JVM heap allocations or java.util.concurrent wrappers.
 */
@Draft
@Volatile
@Intention("Zero-GC off-heap atomic variables and synchronization primitives acting directly on 64-bit native memory.")
public final class Atomic {

    @Required
    public static final int CLASS_ID = TypeRegister.ID_ATOMIC;

    public static final int TYPE_ATOMIC = TypeRegister.ATOMIC_SINGLETON;

    private Atomic() {}

    // =========================================================================
    // 1. ALLOCATION & LIFECYCLE
    // =========================================================================

    /**
     * Allocates an off-heap 1-byte atomic boolean slot.
     */
    public static long allocateBool(boolean initialValue) {
        long block = ForeignMemory.allocateNative(16L);
        long userPtr = block + 8L;
        ForeignMemory.setInt(block, TYPE_ATOMIC);
        ForeignMemory.setInt(block + 4L, 1);
        ForeignMemory.setVolatileByte(userPtr, (byte) (initialValue ? 1 : 0));
        return userPtr;
    }

    /**
     * Allocates an off-heap 4-byte atomic integer slot.
     */
    public static long allocateInt(int initialValue) {
        long block = ForeignMemory.allocateNative(16L);
        long userPtr = block + 8L;
        ForeignMemory.setInt(block, TYPE_ATOMIC);
        ForeignMemory.setInt(block + 4L, 4);
        ForeignMemory.setVolatile(userPtr, initialValue);
        return userPtr;
    }

    /**
     * Allocates an off-heap 8-byte atomic long slot.
     */
    public static long allocateLong(long initialValue) {
        long block = ForeignMemory.allocateNative(16L);
        long userPtr = block + 8L;
        ForeignMemory.setInt(block, TYPE_ATOMIC);
        ForeignMemory.setInt(block + 4L, 8);
        ForeignMemory.setVolatile(userPtr, initialValue);
        return userPtr;
    }

    /**
     * Allocates an off-heap 8-byte atomic pointer slot.
     */
    public static long allocatePointer(long initialTargetPtr) {
        return allocateLong(initialTargetPtr);
    }

    /**
     * Frees an allocated atomic variable slot.
     */
    public static void free(long ptr) {
        if (ptr == 0L) return;
        long block = ptr - 8L;
        ForeignMemory.freeNative(block);
    }

    // =========================================================================
    // 2. ATOMIC BOOLEAN OPERATIONS
    // =========================================================================

    public static boolean getBool(long ptr) {
        if (ptr == 0L) throw new NullPointerException("Reading from NULL atomic pointer!");
        return ForeignMemory.getVolatileByte(ptr) != 0;
    }

    public static void setBool(long ptr, boolean value) {
        if (ptr == 0L) throw new NullPointerException("Writing to NULL atomic pointer!");
        ForeignMemory.setVolatileByte(ptr, (byte) (value ? 1 : 0));
    }

    public static boolean compareAndSet(long ptr, boolean expected, boolean value) {
        if (ptr == 0L) throw new NullPointerException("CAS on NULL atomic pointer!");
        return ForeignMemory.compareAndSetByte(ptr, (byte) (expected ? 1 : 0), (byte) (value ? 1 : 0));
    }

    public static boolean getAndSet(long ptr, boolean value) {
        if (ptr == 0L) throw new NullPointerException("GetAndSet on NULL atomic pointer!");
        return ForeignMemory.getAndSetByte(ptr, (byte) (value ? 1 : 0)) != 0;
    }

    // =========================================================================
    // 3. ATOMIC INTEGER OPERATIONS
    // =========================================================================

    public static int getInt(long ptr) {
        if (ptr == 0L) throw new NullPointerException("Reading from NULL atomic pointer!");
        return ForeignMemory.getVolatileInt(ptr);
    }

    public static void setInt(long ptr, int value) {
        if (ptr == 0L) throw new NullPointerException("Writing to NULL atomic pointer!");
        ForeignMemory.setVolatile(ptr, value);
    }

    public static boolean compareAndSet(long ptr, int expected, int value) {
        if (ptr == 0L) throw new NullPointerException("CAS on NULL atomic pointer!");
        return ForeignMemory.compareAndSetInt(ptr, expected, value);
    }

    public static int getAndSet(long ptr, int value) {
        if (ptr == 0L) throw new NullPointerException("GetAndSet on NULL atomic pointer!");
        return ForeignMemory.getAndSetInt(ptr, value);
    }

    public static int getAndAdd(long ptr, int delta) {
        if (ptr == 0L) throw new NullPointerException("GetAndAdd on NULL atomic pointer!");
        return ForeignMemory.getAndAddInt(ptr, delta);
    }

    public static int incrementAndGet(long ptr) {
        return getAndAdd(ptr, 1) + 1;
    }

    public static int decrementAndGet(long ptr) {
        return getAndAdd(ptr, -1) - 1;
    }

    // =========================================================================
    // 4. ATOMIC LONG / POINTER OPERATIONS
    // =========================================================================

    public static long getLong(long ptr) {
        if (ptr == 0L) throw new NullPointerException("Reading from NULL atomic pointer!");
        return ForeignMemory.getVolatileLong(ptr);
    }

    public static void setLong(long ptr, long value) {
        if (ptr == 0L) throw new NullPointerException("Writing to NULL atomic pointer!");
        ForeignMemory.setVolatile(ptr, value);
    }

    public static boolean compareAndSet(long ptr, long expected, long value) {
        if (ptr == 0L) throw new NullPointerException("CAS on NULL atomic pointer!");
        return ForeignMemory.compareAndSetLong(ptr, expected, value);
    }

    public static long getAndSet(long ptr, long value) {
        if (ptr == 0L) throw new NullPointerException("GetAndSet on NULL atomic pointer!");
        return ForeignMemory.getAndSetLong(ptr, value);
    }

    public static long getAndAdd(long ptr, long delta) {
        if (ptr == 0L) throw new NullPointerException("GetAndAdd on NULL atomic pointer!");
        return ForeignMemory.getAndAddLong(ptr, delta);
    }

    public static long incrementAndGetLong(long ptr) {
        return getAndAdd(ptr, 1L) + 1L;
    }

    public static long decrementAndGetLong(long ptr) {
        return getAndAdd(ptr, -1L) - 1L;
    }

    // =========================================================================
    // 5. CONVENIENCE & VALUE MUTATION HELPERS
    // =========================================================================

    /**
     * Atomically toggles an off-heap boolean from true to false or false to true.
     * @return the new boolean value after toggling.
     */
    public static boolean toggleAtomic(long ptr) {
        if (ptr == 0L) throw new NullPointerException("Toggling NULL atomic pointer!");
        while (true) {
            boolean cur = getBool(ptr);
            boolean next = !cur;
            if (compareAndSet(ptr, cur, next)) {
                return next;
            }
        }
    }

    /**
     * Atomically adds delta to an int atomic and returns the new value.
     */
    public static int addAtomicValue(long ptr, int delta) {
        return getAndAdd(ptr, delta) + delta;
    }

    /**
     * Atomically adds delta to a long atomic and returns the new value.
     */
    public static long addAtomicValue(long ptr, long delta) {
        return getAndAdd(ptr, delta) + delta;
    }

    /**
     * Atomically increments an int atomic by the given delta and returns the new value.
     */
    public static int incrementAtomicValue(long ptr, int delta) {
        return addAtomicValue(ptr, delta);
    }

    /**
     * Atomically increments a long atomic by the given delta and returns the new value.
     */
    public static long incrementAtomicValue(long ptr, long delta) {
        return addAtomicValue(ptr, delta);
    }

    /**
     * Atomically increments an int atomic by 1 and returns the new value.
     */
    public static int incrementAtomicOne(long ptr) {
        return addAtomicValue(ptr, 1);
    }

    /**
     * Atomically increments a long atomic by 1 and returns the new value.
     */
    public static long incrementAtomicOneLong(long ptr) {
        return addAtomicValue(ptr, 1L);
    }

    /**
     * Atomically decrements an int atomic by 1 and returns the new value.
     */
    public static int decrementAtomicOne(long ptr) {
        return addAtomicValue(ptr, -1);
    }

    /**
     * Atomically decrements a long atomic by 1 and returns the new value.
     */
    public static long decrementAtomicOneLong(long ptr) {
        return addAtomicValue(ptr, -1L);
    }

    // =========================================================================
    // 6. SYNCHRONIZATION HELPERS & SPIN WAITS
    // =========================================================================

    /**
     * Spins until the off-heap boolean at ptr equals the target value.
     */
    public static void spinWait(long ptr, boolean targetValue) {
        if (ptr == 0L) throw new NullPointerException("Spin-wait on NULL atomic pointer!");
        while (getBool(ptr) != targetValue) {
            Thread.onSpinWait();
        }
    }

    /**
     * Spins until the off-heap boolean at ptr equals the target value, or timeout expires.
     * @return true if target value was reached before timeout, false on timeout.
     */
    public static boolean spinWait(long ptr, boolean targetValue, long timeoutNanos) {
        if (ptr == 0L) throw new NullPointerException("Spin-wait on NULL atomic pointer!");
        long deadline = System.nanoTime() + timeoutNanos;
        while (getBool(ptr) != targetValue) {
            if (timeoutNanos >= 0L && System.nanoTime() >= deadline) return false;
            Thread.onSpinWait();
        }
        return true;
    }

    /**
     * Spins until the off-heap int at ptr equals the target value.
     */
    public static void spinWait(long ptr, int targetValue) {
        if (ptr == 0L) throw new NullPointerException("Spin-wait on NULL atomic pointer!");
        while (getInt(ptr) != targetValue) {
            Thread.onSpinWait();
        }
    }

    // =========================================================================
    // 6. UNSAFE OPERATIONS (No Null Checks)
    // =========================================================================

    @Unsafe
    public static boolean compareAndSetUnsafe(long ptr, boolean expected, boolean value) {
        return ForeignMemory.compareAndSetByte(ptr, (byte) (expected ? 1 : 0), (byte) (value ? 1 : 0));
    }

    @Unsafe
    public static boolean compareAndSetUnsafe(long ptr, int expected, int value) {
        return ForeignMemory.compareAndSetInt(ptr, expected, value);
    }

    @Unsafe
    public static boolean compareAndSetUnsafe(long ptr, long expected, long value) {
        return ForeignMemory.compareAndSetLong(ptr, expected, value);
    }

    @Unsafe
    public static int getAndAddUnsafe(long ptr, int delta) {
        return ForeignMemory.getAndAddInt(ptr, delta);
    }

    @Unsafe
    public static long getAndAddUnsafe(long ptr, long delta) {
        return ForeignMemory.getAndAddLong(ptr, delta);
    }
}
