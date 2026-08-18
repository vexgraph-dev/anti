package primitive;

import annotation.Unsafe;
import annotation.Volatile;
import annotation.Required;

import bit.Bit64;
import nio.ForeignMemory;
import oop.TypeRegister;


public final class Long
{
    @Required
    public static final int CLASS_ID = TypeRegister.ID_LONG;
    public static final long MAX_VALUE = 9223372036854775807L;
    public static final long MIN_VALUE = -9223372036854775808L;

    public static final int TYPE_SINGLETON = TypeRegister.LONG_SINGLETON;
    public static final int TYPE_ARRAY     = TypeRegister.LONG_ARRAY;
    public static final int TYPE_MATRIX    = TypeRegister.LONG_POINTER;

    // =========================================================
    // ALLOCATION — DELEGATED TO THE BIT-WIDTH POOL (bit.Bit64)
    // =========================================================

    public static void freeAll()
    {
        Bit64.freeAll();
    }

    public static long allocateSingleton()
    {
        return Bit64.allocateSingleton(TYPE_SINGLETON);
    }

    public static long allocateArray(int length)
    {
        return Bit64.allocateArray(TYPE_ARRAY, length);
    }

    public static long allocateMatrix(int length)
    {
        return Bit64.allocateMatrix(TYPE_MATRIX, length);
    }

    public static long expandArray(long oldPointer, int newLength)
    {
        if(oldPointer == 0L) return allocateArray(newLength);
        int oldLength = length(oldPointer);
        long newPointer = allocateArray(newLength);

        int elementsToCopy = Math.min(oldLength, newLength);
        ForeignMemory.copy(oldPointer, newPointer, elementsToCopy * 8L);
        free(oldPointer);
        return newPointer;
    }

    public static long expandMatrix(long oldPointer, int newLength)
    {
        if(oldPointer == 0L) return allocateMatrix(newLength);
        int oldLength = length(oldPointer);
        long newPointer = allocateMatrix(newLength);

        int elementsToCopy = Math.min(oldLength, newLength);
        ForeignMemory.copy(oldPointer, newPointer, elementsToCopy * 8L);
        free(oldPointer);
        return newPointer;
    }

    public static void free(long pointer)
    {
        Bit64.free(pointer);
    }

    // =========================================================================
    // ARCHITECTURAL CHECKS & METADATA
    // =========================================================================

    private static void checkBounds(long pointer, int index)
    {
        if(pointer == 0L) throw new NullPointerException("Checking bounds on NULL off-heap pointer!");
        int len = length(pointer);
        if(index < 0 || index >= len) throw new IndexOutOfBoundsException("Index " + index + " out of bounds for off-heap length " + len + " (Ptr: 0x" + java.lang.Long.toHexString(pointer).toUpperCase() + ", Type: 0x" + Integer.toHexString(type(pointer)).toUpperCase() + ")");
    }

    public static int classId() { return CLASS_ID; }
    public static int type(long pointer) { return ForeignMemory.getUnsafeInt(pointer - 8L); }
    public static int length(long pointer) { return ForeignMemory.getUnsafeInt(pointer - 4L); }
    public static int classId(long pointer) { return TypeRegister.getClassId(type(pointer)); }
    public static boolean isSingleton(long pointer) { return TypeRegister.isSingleton(type(pointer)); }
    public static boolean isArray(long pointer) { return TypeRegister.isArray(type(pointer)); }
    public static boolean isPointer(long pointer) { return TypeRegister.isPointer(type(pointer)); }

    // =========================================================================
    // 1. STANDARD SAFE OPERATIONS (Bounds & Null Checked)
    // =========================================================================

    public static long get(long pointer) {
        if(pointer == 0L) throw new NullPointerException("Accessing NULL off-heap pointer!");
        if(classId(pointer) != CLASS_ID) throw new IllegalArgumentException("Pointer 0x" + java.lang.Long.toHexString(pointer).toUpperCase() + " is Class ID " + classId(pointer) + ", expected Long");
        return ForeignMemory.getLong(pointer);
    }

    public static long get(long pointer, int index) {
        checkBounds(pointer, index);
        if(classId(pointer) != CLASS_ID) throw new IllegalArgumentException("Pointer 0x" + java.lang.Long.toHexString(pointer).toUpperCase() + " is Class ID " + classId(pointer) + ", expected Long");
        return ForeignMemory.getLong(pointer + (index * 8L));
    }

    public static long getPointer(long matrixPointer, int index) {
        if(matrixPointer == 0L) throw new NullPointerException("Accessing NULL matrix pointer!");
        if(!isPointer(matrixPointer)) throw new IllegalArgumentException("Expected Pointer Array (Matrix)");
        checkBounds(matrixPointer, index);
        return ForeignMemory.getLong(matrixPointer + (index * 8L));
    }

