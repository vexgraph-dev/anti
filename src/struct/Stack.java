package struct;

import annotation.Volatile;

import annotation.Draft;
import annotation.Intention;
import annotation.Required;
import nio.ForeignMemory;
import oop.Stride;
import oop.TypeRegister;

import java.lang.foreign.Arena;

import nio.StringLookup;
/**
 * Off-heap dynamic stride-based stack (LIFO) implementation.
 */
@Draft
@Intention("Zero-GC off-heap dynamic stride-based stack implementation with thread-safe LIFO mutations and global MemoryRegistry integration.")
@Volatile
public final class Stack {

    @Required
    public static final int CLASS_ID = TypeRegister.ID_STACK;

    public static final int TYPE_STACK = TypeRegister.FORM_ARRAY | CLASS_ID; // 0xBB000015

    private static final int DEFAULT_CAPACITY = 1024;
    private static final long HEADER_SIZE = 24L; // 8B metadata header + 16B slot layout

    private static Arena poolArena;
    private static volatile boolean active;

    static {
        poolArena = Arena.ofShared();
        active = true;
    }

    private Stack() {}

    private static void checkActive() {
        if (!active) throw new IllegalStateException(StringLookup.getJavaString(419));
    }

    private static void checkBounds(long stackPtr) {
        if (stackPtr == 0L) throw new NullPointerException(StringLookup.getJavaString(420));
        if (isEmpty(stackPtr)) {
            throw new IndexOutOfBoundsException(StringLookup.getJavaString(421));
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

    // create empty off-heap stack for given class ID
    public static long instant(int generic) {
        return instant(generic, DEFAULT_CAPACITY);
    }

    // create empty off-heap stack with initial capacity
    public static long instant(int generic, int initialCapacity) {
        checkActive();
        int stride = Stride.get(generic);
        int cap = (initialCapacity <= 0) ? DEFAULT_CAPACITY : initialCapacity;

        long headerBlock = ForeignMemory.allocateNative(HEADER_SIZE);
        long userPtr = headerBlock + 8L;

        ForeignMemory.setInt(headerBlock, TYPE_STACK);
        ForeignMemory.setInt(headerBlock + 4L, 0); // activeCount/size

        ForeignMemory.setInt(userPtr, generic);
        ForeignMemory.setInt(userPtr + 4L, stride);
        ForeignMemory.setInt(userPtr + 8L, cap);
        ForeignMemory.setInt(userPtr + 12L, 0); // padding

        long bufferBytes = (long) cap * stride;
        long alignedBytes = (bufferBytes + 7L) & ~7L;
        long dataBuffer = ForeignMemory.allocateNative(alignedBytes);
        ForeignMemory.setLong(userPtr + 16L, dataBuffer);

        return userPtr;
    }

    // allocate stack with pre-allocated items/slots initialized to size count
    public static synchronized long allocate(int generic, int count) {
        checkActive();
        if (count < 0) throw new IllegalArgumentException(StringLookup.getJavaString(399));
        int stride = Stride.get(generic);
        int cap = Math.max(DEFAULT_CAPACITY, count);

        long headerBlock = ForeignMemory.allocateNative(HEADER_SIZE);
        long userPtr = headerBlock + 8L;

        ForeignMemory.setInt(headerBlock, TYPE_STACK);
        ForeignMemory.setInt(headerBlock + 4L, count); // activeCount/size is set to count

        ForeignMemory.setInt(userPtr, generic);
        ForeignMemory.setInt(userPtr + 4L, stride);
        ForeignMemory.setInt(userPtr + 8L, cap);
        ForeignMemory.setInt(userPtr + 12L, 0); // padding

        long bufferBytes = (long) cap * stride;
        long alignedBytes = (bufferBytes + 7L) & ~7L;
        long dataBuffer = ForeignMemory.allocateNative(alignedBytes);
        ForeignMemory.setMemory(dataBuffer, alignedBytes, (byte) 0);
        ForeignMemory.setLong(userPtr + 16L, dataBuffer);

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

    // push a value or pointer onto the stack
    public static synchronized void push(long stackPtr, long valueOrPointer) {
        checkActive();
        if (stackPtr == 0L) throw new NullPointerException(StringLookup.getJavaString(422));

        int count = size(stackPtr);
        int cap = capacity(stackPtr);
        int stride = stride(stackPtr);
        long dataBuffer = dataBuffer(stackPtr);

        if (count >= cap) {
            int newCap = cap + DEFAULT_CAPACITY;
            long newBytes = (long) newCap * stride;
            long alignedBytes = (newBytes + 7L) & ~7L;
            long newBuffer = ForeignMemory.allocateNative(alignedBytes);

            ForeignMemory.copy(dataBuffer, newBuffer, (long) count * stride);
            ForeignMemory.freeNative(dataBuffer);

            dataBuffer = newBuffer;
            ForeignMemory.setLong(stackPtr + 16L, dataBuffer);
            ForeignMemory.setInt(stackPtr + 8L, newCap);
        }

        long targetSlot = dataBuffer + ((long) count * stride);
        writeSlot(targetSlot, stride, valueOrPointer);

        ForeignMemory.setInt(stackPtr - 4L, count + 1);
    }

    // pop and return the top element from the stack
    public static synchronized long pop(long stackPtr) {
        checkActive();
        checkBounds(stackPtr);

        int count = size(stackPtr);
        int stride = stride(stackPtr);
        long dataBuffer = dataBuffer(stackPtr);

        int targetIndex = count - 1;
        long targetSlot = dataBuffer + ((long) targetIndex * stride);
        long value = readSlot(targetSlot, stride);

        ForeignMemory.setInt(stackPtr - 4L, targetIndex);

        return value;
    }

    // retrieve the top element without removing it
    public static synchronized long peek(long stackPtr) {
        checkActive();
        checkBounds(stackPtr);

        int count = size(stackPtr);
        int stride = stride(stackPtr);
        long dataBuffer = dataBuffer(stackPtr);

        int targetIndex = count - 1;
        long targetSlot = dataBuffer + ((long) targetIndex * stride);
        return readSlot(targetSlot, stride);
    }

    // get pointer to struct element at index
    public static synchronized long getStruct(long stackPtr, int index) {
        if (stackPtr == 0L) throw new NullPointerException(StringLookup.getJavaString(420));
        int len = size(stackPtr);
        if (index < 0 || index >= len) {
            throw new IndexOutOfBoundsException(StringLookup.getJavaString(34) + index + StringLookup.getJavaString(423) + len);
        }
        int stride = stride(stackPtr);
        long dataBuffer = dataBuffer(stackPtr);
        return dataBuffer + (long) index * stride;
    }

    public static boolean isEmpty(long stackPtr) {
        return Collection.isEmpty(stackPtr);
    }

    public static int size(long stackPtr) {
        return Collection.size(stackPtr);
    }

    public static int length(long stackPtr) {
        return Collection.length(stackPtr);
    }

    public static int elementClassId(long stackPtr) {
        return Collection.elementClassId(stackPtr);
    }

    public static int stride(long stackPtr) {
        return Collection.stride(stackPtr);
    }

    public static int capacity(long stackPtr) {
        return Collection.capacity(stackPtr);
    }

    public static long dataBuffer(long stackPtr) {
        return Collection.dataBuffer(stackPtr);
    }

    // free stack data buffer and header back to native RAM
    public static void free(long stackPtr) {
        checkActive();
        if (stackPtr == 0L) return;

        long headerBlock = stackPtr - 8L;
        int type = ForeignMemory.getInt(headerBlock);
        if (type == 0 || !TypeRegister.isArray(type)) {
            throw new IllegalStateException(StringLookup.getJavaString(424) + Long.toHexString(stackPtr).toUpperCase());
        }

        long dataBuffer = dataBuffer(stackPtr);
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
