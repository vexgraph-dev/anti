package primitive;

import annotation.Required;
import annotation.Unsafe;
import nio.ForeignMemory;
import oop.TypeRegister;
import thread.ThreadRegistry;

import java.lang.foreign.Arena;
import java.lang.invoke.MethodHandles;
import java.lang.invoke.VarHandle;

public final class Bool {

    @Required
    public static final int CLASS_ID = TypeRegister.ID_BOOL;

    public static final int TYPE_SINGLETON = TypeRegister.BOOL_SINGLETON; // 0xAA000005
    public static final int TYPE_ARRAY     = TypeRegister.BOOL_ARRAY;     // 0xBB000005
    public static final int TYPE_MATRIX    = TypeRegister.BOOL_POINTER;   // 0xCC000005

    
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

    static {
        try {
            MethodHandles.Lookup lookup = MethodHandles.lookup();
            SINGLETON_FREE_HEAD_VH = lookup.findStaticVarHandle(Bool.class, "singletonFreeHead", long.class);
            ARRAY_FREE_HEAD_8_VH = lookup.findStaticVarHandle(Bool.class, "arrayFreeHead8", long.class);
            ARRAY_FREE_HEAD_32_VH = lookup.findStaticVarHandle(Bool.class, "arrayFreeHead32", long.class);
            ARRAY_FREE_HEAD_128_VH = lookup.findStaticVarHandle(Bool.class, "arrayFreeHead128", long.class);
            ARRAY_FREE_HEAD_512_VH = lookup.findStaticVarHandle(Bool.class, "arrayFreeHead512", long.class);
            MATRIX_FREE_HEAD_8_VH = lookup.findStaticVarHandle(Bool.class, "matrixFreeHead8", long.class);
            MATRIX_FREE_HEAD_32_VH = lookup.findStaticVarHandle(Bool.class, "matrixFreeHead32", long.class);
            MATRIX_FREE_HEAD_128_VH = lookup.findStaticVarHandle(Bool.class, "matrixFreeHead128", long.class);
            MATRIX_FREE_HEAD_512_VH = lookup.findStaticVarHandle(Bool.class, "matrixFreeHead512", long.class);

            SINGLETON_EXPANDING_VH = lookup.findStaticVarHandle(Bool.class, "singletonExpanding", int.class);
            ARRAY_EXPANDING_8_VH = lookup.findStaticVarHandle(Bool.class, "arrayExpanding8", int.class);
            ARRAY_EXPANDING_32_VH = lookup.findStaticVarHandle(Bool.class, "arrayExpanding32", int.class);
            ARRAY_EXPANDING_128_VH = lookup.findStaticVarHandle(Bool.class, "arrayExpanding128", int.class);
            ARRAY_EXPANDING_512_VH = lookup.findStaticVarHandle(Bool.class, "arrayExpanding512", int.class);
            MATRIX_EXPANDING_8_VH = lookup.findStaticVarHandle(Bool.class, "matrixExpanding8", int.class);
            MATRIX_EXPANDING_32_VH = lookup.findStaticVarHandle(Bool.class, "matrixExpanding32", int.class);
            MATRIX_EXPANDING_128_VH = lookup.findStaticVarHandle(Bool.class, "matrixExpanding128", int.class);
            MATRIX_EXPANDING_512_VH = lookup.findStaticVarHandle(Bool.class, "matrixExpanding512", int.class);
        }
        catch(ReflectiveOperationException e) {
            throw new ExceptionInInitializerError(e);
        }

        poolArena = Arena.ofShared();
        active = true;

        CACHE_ARENA_BASE = ForeignMemory.allocateNative(256 * 1024L);
        ForeignMemory.setMemory(CACHE_ARENA_BASE, 256 * 1024L, (byte) 0);

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

    private Bool()
    {
    }

    private static void checkActive()
    {
        if(!active) throw new IllegalStateException("Bool subsystem is not active!");
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
        long slotSize = 8L + (((bucketSize + 63) / 64) * 8L);
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
        int count = ForeignMemory.getUnsafeInt(slotBase + 0L);
        if (count > 0) {
            ForeignMemory.setUnsafe(slotBase + 0L, count - 1);
            long pointer = ForeignMemory.getUnsafeLong(slotBase + 64L + (count - 1) * 8L);
            long base = pointer - 8L;
            ForeignMemory.setUnsafe(base, TYPE_SINGLETON);
            ForeignMemory.setUnsafe(base + 4L, 1);
            ForeignMemory.set(pointer, 0L);
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
                ForeignMemory.setUnsafe(base, TYPE_SINGLETON);
                ForeignMemory.setUnsafe(base + 4L, 1);
                ForeignMemory.set(rawHead, 0L);
                return rawHead;
            }
        }
    }

