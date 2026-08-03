package primitive;

import annotation.Unsafe;
import annotation.Volatile;

import annotation.Required;
import nio.ForeignMemory;
import oop.TypeRegister;
import thread.ThreadRegistry;

import java.lang.foreign.Arena;
import java.lang.invoke.MethodHandles;
import java.lang.invoke.VarHandle;

public final class Float {

    @Required
    public static final int CLASS_ID = TypeRegister.ID_FLOAT;

    public static final float MAX_VALUE = java.lang.Float.MAX_VALUE;
    public static final float MIN_VALUE = java.lang.Float.MIN_VALUE;

    public static final int TYPE_SINGLETON = TypeRegister.FLOAT_SINGLETON; // 0xAA000003
    public static final int TYPE_ARRAY     = TypeRegister.FLOAT_ARRAY;     // 0xBB000003
    public static final int TYPE_MATRIX    = TypeRegister.FLOAT_POINTER;   // 0xCC000003

    
    private static final int DEFAULT_CAPACITY = 1024;
    private static final int BUCKET_8 = 8;
    private static final int BUCKET_32 = 32;
    private static final int BUCKET_128 = 128;
    private static final int BUCKET_512 = 512;

    private static final long SINGLETON_SLOT_SIZE = 16L;

    private static final VarHandle SINGLETON_FREE_HEAD_VH;
    private static final VarHandle ARRAY_FREE_HEAD_8_VH;
    private static final VarHandle ARRAY_FREE_HEAD_32_VH;
    private static final VarHandle ARRAY_FREE_HEAD_128_VH;
    private static final VarHandle ARRAY_FREE_HEAD_512_VH;
    private static final VarHandle MATRIX_FREE_HEAD_8_VH;
    private static final VarHandle MATRIX_FREE_HEAD_32_VH;
    private static final VarHandle MATRIX_FREE_HEAD_128_VH;
    private static final VarHandle MATRIX_FREE_HEAD_512_VH;

    private static final VarHandle SINGLETON_EXPANDING_VH;
    private static final VarHandle ARRAY_EXPANDING_8_VH;
    private static final VarHandle ARRAY_EXPANDING_32_VH;
    private static final VarHandle ARRAY_EXPANDING_128_VH;
    private static final VarHandle ARRAY_EXPANDING_512_VH;
    private static final VarHandle MATRIX_EXPANDING_8_VH;
    private static final VarHandle MATRIX_EXPANDING_32_VH;
    private static final VarHandle MATRIX_EXPANDING_128_VH;
    private static final VarHandle MATRIX_EXPANDING_512_VH;

    private static volatile int singletonExpanding = 0;
    private static volatile int arrayExpanding8 = 0;
    private static volatile int arrayExpanding32 = 0;
    private static volatile int arrayExpanding128 = 0;
    private static volatile int arrayExpanding512 = 0;
    private static volatile int matrixExpanding8 = 0;
    private static volatile int matrixExpanding32 = 0;
    private static volatile int matrixExpanding128 = 0;
    private static volatile int matrixExpanding512 = 0;

    private static Arena poolArena;
    private static volatile boolean active;

    private static volatile long singletonFreeHead;
    private static volatile long arrayFreeHead8;
    private static volatile long arrayFreeHead32;
    private static volatile long arrayFreeHead128;
    private static volatile long arrayFreeHead512;
    private static volatile long matrixFreeHead8;
    private static volatile long matrixFreeHead32;
    private static volatile long matrixFreeHead128;
    private static volatile long matrixFreeHead512;

    private static final long CACHE_ARENA_BASE;

    // Cache Arena Layout Offsets
    private static final long OFFSET_COUNT_SINGLETON = 0L;
    private static final long OFFSET_COUNT_ARRAY8 = 4L;
    private static final long OFFSET_COUNT_ARRAY32 = 8L;
    private static final long OFFSET_COUNT_ARRAY128 = 12L;
    private static final long OFFSET_COUNT_ARRAY512 = 16L;
    private static final long OFFSET_COUNT_MATRIX8 = 20L;
    private static final long OFFSET_COUNT_MATRIX32 = 24L;
    private static final long OFFSET_COUNT_MATRIX128 = 28L;
    private static final long OFFSET_COUNT_MATRIX512 = 32L;

    private static final long OFFSET_DATA_SINGLETON = 64L;
    private static final long OFFSET_DATA_ARRAY8 = 128L;
    private static final long OFFSET_DATA_ARRAY32 = 192L;
    private static final long OFFSET_DATA_ARRAY128 = 256L;
    private static final long OFFSET_DATA_ARRAY512 = 320L;
    private static final long OFFSET_DATA_MATRIX8 = 384L;
    private static final long OFFSET_DATA_MATRIX32 = 448L;
    private static final long OFFSET_DATA_MATRIX128 = 512L;
    private static final long OFFSET_DATA_MATRIX512 = 576L;

