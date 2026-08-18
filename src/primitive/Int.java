package primitive;

import annotation.Unsafe;
import annotation.Volatile;
import annotation.Required;

import bit.Bit32;
import nio.ForeignMemory;
import oop.TypeRegister;

public final class Int
{
    @Required
    public static final int CLASS_ID = TypeRegister.ID_INT;
    public static final int MAX_VALUE = 2147483647;
    public static final int MIN_VALUE = -2147483648;

    public static final int TYPE_SINGLETON = TypeRegister.INT_SINGLETON; // 0x10000001
    public static final int TYPE_ARRAY     = TypeRegister.INT_ARRAY;     // 0x20000001
    public static final int TYPE_MATRIX    = TypeRegister.INT_POINTER;   // 0x30000001

    private Int()
    {
    }

    // =========================================================================
    // ALLOCATION — DELEGATED TO THE BIT-WIDTH POOL (bit.Bit32)
    // =========================================================================

    public static void freeAll()
    {
        Bit32.freeAll();
    }

    public static long allocateSingleton()
    {
        return Bit32.allocateSingleton(TYPE_SINGLETON);
    }

    public static long allocateArray(int length)
    {
        return Bit32.allocateArray(TYPE_ARRAY, length);
    }

    public static long allocateMatrix(int length)
    {
        return Bit32.allocateMatrix(TYPE_MATRIX, length);
    }

