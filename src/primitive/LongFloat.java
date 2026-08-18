package primitive;

import annotation.Unsafe;
import annotation.Volatile;

import annotation.Required;
import bit.Bit128;
import nio.ForeignMemory;
import oop.TypeRegister;


import nio.StringLookup;
public final class LongFloat {

    @Required
    public static final int CLASS_ID = TypeRegister.ID_LONG_FLOAT;

    public static final int TYPE_SINGLETON = TypeRegister.LONG_FLOAT_SINGLETON; // 0xAA00000A
    public static final int TYPE_ARRAY     = TypeRegister.LONG_FLOAT_ARRAY;     // 0xBB00000A
    public static final int TYPE_MATRIX    = TypeRegister.LONG_FLOAT_POINTER;   // 0xCC00000A

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
        ForeignMemory.copy(oldPointer, newPointer, elementsToCopy * 12L);
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
    public static long getIntPart(long pointer) {
        return Long.get(pointer);
    }

    public static float getFracPart(long pointer) {
        if (pointer == 0L) throw new NullPointerException(StringLookup.getJavaString(27));
        return ForeignMemory.getUnsafeFloat(pointer + 8L);
    }

    public static double getAsDouble(long pointer) {
        if (pointer == 0L) throw new NullPointerException(StringLookup.getJavaString(27));
        return ForeignMemory.getUnsafeLong(pointer) + ForeignMemory.getUnsafeFloat(pointer + 8L);
    }

    public static long getIntPart(long pointer, int index) {
        checkBounds(pointer, index);
        return ForeignMemory.getUnsafeLong(pointer + (index * 12L));
    }

    public static float getFracPart(long pointer, int index) {
        checkBounds(pointer, index);
        return ForeignMemory.getUnsafeFloat(pointer + (index * 12L) + 8L);
    }

    public static double getAsDouble(long pointer, int index) {
        checkBounds(pointer, index);
        return ForeignMemory.getUnsafeLong(pointer + (index * 12L)) + ForeignMemory.getUnsafeFloat(pointer + (index * 12L) + 8L);
    }

    public static long getPointer(long matrixPointer, int index) { 
        if (matrixPointer == 0L) throw new NullPointerException(StringLookup.getJavaString(260));
        if (!isPointer(matrixPointer)) {
            throw new IllegalArgumentException(StringLookup.getJavaString(263) + Integer.toHexString(type(matrixPointer)).toUpperCase());
        }
        checkBounds(matrixPointer, index);
        return ForeignMemory.getUnsafeLong(matrixPointer + (index * 8L)); 
    }

    public static void set(long pointer, long intPart, float fracPart) {
        if (pointer == 0L) throw new NullPointerException(StringLookup.getJavaString(31));
        if (classId(pointer) != CLASS_ID) throw new IllegalArgumentException(StringLookup.getJavaString(28) + java.lang.Long.toHexString(pointer).toUpperCase() + StringLookup.getJavaString(29) + classId(pointer) + StringLookup.getJavaString(281) + CLASS_ID + StringLookup.getJavaString(18));
        ForeignMemory.setUnsafe(pointer, intPart);
        ForeignMemory.setUnsafe(pointer + 8L, fracPart);
    }

    public static void set(long pointer, int index, long intPart, float fracPart) {
        checkBounds(pointer, index); // safe checking of bounds
        if (classId(pointer) != CLASS_ID) throw new IllegalArgumentException(StringLookup.getJavaString(28) + java.lang.Long.toHexString(pointer).toUpperCase() + StringLookup.getJavaString(29) + classId(pointer) + StringLookup.getJavaString(281) + CLASS_ID + StringLookup.getJavaString(18));
        setUnsafe(pointer, index, intPart, fracPart);
    }

    public static void setPointer(long matrixPointer, int index, long targetPointer) { 
        if (matrixPointer == 0L) throw new NullPointerException(StringLookup.getJavaString(262));
        if (!isPointer(matrixPointer)) {
            throw new IllegalArgumentException(StringLookup.getJavaString(263) + Integer.toHexString(type(matrixPointer)).toUpperCase());
        }
        if (classId(matrixPointer) != CLASS_ID) throw new IllegalArgumentException(StringLookup.getJavaString(28) + java.lang.Long.toHexString(matrixPointer).toUpperCase() + StringLookup.getJavaString(29) + classId(matrixPointer) + StringLookup.getJavaString(281) + CLASS_ID + StringLookup.getJavaString(18));
        checkBounds(matrixPointer, index);
        ForeignMemory.setUnsafe(matrixPointer + (index * 8L), targetPointer); 
    }

    private static void checkBounds(long pointer, int index) {
        if (pointer == 0L) throw new NullPointerException(StringLookup.getJavaString(33));
        int len = length(pointer);
        if (index < 0 || index >= len) {
            throw new IndexOutOfBoundsException(StringLookup.getJavaString(34) + index + StringLookup.getJavaString(282) + len + StringLookup.getJavaString(36) + java.lang.Long.toHexString(pointer).toUpperCase() + StringLookup.getJavaString(37) + Integer.toHexString(type(pointer)).toUpperCase() + StringLookup.getJavaString(18));
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
    public static long unsafeGetLongPart(long pointer, int index) {
        return ForeignMemory.getUnsafeLong(pointer + (index * 12L));
    }

    @Unsafe
    public static float unsafeGetFloatPart(long pointer, int index) {
        return ForeignMemory.getUnsafeFloat(pointer + (index * 12L) + 8L);
    }

    @Unsafe
    public static void setUnsafe(long pointer, long val1, float val2) {
        ForeignMemory.setUnsafe(pointer, val1);
        ForeignMemory.setUnsafe(pointer + 8L, val2);
    }

    @Unsafe
    public static void setUnsafe(long pointer, int index, long val1, float val2) {
        ForeignMemory.setUnsafe(pointer + (index * 12L), val1);
        ForeignMemory.setUnsafe(pointer + (index * 12L) + 8L, val2);
    }

    @Volatile
    public static long getLongPartVolatile(long pointer, int index) {
        checkBounds(pointer, index);
        return ForeignMemory.getUnsafeVolatileLong(pointer + (index * 12L));
    }

    @Volatile
    public static float getFloatPartVolatile(long pointer, int index) {
        checkBounds(pointer, index);
        return ForeignMemory.getUnsafeVolatileFloat(pointer + (index * 12L) + 8L);
    }

    @Volatile
    public static void setVolatile(long pointer, int index, long val1, float val2) {
        checkBounds(pointer, index); // this is where the checking bounds are
        setUnsafeVolatile(pointer, index, val1, val2);
    }

    @Unsafe
    @Volatile
    public static long unsafeVolatileGetLongPart(long pointer, int index) {
        return ForeignMemory.getUnsafeVolatileLong(pointer + (index * 12L));
    }

    @Unsafe
    @Volatile
    public static float unsafeVolatileGetFloatPart(long pointer, int index) {
        return ForeignMemory.getUnsafeVolatileFloat(pointer + (index * 12L) + 8L);
    }

    @Unsafe
    @Volatile
    public static void setUnsafeVolatile(long pointer, int index, long val1, float val2) {
        ForeignMemory.setUnsafeVolatile(pointer + (index * 12L), val1);
        ForeignMemory.setUnsafeVolatile(pointer + (index * 12L) + 8L, val2);
    }
}