    static {
        try {
            MethodHandles.Lookup lookup = MethodHandles.lookup();
            SINGLETON_FREE_HEAD_VH = lookup.findStaticVarHandle(Float.class, "singletonFreeHead", long.class);
            ARRAY_FREE_HEAD_8_VH = lookup.findStaticVarHandle(Float.class, "arrayFreeHead8", long.class);
            ARRAY_FREE_HEAD_32_VH = lookup.findStaticVarHandle(Float.class, "arrayFreeHead32", long.class);
            ARRAY_FREE_HEAD_128_VH = lookup.findStaticVarHandle(Float.class, "arrayFreeHead128", long.class);
            ARRAY_FREE_HEAD_512_VH = lookup.findStaticVarHandle(Float.class, "arrayFreeHead512", long.class);
            MATRIX_FREE_HEAD_8_VH = lookup.findStaticVarHandle(Float.class, "matrixFreeHead8", long.class);
            MATRIX_FREE_HEAD_32_VH = lookup.findStaticVarHandle(Float.class, "matrixFreeHead32", long.class);
            MATRIX_FREE_HEAD_128_VH = lookup.findStaticVarHandle(Float.class, "matrixFreeHead128", long.class);
            MATRIX_FREE_HEAD_512_VH = lookup.findStaticVarHandle(Float.class, "matrixFreeHead512", long.class);

            SINGLETON_EXPANDING_VH = lookup.findStaticVarHandle(Float.class, "singletonExpanding", int.class);
            ARRAY_EXPANDING_8_VH = lookup.findStaticVarHandle(Float.class, "arrayExpanding8", int.class);
            ARRAY_EXPANDING_32_VH = lookup.findStaticVarHandle(Float.class, "arrayExpanding32", int.class);
            ARRAY_EXPANDING_128_VH = lookup.findStaticVarHandle(Float.class, "arrayExpanding128", int.class);
            ARRAY_EXPANDING_512_VH = lookup.findStaticVarHandle(Float.class, "arrayExpanding512", int.class);
            MATRIX_EXPANDING_8_VH = lookup.findStaticVarHandle(Float.class, "matrixExpanding8", int.class);
            MATRIX_EXPANDING_32_VH = lookup.findStaticVarHandle(Float.class, "matrixExpanding32", int.class);
            MATRIX_EXPANDING_128_VH = lookup.findStaticVarHandle(Float.class, "matrixExpanding128", int.class);
            MATRIX_EXPANDING_512_VH = lookup.findStaticVarHandle(Float.class, "matrixExpanding512", int.class);
        }
        catch(ReflectiveOperationException e) {
            throw new ExceptionInInitializerError(e);
        }

        poolArena = Arena.ofShared();
        active = true;

        CACHE_ARENA_BASE = ForeignMemory.allocateNative(256 * 1024L);
        for (long i = 0; i < 256 * 1024L; i += 8L) {
            ForeignMemory.set(CACHE_ARENA_BASE + i, 0L);
        }

        expandSingletonPool();
        expandArrayPool(BUCKET_8, ARRAY_FREE_HEAD_8_VH);
        expandArrayPool(BUCKET_32, ARRAY_FREE_HEAD_32_VH);
        expandArrayPool(BUCKET_128, ARRAY_FREE_HEAD_128_VH);
        expandArrayPool(BUCKET_512, ARRAY_FREE_HEAD_512_VH);
        expandMatrixPool(BUCKET_8, MATRIX_FREE_HEAD_8_VH);
        expandMatrixPool(BUCKET_32, MATRIX_FREE_HEAD_32_VH);
        expandMatrixPool(BUCKET_128, MATRIX_FREE_HEAD_128_VH);
        expandMatrixPool(BUCKET_512, MATRIX_FREE_HEAD_512_VH);
    }

    private Float()
    {
    }

    private static void checkActive()
    {
        if(!active) throw new IllegalStateException("Float subsystem is not active!");
    }

    private static long getThreadSlotBase()
    {
        int threadIdx = ThreadRegistry.getThreadIndex();
        return CACHE_ARENA_BASE + (threadIdx * 1024L);
    }

    public static void freeAll()
    {
        if(active) {
            active = false;
            if(poolArena != null && poolArena.scope().isAlive()) poolArena.close();
            if(CACHE_ARENA_BASE != 0L) {
                ForeignMemory.freeNative(CACHE_ARENA_BASE);
            }
        }
    }

    private static void expandSingletonPool()
    {
        long totalBytes = DEFAULT_CAPACITY * SINGLETON_SLOT_SIZE;
        long baseAddress = poolArena.allocate(totalBytes, 8).address();

        for(int i = 0; i < DEFAULT_CAPACITY; i++) {
            long currentBlock = baseAddress + (i * SINGLETON_SLOT_SIZE);
            long userPtr = currentBlock + 8L;

            while(true) {
                long oldTagged = singletonFreeHead;
                long oldRawHead = oldTagged & 0x0000FFFFFFFFFFFFL;

                ForeignMemory.set(userPtr, oldRawHead);

                long nextGen = ((oldTagged >>> 48) + 1L) & 0xFFFFL;
                long newTagged = (nextGen << 48) | (userPtr & 0x0000FFFFFFFFFFFFL);

                if(SINGLETON_FREE_HEAD_VH.compareAndSet(oldTagged, newTagged)) break;
            }
        }
    }

