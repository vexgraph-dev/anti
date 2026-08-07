package thread;

import annotation.Volatile;

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
@Volatile
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

        ForeignMemory.setInt(block, TYPE_SPIN_LOCK);
        ForeignMemory.setInt(block + 4L, 1);
        ForeignMemory.setInt(userPtr, 0); // 0 = unlocked

        return userPtr;
    }

    // free a spinlock
    public static void free(long lockPtr) {
        if (lockPtr == 0L) return;
        long block = lockPtr - 8L;
        ForeignMemory.freeNative(block);
    }

    // lock word: 0 = free; otherwise owner-encoded: (threadId & 0x3FFFFFFF) << 1 | 1.
    // Single word so embedded one-word locks (e.g. RingBuffer header) keep working.

    private static int ticket(long threadId) {
        return (int) ((threadId & 0x3FFFFFFFL) << 1) | 1;
    }

    private static long ownerOf(int held) {
        return (held >>> 1) & 0x3FFFFFFFL;
    }

    // spin until lock is acquired (unbounded; use tryLock(lockPtr, timeoutNanos) for deadlock safety)
    public static void lock(long lockPtr) {
        if (lockPtr == 0L) throw new NullPointerException("Locking NULL spinlock pointer!");
        int ticket = ticket(Thread.currentThread().threadId());
        while (!(boolean) INT_VH.compareAndSet(GLOBAL_MEMORY, lockPtr, 0, ticket)) {
            Thread.onSpinWait();
        }
    }

    // try to acquire lock immediately, returns true if successful
    public static boolean tryLock(long lockPtr) {
        if (lockPtr == 0L) throw new NullPointerException("Locking NULL spinlock pointer!");
        return (boolean) INT_VH.compareAndSet(GLOBAL_MEMORY, lockPtr, 0, ticket(Thread.currentThread().threadId()));
    }

    // try to acquire lock within timeoutNanos; returns false on timeout, never spins forever
    public static boolean tryLock(long lockPtr, long timeoutNanos) {
        if (lockPtr == 0L) throw new NullPointerException("Locking NULL spinlock pointer!");
        long deadline = System.nanoTime() + timeoutNanos;
        while (!tryLock(lockPtr)) {
            if (timeoutNanos >= 0L && System.nanoTime() >= deadline) return false;
            Thread.onSpinWait();
        }
        return true;
    }

    // release lock, verifying the calling thread owns it
    public static void unlock(long lockPtr) {
        if (lockPtr == 0L) throw new NullPointerException("Unlocking NULL spinlock pointer!");
        long owner = Thread.currentThread().threadId();
        int held = (int) INT_VH.getVolatile(GLOBAL_MEMORY, lockPtr);
        if (held != 0 && ownerOf(held) != owner) {
            throw new IllegalStateException("SpinLock unlock by non-owning thread 0x" + Long.toHexString(owner));
        }
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
