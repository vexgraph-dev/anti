package primitive;

import annotation.Unsafe;
import annotation.Volatile;

import annotation.Required;
import nio.ForeignMemory;
import oop.TypeRegister;

import java.lang.foreign.Arena;
import java.lang.invoke.MethodHandles;
import java.lang.invoke.VarHandle;

public final class Fixed32 {

    @Required
    public static final int CLASS_ID = TypeRegister.ID_FIXED32;

    public static final int TYPE_SINGLETON = TypeRegister.FIXED32_SINGLETON; // 0xAA00002A
    public static final int TYPE_ARRAY     = TypeRegister.FIXED32_ARRAY;     // 0xBB00002A
    public static final int TYPE_MATRIX    = TypeRegister.FIXED32_POINTER;   // 0xCC00002A

    private static final int DEFAULT_CAPACITY = 1024;

    // Memory Block Sizes (Including 8-byte headers: 4B typeId + 4B length)
    private static final long SINGLETON_SLOT_SIZE = 16L; // 8B header + 4B data + 4B padding
    private static final long POOLED_ARRAY_SIZE = 8L + (DEFAULT_CAPACITY * 4L);  // 4104 Bytes
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

    private static long CACHE_ARENA_BASE;

    // Top 16 bits = 16-bit Generation Tag, Bottom 48 bits = Raw Memory Pointer
    private static volatile long singletonFreeHead;
    private static volatile long arrayFreeHead;
    private static volatile long matrixFreeHead;

    static {
        try {
            MethodHandles.Lookup lookup = MethodHandles.lookup();
            SINGLETON_FREE_HEAD_VH = lookup.findStaticVarHandle(Fixed32.class, "singletonFreeHead", long.class);
            ARRAY_FREE_HEAD_VH = lookup.findStaticVarHandle(Fixed32.class, "arrayFreeHead", long.class);
            MATRIX_FREE_HEAD_VH = lookup.findStaticVarHandle(Fixed32.class, "matrixFreeHead", long.class);

            SINGLETON_EXPANDING_VH = lookup.findStaticVarHandle(Fixed32.class, "singletonExpanding", int.class);
            ARRAY_EXPANDING_VH = lookup.findStaticVarHandle(Fixed32.class, "arrayExpanding", int.class);
            MATRIX_EXPANDING_VH = lookup.findStaticVarHandle(Fixed32.class, "matrixExpanding", int.class);
        } catch (ReflectiveOperationException e) {
            throw new ExceptionInInitializerError(e);
        }

        poolArena = Arena.ofShared();
        active = true;

        CACHE_ARENA_BASE = ForeignMemory.allocateNative(256L * 256L);
        ForeignMemory.setMemory(CACHE_ARENA_BASE, 256L * 256L, (byte) 0);

        expandSingletonPool();
        expandArrayPool();
        expandMatrixPool();
    }

    private Fixed32() {}

    private static void checkActive() {
        if (!active) throw new IllegalStateException("Fixed32 subsystem is not active!");
    }

    public static void freeAll() {
        if (active) {
            active = false;
            if (poolArena != null && poolArena.scope().isAlive()) {
                poolArena.close();
            }
            if (CACHE_ARENA_BASE != 0L) {
                ForeignMemory.freeNative(CACHE_ARENA_BASE);
                CACHE_ARENA_BASE = 0L;
            }
        }
    }

    // --- CONVERSION METHODS ---
    public static int floatToFixed32(float val) {
        return Math.round(val * 65536.0f);
    }

