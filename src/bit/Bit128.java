package bit;

import annotation.HotCode;
import annotation.Intention;
import annotation.Required;
import annotation.Volatile;

import nio.ForeignMemory;
import oop.TypeRegister;
import thread.ThreadRegistry;

import java.lang.foreign.Arena;
import java.lang.invoke.MethodHandles;
import java.lang.invoke.VarHandle;

@Required
@Volatile
@HotCode
@Intention("Bit-width memory pool: decouples physical 128-bit allocation from type semantics. the 128-bit primitives delegate here, sharing one lockless pool for cross-type slot recycling.")
public final class Bit128
{
    // --- SLOT WIDTHS ---
    public static final int ELEMENT_SIZE = 16; // bytes per element (Bit128: IntFloat, IntDouble, LongFloat, LongDouble, Pack)
    public static final long SINGLETON_SLOT_SIZE = 8L + (ELEMENT_SIZE >= 8L ? ELEMENT_SIZE : 8L); // 8B header + payload, 8-byte aligned
    public static final int BUCKET_8 = 8;
    public static final int BUCKET_32 = 32;
    public static final int BUCKET_128 = 128;
    public static final int BUCKET_512 = 512;

    private static final int DEFAULT_CAPACITY = 1024;

    // --- 18 STATIC VarHandleS: 9 ABA-tagged free-list heads + 9 expansion guards ---
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
    private static final long CACHE_ARENA_BASE;

    // Top 16 bits = 16-bit Generation Tag, Bottom 48 bits = Raw Memory Pointer
    private static volatile long singletonFreeHead;
    private static volatile long arrayFreeHead8;
    private static volatile long arrayFreeHead32;
    private static volatile long arrayFreeHead128;
    private static volatile long arrayFreeHead512;
    private static volatile long matrixFreeHead8;
    private static volatile long matrixFreeHead32;
    private static volatile long matrixFreeHead128;
    private static volatile long matrixFreeHead512;

