package thread;

import annotation.Volatile;

import annotation.Draft;
import annotation.Intention;
import annotation.Required;
import nio.ForeignMemory;
import oop.Stride;
import oop.TypeRegister;

import java.lang.foreign.Arena;

/**
 * High-throughput off-heap Ring Buffer queue for inter-thread message dispatching.
 */
@Draft
@Intention("High-throughput spinlock-coordinated off-heap MPMC ring buffer coordinating threads with zero allocation and power-of-two masking.")
@Volatile
public final class RingBuffer {

    @Required
    public static final int CLASS_ID = TypeRegister.ID_RING_BUFFER;

    public static final int TYPE_RING_BUFFER = TypeRegister.FORM_ARRAY | CLASS_ID; // 0xBB00001A

    private static final int DEFAULT_CAPACITY = 1024;
    private static final long HEADER_SIZE = 48L; // 8B metadata header + 40B slot layout

    private static Arena poolArena;
    private static volatile boolean active;

    static {
        poolArena = Arena.ofShared();
        active = true;
    }

    private RingBuffer() {}

    private static void checkActive() {
        if (!active) throw new IllegalStateException("RingBuffer subsystem is not active!");
    }

    private static int nextPowerOfTwo(int value) {
        if (value <= 0) return 1;
        value--;
        value |= value >> 1;
        value |= value >> 2;
        value |= value >> 4;
        value |= value >> 8;
        value |= value >> 16;
        return value + 1;
    }

    // free all subsystem resources
    public static void freeAll() {
        if (active) {
            active = false;
            if (poolArena != null && poolArena.scope().isAlive()) {
                poolArena.close();
            }
        }
    }

    // create empty off-heap ring buffer for given class ID and capacity
    public static long instant(int classId) {
        return instant(classId, DEFAULT_CAPACITY);
    }

    public static long instant(int classId, int capacity) {
        checkActive();
        int stride = Stride.get(classId);
        int cap = nextPowerOfTwo(capacity <= 0 ? DEFAULT_CAPACITY : capacity);

        long headerBlock = ForeignMemory.allocateNative(HEADER_SIZE);
        long userPtr = headerBlock + 8L;

        // Write header type
        ForeignMemory.setInt(headerBlock, TYPE_RING_BUFFER);
        ForeignMemory.setInt(headerBlock + 4L, 0); // padding/size field unused, we use volatile w - r

        // Write user fields
        ForeignMemory.setInt(userPtr, classId);
        ForeignMemory.setInt(userPtr + 4L, cap);
        ForeignMemory.setVolatileLong(userPtr + 8L, 0L);  // writeIndex
        ForeignMemory.setVolatileLong(userPtr + 16L, 0L); // readIndex
        ForeignMemory.setInt(userPtr + 24L, 0);           // SpinLock field
        ForeignMemory.setInt(userPtr + 28L, stride);
        
        long bufferBytes = (long) cap * stride;
        long alignedBytes = (bufferBytes + 7L) & ~7L;
        long dataBuffer = ForeignMemory.allocateNative(alignedBytes);
        ForeignMemory.setLong(userPtr + 32L, dataBuffer);

        return userPtr;
    }

    private static long readSlot(long slot, int stride) {
        if (stride == 1) return ForeignMemory.getByte(slot) & 0xFF;
        if (stride == 2) return ForeignMemory.getShort(slot) & 0xFFFF;
        if (stride == 4) return ForeignMemory.getInt(slot) & 0xFFFFFFFFL;
        return ForeignMemory.getLong(slot);
    }

    private static void writeSlot(long slot, int stride, long val) {
        if (stride == 1) ForeignMemory.setByte(slot, (byte) val);
        else if (stride == 2) ForeignMemory.setShort(slot, (short) val);
        else if (stride == 4) ForeignMemory.setInt(slot, (int) val);
        else ForeignMemory.setLong(slot, val);
    }

    // push a value or pointer to the ring buffer, returns true if successful
    public static boolean offer(long ringBufferPtr, long valueOrPointer) {
        checkActive();
        if (ringBufferPtr == 0L) throw new NullPointerException("Writing to NULL off-heap RingBuffer!");

        long lockPtr = ringBufferPtr + 24L;
        SpinLock.lock(lockPtr);
        try {
            long w = ForeignMemory.getVolatileLong(ringBufferPtr + 8L);
            long r = ForeignMemory.getVolatileLong(ringBufferPtr + 16L);
            int cap = capacity(ringBufferPtr);

            if (w - r >= cap) {
                return false; // Full
            }

            int stride = stride(ringBufferPtr);
            long dataBuffer = dataBuffer(ringBufferPtr);
            long slot = dataBuffer + ((w & (cap - 1)) * stride);
            writeSlot(slot, stride, valueOrPointer);

            ForeignMemory.setVolatileLong(ringBufferPtr + 8L, w + 1);
            return true;
        } finally {
            SpinLock.unlock(lockPtr);
        }
    }

