package primitive;

import annotation.Unsafe;
import annotation.Volatile;

import annotation.Required;
import nio.ForeignMemory;
import oop.TypeRegister;

import java.lang.foreign.Arena;
import java.lang.invoke.MethodHandles;
import java.lang.invoke.VarHandle;

public final class LongDouble {

    @Required
    public static final int CLASS_ID = TypeRegister.ID_LONG_DOUBLE;

    public static final int TYPE_SINGLETON = TypeRegister.LONG_DOUBLE_SINGLETON; // 0xAA00000B
    public static final int TYPE_ARRAY     = TypeRegister.LONG_DOUBLE_ARRAY;     // 0xBB00000B
    public static final int TYPE_MATRIX    = TypeRegister.LONG_DOUBLE_POINTER;   // 0xCC00000B

    private static final int DEFAULT_CAPACITY = 1024;

    // Memory Block Sizes (Including 8-byte headers: 4B typeId + 4B length)
    private static final long SINGLETON_SLOT_SIZE = 24L; // 8B header + 8B long + 8B double
    private static final long POOLED_ARRAY_SIZE = 8L + (DEFAULT_CAPACITY * 16L);  // 16392 Bytes
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
            SINGLETON_FREE_HEAD_VH = lookup.findStaticVarHandle(LongDouble.class, "singletonFreeHead", long.class);
            ARRAY_FREE_HEAD_VH = lookup.findStaticVarHandle(LongDouble.class, "arrayFreeHead", long.class);
            MATRIX_FREE_HEAD_VH = lookup.findStaticVarHandle(LongDouble.class, "matrixFreeHead", long.class);

            SINGLETON_EXPANDING_VH = lookup.findStaticVarHandle(LongDouble.class, "singletonExpanding", int.class);
            ARRAY_EXPANDING_VH = lookup.findStaticVarHandle(LongDouble.class, "arrayExpanding", int.class);
            MATRIX_EXPANDING_VH = lookup.findStaticVarHandle(LongDouble.class, "matrixExpanding", int.class);
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

    private LongDouble() {}

