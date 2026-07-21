package struct;

import annotation.Volatile;

import annotation.Intention;
import annotation.Required;
import nio.ForeignMemory;
import oop.Stride;
import oop.TypeRegister;

import java.lang.foreign.Arena;

/**
 * Off-heap dynamic stride-based list implementation.
 */
@Intention("Zero-GC off-heap dynamic stride-based list implementation with self-describing type header, thread-safe memory mutations, and global MemoryRegistry integration.")
@Volatile
public final class List {


    @Required
    public static final int CLASS_ID = TypeRegister.ID_LIST;

    public static final int TYPE_LIST = TypeRegister.FORM_ARRAY | CLASS_ID; // 0xBB000012

    private static final int DEFAULT_CAPACITY = 1024;
    private static final long HEADER_SIZE = 24L; // 8B metadata header + 16B slot layout

    private static Arena poolArena;
    private static volatile boolean active;

    static {
        poolArena = Arena.ofShared();
        active = true;
    }

    private List() {}

    private static void checkActive() {
        if (!active) throw new IllegalStateException("List subsystem is not active!");
    }

    private static void checkBounds(long listPtr, int index) {
        if (listPtr == 0L) throw new NullPointerException("Accessing NULL off-heap list pointer!");
        int len = size(listPtr);
        if (index < 0 || index >= len) {
            throw new IndexOutOfBoundsException("Index " + index + " out of bounds for list size " + len);
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

    // create empty off-heap list for given class ID
    public static long instant(int generic) {
        return instant(generic, DEFAULT_CAPACITY);
    }

    // create empty off-heap list with initial capacity
    public static long instant(int generic, int initialCapacity) {
        checkActive();
        int stride = Stride.get(generic);
        int cap = (initialCapacity <= 0) ? DEFAULT_CAPACITY : initialCapacity;

        long headerBlock = ForeignMemory.allocateNative(HEADER_SIZE);
        long userPtr = headerBlock + 8L;

        ForeignMemory.putInt(headerBlock, TYPE_LIST);
        ForeignMemory.putInt(headerBlock + 4L, 0); // activeCount

        ForeignMemory.putInt(userPtr, generic);
        ForeignMemory.putInt(userPtr + 4L, stride);
        ForeignMemory.putInt(userPtr + 8L, cap);
        ForeignMemory.putInt(userPtr + 12L, 0); // padding

        long bufferBytes = (long) cap * stride;
        long alignedBytes = (bufferBytes + 7L) & ~7L;
        long dataBuffer = ForeignMemory.allocateNative(alignedBytes);
        ForeignMemory.putLong(userPtr + 16L, dataBuffer);

        return userPtr;
    }

    // append value or pointer to list
    public static synchronized void add(long listPtr, long valueOrPointer) {
        checkActive();
        if (listPtr == 0L) throw new NullPointerException("Writing to NULL off-heap list!");

        int count = size(listPtr);
        int cap = capacity(listPtr);
        int stride = stride(listPtr);
        long dataBuffer = dataBuffer(listPtr);

        if (count >= cap) {
            int newCap = cap + DEFAULT_CAPACITY;
            long newBytes = (long) newCap * stride;
            long alignedBytes = (newBytes + 7L) & ~7L;
            long newBuffer = ForeignMemory.allocateNative(alignedBytes);

            ForeignMemory.copy(dataBuffer, newBuffer, (long) count * stride);
            ForeignMemory.freeNative(dataBuffer);

            dataBuffer = newBuffer;
            ForeignMemory.putLong(listPtr + 16L, dataBuffer);
            ForeignMemory.putInt(listPtr + 8L, newCap);
        }

        long targetSlot = dataBuffer + ((long) count * stride);
        if (stride == 1) ForeignMemory.putByte(targetSlot, (byte) valueOrPointer);
        else if (stride == 2) ForeignMemory.putShort(targetSlot, (short) valueOrPointer);
        else if (stride == 4) ForeignMemory.putInt(targetSlot, (int) valueOrPointer);
        else ForeignMemory.putLong(targetSlot, valueOrPointer);

        ForeignMemory.putInt(listPtr - 4L, count + 1);
    }

    // get value or pointer at index
    public static synchronized long get(long listPtr, int index) {
        checkBounds(listPtr, index);
        int stride = stride(listPtr);
        long dataBuffer = dataBuffer(listPtr);
        long targetSlot = dataBuffer + ((long) index * stride);

        if (stride == 1) return ForeignMemory.getByte(targetSlot) & 0xFF;
        if (stride == 2) return ForeignMemory.getShort(targetSlot) & 0xFFFF;
        if (stride == 4) return ForeignMemory.getInt(targetSlot) & 0xFFFFFFFFL;
        return ForeignMemory.getLong(targetSlot);
    }

    // set value or pointer at index
    public static synchronized void set(long listPtr, int index, long valueOrPointer) {
        checkBounds(listPtr, index);
        int stride = stride(listPtr);
        long dataBuffer = dataBuffer(listPtr);
        long targetSlot = dataBuffer + ((long) index * stride);

        if (stride == 1) ForeignMemory.putByte(targetSlot, (byte) valueOrPointer);
        else if (stride == 2) ForeignMemory.putShort(targetSlot, (short) valueOrPointer);
        else if (stride == 4) ForeignMemory.putInt(targetSlot, (int) valueOrPointer);
        else ForeignMemory.putLong(targetSlot, valueOrPointer);
    }

    // remove element at index and shift remaining left
    public static synchronized void remove(long listPtr, int index) {
        checkBounds(listPtr, index);
        int count = size(listPtr);
        int stride = stride(listPtr);
        long dataBuffer = dataBuffer(listPtr);

        int numToMove = count - index - 1;
        if (numToMove > 0) {
            long srcSlot = dataBuffer + ((long) (index + 1) * stride);
            long destSlot = dataBuffer + ((long) index * stride);
            ForeignMemory.copy(srcSlot, destSlot, (long) numToMove * stride);
        }

        ForeignMemory.putInt(listPtr - 4L, count - 1);
    }


    // check if list is empty
    public static boolean isEmpty(long listPtr) {
        return Collection.isEmpty(listPtr);
    }

    // get list element count
    public static int size(long listPtr) {
        return Collection.size(listPtr);
    }

    public static int length(long listPtr) {
        return Collection.length(listPtr);
    }

    // compare two lists for equality
    public static boolean compare(long list1Ptr, long list2Ptr) {
        if (list1Ptr == list2Ptr) return true;
        if (list1Ptr == 0L || list2Ptr == 0L) return false;

        int sz1 = size(list1Ptr);
        int sz2 = size(list2Ptr);
        if (sz1 != sz2) return false;

        int str1 = stride(list1Ptr);
        int str2 = stride(list2Ptr);
        if (str1 != str2) return false;

        long buf1 = dataBuffer(list1Ptr);
        long buf2 = dataBuffer(list2Ptr);

        for (int i = 0; i < sz1; i++) {
            long slot1 = buf1 + ((long) i * str1);
            long slot2 = buf2 + ((long) i * str1);
            if (ForeignMemory.getLong(slot1) != ForeignMemory.getLong(slot2)) {
                return false;
            }
        }
        return true;
    }

    // free list data buffer and header back to native RAM
    public static void free(long listPtr) {
        checkActive();
        if (listPtr == 0L) return;

        long headerBlock = listPtr - 8L;
        int type = ForeignMemory.getInt(headerBlock);
        if (type == 0 || !TypeRegister.isArray(type)) {
            throw new IllegalStateException("Double free or corrupt list pointer: 0x" + Long.toHexString(listPtr).toUpperCase());
        }

        long dataBuffer = dataBuffer(listPtr);
        if (dataBuffer != 0L) {
            ForeignMemory.freeNative(dataBuffer);
        }

        ForeignMemory.putInt(headerBlock, 0);
        ForeignMemory.putInt(headerBlock + 4L, -1);
        ForeignMemory.freeNative(headerBlock);
    }

    // allocate list for custom struct
    public static long allocateStruct(int generic, int capacity) {
        return instant(generic, capacity);
    }

    // allocate list with pre-allocated items/slots initialized to size count
    public static synchronized long allocate(int generic, int count) {
        checkActive();
        if (count < 0) throw new IllegalArgumentException("Count must be non-negative!");
        int stride = Stride.get(generic);
        int cap = Math.max(DEFAULT_CAPACITY, count);

        long headerBlock = ForeignMemory.allocateNative(HEADER_SIZE);
        long userPtr = headerBlock + 8L;

        ForeignMemory.putInt(headerBlock, TYPE_LIST);
        ForeignMemory.putInt(headerBlock + 4L, count); // activeCount is set to count

        ForeignMemory.putInt(userPtr, generic);
        ForeignMemory.putInt(userPtr + 4L, stride);
        ForeignMemory.putInt(userPtr + 8L, cap);
        ForeignMemory.putInt(userPtr + 12L, 0); // padding

        long bufferBytes = (long) cap * stride;
        long alignedBytes = (bufferBytes + 7L) & ~7L;
        long dataBuffer = ForeignMemory.allocateNative(alignedBytes);
        ForeignMemory.setMemory(dataBuffer, alignedBytes, (byte) 0);
        ForeignMemory.putLong(userPtr + 16L, dataBuffer);

        return userPtr;
    }

    // append new uninitialized struct element slot and return its pointer
    public static synchronized long addStruct(long listPtr) {
        checkActive();
        if (listPtr == 0L) throw new NullPointerException("Writing to NULL off-heap list!");

        int count = size(listPtr);
        int cap = capacity(listPtr);
        int stride = stride(listPtr);
        long dataBuffer = dataBuffer(listPtr);

        if (count >= cap) {
            int newCap = cap + DEFAULT_CAPACITY;
            long newBytes = (long) newCap * stride;
            long alignedBytes = (newBytes + 7L) & ~7L;
            long newBuffer = ForeignMemory.allocateNative(alignedBytes);

            ForeignMemory.copy(dataBuffer, newBuffer, (long) count * stride);
            ForeignMemory.freeNative(dataBuffer);

            dataBuffer = newBuffer;
            ForeignMemory.putLong(listPtr + 16L, dataBuffer);
            ForeignMemory.putInt(listPtr + 8L, newCap);
        }

        long targetSlot = dataBuffer + ((long) count * stride);
        ForeignMemory.setMemory(targetSlot, stride, (byte) 0);
        ForeignMemory.putInt(listPtr - 4L, count + 1);
        return targetSlot;
    }

    // get pointer to struct element at index
    public static synchronized long getStruct(long listPtr, int index) {
        checkBounds(listPtr, index);
        int stride = stride(listPtr);
        long dataBuffer = dataBuffer(listPtr);
        return dataBuffer + (long) index * stride;
    }

    public static int elementClassId(long listPtr) {
        return Collection.elementClassId(listPtr);
    }

    public static int stride(long listPtr) {
        return Collection.stride(listPtr);
    }

    public static int capacity(long listPtr) {
        return Collection.capacity(listPtr);
    }

    public static long dataBuffer(long listPtr) {
        return Collection.dataBuffer(listPtr);
    }

    public static int classId() {
        return CLASS_ID;
    }
}