    public static float fixed32ToFloat(int val) {
        return val / 65536.0f;
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

                ForeignMemory.set(userPtr, oldRawHead);

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

                ForeignMemory.set(userPtr, oldRawHead);

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

                ForeignMemory.set(userPtr, oldRawHead);

                long nextGen = ((oldTagged >>> 48) + 1L) & 0xFFFFL;
                long newTagged = (nextGen << 48) | (userPtr & 0x0000FFFFFFFFFFFFL);

                if (MATRIX_FREE_HEAD_VH.compareAndSet(oldTagged, newTagged)) break;
            }
        }
    }

    // --- ALLOCATION LAYER ---
    public static long allocateSingleton() {
        checkActive();
        int tid = thread.ThreadRegistry.getThreadIndex();
        long slotBase = CACHE_ARENA_BASE + (tid * 256L);
        long countSingletonAddr = slotBase;
        int count = ForeignMemory.getUnsafeInt(countSingletonAddr);
        if (count > 0) {
            int newCount = count - 1;
            ForeignMemory.set(countSingletonAddr, newCount);
            long dataAddr = slotBase + 32L + (newCount * 8L);
            long ptr = ForeignMemory.getUnsafeLong(dataAddr);
            long base = ptr - 8L;
            ForeignMemory.set(base, TYPE_SINGLETON);
            ForeignMemory.set(base + 4L, 1);
            ForeignMemory.set(ptr, 0);
            return ptr;
        }
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

            long nextRawHead = ForeignMemory.getUnsafeLong(rawHead);
            long nextGen = ((oldTagged >>> 48) + 1L) & 0xFFFFL;
            long newTagged = (nextGen << 48) | (nextRawHead & 0x0000FFFFFFFFFFFFL);

            if (SINGLETON_FREE_HEAD_VH.compareAndSet(oldTagged, newTagged)) {
                long block = rawHead - 8L;
                ForeignMemory.set(block, TYPE_SINGLETON);
                ForeignMemory.set(block + 4L, 1);
                ForeignMemory.set(rawHead, 0);
                return rawHead;
            }
        }
    }

    public static long allocateArray(int length) {
        checkActive();
        if (length <= 0) throw new IllegalArgumentException("Length must be > 0!");

        if (length == DEFAULT_CAPACITY) {
            int tid = thread.ThreadRegistry.getThreadIndex();
            long slotBase = CACHE_ARENA_BASE + (tid * 256L);
            long countArrayAddr = slotBase + 4L;
            int count = ForeignMemory.getUnsafeInt(countArrayAddr);
            if (count > 0) {
                int newCount = count - 1;
                ForeignMemory.set(countArrayAddr, newCount);
                long dataAddr = slotBase + 96L + (newCount * 8L);
                long ptr = ForeignMemory.getUnsafeLong(dataAddr);
                long base = ptr - 8L;
                ForeignMemory.set(base, TYPE_ARRAY);
                ForeignMemory.set(base + 4L, length);
                ForeignMemory.setMemory(ptr, length * 4L, (byte) 0);
                return ptr;
            }
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

                long nextRawHead = ForeignMemory.getUnsafeLong(rawHead);
                long nextGen = ((oldTagged >>> 48) + 1L) & 0xFFFFL;
                long newTagged = (nextGen << 48) | (nextRawHead & 0x0000FFFFFFFFFFFFL);

                if (ARRAY_FREE_HEAD_VH.compareAndSet(oldTagged, newTagged)) {
                    long block = rawHead - 8L;
                    ForeignMemory.set(block, TYPE_ARRAY);
                    ForeignMemory.set(block + 4L, length);
                    ForeignMemory.setMemory(rawHead, length * 4L, (byte) 0);
                    return rawHead;
                }
            }
        } else {
            long totalBytes = 8L + (length * 4L);
            long block = ForeignMemory.allocateNative(totalBytes);
            long userPtr = block + 8L;
            ForeignMemory.set(block, TYPE_ARRAY);
            ForeignMemory.set(block + 4L, length);
            ForeignMemory.setMemory(userPtr, length * 4L, (byte) 0);
            return userPtr;
        }
    }

    public static long allocateMatrix(int length) {
        checkActive();
        if (length <= 0) throw new IllegalArgumentException("Length must be > 0!");

        if (length == DEFAULT_CAPACITY) {
            int tid = thread.ThreadRegistry.getThreadIndex();
            long slotBase = CACHE_ARENA_BASE + (tid * 256L);
            long countMatrixAddr = slotBase + 8L;
            int count = ForeignMemory.getUnsafeInt(countMatrixAddr);
            if (count > 0) {
                int newCount = count - 1;
                ForeignMemory.set(countMatrixAddr, newCount);
                long dataAddr = slotBase + 160L + (newCount * 8L);
                long ptr = ForeignMemory.getUnsafeLong(dataAddr);
                long base = ptr - 8L;
                ForeignMemory.set(base, TYPE_MATRIX);
                ForeignMemory.set(base + 4L, length);
                ForeignMemory.setMemory(ptr, length * 8L, (byte) 0);
                return ptr;
            }
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

                long nextRawHead = ForeignMemory.getUnsafeLong(rawHead);
                long nextGen = ((oldTagged >>> 48) + 1L) & 0xFFFFL;
                long newTagged = (nextGen << 48) | (nextRawHead & 0x0000FFFFFFFFFFFFL);

                if (MATRIX_FREE_HEAD_VH.compareAndSet(oldTagged, newTagged)) {
                    long block = rawHead - 8L;
                    ForeignMemory.set(block, TYPE_MATRIX);
                    ForeignMemory.set(block + 4L, length);
                    ForeignMemory.setMemory(rawHead, length * 8L, (byte) 0);
                    return rawHead;
                }
            }
        } else {
            long totalBytes = 8L + (length * 8L);
            long block = ForeignMemory.allocateNative(totalBytes);
            long userPtr = block + 8L;
            ForeignMemory.set(block, TYPE_MATRIX);
            ForeignMemory.set(block + 4L, length);
            ForeignMemory.setMemory(userPtr, length * 8L, (byte) 0);
            return userPtr;
        }
    }

    public static void free(long pointer) {
        if (pointer == 0L) return;
        checkActive();

        int type = type(pointer);
        if (type == 0 || (!TypeRegister.isSingleton(type) && !TypeRegister.isArray(type) && !TypeRegister.isPointer(type))) {
            throw new IllegalStateException("Double free or corrupt off-heap pointer: 0x" + java.lang.Long.toHexString(pointer).toUpperCase());
        }

        int length = length(pointer);
        long base = pointer - 8L;

        ForeignMemory.set(base, 0);
        ForeignMemory.set(base + 4L, -1);

        int tid = thread.ThreadRegistry.getThreadIndex();
        long slotBase = CACHE_ARENA_BASE + (tid * 256L);

        if (type == TYPE_SINGLETON) {
            long countSingletonAddr = slotBase;
            int count = ForeignMemory.getUnsafeInt(countSingletonAddr);
            if (count < 8) {
                long dataAddr = slotBase + 32L + (count * 8L);
                ForeignMemory.set(dataAddr, pointer);
                ForeignMemory.set(countSingletonAddr, count + 1);
                return;
            }
            while (true) {
                long oldTagged = singletonFreeHead;
                long rawHead = oldTagged & 0x0000FFFFFFFFFFFFL;

                ForeignMemory.set(pointer, rawHead);

                long nextGen = ((oldTagged >>> 48) + 1L) & 0xFFFFL;
                long newTagged = (nextGen << 48) | (pointer & 0x0000FFFFFFFFFFFFL);

                if (SINGLETON_FREE_HEAD_VH.compareAndSet(oldTagged, newTagged)) break;
            }
        } else if (type == TYPE_ARRAY) {
            if (length != DEFAULT_CAPACITY) {
                ForeignMemory.freeNative(base);
                return;
            }
            long countArrayAddr = slotBase + 4L;
            int count = ForeignMemory.getUnsafeInt(countArrayAddr);
            if (count < 8) {
                long dataAddr = slotBase + 96L + (count * 8L);
                ForeignMemory.set(dataAddr, pointer);
                ForeignMemory.set(countArrayAddr, count + 1);
                return;
            }
            while (true) {
                long oldTagged = arrayFreeHead;
                long rawHead = oldTagged & 0x0000FFFFFFFFFFFFL;

                ForeignMemory.set(pointer, rawHead);

                long nextGen = ((oldTagged >>> 48) + 1L) & 0xFFFFL;
                long newTagged = (nextGen << 48) | (pointer & 0x0000FFFFFFFFFFFFL);

                if (ARRAY_FREE_HEAD_VH.compareAndSet(oldTagged, newTagged)) break;
            }
        } else if (type == TYPE_MATRIX) {
            if (length != DEFAULT_CAPACITY) {
                ForeignMemory.freeNative(base);
                return;
            }
            long countMatrixAddr = slotBase + 8L;
            int count = ForeignMemory.getUnsafeInt(countMatrixAddr);
            if (count < 8) {
                long dataAddr = slotBase + 160L + (count * 8L);
                ForeignMemory.set(dataAddr, pointer);
                ForeignMemory.set(countMatrixAddr, count + 1);
                return;
            }
            while (true) {
                long oldTagged = matrixFreeHead;
                long rawHead = oldTagged & 0x0000FFFFFFFFFFFFL;

                ForeignMemory.set(pointer, rawHead);

                long nextGen = ((oldTagged >>> 48) + 1L) & 0xFFFFL;
                long newTagged = (nextGen << 48) | (pointer & 0x0000FFFFFFFFFFFFL);

                if (MATRIX_FREE_HEAD_VH.compareAndSet(oldTagged, newTagged)) break;
            }
        }
    }

    // --- MUTATORS & ACCESSORS ---
    public static float get(long pointer) {
        if (pointer == 0L) throw new NullPointerException("Reading from NULL off-heap pointer!");
        int rawVal = ForeignMemory.getUnsafeInt(pointer);
        return fixed32ToFloat(rawVal);
    }

    public static float get(long pointer, int index) {
        checkBounds(pointer, index);
        int rawVal = ForeignMemory.getUnsafeInt(pointer + (index * 4L));
        return fixed32ToFloat(rawVal);
    }

    public static void set(long pointer, float value) {
        if (pointer == 0L) throw new NullPointerException("Writing to NULL off-heap pointer!");
        if (classId(pointer) != CLASS_ID) throw new IllegalArgumentException("Pointer 0x" + java.lang.Long.toHexString(pointer).toUpperCase() + " is Class ID " + classId(pointer) + ", expected Fixed32 (Class ID " + CLASS_ID + ")");
        int rawVal = floatToFixed32(value);
        ForeignMemory.set(pointer, rawVal);
    }

    public static void set(long pointer, int index, float value) {
        checkBounds(pointer, index);
        if (classId(pointer) != CLASS_ID) throw new IllegalArgumentException("Pointer 0x" + java.lang.Long.toHexString(pointer).toUpperCase() + " is Class ID " + classId(pointer) + ", expected Fixed32 (Class ID " + CLASS_ID + ")");
        int rawVal = floatToFixed32(value);
        ForeignMemory.set(pointer + (index * 4L), rawVal);
    }

    @Volatile
    public static float getVolatile(long pointer) {
        if (pointer == 0L) throw new NullPointerException("Reading from NULL off-heap pointer!");
        if (classId(pointer) != CLASS_ID) throw new IllegalArgumentException("Pointer 0x" + java.lang.Long.toHexString(pointer).toUpperCase() + " is Class ID " + classId(pointer) + ", expected Fixed32 (Class ID " + CLASS_ID + ")");
        int rawVal = ForeignMemory.getUnsafeVolatileInt(pointer);
        return fixed32ToFloat(rawVal);
    }

    @Volatile
    public static void setVolatile(long pointer, float value) {
        if (pointer == 0L) throw new NullPointerException("Writing to NULL off-heap pointer!");
        if (classId(pointer) != CLASS_ID) throw new IllegalArgumentException("Pointer 0x" + java.lang.Long.toHexString(pointer).toUpperCase() + " is Class ID " + classId(pointer) + ", expected Fixed32 (Class ID " + CLASS_ID + ")");
        int rawVal = floatToFixed32(value);
        ForeignMemory.setVolatile(pointer, rawVal);
    }

    public static boolean compareAndSet(long pointer, float expected, float value) {
        if (pointer == 0L) throw new NullPointerException("Writing to NULL off-heap pointer!");
        if (classId(pointer) != CLASS_ID) throw new IllegalArgumentException("Pointer 0x" + java.lang.Long.toHexString(pointer).toUpperCase() + " is Class ID " + classId(pointer) + ", expected Fixed32 (Class ID " + CLASS_ID + ")");
        int expectedRaw = floatToFixed32(expected);
        int valueRaw = floatToFixed32(value);
        return ForeignMemory.compareAndSetInt(pointer, expectedRaw, valueRaw);
    }

    public static long getPointer(long matrixPointer, int index) {
        if (matrixPointer == 0L) throw new NullPointerException("Accessing NULL matrix pointer!");
        if (!isPointer(matrixPointer)) {
            throw new IllegalArgumentException("Expected Pointer Array (Matrix), but got Type: 0x" + Integer.toHexString(type(matrixPointer)).toUpperCase());
        }
        checkBounds(matrixPointer, index);
        return ForeignMemory.getUnsafeLong(matrixPointer + (index * 8L));
    }

    public static void setPointer(long matrixPointer, int index, long targetPointer) {
        if (matrixPointer == 0L) throw new NullPointerException("Writing to NULL matrix pointer!");
        if (!isPointer(matrixPointer)) {
            throw new IllegalArgumentException("Expected Pointer Array (Matrix), but got Type: 0x" + Integer.toHexString(type(matrixPointer)).toUpperCase());
        }
        if (classId(matrixPointer) != CLASS_ID) throw new IllegalArgumentException("Pointer 0x" + java.lang.Long.toHexString(matrixPointer).toUpperCase() + " is Class ID " + classId(matrixPointer) + ", expected Fixed32 (Class ID " + CLASS_ID + ")");
        checkBounds(matrixPointer, index);
        ForeignMemory.set(matrixPointer + (index * 8L), targetPointer);
    }

    // --- ARCHITECTURAL CHECKS ---
    private static void checkBounds(long pointer, int index) {
        if (pointer == 0L) throw new NullPointerException("Checking bounds on NULL off-heap pointer!");
        int len = length(pointer);
        if (index < 0 || index >= len) {
            throw new IndexOutOfBoundsException("Index " + index + " out of bounds for off-heap Fixed32 length " + len + " (Ptr: 0x" + java.lang.Long.toHexString(pointer).toUpperCase() + ", Type: 0x" + Integer.toHexString(type(pointer)).toUpperCase() + ")");
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
    public static int getUnsafe(long pointer) {
        return ForeignMemory.getUnsafeInt(pointer);
    }

    @Unsafe
    public static int getUnsafe(long pointer, int index) {
        return ForeignMemory.getUnsafeInt(pointer + (index * 4L));
    }

    @Unsafe
    public static long getUnsafePointer(long matrixPointer, int index) {
        return ForeignMemory.getUnsafeLong(matrixPointer + (index * 8L));
    }

    @Unsafe
    public static void setUnsafe(long pointer, int value) {
        ForeignMemory.setUnsafe(pointer, value);
    }

    @Unsafe
    public static void setUnsafe(long pointer, int index, int value) {
        ForeignMemory.setUnsafe(pointer + (index * 4L), value);
    }

    @Unsafe
    public static void setUnsafePointer(long matrixPointer, int index, long targetPointer) {
        ForeignMemory.setUnsafe(matrixPointer + (index * 8L), targetPointer);
    }

    @Volatile
    public static int getVolatile(long pointer, int index) {
        checkBounds(pointer, index);
        return ForeignMemory.getUnsafeVolatileInt(pointer + (index * 4L));
    }

    @Volatile
    public static long getVolatilePointer(long matrixPointer, int index) {
        if(matrixPointer == 0L) throw new NullPointerException("Accessing NULL matrix pointer!");
        checkBounds(matrixPointer, index);
        return ForeignMemory.getUnsafeVolatileLong(matrixPointer + (index * 8L));
    }

    @Volatile
    public static void setVolatile(long pointer, int index, int value) {
        checkBounds(pointer, index);
        ForeignMemory.setVolatile(pointer + (index * 4L), value);
    }

    @Volatile
    public static void setVolatilePointer(long matrixPointer, int index, long targetPointer) {
        if(matrixPointer == 0L) throw new NullPointerException("Writing to NULL matrix pointer!");
        checkBounds(matrixPointer, index);
        ForeignMemory.setVolatile(matrixPointer + (index * 8L), targetPointer);
    }

    @Unsafe
    @Volatile
    public static int getUnsafeVolatile(long pointer) {
        return ForeignMemory.getUnsafeVolatileInt(pointer);
    }

    @Unsafe
    @Volatile
    public static int getUnsafeVolatile(long pointer, int index) {
        return ForeignMemory.getUnsafeVolatileInt(pointer + (index * 4L));
    }

    @Unsafe
    @Volatile
    public static long getUnsafeVolatilePointer(long matrixPointer, int index) {
        return ForeignMemory.getUnsafeVolatileLong(matrixPointer + (index * 8L));
    }

    @Unsafe
    @Volatile
    public static void setUnsafeVolatile(long pointer, int value) {
        ForeignMemory.setUnsafeVolatile(pointer, value);
    }

    @Unsafe
    @Volatile
    public static void setUnsafeVolatile(long pointer, int index, int value) {
        ForeignMemory.setUnsafeVolatile(pointer + (index * 4L), value);
    }

    @Unsafe
    @Volatile
    public static void setUnsafeVolatilePointer(long matrixPointer, int index, long targetPointer) {
        ForeignMemory.setUnsafeVolatile(matrixPointer + (index * 8L), targetPointer);
    }

}
