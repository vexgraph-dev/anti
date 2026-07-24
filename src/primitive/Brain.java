package primitive;

import annotation.Volatile;

import annotation.Required;
import nio.ForeignMemory;
import oop.TypeRegister;

import java.lang.foreign.Arena;
import java.lang.invoke.MethodHandles;
import java.lang.invoke.VarHandle;

public final class Brain {

    @Required
    public static final int CLASS_ID = TypeRegister.ID_BRAIN;

    public static final int TYPE_SINGLETON = TypeRegister.BRAIN_SINGLETON; // 0xAA000029
    public static final int TYPE_ARRAY     = TypeRegister.BRAIN_ARRAY;     // 0xBB000029
    public static final int TYPE_MATRIX    = TypeRegister.BRAIN_POINTER;   // 0xCC000029

    private static final int DEFAULT_CAPACITY = 1024;

    // Memory Block Sizes (Including 8-byte headers: 4B typeId + 4B length)
    private static final long SINGLETON_SLOT_SIZE = 16L; // 8B header + 2B data + 6B padding
    private static final long POOLED_ARRAY_SIZE = 8L + (DEFAULT_CAPACITY * 2L);  // 2056 Bytes
    private static final long POOLED_MATRIX_SIZE = 8L + (DEFAULT_CAPACITY * 8L); // 8200 Bytes

    private static final VarHandle SINGLETON_FREE_HEAD_VH;
    private static final VarHandle ARRAY_FREE_HEAD_VH;
    private static final VarHandle MATRIX_FREE_HEAD_VH;

    private static final VarHandle SINGLETON_EXPANDING_VH;
    private static final VarHandle ARRAY_EXPANDING_VH;
    private static final VarHandle MATRIX_EXPANDING_VH;

    private static volatile int singletonExpanding = 0;
    private static volatile int arrayExpanding = 0;
    private static volatile int matrixExpanding = 0;

    private static Arena poolArena;
    private static volatile boolean active;

    // Top 16 bits = 16-bit Generation Tag, Bottom 48 bits = Raw Memory Pointer
    private static volatile long singletonFreeHead;
    private static volatile long arrayFreeHead;
    private static volatile long matrixFreeHead;

    static {
        try {
            MethodHandles.Lookup lookup = MethodHandles.lookup();
            SINGLETON_FREE_HEAD_VH = lookup.findStaticVarHandle(Brain.class, "singletonFreeHead", long.class);
            ARRAY_FREE_HEAD_VH = lookup.findStaticVarHandle(Brain.class, "arrayFreeHead", long.class);
            MATRIX_FREE_HEAD_VH = lookup.findStaticVarHandle(Brain.class, "matrixFreeHead", long.class);

            SINGLETON_EXPANDING_VH = lookup.findStaticVarHandle(Brain.class, "singletonExpanding", int.class);
            ARRAY_EXPANDING_VH = lookup.findStaticVarHandle(Brain.class, "arrayExpanding", int.class);
            MATRIX_EXPANDING_VH = lookup.findStaticVarHandle(Brain.class, "matrixExpanding", int.class);
        } catch (ReflectiveOperationException e) {
            throw new ExceptionInInitializerError(e);
        }

        poolArena = Arena.ofShared();
        active = true;

        expandSingletonPool();
        expandArrayPool();
        expandMatrixPool();
    }

    private Brain() {}

    private static void checkActive() {
        if (!active) throw new IllegalStateException("Brain subsystem is not active!");
    }

    public static void freeAll() {
        if (active) {
            active = false;
            if (poolArena != null && poolArena.scope().isAlive()) {
                poolArena.close();
            }
        }
    }

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

    // --- POOL EXPANSIONS ---
    private static void expandSingletonPool() {
        long totalBytes = DEFAULT_CAPACITY * SINGLETON_SLOT_SIZE;
        long baseAddress = poolArena.allocate(totalBytes, 8).address();

        for (int i = 0; i < DEFAULT_CAPACITY; i++) {
            long currentBlock = baseAddress + (i * SINGLETON_SLOT_SIZE);
            long userPtr = currentBlock + 8L;

            while (true) {
                long oldTagged = singletonFreeHead;
                long oldRawHead = oldTagged & 0x0000FFFFFFFFFFFFL;

                ForeignMemory.putLong(userPtr, oldRawHead);

                long nextGen = ((oldTagged >>> 48) + 1L) & 0xFFFFL;
                long newTagged = (nextGen << 48) | (userPtr & 0x0000FFFFFFFFFFFFL);

                if (SINGLETON_FREE_HEAD_VH.compareAndSet(oldTagged, newTagged)) break;
            }
        }
    }

