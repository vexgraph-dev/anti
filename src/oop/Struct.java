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
        int id = TypeRegister.CUSTOM_STRUCT + nextStructId++;
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
        int index = generic - TypeRegister.CUSTOM_STRUCT;
        if (index < 0 || index >= MAX_STRUCTS) {
            throw new IllegalArgumentException("Struct ID " + generic + " must map to index between 0 and " + (MAX_STRUCTS - 1));
        }
        if (fieldClassIds == null || fieldClassIds.length == 0) {
            throw new IllegalArgumentException("Fields layout cannot be empty!");
        }

        long slot = REGISTRY_BASE + (index * SLOT_SIZE);

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
        int index = generic - TypeRegister.CUSTOM_STRUCT;
        if (index < 0 || index >= MAX_STRUCTS) {
            throw new IllegalArgumentException("Invalid Struct ID " + generic);
        }
        long slot = REGISTRY_BASE + (index * SLOT_SIZE);
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
        int index = generic - TypeRegister.CUSTOM_STRUCT;
        long slot = REGISTRY_BASE + (index * SLOT_SIZE);
        long offsetsPtr = ForeignMemory.getLong(slot + 16L);
        return ForeignMemory.getInt(offsetsPtr + fieldIndex * 4L);
    }

    private static int getStride(int generic) {
        int index = generic - TypeRegister.CUSTOM_STRUCT;
        if (index < 0 || index >= MAX_STRUCTS) {
            return 0;
        }
        long slot = REGISTRY_BASE + (index * SLOT_SIZE);
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

    // Level 3: Allocate a pointer array (Matrix) of custom structs (FORM_POINTER | generic)
    public static long allocateMatrix(int generic, int length) {
        int stride = getStride(generic);
        if (stride == 0) throw new IllegalArgumentException("Struct ID 0x" + Integer.toHexString(generic).toUpperCase() + " is not defined!");
        if (length <= 0) throw new IllegalArgumentException("Matrix length must be positive!");

        long bufferBytes = (long) length * 8L;
        long block = ForeignMemory.allocateNative(8L + bufferBytes);
        long userPtr = block + 8L;

        // Write header: type (FORM_POINTER | generic), length (length)
        ForeignMemory.putInt(block, TypeRegister.FORM_POINTER | generic);
        ForeignMemory.putInt(block + 4L, length);

        // Zero-initialize pointers
        ForeignMemory.setMemory(userPtr, bufferBytes, (byte) 0);

        return userPtr;
    }

    // get pointer at index in struct pointer array (matrix)
    public static long getPointer(long userPtr, int index) {
        if (userPtr == 0L) throw new NullPointerException("Accessing NULL off-heap struct matrix pointer!");
        int type = ForeignMemory.getInt(userPtr - 8L);
        if (!TypeRegister.isPointer(type)) {
            throw new IllegalArgumentException("Expected pointer array (matrix) form but found 0x" + Integer.toHexString(type).toUpperCase());
        }
        int length = ForeignMemory.getInt(userPtr - 4L);
        if (index < 0 || index >= length) {
            throw new IndexOutOfBoundsException("Matrix index " + index + " out of bounds (length: " + length + ")");
        }
        return ForeignMemory.getLong(userPtr + (long) index * 8L);
    }

    // set pointer at index in struct pointer array (matrix)
    public static void setPointer(long userPtr, int index, long targetPointer) {
        if (userPtr == 0L) throw new NullPointerException("Accessing NULL off-heap struct matrix pointer!");
        int type = ForeignMemory.getInt(userPtr - 8L);
        if (!TypeRegister.isPointer(type)) {
            throw new IllegalArgumentException("Expected pointer array (matrix) form but found 0x" + Integer.toHexString(type).toUpperCase());
        }
        int length = ForeignMemory.getInt(userPtr - 4L);
        if (index < 0 || index >= length) {
            throw new IndexOutOfBoundsException("Matrix index " + index + " out of bounds (length: " + length + ")");
        }
        ForeignMemory.putLong(userPtr + (long) index * 8L, targetPointer);
    }

    // free a struct singleton, array, or pointer array (matrix)
    public static void free(long userPtr) {
        if (userPtr == 0L) return;
        long block = userPtr - 8L;
        int type = ForeignMemory.getInt(block);
        if (type == 0 || (!TypeRegister.isSingleton(type) && !TypeRegister.isArray(type) && !TypeRegister.isPointer(type))) {
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

    public static void setInt(long userPtr, int fieldIndex, int value) {
        int generic = getStructIdFromPointer(userPtr);
        setInt(generic, userPtr, fieldIndex, value);
    }

    public static void setInt(int generic, long userPtr, int fieldIndex, int value) {
        checkFieldType(generic, fieldIndex, TypeRegister.ID_INT);
        int offset = getOffset(generic, fieldIndex);
        ForeignMemory.putInt(userPtr + offset, value);
    }

    public static int getInt(long userPtr, int fieldIndex) {
        int generic = getStructIdFromPointer(userPtr);
        return getInt(generic, userPtr, fieldIndex);
    }

    public static int getInt(int generic, long userPtr, int fieldIndex) {
        checkFieldType(generic, fieldIndex, TypeRegister.ID_INT);
        int offset = getOffset(generic, fieldIndex);
        return ForeignMemory.getInt(userPtr + offset);
    }

    public static void setLong(long userPtr, int fieldIndex, long value) {
        int generic = getStructIdFromPointer(userPtr);
        setLong(generic, userPtr, fieldIndex, value);
    }

    public static void setLong(int generic, long userPtr, int fieldIndex, long value) {
        checkFieldType(generic, fieldIndex, TypeRegister.ID_LONG);
        int offset = getOffset(generic, fieldIndex);
        ForeignMemory.putLong(userPtr + offset, value);
    }

    public static long getLong(long userPtr, int fieldIndex) {
        int generic = getStructIdFromPointer(userPtr);
        return getLong(generic, userPtr, fieldIndex);
    }

    public static long getLong(int generic, long userPtr, int fieldIndex) {
        checkFieldType(generic, fieldIndex, TypeRegister.ID_LONG);
        int offset = getOffset(generic, fieldIndex);
        return ForeignMemory.getLong(userPtr + offset);
    }

    public static void setFloat(long userPtr, int fieldIndex, float value) {
        int generic = getStructIdFromPointer(userPtr);
        setFloat(generic, userPtr, fieldIndex, value);
    }

    public static void setFloat(int generic, long userPtr, int fieldIndex, float value) {
        checkFieldType(generic, fieldIndex, TypeRegister.ID_FLOAT);
        int offset = getOffset(generic, fieldIndex);
        ForeignMemory.putFloat(userPtr + offset, value);
    }

    public static float getFloat(long userPtr, int fieldIndex) {
        int generic = getStructIdFromPointer(userPtr);
        return getFloat(generic, userPtr, fieldIndex);
    }

    public static float getFloat(int generic, long userPtr, int fieldIndex) {
        checkFieldType(generic, fieldIndex, TypeRegister.ID_FLOAT);
        int offset = getOffset(generic, fieldIndex);
        return ForeignMemory.getFloat(userPtr + offset);
    }

    public static void setDouble(long userPtr, int fieldIndex, double value) {
        int generic = getStructIdFromPointer(userPtr);
        setDouble(generic, userPtr, fieldIndex, value);
    }

    public static void setDouble(int generic, long userPtr, int fieldIndex, double value) {
        checkFieldType(generic, fieldIndex, TypeRegister.ID_DOUBLE);
        int offset = getOffset(generic, fieldIndex);
        ForeignMemory.putDouble(userPtr + offset, value);
    }

    public static double getDouble(long userPtr, int fieldIndex) {
        int generic = getStructIdFromPointer(userPtr);
        return getDouble(generic, userPtr, fieldIndex);
    }

    public static double getDouble(int generic, long userPtr, int fieldIndex) {
        checkFieldType(generic, fieldIndex, TypeRegister.ID_DOUBLE);
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

    public static void setInt(long userPtr, int elementIndex, int fieldIndex, int value) {
        int generic = getStructIdFromPointer(userPtr);
        setInt(generic, userPtr, elementIndex, fieldIndex, value);
    }

    public static void setInt(int generic, long userPtr, int elementIndex, int fieldIndex, int value) {
        checkFieldType(generic, fieldIndex, TypeRegister.ID_INT);
        int stride = getStride(generic);
        int offset = getOffset(generic, fieldIndex);
        ForeignMemory.putInt(userPtr + (long) elementIndex * stride + offset, value);
    }

    public static int getInt(long userPtr, int elementIndex, int fieldIndex) {
        int generic = getStructIdFromPointer(userPtr);
        return getInt(generic, userPtr, elementIndex, fieldIndex);
    }

    public static int getInt(int generic, long userPtr, int elementIndex, int fieldIndex) {
        checkFieldType(generic, fieldIndex, TypeRegister.ID_INT);
        int stride = getStride(generic);
        int offset = getOffset(generic, fieldIndex);
        return ForeignMemory.getInt(userPtr + (long) elementIndex * stride + offset);
    }

    public static void setLong(long userPtr, int elementIndex, int fieldIndex, long value) {
        int generic = getStructIdFromPointer(userPtr);
        setLong(generic, userPtr, elementIndex, fieldIndex, value);
    }

    public static void setLong(int generic, long userPtr, int elementIndex, int fieldIndex, long value) {
        checkFieldType(generic, fieldIndex, TypeRegister.ID_LONG);
        int stride = getStride(generic);
        int offset = getOffset(generic, fieldIndex);
        ForeignMemory.putLong(userPtr + (long) elementIndex * stride + offset, value);
    }

    public static long getLong(long userPtr, int elementIndex, int fieldIndex) {
        int generic = getStructIdFromPointer(userPtr);
        return getLong(generic, userPtr, elementIndex, fieldIndex);
    }

    public static long getLong(int generic, long userPtr, int elementIndex, int fieldIndex) {
        checkFieldType(generic, fieldIndex, TypeRegister.ID_LONG);
        int stride = getStride(generic);
        int offset = getOffset(generic, fieldIndex);
        return ForeignMemory.getLong(userPtr + (long) elementIndex * stride + offset);
    }

    public static void setFloat(long userPtr, int elementIndex, int fieldIndex, float value) {
        int generic = getStructIdFromPointer(userPtr);
        setFloat(generic, userPtr, elementIndex, fieldIndex, value);
    }

    public static void setFloat(int generic, long userPtr, int elementIndex, int fieldIndex, float value) {
        checkFieldType(generic, fieldIndex, TypeRegister.ID_FLOAT);
        int stride = getStride(generic);
        int offset = getOffset(generic, fieldIndex);
        ForeignMemory.putFloat(userPtr + (long) elementIndex * stride + offset, value);
    }

    public static float getFloat(long userPtr, int elementIndex, int fieldIndex) {
        int generic = getStructIdFromPointer(userPtr);
        return getFloat(generic, userPtr, elementIndex, fieldIndex);
    }

    public static float getFloat(int generic, long userPtr, int elementIndex, int fieldIndex) {
        checkFieldType(generic, fieldIndex, TypeRegister.ID_FLOAT);
        int stride = getStride(generic);
        int offset = getOffset(generic, fieldIndex);
        return ForeignMemory.getFloat(userPtr + (long) elementIndex * stride + offset);
    }

    public static void setDouble(long userPtr, int elementIndex, int fieldIndex, double value) {
        int generic = getStructIdFromPointer(userPtr);
        setDouble(generic, userPtr, elementIndex, fieldIndex, value);
    }

    public static void setDouble(int generic, long userPtr, int elementIndex, int fieldIndex, double value) {
        checkFieldType(generic, fieldIndex, TypeRegister.ID_DOUBLE);
        int stride = getStride(generic);
        int offset = getOffset(generic, fieldIndex);
        ForeignMemory.putDouble(userPtr + (long) elementIndex * stride + offset, value);
    }

    public static double getDouble(long userPtr, int elementIndex, int fieldIndex) {
        int generic = getStructIdFromPointer(userPtr);
        return getDouble(generic, userPtr, elementIndex, fieldIndex);
    }

    public static double getDouble(int generic, long userPtr, int elementIndex, int fieldIndex) {
        checkFieldType(generic, fieldIndex, TypeRegister.ID_DOUBLE);
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

    // =========================================================================
    // VOLATILE & ATOMIC MUTATORS & ACCESSORS (SINGLETON / LEVEL 1)
    // =========================================================================

    public static void setIntVolatile(long userPtr, int fieldIndex, int value) {
        int generic = getStructIdFromPointer(userPtr);
        setIntVolatile(generic, userPtr, fieldIndex, value);
    }

    public static void setIntVolatile(int generic, long userPtr, int fieldIndex, int value) {
        checkFieldType(generic, fieldIndex, TypeRegister.ID_INT);
        int offset = getOffset(generic, fieldIndex);
        ForeignMemory.putIntVolatile(userPtr + offset, value);
    }

    public static int getIntVolatile(long userPtr, int fieldIndex) {
        int generic = getStructIdFromPointer(userPtr);
        return getIntVolatile(generic, userPtr, fieldIndex);
    }

    public static int getIntVolatile(int generic, long userPtr, int fieldIndex) {
        checkFieldType(generic, fieldIndex, TypeRegister.ID_INT);
        int offset = getOffset(generic, fieldIndex);
        return ForeignMemory.getIntVolatile(userPtr + offset);
    }

    public static boolean compareAndSetInt(long userPtr, int fieldIndex, int expected, int value) {
        int generic = getStructIdFromPointer(userPtr);
        return compareAndSetInt(generic, userPtr, fieldIndex, expected, value);
    }

    public static boolean compareAndSetInt(int generic, long userPtr, int fieldIndex, int expected, int value) {
        checkFieldType(generic, fieldIndex, TypeRegister.ID_INT);
        int offset = getOffset(generic, fieldIndex);
        return ForeignMemory.compareAndSetInt(userPtr + offset, expected, value);
    }

    public static void setLongVolatile(long userPtr, int fieldIndex, long value) {
        int generic = getStructIdFromPointer(userPtr);
        setLongVolatile(generic, userPtr, fieldIndex, value);
    }

    public static void setLongVolatile(int generic, long userPtr, int fieldIndex, long value) {
        checkFieldType(generic, fieldIndex, TypeRegister.ID_LONG);
        int offset = getOffset(generic, fieldIndex);
        ForeignMemory.putLongVolatile(userPtr + offset, value);
    }

    public static long getLongVolatile(long userPtr, int fieldIndex) {
        int generic = getStructIdFromPointer(userPtr);
        return getLongVolatile(generic, userPtr, fieldIndex);
    }

    public static long getLongVolatile(int generic, long userPtr, int fieldIndex) {
        checkFieldType(generic, fieldIndex, TypeRegister.ID_LONG);
        int offset = getOffset(generic, fieldIndex);
        return ForeignMemory.getLongVolatile(userPtr + offset);
    }

    public static boolean compareAndSetLong(long userPtr, int fieldIndex, long expected, long value) {
        int generic = getStructIdFromPointer(userPtr);
        return compareAndSetLong(generic, userPtr, fieldIndex, expected, value);
    }

    public static boolean compareAndSetLong(int generic, long userPtr, int fieldIndex, long expected, long value) {
        checkFieldType(generic, fieldIndex, TypeRegister.ID_LONG);
        int offset = getOffset(generic, fieldIndex);
        return ForeignMemory.compareAndSetLong(userPtr + offset, expected, value);
    }

    public static void setFloatVolatile(long userPtr, int fieldIndex, float value) {
        int generic = getStructIdFromPointer(userPtr);
        setFloatVolatile(generic, userPtr, fieldIndex, value);
    }

    public static void setFloatVolatile(int generic, long userPtr, int fieldIndex, float value) {
        checkFieldType(generic, fieldIndex, TypeRegister.ID_FLOAT);
        int offset = getOffset(generic, fieldIndex);
        ForeignMemory.putFloatVolatile(userPtr + offset, value);
    }

    public static float getFloatVolatile(long userPtr, int fieldIndex) {
        int generic = getStructIdFromPointer(userPtr);
        return getFloatVolatile(generic, userPtr, fieldIndex);
    }

    public static float getFloatVolatile(int generic, long userPtr, int fieldIndex) {
        checkFieldType(generic, fieldIndex, TypeRegister.ID_FLOAT);
        int offset = getOffset(generic, fieldIndex);
        return ForeignMemory.getFloatVolatile(userPtr + offset);
    }

    public static boolean compareAndSetFloat(long userPtr, int fieldIndex, float expected, float value) {
        int generic = getStructIdFromPointer(userPtr);
        return compareAndSetFloat(generic, userPtr, fieldIndex, expected, value);
    }

    public static boolean compareAndSetFloat(int generic, long userPtr, int fieldIndex, float expected, float value) {
        checkFieldType(generic, fieldIndex, TypeRegister.ID_FLOAT);
        int offset = getOffset(generic, fieldIndex);
        return ForeignMemory.compareAndSetFloat(userPtr + offset, expected, value);
    }

    public static void setDoubleVolatile(long userPtr, int fieldIndex, double value) {
        int generic = getStructIdFromPointer(userPtr);
        setDoubleVolatile(generic, userPtr, fieldIndex, value);
    }

    public static void setDoubleVolatile(int generic, long userPtr, int fieldIndex, double value) {
        checkFieldType(generic, fieldIndex, TypeRegister.ID_DOUBLE);
        int offset = getOffset(generic, fieldIndex);
        ForeignMemory.putDoubleVolatile(userPtr + offset, value);
    }

    public static double getDoubleVolatile(long userPtr, int fieldIndex) {
        int generic = getStructIdFromPointer(userPtr);
        return getDoubleVolatile(generic, userPtr, fieldIndex);
    }

    public static double getDoubleVolatile(int generic, long userPtr, int fieldIndex) {
        checkFieldType(generic, fieldIndex, TypeRegister.ID_DOUBLE);
        int offset = getOffset(generic, fieldIndex);
        return ForeignMemory.getDoubleVolatile(userPtr + offset);
    }

    public static boolean compareAndSetDouble(long userPtr, int fieldIndex, double expected, double value) {
        int generic = getStructIdFromPointer(userPtr);
        return compareAndSetDouble(generic, userPtr, fieldIndex, expected, value);
    }

    public static boolean compareAndSetDouble(int generic, long userPtr, int fieldIndex, double expected, double value) {
        checkFieldType(generic, fieldIndex, TypeRegister.ID_DOUBLE);
        int offset = getOffset(generic, fieldIndex);
        return ForeignMemory.compareAndSetDouble(userPtr + offset, expected, value);
    }

    public static void setByteVolatile(long userPtr, int fieldIndex, byte value) {
        int generic = getStructIdFromPointer(userPtr);
        setByteVolatile(generic, userPtr, fieldIndex, value);
    }

    public static void setByteVolatile(int generic, long userPtr, int fieldIndex, byte value) {
        checkFieldType(generic, fieldIndex, TypeRegister.ID_BYTE);
        int offset = getOffset(generic, fieldIndex);
        ForeignMemory.putByteVolatile(userPtr + offset, value);
    }

    public static byte getByteVolatile(long userPtr, int fieldIndex) {
        int generic = getStructIdFromPointer(userPtr);
        return getByteVolatile(generic, userPtr, fieldIndex);
    }

    public static byte getByteVolatile(int generic, long userPtr, int fieldIndex) {
        checkFieldType(generic, fieldIndex, TypeRegister.ID_BYTE);
        int offset = getOffset(generic, fieldIndex);
        return ForeignMemory.getByteVolatile(userPtr + offset);
    }

    public static boolean compareAndSetByte(long userPtr, int fieldIndex, byte expected, byte value) {
        int generic = getStructIdFromPointer(userPtr);
        return compareAndSetByte(generic, userPtr, fieldIndex, expected, value);
    }

    public static boolean compareAndSetByte(int generic, long userPtr, int fieldIndex, byte expected, byte value) {
        checkFieldType(generic, fieldIndex, TypeRegister.ID_BYTE);
        int offset = getOffset(generic, fieldIndex);
        return ForeignMemory.compareAndSetByte(userPtr + offset, expected, value);
    }

    public static void setShortVolatile(long userPtr, int fieldIndex, short value) {
        int generic = getStructIdFromPointer(userPtr);
        setShortVolatile(generic, userPtr, fieldIndex, value);
    }

    public static void setShortVolatile(int generic, long userPtr, int fieldIndex, short value) {
        checkFieldType(generic, fieldIndex, TypeRegister.ID_SHORT);
        int offset = getOffset(generic, fieldIndex);
        ForeignMemory.putShortVolatile(userPtr + offset, value);
    }

    public static short getShortVolatile(long userPtr, int fieldIndex) {
        int generic = getStructIdFromPointer(userPtr);
        return getShortVolatile(generic, userPtr, fieldIndex);
    }

    public static short getShortVolatile(int generic, long userPtr, int fieldIndex) {
        checkFieldType(generic, fieldIndex, TypeRegister.ID_SHORT);
        int offset = getOffset(generic, fieldIndex);
        return ForeignMemory.getShortVolatile(userPtr + offset);
    }

    public static boolean compareAndSetShort(long userPtr, int fieldIndex, short expected, short value) {
        int generic = getStructIdFromPointer(userPtr);
        return compareAndSetShort(generic, userPtr, fieldIndex, expected, value);
    }

    public static boolean compareAndSetShort(int generic, long userPtr, int fieldIndex, short expected, short value) {
        checkFieldType(generic, fieldIndex, TypeRegister.ID_SHORT);
        int offset = getOffset(generic, fieldIndex);
        return ForeignMemory.compareAndSetShort(userPtr + offset, expected, value);
    }

    // =========================================================================
    // VOLATILE & ATOMIC ARRAY FIELD MUTATORS & ACCESSORS (ARRAY / LEVEL 2)
    // =========================================================================

    public static void setIntVolatile(long userPtr, int elementIndex, int fieldIndex, int value) {
        int generic = getStructIdFromPointer(userPtr);
        setIntVolatile(generic, userPtr, elementIndex, fieldIndex, value);
    }

    public static void setIntVolatile(int generic, long userPtr, int elementIndex, int fieldIndex, int value) {
        checkFieldType(generic, fieldIndex, TypeRegister.ID_INT);
        int stride = getStride(generic);
        int offset = getOffset(generic, fieldIndex);
        ForeignMemory.putIntVolatile(userPtr + (long) elementIndex * stride + offset, value);
    }

    public static int getIntVolatile(long userPtr, int elementIndex, int fieldIndex) {
        int generic = getStructIdFromPointer(userPtr);
        return getIntVolatile(generic, userPtr, elementIndex, fieldIndex);
    }

    public static int getIntVolatile(int generic, long userPtr, int elementIndex, int fieldIndex) {
        checkFieldType(generic, fieldIndex, TypeRegister.ID_INT);
        int stride = getStride(generic);
        int offset = getOffset(generic, fieldIndex);
        return ForeignMemory.getIntVolatile(userPtr + (long) elementIndex * stride + offset);
    }

    public static boolean compareAndSetInt(long userPtr, int elementIndex, int fieldIndex, int expected, int value) {
        int generic = getStructIdFromPointer(userPtr);
        return compareAndSetInt(generic, userPtr, elementIndex, fieldIndex, expected, value);
    }

    public static boolean compareAndSetInt(int generic, long userPtr, int elementIndex, int fieldIndex, int expected, int value) {
        checkFieldType(generic, fieldIndex, TypeRegister.ID_INT);
        int stride = getStride(generic);
        int offset = getOffset(generic, fieldIndex);
        return ForeignMemory.compareAndSetInt(userPtr + (long) elementIndex * stride + offset, expected, value);
    }

    public static void setLongVolatile(long userPtr, int elementIndex, int fieldIndex, long value) {
        int generic = getStructIdFromPointer(userPtr);
        setLongVolatile(generic, userPtr, elementIndex, fieldIndex, value);
    }

    public static void setLongVolatile(int generic, long userPtr, int elementIndex, int fieldIndex, long value) {
        checkFieldType(generic, fieldIndex, TypeRegister.ID_LONG);
        int stride = getStride(generic);
        int offset = getOffset(generic, fieldIndex);
        ForeignMemory.putLongVolatile(userPtr + (long) elementIndex * stride + offset, value);
    }

    public static long getLongVolatile(long userPtr, int elementIndex, int fieldIndex) {
        int generic = getStructIdFromPointer(userPtr);
        return getLongVolatile(generic, userPtr, elementIndex, fieldIndex);
    }

    public static long getLongVolatile(int generic, long userPtr, int elementIndex, int fieldIndex) {
        checkFieldType(generic, fieldIndex, TypeRegister.ID_LONG);
        int stride = getStride(generic);
        int offset = getOffset(generic, fieldIndex);
        return ForeignMemory.getLongVolatile(userPtr + (long) elementIndex * stride + offset);
    }

    public static boolean compareAndSetLong(long userPtr, int elementIndex, int fieldIndex, long expected, long value) {
        int generic = getStructIdFromPointer(userPtr);
        return compareAndSetLong(generic, userPtr, elementIndex, fieldIndex, expected, value);
    }

    public static boolean compareAndSetLong(int generic, long userPtr, int elementIndex, int fieldIndex, long expected, long value) {
        checkFieldType(generic, fieldIndex, TypeRegister.ID_LONG);
        int stride = getStride(generic);
        int offset = getOffset(generic, fieldIndex);
        return ForeignMemory.compareAndSetLong(userPtr + (long) elementIndex * stride + offset, expected, value);
    }

    public static void setFloatVolatile(long userPtr, int elementIndex, int fieldIndex, float value) {
        int generic = getStructIdFromPointer(userPtr);
        setFloatVolatile(generic, userPtr, elementIndex, fieldIndex, value);
    }

    public static void setFloatVolatile(int generic, long userPtr, int elementIndex, int fieldIndex, float value) {
        checkFieldType(generic, fieldIndex, TypeRegister.ID_FLOAT);
        int stride = getStride(generic);
        int offset = getOffset(generic, fieldIndex);
        ForeignMemory.putFloatVolatile(userPtr + (long) elementIndex * stride + offset, value);
    }

    public static float getFloatVolatile(long userPtr, int elementIndex, int fieldIndex) {
        int generic = getStructIdFromPointer(userPtr);
        return getFloatVolatile(generic, userPtr, elementIndex, fieldIndex);
    }

    public static float getFloatVolatile(int generic, long userPtr, int elementIndex, int fieldIndex) {
        checkFieldType(generic, fieldIndex, TypeRegister.ID_FLOAT);
        int stride = getStride(generic);
        int offset = getOffset(generic, fieldIndex);
        return ForeignMemory.getFloatVolatile(userPtr + (long) elementIndex * stride + offset);
    }

    public static boolean compareAndSetFloat(long userPtr, int elementIndex, int fieldIndex, float expected, float value) {
        int generic = getStructIdFromPointer(userPtr);
        return compareAndSetFloat(generic, userPtr, elementIndex, fieldIndex, expected, value);
    }

    public static boolean compareAndSetFloat(int generic, long userPtr, int elementIndex, int fieldIndex, float expected, float value) {
        checkFieldType(generic, fieldIndex, TypeRegister.ID_FLOAT);
        int stride = getStride(generic);
        int offset = getOffset(generic, fieldIndex);
        return ForeignMemory.compareAndSetFloat(userPtr + (long) elementIndex * stride + offset, expected, value);
    }

    public static void setDoubleVolatile(long userPtr, int elementIndex, int fieldIndex, double value) {
        int generic = getStructIdFromPointer(userPtr);
        setDoubleVolatile(generic, userPtr, elementIndex, fieldIndex, value);
    }

    public static void setDoubleVolatile(int generic, long userPtr, int elementIndex, int fieldIndex, double value) {
        checkFieldType(generic, fieldIndex, TypeRegister.ID_DOUBLE);
        int stride = getStride(generic);
        int offset = getOffset(generic, fieldIndex);
        ForeignMemory.putDoubleVolatile(userPtr + (long) elementIndex * stride + offset, value);
    }

    public static double getDoubleVolatile(long userPtr, int elementIndex, int fieldIndex) {
        int generic = getStructIdFromPointer(userPtr);
        return getDoubleVolatile(generic, userPtr, elementIndex, fieldIndex);
    }

    public static double getDoubleVolatile(int generic, long userPtr, int elementIndex, int fieldIndex) {
        checkFieldType(generic, fieldIndex, TypeRegister.ID_DOUBLE);
        int stride = getStride(generic);
        int offset = getOffset(generic, fieldIndex);
        return ForeignMemory.getDoubleVolatile(userPtr + (long) elementIndex * stride + offset);
    }

    public static boolean compareAndSetDouble(long userPtr, int elementIndex, int fieldIndex, double expected, double value) {
        int generic = getStructIdFromPointer(userPtr);
        return compareAndSetDouble(generic, userPtr, elementIndex, fieldIndex, expected, value);
    }

    public static boolean compareAndSetDouble(int generic, long userPtr, int elementIndex, int fieldIndex, double expected, double value) {
        checkFieldType(generic, fieldIndex, TypeRegister.ID_DOUBLE);
        int stride = getStride(generic);
        int offset = getOffset(generic, fieldIndex);
        return ForeignMemory.compareAndSetDouble(userPtr + (long) elementIndex * stride + offset, expected, value);
    }

    public static void setByteVolatile(long userPtr, int elementIndex, int fieldIndex, byte value) {
        int generic = getStructIdFromPointer(userPtr);
        setByteVolatile(generic, userPtr, elementIndex, fieldIndex, value);
    }

    public static void setByteVolatile(int generic, long userPtr, int elementIndex, int fieldIndex, byte value) {
        checkFieldType(generic, fieldIndex, TypeRegister.ID_BYTE);
        int stride = getStride(generic);
        int offset = getOffset(generic, fieldIndex);
        ForeignMemory.putByteVolatile(userPtr + (long) elementIndex * stride + offset, value);
    }

    public static byte getByteVolatile(long userPtr, int elementIndex, int fieldIndex) {
        int generic = getStructIdFromPointer(userPtr);
        return getByteVolatile(generic, userPtr, elementIndex, fieldIndex);
    }

    public static byte getByteVolatile(int generic, long userPtr, int elementIndex, int fieldIndex) {
        checkFieldType(generic, fieldIndex, TypeRegister.ID_BYTE);
        int stride = getStride(generic);
        int offset = getOffset(generic, fieldIndex);
        return ForeignMemory.getByteVolatile(userPtr + (long) elementIndex * stride + offset);
    }

    public static boolean compareAndSetByte(long userPtr, int elementIndex, int fieldIndex, byte expected, byte value) {
        int generic = getStructIdFromPointer(userPtr);
        return compareAndSetByte(generic, userPtr, elementIndex, fieldIndex, expected, value);
    }

    public static boolean compareAndSetByte(int generic, long userPtr, int elementIndex, int fieldIndex, byte expected, byte value) {
        checkFieldType(generic, fieldIndex, TypeRegister.ID_BYTE);
        int stride = getStride(generic);
        int offset = getOffset(generic, fieldIndex);
        return ForeignMemory.compareAndSetByte(userPtr + (long) elementIndex * stride + offset, expected, value);
    }

    public static void setShortVolatile(long userPtr, int elementIndex, int fieldIndex, short value) {
        int generic = getStructIdFromPointer(userPtr);
        setShortVolatile(generic, userPtr, elementIndex, fieldIndex, value);
    }

    public static void setShortVolatile(int generic, long userPtr, int elementIndex, int fieldIndex, short value) {
        checkFieldType(generic, fieldIndex, TypeRegister.ID_SHORT);
        int stride = getStride(generic);
        int offset = getOffset(generic, fieldIndex);
        ForeignMemory.putShortVolatile(userPtr + (long) elementIndex * stride + offset, value);
    }

    public static short getShortVolatile(long userPtr, int elementIndex, int fieldIndex) {
        int generic = getStructIdFromPointer(userPtr);
        return getShortVolatile(generic, userPtr, elementIndex, fieldIndex);
    }

    public static short getShortVolatile(int generic, long userPtr, int elementIndex, int fieldIndex) {
        checkFieldType(generic, fieldIndex, TypeRegister.ID_SHORT);
        int stride = getStride(generic);
        int offset = getOffset(generic, fieldIndex);
        return ForeignMemory.getShortVolatile(userPtr + (long) elementIndex * stride + offset);
    }

    public static boolean compareAndSetShort(long userPtr, int elementIndex, int fieldIndex, short expected, short value) {
        int generic = getStructIdFromPointer(userPtr);
        return compareAndSetShort(generic, userPtr, elementIndex, fieldIndex, expected, value);
    }

    public static boolean compareAndSetShort(int generic, long userPtr, int elementIndex, int fieldIndex, short expected, short value) {
        checkFieldType(generic, fieldIndex, TypeRegister.ID_SHORT);
        int stride = getStride(generic);
        int offset = getOffset(generic, fieldIndex);
        return ForeignMemory.compareAndSetShort(userPtr + (long) elementIndex * stride + offset, expected, value);
    }

    public static int stride(int generic) {
        return getStride(generic);
    }

    public static int classId() {
        return CLASS_ID;
    }

    // =========================================================================
    // UNSAFE MUTATORS & ACCESSORS (SINGLETON / LEVEL 1)
    // =========================================================================


    public static void unsafeSetInt(long userPtr, int fieldIndex, int value) {
        int generic = getStructIdFromPointer(userPtr);
        unsafeSetInt(generic, userPtr, fieldIndex, value);
    }

    public static void unsafeSetInt(int generic, long userPtr, int fieldIndex, int value) {
        int offset = getOffset(generic, fieldIndex);
        ForeignMemory.putInt(userPtr + offset, value);
    }

    public static int unsafeGetInt(long userPtr, int fieldIndex) {
        int generic = getStructIdFromPointer(userPtr);
        return unsafeGetInt(generic, userPtr, fieldIndex);
    }

    public static int unsafeGetInt(int generic, long userPtr, int fieldIndex) {
        int offset = getOffset(generic, fieldIndex);
        return ForeignMemory.getInt(userPtr + offset);
    }

    public static void unsafeSetLong(long userPtr, int fieldIndex, long value) {
        int generic = getStructIdFromPointer(userPtr);
        unsafeSetLong(generic, userPtr, fieldIndex, value);
    }

    public static void unsafeSetLong(int generic, long userPtr, int fieldIndex, long value) {
        int offset = getOffset(generic, fieldIndex);
        ForeignMemory.putLong(userPtr + offset, value);
    }

    public static long unsafeGetLong(long userPtr, int fieldIndex) {
        int generic = getStructIdFromPointer(userPtr);
        return unsafeGetLong(generic, userPtr, fieldIndex);
    }

    public static long unsafeGetLong(int generic, long userPtr, int fieldIndex) {
        int offset = getOffset(generic, fieldIndex);
        return ForeignMemory.getLong(userPtr + offset);
    }

    public static void unsafeSetFloat(long userPtr, int fieldIndex, float value) {
        int generic = getStructIdFromPointer(userPtr);
        unsafeSetFloat(generic, userPtr, fieldIndex, value);
    }

    public static void unsafeSetFloat(int generic, long userPtr, int fieldIndex, float value) {
        int offset = getOffset(generic, fieldIndex);
        ForeignMemory.putFloat(userPtr + offset, value);
    }

    public static float unsafeGetFloat(long userPtr, int fieldIndex) {
        int generic = getStructIdFromPointer(userPtr);
        return unsafeGetFloat(generic, userPtr, fieldIndex);
    }

    public static float unsafeGetFloat(int generic, long userPtr, int fieldIndex) {
        int offset = getOffset(generic, fieldIndex);
        return ForeignMemory.getFloat(userPtr + offset);
    }

    public static void unsafeSetDouble(long userPtr, int fieldIndex, double value) {
        int generic = getStructIdFromPointer(userPtr);
        unsafeSetDouble(generic, userPtr, fieldIndex, value);
    }

    public static void unsafeSetDouble(int generic, long userPtr, int fieldIndex, double value) {
        int offset = getOffset(generic, fieldIndex);
        ForeignMemory.putDouble(userPtr + offset, value);
    }

    public static double unsafeGetDouble(long userPtr, int fieldIndex) {
        int generic = getStructIdFromPointer(userPtr);
        return unsafeGetDouble(generic, userPtr, fieldIndex);
    }

    public static double unsafeGetDouble(int generic, long userPtr, int fieldIndex) {
        int offset = getOffset(generic, fieldIndex);
        return ForeignMemory.getDouble(userPtr + offset);
    }

    public static void unsafeSetByte(long userPtr, int fieldIndex, byte value) {
        int generic = getStructIdFromPointer(userPtr);
        unsafeSetByte(generic, userPtr, fieldIndex, value);
    }

    public static void unsafeSetByte(int generic, long userPtr, int fieldIndex, byte value) {
        int offset = getOffset(generic, fieldIndex);
        ForeignMemory.putByte(userPtr + offset, value);
    }

    public static byte unsafeGetByte(long userPtr, int fieldIndex) {
        int generic = getStructIdFromPointer(userPtr);
        return unsafeGetByte(generic, userPtr, fieldIndex);
    }

    public static byte unsafeGetByte(int generic, long userPtr, int fieldIndex) {
        int offset = getOffset(generic, fieldIndex);
        return ForeignMemory.getByte(userPtr + offset);
    }

    public static void unsafeSetShort(long userPtr, int fieldIndex, short value) {
        int generic = getStructIdFromPointer(userPtr);
        unsafeSetShort(generic, userPtr, fieldIndex, value);
    }

    public static void unsafeSetShort(int generic, long userPtr, int fieldIndex, short value) {
        int offset = getOffset(generic, fieldIndex);
        ForeignMemory.putShort(userPtr + offset, value);
    }

    public static short unsafeGetShort(long userPtr, int fieldIndex) {
        int generic = getStructIdFromPointer(userPtr);
        return unsafeGetShort(generic, userPtr, fieldIndex);
    }

    public static short unsafeGetShort(int generic, long userPtr, int fieldIndex) {
        int offset = getOffset(generic, fieldIndex);
        return ForeignMemory.getShort(userPtr + offset);
    }

    // =========================================================================
    // UNSAFE ARRAY FIELD MUTATORS & ACCESSORS (ARRAY / LEVEL 2)
    // =========================================================================


    public static void unsafeSetInt(long userPtr, int elementIndex, int fieldIndex, int value) {
        int generic = getStructIdFromPointer(userPtr);
        unsafeSetInt(generic, userPtr, elementIndex, fieldIndex, value);
    }

    public static void unsafeSetInt(int generic, long userPtr, int elementIndex, int fieldIndex, int value) {
        int stride = getStride(generic);
        int offset = getOffset(generic, fieldIndex);
        ForeignMemory.putInt(userPtr + (long) elementIndex * stride + offset, value);
    }

    public static int unsafeGetInt(long userPtr, int elementIndex, int fieldIndex) {
        int generic = getStructIdFromPointer(userPtr);
        return unsafeGetInt(generic, userPtr, elementIndex, fieldIndex);
    }

    public static int unsafeGetInt(int generic, long userPtr, int elementIndex, int fieldIndex) {
        int stride = getStride(generic);
        int offset = getOffset(generic, fieldIndex);
        return ForeignMemory.getInt(userPtr + (long) elementIndex * stride + offset);
    }

    public static void unsafeSetLong(long userPtr, int elementIndex, int fieldIndex, long value) {
        int generic = getStructIdFromPointer(userPtr);
        unsafeSetLong(generic, userPtr, elementIndex, fieldIndex, value);
    }

    public static void unsafeSetLong(int generic, long userPtr, int elementIndex, int fieldIndex, long value) {
        int stride = getStride(generic);
        int offset = getOffset(generic, fieldIndex);
        ForeignMemory.putLong(userPtr + (long) elementIndex * stride + offset, value);
    }

    public static long unsafeGetLong(long userPtr, int elementIndex, int fieldIndex) {
        int generic = getStructIdFromPointer(userPtr);
        return unsafeGetLong(generic, userPtr, elementIndex, fieldIndex);
    }

    public static long unsafeGetLong(int generic, long userPtr, int elementIndex, int fieldIndex) {
        int stride = getStride(generic);
        int offset = getOffset(generic, fieldIndex);
        return ForeignMemory.getLong(userPtr + (long) elementIndex * stride + offset);
    }

    public static void unsafeSetFloat(long userPtr, int elementIndex, int fieldIndex, float value) {
        int generic = getStructIdFromPointer(userPtr);
        unsafeSetFloat(generic, userPtr, elementIndex, fieldIndex, value);
    }

    public static void unsafeSetFloat(int generic, long userPtr, int elementIndex, int fieldIndex, float value) {
        int stride = getStride(generic);
        int offset = getOffset(generic, fieldIndex);
        ForeignMemory.putFloat(userPtr + (long) elementIndex * stride + offset, value);
    }

    public static float unsafeGetFloat(long userPtr, int elementIndex, int fieldIndex) {
        int generic = getStructIdFromPointer(userPtr);
        return unsafeGetFloat(generic, userPtr, elementIndex, fieldIndex);
    }

    public static float unsafeGetFloat(int generic, long userPtr, int elementIndex, int fieldIndex) {
        int stride = getStride(generic);
        int offset = getOffset(generic, fieldIndex);
        return ForeignMemory.getFloat(userPtr + (long) elementIndex * stride + offset);
    }

    public static void unsafeSetDouble(long userPtr, int elementIndex, int fieldIndex, double value) {
        int generic = getStructIdFromPointer(userPtr);
        unsafeSetDouble(generic, userPtr, elementIndex, fieldIndex, value);
    }

    public static void unsafeSetDouble(int generic, long userPtr, int elementIndex, int fieldIndex, double value) {
        int stride = getStride(generic);
        int offset = getOffset(generic, fieldIndex);
        ForeignMemory.putDouble(userPtr + (long) elementIndex * stride + offset, value);
    }

    public static double unsafeGetDouble(long userPtr, int elementIndex, int fieldIndex) {
        int generic = getStructIdFromPointer(userPtr);
        return unsafeGetDouble(generic, userPtr, elementIndex, fieldIndex);
    }

    public static double unsafeGetDouble(int generic, long userPtr, int elementIndex, int fieldIndex) {
        int stride = getStride(generic);
        int offset = getOffset(generic, fieldIndex);
        return ForeignMemory.getDouble(userPtr + (long) elementIndex * stride + offset);
    }

    public static void unsafeSetByte(long userPtr, int elementIndex, int fieldIndex, byte value) {
        int generic = getStructIdFromPointer(userPtr);
        unsafeSetByte(generic, userPtr, elementIndex, fieldIndex, value);
    }

    public static void unsafeSetByte(int generic, long userPtr, int elementIndex, int fieldIndex, byte value) {
        int stride = getStride(generic);
        int offset = getOffset(generic, fieldIndex);
        ForeignMemory.putByte(userPtr + (long) elementIndex * stride + offset, value);
    }

    public static byte unsafeGetByte(long userPtr, int elementIndex, int fieldIndex) {
        int generic = getStructIdFromPointer(userPtr);
        return unsafeGetByte(generic, userPtr, elementIndex, fieldIndex);
    }

    public static byte unsafeGetByte(int generic, long userPtr, int elementIndex, int fieldIndex) {
        int stride = getStride(generic);
        int offset = getOffset(generic, fieldIndex);
        return ForeignMemory.getByte(userPtr + (long) elementIndex * stride + offset);
    }

    public static void unsafeSetShort(long userPtr, int elementIndex, int fieldIndex, short value) {
        int generic = getStructIdFromPointer(userPtr);
        unsafeSetShort(generic, userPtr, elementIndex, fieldIndex, value);
    }

    public static void unsafeSetShort(int generic, long userPtr, int elementIndex, int fieldIndex, short value) {
        int stride = getStride(generic);
        int offset = getOffset(generic, fieldIndex);
        ForeignMemory.putShort(userPtr + (long) elementIndex * stride + offset, value);
    }

    public static short unsafeGetShort(long userPtr, int elementIndex, int fieldIndex) {
        int generic = getStructIdFromPointer(userPtr);
        return unsafeGetShort(generic, userPtr, elementIndex, fieldIndex);
    }

    public static short unsafeGetShort(int generic, long userPtr, int elementIndex, int fieldIndex) {
        int stride = getStride(generic);
        int offset = getOffset(generic, fieldIndex);
        return ForeignMemory.getShort(userPtr + (long) elementIndex * stride + offset);
    }

    // =========================================================================
    // UNSAFE VOLATILE MUTATORS & ACCESSORS (SINGLETON / LEVEL 1)
    // =========================================================================


    public static void unsafeVolatileSetInt(long userPtr, int fieldIndex, int value) {
        int generic = getStructIdFromPointer(userPtr);
        unsafeVolatileSetInt(generic, userPtr, fieldIndex, value);
    }

    public static void unsafeVolatileSetInt(int generic, long userPtr, int fieldIndex, int value) {
        int offset = getOffset(generic, fieldIndex);
        ForeignMemory.putIntVolatile(userPtr + offset, value);
    }

    public static int unsafeVolatileGetInt(long userPtr, int fieldIndex) {
        int generic = getStructIdFromPointer(userPtr);
        return unsafeVolatileGetInt(generic, userPtr, fieldIndex);
    }

    public static int unsafeVolatileGetInt(int generic, long userPtr, int fieldIndex) {
        int offset = getOffset(generic, fieldIndex);
        return ForeignMemory.getIntVolatile(userPtr + offset);
    }

    public static void unsafeVolatileSetLong(long userPtr, int fieldIndex, long value) {
        int generic = getStructIdFromPointer(userPtr);
        unsafeVolatileSetLong(generic, userPtr, fieldIndex, value);
    }

    public static void unsafeVolatileSetLong(int generic, long userPtr, int fieldIndex, long value) {
        int offset = getOffset(generic, fieldIndex);
        ForeignMemory.putLongVolatile(userPtr + offset, value);
    }

    public static long unsafeVolatileGetLong(long userPtr, int fieldIndex) {
        int generic = getStructIdFromPointer(userPtr);
        return unsafeVolatileGetLong(generic, userPtr, fieldIndex);
    }

    public static long unsafeVolatileGetLong(int generic, long userPtr, int fieldIndex) {
        int offset = getOffset(generic, fieldIndex);
        return ForeignMemory.getLongVolatile(userPtr + offset);
    }

    public static void unsafeVolatileSetFloat(long userPtr, int fieldIndex, float value) {
        int generic = getStructIdFromPointer(userPtr);
        unsafeVolatileSetFloat(generic, userPtr, fieldIndex, value);
    }

    public static void unsafeVolatileSetFloat(int generic, long userPtr, int fieldIndex, float value) {
        int offset = getOffset(generic, fieldIndex);
        ForeignMemory.putFloatVolatile(userPtr + offset, value);
    }

    public static float unsafeVolatileGetFloat(long userPtr, int fieldIndex) {
        int generic = getStructIdFromPointer(userPtr);
        return unsafeVolatileGetFloat(generic, userPtr, fieldIndex);
    }

    public static float unsafeVolatileGetFloat(int generic, long userPtr, int fieldIndex) {
        int offset = getOffset(generic, fieldIndex);
        return ForeignMemory.getFloatVolatile(userPtr + offset);
    }

    public static void unsafeVolatileSetDouble(long userPtr, int fieldIndex, double value) {
        int generic = getStructIdFromPointer(userPtr);
        unsafeVolatileSetDouble(generic, userPtr, fieldIndex, value);
    }

    public static void unsafeVolatileSetDouble(int generic, long userPtr, int fieldIndex, double value) {
        int offset = getOffset(generic, fieldIndex);
        ForeignMemory.putDoubleVolatile(userPtr + offset, value);
    }

    public static double unsafeVolatileGetDouble(long userPtr, int fieldIndex) {
        int generic = getStructIdFromPointer(userPtr);
        return unsafeVolatileGetDouble(generic, userPtr, fieldIndex);
    }

    public static double unsafeVolatileGetDouble(int generic, long userPtr, int fieldIndex) {
        int offset = getOffset(generic, fieldIndex);
        return ForeignMemory.getDoubleVolatile(userPtr + offset);
    }

    public static void unsafeVolatileSetByte(long userPtr, int fieldIndex, byte value) {
        int generic = getStructIdFromPointer(userPtr);
        unsafeVolatileSetByte(generic, userPtr, fieldIndex, value);
    }

    public static void unsafeVolatileSetByte(int generic, long userPtr, int fieldIndex, byte value) {
        int offset = getOffset(generic, fieldIndex);
        ForeignMemory.putByteVolatile(userPtr + offset, value);
    }

    public static byte unsafeVolatileGetByte(long userPtr, int fieldIndex) {
        int generic = getStructIdFromPointer(userPtr);
        return unsafeVolatileGetByte(generic, userPtr, fieldIndex);
    }

    public static byte unsafeVolatileGetByte(int generic, long userPtr, int fieldIndex) {
        int offset = getOffset(generic, fieldIndex);
        return ForeignMemory.getByteVolatile(userPtr + offset);
    }

    public static void unsafeVolatileSetShort(long userPtr, int fieldIndex, short value) {
        int generic = getStructIdFromPointer(userPtr);
        unsafeVolatileSetShort(generic, userPtr, fieldIndex, value);
    }

    public static void unsafeVolatileSetShort(int generic, long userPtr, int fieldIndex, short value) {
        int offset = getOffset(generic, fieldIndex);
        ForeignMemory.putShortVolatile(userPtr + offset, value);
    }

    public static short unsafeVolatileGetShort(long userPtr, int fieldIndex) {
        int generic = getStructIdFromPointer(userPtr);
        return unsafeVolatileGetShort(generic, userPtr, fieldIndex);
    }

    public static short unsafeVolatileGetShort(int generic, long userPtr, int fieldIndex) {
        int offset = getOffset(generic, fieldIndex);
        return ForeignMemory.getShortVolatile(userPtr + offset);
    }

    // =========================================================================
    // UNSAFE VOLATILE ARRAY FIELD MUTATORS & ACCESSORS (ARRAY / LEVEL 2)
    // =========================================================================


    public static void unsafeVolatileSetInt(long userPtr, int elementIndex, int fieldIndex, int value) {
        int generic = getStructIdFromPointer(userPtr);
        unsafeVolatileSetInt(generic, userPtr, elementIndex, fieldIndex, value);
    }

    public static void unsafeVolatileSetInt(int generic, long userPtr, int elementIndex, int fieldIndex, int value) {
        int stride = getStride(generic);
        int offset = getOffset(generic, fieldIndex);
        ForeignMemory.putIntVolatile(userPtr + (long) elementIndex * stride + offset, value);
    }

    public static int unsafeVolatileGetInt(long userPtr, int elementIndex, int fieldIndex) {
        int generic = getStructIdFromPointer(userPtr);
        return unsafeVolatileGetInt(generic, userPtr, elementIndex, fieldIndex);
    }

    public static int unsafeVolatileGetInt(int generic, long userPtr, int elementIndex, int fieldIndex) {
        int stride = getStride(generic);
        int offset = getOffset(generic, fieldIndex);
        return ForeignMemory.getIntVolatile(userPtr + (long) elementIndex * stride + offset);
    }

    public static void unsafeVolatileSetLong(long userPtr, int elementIndex, int fieldIndex, long value) {
        int generic = getStructIdFromPointer(userPtr);
        unsafeVolatileSetLong(generic, userPtr, elementIndex, fieldIndex, value);
    }

    public static void unsafeVolatileSetLong(int generic, long userPtr, int elementIndex, int fieldIndex, long value) {
        int stride = getStride(generic);
        int offset = getOffset(generic, fieldIndex);
        ForeignMemory.putLongVolatile(userPtr + (long) elementIndex * stride + offset, value);
    }

    public static long unsafeVolatileGetLong(long userPtr, int elementIndex, int fieldIndex) {
        int generic = getStructIdFromPointer(userPtr);
        return unsafeVolatileGetLong(generic, userPtr, elementIndex, fieldIndex);
    }

    public static long unsafeVolatileGetLong(int generic, long userPtr, int elementIndex, int fieldIndex) {
        int stride = getStride(generic);
        int offset = getOffset(generic, fieldIndex);
        return ForeignMemory.getLongVolatile(userPtr + (long) elementIndex * stride + offset);
    }

    public static void unsafeVolatileSetFloat(long userPtr, int elementIndex, int fieldIndex, float value) {
        int generic = getStructIdFromPointer(userPtr);
        unsafeVolatileSetFloat(generic, userPtr, elementIndex, fieldIndex, value);
    }

    public static void unsafeVolatileSetFloat(int generic, long userPtr, int elementIndex, int fieldIndex, float value) {
        int stride = getStride(generic);
        int offset = getOffset(generic, fieldIndex);
        ForeignMemory.putFloatVolatile(userPtr + (long) elementIndex * stride + offset, value);
    }

    public static float unsafeVolatileGetFloat(long userPtr, int elementIndex, int fieldIndex) {
        int generic = getStructIdFromPointer(userPtr);
        return unsafeVolatileGetFloat(generic, userPtr, elementIndex, fieldIndex);
    }

    public static float unsafeVolatileGetFloat(int generic, long userPtr, int elementIndex, int fieldIndex) {
        int stride = getStride(generic);
        int offset = getOffset(generic, fieldIndex);
        return ForeignMemory.getFloatVolatile(userPtr + (long) elementIndex * stride + offset);
    }

    public static void unsafeVolatileSetDouble(long userPtr, int elementIndex, int fieldIndex, double value) {
        int generic = getStructIdFromPointer(userPtr);
        unsafeVolatileSetDouble(generic, userPtr, elementIndex, fieldIndex, value);
    }

    public static void unsafeVolatileSetDouble(int generic, long userPtr, int elementIndex, int fieldIndex, double value) {
        int stride = getStride(generic);
        int offset = getOffset(generic, fieldIndex);
        ForeignMemory.putDoubleVolatile(userPtr + (long) elementIndex * stride + offset, value);
    }

    public static double unsafeVolatileGetDouble(long userPtr, int elementIndex, int fieldIndex) {
        int generic = getStructIdFromPointer(userPtr);
        return unsafeVolatileGetDouble(generic, userPtr, elementIndex, fieldIndex);
    }

    public static double unsafeVolatileGetDouble(int generic, long userPtr, int elementIndex, int fieldIndex) {
        int stride = getStride(generic);
        int offset = getOffset(generic, fieldIndex);
        return ForeignMemory.getDoubleVolatile(userPtr + (long) elementIndex * stride + offset);
    }

    public static void unsafeVolatileSetByte(long userPtr, int elementIndex, int fieldIndex, byte value) {
        int generic = getStructIdFromPointer(userPtr);
        unsafeVolatileSetByte(generic, userPtr, elementIndex, fieldIndex, value);
    }

    public static void unsafeVolatileSetByte(int generic, long userPtr, int elementIndex, int fieldIndex, byte value) {
        int stride = getStride(generic);
        int offset = getOffset(generic, fieldIndex);
        ForeignMemory.putByteVolatile(userPtr + (long) elementIndex * stride + offset, value);
    }

    public static byte unsafeVolatileGetByte(long userPtr, int elementIndex, int fieldIndex) {
        int generic = getStructIdFromPointer(userPtr);
        return unsafeVolatileGetByte(generic, userPtr, elementIndex, fieldIndex);
    }

    public static byte unsafeVolatileGetByte(int generic, long userPtr, int elementIndex, int fieldIndex) {
        int stride = getStride(generic);
        int offset = getOffset(generic, fieldIndex);
        return ForeignMemory.getByteVolatile(userPtr + (long) elementIndex * stride + offset);
    }

    public static void unsafeVolatileSetShort(long userPtr, int elementIndex, int fieldIndex, short value) {
        int generic = getStructIdFromPointer(userPtr);
        unsafeVolatileSetShort(generic, userPtr, elementIndex, fieldIndex, value);
    }

    public static void unsafeVolatileSetShort(int generic, long userPtr, int elementIndex, int fieldIndex, short value) {
        int stride = getStride(generic);
        int offset = getOffset(generic, fieldIndex);
        ForeignMemory.putShortVolatile(userPtr + (long) elementIndex * stride + offset, value);
    }

    public static short unsafeVolatileGetShort(long userPtr, int elementIndex, int fieldIndex) {
        int generic = getStructIdFromPointer(userPtr);
        return unsafeVolatileGetShort(generic, userPtr, elementIndex, fieldIndex);
    }

    public static short unsafeVolatileGetShort(int generic, long userPtr, int elementIndex, int fieldIndex) {
        int stride = getStride(generic);
        int offset = getOffset(generic, fieldIndex);
        return ForeignMemory.getShortVolatile(userPtr + (long) elementIndex * stride + offset);
    }

}
