package primitive;

import annotation.Unsafe;
import annotation.Volatile;

import annotation.Required;
import bit.Bit128;
import nio.ForeignMemory;
import oop.TypeRegister;


public final class IntFloat {

    @Required
    public static final int CLASS_ID = TypeRegister.ID_INT_FLOAT;

    public static final int TYPE_SINGLETON = TypeRegister.INT_FLOAT_SINGLETON; // 0xAA000008
    public static final int TYPE_ARRAY     = TypeRegister.INT_FLOAT_ARRAY;     // 0xBB000008
    public static final int TYPE_MATRIX    = TypeRegister.INT_FLOAT_POINTER;   // 0xCC000008

    // =========================================================
    // ALLOCATION — DELEGATED TO THE BIT-WIDTH POOL (bit.Bit128)
    // =========================================================

    public static void freeAll()
    {
        Bit128.freeAll();
    }

    public static long allocateSingleton()
    {
        return Bit128.allocateSingleton(TYPE_SINGLETON);
    }

    public static long allocateArray(int length)
    {
        return Bit128.allocateArray(TYPE_ARRAY, length);
    }

    public static long allocateMatrix(int length)
    {
        return Bit128.allocateMatrix(TYPE_MATRIX, length);
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
        Bit128.free(pointer);
    }

    // --- DATA ACCESSORS & BOUNDS CHECKS ---
    public static int getIntPart(long pointer) {
        if (pointer == 0L) throw new NullPointerException("Accessing NULL off-heap pointer!");
        return ForeignMemory.getUnsafeInt(pointer);
    }

    public static float getFracPart(long pointer) {
        if (pointer == 0L) throw new NullPointerException("Accessing NULL off-heap pointer!");
        return ForeignMemory.getUnsafeFloat(pointer + 4L);
    }

    public static float getAsFloat(long pointer) {
        if (pointer == 0L) throw new NullPointerException("Accessing NULL off-heap pointer!");
        return ForeignMemory.getUnsafeInt(pointer) + ForeignMemory.getUnsafeFloat(pointer + 4L);
    }

    public static int getIntPart(long pointer, int index) {
        checkBounds(pointer, index);
        return ForeignMemory.getUnsafeInt(pointer + (index * 8L));
    }

    public static float getFracPart(long pointer, int index) {
        checkBounds(pointer, index);
        return ForeignMemory.getUnsafeFloat(pointer + (index * 8L) + 4L);
    }

    public static float getAsFloat(long pointer, int index) {
        checkBounds(pointer, index);
        return ForeignMemory.getUnsafeInt(pointer + (index * 8L)) + ForeignMemory.getUnsafeFloat(pointer + (index * 8L) + 4L);
    }

    public static long getPointer(long matrixPointer, int index) { 
        if (matrixPointer == 0L) throw new NullPointerException("Accessing NULL matrix pointer!");
        if (!isPointer(matrixPointer)) {
            throw new IllegalArgumentException("Expected Pointer Array (Matrix), but got Type: 0x" + Integer.toHexString(type(matrixPointer)).toUpperCase());
        }
        checkBounds(matrixPointer, index);
        return ForeignMemory.getUnsafeLong(matrixPointer + (index * 8L)); 
    }

    public static void set(long pointer, int intPart, float fracPart) {
        if (pointer == 0L) throw new NullPointerException("Writing to NULL off-heap pointer!");
        if (classId(pointer) != CLASS_ID) throw new IllegalArgumentException("Pointer 0x" + java.lang.Long.toHexString(pointer).toUpperCase() + " is Class ID " + classId(pointer) + ", expected IntFloat (Class ID " + CLASS_ID + ")");
        setUnsafe(pointer, intPart, fracPart);
    }