    private static void checkActive() {
        if (!active) throw new IllegalStateException("LongDouble subsystem is not active!");
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

                ForeignMemory.setUnsafe(userPtr, oldRawHead);

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

                ForeignMemory.setUnsafe(userPtr, oldRawHead);

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

                ForeignMemory.setUnsafe(userPtr, oldRawHead);

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
        long countSingletonAddr = slotBase + 0L;
        int count = ForeignMemory.getUnsafeInt(countSingletonAddr);
        if (count > 0) {
            int newCount = count - 1;
            ForeignMemory.setUnsafe(countSingletonAddr, newCount);
            long dataAddr = slotBase + 32L + (newCount * 8L);
            long ptr = ForeignMemory.getUnsafeLong(dataAddr);
            long base = ptr - 8L;
            ForeignMemory.setUnsafe(base, TYPE_SINGLETON);
            ForeignMemory.setUnsafe(base + 4L, 1);
            ForeignMemory.setUnsafe(ptr, 0L);
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
                long base = rawHead - 8L;
                ForeignMemory.setUnsafe(base, TYPE_SINGLETON);
                ForeignMemory.setUnsafe(base + 4L, 1);
                ForeignMemory.setUnsafe(rawHead, 0L);
                return rawHead;
            }
        }
    }

    public static long allocateArray(int length) {
        checkActive();
        if (length <= DEFAULT_CAPACITY) {
            int tid = thread.ThreadRegistry.getThreadIndex();
            long slotBase = CACHE_ARENA_BASE + (tid * 256L);
            long countArrayAddr = slotBase + 4L;
            int count = ForeignMemory.getUnsafeInt(countArrayAddr);
            if (count > 0) {
                int newCount = count - 1;
                ForeignMemory.setUnsafe(countArrayAddr, newCount);
                long dataAddr = slotBase + 96L + (newCount * 8L);
                long ptr = ForeignMemory.getUnsafeLong(dataAddr);
                long base = ptr - 8L;
                ForeignMemory.setUnsafe(base, TYPE_ARRAY);
                ForeignMemory.setUnsafe(base + 4L, length);
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
                    long base = rawHead - 8L;
                    ForeignMemory.setUnsafe(base, TYPE_ARRAY);
                    ForeignMemory.setUnsafe(base + 4L, length);
                    return rawHead;
                }
            }
        } else {
            // Oversized: Pure FFM C malloc downcall (0% GC)
            long totalBytes = 8L + (length * 16L);
            long alignedBytes = (totalBytes + 7L) & ~7L;
            long base = ForeignMemory.allocateNative(alignedBytes);
            ForeignMemory.setUnsafe(base, TYPE_ARRAY);
            ForeignMemory.setUnsafe(base + 4L, length);
            return base + 8L;
        }
    }

    public static long allocateMatrix(int length) {
        checkActive();
        if (length <= DEFAULT_CAPACITY) {
            int tid = thread.ThreadRegistry.getThreadIndex();
            long slotBase = CACHE_ARENA_BASE + (tid * 256L);
            long countMatrixAddr = slotBase + 8L;
            int count = ForeignMemory.getUnsafeInt(countMatrixAddr);
            if (count > 0) {
                int newCount = count - 1;
                ForeignMemory.setUnsafe(countMatrixAddr, newCount);
                long dataAddr = slotBase + 160L + (newCount * 8L);
                long ptr = ForeignMemory.getUnsafeLong(dataAddr);
                long base = ptr - 8L;
                ForeignMemory.setUnsafe(base, TYPE_MATRIX);
                ForeignMemory.setUnsafe(base + 4L, length);
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
                    long base = rawHead - 8L;
                    ForeignMemory.setUnsafe(base, TYPE_MATRIX);
                    ForeignMemory.setUnsafe(base + 4L, length);
                    return rawHead;
                }
            }
        } else {
            // Oversized: Pure FFM C malloc downcall (0% GC)
            long totalBytes = 8L + (length * 8L);
            long base = ForeignMemory.allocateNative(totalBytes);
            ForeignMemory.setUnsafe(base, TYPE_MATRIX);
            ForeignMemory.setUnsafe(base + 4L, length);
            return base + 8L;
        }
    }

    // --- MUTATING EXPANSION LAYER ---
    public static long expandArray(long oldPointer, int newLength) {
        checkActive();
        if (oldPointer == 0L) return allocateArray(newLength);
        int oldLength = length(oldPointer);
        long newPointer = allocateArray(newLength);

        int elementsToCopy = Math.min(oldLength, newLength);
        ForeignMemory.copy(oldPointer, newPointer, elementsToCopy * 16L);
        free(oldPointer);
        return newPointer;
    }

    public static long expandMatrix(long oldPointer, int newLength) {
        checkActive();
        if (oldPointer == 0L) return allocateMatrix(newLength);
        int oldLength = length(oldPointer);
        long newPointer = allocateMatrix(newLength);

        int elementsToCopy = Math.min(oldLength, newLength);
        ForeignMemory.copy(oldPointer, newPointer, elementsToCopy * 8L);
        free(oldPointer);
        return newPointer;
    }

    // --- RECYCLING LAYER ---
    public static void free(long pointer) {
        checkActive();
        if (pointer == 0L) return;

        int type = type(pointer);
        if (type == 0 || (!TypeRegister.isSingleton(type) && !TypeRegister.isArray(type) && !TypeRegister.isPointer(type))) {
            throw new IllegalStateException("Double free or corrupt off-heap pointer: 0x" + java.lang.Long.toHexString(pointer).toUpperCase());
        }

        int length = length(pointer);
        long base = pointer - 8L;

        ForeignMemory.setUnsafe(base, 0);
        ForeignMemory.setUnsafe(base + 4L, -1);

        int tid = thread.ThreadRegistry.getThreadIndex();
        long slotBase = CACHE_ARENA_BASE + (tid * 256L);

        if (TypeRegister.isSingleton(type)) {
            long countSingletonAddr = slotBase + 0L;
            int count = ForeignMemory.getUnsafeInt(countSingletonAddr);
            if (count < 8) {
                long dataAddr = slotBase + 32L + (count * 8L);
                ForeignMemory.setUnsafe(dataAddr, pointer);
                ForeignMemory.setUnsafe(countSingletonAddr, count + 1);
                return;
            }
            while (true) {
                long oldTagged = singletonFreeHead;
                long oldRawHead = oldTagged & 0x0000FFFFFFFFFFFFL;

                ForeignMemory.setUnsafe(pointer, oldRawHead);

                long nextGen = ((oldTagged >>> 48) + 1L) & 0xFFFFL;
                long newTagged = (nextGen << 48) | (pointer & 0x0000FFFFFFFFFFFFL);

                if (SINGLETON_FREE_HEAD_VH.compareAndSet(oldTagged, newTagged)) return;
            }
        } else if (TypeRegister.isArray(type)) {
            if (length > DEFAULT_CAPACITY) {
                ForeignMemory.freeNative(base);
                return;
            }
            long countArrayAddr = slotBase + 4L;
            int count = ForeignMemory.getUnsafeInt(countArrayAddr);
            if (count < 8) {
                long dataAddr = slotBase + 96L + (count * 8L);
                ForeignMemory.setUnsafe(dataAddr, pointer);
                ForeignMemory.setUnsafe(countArrayAddr, count + 1);
                return;
            }
            while (true) {
                long oldTagged = arrayFreeHead;
                long oldRawHead = oldTagged & 0x0000FFFFFFFFFFFFL;

                ForeignMemory.setUnsafe(pointer, oldRawHead);

                long nextGen = ((oldTagged >>> 48) + 1L) & 0xFFFFL;
                long newTagged = (nextGen << 48) | (pointer & 0x0000FFFFFFFFFFFFL);

                if (ARRAY_FREE_HEAD_VH.compareAndSet(oldTagged, newTagged)) return;
            }
        } else if (TypeRegister.isPointer(type)) {
            if (length > DEFAULT_CAPACITY) {
                ForeignMemory.freeNative(base);
                return;
            }
            long countMatrixAddr = slotBase + 8L;
            int count = ForeignMemory.getUnsafeInt(countMatrixAddr);
            if (count < 8) {
                long dataAddr = slotBase + 160L + (count * 8L);
                ForeignMemory.setUnsafe(dataAddr, pointer);
                ForeignMemory.setUnsafe(countMatrixAddr, count + 1);
                return;
            }
            while (true) {
                long oldTagged = matrixFreeHead;
                long oldRawHead = oldTagged & 0x0000FFFFFFFFFFFFL;

                ForeignMemory.setUnsafe(pointer, oldRawHead);

                long nextGen = ((oldTagged >>> 48) + 1L) & 0xFFFFL;
                long newTagged = (nextGen << 48) | (pointer & 0x0000FFFFFFFFFFFFL);

                if (MATRIX_FREE_HEAD_VH.compareAndSet(oldTagged, newTagged)) return;
            }
        }
    }

    // --- DATA ACCESSORS & BOUNDS CHECKS ---
    public static long getIntPart(long pointer) {
        if (pointer == 0L) throw new NullPointerException("Accessing NULL off-heap pointer!");
        return ForeignMemory.getUnsafeLong(pointer);
    }

    public static double getFracPart(long pointer) {
        if (pointer == 0L) throw new NullPointerException("Accessing NULL off-heap pointer!");
        return ForeignMemory.getUnsafeDouble(pointer + 8L);
    }

    public static double getAsDouble(long pointer) {
        if (pointer == 0L) throw new NullPointerException("Accessing NULL off-heap pointer!");
        return ForeignMemory.getUnsafeLong(pointer) + ForeignMemory.getUnsafeDouble(pointer + 8L);
    }

    public static long getIntPart(long pointer, int index) {
        checkBounds(pointer, index);
        return ForeignMemory.getUnsafeLong(pointer + (index * 16L));
    }

    public static double getFracPart(long pointer, int index) {
        checkBounds(pointer, index);
        return ForeignMemory.getUnsafeDouble(pointer + (index * 16L) + 8L);
    }

    public static double getAsDouble(long pointer, int index) {
        checkBounds(pointer, index);
        return ForeignMemory.getUnsafeLong(pointer + (index * 16L)) + ForeignMemory.getUnsafeDouble(pointer + (index * 16L) + 8L);
    }

    public static long getPointer(long matrixPointer, int index) { 
        if (matrixPointer == 0L) throw new NullPointerException("Accessing NULL matrix pointer!");
        if (!isPointer(matrixPointer)) {
            throw new IllegalArgumentException("Expected Pointer Array (Matrix), but got Type: 0x" + Integer.toHexString(type(matrixPointer)).toUpperCase());
        }
        checkBounds(matrixPointer, index);
        return ForeignMemory.getUnsafeLong(matrixPointer + (index * 8L)); 
    }

    public static void set(long pointer, long intPart, double fracPart) {
        if (pointer == 0L) throw new NullPointerException("Writing to NULL off-heap pointer!");
        if (classId(pointer) != CLASS_ID) throw new IllegalArgumentException("Pointer 0x" + java.lang.Long.toHexString(pointer).toUpperCase() + " is Class ID " + classId(pointer) + ", expected LongDouble (Class ID " + CLASS_ID + ")");
        ForeignMemory.setUnsafe(pointer, intPart);
        ForeignMemory.setUnsafe(pointer + 8L, fracPart);
    }

    public static void set(long pointer, int index, long intPart, double fracPart) {
        checkBounds(pointer, index);
        if (classId(pointer) != CLASS_ID) throw new IllegalArgumentException("Pointer 0x" + java.lang.Long.toHexString(pointer).toUpperCase() + " is Class ID " + classId(pointer) + ", expected LongDouble (Class ID " + CLASS_ID + ")");
        ForeignMemory.setUnsafe(pointer + (index * 16L), intPart);
        ForeignMemory.setUnsafe(pointer + (index * 16L) + 8L, fracPart);
    }

    public static void setPointer(long matrixPointer, int index, long targetPointer) { 
        if (matrixPointer == 0L) throw new NullPointerException("Writing to NULL matrix pointer!");
        if (!isPointer(matrixPointer)) {
            throw new IllegalArgumentException("Expected Pointer Array (Matrix), but got Type: 0x" + Integer.toHexString(type(matrixPointer)).toUpperCase());
        }
        if (classId(matrixPointer) != CLASS_ID) throw new IllegalArgumentException("Pointer 0x" + java.lang.Long.toHexString(matrixPointer).toUpperCase() + " is Class ID " + classId(matrixPointer) + ", expected LongDouble (Class ID " + CLASS_ID + ")");
        checkBounds(matrixPointer, index);
        ForeignMemory.setUnsafe(matrixPointer + (index * 8L), targetPointer); 
    }

    private static void checkBounds(long pointer, int index) {
        if (pointer == 0L) throw new NullPointerException("Checking bounds on NULL off-heap pointer!");
        int len = length(pointer);
        if (index < 0 || index >= len) {
            throw new IndexOutOfBoundsException("Index " + index + " out of bounds for off-heap LongDouble length " + len + " (Ptr: 0x" + java.lang.Long.toHexString(pointer).toUpperCase() + ", Type: 0x" + Integer.toHexString(type(pointer)).toUpperCase() + ")");
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
        return ForeignMemory.getUnsafeLong(pointer + (index * 16L));
    }

    @Unsafe
    public static double unsafeGetDoublePart(long pointer, int index) {
        return ForeignMemory.getUnsafeDouble(pointer + (index * 16L) + 8L);
    }

    @Unsafe
    public static void setUnsafe(long pointer, long val1, double val2) {
        ForeignMemory.setUnsafe(pointer, val1);
        ForeignMemory.setUnsafe(pointer + 8L, val2);
    }

    @Unsafe
    public static void setUnsafe(long pointer, int index, long val1, double val2) {
        ForeignMemory.setUnsafe(pointer + (index * 16L), val1);
        ForeignMemory.setUnsafe(pointer + (index * 16L) + 8L, val2);
    }

    @Volatile
    public static long getLongPartVolatile(long pointer, int index) {
        checkBounds(pointer, index);
        return ForeignMemory.getUnsafeVolatileLong(pointer + (index * 16L));
    }

    @Volatile
    public static double getDoublePartVolatile(long pointer, int index) {
        checkBounds(pointer, index);
        return ForeignMemory.getUnsafeVolatileDouble(pointer + (index * 16L) + 8L);
    }

    @Volatile
    public static void setVolatile(long pointer, int index, long val1, double val2) {
        checkBounds(pointer, index);
        ForeignMemory.setUnsafeVolatile(pointer + (index * 16L), val1);
        ForeignMemory.setUnsafeVolatile(pointer + (index * 16L) + 8L, val2);
    }

    @Unsafe
    @Volatile
    public static long unsafeVolatileGetLongPart(long pointer, int index) {
        return ForeignMemory.getUnsafeVolatileLong(pointer + (index * 16L));
    }

    @Unsafe
    @Volatile
    public static double unsafeVolatileGetDoublePart(long pointer, int index) {
        return ForeignMemory.getUnsafeVolatileDouble(pointer + (index * 16L) + 8L);
    }

    @Unsafe
    @Volatile
    public static void setUnsafeVolatile(long pointer, int index, long val1, double val2) {
        ForeignMemory.setUnsafeVolatile(pointer + (index * 16L), val1);
        ForeignMemory.setUnsafeVolatile(pointer + (index * 16L) + 8L, val2);
    }
}
