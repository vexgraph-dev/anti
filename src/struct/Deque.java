package struct;

import annotation.Volatile;

import annotation.Draft;
import annotation.Intention;
import annotation.Required;
import nio.ForeignMemory;
import nio.MemoryRegistry;
import oop.Stride;
import oop.TypeRegister;

import java.lang.foreign.Arena;

/**
 * Off-heap circular buffer double-ended queue (Deque) implementation.
 */
@Draft
@Intention("Zero-GC off-heap dynamic stride-based double-ended queue (Deque) utilizing a circular array, thread-safe memory mutations, and global MemoryRegistry integration.")
@Volatile
public final class Deque {

    @Required
    public static final int CLASS_ID = TypeRegister.ID_DEQUE;

    public static final int TYPE_DEQUE = TypeRegister.FORM_ARRAY | CLASS_ID; // 0xBB000016

    private static final int DEFAULT_CAPACITY = 1024;
    private static final long HEADER_SIZE = 24L; // 8B metadata header + 16B slot layout

    private static Arena poolArena;
    private static volatile boolean active;

    static {
        poolArena = Arena.ofShared();
        active = true;
        MemoryRegistry.register(Deque::freeAll);
    }

    private Deque() {}

    private static void checkActive() {
        if (!active) throw new IllegalStateException("Deque subsystem is not active!");
    }

