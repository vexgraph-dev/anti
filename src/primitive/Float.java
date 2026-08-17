package primitive;

import annotation.Unsafe;
import annotation.Volatile;
import annotation.Required;

import bit.Bit32;
import nio.ForeignMemory;
import oop.TypeRegister;


public final class Float
{
    @Required
    public static final int CLASS_ID = TypeRegister.ID_FLOAT;
    public static final float MAX_VALUE = java.lang.Float.MAX_VALUE;
    public static final float MIN_VALUE = java.lang.Float.MIN_VALUE;

    public static final int TYPE_SINGLETON = TypeRegister.FLOAT_SINGLETON;
    public static final int TYPE_ARRAY     = TypeRegister.FLOAT_ARRAY;
    public static final int TYPE_MATRIX    = TypeRegister.FLOAT_POINTER;

    // =========================================================
    // ALLOCATION — DELEGATED TO THE BIT-WIDTH POOL (bit.Bit32)
    // =========================================================

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

    public static float get(long pointer) {
        if(pointer == 0L) throw new NullPointerException("Accessing NULL off-heap pointer!");
        if(classId(pointer) != CLASS_ID) throw new IllegalArgumentException("Pointer 0x" + java.lang.Long.toHexString(pointer).toUpperCase() + " is Class ID " + classId(pointer) + ", expected Float");
        return ForeignMemory.getFloat(pointer);
    }

    public static float get(long pointer, int index) {
        checkBounds(pointer, index);
        if(classId(pointer) != CLASS_ID) throw new IllegalArgumentException("Pointer 0x" + java.lang.Long.toHexString(pointer).toUpperCase() + " is Class ID " + classId(pointer) + ", expected Float");
        return ForeignMemory.getFloat(pointer + (index * 4L));
    }

    public static long getPointer(long matrixPointer, int index) {
        if(matrixPointer == 0L) throw new NullPointerException("Accessing NULL matrix pointer!");
        if(!isPointer(matrixPointer)) throw new IllegalArgumentException("Expected Pointer Array (Matrix)");
        checkBounds(matrixPointer, index);
        return ForeignMemory.getLong(matrixPointer + (index * 8L));
    }

    public static void set(long pointer, float value) {
        if(pointer == 0L) throw new NullPointerException("Writing to NULL off-heap pointer!");
        if(classId(pointer) != CLASS_ID) throw new IllegalArgumentException("Pointer 0x" + java.lang.Long.toHexString(pointer).toUpperCase() + " is Class ID " + classId(pointer) + ", expected Float");
        ForeignMemory.set(pointer, value);
    }