    public static long allocateArray(int length)
    {
        checkActive();
        VarHandle headVH;
        VarHandle expandingVH;
        int bucketSize;
        long countOffset;
        long dataOffset;

        if(length <= BUCKET_8) {
            countOffset = 4L;
            dataOffset = 128L;
            headVH = ARRAY_FREE_HEAD_8_VH;
            expandingVH = ARRAY_EXPANDING_8_VH;
            bucketSize = BUCKET_8;
        }
        else if(length <= BUCKET_32) {
            countOffset = 8L;
            dataOffset = 192L;
            headVH = ARRAY_FREE_HEAD_32_VH;
            expandingVH = ARRAY_EXPANDING_32_VH;
            bucketSize = BUCKET_32;
        }
        else if(length <= BUCKET_128) {
            countOffset = 12L;
            dataOffset = 256L;
            headVH = ARRAY_FREE_HEAD_128_VH;
            expandingVH = ARRAY_EXPANDING_128_VH;
            bucketSize = BUCKET_128;
        }
        else if(length <= BUCKET_512) {
            countOffset = 16L;
            dataOffset = 320L;
            headVH = ARRAY_FREE_HEAD_512_VH;
            expandingVH = ARRAY_EXPANDING_512_VH;
            bucketSize = BUCKET_512;
        }
        else {
            long totalBytes = 8L + (((length + 63) / 64) * 8L);
            long alignedBytes = (totalBytes + 7L) & ~7L;
            long base = ForeignMemory.allocateNative(alignedBytes);
            ForeignMemory.setUnsafe(base, TYPE_ARRAY);
            ForeignMemory.setUnsafe(base + 4L, length);
            return base + 8L;
        }

        int threadIdx = ThreadRegistry.getThreadIndex();
        long slotBase = CACHE_ARENA_BASE + (threadIdx * 1024L);
        int count = ForeignMemory.getUnsafeInt(slotBase + countOffset);
        if (count > 0) {
            ForeignMemory.setUnsafe(slotBase + countOffset, count - 1);
            long pointer = ForeignMemory.getUnsafeLong(slotBase + dataOffset + (count - 1) * 8L);
            long base = pointer - 8L;
            ForeignMemory.setUnsafe(base, TYPE_ARRAY);
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
                ForeignMemory.setUnsafe(base, TYPE_ARRAY);
                ForeignMemory.setUnsafe(base + 4L, length);
                return rawHead;
            }
        }
    }

    public static long allocateMatrix(int length)
    {
        checkActive();
        VarHandle headVH;
        VarHandle expandingVH;
        int bucketSize;
        long countOffset;
        long dataOffset;

        if(length <= BUCKET_8) {
            countOffset = 20L;
            dataOffset = 384L;
            headVH = MATRIX_FREE_HEAD_8_VH;
            expandingVH = MATRIX_EXPANDING_8_VH;
            bucketSize = BUCKET_8;
        }
        else if(length <= BUCKET_32) {
            countOffset = 24L;
            dataOffset = 448L;
            headVH = MATRIX_FREE_HEAD_32_VH;
            expandingVH = MATRIX_EXPANDING_32_VH;
            bucketSize = BUCKET_32;
        }
        else if(length <= BUCKET_128) {
            countOffset = 28L;
            dataOffset = 512L;
            headVH = MATRIX_FREE_HEAD_128_VH;
            expandingVH = MATRIX_EXPANDING_128_VH;
            bucketSize = BUCKET_128;
        }
        else if(length <= BUCKET_512) {
            countOffset = 32L;
            dataOffset = 576L;
            headVH = MATRIX_FREE_HEAD_512_VH;
            expandingVH = MATRIX_EXPANDING_512_VH;
            bucketSize = BUCKET_512;
        }
        else {
            long totalBytes = 8L + (length * 8L);
            long base = ForeignMemory.allocateNative(totalBytes);
            ForeignMemory.setUnsafe(base, TYPE_MATRIX);
            ForeignMemory.setUnsafe(base + 4L, length);
            return base + 8L;
        }

        int threadIdx = ThreadRegistry.getThreadIndex();
        long slotBase = CACHE_ARENA_BASE + (threadIdx * 1024L);
        int count = ForeignMemory.getUnsafeInt(slotBase + countOffset);
        if (count > 0) {
            ForeignMemory.setUnsafe(slotBase + countOffset, count - 1);
            long pointer = ForeignMemory.getUnsafeLong(slotBase + dataOffset + (count - 1) * 8L);
            long base = pointer - 8L;
            ForeignMemory.setUnsafe(base, TYPE_MATRIX);
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
                ForeignMemory.setUnsafe(base, TYPE_MATRIX);
                ForeignMemory.setUnsafe(base + 4L, length);
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
        ForeignMemory.copy(oldPointer, newPointer, (((elementsToCopy + 63) / 64) * 8L));
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

        ForeignMemory.setUnsafe(base, 0);
        ForeignMemory.setUnsafe(base + 4L, -1);

        int threadIdx = ThreadRegistry.getThreadIndex();
        long slotBase = CACHE_ARENA_BASE + (threadIdx * 1024L);

        if(TypeRegister.isSingleton(type)) {
            int count = ForeignMemory.getUnsafeInt(slotBase + 0L);
            if (count < 8) {
                ForeignMemory.set(slotBase + 64L + count * 8L, pointer);
                ForeignMemory.setUnsafe(slotBase + 0L, count + 1);
                return;
            }

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
            long countOffset;
            long dataOffset;
            VarHandle headVH;
            if(length <= BUCKET_8) {
                countOffset = 4L;
                dataOffset = 128L;
                headVH = ARRAY_FREE_HEAD_8_VH;
            }
            else if(length <= BUCKET_32) {
                countOffset = 8L;
                dataOffset = 192L;
                headVH = ARRAY_FREE_HEAD_32_VH;
            }
            else if(length <= BUCKET_128) {
                countOffset = 12L;
                dataOffset = 256L;
                headVH = ARRAY_FREE_HEAD_128_VH;
            }
            else if(length <= BUCKET_512) {
                countOffset = 16L;
                dataOffset = 320L;
                headVH = ARRAY_FREE_HEAD_512_VH;
            }
            else {
                ForeignMemory.freeNative(base);
                return;
            }

            int count = ForeignMemory.getUnsafeInt(slotBase + countOffset);
            if (count < 8) {
                ForeignMemory.set(slotBase + dataOffset + count * 8L, pointer);
                ForeignMemory.setUnsafe(slotBase + countOffset, count + 1);
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
            long countOffset;
            long dataOffset;
            VarHandle headVH;
            if(length <= BUCKET_8) {
                countOffset = 20L;
                dataOffset = 384L;
                headVH = MATRIX_FREE_HEAD_8_VH;
            }
            else if(length <= BUCKET_32) {
                countOffset = 24L;
                dataOffset = 448L;
                headVH = MATRIX_FREE_HEAD_32_VH;
            }
            else if(length <= BUCKET_128) {
                countOffset = 28L;
                dataOffset = 512L;
                headVH = MATRIX_FREE_HEAD_128_VH;
            }
            else if(length <= BUCKET_512) {
                countOffset = 32L;
                dataOffset = 576L;
                headVH = MATRIX_FREE_HEAD_512_VH;
            }
            else {
                ForeignMemory.freeNative(base);
                return;
            }

            int count = ForeignMemory.getUnsafeInt(slotBase + countOffset);
            if (count < 8) {
                ForeignMemory.set(slotBase + dataOffset + count * 8L, pointer);
                ForeignMemory.setUnsafe(slotBase + countOffset, count + 1);
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