    private static void expandArrayPool() {
        long totalBytes = DEFAULT_CAPACITY * POOLED_ARRAY_SIZE;
        long baseAddress = poolArena.allocate(totalBytes, 8).address();

        for (int i = 0; i < DEFAULT_CAPACITY; i++) {
            long currentBlock = baseAddress + (i * POOLED_ARRAY_SIZE);
            long userPtr = currentBlock + 8L;

            while (true) {
                long oldTagged = arrayFreeHead;
                long oldRawHead = oldTagged & 0x0000FFFFFFFFFFFFL;

                ForeignMemory.putLong(userPtr, oldRawHead);

                long nextGen = ((oldTagged >>> 48) + 1L) & 0xFFFFL;
                long newTagged = (nextGen << 48) | (userPtr & 0x0000FFFFFFFFFFFFL);

                if (ARRAY_FREE_HEAD_VH.compareAndSet(oldTagged, newTagged)) break;
            }
        }
    }

    private static void expandMatrixPool() {
        long totalBytes = DEFAULT_CAPACITY * POOLED_MATRIX_SIZE;
        long baseAddress = poolArena.allocate(totalBytes, 8).address();

        for (int i = 0; i < DEFAULT_CAPACITY; i++) {
            long currentBlock = baseAddress + (i * POOLED_MATRIX_SIZE);
            long userPtr = currentBlock + 8L;

            while (true) {
                long oldTagged = matrixFreeHead;
                long oldRawHead = oldTagged & 0x0000FFFFFFFFFFFFL;

                ForeignMemory.putLong(userPtr, oldRawHead);

                long nextGen = ((oldTagged >>> 48) + 1L) & 0xFFFFL;
                long newTagged = (nextGen << 48) | (userPtr & 0x0000FFFFFFFFFFFFL);

                if (MATRIX_FREE_HEAD_VH.compareAndSet(oldTagged, newTagged)) break;
            }
        }
    }

    // --- ALLOCATION LAYER ---
    public static long allocateSingleton() {
        checkActive();
        while (true) {
            long oldTagged = singletonFreeHead;
            long rawHead = oldTagged & 0x0000FFFFFFFFFFFFL;

            if (rawHead == 0L) {
                if (SINGLETON_EXPANDING_VH.compareAndSet(0, 1)) {
                    expandSingletonPool();
                    SINGLETON_EXPANDING_VH.setVolatile(0);
                } else {
                    Thread.onSpinWait();
                }
                continue;
            }

            long nextRawHead = ForeignMemory.getLong(rawHead);
            long nextGen = ((oldTagged >>> 48) + 1L) & 0xFFFFL;
            long newTagged = (nextGen << 48) | (nextRawHead & 0x0000FFFFFFFFFFFFL);

            if (SINGLETON_FREE_HEAD_VH.compareAndSet(oldTagged, newTagged)) {
                long block = rawHead - 8L;
                ForeignMemory.putInt(block, TYPE_SINGLETON);
                ForeignMemory.putInt(block + 4L, 1);
                ForeignMemory.putShort(rawHead, (short) 0);
                return rawHead;
            }
        }
    }

    public static long allocateArray(int length) {
        checkActive();
        if (length <= 0) throw new IllegalArgumentException("Length must be > 0!");

        if (length == DEFAULT_CAPACITY) {
            while (true) {
                long oldTagged = arrayFreeHead;
                long rawHead = oldTagged & 0x0000FFFFFFFFFFFFL;

                if (rawHead == 0L) {
                    if (ARRAY_EXPANDING_VH.compareAndSet(0, 1)) {
                        expandArrayPool();
                        ARRAY_EXPANDING_VH.setVolatile(0);
                    } else {
                        Thread.onSpinWait();
                    }
                    continue;
                }

                long nextRawHead = ForeignMemory.getLong(rawHead);
                long nextGen = ((oldTagged >>> 48) + 1L) & 0xFFFFL;
                long newTagged = (nextGen << 48) | (nextRawHead & 0x0000FFFFFFFFFFFFL);

                if (ARRAY_FREE_HEAD_VH.compareAndSet(oldTagged, newTagged)) {
                    long block = rawHead - 8L;
                    ForeignMemory.putInt(block, TYPE_ARRAY);
                    ForeignMemory.putInt(block + 4L, length);
                    ForeignMemory.setMemory(rawHead, length * 2L, (byte) 0);
                    return rawHead;
                }
            }
        } else {
            long totalBytes = 8L + (length * 2L);
            long block = ForeignMemory.allocateNative(totalBytes);
            long userPtr = block + 8L;
            ForeignMemory.putInt(block, TYPE_ARRAY);
            ForeignMemory.putInt(block + 4L, length);
            ForeignMemory.setMemory(userPtr, length * 2L, (byte) 0);
            return userPtr;
        }
    }

