package primitive;

import annotation.Unsafe;
import annotation.Volatile;

import annotation.Required;
import bit.Bit64;
import nio.ForeignMemory;
import oop.TypeRegister;


import nio.StringLookup;
public final class Double {

    @Required
    public static final int CLASS_ID = TypeRegister.ID_DOUBLE;

    public static final double MAX_VALUE = java.lang.Double.MAX_VALUE;
    public static final double MIN_VALUE = java.lang.Double.MIN_VALUE;

    public static final int TYPE_SINGLETON = TypeRegister.DOUBLE_SINGLETON; // 0xAA000004
    public static final int TYPE_ARRAY     = TypeRegister.DOUBLE_ARRAY;     // 0xBB000004
    public static final int TYPE_MATRIX    = TypeRegister.DOUBLE_POINTER;   // 0xCC000004

    
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

    public static double get(long pointer) {
        if (pointer == 0L) throw new NullPointerException(StringLookup.getJavaString(27));
        if (classId(pointer) != CLASS_ID) throw new IllegalArgumentException(StringLookup.getJavaString(28) + java.lang.Long.toHexString(pointer).toUpperCase() + StringLookup.getJavaString(29) + classId(pointer) + StringLookup.getJavaString(279) + CLASS_ID + StringLookup.getJavaString(18));
        return ForeignMemory.getUnsafeDouble(pointer);
    }

    public static double get(long pointer, int index) { 
        checkBounds(pointer, index);
        if (classId(pointer) != CLASS_ID) throw new IllegalArgumentException(StringLookup.getJavaString(28) + java.lang.Long.toHexString(pointer).toUpperCase() + StringLookup.getJavaString(29) + classId(pointer) + StringLookup.getJavaString(279) + CLASS_ID + StringLookup.getJavaString(18));
        return ForeignMemory.getUnsafeDouble(pointer + (index * 8L)); 
    }

    public static long getPointer(long matrixPointer, int index) { 
        if (matrixPointer == 0L) throw new NullPointerException(StringLookup.getJavaString(260));
        if(!isPointer(matrixPointer)) throw new IllegalArgumentException(StringLookup.getJavaString(263) + Integer.toHexString(type(matrixPointer)).toUpperCase());
        if (classId(matrixPointer) != CLASS_ID) throw new IllegalArgumentException(StringLookup.getJavaString(28) + java.lang.Long.toHexString(matrixPointer).toUpperCase() + StringLookup.getJavaString(29) + classId(matrixPointer) + StringLookup.getJavaString(279) + CLASS_ID + StringLookup.getJavaString(18));
        checkBounds(matrixPointer, index);
        return ForeignMemory.getUnsafeLong(matrixPointer + (index * 8L)); 
    }

    public static void set(long pointer, double value) {
        if (pointer == 0L) throw new NullPointerException(StringLookup.getJavaString(31));
        if (classId(pointer) != CLASS_ID) throw new IllegalArgumentException(StringLookup.getJavaString(28) + java.lang.Long.toHexString(pointer).toUpperCase() + StringLookup.getJavaString(29) + classId(pointer) + StringLookup.getJavaString(279) + CLASS_ID + StringLookup.getJavaString(18));
        ForeignMemory.setUnsafe(pointer, value);
    }

    public static void set(long pointer, int index, double value) { 
        checkBounds(pointer, index);
        if (classId(pointer) != CLASS_ID) throw new IllegalArgumentException(StringLookup.getJavaString(28) + java.lang.Long.toHexString(pointer).toUpperCase() + StringLookup.getJavaString(29) + classId(pointer) + StringLookup.getJavaString(279) + CLASS_ID + StringLookup.getJavaString(18));
        ForeignMemory.setUnsafe(pointer + (index * 8L), value); 
    }

