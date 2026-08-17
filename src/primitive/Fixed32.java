package primitive;

import annotation.Unsafe;
import annotation.Volatile;

import annotation.Required;
import bit.Bit32;
import nio.ForeignMemory;
import oop.TypeRegister;


public final class Fixed32 {

    @Required
    public static final int CLASS_ID = TypeRegister.ID_FIXED32;

    public static final int TYPE_SINGLETON = TypeRegister.FIXED32_SINGLETON; // 0xAA00002A
    public static final int TYPE_ARRAY     = TypeRegister.FIXED32_ARRAY;     // 0xBB00002A
    public static final int TYPE_MATRIX    = TypeRegister.FIXED32_POINTER;   // 0xCC00002A

    // --- CONVERSION METHODS ---
    public static int floatToFixed32(float val) {
        return Math.round(val * 65536.0f);
    }

    public static float fixed32ToFloat(int val) {
        return val / 65536.0f;
    }

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

    public static void free(long pointer)
    {
        Bit32.free(pointer);
    }

    // --- MUTATORS & ACCESSORS ---
    public static float get(long pointer) {
        if (pointer == 0L) throw new NullPointerException("Reading from NULL off-heap pointer!");
        int rawVal = ForeignMemory.getUnsafeInt(pointer);
        return fixed32ToFloat(rawVal);
    }

    public static float get(long pointer, int index) {
        checkBounds(pointer, index);
        int rawVal = ForeignMemory.getUnsafeInt(pointer + (index * 4L));
        return fixed32ToFloat(rawVal);
    }

    public static void set(long pointer, float value) {
        if (pointer == 0L) throw new NullPointerException("Writing to NULL off-heap pointer!");
        if (classId(pointer) != CLASS_ID) throw new IllegalArgumentException("Pointer 0x" + java.lang.Long.toHexString(pointer).toUpperCase() + " is Class ID " + classId(pointer) + ", expected Fixed32 (Class ID " + CLASS_ID + ")");
        int rawVal = floatToFixed32(value);
        ForeignMemory.set(pointer, rawVal);
    }

    public static void set(long pointer, int index, float value) {
        checkBounds(pointer, index);
        if (classId(pointer) != CLASS_ID) throw new IllegalArgumentException("Pointer 0x" + java.lang.Long.toHexString(pointer).toUpperCase() + " is Class ID " + classId(pointer) + ", expected Fixed32 (Class ID " + CLASS_ID + ")");
        int rawVal = floatToFixed32(value);
        ForeignMemory.set(pointer + (index * 4L), rawVal);
    }

    @Volatile
    public static float getVolatile(long pointer) {
        if (pointer == 0L) throw new NullPointerException("Reading from NULL off-heap pointer!");
        if (classId(pointer) != CLASS_ID) throw new IllegalArgumentException("Pointer 0x" + java.lang.Long.toHexString(pointer).toUpperCase() + " is Class ID " + classId(pointer) + ", expected Fixed32 (Class ID " + CLASS_ID + ")");
        int rawVal = ForeignMemory.getUnsafeVolatileInt(pointer);
        return fixed32ToFloat(rawVal);
    }

    @Volatile
    public static void setVolatile(long pointer, float value) {
        if (pointer == 0L) throw new NullPointerException("Writing to NULL off-heap pointer!");
        if (classId(pointer) != CLASS_ID) throw new IllegalArgumentException("Pointer 0x" + java.lang.Long.toHexString(pointer).toUpperCase() + " is Class ID " + classId(pointer) + ", expected Fixed32 (Class ID " + CLASS_ID + ")");
        int rawVal = floatToFixed32(value);
        ForeignMemory.setVolatile(pointer, rawVal);
    }

    public static boolean compareAndSet(long pointer, float expected, float value) {
        if (pointer == 0L) throw new NullPointerException("Writing to NULL off-heap pointer!");
        if (classId(pointer) != CLASS_ID) throw new IllegalArgumentException("Pointer 0x" + java.lang.Long.toHexString(pointer).toUpperCase() + " is Class ID " + classId(pointer) + ", expected Fixed32 (Class ID " + CLASS_ID + ")");
        int expectedRaw = floatToFixed32(expected);
        int valueRaw = floatToFixed32(value);
        return ForeignMemory.compareAndSetInt(pointer, expectedRaw, valueRaw);
    }

    public static long getPointer(long matrixPointer, int index) {
        if (matrixPointer == 0L) throw new NullPointerException("Accessing NULL matrix pointer!");
        if (!isPointer(matrixPointer)) {
            throw new IllegalArgumentException("Expected Pointer Array (Matrix), but got Type: 0x" + Integer.toHexString(type(matrixPointer)).toUpperCase());
        }
        checkBounds(matrixPointer, index);
        return ForeignMemory.getUnsafeLong(matrixPointer + (index * 8L));
    }

    public static void setPointer(long matrixPointer, int index, long targetPointer) {
        if (matrixPointer == 0L) throw new NullPointerException("Writing to NULL matrix pointer!");
        if (!isPointer(matrixPointer)) {
            throw new IllegalArgumentException("Expected Pointer Array (Matrix), but got Type: 0x" + Integer.toHexString(type(matrixPointer)).toUpperCase());
        }
        if (classId(matrixPointer) != CLASS_ID) throw new IllegalArgumentException("Pointer 0x" + java.lang.Long.toHexString(matrixPointer).toUpperCase() + " is Class ID " + classId(matrixPointer) + ", expected Fixed32 (Class ID " + CLASS_ID + ")");
        checkBounds(matrixPointer, index);
        ForeignMemory.set(matrixPointer + (index * 8L), targetPointer);
    }

    // --- ARCHITECTURAL CHECKS ---
    private static void checkBounds(long pointer, int index) {
        if (pointer == 0L) throw new NullPointerException("Checking bounds on NULL off-heap pointer!");
        int len = length(pointer);
        if (index < 0 || index >= len) {
            throw new IndexOutOfBoundsException("Index " + index + " out of bounds for off-heap Fixed32 length " + len + " (Ptr: 0x" + java.lang.Long.toHexString(pointer).toUpperCase() + ", Type: 0x" + Integer.toHexString(type(pointer)).toUpperCase() + ")");
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

    @Volatile
    public static int getVolatile(long pointer, int index) {
        checkBounds(pointer, index);
        return ForeignMemory.getUnsafeVolatileInt(pointer + (index * 4L));
    }

    @Volatile
    public static long getVolatilePointer(long matrixPointer, int index) {
        if(matrixPointer == 0L) throw new NullPointerException("Accessing NULL matrix pointer!");
        checkBounds(matrixPointer, index);
        return ForeignMemory.getUnsafeVolatileLong(matrixPointer + (index * 8L));
    }

    @Volatile
    public static void setVolatile(long pointer, int index, int value) {
        checkBounds(pointer, index);
        ForeignMemory.setVolatile(pointer + (index * 4L), value);
    }

    @Volatile
    public static void setVolatilePointer(long matrixPointer, int index, long targetPointer) {
        if(matrixPointer == 0L) throw new NullPointerException("Writing to NULL matrix pointer!");
        checkBounds(matrixPointer, index);
        ForeignMemory.setVolatile(matrixPointer + (index * 8L), targetPointer);
    }

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

}
