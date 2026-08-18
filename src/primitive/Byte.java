package primitive;

import annotation.Unsafe;
import annotation.Volatile;
import annotation.Required;

import bit.Bit8;
import nio.ForeignMemory;
import oop.TypeRegister;


import nio.StringLookup;
public final class Byte
{
    @Required
    public static final int CLASS_ID = TypeRegister.ID_BYTE;
    public static final byte MAX_VALUE = 127;
    public static final byte MIN_VALUE = -128;

    public static final int TYPE_SINGLETON = TypeRegister.BYTE_SINGLETON;
    public static final int TYPE_ARRAY     = TypeRegister.BYTE_ARRAY;
    public static final int TYPE_MATRIX    = TypeRegister.BYTE_POINTER;

    // =========================================================
    // ALLOCATION — DELEGATED TO THE BIT-WIDTH POOL (bit.Bit8)
    // =========================================================

    public static void freeAll()
    {
        Bit8.freeAll();
    }

    public static long allocateSingleton()
    {
        return Bit8.allocateSingleton(TYPE_SINGLETON);
    }

    public static long allocateArray(int length)
    {
        return Bit8.allocateArray(TYPE_ARRAY, length);
    }

    public static long allocateMatrix(int length)
    {
        return Bit8.allocateMatrix(TYPE_MATRIX, length);
    }

    public static long expandArray(long oldPointer, int newLength)
    {
        if(oldPointer == 0L) return allocateArray(newLength);
        int oldLength = length(oldPointer);
        long newPointer = allocateArray(newLength);

        int elementsToCopy = Math.min(oldLength, newLength);
        ForeignMemory.copy(oldPointer, newPointer, elementsToCopy * 1L);
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
        Bit8.free(pointer);
    }

    // =========================================================================
    // ARCHITECTURAL CHECKS & METADATA
    // =========================================================================

