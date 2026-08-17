package primitive;

import annotation.Unsafe;
import annotation.Volatile;

import annotation.Required;
import bit.Bit64;
import nio.ForeignMemory;
import oop.TypeRegister;


public final class Fixed64 {

    @Required
    public static final int CLASS_ID = TypeRegister.ID_FIXED64;

    public static final int TYPE_SINGLETON = TypeRegister.FIXED64_SINGLETON; // 0xAA00002B
    public static final int TYPE_ARRAY     = TypeRegister.FIXED64_ARRAY;     // 0xBB00002B
    public static final int TYPE_MATRIX    = TypeRegister.FIXED64_POINTER;   // 0xCC00002B

    // --- CONVERSION METHODS ---
    public static long doubleToFixed64(double val) {
        return Math.round(val * 4294967296.0);
    }

    public static double fixed64ToDouble(long val) {
        return val / 4294967296.0;
    }

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

    public static void free(long pointer)
    {
        Bit64.free(pointer);
    }

    // --- MUTATORS & ACCESSORS ---
    public static double get(long pointer) {
        if (pointer == 0L) throw new NullPointerException("Reading from NULL off-heap pointer!");
        long rawVal = ForeignMemory.getUnsafeLong(pointer);
        return fixed64ToDouble(rawVal);
    }

    public static double get(long pointer, int index) {
        checkBounds(pointer, index);
        long rawVal = ForeignMemory.getUnsafeLong(pointer + (index * 8L));
        return fixed64ToDouble(rawVal);
    }

    public static void set(long pointer, double value) {
        if (pointer == 0L) throw new NullPointerException("Writing to NULL off-heap pointer!");
        if (classId(pointer) != CLASS_ID) throw new IllegalArgumentException("Pointer 0x" + java.lang.Long.toHexString(pointer).toUpperCase() + " is Class ID " + classId(pointer) + ", expected Fixed64 (Class ID " + CLASS_ID + ")");
        long rawVal = doubleToFixed64(value);
        ForeignMemory.set(pointer, rawVal);
    }

    public static void set(long pointer, int index, double value) {
        checkBounds(pointer, index);
        if (classId(pointer) != CLASS_ID) throw new IllegalArgumentException("Pointer 0x" + java.lang.Long.toHexString(pointer).toUpperCase() + " is Class ID " + classId(pointer) + ", expected Fixed64 (Class ID " + CLASS_ID + ")");
        long rawVal = doubleToFixed64(value);
        ForeignMemory.set(pointer + (index * 8L), rawVal);
    }

    @Volatile
    public static double getVolatile(long pointer) {
        if (pointer == 0L) throw new NullPointerException("Reading from NULL off-heap pointer!");
        if (classId(pointer) != CLASS_ID) throw new IllegalArgumentException("Pointer 0x" + java.lang.Long.toHexString(pointer).toUpperCase() + " is Class ID " + classId(pointer) + ", expected Fixed64 (Class ID " + CLASS_ID + ")");
        long rawVal = ForeignMemory.getUnsafeVolatileLong(pointer);
        return fixed64ToDouble(rawVal);
    }

    @Volatile
    public static void setVolatile(long pointer, double value) {
        if (pointer == 0L) throw new NullPointerException("Writing to NULL off-heap pointer!");
        if (classId(pointer) != CLASS_ID) throw new IllegalArgumentException("Pointer 0x" + java.lang.Long.toHexString(pointer).toUpperCase() + " is Class ID " + classId(pointer) + ", expected Fixed64 (Class ID " + CLASS_ID + ")");
        long rawVal = doubleToFixed64(value);
        ForeignMemory.setVolatile(pointer, rawVal);
    }

    public static boolean compareAndSet(long pointer, double expected, double value) {
        if (pointer == 0L) throw new NullPointerException("Writing to NULL off-heap pointer!");
        if (classId(pointer) != CLASS_ID) throw new IllegalArgumentException("Pointer 0x" + java.lang.Long.toHexString(pointer).toUpperCase() + " is Class ID " + classId(pointer) + ", expected Fixed64 (Class ID " + CLASS_ID + ")");
        long expectedRaw = doubleToFixed64(expected);
        long valueRaw = doubleToFixed64(value);
        return ForeignMemory.compareAndSetLong(pointer, expectedRaw, valueRaw);
    }