    @Volatile
    public static double getVolatile(long pointer) {
        if (pointer == 0L) throw new NullPointerException(StringLookup.getJavaString(32));
        if (classId(pointer) != CLASS_ID) throw new IllegalArgumentException(StringLookup.getJavaString(28) + java.lang.Long.toHexString(pointer).toUpperCase() + StringLookup.getJavaString(29) + classId(pointer) + StringLookup.getJavaString(279) + CLASS_ID + StringLookup.getJavaString(18));
        return ForeignMemory.getUnsafeVolatileDouble(pointer);
    }

    @Volatile
    public static void setVolatile(long pointer, double value) {
        if (pointer == 0L) throw new NullPointerException(StringLookup.getJavaString(31));
        if (classId(pointer) != CLASS_ID) throw new IllegalArgumentException(StringLookup.getJavaString(28) + java.lang.Long.toHexString(pointer).toUpperCase() + StringLookup.getJavaString(29) + classId(pointer) + StringLookup.getJavaString(279) + CLASS_ID + StringLookup.getJavaString(18));
        ForeignMemory.setUnsafeVolatile(pointer, value);
    }

    public static boolean compareAndSet(long pointer, double expected, double value) {
        if (pointer == 0L) throw new NullPointerException(StringLookup.getJavaString(31));
        if (classId(pointer) != CLASS_ID) throw new IllegalArgumentException(StringLookup.getJavaString(28) + java.lang.Long.toHexString(pointer).toUpperCase() + StringLookup.getJavaString(29) + classId(pointer) + StringLookup.getJavaString(279) + CLASS_ID + StringLookup.getJavaString(18));
        return ForeignMemory.compareAndSetDouble(pointer, expected, value);
    }

    public static void setPointer(long matrixPointer, int index, long targetPointer) { 
        if (matrixPointer == 0L) throw new NullPointerException(StringLookup.getJavaString(262));
        if(!isPointer(matrixPointer)) throw new IllegalArgumentException(StringLookup.getJavaString(263) + Integer.toHexString(type(matrixPointer)).toUpperCase());
        if (classId(matrixPointer) != CLASS_ID) throw new IllegalArgumentException(StringLookup.getJavaString(28) + java.lang.Long.toHexString(matrixPointer).toUpperCase() + StringLookup.getJavaString(29) + classId(matrixPointer) + StringLookup.getJavaString(279) + CLASS_ID + StringLookup.getJavaString(18));
        checkBounds(matrixPointer, index);
        ForeignMemory.setUnsafe(matrixPointer + (index * 8L), targetPointer); 
    }

