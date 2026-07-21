package oop;

import annotation.Draft;
import annotation.Intention;
import annotation.Required;
import annotation.Volatile;
import nio.ForeignMemory;
/**
 * Off-heap generic dynamic struct layout manager.
 */
@Volatile
@Draft
@Intention("Zero-GC off-heap generic dynamic struct layout manager supporting runtime definition, singleton (Level 1), and array (Level 2) allocations.")
public final class Struct {

    @Required
    public static final int CLASS_ID = TypeRegister.CUSTOM_STRUCT;

    private static final int MAX_STRUCTS = 65536;
    private static final long SLOT_SIZE = 24L;

    private static final long REGISTRY_BASE;

    static {
        REGISTRY_BASE = ForeignMemory.allocateNative(MAX_STRUCTS * SLOT_SIZE);
        ForeignMemory.setMemory(REGISTRY_BASE, MAX_STRUCTS * SLOT_SIZE, (byte) 0);
    }

    public static void freeAll() {
        for (int i = 0; i < MAX_STRUCTS; i++) {
            long slot = REGISTRY_BASE + (i * SLOT_SIZE);
            long fieldTypesPtr = ForeignMemory.getLong(slot + 8L);
            long offsetsPtr = ForeignMemory.getLong(slot + 16L);
            if (fieldTypesPtr != 0L) {
                ForeignMemory.freeNative(fieldTypesPtr);
            }
            if (offsetsPtr != 0L) {
                ForeignMemory.freeNative(offsetsPtr);
            }
        }
        ForeignMemory.freeNative(REGISTRY_BASE);
    }

    private static int nextStructId = 1;

    public static synchronized int construct(int... fieldClassIds) {
        int id = nextStructId++;
        define(id, fieldClassIds);
        return id;
    }

    public static long instant(int generic) {
        return allocateSingleton(generic);
    }

    public static long instant(int generic, int length) {
        return allocateArray(generic, length);
    }

    private Struct() {}

    // define a custom struct layout
    public static synchronized void define(int generic, int... fieldClassIds) {
        if (generic < 0 || generic >= MAX_STRUCTS) {
            throw new IllegalArgumentException("Struct ID " + generic + " must be between 0 and " + (MAX_STRUCTS - 1));
        }
        if (fieldClassIds == null || fieldClassIds.length == 0) {
            throw new IllegalArgumentException("Fields layout cannot be empty!");
        }

        long slot = REGISTRY_BASE + (generic * SLOT_SIZE);

        // Free previous if redefined
        long prevFieldTypesPtr = ForeignMemory.getLong(slot + 8L);
        long prevOffsetsPtr = ForeignMemory.getLong(slot + 16L);
        if (prevFieldTypesPtr != 0L) ForeignMemory.freeNative(prevFieldTypesPtr);
        if (prevOffsetsPtr != 0L) ForeignMemory.freeNative(prevOffsetsPtr);

        int currentOffset = 0;
        int len = fieldClassIds.length;
        long fieldTypesPtr = ForeignMemory.allocateNative(len * 4L);
        long offsetsPtr = ForeignMemory.allocateNative(len * 4L);

        for (int i = 0; i < len; i++) {
            ForeignMemory.putInt(fieldTypesPtr + i * 4L, fieldClassIds[i]);
            ForeignMemory.putInt(offsetsPtr + i * 4L, currentOffset);
            currentOffset += Stride.get(fieldClassIds[i]);
        }

        ForeignMemory.putInt(slot, currentOffset); // stride
        ForeignMemory.putInt(slot + 4L, len);      // fieldsCount
        ForeignMemory.putLong(slot + 8L, fieldTypesPtr);
        ForeignMemory.putLong(slot + 16L, offsetsPtr);
    }