    private static void expandArrayPool(int bucketSize, VarHandle freeHeadVH)
    {
        long slotSize = 8L + (bucketSize * 4L);
        long totalBytes = DEFAULT_CAPACITY * slotSize;
        long baseAddress = poolArena.allocate(totalBytes, 8).address();

        for(int i = 0; i < DEFAULT_CAPACITY; i++) {
            long currentBlock = baseAddress + (i * slotSize);
            long userPtr = currentBlock + 8L;

            while(true) {
                long oldTagged = (long) freeHeadVH.getVolatile();
                long oldRawHead = oldTagged & 0x0000FFFFFFFFFFFFL;

                ForeignMemory.set(userPtr, oldRawHead);

                long nextGen = ((oldTagged >>> 48) + 1L) & 0xFFFFL;
                long newTagged = (nextGen << 48) | (userPtr & 0x0000FFFFFFFFFFFFL);

                if(freeHeadVH.compareAndSet(oldTagged, newTagged)) break;
            }
        }
    }

    private static void expandMatrixPool(int bucketSize, VarHandle freeHeadVH)
    {
        long slotSize = 8L + (bucketSize * 8L);
        long totalBytes = DEFAULT_CAPACITY * slotSize;
        long baseAddress = poolArena.allocate(totalBytes, 8).address();

        for(int i = 0; i < DEFAULT_CAPACITY; i++) {
            long currentBlock = baseAddress + (i * slotSize);
            long userPtr = currentBlock + 8L;

            while(true) {
                long oldTagged = (long) freeHeadVH.getVolatile();
                long oldRawHead = oldTagged & 0x0000FFFFFFFFFFFFL;

                ForeignMemory.set(userPtr, oldRawHead);

                long nextGen = ((oldTagged >>> 48) + 1L) & 0xFFFFL;
                long newTagged = (nextGen << 48) | (userPtr & 0x0000FFFFFFFFFFFFL);

                if(freeHeadVH.compareAndSet(oldTagged, newTagged)) break;
            }
        }
    }

    public static long allocateSingleton()
    {
        checkActive();
        int threadIdx = ThreadRegistry.getThreadIndex();
        long slotBase = CACHE_ARENA_BASE + (threadIdx * 1024L);
        int count = ForeignMemory.unsafeGetInt(slotBase + OFFSET_COUNT_SINGLETON);
        if (count > 0) {
            int newCount = count - 1;
            long ptr = ForeignMemory.unsafeGetLong(slotBase + OFFSET_DATA_SINGLETON + (newCount * 8L));
            ForeignMemory.unsafeSet(slotBase + OFFSET_COUNT_SINGLETON, newCount);
            long base = ptr - 8L;
            ForeignMemory.unsafeSet(base, TYPE_SINGLETON);
            ForeignMemory.unsafeSet(base + 4L, 1);
            ForeignMemory.set(ptr, 0L);
            return ptr;
        }

        while(true) {
            long oldTagged = singletonFreeHead;
            long rawHead = oldTagged & 0x0000FFFFFFFFFFFFL;

            if(rawHead == 0L) {
                if(SINGLETON_EXPANDING_VH.compareAndSet(0, 1)) {
                    expandSingletonPool();
                    SINGLETON_EXPANDING_VH.setVolatile(0);
                }
                else Thread.onSpinWait();
                continue;
            }

            long nextRawHead = ForeignMemory.unsafeGetLong(rawHead);
            long nextGen = ((oldTagged >>> 48) + 1L) & 0xFFFFL;
            long newTagged = (nextGen << 48) | (nextRawHead & 0x0000FFFFFFFFFFFFL);

            if(SINGLETON_FREE_HEAD_VH.compareAndSet(oldTagged, newTagged)) {
                long base = rawHead - 8L;
                ForeignMemory.unsafeSet(base, TYPE_SINGLETON);
                ForeignMemory.unsafeSet(base + 4L, 1);
                ForeignMemory.set(rawHead, 0L);
                return rawHead;
            }
        }
    }