    public static void set(long pointer, int index, float value) {
        checkBounds(pointer, index);
        if(classId(pointer) != CLASS_ID) throw new IllegalArgumentException("Pointer 0x" + java.lang.Long.toHexString(pointer).toUpperCase() + " is Class ID " + classId(pointer) + ", expected Float");
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
    public static float getUnsafe(long pointer) {
        return ForeignMemory.getUnsafeFloat(pointer);
    }

    @Unsafe
    public static float getUnsafe(long pointer, int index) {
        return ForeignMemory.getUnsafeFloat(pointer + (index * 4L));
    }

    @Unsafe
    public static long getUnsafePointer(long matrixPointer, int index) {
        return ForeignMemory.getUnsafeLong(matrixPointer + (index * 8L));
    }

    @Unsafe
    public static void setUnsafe(long pointer, float value) {
        ForeignMemory.setUnsafe(pointer, value);
    }

    @Unsafe
    public static void setUnsafe(long pointer, int index, float value) {
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
    public static float getVolatile(long pointer) {
        if(pointer == 0L) throw new NullPointerException("Reading from NULL off-heap pointer!");
        if(classId(pointer) != CLASS_ID) throw new IllegalArgumentException("Pointer 0x" + java.lang.Long.toHexString(pointer).toUpperCase() + " is Class ID " + classId(pointer) + ", expected Float");
        return ForeignMemory.getVolatileFloat(pointer);
    }

    @Volatile
    public static float getVolatile(long pointer, int index) {
        checkBounds(pointer, index);
        if(classId(pointer) != CLASS_ID) throw new IllegalArgumentException("Pointer 0x" + java.lang.Long.toHexString(pointer).toUpperCase() + " is Class ID " + classId(pointer) + ", expected Float");
        return ForeignMemory.getVolatileFloat(pointer + (index * 4L));
    }

    @Volatile
    public static long getVolatilePointer(long matrixPointer, int index) {
        if(matrixPointer == 0L) throw new NullPointerException("Accessing NULL matrix pointer!");
        checkBounds(matrixPointer, index);
        return ForeignMemory.getVolatileLong(matrixPointer + (index * 8L));
    }

    @Volatile
    public static void setVolatile(long pointer, float value) {
        if(pointer == 0L) throw new NullPointerException("Writing to NULL off-heap pointer!");
        if(classId(pointer) != CLASS_ID) throw new IllegalArgumentException("Pointer 0x" + java.lang.Long.toHexString(pointer).toUpperCase() + " is Class ID " + classId(pointer) + ", expected Float");
        ForeignMemory.setVolatile(pointer, value);
    }

    @Volatile
    public static void setVolatile(long pointer, int index, float value) {
        checkBounds(pointer, index);
        if(classId(pointer) != CLASS_ID) throw new IllegalArgumentException("Pointer 0x" + java.lang.Long.toHexString(pointer).toUpperCase() + " is Class ID " + classId(pointer) + ", expected Float");
        ForeignMemory.setVolatile(pointer + (index * 4L), value);
    }

    @Volatile
    public static void setVolatilePointer(long matrixPointer, int index, long targetPointer) {
        if(matrixPointer == 0L) throw new NullPointerException("Writing to NULL matrix pointer!");
        checkBounds(matrixPointer, index);
        ForeignMemory.setVolatile(matrixPointer + (index * 8L), targetPointer);
    }

    public static boolean compareAndSet(long pointer, float expected, float value) {
        if(pointer == 0L) throw new NullPointerException("Writing to NULL off-heap pointer!");
        if(classId(pointer) != CLASS_ID) throw new IllegalArgumentException("Pointer 0x" + java.lang.Long.toHexString(pointer).toUpperCase() + " is Class ID " + classId(pointer) + ", expected Float");
        return ForeignMemory.compareAndSetFloat(pointer, expected, value);
    }

    public static float getAndSet(long pointer, float value) {
        if(pointer == 0L) throw new NullPointerException("Writing to NULL off-heap pointer!");
        if(classId(pointer) != CLASS_ID) throw new IllegalArgumentException("Pointer 0x" + java.lang.Long.toHexString(pointer).toUpperCase() + " is Class ID " + classId(pointer) + ", expected Float");
        return ForeignMemory.getAndSetFloat(pointer, value);
    }

    // =========================================================================
    // 4. UNSAFE & VOLATILE OPERATIONS (Thread-Safe, No Checks)
    // =========================================================================

    @Unsafe
    @Volatile
    public static float getUnsafeVolatile(long pointer) {
        return ForeignMemory.getUnsafeVolatileFloat(pointer);
    }

    @Unsafe
    @Volatile
    public static float getUnsafeVolatile(long pointer, int index) {
        return ForeignMemory.getUnsafeVolatileFloat(pointer + (index * 4L));
    }

    @Unsafe
    @Volatile
    public static long getUnsafeVolatilePointer(long matrixPointer, int index) {
        return ForeignMemory.getUnsafeVolatileLong(matrixPointer + (index * 8L));
    }

    @Unsafe
    @Volatile
    public static void setUnsafeVolatile(long pointer, float value) {
        ForeignMemory.setUnsafeVolatile(pointer, value);
    }

    @Unsafe
    @Volatile
    public static void setUnsafeVolatile(long pointer, int index, float value) {
        ForeignMemory.setUnsafeVolatile(pointer + (index * 4L), value);
    }

    @Unsafe
    @Volatile
    public static void setUnsafeVolatilePointer(long matrixPointer, int index, long targetPointer) {
        ForeignMemory.setUnsafeVolatile(matrixPointer + (index * 8L), targetPointer);
    }
}