package struct;

import annotation.Volatile;
import annotation.Draft;
import annotation.Intention;
import annotation.Required;
import nio.ForeignMemory;
import oop.Stride;
import oop.TypeRegister;

/**
 * Off-heap dynamic stride-based array implementation.
 */
@Draft
@Intention("Zero-GC off-heap dynamic stride-based array implementation with self-describing type header, thread-safe memory mutations, and global MemoryRegistry integration.")
@Volatile
public final class Array {

    @Required
    public static final int CLASS_ID = TypeRegister.ID_ARRAYS;

    public static final int TYPE_ARRAY = TypeRegister.FORM_ARRAY | CLASS_ID; // 0xBB00000E

    private static final long HEADER_SIZE = 24L; // 8B metadata header + 16B slot layout

    private static volatile boolean active = true;

    private Array() {}

    private static void checkActive() {
        if (!active) throw new IllegalStateException("Array subsystem is not active!");
    }

    private static void checkBounds(long arrayPtr, int index) {
        if (arrayPtr == 0L) throw new NullPointerException("Accessing NULL off-heap array pointer!");
        int len = size(arrayPtr);
        if (index < 0 || index >= len) {
            throw new IndexOutOfBoundsException("Index " + index + " out of bounds for array size " + len);
        }
    }

    // free all subsystem resources
    public static void freeAll() {
        active = false;
    }

    // create off-heap array for given class ID and length
    public static long instant(int generic, int length) {
        return allocate(generic, length);
    }