    public static long allocateArray(int length)
    {
        checkActive();
        if (length <= BUCKET_512) {
            int threadIdx = ThreadRegistry.getThreadIndex();
            long slotBase = CACHE_ARENA_BASE + (threadIdx * 1024L);
            long countOffset;
            long dataOffset;
            if (length <= BUCKET_8) {
                countOffset = OFFSET_COUNT_ARRAY8;
                dataOffset = OFFSET_DATA_ARRAY8;
            } else if (length <= BUCKET_32) {
                countOffset = OFFSET_COUNT_ARRAY32;
                dataOffset = OFFSET_DATA_ARRAY32;
            } else if (length <= BUCKET_128) {
                countOffset = OFFSET_COUNT_ARRAY128;
                dataOffset = OFFSET_DATA_ARRAY128;
            } else {
                countOffset = OFFSET_COUNT_ARRAY512;
                dataOffset = OFFSET_DATA_ARRAY512;
            }
            int count = ForeignMemory.unsafeGetInt(slotBase + countOffset);
            if (count > 0) {
                int newCount = count - 1;
                long ptr = ForeignMemory.unsafeGetLong(slotBase + dataOffset + (newCount * 8L));
                ForeignMemory.unsafeSet(slotBase + countOffset, newCount);
                long base = ptr - 8L;
                ForeignMemory.unsafeSet(base, TYPE_ARRAY);
                ForeignMemory.unsafeSet(base + 4L, length);
                return ptr;
            }
        }

        VarHandle headVH;
        VarHandle expandingVH;
        int bucketSize;

        if(length <= BUCKET_8) {
            headVH = ARRAY_FREE_HEAD_8_VH;
            expandingVH = ARRAY_EXPANDING_8_VH;
            bucketSize = BUCKET_8;
        }
        else if(length <= BUCKET_32) {
            headVH = ARRAY_FREE_HEAD_32_VH;
            expandingVH = ARRAY_EXPANDING_32_VH;
            bucketSize = BUCKET_32;
        }
        else if(length <= BUCKET_128) {
            headVH = ARRAY_FREE_HEAD_128_VH;
            expandingVH = ARRAY_EXPANDING_128_VH;
            bucketSize = BUCKET_128;
        }
        else if(length <= BUCKET_512) {
            headVH = ARRAY_FREE_HEAD_512_VH;
            expandingVH = ARRAY_EXPANDING_512_VH;
            bucketSize = BUCKET_512;
        }
        else {
            long totalBytes = 8L + (length * 4L);
            long alignedBytes = (totalBytes + 7L) & ~7L;
            long base = ForeignMemory.allocateNative(alignedBytes);
            ForeignMemory.unsafeSet(base, TYPE_ARRAY);
            ForeignMemory.unsafeSet(base + 4L, length);
            return base + 8L;
        }

        while(true) {
            long oldTagged = (long) headVH.getVolatile();
            long rawHead = oldTagged & 0x0000FFFFFFFFFFFFL;

            if(rawHead == 0L) {
                if(expandingVH.compareAndSet(0, 1)) {
                    expandArrayPool(bucketSize, headVH);
                    expandingVH.setVolatile(0);
                }
                else Thread.onSpinWait();
                continue;
            }

            long nextRawHead = ForeignMemory.unsafeGetLong(rawHead);
            long nextGen = ((oldTagged >>> 48) + 1L) & 0xFFFFL;
            long newTagged = (nextGen << 48) | (nextRawHead & 0x0000FFFFFFFFFFFFL);

            if(headVH.compareAndSet(oldTagged, newTagged)) {
                long base = rawHead - 8L;
                ForeignMemory.unsafeSet(base, TYPE_ARRAY);
                ForeignMemory.unsafeSet(base + 4L, length);
                return rawHead;
            }
        }
    }

