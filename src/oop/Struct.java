package oop;

import annotation.Draft;
import annotation.Intention;
import annotation.Required;
import annotation.Volatile;
import nio.ForeignMemory;

import java.lang.foreign.MemorySegment;
import java.lang.foreign.ValueLayout;
import java.lang.invoke.VarHandle;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Off-heap generic dynamic struct layout manager.
 */
@Volatile
@Draft
@Intention("Zero-GC off-heap generic dynamic struct layout manager supporting runtime definition, singleton (Level 1), and array (Level 2) allocations.")
public final class Struct {

    @Required
    public static final int CLASS_ID = TypeRegister.CUSTOM_STRUCT;

    private static final ConcurrentHashMap<Integer, Integer> strides = new ConcurrentHashMap<>();
    private static final ConcurrentHashMap<Integer, int[]> offsets = new ConcurrentHashMap<>();
    private static final ConcurrentHashMap<Integer, int[]> fieldTypes = new ConcurrentHashMap<>();

    private Struct() {}

    // define a custom struct layout
    public static void define(int structId, int... fieldClassIds) {
        if (fieldClassIds == null || fieldClassIds.length == 0) {
            throw new IllegalArgumentException("Fields layout cannot be empty!");
        }

        int currentOffset = 0;
        int[] fieldOffsets = new int[fieldClassIds.length];
        for (int i = 0; i < fieldClassIds.length; i++) {
            fieldOffsets[i] = currentOffset;
            currentOffset += Stride.get(fieldClassIds[i]);
        }

        strides.put(structId, currentOffset);
        offsets.put(structId, fieldOffsets);
        fieldTypes.put(structId, fieldClassIds);
    }

    private static void checkFieldType(int structId, int fieldIndex, int expectedClassId) {
        int[] types = fieldTypes.get(structId);
        if (types == null) throw new IllegalArgumentException("Struct ID 0x" + Integer.toHexString(structId).toUpperCase() + " is not defined!");
        if (fieldIndex < 0 || fieldIndex >= types.length) {
            throw new IndexOutOfBoundsException("Field index " + fieldIndex + " out of bounds for struct (fields count: " + types.length + ")");
        }
        if (types[fieldIndex] != expectedClassId) {
            throw new IllegalArgumentException("Type mismatch: expected field class ID 0x" + Integer.toHexString(expectedClassId).toUpperCase() + " but found 0x" + Integer.toHexString(types[fieldIndex]).toUpperCase());
        }
    }

    private static int getStructIdFromPointer(long userPtr) {
        if (userPtr == 0L) throw new NullPointerException("Accessing NULL off-heap struct pointer!");
        int type = ForeignMemory.getInt(userPtr - 8L);
        return TypeRegister.getClassId(type);
    }

    // Level 1: Allocate a single struct (Singleton)
    public static long allocateSingleton(int structId) {
        Integer stride = strides.get(structId);
        if (stride == null) throw new IllegalArgumentException("Struct ID 0x" + Integer.toHexString(structId).toUpperCase() + " is not defined!");

        long block = ForeignMemory.allocateNative(8L + stride);
        long userPtr = block + 8L;

        // Write header: type (FORM_SINGLETON | structId), length (1)
        ForeignMemory.putInt(block, TypeRegister.FORM_SINGLETON | structId);
        ForeignMemory.putInt(block + 4L, 1);

        // Zero-initialize fields
        ForeignMemory.setMemory(userPtr, stride, (byte) 0);

        return userPtr;
    }

    // Level 2: Allocate an array of structs
    public static long allocateArray(int structId, int length) {
        Integer stride = strides.get(structId);
        if (stride == null) throw new IllegalArgumentException("Struct ID 0x" + Integer.toHexString(structId).toUpperCase() + " is not defined!");
        if (length <= 0) throw new IllegalArgumentException("Array length must be positive!");

        long bufferBytes = (long) length * stride;
        long block = ForeignMemory.allocateNative(8L + bufferBytes);
        long userPtr = block + 8L;

        // Write header: type (FORM_ARRAY | structId), length (length)
        ForeignMemory.putInt(block, TypeRegister.FORM_ARRAY | structId);
        ForeignMemory.putInt(block + 4L, length);

        // Zero-initialize elements
        ForeignMemory.setMemory(userPtr, bufferBytes, (byte) 0);

        return userPtr;
    }

    // free a struct singleton or array
    public static void free(long userPtr) {
        if (userPtr == 0L) return;
        long block = userPtr - 8L;
        int type = ForeignMemory.getInt(block);
        if (type == 0 || (!TypeRegister.isSingleton(type) && !TypeRegister.isArray(type))) {
            throw new IllegalStateException("Double free or corrupt struct pointer: 0x" + Long.toHexString(userPtr).toUpperCase());
        }
        ForeignMemory.putInt(block, 0);
        ForeignMemory.putInt(block + 4L, -1);
        ForeignMemory.freeNative(block);
    }