    static {
        try {
            MethodHandles.Lookup lookup = MethodHandles.lookup();
            SINGLETON_FREE_HEAD_VH = lookup.findStaticVarHandle(Bit128.class, "singletonFreeHead", long.class);
            ARRAY_FREE_HEAD_8_VH = lookup.findStaticVarHandle(Bit128.class, "arrayFreeHead8", long.class);
            ARRAY_FREE_HEAD_32_VH = lookup.findStaticVarHandle(Bit128.class, "arrayFreeHead32", long.class);
            ARRAY_FREE_HEAD_128_VH = lookup.findStaticVarHandle(Bit128.class, "arrayFreeHead128", long.class);
            ARRAY_FREE_HEAD_512_VH = lookup.findStaticVarHandle(Bit128.class, "arrayFreeHead512", long.class);
            MATRIX_FREE_HEAD_8_VH = lookup.findStaticVarHandle(Bit128.class, "matrixFreeHead8", long.class);
            MATRIX_FREE_HEAD_32_VH = lookup.findStaticVarHandle(Bit128.class, "matrixFreeHead32", long.class);
            MATRIX_FREE_HEAD_128_VH = lookup.findStaticVarHandle(Bit128.class, "matrixFreeHead128", long.class);
            MATRIX_FREE_HEAD_512_VH = lookup.findStaticVarHandle(Bit128.class, "matrixFreeHead512", long.class);

            SINGLETON_EXPANDING_VH = lookup.findStaticVarHandle(Bit128.class, "singletonExpanding", int.class);
            ARRAY_EXPANDING_8_VH = lookup.findStaticVarHandle(Bit128.class, "arrayExpanding8", int.class);
            ARRAY_EXPANDING_32_VH = lookup.findStaticVarHandle(Bit128.class, "arrayExpanding32", int.class);
            ARRAY_EXPANDING_128_VH = lookup.findStaticVarHandle(Bit128.class, "arrayExpanding128", int.class);
            ARRAY_EXPANDING_512_VH = lookup.findStaticVarHandle(Bit128.class, "arrayExpanding512", int.class);
            MATRIX_EXPANDING_8_VH = lookup.findStaticVarHandle(Bit128.class, "matrixExpanding8", int.class);
            MATRIX_EXPANDING_32_VH = lookup.findStaticVarHandle(Bit128.class, "matrixExpanding32", int.class);
            MATRIX_EXPANDING_128_VH = lookup.findStaticVarHandle(Bit128.class, "matrixExpanding128", int.class);
            MATRIX_EXPANDING_512_VH = lookup.findStaticVarHandle(Bit128.class, "matrixExpanding512", int.class);
        }
        catch(ReflectiveOperationException e) {
            throw new ExceptionInInitializerError(e);
        }

        CACHE_ARENA_BASE = ForeignMemory.allocateNative(256 * 1024L);
        ForeignMemory.setMemory(CACHE_ARENA_BASE, 256 * 1024L, (byte) 0);

        poolArena = Arena.ofShared();
        active = true;

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

    private Bit128()
    {
    }

    private static void checkActive()
    {
        if(!active) throw new IllegalStateException("Bit128 subsystem is not active!");
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

    // =========================================================================
    // POOL EXPANSION
    // =========================================================================

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

                ForeignMemory.setUnsafe(userPtr, oldRawHead);

                long nextGen = ((oldTagged >>> 48) + 1L) & 0xFFFFL;
                long newTagged = (nextGen << 48) | (userPtr & 0x0000FFFFFFFFFFFFL);

                if(SINGLETON_FREE_HEAD_VH.compareAndSet(oldTagged, newTagged)) break;
            }
        }
    }

    private static void expandArrayPool(int bucketSize, VarHandle freeHeadVH)
    {
        long slotSize = 8L + ((long) bucketSize * ELEMENT_SIZE);
        long totalBytes = DEFAULT_CAPACITY * slotSize;
        long baseAddress = poolArena.allocate(totalBytes, 8).address();

        for(int i = 0; i < DEFAULT_CAPACITY; i++) {
            long currentBlock = baseAddress + (i * slotSize);
            long userPtr = currentBlock + 8L;

            while(true) {
                long oldTagged = (long) freeHeadVH.getVolatile();
                long oldRawHead = oldTagged & 0x0000FFFFFFFFFFFFL;

                ForeignMemory.setUnsafe(userPtr, oldRawHead);

                long nextGen = ((oldTagged >>> 48) + 1L) & 0xFFFFL;
                long newTagged = (nextGen << 48) | (userPtr & 0x0000FFFFFFFFFFFFL);

                if(freeHeadVH.compareAndSet(oldTagged, newTagged)) break;
            }
        }
    }

    private static void expandMatrixPool(int bucketSize, VarHandle freeHeadVH)
    {
        long slotSize = 8L + ((long) bucketSize * 8L);
        long totalBytes = DEFAULT_CAPACITY * slotSize;
        long baseAddress = poolArena.allocate(totalBytes, 8).address();

        for(int i = 0; i < DEFAULT_CAPACITY; i++) {
            long currentBlock = baseAddress + (i * slotSize);
            long userPtr = currentBlock + 8L;

            while(true) {
                long oldTagged = (long) freeHeadVH.getVolatile();
                long oldRawHead = oldTagged & 0x0000FFFFFFFFFFFFL;

                ForeignMemory.setUnsafe(userPtr, oldRawHead);

                long nextGen = ((oldTagged >>> 48) + 1L) & 0xFFFFL;
                long newTagged = (nextGen << 48) | (userPtr & 0x0000FFFFFFFFFFFFL);

                if(freeHeadVH.compareAndSet(oldTagged, newTagged)) break;
            }
        }
    }

    // =========================================================================
    // ALLOCATION
    // =========================================================================

    public static long allocateSingleton(int typeId)
    {
        checkActive();
        long threadSlotBase = getThreadSlotBase();
        long countAddr = threadSlotBase;
        int count = ForeignMemory.getUnsafeInt(countAddr);
        if (count > 0) {
            int nextCount = count - 1;
            ForeignMemory.setUnsafe(countAddr, nextCount);
            long dataAddr = threadSlotBase + 64L + (nextCount * 8L);
            long pointer = ForeignMemory.getUnsafeLong(dataAddr);
            long base = pointer - 8L;
            ForeignMemory.setUnsafe(base, typeId);
            ForeignMemory.setUnsafe(base + 4L, 1);
            ForeignMemory.setUnsafe(pointer, 0);
            return pointer;
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

            long nextRawHead = ForeignMemory.getUnsafeLong(rawHead);
            long nextGen = ((oldTagged >>> 48) + 1L) & 0xFFFFL;
            long newTagged = (nextGen << 48) | (nextRawHead & 0x0000FFFFFFFFFFFFL);

            if(SINGLETON_FREE_HEAD_VH.compareAndSet(oldTagged, newTagged)) {
                long base = rawHead - 8L;
                ForeignMemory.setUnsafe(base, typeId);
                ForeignMemory.setUnsafe(base + 4L, 1);
                ForeignMemory.setUnsafe(rawHead, 0);
                return rawHead;
            }
        }
    }

    public static long allocateArray(int typeId, int length)
    {
        checkActive();
        VarHandle headVH;
        VarHandle expandingVH;
        int bucketSize;
        int countOffset;
        int dataOffset;

        if(length <= BUCKET_8) {
            headVH = ARRAY_FREE_HEAD_8_VH;
            expandingVH = ARRAY_EXPANDING_8_VH;
            bucketSize = BUCKET_8;
            countOffset = 4;
            dataOffset = 128;
        }
        else if(length <= BUCKET_32) {
            headVH = ARRAY_FREE_HEAD_32_VH;
            expandingVH = ARRAY_EXPANDING_32_VH;
            bucketSize = BUCKET_32;
            countOffset = 8;
            dataOffset = 192;
        }
        else if(length <= BUCKET_128) {
            headVH = ARRAY_FREE_HEAD_128_VH;
            expandingVH = ARRAY_EXPANDING_128_VH;
            bucketSize = BUCKET_128;
            countOffset = 12;
            dataOffset = 256;
        }
        else if(length <= BUCKET_512) {
            headVH = ARRAY_FREE_HEAD_512_VH;
            expandingVH = ARRAY_EXPANDING_512_VH;
            bucketSize = BUCKET_512;
            countOffset = 16;
            dataOffset = 320;
        }
        else {
            long totalBytes = 8L + ((long) length * ELEMENT_SIZE);
            long alignedBytes = (totalBytes + 7L) & ~7L;
            long base = ForeignMemory.allocateNative(alignedBytes);
            ForeignMemory.setUnsafe(base, typeId);
            ForeignMemory.setUnsafe(base + 4L, length);
            return base + 8L;
        }

        long threadSlotBase = getThreadSlotBase();
        long countAddr = threadSlotBase + countOffset;
        int count = ForeignMemory.getUnsafeInt(countAddr);
        if (count > 0) {
            int nextCount = count - 1;
            ForeignMemory.setUnsafe(countAddr, nextCount);
            long dataAddr = threadSlotBase + dataOffset + (nextCount * 8L);
            long pointer = ForeignMemory.getUnsafeLong(dataAddr);
            long base = pointer - 8L;
            ForeignMemory.setUnsafe(base, typeId);
            ForeignMemory.setUnsafe(base + 4L, length);
            return pointer;
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

            long nextRawHead = ForeignMemory.getUnsafeLong(rawHead);
            long nextGen = ((oldTagged >>> 48) + 1L) & 0xFFFFL;
            long newTagged = (nextGen << 48) | (nextRawHead & 0x0000FFFFFFFFFFFFL);

            if(headVH.compareAndSet(oldTagged, newTagged)) {
                long base = rawHead - 8L;
                ForeignMemory.setUnsafe(base, typeId);
                ForeignMemory.setUnsafe(base + 4L, length);
                return rawHead;
            }
        }
    }

    public static long allocateMatrix(int typeId, int length)
    {
        checkActive();
        VarHandle headVH;
        VarHandle expandingVH;
        int bucketSize;
        int countOffset;
        int dataOffset;

        if(length <= BUCKET_8) {
            headVH = MATRIX_FREE_HEAD_8_VH;
            expandingVH = MATRIX_EXPANDING_8_VH;
            bucketSize = BUCKET_8;
            countOffset = 20;
            dataOffset = 384;
        }
        else if(length <= BUCKET_32) {
            headVH = MATRIX_FREE_HEAD_32_VH;
            expandingVH = MATRIX_EXPANDING_32_VH;
            bucketSize = BUCKET_32;
            countOffset = 24;
            dataOffset = 448;
        }
        else if(length <= BUCKET_128) {
            headVH = MATRIX_FREE_HEAD_128_VH;
            expandingVH = MATRIX_EXPANDING_128_VH;
            bucketSize = BUCKET_128;
            countOffset = 28;
            dataOffset = 512;
        }
        else if(length <= BUCKET_512) {
            headVH = MATRIX_FREE_HEAD_512_VH;
            expandingVH = MATRIX_EXPANDING_512_VH;
            bucketSize = BUCKET_512;
            countOffset = 32;
            dataOffset = 576;
        }
        else {
            long totalBytes = 8L + ((long) length * 8L);
            long base = ForeignMemory.allocateNative(totalBytes);
            ForeignMemory.setUnsafe(base, typeId);
            ForeignMemory.setUnsafe(base + 4L, length);
            return base + 8L;
        }

        long threadSlotBase = getThreadSlotBase();
        long countAddr = threadSlotBase + countOffset;
        int count = ForeignMemory.getUnsafeInt(countAddr);
        if (count > 0) {
            int nextCount = count - 1;
            ForeignMemory.setUnsafe(countAddr, nextCount);
            long dataAddr = threadSlotBase + dataOffset + (nextCount * 8L);
            long pointer = ForeignMemory.getUnsafeLong(dataAddr);
            long base = pointer - 8L;
            ForeignMemory.setUnsafe(base, typeId);
            ForeignMemory.setUnsafe(base + 4L, length);
            return pointer;
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

            long nextRawHead = ForeignMemory.getUnsafeLong(rawHead);
            long nextGen = ((oldTagged >>> 48) + 1L) & 0xFFFFL;
            long newTagged = (nextGen << 48) | (nextRawHead & 0x0000FFFFFFFFFFFFL);

            if(headVH.compareAndSet(oldTagged, newTagged)) {
                long base = rawHead - 8L;
                ForeignMemory.setUnsafe(base, typeId);
                ForeignMemory.setUnsafe(base + 4L, length);
                return rawHead;
            }
        }
    }

    // =========================================================================
    // FREE — dispatch table on the self-describing header
    // =========================================================================

    public static void free(long userPtr)
    {
        checkActive();
        if(userPtr == 0L) return;

        int type = ForeignMemory.getUnsafeInt(userPtr - 8L);
        if(type == 0 || (!TypeRegister.isSingleton(type) && !TypeRegister.isArray(type) && !TypeRegister.isPointer(type))) throw new IllegalStateException("Double free or corrupt off-heap pointer: 0x" + java.lang.Long.toHexString(userPtr).toUpperCase());

        int length = ForeignMemory.getUnsafeInt(userPtr - 4L);
        long base = userPtr - 8L;

        ForeignMemory.setUnsafe(base, 0);
        ForeignMemory.setUnsafe(base + 4L, -1);

        if(TypeRegister.isSingleton(type)) {
            long threadSlotBase = getThreadSlotBase();
            long countAddr = threadSlotBase;
            int count = ForeignMemory.getUnsafeInt(countAddr);
            if (count < 8) {
                long dataAddr = threadSlotBase + 64L + (count * 8L);
                ForeignMemory.setUnsafe(dataAddr, userPtr);
                ForeignMemory.setUnsafe(countAddr, count + 1);
                return;
            }
            while(true) {
                long oldTagged = singletonFreeHead;
                long oldRawHead = oldTagged & 0x0000FFFFFFFFFFFFL;

                ForeignMemory.setUnsafe(userPtr, oldRawHead);

                long nextGen = ((oldTagged >>> 48) + 1L) & 0xFFFFL;
                long newTagged = (nextGen << 48) | (userPtr & 0x0000FFFFFFFFFFFFL);

                if(SINGLETON_FREE_HEAD_VH.compareAndSet(oldTagged, newTagged)) return;
            }
        }
        else if(TypeRegister.isArray(type)) {
            int countOffset;
            int dataOffset;
            VarHandle headVH;

            if(length <= BUCKET_8) {
                countOffset = 4;
                dataOffset = 128;
                headVH = ARRAY_FREE_HEAD_8_VH;
            }
            else if(length <= BUCKET_32) {
                countOffset = 8;
                dataOffset = 192;
                headVH = ARRAY_FREE_HEAD_32_VH;
            }
            else if(length <= BUCKET_128) {
                countOffset = 12;
                dataOffset = 256;
                headVH = ARRAY_FREE_HEAD_128_VH;
            }
            else if(length <= BUCKET_512) {
                countOffset = 16;
                dataOffset = 320;
                headVH = ARRAY_FREE_HEAD_512_VH;
            }
            else {
                ForeignMemory.freeNative(base);
                return;
            }

            long threadSlotBase = getThreadSlotBase();
            long countAddr = threadSlotBase + countOffset;
            int count = ForeignMemory.getUnsafeInt(countAddr);
            if (count < 8) {
                long dataAddr = threadSlotBase + dataOffset + (count * 8L);
                ForeignMemory.setUnsafe(dataAddr, userPtr);
                ForeignMemory.setUnsafe(countAddr, count + 1);
                return;
            }

            while(true) {
                long oldTagged = (long) headVH.getVolatile();
                long oldRawHead = oldTagged & 0x0000FFFFFFFFFFFFL;

                ForeignMemory.setUnsafe(userPtr, oldRawHead);

                long nextGen = ((oldTagged >>> 48) + 1L) & 0xFFFFL;
                long newTagged = (nextGen << 48) | (userPtr & 0x0000FFFFFFFFFFFFL);

                if(headVH.compareAndSet(oldTagged, newTagged)) return;
            }
        }
        else if(TypeRegister.isPointer(type)) {
            int countOffset;
            int dataOffset;
            VarHandle headVH;

            if(length <= BUCKET_8) {
                countOffset = 20;
                dataOffset = 384;
                headVH = MATRIX_FREE_HEAD_8_VH;
            }
            else if(length <= BUCKET_32) {
                countOffset = 24;
                dataOffset = 448;
                headVH = MATRIX_FREE_HEAD_32_VH;
            }
            else if(length <= BUCKET_128) {
                countOffset = 28;
                dataOffset = 512;
                headVH = MATRIX_FREE_HEAD_128_VH;
            }
            else if(length <= BUCKET_512) {
                countOffset = 32;
                dataOffset = 576;
                headVH = MATRIX_FREE_HEAD_512_VH;
            }
            else {
                ForeignMemory.freeNative(base);
                return;
            }

            long threadSlotBase = getThreadSlotBase();
            long countAddr = threadSlotBase + countOffset;
            int count = ForeignMemory.getUnsafeInt(countAddr);
            if (count < 8) {
                long dataAddr = threadSlotBase + dataOffset + (count * 8L);
                ForeignMemory.setUnsafe(dataAddr, userPtr);
                ForeignMemory.setUnsafe(countAddr, count + 1);
                return;
            }

            while(true) {
                long oldTagged = (long) headVH.getVolatile();
                long oldRawHead = oldTagged & 0x0000FFFFFFFFFFFFL;

                ForeignMemory.setUnsafe(userPtr, oldRawHead);

                long nextGen = ((oldTagged >>> 48) + 1L) & 0xFFFFL;
                long newTagged = (nextGen << 48) | (userPtr & 0x0000FFFFFFFFFFFFL);

                if(headVH.compareAndSet(oldTagged, newTagged)) return;
            }
        }
    }
}