    public static long allocateMatrix(int length)
    {
        checkActive();
        if (length <= BUCKET_512) {
            int threadIdx = ThreadRegistry.getThreadIndex();
            long slotBase = CACHE_ARENA_BASE + (threadIdx * 1024L);
            long countOffset;
            long dataOffset;
            if (length <= BUCKET_8) {
                countOffset = OFFSET_COUNT_MATRIX8;
                dataOffset = OFFSET_DATA_MATRIX8;
            } else if (length <= BUCKET_32) {
                countOffset = OFFSET_COUNT_MATRIX32;
                dataOffset = OFFSET_DATA_MATRIX32;
            } else if (length <= BUCKET_128) {
                countOffset = OFFSET_COUNT_MATRIX128;
                dataOffset = OFFSET_DATA_MATRIX128;
            } else {
                countOffset = OFFSET_COUNT_MATRIX512;
                dataOffset = OFFSET_DATA_MATRIX512;
            }
            int count = ForeignMemory.unsafeGetInt(slotBase + countOffset);
            if (count > 0) {
                int newCount = count - 1;
                long ptr = ForeignMemory.unsafeGetLong(slotBase + dataOffset + (newCount * 8L));
                ForeignMemory.unsafeSet(slotBase + countOffset, newCount);
                long base = ptr - 8L;
                ForeignMemory.unsafeSet(base, TYPE_MATRIX);
                ForeignMemory.unsafeSet(base + 4L, length);
                return ptr;
            }
        }

        VarHandle headVH;
        VarHandle expandingVH;
        int bucketSize;

        if(length <= BUCKET_8) {
            headVH = MATRIX_FREE_HEAD_8_VH;
            expandingVH = MATRIX_EXPANDING_8_VH;
            bucketSize = BUCKET_8;
        }
        else if(length <= BUCKET_32) {
            headVH = MATRIX_FREE_HEAD_32_VH;
            expandingVH = MATRIX_EXPANDING_32_VH;
            bucketSize = BUCKET_32;
        }
        else if(length <= BUCKET_128) {
            headVH = MATRIX_FREE_HEAD_128_VH;
            expandingVH = MATRIX_EXPANDING_128_VH;
            bucketSize = BUCKET_128;
        }
        else if(length <= BUCKET_512) {
            headVH = MATRIX_FREE_HEAD_512_VH;
            expandingVH = MATRIX_EXPANDING_512_VH;
            bucketSize = BUCKET_512;
        }
        else {
            long totalBytes = 8L + (length * 8L);
            long base = ForeignMemory.allocateNative(totalBytes);
            ForeignMemory.unsafeSet(base, TYPE_MATRIX);
            ForeignMemory.unsafeSet(base + 4L, length);
            return base + 8L;
        }

        while(true) {
            long oldTagged = (long) headVH.getVolatile();
            long rawHead = oldTagged & 0x0000FFFFFFFFFFFFL;

            if(rawHead == 0L) {
                if(expandingVH.compareAndSet(0, 1)) {
                    expandMatrixPool(bucketSize, headVH);
                    expandingVH.setVolatile(0);
                }
                else Thread.onSpinWait();
                continue;
            }

            long nextRawHead = ForeignMemory.unsafeGetLong(rawHead);
            long nextGen = ((oldTagged >>> 48) + 1L) & 0xFFFFL;
            long newTagged = (nextGen << 48) | (nextRawHead & 0x0000FFFFFFFFFFFFL);

            if(headVH.compareAndSet(oldTagged, newTagged)) {
                long base = rawHead - 8L;
                ForeignMemory.unsafeSet(base, TYPE_MATRIX);
                ForeignMemory.unsafeSet(base + 4L, length);
                return rawHead;
            }
        }
    }

    public static long expandArray(long oldPointer, int newLength)
    {
        checkActive();
        if(oldPointer == 0L) return allocateArray(newLength);
        int oldLength = length(oldPointer);
        long newPointer = allocateArray(newLength);

        int elementsToCopy = Math.min(oldLength, newLength);
        ForeignMemory.copy(oldPointer, newPointer, (elementsToCopy * 4L));
        free(oldPointer);
        return newPointer;
    }