    // =========================================================================
    // FIELD MUTATORS & ACCESSORS (SINGLETON / LEVEL 1)
    // =========================================================================

    public static void setInt32(long userPtr, int fieldIndex, int value) {
        int structId = getStructIdFromPointer(userPtr);
        checkFieldType(structId, fieldIndex, TypeRegister.ID_INT32);
        int offset = offsets.get(structId)[fieldIndex];
        ForeignMemory.putInt(userPtr + offset, value);
    }

    public static int getInt32(long userPtr, int fieldIndex) {
        int structId = getStructIdFromPointer(userPtr);
        checkFieldType(structId, fieldIndex, TypeRegister.ID_INT32);
        int offset = offsets.get(structId)[fieldIndex];
        return ForeignMemory.getInt(userPtr + offset);
    }

    public static void setInt64(long userPtr, int fieldIndex, long value) {
        int structId = getStructIdFromPointer(userPtr);
        checkFieldType(structId, fieldIndex, TypeRegister.ID_INT64);
        int offset = offsets.get(structId)[fieldIndex];
        ForeignMemory.putLong(userPtr + offset, value);
    }

    public static long getInt64(long userPtr, int fieldIndex) {
        int structId = getStructIdFromPointer(userPtr);
        checkFieldType(structId, fieldIndex, TypeRegister.ID_INT64);
        int offset = offsets.get(structId)[fieldIndex];
        return ForeignMemory.getLong(userPtr + offset);
    }

    public static void setFloat32(long userPtr, int fieldIndex, float value) {
        int structId = getStructIdFromPointer(userPtr);
        checkFieldType(structId, fieldIndex, TypeRegister.ID_FLOAT32);
        int offset = offsets.get(structId)[fieldIndex];
        ForeignMemory.putFloat(userPtr + offset, value);
    }

    public static float getFloat32(long userPtr, int fieldIndex) {
        int structId = getStructIdFromPointer(userPtr);
        checkFieldType(structId, fieldIndex, TypeRegister.ID_FLOAT32);
        int offset = offsets.get(structId)[fieldIndex];
        return ForeignMemory.getFloat(userPtr + offset);
    }

    public static void setFloat64(long userPtr, int fieldIndex, double value) {
        int structId = getStructIdFromPointer(userPtr);
        checkFieldType(structId, fieldIndex, TypeRegister.ID_FLOAT64);
        int offset = offsets.get(structId)[fieldIndex];
        ForeignMemory.putDouble(userPtr + offset, value);
    }

    public static double getFloat64(long userPtr, int fieldIndex) {
        int structId = getStructIdFromPointer(userPtr);
        checkFieldType(structId, fieldIndex, TypeRegister.ID_FLOAT64);
        int offset = offsets.get(structId)[fieldIndex];
        return ForeignMemory.getDouble(userPtr + offset);
    }

    public static void setByte(long userPtr, int fieldIndex, byte value) {
        int structId = getStructIdFromPointer(userPtr);
        checkFieldType(structId, fieldIndex, TypeRegister.ID_BYTE);
        int offset = offsets.get(structId)[fieldIndex];
        ForeignMemory.putByte(userPtr + offset, value);
    }

    public static byte getByte(long userPtr, int fieldIndex) {
        int structId = getStructIdFromPointer(userPtr);
        checkFieldType(structId, fieldIndex, TypeRegister.ID_BYTE);
        int offset = offsets.get(structId)[fieldIndex];
        return ForeignMemory.getByte(userPtr + offset);
    }

    public static void setShort(long userPtr, int fieldIndex, short value) {
        int structId = getStructIdFromPointer(userPtr);
        checkFieldType(structId, fieldIndex, TypeRegister.ID_SHORT);
        int offset = offsets.get(structId)[fieldIndex];
        ForeignMemory.putShort(userPtr + offset, value);
    }

    public static short getShort(long userPtr, int fieldIndex) {
        int structId = getStructIdFromPointer(userPtr);
        checkFieldType(structId, fieldIndex, TypeRegister.ID_SHORT);
        int offset = offsets.get(structId)[fieldIndex];
        return ForeignMemory.getShort(userPtr + offset);
    }

    // =========================================================================
    // ARRAY FIELD MUTATORS & ACCESSORS (ARRAY / LEVEL 2)
    // =========================================================================

    public static void setInt32(long userPtr, int elementIndex, int fieldIndex, int value) {
        int structId = getStructIdFromPointer(userPtr);
        checkFieldType(structId, fieldIndex, TypeRegister.ID_INT32);
        int stride = strides.get(structId);
        int offset = offsets.get(structId)[fieldIndex];
        ForeignMemory.putInt(userPtr + (long) elementIndex * stride + offset, value);
    }