    public static long getPointer(long matrixPointer, int index) {
        if (matrixPointer == 0L) throw new NullPointerException("Accessing NULL matrix pointer!");
        if (!isPointer(matrixPointer)) {
            throw new IllegalArgumentException("Expected Pointer Array (Matrix), but got Type: 0x" + java.lang.Integer.toHexString(type(matrixPointer)).toUpperCase());
        }
        checkBounds(matrixPointer, index);
        return ForeignMemory.getUnsafeLong(matrixPointer + (index * 8L));
    }

    public static void setPointer(long matrixPointer, int index, long targetPointer) {
        if (matrixPointer == 0L) throw new NullPointerException("Writing to NULL matrix pointer!");
        if (!isPointer(matrixPointer)) {
            throw new IllegalArgumentException("Expected Pointer Array (Matrix), but got Type: 0x" + java.lang.Integer.toHexString(type(matrixPointer)).toUpperCase());
        }
        if (classId(matrixPointer) != CLASS_ID) throw new IllegalArgumentException("Pointer 0x" + java.lang.Long.toHexString(matrixPointer).toUpperCase() + " is Class ID " + classId(matrixPointer) + ", expected Fixed64 (Class ID " + CLASS_ID + ")");
        checkBounds(matrixPointer, index);
        ForeignMemory.set(matrixPointer + (index * 8L), targetPointer);
    }

    // --- ARCHITECTURAL CHECKS ---
    private static void checkBounds(long pointer, int index) {
        if (pointer == 0L) throw new NullPointerException("Checking bounds on NULL off-heap pointer!");
        int len = length(pointer);
        if (index < 0 || index >= len) {
            throw new IndexOutOfBoundsException("Index " + index + " out of bounds for off-heap Fixed64 length " + len + " (Ptr: 0x" + java.lang.Long.toHexString(pointer).toUpperCase() + ", Type: 0x" + java.lang.Integer.toHexString(type(pointer)).toUpperCase() + ")");
        }
    }

    public static int classId() {
        return CLASS_ID;
    }

    public static int type(long pointer) {
        return ForeignMemory.getUnsafeInt(pointer - 8L);
    }

    public static int length(long pointer) {
        return ForeignMemory.getUnsafeInt(pointer - 4L);
    }

    public static int classId(long pointer) {
        return TypeRegister.getClassId(type(pointer));
    }

    public static boolean isSingleton(long pointer) {
        return TypeRegister.isSingleton(type(pointer));
    }

    public static boolean isArray(long pointer) {
        return TypeRegister.isArray(type(pointer));
    }

    public static boolean isPointer(long pointer) {
        return TypeRegister.isPointer(type(pointer));
    }

    // --- AUTOGENERATED UNSAFE & VOLATILE VARIANTS ---

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

    @Volatile
    public static long getVolatile(long pointer, int index) {
        checkBounds(pointer, index);
        return ForeignMemory.getUnsafeVolatileLong(pointer + (index * 8L));
    }

    @Volatile
    public static long getVolatilePointer(long matrixPointer, int index) {
        if(matrixPointer == 0L) throw new NullPointerException("Accessing NULL matrix pointer!");
        checkBounds(matrixPointer, index);
        return ForeignMemory.getUnsafeVolatileLong(matrixPointer + (index * 8L));
    }

    @Volatile
    public static void setVolatile(long pointer, int index, long value) {
        checkBounds(pointer, index);
        ForeignMemory.setVolatile(pointer + (index * 8L), value);
    }

    @Volatile
    public static void setVolatilePointer(long matrixPointer, int index, long targetPointer) {
        if(matrixPointer == 0L) throw new NullPointerException("Writing to NULL matrix pointer!");
        checkBounds(matrixPointer, index);
        ForeignMemory.setVolatile(matrixPointer + (index * 8L), targetPointer);
    }

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

}