    public static long expandMatrix(long oldPointer, int newLength)
    {
        checkActive();
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
        checkActive();
        if(pointer == 0L) return;

        int type = type(pointer);
        if(type == 0 || (!TypeRegister.isSingleton(type) && !TypeRegister.isArray(type) && !TypeRegister.isPointer(type))) throw new IllegalStateException("Double free or corrupt off-heap pointer: 0x" + java.lang.Long.toHexString(pointer).toUpperCase());

        int length = length(pointer);
        long base = pointer - 8L;

        // Try caching in the Thread-Local Cache Arena
        int threadIdx = ThreadRegistry.getThreadIndex();
        long slotBase = CACHE_ARENA_BASE + (threadIdx * 1024L);

        if (TypeRegister.isSingleton(type)) {
            int count = ForeignMemory.unsafeGetInt(slotBase + OFFSET_COUNT_SINGLETON);
            if (count < 8) {
                ForeignMemory.unsafeSet(base, 0);
                ForeignMemory.unsafeSet(base + 4L, -1);
                ForeignMemory.set(slotBase + OFFSET_DATA_SINGLETON + (count * 8L), pointer);
                ForeignMemory.unsafeSet(slotBase + OFFSET_COUNT_SINGLETON, count + 1);
                return;
            }
        }
        else if (TypeRegister.isArray(type)) {
            long countOffset = 0L;
            long dataOffset = 0L;
            boolean cached = false;
            if (length <= BUCKET_8) {
                countOffset = OFFSET_COUNT_ARRAY8;
                dataOffset = OFFSET_DATA_ARRAY8;
                cached = true;
            } else if (length <= BUCKET_32) {
                countOffset = OFFSET_COUNT_ARRAY32;
                dataOffset = OFFSET_DATA_ARRAY32;
                cached = true;
            } else if (length <= BUCKET_128) {
                countOffset = OFFSET_COUNT_ARRAY128;
                dataOffset = OFFSET_DATA_ARRAY128;
                cached = true;
            } else if (length <= BUCKET_512) {
                countOffset = OFFSET_COUNT_ARRAY512;
                dataOffset = OFFSET_DATA_ARRAY512;
                cached = true;
            }

            if (cached) {
                int count = ForeignMemory.unsafeGetInt(slotBase + countOffset);
                if (count < 8) {
                    ForeignMemory.unsafeSet(base, 0);
                    ForeignMemory.unsafeSet(base + 4L, -1);
                    ForeignMemory.set(slotBase + dataOffset + (count * 8L), pointer);
                    ForeignMemory.unsafeSet(slotBase + countOffset, count + 1);
                    return;
                }
            }
        }
        else if (TypeRegister.isPointer(type)) {
            long countOffset = 0L;
            long dataOffset = 0L;
            boolean cached = false;
            if (length <= BUCKET_8) {
                countOffset = OFFSET_COUNT_MATRIX8;
                dataOffset = OFFSET_DATA_MATRIX8;
                cached = true;
            } else if (length <= BUCKET_32) {
                countOffset = OFFSET_COUNT_MATRIX32;
                dataOffset = OFFSET_DATA_MATRIX32;
                cached = true;
            } else if (length <= BUCKET_128) {
                countOffset = OFFSET_COUNT_MATRIX128;
                dataOffset = OFFSET_DATA_MATRIX128;
                cached = true;
            } else if (length <= BUCKET_512) {
                countOffset = OFFSET_COUNT_MATRIX512;
                dataOffset = OFFSET_DATA_MATRIX512;
                cached = true;
            }

            if (cached) {
                int count = ForeignMemory.unsafeGetInt(slotBase + countOffset);
                if (count < 8) {
                    ForeignMemory.unsafeSet(base, 0);
                    ForeignMemory.unsafeSet(base + 4L, -1);
                    ForeignMemory.set(slotBase + dataOffset + (count * 8L), pointer);
                    ForeignMemory.unsafeSet(slotBase + countOffset, count + 1);
                    return;
                }
            }
        }

        // Fallback to global pool
        ForeignMemory.unsafeSet(base, 0);
        ForeignMemory.unsafeSet(base + 4L, -1);

        if(TypeRegister.isSingleton(type)) {
            while(true) {
                long oldTagged = singletonFreeHead;
                long oldRawHead = oldTagged & 0x0000FFFFFFFFFFFFL;

                ForeignMemory.set(pointer, oldRawHead);

                long nextGen = ((oldTagged >>> 48) + 1L) & 0xFFFFL;
                long newTagged = (nextGen << 48) | (pointer & 0x0000FFFFFFFFFFFFL);

                if(SINGLETON_FREE_HEAD_VH.compareAndSet(oldTagged, newTagged)) return;
            }
        }
        else if(TypeRegister.isArray(type)) {
            VarHandle headVH;
            if(length <= BUCKET_8) headVH = ARRAY_FREE_HEAD_8_VH;
            else if(length <= BUCKET_32) headVH = ARRAY_FREE_HEAD_32_VH;
            else if(length <= BUCKET_128) headVH = ARRAY_FREE_HEAD_128_VH;
            else if(length <= BUCKET_512) headVH = ARRAY_FREE_HEAD_512_VH;
            else {
                ForeignMemory.freeNative(base);
                return;
            }
            while(true) {
                long oldTagged = (long) headVH.getVolatile();
                long oldRawHead = oldTagged & 0x0000FFFFFFFFFFFFL;

                ForeignMemory.set(pointer, oldRawHead);

                long nextGen = ((oldTagged >>> 48) + 1L) & 0xFFFFL;
                long newTagged = (nextGen << 48) | (pointer & 0x0000FFFFFFFFFFFFL);

                if(headVH.compareAndSet(oldTagged, newTagged)) return;
            }
        }
        else if(TypeRegister.isPointer(type)) {
            VarHandle headVH;
            if(length <= BUCKET_8) headVH = MATRIX_FREE_HEAD_8_VH;
            else if(length <= BUCKET_32) headVH = MATRIX_FREE_HEAD_32_VH;
            else if(length <= BUCKET_128) headVH = MATRIX_FREE_HEAD_128_VH;
            else if(length <= BUCKET_512) headVH = MATRIX_FREE_HEAD_512_VH;
            else {
                ForeignMemory.freeNative(base);
                return;
            }
            while(true) {
                long oldTagged = (long) headVH.getVolatile();
                long oldRawHead = oldTagged & 0x0000FFFFFFFFFFFFL;

                ForeignMemory.set(pointer, oldRawHead);

                long nextGen = ((oldTagged >>> 48) + 1L) & 0xFFFFL;
                long newTagged = (nextGen << 48) | (pointer & 0x0000FFFFFFFFFFFFL);

                if(headVH.compareAndSet(oldTagged, newTagged)) return;
            }
        }
    }
    public static float get(long pointer) {
        if (pointer == 0L) throw new NullPointerException("Accessing NULL off-heap pointer!");
        if(classId(pointer) != CLASS_ID) throw new IllegalArgumentException("Pointer 0x" + java.lang.Long.toHexString(pointer).toUpperCase() + " is Class ID " + classId(pointer) + ", expected Float (Class ID " + CLASS_ID + ")");
        return ForeignMemory.unsafeGetFloat(pointer);
    }