    private static void checkBounds(long dequePtr, int index) {
        if (dequePtr == 0L) throw new NullPointerException("Accessing NULL off-heap deque pointer!");
        int len = size(dequePtr);
        if (index < 0 || index >= len) {
            throw new IndexOutOfBoundsException("Index " + index + " out of bounds for deque size " + len);
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

    // create empty off-heap deque for given class ID
    public static long instant(int classId) {
        return instant(classId, DEFAULT_CAPACITY);
    }

    // create empty off-heap deque with initial capacity
    public static long instant(int classId, int initialCapacity) {
        checkActive();
        int stride = Stride.get(classId);
        int cap = (initialCapacity <= 0) ? DEFAULT_CAPACITY : initialCapacity;

        long headerBlock = ForeignMemory.allocateNative(HEADER_SIZE);
        long userPtr = headerBlock + 8L;

        ForeignMemory.putInt(headerBlock, TYPE_DEQUE);
        ForeignMemory.putInt(headerBlock + 4L, 0); // activeCount/size

        ForeignMemory.putInt(userPtr, classId);
        ForeignMemory.putInt(userPtr + 4L, stride);
        ForeignMemory.putInt(userPtr + 8L, cap);
        ForeignMemory.putInt(userPtr + 12L, 0); // head index

        long bufferBytes = (long) cap * stride;
        long alignedBytes = (bufferBytes + 7L) & ~7L;
        long dataBuffer = ForeignMemory.allocateNative(alignedBytes);
        ForeignMemory.putLong(userPtr + 16L, dataBuffer);

        return userPtr;
    }

    private static void ensureCapacity(long dequePtr) {
        int count = size(dequePtr);
        int cap = capacity(dequePtr);
        if (count >= cap) {
            int stride = stride(dequePtr);
            int newCap = cap + DEFAULT_CAPACITY;
            long newBytes = (long) newCap * stride;
            long alignedBytes = (newBytes + 7L) & ~7L;
            long newBuffer = ForeignMemory.allocateNative(alignedBytes);
            long oldBuffer = dataBuffer(dequePtr);
            int head = head(dequePtr);

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

            ForeignMemory.putLong(dequePtr + 16L, newBuffer);
            ForeignMemory.putInt(dequePtr + 8L, newCap);
            ForeignMemory.putInt(dequePtr + 12L, 0); // head index reset to 0
        }
    }

    private static long readSlot(long slot, int stride) {
        if (stride == 1) return ForeignMemory.getByte(slot) & 0xFF;
        if (stride == 2) return ForeignMemory.getShort(slot) & 0xFFFF;
        if (stride == 4) return ForeignMemory.getInt(slot) & 0xFFFFFFFFL;
        return ForeignMemory.getLong(slot);
    }

    private static void writeSlot(long slot, int stride, long val) {
        if (stride == 1) ForeignMemory.putByte(slot, (byte) val);
        else if (stride == 2) ForeignMemory.putShort(slot, (short) val);
        else if (stride == 4) ForeignMemory.putInt(slot, (int) val);
        else ForeignMemory.putLong(slot, val);
    }

    // append value or pointer to back of deque
    public static synchronized void addLast(long dequePtr, long valueOrPointer) {
        checkActive();
        if (dequePtr == 0L) throw new NullPointerException("Writing to NULL off-heap deque!");

        ensureCapacity(dequePtr);

        int count = size(dequePtr);
        int cap = capacity(dequePtr);
        int stride = stride(dequePtr);
        long dataBuffer = dataBuffer(dequePtr);
        int head = head(dequePtr);

        int targetIndex = (head + count) % cap;
        long targetSlot = dataBuffer + ((long) targetIndex * stride);
        writeSlot(targetSlot, stride, valueOrPointer);

        ForeignMemory.putInt(dequePtr - 4L, count + 1);
    }

    // prepend value or pointer to front of deque
    public static synchronized void addFirst(long dequePtr, long valueOrPointer) {
        checkActive();
        if (dequePtr == 0L) throw new NullPointerException("Writing to NULL off-heap deque!");

        ensureCapacity(dequePtr);

        int count = size(dequePtr);
        int cap = capacity(dequePtr);
        int stride = stride(dequePtr);
        long dataBuffer = dataBuffer(dequePtr);
        int head = head(dequePtr);

        int newHead = (head - 1 + cap) % cap;
        long targetSlot = dataBuffer + ((long) newHead * stride);
        writeSlot(targetSlot, stride, valueOrPointer);

        ForeignMemory.putInt(dequePtr + 12L, newHead);
        ForeignMemory.putInt(dequePtr - 4L, count + 1);
    }

    // remove and return the first element
    public static synchronized long removeFirst(long dequePtr) {
        checkActive();
        checkBounds(dequePtr, 0);

        int count = size(dequePtr);
        int cap = capacity(dequePtr);
        int stride = stride(dequePtr);
        long dataBuffer = dataBuffer(dequePtr);
        int head = head(dequePtr);

        long targetSlot = dataBuffer + ((long) head * stride);
        long value = readSlot(targetSlot, stride);

        int newHead = (head + 1) % cap;
        ForeignMemory.putInt(dequePtr + 12L, newHead);
        ForeignMemory.putInt(dequePtr - 4L, count - 1);

        return value;
    }

    // remove and return the last element
    public static synchronized long removeLast(long dequePtr) {
        checkActive();
        checkBounds(dequePtr, 0);

        int count = size(dequePtr);
        int cap = capacity(dequePtr);
        int stride = stride(dequePtr);
        long dataBuffer = dataBuffer(dequePtr);
        int head = head(dequePtr);

        int targetIndex = (head + count - 1) % cap;
        long targetSlot = dataBuffer + ((long) targetIndex * stride);
        long value = readSlot(targetSlot, stride);

        ForeignMemory.putInt(dequePtr - 4L, count - 1);

        return value;
    }

    // retrieve the first element without removing it
    public static synchronized long peekFirst(long dequePtr) {
        if (isEmpty(dequePtr)) return 0L;
        int stride = stride(dequePtr);
        long dataBuffer = dataBuffer(dequePtr);
        int head = head(dequePtr);
        long slot = dataBuffer + ((long) head * stride);
        return readSlot(slot, stride);
    }

    // retrieve the last element without removing it
    public static synchronized long peekLast(long dequePtr) {
        if (isEmpty(dequePtr)) return 0L;
        int count = size(dequePtr);
        int cap = capacity(dequePtr);
        int stride = stride(dequePtr);
        long dataBuffer = dataBuffer(dequePtr);
        int head = head(dequePtr);
        int lastIndex = (head + count - 1) % cap;
        long slot = dataBuffer + ((long) lastIndex * stride);
        return readSlot(slot, stride);
    }

    // retrieve element at logical index from the front
    public static synchronized long get(long dequePtr, int index) {
        checkBounds(dequePtr, index);
        int cap = capacity(dequePtr);
        int stride = stride(dequePtr);
        long dataBuffer = dataBuffer(dequePtr);
        int head = head(dequePtr);
        int targetIndex = (head + index) % cap;
        long slot = dataBuffer + ((long) targetIndex * stride);
        return readSlot(slot, stride);
    }

    public static boolean isEmpty(long dequePtr) {
        return Collection.isEmpty(dequePtr);
    }

    public static int size(long dequePtr) {
        return Collection.size(dequePtr);
    }

    public static int length(long dequePtr) {
        return Collection.length(dequePtr);
    }

    public static int elementClassId(long dequePtr) {
        return Collection.elementClassId(dequePtr);
    }

    public static int stride(long dequePtr) {
        return Collection.stride(dequePtr);
    }

    public static int capacity(long dequePtr) {
        return Collection.capacity(dequePtr);
    }

    public static long dataBuffer(long dequePtr) {
        return Collection.dataBuffer(dequePtr);
    }

    public static int head(long dequePtr) {
        return dequePtr == 0L ? 0 : ForeignMemory.getInt(dequePtr + 12L);
    }

    // free deque data buffer and header back to native RAM
    public static void free(long dequePtr) {
        checkActive();
        if (dequePtr == 0L) return;

        long headerBlock = dequePtr - 8L;
        int type = ForeignMemory.getInt(headerBlock);
        if (type == 0 || !TypeRegister.isArray(type)) {
            throw new IllegalStateException("Double free or corrupt deque pointer: 0x" + Long.toHexString(dequePtr).toUpperCase());
        }

        long dataBuffer = dataBuffer(dequePtr);
        if (dataBuffer != 0L) {
            ForeignMemory.freeNative(dataBuffer);
        }

        ForeignMemory.putInt(headerBlock, 0);
        ForeignMemory.putInt(headerBlock + 4L, -1);
        ForeignMemory.freeNative(headerBlock);
    }

    public static int classId() {
        return CLASS_ID;
    }
}