    private static void checkBounds(long pointer, int index) {
        if (pointer == 0L) throw new NullPointerException(StringLookup.getJavaString(33));
        int len = length(pointer);
        if(index < 0 || index >= len) throw new IndexOutOfBoundsException(StringLookup.getJavaString(34) + index + StringLookup.getJavaString(280) + len + StringLookup.getJavaString(36) + java.lang.Long.toHexString(pointer).toUpperCase() + StringLookup.getJavaString(37) + Integer.toHexString(type(pointer)).toUpperCase() + StringLookup.getJavaString(18));
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
    public static double getUnsafe(long pointer) {
        return ForeignMemory.getUnsafeDouble(pointer);
    }

    @Unsafe
    public static double getUnsafe(long pointer, int index) {
        return ForeignMemory.getUnsafeDouble(pointer + (index * 8L));
    }

    @Unsafe
    public static long getUnsafePointer(long matrixPointer, int index) {
        return ForeignMemory.getUnsafeLong(matrixPointer + (index * 8L));
    }

    @Unsafe
    public static void setUnsafe(long pointer, double value) {
        ForeignMemory.setUnsafe(pointer, value);
    }

    @Unsafe
    public static void setUnsafe(long pointer, int index, double value) {
        ForeignMemory.setUnsafe(pointer + (index * 8L), value);
    }

    @Unsafe
    public static void setUnsafePointer(long matrixPointer, int index, long targetPointer) {
        ForeignMemory.setUnsafe(matrixPointer + (index * 8L), targetPointer);
    }

    @Volatile
    public static double getVolatile(long pointer, int index) {
        checkBounds(pointer, index);
        if (classId(pointer) != CLASS_ID) throw new IllegalArgumentException(StringLookup.getJavaString(28) + java.lang.Long.toHexString(pointer).toUpperCase() + StringLookup.getJavaString(29) + classId(pointer) + StringLookup.getJavaString(279) + CLASS_ID + StringLookup.getJavaString(18));
        return ForeignMemory.getUnsafeVolatileDouble(pointer + (index * 8L));
    }

    @Volatile
    public static long getVolatilePointer(long matrixPointer, int index) {
        if(matrixPointer == 0L) throw new NullPointerException(StringLookup.getJavaString(260));
        if(!isPointer(matrixPointer)) throw new IllegalArgumentException(StringLookup.getJavaString(263) + Integer.toHexString(type(matrixPointer)).toUpperCase());
        if (classId(matrixPointer) != CLASS_ID) throw new IllegalArgumentException(StringLookup.getJavaString(28) + java.lang.Long.toHexString(matrixPointer).toUpperCase() + StringLookup.getJavaString(29) + classId(matrixPointer) + StringLookup.getJavaString(279) + CLASS_ID + StringLookup.getJavaString(18));
        checkBounds(matrixPointer, index);
        return ForeignMemory.getUnsafeVolatileLong(matrixPointer + (index * 8L));
    }

    @Volatile
    public static void setVolatile(long pointer, int index, double value) {
        checkBounds(pointer, index);
        if (classId(pointer) != CLASS_ID) throw new IllegalArgumentException(StringLookup.getJavaString(28) + java.lang.Long.toHexString(pointer).toUpperCase() + StringLookup.getJavaString(29) + classId(pointer) + StringLookup.getJavaString(279) + CLASS_ID + StringLookup.getJavaString(18));
        ForeignMemory.setUnsafeVolatile(pointer + (index * 8L), value);
    }

    @Volatile
    public static void setVolatilePointer(long matrixPointer, int index, long targetPointer) {
        if(matrixPointer == 0L) throw new NullPointerException(StringLookup.getJavaString(262));
        if(!isPointer(matrixPointer)) throw new IllegalArgumentException(StringLookup.getJavaString(263) + Integer.toHexString(type(matrixPointer)).toUpperCase());
        if (classId(matrixPointer) != CLASS_ID) throw new IllegalArgumentException(StringLookup.getJavaString(28) + java.lang.Long.toHexString(matrixPointer).toUpperCase() + StringLookup.getJavaString(29) + classId(matrixPointer) + StringLookup.getJavaString(279) + CLASS_ID + StringLookup.getJavaString(18));
        checkBounds(matrixPointer, index);
        ForeignMemory.setUnsafeVolatile(matrixPointer + (index * 8L), targetPointer);
    }

    @Unsafe
    @Volatile
    public static double getUnsafeVolatile(long pointer) {
        return ForeignMemory.getUnsafeVolatileDouble(pointer);
    }

    @Unsafe
    @Volatile
    public static double getUnsafeVolatile(long pointer, int index) {
        return ForeignMemory.getUnsafeVolatileDouble(pointer + (index * 8L));
    }

    @Unsafe
    @Volatile
    public static long getUnsafeVolatilePointer(long matrixPointer, int index) {
        return ForeignMemory.getUnsafeVolatileLong(matrixPointer + (index * 8L));
    }

    @Unsafe
    @Volatile
    public static void setUnsafeVolatile(long pointer, double value) {
        ForeignMemory.setUnsafeVolatile(pointer, value);
    }

    @Unsafe
    @Volatile
    public static void setUnsafeVolatile(long pointer, int index, double value) {
        ForeignMemory.setUnsafeVolatile(pointer + (index * 8L), value);
    }

    @Unsafe
    @Volatile
    public static void setUnsafeVolatilePointer(long matrixPointer, int index, long targetPointer) {
        ForeignMemory.setUnsafeVolatile(matrixPointer + (index * 8L), targetPointer);
    }

}
