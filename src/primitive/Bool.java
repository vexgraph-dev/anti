package primitive;

import annotation.Required;
import annotation.Unsafe;
import bit.Bit8;
import nio.ForeignMemory;
import oop.TypeRegister;


public final class Bool {

    @Required
    public static final int CLASS_ID = TypeRegister.ID_BOOL;

    public static final int TYPE_SINGLETON = TypeRegister.BOOL_SINGLETON; // 0xAA000005
    public static final int TYPE_ARRAY     = TypeRegister.BOOL_ARRAY;     // 0xBB000005
    public static final int TYPE_MATRIX    = TypeRegister.BOOL_POINTER;   // 0xCC000005

    
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

    public static boolean get(long pointer) {
        if (pointer == 0L) throw new NullPointerException("Accessing NULL off-heap pointer!");
        return ForeignMemory.getUnsafeByte(pointer) != 0;
    }

    public static boolean get(long pointer, int index) { 
        checkBounds(pointer, index);
        return ForeignMemory.getUnsafeByte(pointer + index) != 0;
    }

    public static long getPointer(long matrixPointer, int index) { 
        if (matrixPointer == 0L) throw new NullPointerException("Accessing NULL matrix pointer!");
        if(!isPointer(matrixPointer)) throw new IllegalArgumentException("Expected Pointer Array (Matrix), but got Type: 0x" + Integer.toHexString(type(matrixPointer)).toUpperCase());
        checkBounds(matrixPointer, index);
        return ForeignMemory.getUnsafeLong(matrixPointer + (index * 8L)); 
    }

    public static void set(long pointer, boolean value) {
        if (pointer == 0L) throw new NullPointerException("Writing to NULL off-heap pointer!");
        if (classId(pointer) != CLASS_ID) throw new IllegalArgumentException("Pointer 0x" + java.lang.Long.toHexString(pointer).toUpperCase() + " is Class ID " + classId(pointer) + ", expected Bool (Class ID " + CLASS_ID + ")");
        ForeignMemory.setByte(pointer, (byte) (value ? 1 : 0));
    }

    public static void set(long pointer, int index, boolean value) { 
        checkBounds(pointer, index);
        if (classId(pointer) != CLASS_ID) throw new IllegalArgumentException("Pointer 0x" + java.lang.Long.toHexString(pointer).toUpperCase() + " is Class ID " + classId(pointer) + ", expected Bool (Class ID " + CLASS_ID + ")");
        ForeignMemory.setByte(pointer + index, (byte) (value ? 1 : 0));
    }

    public static void setPointer(long matrixPointer, int index, long targetPointer) { 
        if (matrixPointer == 0L) throw new NullPointerException("Writing to NULL matrix pointer!");
        if(!isPointer(matrixPointer)) throw new IllegalArgumentException("Expected Pointer Array (Matrix), but got Type: 0x" + Integer.toHexString(type(matrixPointer)).toUpperCase());
        if (classId(matrixPointer) != CLASS_ID) throw new IllegalArgumentException("Pointer 0x" + java.lang.Long.toHexString(matrixPointer).toUpperCase() + " is Class ID " + classId(matrixPointer) + ", expected Bool (Class ID " + CLASS_ID + ")");
        checkBounds(matrixPointer, index);
        ForeignMemory.set(matrixPointer + (index * 8L), targetPointer); 
    }

    private static void checkBounds(long pointer, int index) {
        if (pointer == 0L) throw new NullPointerException("Checking bounds on NULL off-heap pointer!");
        int len = length(pointer);
        if(index < 0 || index >= len) throw new IndexOutOfBoundsException("Index " + index + " out of bounds for off-heap bool length " + len + " (Ptr: 0x" + java.lang.Long.toHexString(pointer).toUpperCase() + ", Type: 0x" + Integer.toHexString(type(pointer)).toUpperCase() + ")");
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
    public static boolean getUnsafe(long pointer) {
        return ForeignMemory.getUnsafeByte(pointer) != 0;
    }

    @Unsafe
    public static boolean getUnsafe(long pointer, int index) {
        return ForeignMemory.getUnsafeByte(pointer + index) != 0;
    }

    @Unsafe
    public static long getUnsafePointer(long matrixPointer, int index) {
        return ForeignMemory.getUnsafeLong(matrixPointer + (index * 8L));
    }

    @Unsafe
    public static void setUnsafe(long pointer, boolean value) {
        ForeignMemory.setUnsafeByte(pointer, (byte) (value ? 1 : 0));
    }

    @Unsafe
    public static void setUnsafe(long pointer, int index, boolean value) {
        ForeignMemory.setUnsafeByte(pointer + index, (byte) (value ? 1 : 0));
    }

    @Unsafe
    public static void setUnsafePointer(long matrixPointer, int index, long targetPointer) {
        ForeignMemory.setUnsafeLong(matrixPointer + (index * 8L), targetPointer);
    }

    // --- VOLATILE VARIANTS ---

    public static boolean getVolatile(long pointer) {
        if (pointer == 0L) throw new NullPointerException("Reading from NULL off-heap pointer!");
        return ForeignMemory.getVolatileByte(pointer) != 0;
    }

    public static boolean getVolatile(long pointer, int index) {
        checkBounds(pointer, index);
        return ForeignMemory.getVolatileByte(pointer + index) != 0;
    }

    public static long getVolatilePointer(long matrixPointer, int index) {
        checkBounds(matrixPointer, index);
        return ForeignMemory.getVolatileLong(matrixPointer + (index * 8L));
    }

    public static void setVolatile(long pointer, boolean value) {
        if (pointer == 0L) throw new NullPointerException("Writing to NULL off-heap pointer!");
        ForeignMemory.setVolatileByte(pointer, (byte) (value ? 1 : 0));
    }

    public static void setVolatile(long pointer, int index, boolean value) {
        checkBounds(pointer, index);
        ForeignMemory.setVolatileByte(pointer + index, (byte) (value ? 1 : 0));
    }

    public static void setVolatilePointer(long matrixPointer, int index, long targetPointer) {
        checkBounds(matrixPointer, index);
        ForeignMemory.setVolatileLong(matrixPointer + (index * 8L), targetPointer);
    }

    // --- UNSAFE VOLATILE VARIANTS ---

    @Unsafe
    public static boolean getUnsafeVolatile(long pointer) {
        return ForeignMemory.getUnsafeVolatileByte(pointer) != 0;
    }

    @Unsafe
    public static boolean getUnsafeVolatile(long pointer, int index) {
        return ForeignMemory.getUnsafeVolatileByte(pointer + index) != 0;
    }

    @Unsafe
    public static long getUnsafeVolatilePointer(long matrixPointer, int index) {
        return ForeignMemory.getUnsafeVolatileLong(matrixPointer + (index * 8L));
    }

    @Unsafe
    public static void setUnsafeVolatile(long pointer, boolean value) {
        ForeignMemory.setUnsafeVolatileByte(pointer, (byte) (value ? 1 : 0));
    }

    @Unsafe
    public static void setUnsafeVolatile(long pointer, int index, boolean value) {
        ForeignMemory.setUnsafeVolatileByte(pointer + index, (byte) (value ? 1 : 0));
    }

    @Unsafe
    public static void setUnsafeVolatilePointer(long matrixPointer, int index, long targetPointer) {
        ForeignMemory.setUnsafeVolatileLong(matrixPointer + (index * 8L), targetPointer);
    }

}