    private static void checkFieldType(int generic, int fieldIndex, int expectedClassId) {
        if (generic < 0 || generic >= MAX_STRUCTS) {
            throw new IllegalArgumentException("Invalid Struct ID " + generic);
        }
        long slot = REGISTRY_BASE + (generic * SLOT_SIZE);
        int fieldsCount = ForeignMemory.getInt(slot + 4L);
        long fieldTypesPtr = ForeignMemory.getLong(slot + 8L);

        if (fieldTypesPtr == 0L) {
            throw new IllegalArgumentException("Struct ID 0x" + Integer.toHexString(generic).toUpperCase() + " is not defined!");
        }
        if (fieldIndex < 0 || fieldIndex >= fieldsCount) {
            throw new IndexOutOfBoundsException("Field index " + fieldIndex + " out of bounds for struct (fields count: " + fieldsCount + ")");
        }
        int classId = ForeignMemory.getInt(fieldTypesPtr + fieldIndex * 4L);
        if (classId != expectedClassId) {
            throw new IllegalArgumentException("Type mismatch: expected field class ID 0x" + Integer.toHexString(expectedClassId).toUpperCase() + " but found 0x" + Integer.toHexString(classId).toUpperCase());
        }
    }

    private static int getStructIdFromPointer(long userPtr) {
        if (userPtr == 0L) throw new NullPointerException("Accessing NULL off-heap struct pointer!");
        int type = ForeignMemory.getInt(userPtr - 8L);
        return TypeRegister.getClassId(type);
    }

    private static int getOffset(int generic, int fieldIndex) {
        long slot = REGISTRY_BASE + (generic * SLOT_SIZE);
        long offsetsPtr = ForeignMemory.getLong(slot + 16L);
        return ForeignMemory.getInt(offsetsPtr + fieldIndex * 4L);
    }

    private static int getStride(int generic) {
        if (generic < 0 || generic >= MAX_STRUCTS) {
            return 0;
        }
        long slot = REGISTRY_BASE + (generic * SLOT_SIZE);
        return ForeignMemory.getInt(slot);
    }

    // Level 1: Allocate a single struct (Singleton)
    public static long allocateSingleton(int generic) {
        int stride = getStride(generic);
        if (stride == 0) throw new IllegalArgumentException("Struct ID 0x" + Integer.toHexString(generic).toUpperCase() + " is not defined!");

        long block = ForeignMemory.allocateNative(8L + stride);
        long userPtr = block + 8L;

        // Write header: type (FORM_SINGLETON | generic), length (1)
        ForeignMemory.putInt(block, TypeRegister.FORM_SINGLETON | generic);
        ForeignMemory.putInt(block + 4L, 1);

        // Zero-initialize fields
        ForeignMemory.setMemory(userPtr, stride, (byte) 0);

        return userPtr;
    }

