package struct;

import annotation.Unsafe;

import annotation.Volatile;

import annotation.Intention;
import annotation.Required;
import nio.ForeignMemory;
import oop.Stride;
import oop.TypeRegister;

import java.lang.foreign.Arena;

import nio.StringLookup;
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
        if (!active) throw new IllegalStateException(StringLookup.getJavaString(394));
    }

    private static void checkBounds(long listPtr, int index) {
        if (listPtr == 0L) throw new NullPointerException(StringLookup.getJavaString(395));
        int len = size(listPtr);
        if (index < 0 || index >= len) {
            throw new IndexOutOfBoundsException(StringLookup.getJavaString(34) + index + StringLookup.getJavaString(396) + len);
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

        ForeignMemory.setInt(headerBlock, TYPE_LIST);
        ForeignMemory.setInt(headerBlock + 4L, 0); // activeCount

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

    // append value or pointer to list
    public static synchronized void add(long listPtr, long valueOrPointer) {
        checkActive();
        if (listPtr == 0L) throw new NullPointerException(StringLookup.getJavaString(397));

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
            ForeignMemory.setLong(listPtr + 16L, dataBuffer);
            ForeignMemory.setInt(listPtr + 8L, newCap);
        }

        long targetSlot = dataBuffer + ((long) count * stride);
        if (stride == 1) ForeignMemory.setByte(targetSlot, (byte) valueOrPointer);
        else if (stride == 2) ForeignMemory.setShort(targetSlot, (short) valueOrPointer);
        else if (stride == 4) ForeignMemory.setInt(targetSlot, (int) valueOrPointer);
        else ForeignMemory.setLong(targetSlot, valueOrPointer);

        ForeignMemory.setInt(listPtr - 4L, count + 1);
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

    @Volatile
    private static long readSlotVolatile(long slot, int stride) {
        if (stride == 1) return ForeignMemory.getVolatileByte(slot) & 0xFF;
        if (stride == 2) return ForeignMemory.getVolatileShort(slot) & 0xFFFF;
        if (stride == 4) return ForeignMemory.getVolatileInt(slot) & 0xFFFFFFFFL;
        return ForeignMemory.getVolatileLong(slot);
    }

    @Volatile
    private static void writeSlotVolatile(long slot, int stride, long val) {
        if (stride == 1) ForeignMemory.setVolatileByte(slot, (byte) val);
        else if (stride == 2) ForeignMemory.setVolatileShort(slot, (short) val);
        else if (stride == 4) ForeignMemory.setVolatileInt(slot, (int) val);
        else ForeignMemory.setVolatileLong(slot, val);
    }

    // get value or pointer at index (standard)
    public static long get(long listPtr, int index) {
        checkBounds(listPtr, index);
        int stride = stride(listPtr);
        long dataBuffer = dataBuffer(listPtr);
        long targetSlot = dataBuffer + ((long) index * stride);
        return readSlot(targetSlot, stride);
    }

    // set value or pointer at index (standard)
    public static void set(long listPtr, int index, long valueOrPointer) {
        checkBounds(listPtr, index);
        int stride = stride(listPtr);
        long dataBuffer = dataBuffer(listPtr);
        long targetSlot = dataBuffer + ((long) index * stride);
        writeSlot(targetSlot, stride, valueOrPointer);
    }

    // get value or pointer at index (volatile)
    @Volatile
    public static long getVolatile(long listPtr, int index) {
        checkBounds(listPtr, index);
        int stride = stride(listPtr);
        long dataBuffer = dataBuffer(listPtr);
        long targetSlot = dataBuffer + ((long) index * stride);
        return readSlotVolatile(targetSlot, stride);
    }

    // set value or pointer at index (volatile)
    @Volatile
    public static void setVolatile(long listPtr, int index, long valueOrPointer) {
        checkBounds(listPtr, index);
        int stride = stride(listPtr);
        long dataBuffer = dataBuffer(listPtr);
        long targetSlot = dataBuffer + ((long) index * stride);
        writeSlotVolatile(targetSlot, stride, valueOrPointer);
    }

    // get value or pointer at index (unsafe, no bounds check)
    @Unsafe
    public static long unsafeGet(long listPtr, int index) {
        int stride = stride(listPtr);
        long dataBuffer = dataBuffer(listPtr);
        long targetSlot = dataBuffer + ((long) index * stride);
        return readSlot(targetSlot, stride);
    }

    // set value or pointer at index (unsafe, no bounds check)
    @Unsafe
    public static void setUnsafe(long listPtr, int index, long valueOrPointer) {
        int stride = stride(listPtr);
        long dataBuffer = dataBuffer(listPtr);
        long targetSlot = dataBuffer + ((long) index * stride);
        writeSlot(targetSlot, stride, valueOrPointer);
    }

    // get value or pointer at index (unsafe volatile, no bounds check)
    @Unsafe
    @Volatile
    public static long getUnsafeVolatile(long listPtr, int index) {
        int stride = stride(listPtr);
        long dataBuffer = dataBuffer(listPtr);
        long targetSlot = dataBuffer + ((long) index * stride);
        return readSlotVolatile(targetSlot, stride);
    }

    // set value or pointer at index (unsafe volatile, no bounds check)
    @Unsafe
    @Volatile
    public static void setUnsafeVolatile(long listPtr, int index, long valueOrPointer) {
        int stride = stride(listPtr);
        long dataBuffer = dataBuffer(listPtr);
        long targetSlot = dataBuffer + ((long) index * stride);
        writeSlotVolatile(targetSlot, stride, valueOrPointer);
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

        ForeignMemory.setInt(listPtr - 4L, count - 1);
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
            throw new IllegalStateException(StringLookup.getJavaString(398) + Long.toHexString(listPtr).toUpperCase());
        }

        long dataBuffer = dataBuffer(listPtr);
        if (dataBuffer != 0L) {
            ForeignMemory.freeNative(dataBuffer);
        }

        ForeignMemory.setInt(headerBlock, 0);
        ForeignMemory.setInt(headerBlock + 4L, -1);
        ForeignMemory.freeNative(headerBlock);
    }

    // allocate list for custom struct
    public static long allocateStruct(int generic, int capacity) {
        return instant(generic, capacity);
    }

    // allocate list with pre-allocated items/slots initialized to size count
    public static synchronized long allocate(int generic, int count) {
        checkActive();
        if (count < 0) throw new IllegalArgumentException(StringLookup.getJavaString(399));
        int stride = Stride.get(generic);
        int cap = Math.max(DEFAULT_CAPACITY, count);

        long headerBlock = ForeignMemory.allocateNative(HEADER_SIZE);
        long userPtr = headerBlock + 8L;

        ForeignMemory.setInt(headerBlock, TYPE_LIST);
        ForeignMemory.setInt(headerBlock + 4L, count); // activeCount is set to count

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

    // append new uninitialized struct element slot and return its pointer
    public static synchronized long addStruct(long listPtr) {
        checkActive();
        if (listPtr == 0L) throw new NullPointerException(StringLookup.getJavaString(397));

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
            ForeignMemory.setLong(listPtr + 16L, dataBuffer);
            ForeignMemory.setInt(listPtr + 8L, newCap);
        }

        long targetSlot = dataBuffer + ((long) count * stride);
        ForeignMemory.setMemory(targetSlot, stride, (byte) 0);
        ForeignMemory.setInt(listPtr - 4L, count + 1);
        return targetSlot;
    }

    // get pointer to struct element at index
    public static long getStruct(long listPtr, int index) {
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