    // pop and return the oldest element from the ring buffer, returns 0 if empty
    public static long poll(long ringBufferPtr) {
        checkActive();
        if (ringBufferPtr == 0L) throw new NullPointerException("Reading from NULL off-heap RingBuffer!");

        long lockPtr = ringBufferPtr + 24L;
        SpinLock.lock(lockPtr);
        try {
            long w = ForeignMemory.getVolatileLong(ringBufferPtr + 8L);
            long r = ForeignMemory.getVolatileLong(ringBufferPtr + 16L);

            if (r >= w) {
                return 0L; // Empty
            }

            int cap = capacity(ringBufferPtr);
            int stride = stride(ringBufferPtr);
            long dataBuffer = dataBuffer(ringBufferPtr);
            long slot = dataBuffer + ((r & (cap - 1)) * stride);
            long val = readSlot(slot, stride);

            ForeignMemory.setVolatileLong(ringBufferPtr + 16L, r + 1);
            return val;
        } finally {
            SpinLock.unlock(lockPtr);
        }
    }

    // retrieve the oldest element without popping it, returns 0 if empty
    public static long peek(long ringBufferPtr) {
        checkActive();
        if (ringBufferPtr == 0L) throw new NullPointerException("Reading from NULL off-heap RingBuffer!");

        long lockPtr = ringBufferPtr + 24L;
        SpinLock.lock(lockPtr);
        try {
            long w = ForeignMemory.getVolatileLong(ringBufferPtr + 8L);
            long r = ForeignMemory.getVolatileLong(ringBufferPtr + 16L);

            if (r >= w) {
                return 0L; // Empty
            }

            int cap = capacity(ringBufferPtr);
            int stride = stride(ringBufferPtr);
            long dataBuffer = dataBuffer(ringBufferPtr);
            long slot = dataBuffer + ((r & (cap - 1)) * stride);
            return readSlot(slot, stride);
        } finally {
            SpinLock.unlock(lockPtr);
        }
    }

    public static int elementClassId(long ringBufferPtr) {
        return ringBufferPtr == 0L ? 0 : ForeignMemory.getInt(ringBufferPtr);
    }

    public static int capacity(long ringBufferPtr) {
        return ringBufferPtr == 0L ? 0 : ForeignMemory.getInt(ringBufferPtr + 4L);
    }

    public static long writeIndex(long ringBufferPtr) {
        return ringBufferPtr == 0L ? 0L : ForeignMemory.getVolatileLong(ringBufferPtr + 8L);
    }

    public static long readIndex(long ringBufferPtr) {
        return ringBufferPtr == 0L ? 0L : ForeignMemory.getVolatileLong(ringBufferPtr + 16L);
    }

    public static int size(long ringBufferPtr) {
        if (ringBufferPtr == 0L) return 0;
        long w = writeIndex(ringBufferPtr);
        long r = readIndex(ringBufferPtr);
        return (int) (w - r);
    }

    public static boolean isEmpty(long ringBufferPtr) {
        return size(ringBufferPtr) == 0;
    }

    public static boolean isFull(long ringBufferPtr) {
        return size(ringBufferPtr) >= capacity(ringBufferPtr);
    }

    public static int stride(long ringBufferPtr) {
        return ringBufferPtr == 0L ? 0 : ForeignMemory.getInt(ringBufferPtr + 28L);
    }

    public static long dataBuffer(long ringBufferPtr) {
        return ringBufferPtr == 0L ? 0L : ForeignMemory.getLong(ringBufferPtr + 32L);
    }

    // free ring buffer native RAM
    public static void free(long ringBufferPtr) {
        checkActive();
        if (ringBufferPtr == 0L) return;

        long headerBlock = ringBufferPtr - 8L;
        int type = ForeignMemory.getInt(headerBlock);
        if (type == 0 || (type & TypeRegister.MASK_FORM) != TypeRegister.FORM_ARRAY) {
            throw new IllegalStateException("Double free or corrupt ring buffer pointer: 0x" + Long.toHexString(ringBufferPtr).toUpperCase());
        }

        long dataBuffer = dataBuffer(ringBufferPtr);
        if (dataBuffer != 0L) {
            ForeignMemory.freeNative(dataBuffer);
        }

        ForeignMemory.setInt(headerBlock, 0);
        ForeignMemory.setInt(headerBlock + 4L, -1);
        ForeignMemory.freeNative(headerBlock);
    }

    public static int classId() {
        return CLASS_ID;
    }
}