    public static int getInt32(long userPtr, int elementIndex, int fieldIndex) {
        int structId = getStructIdFromPointer(userPtr);
        checkFieldType(structId, fieldIndex, TypeRegister.ID_INT32);
        int stride = strides.get(structId);
        int offset = offsets.get(structId)[fieldIndex];
        return ForeignMemory.getInt(userPtr + (long) elementIndex * stride + offset);
    }

    public static void setInt64(long userPtr, int elementIndex, int fieldIndex, long value) {
        int structId = getStructIdFromPointer(userPtr);
        checkFieldType(structId, fieldIndex, TypeRegister.ID_INT64);
        int stride = strides.get(structId);
        int offset = offsets.get(structId)[fieldIndex];
        ForeignMemory.putLong(userPtr + (long) elementIndex * stride + offset, value);
    }

    public static long getInt64(long userPtr, int elementIndex, int fieldIndex) {
        int structId = getStructIdFromPointer(userPtr);
        checkFieldType(structId, fieldIndex, TypeRegister.ID_INT64);
        int stride = strides.get(structId);
        int offset = offsets.get(structId)[fieldIndex];
        return ForeignMemory.getLong(userPtr + (long) elementIndex * stride + offset);
    }

    public static void setFloat32(long userPtr, int elementIndex, int fieldIndex, float value) {
        int structId = getStructIdFromPointer(userPtr);
        checkFieldType(structId, fieldIndex, TypeRegister.ID_FLOAT32);
        int stride = strides.get(structId);
        int offset = offsets.get(structId)[fieldIndex];
        ForeignMemory.putFloat(userPtr + (long) elementIndex * stride + offset, value);
    }

    public static float getFloat32(long userPtr, int elementIndex, int fieldIndex) {
        int structId = getStructIdFromPointer(userPtr);
        checkFieldType(structId, fieldIndex, TypeRegister.ID_FLOAT32);
        int stride = strides.get(structId);
        int offset = offsets.get(structId)[fieldIndex];
        return ForeignMemory.getFloat(userPtr + (long) elementIndex * stride + offset);
    }

    public static void setFloat64(long userPtr, int elementIndex, int fieldIndex, double value) {
        int structId = getStructIdFromPointer(userPtr);
        checkFieldType(structId, fieldIndex, TypeRegister.ID_FLOAT64);
        int stride = strides.get(structId);
        int offset = offsets.get(structId)[fieldIndex];
        ForeignMemory.putDouble(userPtr + (long) elementIndex * stride + offset, value);
    }

    public static double getFloat64(long userPtr, int elementIndex, int fieldIndex) {
        int structId = getStructIdFromPointer(userPtr);
        checkFieldType(structId, fieldIndex, TypeRegister.ID_FLOAT64);
        int stride = strides.get(structId);
        int offset = offsets.get(structId)[fieldIndex];
        return ForeignMemory.getDouble(userPtr + (long) elementIndex * stride + offset);
    }

    public static void setByte(long userPtr, int elementIndex, int fieldIndex, byte value) {
        int structId = getStructIdFromPointer(userPtr);
        checkFieldType(structId, fieldIndex, TypeRegister.ID_BYTE);
        int stride = strides.get(structId);
        int offset = offsets.get(structId)[fieldIndex];
        ForeignMemory.putByte(userPtr + (long) elementIndex * stride + offset, value);
    }

    public static byte getByte(long userPtr, int elementIndex, int fieldIndex) {
        int structId = getStructIdFromPointer(userPtr);
        checkFieldType(structId, fieldIndex, TypeRegister.ID_BYTE);
        int stride = strides.get(structId);
        int offset = offsets.get(structId)[fieldIndex];
        return ForeignMemory.getByte(userPtr + (long) elementIndex * stride + offset);
    }

    public static void setShort(long userPtr, int elementIndex, int fieldIndex, short value) {
        int structId = getStructIdFromPointer(userPtr);
        checkFieldType(structId, fieldIndex, TypeRegister.ID_SHORT);
        int stride = strides.get(structId);
        int offset = offsets.get(structId)[fieldIndex];
        ForeignMemory.putShort(userPtr + (long) elementIndex * stride + offset, value);
    }

    public static short getShort(long userPtr, int elementIndex, int fieldIndex) {
        int structId = getStructIdFromPointer(userPtr);
        checkFieldType(structId, fieldIndex, TypeRegister.ID_SHORT);
        int stride = strides.get(structId);
        int offset = offsets.get(structId)[fieldIndex];
        return ForeignMemory.getShort(userPtr + (long) elementIndex * stride + offset);
    }

    public static int stride(int structId) {
        Integer s = strides.get(structId);
        return s == null ? 0 : s;
    }

    public static int classId() {
        return CLASS_ID;
    }
}