    public static long expandArray(long oldPointer, int newLength)
    {
        if(oldPointer == 0L) return allocateArray(newLength);
        int oldLength = length(oldPointer);
        long newPointer = allocateArray(newLength);

        int elementsToCopy = Math.min(oldLength, newLength);
        ForeignMemory.copy(oldPointer, newPointer, elementsToCopy * 4L);
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
        Bit32.free(pointer);
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

    public static int get(long pointer) {
        if(pointer == 0L) throw new NullPointerException("Accessing NULL off-heap pointer!");
        if(classId(pointer) != CLASS_ID) throw new IllegalArgumentException("Pointer 0x" + java.lang.Long.toHexString(pointer).toUpperCase() + " is Class ID " + classId(pointer) + ", expected Int");
        return ForeignMemory.getInt(pointer);
    }

    public static int get(long pointer, int index) {
        checkBounds(pointer, index);
        if(classId(pointer) != CLASS_ID) throw new IllegalArgumentException("Pointer 0x" + java.lang.Long.toHexString(pointer).toUpperCase() + " is Class ID " + classId(pointer) + ", expected Int");
        return ForeignMemory.getInt(pointer + (index * 4L));
    }

    public static long getPointer(long matrixPointer, int index) {
        if(matrixPointer == 0L) throw new NullPointerException("Accessing NULL matrix pointer!");
        if(!isPointer(matrixPointer)) throw new IllegalArgumentException("Expected Pointer Array (Matrix)");
        checkBounds(matrixPointer, index);
        return ForeignMemory.getLong(matrixPointer + (index * 8L));
    }

    public static void set(long pointer, int value) {
        if(pointer == 0L) throw new NullPointerException("Writing to NULL off-heap pointer!");
        if(classId(pointer) != CLASS_ID) throw new IllegalArgumentException("Pointer 0x" + java.lang.Long.toHexString(pointer).toUpperCase() + " is Class ID " + classId(pointer) + ", expected Int");
        ForeignMemory.set(pointer, value);
    }

    public static void set(long pointer, int index, int value) {
        checkBounds(pointer, index);
        if(classId(pointer) != CLASS_ID) throw new IllegalArgumentException("Pointer 0x" + java.lang.Long.toHexString(pointer).toUpperCase() + " is Class ID " + classId(pointer) + ", expected Int");
        ForeignMemory.set(pointer + (index * 4L), value);
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
    public static int getUnsafe(long pointer) {
        return ForeignMemory.getUnsafeInt(pointer);
    }

    @Unsafe
    public static int getUnsafe(long pointer, int index) {
        return ForeignMemory.getUnsafeInt(pointer + (index * 4L));
    }

    @Unsafe
    public static long getUnsafePointer(long matrixPointer, int index) {
        return ForeignMemory.getUnsafeLong(matrixPointer + (index * 8L));
    }

    @Unsafe
    public static void setUnsafe(long pointer, int value) {
        ForeignMemory.setUnsafe(pointer, value);
    }

    @Unsafe
    public static void setUnsafe(long pointer, int index, int value) {
        ForeignMemory.setUnsafe(pointer + (index * 4L), value);
    }

    @Unsafe
    public static void setUnsafePointer(long matrixPointer, int index, long targetPointer) {
        ForeignMemory.setUnsafe(matrixPointer + (index * 8L), targetPointer);
    }

    // =========================================================================
    // 3. VOLATILE OPERATIONS (Thread-Safe, Bounds Checked)
    // =========================================================================

    @Volatile
    public static int getVolatile(long pointer) {
        if(pointer == 0L) throw new NullPointerException("Reading from NULL off-heap pointer!");
        if(classId(pointer) != CLASS_ID) throw new IllegalArgumentException("Pointer 0x" + java.lang.Long.toHexString(pointer).toUpperCase() + " is Class ID " + classId(pointer) + ", expected Int");
        return ForeignMemory.getVolatileInt(pointer);
    }

    @Volatile
    public static int getVolatile(long pointer, int index) {
        checkBounds(pointer, index);
        if(classId(pointer) != CLASS_ID) throw new IllegalArgumentException("Pointer 0x" + java.lang.Long.toHexString(pointer).toUpperCase() + " is Class ID " + classId(pointer) + ", expected Int");
        return ForeignMemory.getVolatileInt(pointer + (index * 4L));
    }

    @Volatile
    public static long getVolatilePointer(long matrixPointer, int index) {
        if(matrixPointer == 0L) throw new NullPointerException("Accessing NULL matrix pointer!");
        checkBounds(matrixPointer, index);
        return ForeignMemory.getVolatileLong(matrixPointer + (index * 8L));
    }

    @Volatile
    public static void setVolatile(long pointer, int value) {
        if(pointer == 0L) throw new NullPointerException("Writing to NULL off-heap pointer!");
        if(classId(pointer) != CLASS_ID) throw new IllegalArgumentException("Pointer 0x" + java.lang.Long.toHexString(pointer).toUpperCase() + " is Class ID " + classId(pointer) + ", expected Int");
        ForeignMemory.setVolatile(pointer, value);
    }

    @Volatile
    public static void setVolatile(long pointer, int index, int value) {
        checkBounds(pointer, index);
        if(classId(pointer) != CLASS_ID) throw new IllegalArgumentException("Pointer 0x" + java.lang.Long.toHexString(pointer).toUpperCase() + " is Class ID " + classId(pointer) + ", expected Int");
        ForeignMemory.setVolatile(pointer + (index * 4L), value);
    }

    @Volatile
    public static void setVolatilePointer(long matrixPointer, int index, long targetPointer) {
        if(matrixPointer == 0L) throw new NullPointerException("Writing to NULL matrix pointer!");
        checkBounds(matrixPointer, index);
        ForeignMemory.setVolatile(matrixPointer + (index * 8L), targetPointer);
    }

    public static boolean compareAndSet(long pointer, int expected, int value) {
        if(pointer == 0L) throw new NullPointerException("Writing to NULL off-heap pointer!");
        if(classId(pointer) != CLASS_ID) throw new IllegalArgumentException("Pointer 0x" + java.lang.Long.toHexString(pointer).toUpperCase() + " is Class ID " + classId(pointer) + ", expected Int");
        return ForeignMemory.compareAndSetInt(pointer, expected, value);
    }

    public static boolean compareAndSet(long pointer, int index, int expected, int value) {
        checkBounds(pointer, index);
        if(classId(pointer) != CLASS_ID) throw new IllegalArgumentException("Pointer 0x" + java.lang.Long.toHexString(pointer).toUpperCase() + " is Class ID " + classId(pointer) + ", expected Int");
        return ForeignMemory.compareAndSetInt(pointer + (index * 4L), expected, value);
    }

    public static int getAndSet(long pointer, int value) {
        if(pointer == 0L) throw new NullPointerException("Writing to NULL off-heap pointer!");
        if(classId(pointer) != CLASS_ID) throw new IllegalArgumentException("Pointer 0x" + java.lang.Long.toHexString(pointer).toUpperCase() + " is Class ID " + classId(pointer) + ", expected Int");
        return ForeignMemory.getAndSetInt(pointer, value);
    }

    public static int getAndSet(long pointer, int index, int value) {
        checkBounds(pointer, index);
        if(classId(pointer) != CLASS_ID) throw new IllegalArgumentException("Pointer 0x" + java.lang.Long.toHexString(pointer).toUpperCase() + " is Class ID " + classId(pointer) + ", expected Int");
        return ForeignMemory.getAndSetInt(pointer + (index * 4L), value);
    }

    public static int getAndAdd(long pointer, int delta) {
        if(pointer == 0L) throw new NullPointerException("Writing to NULL off-heap pointer!");
        if(classId(pointer) != CLASS_ID) throw new IllegalArgumentException("Pointer 0x" + java.lang.Long.toHexString(pointer).toUpperCase() + " is Class ID " + classId(pointer) + ", expected Int");
        return ForeignMemory.getAndAddInt(pointer, delta);
    }

    public static int getAndAdd(long pointer, int index, int delta) {
        checkBounds(pointer, index);
        if(classId(pointer) != CLASS_ID) throw new IllegalArgumentException("Pointer 0x" + java.lang.Long.toHexString(pointer).toUpperCase() + " is Class ID " + classId(pointer) + ", expected Int");
        return ForeignMemory.getAndAddInt(pointer + (index * 4L), delta);
    }

    public static int incrementAndGet(long pointer) {
        return getAndAdd(pointer, 1) + 1;
    }

    public static int decrementAndGet(long pointer) {
        return getAndAdd(pointer, -1) - 1;
    }

    // =========================================================================
    // 4. UNSAFE & VOLATILE OPERATIONS (Thread-Safe, No Checks)
    // =========================================================================

    @Unsafe
    @Volatile
    public static int getUnsafeVolatile(long pointer) {
        return ForeignMemory.getUnsafeVolatileInt(pointer);
    }

    @Unsafe
    @Volatile
    public static int getUnsafeVolatile(long pointer, int index) {
        return ForeignMemory.getUnsafeVolatileInt(pointer + (index * 4L));
    }

    @Unsafe
    @Volatile
    public static long getUnsafeVolatilePointer(long matrixPointer, int index) {
        return ForeignMemory.getUnsafeVolatileLong(matrixPointer + (index * 8L));
    }

    @Unsafe
    @Volatile
    public static void setUnsafeVolatile(long pointer, int value) {
        ForeignMemory.setUnsafeVolatile(pointer, value);
    }

    @Unsafe
    @Volatile
    public static void setUnsafeVolatile(long pointer, int index, int value) {
        ForeignMemory.setUnsafeVolatile(pointer + (index * 4L), value);
    }

    @Unsafe
    @Volatile
    public static void setUnsafeVolatilePointer(long matrixPointer, int index, long targetPointer) {
        ForeignMemory.setUnsafeVolatile(matrixPointer + (index * 8L), targetPointer);
    }

    @Unsafe
    public static boolean compareAndSetUnsafe(long pointer, int expected, int value) {
        return ForeignMemory.compareAndSetInt(pointer, expected, value);
    }

    @Unsafe
    public static boolean compareAndSetUnsafe(long pointer, int index, int expected, int value) {
        return ForeignMemory.compareAndSetInt(pointer + (index * 4L), expected, value);
    }

    @Unsafe
    public static int getAndSetUnsafe(long pointer, int value) {
        return ForeignMemory.getAndSetInt(pointer, value);
    }

    @Unsafe
    public static int getAndSetUnsafe(long pointer, int index, int value) {
        return ForeignMemory.getAndSetInt(pointer + (index * 4L), value);
    }

    @Unsafe
    public static int getAndAddUnsafe(long pointer, int delta) {
        return ForeignMemory.getAndAddInt(pointer, delta);
    }

    @Unsafe
    public static int getAndAddUnsafe(long pointer, int index, int delta) {
        return ForeignMemory.getAndAddInt(pointer + (index * 4L), delta);
    }
}