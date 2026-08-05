package struct;

import annotation.Volatile;
import annotation.Draft;
import annotation.Intention;
import annotation.Required;
import nio.ForeignMemory;
import oop.Stride;
import oop.TypeRegister;

import java.lang.foreign.Arena;

/**
 * Off-heap circular buffer queue (FIFO) implementation.
 */
@Draft
@Intention("Zero-GC off-heap dynamic stride-based queue (Queue) utilizing a circular array, thread-safe memory mutations, and global MemoryRegistry integration.")
@Volatile
public final class Queue {

    @Required
    public static final int CLASS_ID = TypeRegister.ID_QUEUE;

    public static final int TYPE_QUEUE = TypeRegister.FORM_ARRAY | CLASS_ID; // 0xBB000076

    private static final int DEFAULT_CAPACITY = 1024;
    private static final long HEADER_SIZE = 24L; // 8B metadata header + 16B slot layout

    private static Arena poolArena;
    private static volatile boolean active;

    static {
        poolArena = Arena.ofShared();
        active = true;
    }

    private Queue() {}

    private static void checkActive() {
        if (!active) throw new IllegalStateException("Queue subsystem is not active!");
    }

    private static void checkBounds(long queuePtr) {
        if (queuePtr == 0L) throw new NullPointerException("Accessing NULL off-heap queue pointer!");
        if (isEmpty(queuePtr)) {
            throw new IndexOutOfBoundsException("Attempted FIFO operation on an empty queue!");
        }
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

    // create empty off-heap queue for given class ID
    public static long instant(int generic) {
        return instant(generic, DEFAULT_CAPACITY);
    }

    // create empty off-heap queue with initial capacity
    public static long instant(int generic, int initialCapacity) {
        checkActive();
        int stride = Stride.get(generic);
        int cap = (initialCapacity <= 0) ? DEFAULT_CAPACITY : initialCapacity;

        long headerBlock = ForeignMemory.allocateNative(HEADER_SIZE);
        long userPtr = headerBlock + 8L;

        ForeignMemory.setInt(headerBlock, TYPE_QUEUE);
        ForeignMemory.setInt(headerBlock + 4L, 0); // activeCount/size

        ForeignMemory.setInt(userPtr, generic);
        ForeignMemory.setInt(userPtr + 4L, stride);
        ForeignMemory.setInt(userPtr + 8L, cap);
        ForeignMemory.setInt(userPtr + 12L, 0); // head index

        long bufferBytes = (long) cap * stride;
        long alignedBytes = (bufferBytes + 7L) & ~7L;
        long dataBuffer = ForeignMemory.allocateNative(alignedBytes);
        ForeignMemory.setLong(userPtr + 16L, dataBuffer);

        return userPtr;
    }

    // allocate queue with pre-allocated items/slots initialized to size count
    public static synchronized long allocate(int generic, int count) {
        checkActive();
        if (count < 0) throw new IllegalArgumentException("Count must be non-negative!");
        int stride = Stride.get(generic);
        int cap = Math.max(DEFAULT_CAPACITY, count);

        long headerBlock = ForeignMemory.allocateNative(HEADER_SIZE);
        long userPtr = headerBlock + 8L;

        ForeignMemory.setInt(headerBlock, TYPE_QUEUE);
        ForeignMemory.setInt(headerBlock + 4L, count); // activeCount/size is set to count

        ForeignMemory.setInt(userPtr, generic);
        ForeignMemory.setInt(userPtr + 4L, stride);
        ForeignMemory.setInt(userPtr + 8L, cap);
        ForeignMemory.setInt(userPtr + 12L, 0); // head index

        long bufferBytes = (long) cap * stride;
        long alignedBytes = (bufferBytes + 7L) & ~7L;
        long dataBuffer = ForeignMemory.allocateNative(alignedBytes);
        ForeignMemory.setMemory(dataBuffer, alignedBytes, (byte) 0);
        ForeignMemory.setLong(userPtr + 16L, dataBuffer);

        return userPtr;
    }

    private static void ensureCapacity(long queuePtr) {
        int count = size(queuePtr);
        int cap = capacity(queuePtr);
        if (count >= cap) {
            int stride = stride(queuePtr);
            int newCap = cap + DEFAULT_CAPACITY;
            long newBytes = (long) newCap * stride;
            long alignedBytes = (newBytes + 7L) & ~7L;
            long newBuffer = ForeignMemory.allocateNative(alignedBytes);
            long oldBuffer = dataBuffer(queuePtr);
            int head = head(queuePtr);

            if (count > 0) {
                if (head == 0) {
                    ForeignMemory.copy(oldBuffer, newBuffer, (long) count * stride);
                } else {
                    int len1 = cap - head;
                    int len2 = head;
                    ForeignMemory.copy(oldBuffer + (long) head * stride, newBuffer, (long) len1 * stride);
                    ForeignMemory.copy(oldBuffer, newBuffer + (long) len1 * stride, (long) len2 * stride);
                }
            }

            if (oldBuffer != 0L) {
                ForeignMemory.freeNative(oldBuffer);
            }

            ForeignMemory.setLong(queuePtr + 16L, newBuffer);
            ForeignMemory.setInt(queuePtr + 8L, newCap);
            ForeignMemory.setInt(queuePtr + 12L, 0); // head index reset to 0
        }
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

    // enqueue / push an element to the back of the queue
    public static synchronized void push(long queuePtr, long valueOrPointer) {
        checkActive();
        if (queuePtr == 0L) throw new NullPointerException("Pushing to NULL off-heap queue!");

        ensureCapacity(queuePtr);

        int count = size(queuePtr);
        int cap = capacity(queuePtr);
        int stride = stride(queuePtr);
        long dataBuffer = dataBuffer(queuePtr);
        int head = head(queuePtr);

        int tail = (head + count) % cap;
        long targetSlot = dataBuffer + ((long) tail * stride);
        writeSlot(targetSlot, stride, valueOrPointer);

        ForeignMemory.setInt(queuePtr - 4L, count + 1);
    }

    // dequeue / pop an element from the front of the queue
    public static synchronized long pop(long queuePtr) {
        checkActive();
        checkBounds(queuePtr);

        int count = size(queuePtr);
        int cap = capacity(queuePtr);
        int stride = stride(queuePtr);
        long dataBuffer = dataBuffer(queuePtr);
        int head = head(queuePtr);

        long targetSlot = dataBuffer + ((long) head * stride);
        long value = readSlot(targetSlot, stride);

        int newHead = (head + 1) % cap;
        ForeignMemory.setInt(queuePtr + 12L, newHead);
        ForeignMemory.setInt(queuePtr - 4L, count - 1);

        return value;
    }

    // peek / retrieve the front element without removing it
    public static synchronized long peek(long queuePtr) {
        checkActive();
        checkBounds(queuePtr);

        int stride = stride(queuePtr);
        long dataBuffer = dataBuffer(queuePtr);
        int head = head(queuePtr);

        long targetSlot = dataBuffer + ((long) head * stride);
        return readSlot(targetSlot, stride);
    }

    public static synchronized void free(long queuePtr) {
        if (queuePtr == 0L) return;
        long dataBuffer = dataBuffer(queuePtr);
        if (dataBuffer != 0L) {
            ForeignMemory.freeNative(dataBuffer);
        }
        ForeignMemory.freeNative(queuePtr - 8L);
    }

    public static int size(long queuePtr) {
        if (queuePtr == 0L) return 0;
        return ForeignMemory.getInt(queuePtr - 4L);
    }

    public static boolean isEmpty(long queuePtr) {
        return size(queuePtr) == 0;
    }

    public static int capacity(long queuePtr) {
        if (queuePtr == 0L) return 0;
        return ForeignMemory.getInt(queuePtr + 8L);
    }

    public static int stride(long queuePtr) {
        if (queuePtr == 0L) return 0;
        return ForeignMemory.getInt(queuePtr + 4L);
    }

    public static int generic(long queuePtr) {
        if (queuePtr == 0L) return 0;
        return ForeignMemory.getInt(queuePtr);
    }

    public static long dataBuffer(long queuePtr) {
        if (queuePtr == 0L) return 0L;
        return ForeignMemory.getLong(queuePtr + 16L);
    }

    public static int head(long queuePtr) {
        if (queuePtr == 0L) return 0;
        return ForeignMemory.getInt(queuePtr + 12L);
    }
}
