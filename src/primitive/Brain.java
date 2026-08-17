package primitive;

import annotation.Volatile;

import annotation.Required;
import bit.Bit16;
import nio.ForeignMemory;
import oop.TypeRegister;


public final class Brain {

    @Required
    public static final int CLASS_ID = TypeRegister.ID_BRAIN;

    public static final int TYPE_SINGLETON = TypeRegister.BRAIN_SINGLETON; // 0xAA000029
    public static final int TYPE_ARRAY     = TypeRegister.BRAIN_ARRAY;     // 0xBB000029
    public static final int TYPE_MATRIX    = TypeRegister.BRAIN_POINTER;   // 0xCC000029

    // --- CONVERSION METHODS ---
    public static short floatToBFloat16(float val) {
        int bits = java.lang.Float.floatToIntBits(val);
        // Round to nearest even to prevent truncation bias
        int lsb = (bits >>> 16) & 1;
        int bias = 0x7FFF + lsb;
        bits += bias;
        return (short) (bits >>> 16);
    }

    public static float bFloat16ToFloat(short val) {
        int bits = (val & 0xFFFF) << 16;
        return java.lang.Float.intBitsToFloat(bits);
    }

    // =========================================================
    // ALLOCATION — DELEGATED TO THE BIT-WIDTH POOL (bit.Bit16)
    // =========================================================

    public static void freeAll()
    {
        Bit16.freeAll();
    }

    public static long allocateSingleton()
    {
        return Bit16.allocateSingleton(TYPE_SINGLETON);
    }

    public static long allocateArray(int length)
    {
        return Bit16.allocateArray(TYPE_ARRAY, length);
    }

    public static long allocateMatrix(int length)
    {
        return Bit16.allocateMatrix(TYPE_MATRIX, length);
    }

    public static void free(long pointer)
    {
        Bit16.free(pointer);
    }

    // --- MUTATORS & ACCESSORS ---
    public static float get(long pointer) {
        if (pointer == 0L) throw new NullPointerException("Reading from NULL off-heap pointer!");
        short rawVal = ForeignMemory.getShort(pointer);
        return bFloat16ToFloat(rawVal);
    }

    public static float get(long pointer, int index) {
        checkBounds(pointer, index);
        short rawVal = ForeignMemory.getShort(pointer + (index * 2L));
        return bFloat16ToFloat(rawVal);
    }

    public static void set(long pointer, float value) {
        if (pointer == 0L) throw new NullPointerException("Writing to NULL off-heap pointer!");
        short rawVal = floatToBFloat16(value);
        ForeignMemory.setShort(pointer, rawVal);
    }

    public static void set(long pointer, int index, float value) {
        checkBounds(pointer, index);
        short rawVal = floatToBFloat16(value);
        ForeignMemory.setShort(pointer + (index * 2L), rawVal);
    }

    @Volatile
    public static float getVolatile(long pointer) {
        if (pointer == 0L) throw new NullPointerException("Reading from NULL off-heap pointer!");
        short rawVal = ForeignMemory.getVolatileShort(pointer);
        return bFloat16ToFloat(rawVal);
    }

    @Volatile
    public static void setVolatile(long pointer, float value) {
        if (pointer == 0L) throw new NullPointerException("Writing to NULL off-heap pointer!");
        short rawVal = floatToBFloat16(value);
        ForeignMemory.setVolatileShort(pointer, rawVal);
    }

    public static boolean compareAndSet(long pointer, float expected, float value) {
        if (pointer == 0L) throw new NullPointerException("Writing to NULL off-heap pointer!");
        short expectedRaw = floatToBFloat16(expected);
        short valueRaw = floatToBFloat16(value);
        return ForeignMemory.compareAndSetShort(pointer, expectedRaw, valueRaw);
    }

    public static long getPointer(long matrixPointer, int index) {
        if (matrixPointer == 0L) throw new NullPointerException("Accessing NULL matrix pointer!");
        if (!isPointer(matrixPointer)) {
            throw new IllegalArgumentException("Expected Pointer Array (Matrix), but got Type: 0x" + Integer.toHexString(type(matrixPointer)).toUpperCase());
        }
        checkBounds(matrixPointer, index);
        return ForeignMemory.getLong(matrixPointer + (index * 8L));
    }

    public static void setPointer(long matrixPointer, int index, long targetPointer) {
        if (matrixPointer == 0L) throw new NullPointerException("Writing to NULL matrix pointer!");
        if (!isPointer(matrixPointer)) {
            throw new IllegalArgumentException("Expected Pointer Array (Matrix), but got Type: 0x" + Integer.toHexString(type(matrixPointer)).toUpperCase());
        }
        checkBounds(matrixPointer, index);
        ForeignMemory.setLong(matrixPointer + (index * 8L), targetPointer);
    }

    // --- ARCHITECTURAL CHECKS ---
    private static void checkBounds(long pointer, int index) {
        if (pointer == 0L) throw new NullPointerException("Checking bounds on NULL off-heap pointer!");
        int len = length(pointer);
        if (index < 0 || index >= len) {
            throw new IndexOutOfBoundsException("Index " + index + " out of bounds for off-heap brain float length " + len + " (Ptr: 0x" + java.lang.Long.toHexString(pointer).toUpperCase() + ", Type: 0x" + Integer.toHexString(type(pointer)).toUpperCase() + ")");
        }
    }

    public static int classId() {
        return CLASS_ID;
    }

    public static int type(long pointer) {
        return ForeignMemory.getInt(pointer - 8L);
    }

    public static int length(long pointer) {
        return ForeignMemory.getInt(pointer - 4L);
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
}