    public static void set(long pointer, long value) {
        if(pointer == 0L) throw new NullPointerException("Writing to NULL off-heap pointer!");
        if(classId(pointer) != CLASS_ID) throw new IllegalArgumentException("Pointer 0x" + java.lang.Long.toHexString(pointer).toUpperCase() + " is Class ID " + classId(pointer) + ", expected Long");
        ForeignMemory.set(pointer, value);
    }

    public static void set(long pointer, int index, long value) {
        checkBounds(pointer, index);
        if(classId(pointer) != CLASS_ID) throw new IllegalArgumentException("Pointer 0x" + java.lang.Long.toHexString(pointer).toUpperCase() + " is Class ID " + classId(pointer) + ", expected Long");
        ForeignMemory.set(pointer + (index * 8L), value);
    }

    public static void setPointer(long matrixPointer, int index, long targetPointer) {
        if(matrixPointer == 0L) throw new NullPointerException("Writing to NULL matrix pointer!");
        if(!isPointer(matrixPointer)) throw new IllegalArgumentException("Expected Pointer Array (Matrix)");
        checkBounds(matrixPointer, index);
        ForeignMemory.set(matrixPointer + (index * 8L), targetPointer);
    }

    // =========================================================================
    // 2. UNSAFE OPERATIONS (No Checks, Maximum Speed)
    // =========================================================================

    @Unsafe
    public static long getUnsafe(long pointer) {
        return ForeignMemory.getUnsafeLong(pointer);
    }

    @Unsafe
    public static long getUnsafe(long pointer, int index) {
        return ForeignMemory.getUnsafeLong(pointer + (index * 8L));
    }

    @Unsafe
    public static long getUnsafePointer(long matrixPointer, int index) {
        return ForeignMemory.getUnsafeLong(matrixPointer + (index * 8L));
    }

    @Unsafe
    public static void setUnsafe(long pointer, long value) {
        ForeignMemory.setUnsafe(pointer, value);
    }

    @Unsafe
    public static void setUnsafe(long pointer, int index, long value) {
        ForeignMemory.setUnsafe(pointer + (index * 8L), value);
    }

    @Unsafe
    public static void setUnsafePointer(long matrixPointer, int index, long targetPointer) {
        ForeignMemory.setUnsafe(matrixPointer + (index * 8L), targetPointer);
    }

    // =========================================================================
    // 3. VOLATILE OPERATIONS (Thread-Safe, Bounds Checked)
    // =========================================================================

    @Volatile
    public static long getVolatile(long pointer) {
        if(pointer == 0L) throw new NullPointerException("Reading from NULL off-heap pointer!");
        if(classId(pointer) != CLASS_ID) throw new IllegalArgumentException("Pointer 0x" + java.lang.Long.toHexString(pointer).toUpperCase() + " is Class ID " + classId(pointer) + ", expected Long");
        return ForeignMemory.getVolatileLong(pointer);
    }

    @Volatile
    public static long getVolatile(long pointer, int index) {
        checkBounds(pointer, index);
        if(classId(pointer) != CLASS_ID) throw new IllegalArgumentException("Pointer 0x" + java.lang.Long.toHexString(pointer).toUpperCase() + " is Class ID " + classId(pointer) + ", expected Long");
        return ForeignMemory.getVolatileLong(pointer + (index * 8L));
    }

    @Volatile
    public static long getVolatilePointer(long matrixPointer, int index) {
        if(matrixPointer == 0L) throw new NullPointerException("Accessing NULL matrix pointer!");
        checkBounds(matrixPointer, index);
        return ForeignMemory.getVolatileLong(matrixPointer + (index * 8L));
    }

    @Volatile
    public static void setVolatile(long pointer, long value) {
        if(pointer == 0L) throw new NullPointerException("Writing to NULL off-heap pointer!");
        if(classId(pointer) != CLASS_ID) throw new IllegalArgumentException("Pointer 0x" + java.lang.Long.toHexString(pointer).toUpperCase() + " is Class ID " + classId(pointer) + ", expected Long");
        ForeignMemory.setVolatile(pointer, value);
    }

    @Volatile
    public static void setVolatile(long pointer, int index, long value) {
        checkBounds(pointer, index);
        if(classId(pointer) != CLASS_ID) throw new IllegalArgumentException("Pointer 0x" + java.lang.Long.toHexString(pointer).toUpperCase() + " is Class ID " + classId(pointer) + ", expected Long");
        ForeignMemory.setVolatile(pointer + (index * 8L), value);
    }

    @Volatile
    public static void setVolatilePointer(long matrixPointer, int index, long targetPointer) {
        if(matrixPointer == 0L) throw new NullPointerException("Writing to NULL matrix pointer!");
        checkBounds(matrixPointer, index);
        ForeignMemory.setVolatile(matrixPointer + (index * 8L), targetPointer);
    }

    public static boolean compareAndSet(long pointer, long expected, long value) {
        if(pointer == 0L) throw new NullPointerException("Writing to NULL off-heap pointer!");
        if(classId(pointer) != CLASS_ID) throw new IllegalArgumentException("Pointer 0x" + java.lang.Long.toHexString(pointer).toUpperCase() + " is Class ID " + classId(pointer) + ", expected Long");
        return ForeignMemory.compareAndSetLong(pointer, expected, value);
    }