    // Level 2: Allocate an array of structs
    public static long allocateArray(int generic, int length) {
        int stride = getStride(generic);
        if (stride == 0) throw new IllegalArgumentException("Struct ID 0x" + Integer.toHexString(generic).toUpperCase() + " is not defined!");
        if (length <= 0) throw new IllegalArgumentException("Array length must be positive!");

        long bufferBytes = (long) length * stride;
        long block = ForeignMemory.allocateNative(8L + bufferBytes);
        long userPtr = block + 8L;

        // Write header: type (FORM_ARRAY | generic), length (length)
        ForeignMemory.putInt(block, TypeRegister.FORM_ARRAY | generic);
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

    // =========================================================================
    // FIELD MUTATORS & ACCESSORS (SINGLETON / LEVEL 1)
    // =========================================================================

    public static void setInt32(long userPtr, int fieldIndex, int value) {
        int generic = getStructIdFromPointer(userPtr);
        setInt32(generic, userPtr, fieldIndex, value);
    }

    public static void setInt32(int generic, long userPtr, int fieldIndex, int value) {
        checkFieldType(generic, fieldIndex, TypeRegister.ID_INT32);
        int offset = getOffset(generic, fieldIndex);
        ForeignMemory.putInt(userPtr + offset, value);
    }

    public static int getInt32(long userPtr, int fieldIndex) {
        int generic = getStructIdFromPointer(userPtr);
        return getInt32(generic, userPtr, fieldIndex);
    }

    public static int getInt32(int generic, long userPtr, int fieldIndex) {
        checkFieldType(generic, fieldIndex, TypeRegister.ID_INT32);
        int offset = getOffset(generic, fieldIndex);
        return ForeignMemory.getInt(userPtr + offset);
    }

    public static void setInt64(long userPtr, int fieldIndex, long value) {
        int generic = getStructIdFromPointer(userPtr);
        setInt64(generic, userPtr, fieldIndex, value);
    }

    public static void setInt64(int generic, long userPtr, int fieldIndex, long value) {
        checkFieldType(generic, fieldIndex, TypeRegister.ID_INT64);
        int offset = getOffset(generic, fieldIndex);
        ForeignMemory.putLong(userPtr + offset, value);
    }

    public static long getInt64(long userPtr, int fieldIndex) {
        int generic = getStructIdFromPointer(userPtr);
        return getInt64(generic, userPtr, fieldIndex);
    }

    public static long getInt64(int generic, long userPtr, int fieldIndex) {
        checkFieldType(generic, fieldIndex, TypeRegister.ID_INT64);
        int offset = getOffset(generic, fieldIndex);
        return ForeignMemory.getLong(userPtr + offset);
    }

    public static void setFloat32(long userPtr, int fieldIndex, float value) {
        int generic = getStructIdFromPointer(userPtr);
        setFloat32(generic, userPtr, fieldIndex, value);
    }

    public static void setFloat32(int generic, long userPtr, int fieldIndex, float value) {
        checkFieldType(generic, fieldIndex, TypeRegister.ID_FLOAT32);
        int offset = getOffset(generic, fieldIndex);
        ForeignMemory.putFloat(userPtr + offset, value);
    }

    public static float getFloat32(long userPtr, int fieldIndex) {
        int generic = getStructIdFromPointer(userPtr);
        return getFloat32(generic, userPtr, fieldIndex);
    }

    public static float getFloat32(int generic, long userPtr, int fieldIndex) {
        checkFieldType(generic, fieldIndex, TypeRegister.ID_FLOAT32);
        int offset = getOffset(generic, fieldIndex);
        return ForeignMemory.getFloat(userPtr + offset);
    }

    public static void setFloat64(long userPtr, int fieldIndex, double value) {
        int generic = getStructIdFromPointer(userPtr);
        setFloat64(generic, userPtr, fieldIndex, value);
    }

    public static void setFloat64(int generic, long userPtr, int fieldIndex, double value) {
        checkFieldType(generic, fieldIndex, TypeRegister.ID_FLOAT64);
        int offset = getOffset(generic, fieldIndex);
        ForeignMemory.putDouble(userPtr + offset, value);
    }

    public static double getFloat64(long userPtr, int fieldIndex) {
        int generic = getStructIdFromPointer(userPtr);
        return getFloat64(generic, userPtr, fieldIndex);
    }

    public static double getFloat64(int generic, long userPtr, int fieldIndex) {
        checkFieldType(generic, fieldIndex, TypeRegister.ID_FLOAT64);
        int offset = getOffset(generic, fieldIndex);
        return ForeignMemory.getDouble(userPtr + offset);
    }

    public static void setByte(long userPtr, int fieldIndex, byte value) {
        int generic = getStructIdFromPointer(userPtr);
        setByte(generic, userPtr, fieldIndex, value);
    }

    public static void setByte(int generic, long userPtr, int fieldIndex, byte value) {
        checkFieldType(generic, fieldIndex, TypeRegister.ID_BYTE);
        int offset = getOffset(generic, fieldIndex);
        ForeignMemory.putByte(userPtr + offset, value);
    }

    public static byte getByte(long userPtr, int fieldIndex) {
        int generic = getStructIdFromPointer(userPtr);
        return getByte(generic, userPtr, fieldIndex);
    }

    public static byte getByte(int generic, long userPtr, int fieldIndex) {
        checkFieldType(generic, fieldIndex, TypeRegister.ID_BYTE);
        int offset = getOffset(generic, fieldIndex);
        return ForeignMemory.getByte(userPtr + offset);
    }

    public static void setShort(long userPtr, int fieldIndex, short value) {
        int generic = getStructIdFromPointer(userPtr);
        setShort(generic, userPtr, fieldIndex, value);
    }

    public static void setShort(int generic, long userPtr, int fieldIndex, short value) {
        checkFieldType(generic, fieldIndex, TypeRegister.ID_SHORT);
        int offset = getOffset(generic, fieldIndex);
        ForeignMemory.putShort(userPtr + offset, value);
    }

    public static short getShort(long userPtr, int fieldIndex) {
        int generic = getStructIdFromPointer(userPtr);
        return getShort(generic, userPtr, fieldIndex);
    }

    public static short getShort(int generic, long userPtr, int fieldIndex) {
        checkFieldType(generic, fieldIndex, TypeRegister.ID_SHORT);
        int offset = getOffset(generic, fieldIndex);
        return ForeignMemory.getShort(userPtr + offset);
    }

    // =========================================================================
    // ARRAY FIELD MUTATORS & ACCESSORS (ARRAY / LEVEL 2)
    // =========================================================================

    public static void setInt32(long userPtr, int elementIndex, int fieldIndex, int value) {
        int generic = getStructIdFromPointer(userPtr);
        setInt32(generic, userPtr, elementIndex, fieldIndex, value);
    }

    public static void setInt32(int generic, long userPtr, int elementIndex, int fieldIndex, int value) {
        checkFieldType(generic, fieldIndex, TypeRegister.ID_INT32);
        int stride = getStride(generic);
        int offset = getOffset(generic, fieldIndex);
        ForeignMemory.putInt(userPtr + (long) elementIndex * stride + offset, value);
    }

    public static int getInt32(long userPtr, int elementIndex, int fieldIndex) {
        int generic = getStructIdFromPointer(userPtr);
        return getInt32(generic, userPtr, elementIndex, fieldIndex);
    }

    public static int getInt32(int generic, long userPtr, int elementIndex, int fieldIndex) {
        checkFieldType(generic, fieldIndex, TypeRegister.ID_INT32);
        int stride = getStride(generic);
        int offset = getOffset(generic, fieldIndex);
        return ForeignMemory.getInt(userPtr + (long) elementIndex * stride + offset);
    }

    public static void setInt64(long userPtr, int elementIndex, int fieldIndex, long value) {
        int generic = getStructIdFromPointer(userPtr);
        setInt64(generic, userPtr, elementIndex, fieldIndex, value);
    }

    public static void setInt64(int generic, long userPtr, int elementIndex, int fieldIndex, long value) {
        checkFieldType(generic, fieldIndex, TypeRegister.ID_INT64);
        int stride = getStride(generic);
        int offset = getOffset(generic, fieldIndex);
        ForeignMemory.putLong(userPtr + (long) elementIndex * stride + offset, value);
    }

    public static long getInt64(long userPtr, int elementIndex, int fieldIndex) {
        int generic = getStructIdFromPointer(userPtr);
        return getInt64(generic, userPtr, elementIndex, fieldIndex);
    }

    public static long getInt64(int generic, long userPtr, int elementIndex, int fieldIndex) {
        checkFieldType(generic, fieldIndex, TypeRegister.ID_INT64);
        int stride = getStride(generic);
        int offset = getOffset(generic, fieldIndex);
        return ForeignMemory.getLong(userPtr + (long) elementIndex * stride + offset);
    }

    public static void setFloat32(long userPtr, int elementIndex, int fieldIndex, float value) {
        int generic = getStructIdFromPointer(userPtr);
        setFloat32(generic, userPtr, elementIndex, fieldIndex, value);
    }

    public static void setFloat32(int generic, long userPtr, int elementIndex, int fieldIndex, float value) {
        checkFieldType(generic, fieldIndex, TypeRegister.ID_FLOAT32);
        int stride = getStride(generic);
        int offset = getOffset(generic, fieldIndex);
        ForeignMemory.putFloat(userPtr + (long) elementIndex * stride + offset, value);
    }

    public static float getFloat32(long userPtr, int elementIndex, int fieldIndex) {
        int generic = getStructIdFromPointer(userPtr);
        return getFloat32(generic, userPtr, elementIndex, fieldIndex);
    }

    public static float getFloat32(int generic, long userPtr, int elementIndex, int fieldIndex) {
        checkFieldType(generic, fieldIndex, TypeRegister.ID_FLOAT32);
        int stride = getStride(generic);
        int offset = getOffset(generic, fieldIndex);
        return ForeignMemory.getFloat(userPtr + (long) elementIndex * stride + offset);
    }

    public static void setFloat64(long userPtr, int elementIndex, int fieldIndex, double value) {
        int generic = getStructIdFromPointer(userPtr);
        setFloat64(generic, userPtr, elementIndex, fieldIndex, value);
    }

    public static void setFloat64(int generic, long userPtr, int elementIndex, int fieldIndex, double value) {
        checkFieldType(generic, fieldIndex, TypeRegister.ID_FLOAT64);
        int stride = getStride(generic);
        int offset = getOffset(generic, fieldIndex);
        ForeignMemory.putDouble(userPtr + (long) elementIndex * stride + offset, value);
    }

    public static double getFloat64(long userPtr, int elementIndex, int fieldIndex) {
        int generic = getStructIdFromPointer(userPtr);
        return getFloat64(generic, userPtr, elementIndex, fieldIndex);
    }

    public static double getFloat64(int generic, long userPtr, int elementIndex, int fieldIndex) {
        checkFieldType(generic, fieldIndex, TypeRegister.ID_FLOAT64);
        int stride = getStride(generic);
        int offset = getOffset(generic, fieldIndex);
        return ForeignMemory.getDouble(userPtr + (long) elementIndex * stride + offset);
    }

    public static void setByte(long userPtr, int elementIndex, int fieldIndex, byte value) {
        int generic = getStructIdFromPointer(userPtr);
        setByte(generic, userPtr, elementIndex, fieldIndex, value);
    }

    public static void setByte(int generic, long userPtr, int elementIndex, int fieldIndex, byte value) {
        checkFieldType(generic, fieldIndex, TypeRegister.ID_BYTE);
        int stride = getStride(generic);
        int offset = getOffset(generic, fieldIndex);
        ForeignMemory.putByte(userPtr + (long) elementIndex * stride + offset, value);
    }

    public static byte getByte(long userPtr, int elementIndex, int fieldIndex) {
        int generic = getStructIdFromPointer(userPtr);
        return getByte(generic, userPtr, elementIndex, fieldIndex);
    }

    public static byte getByte(int generic, long userPtr, int elementIndex, int fieldIndex) {
        checkFieldType(generic, fieldIndex, TypeRegister.ID_BYTE);
        int stride = getStride(generic);
        int offset = getOffset(generic, fieldIndex);
        return ForeignMemory.getByte(userPtr + (long) elementIndex * stride + offset);
    }

    public static void setShort(long userPtr, int elementIndex, int fieldIndex, short value) {
        int generic = getStructIdFromPointer(userPtr);
        setShort(generic, userPtr, elementIndex, fieldIndex, value);
    }

    public static void setShort(int generic, long userPtr, int elementIndex, int fieldIndex, short value) {
        checkFieldType(generic, fieldIndex, TypeRegister.ID_SHORT);
        int stride = getStride(generic);
        int offset = getOffset(generic, fieldIndex);
        ForeignMemory.putShort(userPtr + (long) elementIndex * stride + offset, value);
    }

    public static short getShort(long userPtr, int elementIndex, int fieldIndex) {
        int generic = getStructIdFromPointer(userPtr);
        return getShort(generic, userPtr, elementIndex, fieldIndex);
    }

    public static short getShort(int generic, long userPtr, int elementIndex, int fieldIndex) {
        checkFieldType(generic, fieldIndex, TypeRegister.ID_SHORT);
        int stride = getStride(generic);
        int offset = getOffset(generic, fieldIndex);
        return ForeignMemory.getShort(userPtr + (long) elementIndex * stride + offset);
    }

    public static int stride(int generic) {
        return getStride(generic);
    }

    public static int classId() {
        return CLASS_ID;
    }
}