    public static float get(long pointer, int index) { 
        checkBounds(pointer, index);
        if(classId(pointer) != CLASS_ID) throw new IllegalArgumentException("Pointer 0x" + java.lang.Long.toHexString(pointer).toUpperCase() + " is Class ID " + classId(pointer) + ", expected Float (Class ID " + CLASS_ID + ")");
        return ForeignMemory.unsafeGetFloat(pointer + (index * 4L)); 
    }

    public static long getPointer(long matrixPointer, int index) { 
        if (matrixPointer == 0L) throw new NullPointerException("Accessing NULL matrix pointer!");
        if(!isPointer(matrixPointer)) throw new IllegalArgumentException("Expected Pointer Array (Matrix), but got Type: 0x" + Integer.toHexString(type(matrixPointer)).toUpperCase());
        if(classId(matrixPointer) != CLASS_ID) throw new IllegalArgumentException("Pointer 0x" + java.lang.Long.toHexString(matrixPointer).toUpperCase() + " is Class ID " + classId(matrixPointer) + ", expected Float (Class ID " + CLASS_ID + ")");
        checkBounds(matrixPointer, index);
        return ForeignMemory.unsafeGetLong(matrixPointer + (index * 8L)); 
    }

    public static void set(long pointer, float value) {
        if (pointer == 0L) throw new NullPointerException("Writing to NULL off-heap pointer!");
        if(classId(pointer) != CLASS_ID) throw new IllegalArgumentException("Pointer 0x" + java.lang.Long.toHexString(pointer).toUpperCase() + " is Class ID " + classId(pointer) + ", expected Float (Class ID " + CLASS_ID + ")");
        ForeignMemory.set(pointer, value);
    }

    public static void set(long pointer, int index, float value) { 
        checkBounds(pointer, index);
        if(classId(pointer) != CLASS_ID) throw new IllegalArgumentException("Pointer 0x" + java.lang.Long.toHexString(pointer).toUpperCase() + " is Class ID " + classId(pointer) + ", expected Float (Class ID " + CLASS_ID + ")");
        ForeignMemory.set(pointer + (index * 4L), value); 
    }

    @Volatile
    public static float getVolatile(long pointer) {
        if (pointer == 0L) throw new NullPointerException("Reading from NULL off-heap pointer!");
        if(classId(pointer) != CLASS_ID) throw new IllegalArgumentException("Pointer 0x" + java.lang.Long.toHexString(pointer).toUpperCase() + " is Class ID " + classId(pointer) + ", expected Float (Class ID " + CLASS_ID + ")");
        return ForeignMemory.unsafeGetFloatVolatile(pointer);
    }

    @Volatile
    public static void setVolatile(long pointer, float value) {
        if (pointer == 0L) throw new NullPointerException("Writing to NULL off-heap pointer!");
        if(classId(pointer) != CLASS_ID) throw new IllegalArgumentException("Pointer 0x" + java.lang.Long.toHexString(pointer).toUpperCase() + " is Class ID " + classId(pointer) + ", expected Float (Class ID " + CLASS_ID + ")");
        ForeignMemory.setVolatile(pointer, value);
    }

    public static boolean compareAndSet(long pointer, float expected, float value) {
        if (pointer == 0L) throw new NullPointerException("Writing to NULL off-heap pointer!");
        if(classId(pointer) != CLASS_ID) throw new IllegalArgumentException("Pointer 0x" + java.lang.Long.toHexString(pointer).toUpperCase() + " is Class ID " + classId(pointer) + ", expected Float (Class ID " + CLASS_ID + ")");
        return ForeignMemory.compareAndSetFloat(pointer, expected, value);
    }

    public static void setPointer(long matrixPointer, int index, long targetPointer) { 
        if (matrixPointer == 0L) throw new NullPointerException("Writing to NULL matrix pointer!");
        if(!isPointer(matrixPointer)) throw new IllegalArgumentException("Expected Pointer Array (Matrix), but got Type: 0x" + Integer.toHexString(type(matrixPointer)).toUpperCase());
        if(classId(matrixPointer) != CLASS_ID) throw new IllegalArgumentException("Pointer 0x" + java.lang.Long.toHexString(matrixPointer).toUpperCase() + " is Class ID " + classId(matrixPointer) + ", expected Float (Class ID " + CLASS_ID + ")");
        checkBounds(matrixPointer, index);
        ForeignMemory.set(matrixPointer + (index * 8L), targetPointer); 
    }

    private static void checkBounds(long pointer, int index) {
        if (pointer == 0L) throw new NullPointerException("Checking bounds on NULL off-heap pointer!");
        int len = length(pointer);
        if(index < 0 || index >= len) throw new IndexOutOfBoundsException("Index " + index + " out of bounds for off-heap length " + len + " (Ptr: 0x" + java.lang.Long.toHexString(pointer).toUpperCase() + ", Type: 0x" + Integer.toHexString(type(pointer)).toUpperCase() + ")");
    }

    public static int classId() {
        return CLASS_ID;
    }

    public static int type(long pointer) {
        return ForeignMemory.unsafeGetInt(pointer - 8L);
    }