    // allocate array with pre-allocated items/slots initialized to size count
    public static synchronized long allocate(int generic, int count) {
        checkActive();
        if (count < 0) throw new IllegalArgumentException("Count must be non-negative!");
        int stride = Stride.get(generic);

        long headerBlock = ForeignMemory.allocateNative(HEADER_SIZE);
        long userPtr = headerBlock + 8L;

        ForeignMemory.putInt(headerBlock, TYPE_ARRAY);
        ForeignMemory.putInt(headerBlock + 4L, count); // activeCount is set to count

        ForeignMemory.putInt(userPtr, generic);
        ForeignMemory.putInt(userPtr + 4L, stride);
        ForeignMemory.putInt(userPtr + 8L, count); // capacity
        ForeignMemory.putInt(userPtr + 12L, 0); // padding

        long bufferBytes = (long) count * stride;
        long alignedBytes = (bufferBytes + 7L) & ~7L;
        long dataBuffer = ForeignMemory.allocateNative(alignedBytes);
        ForeignMemory.setMemory(dataBuffer, alignedBytes, (byte) 0);
        ForeignMemory.putLong(userPtr + 16L, dataBuffer);

        return userPtr;
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

    private static long readSlotVolatile(long slot, int stride) {
        if (stride == 1) return ForeignMemory.getByteVolatile(slot) & 0xFF;
        if (stride == 2) return ForeignMemory.getShortVolatile(slot) & 0xFFFF;
        if (stride == 4) return ForeignMemory.getIntVolatile(slot) & 0xFFFFFFFFL;
        return ForeignMemory.getLongVolatile(slot);
    }

    private static void writeSlotVolatile(long slot, int stride, long val) {
        if (stride == 1) ForeignMemory.putByteVolatile(slot, (byte) val);
        else if (stride == 2) ForeignMemory.putShortVolatile(slot, (short) val);
        else if (stride == 4) ForeignMemory.putIntVolatile(slot, (int) val);
        else ForeignMemory.putLongVolatile(slot, val);
    }

    // get value or pointer at index (standard)
    public static long get(long arrayPtr, int index) {
        checkBounds(arrayPtr, index);
        int stride = stride(arrayPtr);
        long dataBuffer = dataBuffer(arrayPtr);
        long targetSlot = dataBuffer + ((long) index * stride);
        return readSlot(targetSlot, stride);
    }

    // set value or pointer at index (standard)
    public static void set(long arrayPtr, int index, long valueOrPointer) {
        checkBounds(arrayPtr, index);
        int stride = stride(arrayPtr);
        long dataBuffer = dataBuffer(arrayPtr);
        long targetSlot = dataBuffer + ((long) index * stride);
        writeSlot(targetSlot, stride, valueOrPointer);
    }

    // get value or pointer at index (volatile)
    public static long getVolatile(long arrayPtr, int index) {
        checkBounds(arrayPtr, index);
        int stride = stride(arrayPtr);
        long dataBuffer = dataBuffer(arrayPtr);
        long targetSlot = dataBuffer + ((long) index * stride);
        return readSlotVolatile(targetSlot, stride);
    }

    // set value or pointer at index (volatile)
    public static void setVolatile(long arrayPtr, int index, long valueOrPointer) {
        checkBounds(arrayPtr, index);
        int stride = stride(arrayPtr);
        long dataBuffer = dataBuffer(arrayPtr);
        long targetSlot = dataBuffer + ((long) index * stride);
        writeSlotVolatile(targetSlot, stride, valueOrPointer);
    }

    // get value or pointer at index (unsafe, no bounds check)
    public static long unsafeGet(long arrayPtr, int index) {
        int stride = stride(arrayPtr);
        long dataBuffer = dataBuffer(arrayPtr);
        long targetSlot = dataBuffer + ((long) index * stride);
        return readSlot(targetSlot, stride);
    }

    // set value or pointer at index (unsafe, no bounds check)
    public static void unsafeSet(long arrayPtr, int index, long valueOrPointer) {
        int stride = stride(arrayPtr);
        long dataBuffer = dataBuffer(arrayPtr);
        long targetSlot = dataBuffer + ((long) index * stride);
        writeSlot(targetSlot, stride, valueOrPointer);
    }

    // get value or pointer at index (unsafe volatile, no bounds check)
    public static long unsafeVolatileGet(long arrayPtr, int index) {
        int stride = stride(arrayPtr);
        long dataBuffer = dataBuffer(arrayPtr);
        long targetSlot = dataBuffer + ((long) index * stride);
        return readSlotVolatile(targetSlot, stride);
    }

    // set value or pointer at index (unsafe volatile, no bounds check)
    public static void unsafeVolatileSet(long arrayPtr, int index, long valueOrPointer) {
        int stride = stride(arrayPtr);
        long dataBuffer = dataBuffer(arrayPtr);
        long targetSlot = dataBuffer + ((long) index * stride);
        writeSlotVolatile(targetSlot, stride, valueOrPointer);
    }

    // get pointer to struct element at index
    public static long getStruct(long arrayPtr, int index) {
        checkBounds(arrayPtr, index);
        int stride = stride(arrayPtr);
        long dataBuffer = dataBuffer(arrayPtr);
        return dataBuffer + (long) index * stride;
    }

    // free array data buffer and header back to native RAM
    public static void free(long arrayPtr) {
        checkActive();
        if (arrayPtr == 0L) return;

        long headerBlock = arrayPtr - 8L;
        int type = ForeignMemory.getInt(headerBlock);
        if (type == 0 || !TypeRegister.isArray(type)) {
            throw new IllegalStateException("Double free or corrupt array pointer: 0x" + Long.toHexString(arrayPtr).toUpperCase());
        }

        long dataBuffer = dataBuffer(arrayPtr);
        if (dataBuffer != 0L) {
            ForeignMemory.freeNative(dataBuffer);
        }

        ForeignMemory.putInt(headerBlock, 0);
        ForeignMemory.putInt(headerBlock + 4L, -1);
        ForeignMemory.freeNative(headerBlock);
    }

    public static boolean isEmpty(long arrayPtr) {
        return Collection.isEmpty(arrayPtr);
    }

    public static int size(long arrayPtr) {
        return Collection.size(arrayPtr);
    }

    public static int length(long arrayPtr) {
        return Collection.length(arrayPtr);
    }

    public static int elementClassId(long arrayPtr) {
        return Collection.elementClassId(arrayPtr);
    }

    public static int stride(long arrayPtr) {
        return Collection.stride(arrayPtr);
    }

    public static int capacity(long arrayPtr) {
        return Collection.capacity(arrayPtr);
    }

    public static long dataBuffer(long arrayPtr) {
        return Collection.dataBuffer(arrayPtr);
    }

    public static int classId() {
        return CLASS_ID;
    }
}