    private static void checkBounds(long pointer, int index)
    {
        if(pointer == 0L) throw new NullPointerException(StringLookup.getJavaString(33));
        int len = length(pointer);
        if(index < 0 || index >= len) throw new IndexOutOfBoundsException(StringLookup.getJavaString(34) + index + StringLookup.getJavaString(35) + len + StringLookup.getJavaString(36) + java.lang.Long.toHexString(pointer).toUpperCase() + StringLookup.getJavaString(37) + Integer.toHexString(type(pointer)).toUpperCase() + StringLookup.getJavaString(18));
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

    public static byte get(long pointer) {
        if(pointer == 0L) throw new NullPointerException(StringLookup.getJavaString(27));
        if(classId(pointer) != CLASS_ID) throw new IllegalArgumentException(StringLookup.getJavaString(28) + java.lang.Long.toHexString(pointer).toUpperCase() + StringLookup.getJavaString(29) + classId(pointer) + StringLookup.getJavaString(259));
        return ForeignMemory.getByte(pointer);
    }

    public static byte get(long pointer, int index) {
        checkBounds(pointer, index);
        if(classId(pointer) != CLASS_ID) throw new IllegalArgumentException(StringLookup.getJavaString(28) + java.lang.Long.toHexString(pointer).toUpperCase() + StringLookup.getJavaString(29) + classId(pointer) + StringLookup.getJavaString(259));
        return ForeignMemory.getByte(pointer + ((long) index));
    }

    public static long getPointer(long matrixPointer, int index) {
        if(matrixPointer == 0L) throw new NullPointerException(StringLookup.getJavaString(260));
        if(!isPointer(matrixPointer)) throw new IllegalArgumentException(StringLookup.getJavaString(261));
        checkBounds(matrixPointer, index);
        return ForeignMemory.getLong(matrixPointer + (index * 8L));
    }

    public static void set(long pointer, byte value) {
        if(pointer == 0L) throw new NullPointerException(StringLookup.getJavaString(31));
        if(classId(pointer) != CLASS_ID) throw new IllegalArgumentException(StringLookup.getJavaString(28) + java.lang.Long.toHexString(pointer).toUpperCase() + StringLookup.getJavaString(29) + classId(pointer) + StringLookup.getJavaString(259));
        ForeignMemory.set(pointer, value);
    }

    public static void set(long pointer, int index, byte value) {
        checkBounds(pointer, index);
        if(classId(pointer) != CLASS_ID) throw new IllegalArgumentException(StringLookup.getJavaString(28) + java.lang.Long.toHexString(pointer).toUpperCase() + StringLookup.getJavaString(29) + classId(pointer) + StringLookup.getJavaString(259));
        ForeignMemory.set(pointer + ((long) index), value);
    }

    public static void setPointer(long matrixPointer, int index, long targetPointer) {
        if(matrixPointer == 0L) throw new NullPointerException(StringLookup.getJavaString(262));
        if(!isPointer(matrixPointer)) throw new IllegalArgumentException(StringLookup.getJavaString(261));
        checkBounds(matrixPointer, index);
        ForeignMemory.set(matrixPointer + (index * 8L), targetPointer);
    }

    // =========================================================================
    // 2. UNSAFE OPERATIONS (No Checks, Maximum Speed)
    // =========================================================================

    @Unsafe
    public static byte getUnsafe(long pointer) {
        return ForeignMemory.getUnsafeByte(pointer);
    }

    @Unsafe
    public static byte getUnsafe(long pointer, int index) {
        return ForeignMemory.getUnsafeByte(pointer + ((long) index));
    }

    @Unsafe
    public static long getUnsafePointer(long matrixPointer, int index) {
        return ForeignMemory.getUnsafeLong(matrixPointer + (index * 8L));
    }

    @Unsafe
    public static void setUnsafe(long pointer, byte value) {
        ForeignMemory.setUnsafe(pointer, value);
    }

    @Unsafe
    public static void setUnsafe(long pointer, int index, byte value) {
        ForeignMemory.setUnsafe(pointer + ((long) index), value);
    }

    @Unsafe
    public static void setUnsafePointer(long matrixPointer, int index, long targetPointer) {
        ForeignMemory.setUnsafe(matrixPointer + (index * 8L), targetPointer);
    }

    // =========================================================================
    // 3. VOLATILE OPERATIONS (Thread-Safe, Bounds Checked)
    // =========================================================================

    @Volatile
    public static byte getVolatile(long pointer) {
        if(pointer == 0L) throw new NullPointerException(StringLookup.getJavaString(32));
        if(classId(pointer) != CLASS_ID) throw new IllegalArgumentException(StringLookup.getJavaString(28) + java.lang.Long.toHexString(pointer).toUpperCase() + StringLookup.getJavaString(29) + classId(pointer) + StringLookup.getJavaString(259));
        return ForeignMemory.getVolatileByte(pointer);
    }

    @Volatile
    public static byte getVolatile(long pointer, int index) {
        checkBounds(pointer, index);
        if(classId(pointer) != CLASS_ID) throw new IllegalArgumentException(StringLookup.getJavaString(28) + java.lang.Long.toHexString(pointer).toUpperCase() + StringLookup.getJavaString(29) + classId(pointer) + StringLookup.getJavaString(259));
        return ForeignMemory.getVolatileByte(pointer + ((long) index));
    }

    @Volatile
    public static long getVolatilePointer(long matrixPointer, int index) {
        if(matrixPointer == 0L) throw new NullPointerException(StringLookup.getJavaString(260));
        checkBounds(matrixPointer, index);
        return ForeignMemory.getVolatileLong(matrixPointer + (index * 8L));
    }

    @Volatile
    public static void setVolatile(long pointer, byte value) {
        if(pointer == 0L) throw new NullPointerException(StringLookup.getJavaString(31));
        if(classId(pointer) != CLASS_ID) throw new IllegalArgumentException(StringLookup.getJavaString(28) + java.lang.Long.toHexString(pointer).toUpperCase() + StringLookup.getJavaString(29) + classId(pointer) + StringLookup.getJavaString(259));
        ForeignMemory.setVolatile(pointer, value);
    }

    @Volatile
    public static void setVolatile(long pointer, int index, byte value) {
        checkBounds(pointer, index);
        if(classId(pointer) != CLASS_ID) throw new IllegalArgumentException(StringLookup.getJavaString(28) + java.lang.Long.toHexString(pointer).toUpperCase() + StringLookup.getJavaString(29) + classId(pointer) + StringLookup.getJavaString(259));
        ForeignMemory.setVolatile(pointer + ((long) index), value);
    }

    @Volatile
    public static void setVolatilePointer(long matrixPointer, int index, long targetPointer) {
        if(matrixPointer == 0L) throw new NullPointerException(StringLookup.getJavaString(262));
        checkBounds(matrixPointer, index);
        ForeignMemory.setVolatile(matrixPointer + (index * 8L), targetPointer);
    }

    public static boolean compareAndSet(long pointer, byte expected, byte value) {
        if(pointer == 0L) throw new NullPointerException(StringLookup.getJavaString(31));
        if(classId(pointer) != CLASS_ID) throw new IllegalArgumentException(StringLookup.getJavaString(28) + java.lang.Long.toHexString(pointer).toUpperCase() + StringLookup.getJavaString(29) + classId(pointer) + StringLookup.getJavaString(259));
        return ForeignMemory.compareAndSetByte(pointer, expected, value);
    }

    public static byte getAndSet(long pointer, byte value) {
        if(pointer == 0L) throw new NullPointerException(StringLookup.getJavaString(31));
        if(classId(pointer) != CLASS_ID) throw new IllegalArgumentException(StringLookup.getJavaString(28) + java.lang.Long.toHexString(pointer).toUpperCase() + StringLookup.getJavaString(29) + classId(pointer) + StringLookup.getJavaString(259));
        return ForeignMemory.getAndSetByte(pointer, value);
    }

    // =========================================================================
    // 4. UNSAFE & VOLATILE OPERATIONS (Thread-Safe, No Checks)
    // =========================================================================

    @Unsafe
    @Volatile
    public static byte getUnsafeVolatile(long pointer) {
        return ForeignMemory.getUnsafeVolatileByte(pointer);
    }

    @Unsafe
    @Volatile
    public static byte getUnsafeVolatile(long pointer, int index) {
        return ForeignMemory.getUnsafeVolatileByte(pointer + ((long) index));
    }

    @Unsafe
    @Volatile
    public static long getUnsafeVolatilePointer(long matrixPointer, int index) {
        return ForeignMemory.getUnsafeVolatileLong(matrixPointer + (index * 8L));
    }

    @Unsafe
    @Volatile
    public static void setUnsafeVolatile(long pointer, byte value) {
        ForeignMemory.setUnsafeVolatile(pointer, value);
    }

    @Unsafe
    @Volatile
    public static void setUnsafeVolatile(long pointer, int index, byte value) {
        ForeignMemory.setUnsafeVolatile(pointer + ((long) index), value);
    }

    @Unsafe
    @Volatile
    public static void setUnsafeVolatilePointer(long matrixPointer, int index, long targetPointer) {
        ForeignMemory.setUnsafeVolatile(matrixPointer + (index * 8L), targetPointer);
    }
}