    public static int length(long pointer) {
        return ForeignMemory.unsafeGetInt(pointer - 4L);
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
    public static float unsafeGet(long pointer) {
        return ForeignMemory.unsafeGetFloat(pointer);
    }

    @Unsafe
    public static float unsafeGet(long pointer, int index) {
        return ForeignMemory.unsafeGetFloat(pointer + (index * 4L));
    }

    @Unsafe
    public static long unsafeGetPointer(long matrixPointer, int index) {
        return ForeignMemory.unsafeGetLong(matrixPointer + (index * 8L));
    }

    @Unsafe
    public static void unsafeSet(long pointer, float value) {
        ForeignMemory.unsafeSet(pointer, value);
    }

    @Unsafe
    public static void unsafeSet(long pointer, int index, float value) {
        ForeignMemory.unsafeSet(pointer + (index * 4L), value);
    }

    @Unsafe
    public static void unsafeSetPointer(long matrixPointer, int index, long targetPointer) {
        ForeignMemory.unsafeSet(matrixPointer + (index * 8L), targetPointer);
    }

    @Volatile
    public static float getVolatile(long pointer, int index) {
        checkBounds(pointer, index);
        if(classId(pointer) != CLASS_ID) throw new IllegalArgumentException("Pointer 0x" + java.lang.Long.toHexString(pointer).toUpperCase() + " is Class ID " + classId(pointer) + ", expected Float (Class ID " + CLASS_ID + ")");
        return ForeignMemory.unsafeGetFloatVolatile(pointer + (index * 4L));
    }

    @Volatile
    public static long getPointerVolatile(long matrixPointer, int index) {
        if(matrixPointer == 0L) throw new NullPointerException("Accessing NULL matrix pointer!");
        if(!isPointer(matrixPointer)) throw new IllegalArgumentException("Expected Pointer Array (Matrix), but got Type: 0x" + Integer.toHexString(type(matrixPointer)).toUpperCase());
        if(classId(matrixPointer) != CLASS_ID) throw new IllegalArgumentException("Pointer 0x" + java.lang.Long.toHexString(matrixPointer).toUpperCase() + " is Class ID " + classId(matrixPointer) + ", expected Float (Class ID " + CLASS_ID + ")");
        checkBounds(matrixPointer, index);
        return ForeignMemory.unsafeGetLongVolatile(matrixPointer + (index * 8L));
    }

    @Volatile
    public static void setVolatile(long pointer, int index, float value) {
        checkBounds(pointer, index);
        if(classId(pointer) != CLASS_ID) throw new IllegalArgumentException("Pointer 0x" + java.lang.Long.toHexString(pointer).toUpperCase() + " is Class ID " + classId(pointer) + ", expected Float (Class ID " + CLASS_ID + ")");
        ForeignMemory.setVolatile(pointer + (index * 4L), value);
    }

    @Volatile
    public static void setPointerVolatile(long matrixPointer, int index, long targetPointer) {
        if(matrixPointer == 0L) throw new NullPointerException("Writing to NULL matrix pointer!");
        if(!isPointer(matrixPointer)) throw new IllegalArgumentException("Expected Pointer Array (Matrix), but got Type: 0x" + Integer.toHexString(type(matrixPointer)).toUpperCase());
        if(classId(matrixPointer) != CLASS_ID) throw new IllegalArgumentException("Pointer 0x" + java.lang.Long.toHexString(matrixPointer).toUpperCase() + " is Class ID " + classId(matrixPointer) + ", expected Float (Class ID " + CLASS_ID + ")");
        checkBounds(matrixPointer, index);
        ForeignMemory.setVolatile(matrixPointer + (index * 8L), targetPointer);
    }

    @Unsafe
    @Volatile
    public static float unsafeVolatileGet(long pointer) {
        return ForeignMemory.unsafeGetFloatVolatile(pointer);
    }

    @Unsafe
    @Volatile
    public static float unsafeVolatileGet(long pointer, int index) {
        return ForeignMemory.unsafeGetFloatVolatile(pointer + (index * 4L));
    }

    @Unsafe
    @Volatile
    public static long unsafeVolatileGetPointer(long matrixPointer, int index) {
        return ForeignMemory.unsafeGetLongVolatile(matrixPointer + (index * 8L));
    }

    @Unsafe
    @Volatile
    public static void unsafeVolatileSet(long pointer, float value) {
        ForeignMemory.unsafeVolatileSet(pointer, value);
    }

    @Unsafe
    @Volatile
    public static void unsafeVolatileSet(long pointer, int index, float value) {
        ForeignMemory.unsafeVolatileSet(pointer + (index * 4L), value);
    }

    @Unsafe
    @Volatile
    public static void unsafeVolatileSetPointer(long matrixPointer, int index, long targetPointer) {
        ForeignMemory.unsafeVolatileSet(matrixPointer + (index * 8L), targetPointer);
    }

}
