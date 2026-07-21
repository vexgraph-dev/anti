package thread;

import annotation.Draft;
import annotation.Intention;
import annotation.Required;
import nio.ForeignMemory;
import oop.TypeRegister;

import java.lang.foreign.MemorySegment;
import java.lang.foreign.ValueLayout;
import java.lang.invoke.VarHandle;

/**
 * Off-heap Atomic Spinlock based on FFM MemorySegment CAS updates.
 */
@Draft
@Intention("Ultra-low-latency spinlock acting directly on memory address flags to coordinate multi-threaded access without thread parking.")
public final class SpinLock {

    @Required
    public static final int CLASS_ID = TypeRegister.ID_SPIN_LOCK;

    public static final int TYPE_SPIN_LOCK = TypeRegister.FORM_SINGLETON | CLASS_ID; // 0xAA000019

    private static final VarHandle INT_VH = ValueLayout.JAVA_INT.varHandle();
    private static final MemorySegment GLOBAL_MEMORY = MemorySegment.ofAddress(0).reinterpret(Long.MAX_VALUE);

    private SpinLock() {}

    // allocate a new off-heap spinlock
    public static long allocate() {
        long block = ForeignMemory.allocateNative(16);
        long userPtr = block + 8L;

        ForeignMemory.putInt(block, TYPE_SPIN_LOCK);
        ForeignMemory.putInt(block + 4L, 1);
        ForeignMemory.putInt(userPtr, 0); // 0 = unlocked

        return userPtr;
    }

    // free a spinlock
    public static void free(long lockPtr) {
        if (lockPtr == 0L) return;
        long block = lockPtr - 8L;
        ForeignMemory.freeNative(block);
    }

    // spin until lock is acquired
    public static void lock(long lockPtr) {
        if (lockPtr == 0L) throw new NullPointerException("Locking NULL spinlock pointer!");
        while (!(boolean) INT_VH.compareAndSet(GLOBAL_MEMORY, lockPtr, 0, 1)) {
            Thread.onSpinWait();
        }
    }

    // try to acquire lock, returns true if successful
    public static boolean tryLock(long lockPtr) {
        if (lockPtr == 0L) throw new NullPointerException("Locking NULL spinlock pointer!");
        return (boolean) INT_VH.compareAndSet(GLOBAL_MEMORY, lockPtr, 0, 1);
    }

    // release lock
    public static void unlock(long lockPtr) {
        if (lockPtr == 0L) throw new NullPointerException("Unlocking NULL spinlock pointer!");
        INT_VH.setVolatile(GLOBAL_MEMORY, lockPtr, 0);
    }

    // check if lock is currently held
    public static boolean isLocked(long lockPtr) {
        if (lockPtr == 0L) return false;
        return (int) INT_VH.getVolatile(GLOBAL_MEMORY, lockPtr) != 0;
    }

    public static int classId() {
        return CLASS_ID;
    }
}