    public static long allocateMatrix(int length) {
        checkActive();
        if (length <= 0) throw new IllegalArgumentException("Length must be > 0!");

        if (length == DEFAULT_CAPACITY) {
            while (true) {
                long oldTagged = matrixFreeHead;
                long rawHead = oldTagged & 0x0000FFFFFFFFFFFFL;

                if (rawHead == 0L) {
                    if (MATRIX_EXPANDING_VH.compareAndSet(0, 1)) {
                        expandMatrixPool();
                        MATRIX_EXPANDING_VH.setVolatile(0);
                    } else {
                        Thread.onSpinWait();
                    }
                    continue;
                }

                long nextRawHead = ForeignMemory.getLong(rawHead);
                long nextGen = ((oldTagged >>> 48) + 1L) & 0xFFFFL;
                long newTagged = (nextGen << 48) | (nextRawHead & 0x0000FFFFFFFFFFFFL);

                if (MATRIX_FREE_HEAD_VH.compareAndSet(oldTagged, newTagged)) {
                    long block = rawHead - 8L;
                    ForeignMemory.putInt(block, TYPE_MATRIX);
                    ForeignMemory.putInt(block + 4L, length);
                    ForeignMemory.setMemory(rawHead, length * 8L, (byte) 0);
                    return rawHead;
                }
            }
        } else {
            long totalBytes = 8L + (length * 8L);
            long block = ForeignMemory.allocateNative(totalBytes);
            long userPtr = block + 8L;
            ForeignMemory.putInt(block, TYPE_MATRIX);
            ForeignMemory.putInt(block + 4L, length);
            ForeignMemory.setMemory(userPtr, length * 8L, (byte) 0);
            return userPtr;
        }
    }

    public static void free(long pointer) {
        if (pointer == 0L) return;
        checkActive();

        int type = type(pointer);
        int length = length(pointer);

        if (type == TYPE_SINGLETON) {
            while (true) {
                long oldTagged = singletonFreeHead;
                long rawHead = oldTagged & 0x0000FFFFFFFFFFFFL;

                ForeignMemory.putLong(pointer, rawHead);

                long nextGen = ((oldTagged >>> 48) + 1L) & 0xFFFFL;
                long newTagged = (nextGen << 48) | (pointer & 0x0000FFFFFFFFFFFFL);

                if (SINGLETON_FREE_HEAD_VH.compareAndSet(oldTagged, newTagged)) break;
            }
        } else if (type == TYPE_ARRAY) {
            if (length == DEFAULT_CAPACITY) {
                while (true) {
                    long oldTagged = arrayFreeHead;
                    long rawHead = oldTagged & 0x0000FFFFFFFFFFFFL;

                    ForeignMemory.putLong(pointer, rawHead);

                    long nextGen = ((oldTagged >>> 48) + 1L) & 0xFFFFL;
                    long newTagged = (nextGen << 48) | (pointer & 0x0000FFFFFFFFFFFFL);

                    if (ARRAY_FREE_HEAD_VH.compareAndSet(oldTagged, newTagged)) break;
                }
            } else {
                long block = pointer - 8L;
                int oldType = ForeignMemory.getInt(block);
                if (oldType == 0 || !TypeRegister.isArray(oldType)) {
                    throw new IllegalStateException("Double free or corrupt off-heap pointer: 0x" + java.lang.Long.toHexString(pointer).toUpperCase());
                }
                ForeignMemory.putInt(block, 0);
                ForeignMemory.putInt(block + 4L, -1);
                ForeignMemory.freeNative(block);
            }
        } else if (type == TYPE_MATRIX) {
            if (length == DEFAULT_CAPACITY) {
                while (true) {
                    long oldTagged = matrixFreeHead;
                    long rawHead = oldTagged & 0x0000FFFFFFFFFFFFL;

                    ForeignMemory.putLong(pointer, rawHead);

                    long nextGen = ((oldTagged >>> 48) + 1L) & 0xFFFFL;
                    long newTagged = (nextGen << 48) | (pointer & 0x0000FFFFFFFFFFFFL);

                    if (MATRIX_FREE_HEAD_VH.compareAndSet(oldTagged, newTagged)) break;
                }
            } else {
                long block = pointer - 8L;
                int oldType = ForeignMemory.getInt(block);
                if (oldType == 0 || !TypeRegister.isPointer(oldType)) {
                    throw new IllegalStateException("Double free or corrupt off-heap pointer: 0x" + java.lang.Long.toHexString(pointer).toUpperCase());
                }
                ForeignMemory.putInt(block, 0);
                ForeignMemory.putInt(block + 4L, -1);
                ForeignMemory.freeNative(block);
            }
        } else {
            throw new IllegalArgumentException("Unknown brain float type format: 0x" + Integer.toHexString(type).toUpperCase());
        }
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
        ForeignMemory.putShort(pointer, rawVal);
    }

    public static void set(long pointer, int index, float value) {
        checkBounds(pointer, index);
        short rawVal = floatToBFloat16(value);
        ForeignMemory.putShort(pointer + (index * 2L), rawVal);
    }

    @Volatile
    public static float getVolatile(long pointer) {
        if (pointer == 0L) throw new NullPointerException("Reading from NULL off-heap pointer!");
        short rawVal = ForeignMemory.getShortVolatile(pointer);
        return bFloat16ToFloat(rawVal);
    }

    @Volatile
    public static void setVolatile(long pointer, float value) {
        if (pointer == 0L) throw new NullPointerException("Writing to NULL off-heap pointer!");
        short rawVal = floatToBFloat16(value);
        ForeignMemory.putShortVolatile(pointer, rawVal);
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
        ForeignMemory.putLong(matrixPointer + (index * 8L), targetPointer);
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