    public static void set(long pointer, int index, int intPart, float fracPart) {
        checkBounds(pointer, index);
        if (classId(pointer) != CLASS_ID) throw new IllegalArgumentException("Pointer 0x" + java.lang.Long.toHexString(pointer).toUpperCase() + " is Class ID " + classId(pointer) + ", expected IntFloat (Class ID " + CLASS_ID + ")");
        setUnsafe(pointer, index, intPart, fracPart);
    }

    public static void setPointer(long matrixPointer, int index, long targetPointer) { 
        if (matrixPointer == 0L) throw new NullPointerException("Writing to NULL matrix pointer!");
        if (!isPointer(matrixPointer)) {
            throw new IllegalArgumentException("Expected Pointer Array (Matrix), but got Type: 0x" + Integer.toHexString(type(matrixPointer)).toUpperCase());
        }
        if (classId(matrixPointer) != CLASS_ID) throw new IllegalArgumentException("Pointer 0x" + java.lang.Long.toHexString(matrixPointer).toUpperCase() + " is Class ID " + classId(matrixPointer) + ", expected IntFloat (Class ID " + CLASS_ID + ")");
        checkBounds(matrixPointer, index);
        ForeignMemory.setUnsafe(matrixPointer + (index * 8L), targetPointer); 
    }

    private static void checkBounds(long pointer, int index) {
        if (pointer == 0L) throw new NullPointerException("Checking bounds on NULL off-heap pointer!");
        int len = length(pointer);
        if (index < 0 || index >= len) {
            throw new IndexOutOfBoundsException("Index " + index + " out of bounds for off-heap IntFloat length " + len + " (Ptr: 0x" + java.lang.Long.toHexString(pointer).toUpperCase() + ", Type: 0x" + Integer.toHexString(type(pointer)).toUpperCase() + ")");
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
    public static int unsafeGetIntPart(long pointer, int index) {
        return ForeignMemory.getUnsafeInt(pointer + (index * 8L));
    }

    @Unsafe
    public static float unsafeGetFloatPart(long pointer, int index) {
        return ForeignMemory.getUnsafeFloat(pointer + (index * 8L) + 4L);
    }

    @Unsafe
    public static void setUnsafe(long pointer, int val1, float val2) {
        ForeignMemory.setUnsafe(pointer, val1);
        ForeignMemory.setUnsafe(pointer + 4L, val2);
    }

    @Unsafe
    public static void setUnsafe(long pointer, int index, int val1, float val2) {
        ForeignMemory.setUnsafe(pointer + (index * 8L), val1);
        ForeignMemory.setUnsafe(pointer + (index * 8L) + 4L, val2);
    }

    @Volatile
    public static int getIntPartVolatile(long pointer, int index) {
        checkBounds(pointer, index);
        return ForeignMemory.getUnsafeVolatileInt(pointer + (index * 8L));
    }

    @Volatile
    public static float getFloatPartVolatile(long pointer, int index) {
        checkBounds(pointer, index);
        return ForeignMemory.getUnsafeVolatileFloat(pointer + (index * 8L) + 4L);
    }

    @Volatile
    public static void setVolatile(long pointer, int index, int val1, float val2) {
        checkBounds(pointer, index);
        ForeignMemory.setUnsafeVolatile(pointer + (index * 8L), val1);
        ForeignMemory.setUnsafeVolatile(pointer + (index * 8L) + 4L, val2);
    }

    @Unsafe
    @Volatile
    public static int unsafeVolatileGetIntPart(long pointer, int index) {
        return ForeignMemory.getUnsafeVolatileInt(pointer + (index * 8L));
    }

    @Unsafe
    @Volatile
    public static float unsafeVolatileGetFloatPart(long pointer, int index) {
        return ForeignMemory.getUnsafeVolatileFloat(pointer + (index * 8L) + 4L);
    }

    @Unsafe
    @Volatile
    public static void setUnsafeVolatile(long pointer, int index, int val1, float val2) {
        ForeignMemory.setUnsafeVolatile(pointer + (index * 8L), val1);
        ForeignMemory.setUnsafeVolatile(pointer + (index * 8L) + 4L, val2);
    }
}