    public static boolean compareAndSet(long pointer, int index, long expected, long value) {
        checkBounds(pointer, index);
        if(classId(pointer) != CLASS_ID) throw new IllegalArgumentException("Pointer 0x" + java.lang.Long.toHexString(pointer).toUpperCase() + " is Class ID " + classId(pointer) + ", expected Long");
        return ForeignMemory.compareAndSetLong(pointer + (index * 8L), expected, value);
    }

    public static long getAndSet(long pointer, long value) {
        if(pointer == 0L) throw new NullPointerException("Writing to NULL off-heap pointer!");
        if(classId(pointer) != CLASS_ID) throw new IllegalArgumentException("Pointer 0x" + java.lang.Long.toHexString(pointer).toUpperCase() + " is Class ID " + classId(pointer) + ", expected Long");
        return ForeignMemory.getAndSetLong(pointer, value);
    }

    public static long getAndSet(long pointer, int index, long value) {
        checkBounds(pointer, index);
        if(classId(pointer) != CLASS_ID) throw new IllegalArgumentException("Pointer 0x" + java.lang.Long.toHexString(pointer).toUpperCase() + " is Class ID " + classId(pointer) + ", expected Long");
        return ForeignMemory.getAndSetLong(pointer + (index * 8L), value);
    }

    public static long getAndAdd(long pointer, long delta) {
        if(pointer == 0L) throw new NullPointerException("Writing to NULL off-heap pointer!");
        if(classId(pointer) != CLASS_ID) throw new IllegalArgumentException("Pointer 0x" + java.lang.Long.toHexString(pointer).toUpperCase() + " is Class ID " + classId(pointer) + ", expected Long");
        return ForeignMemory.getAndAddLong(pointer, delta);
    }

    public static long getAndAdd(long pointer, int index, long delta) {
        checkBounds(pointer, index);
        if(classId(pointer) != CLASS_ID) throw new IllegalArgumentException("Pointer 0x" + java.lang.Long.toHexString(pointer).toUpperCase() + " is Class ID " + classId(pointer) + ", expected Long");
        return ForeignMemory.getAndAddLong(pointer + (index * 8L), delta);
    }

    public static long incrementAndGet(long pointer) {
        return getAndAdd(pointer, 1L) + 1L;
    }

    public static long decrementAndGet(long pointer) {
        return getAndAdd(pointer, -1L) - 1L;
    }

    // =========================================================================
    // 4. UNSAFE & VOLATILE OPERATIONS (Thread-Safe, No Checks)
    // =========================================================================

    @Unsafe
    @Volatile
    public static long getUnsafeVolatile(long pointer) {
        return ForeignMemory.getUnsafeVolatileLong(pointer);
    }

    @Unsafe
    @Volatile
    public static long getUnsafeVolatile(long pointer, int index) {
        return ForeignMemory.getUnsafeVolatileLong(pointer + (index * 8L));
    }

    @Unsafe
    @Volatile
    public static long getUnsafeVolatilePointer(long matrixPointer, int index) {
        return ForeignMemory.getUnsafeVolatileLong(matrixPointer + (index * 8L));
    }

    @Unsafe
    @Volatile
    public static void setUnsafeVolatile(long pointer, long value) {
        ForeignMemory.setUnsafeVolatile(pointer, value);
    }

    @Unsafe
    @Volatile
    public static void setUnsafeVolatile(long pointer, int index, long value) {
        ForeignMemory.setUnsafeVolatile(pointer + (index * 8L), value);
    }

    @Unsafe
    @Volatile
    public static void setUnsafeVolatilePointer(long matrixPointer, int index, long targetPointer) {
        ForeignMemory.setUnsafeVolatile(matrixPointer + (index * 8L), targetPointer);
    }

    @Unsafe
    public static boolean compareAndSetUnsafe(long pointer, long expected, long value) {
        return ForeignMemory.compareAndSetLong(pointer, expected, value);
    }

    @Unsafe
    public static boolean compareAndSetUnsafe(long pointer, int index, long expected, long value) {
        return ForeignMemory.compareAndSetLong(pointer + (index * 8L), expected, value);
    }

    @Unsafe
    public static long getAndSetUnsafe(long pointer, long value) {
        return ForeignMemory.getAndSetLong(pointer, value);
    }

    @Unsafe
    public static long getAndSetUnsafe(long pointer, int index, long value) {
        return ForeignMemory.getAndSetLong(pointer + (index * 8L), value);
    }

    @Unsafe
    public static long getAndAddUnsafe(long pointer, long delta) {
        return ForeignMemory.getAndAddLong(pointer, delta);
    }

    @Unsafe
    public static long getAndAddUnsafe(long pointer, int index, long delta) {
        return ForeignMemory.getAndAddLong(pointer + (index * 8L), delta);
    }
}