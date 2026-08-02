package primitive;

import annotation.Unsafe;
import annotation.Volatile;

import annotation.Required;
import nio.ForeignMemory;
import oop.TypeRegister;

import java.lang.foreign.Arena;
import java.lang.invoke.MethodHandles;
import java.lang.invoke.VarHandle;

public final class Double {

    @Required
    public static final int CLASS_ID = TypeRegister.ID_DOUBLE;

    public static final double MAX_VALUE = java.lang.Double.MAX_VALUE;
    public static final double MIN_VALUE = java.lang.Double.MIN_VALUE;

    public static final int TYPE_SINGLETON = TypeRegister.DOUBLE_SINGLETON; // 0xAA000004
    public static final int TYPE_ARRAY     = TypeRegister.DOUBLE_ARRAY;     // 0xBB000004
    public static final int TYPE_MATRIX    = TypeRegister.DOUBLE_POINTER;   // 0xCC000004

    
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

    static {
        try {
            MethodHandles.Lookup lookup = MethodHandles.lookup();
            SINGLETON_FREE_HEAD_VH = lookup.findStaticVarHandle(Double.class, "singletonFreeHead", long.class);
            ARRAY_FREE_HEAD_8_VH = lookup.findStaticVarHandle(Double.class, "arrayFreeHead8", long.class);
            ARRAY_FREE_HEAD_32_VH = lookup.findStaticVarHandle(Double.class, "arrayFreeHead32", long.class);
            ARRAY_FREE_HEAD_128_VH = lookup.findStaticVarHandle(Double.class, "arrayFreeHead128", long.class);
            ARRAY_FREE_HEAD_512_VH = lookup.findStaticVarHandle(Double.class, "arrayFreeHead512", long.class);
            MATRIX_FREE_HEAD_8_VH = lookup.findStaticVarHandle(Double.class, "matrixFreeHead8", long.class);
            MATRIX_FREE_HEAD_32_VH = lookup.findStaticVarHandle(Double.class, "matrixFreeHead32", long.class);
            MATRIX_FREE_HEAD_128_VH = lookup.findStaticVarHandle(Double.class, "matrixFreeHead128", long.class);
            MATRIX_FREE_HEAD_512_VH = lookup.findStaticVarHandle(Double.class, "matrixFreeHead512", long.class);

            SINGLETON_EXPANDING_VH = lookup.findStaticVarHandle(Double.class, "singletonExpanding", int.class);
            ARRAY_EXPANDING_8_VH = lookup.findStaticVarHandle(Double.class, "arrayExpanding8", int.class);
            ARRAY_EXPANDING_32_VH = lookup.findStaticVarHandle(Double.class, "arrayExpanding32", int.class);
            ARRAY_EXPANDING_128_VH = lookup.findStaticVarHandle(Double.class, "arrayExpanding128", int.class);
            ARRAY_EXPANDING_512_VH = lookup.findStaticVarHandle(Double.class, "arrayExpanding512", int.class);
            MATRIX_EXPANDING_8_VH = lookup.findStaticVarHandle(Double.class, "matrixExpanding8", int.class);
            MATRIX_EXPANDING_32_VH = lookup.findStaticVarHandle(Double.class, "matrixExpanding32", int.class);
            MATRIX_EXPANDING_128_VH = lookup.findStaticVarHandle(Double.class, "matrixExpanding128", int.class);
            MATRIX_EXPANDING_512_VH = lookup.findStaticVarHandle(Double.class, "matrixExpanding512", int.class);
        }
        catch(ReflectiveOperationException e) {
            throw new ExceptionInInitializerError(e);
        }

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

    private Double()
    {
    }

    private static void checkActive()
    {
        if(!active) throw new IllegalStateException("Double subsystem is not active!");
    }

    public static void freeAll()
    {
        if(active) {
            active = false;
            if(poolArena != null && poolArena.scope().isAlive()) poolArena.close();
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

                ForeignMemory.putLong(userPtr, oldRawHead);

                long nextGen = ((oldTagged >>> 48) + 1L) & 0xFFFFL;
                long newTagged = (nextGen << 48) | (userPtr & 0x0000FFFFFFFFFFFFL);

                if(SINGLETON_FREE_HEAD_VH.compareAndSet(oldTagged, newTagged)) break;
            }
        }
    }

    private static void expandArrayPool(int bucketSize, VarHandle freeHeadVH)
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

                ForeignMemory.putLong(userPtr, oldRawHead);

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

                ForeignMemory.putLong(userPtr, oldRawHead);

                long nextGen = ((oldTagged >>> 48) + 1L) & 0xFFFFL;
                long newTagged = (nextGen << 48) | (userPtr & 0x0000FFFFFFFFFFFFL);

                if(freeHeadVH.compareAndSet(oldTagged, newTagged)) break;
            }
        }
    }

    public static long allocateSingleton()
    {
        checkActive();
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

            long nextRawHead = ForeignMemory.getLong(rawHead);
            long nextGen = ((oldTagged >>> 48) + 1L) & 0xFFFFL;
            long newTagged = (nextGen << 48) | (nextRawHead & 0x0000FFFFFFFFFFFFL);

            if(SINGLETON_FREE_HEAD_VH.compareAndSet(oldTagged, newTagged)) {
                long base = rawHead - 8L;
                ForeignMemory.putInt(base, TYPE_SINGLETON);
                ForeignMemory.putInt(base + 4L, 1);
                ForeignMemory.putLong(rawHead, 0);
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
            long totalBytes = 8L + (length * 8L);
            long alignedBytes = (totalBytes + 7L) & ~7L;
            long base = ForeignMemory.allocateNative(alignedBytes);
            ForeignMemory.putInt(base, TYPE_ARRAY);
            ForeignMemory.putInt(base + 4L, length);
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

            long nextRawHead = ForeignMemory.getLong(rawHead);
            long nextGen = ((oldTagged >>> 48) + 1L) & 0xFFFFL;
            long newTagged = (nextGen << 48) | (nextRawHead & 0x0000FFFFFFFFFFFFL);

            if(headVH.compareAndSet(oldTagged, newTagged)) {
                long base = rawHead - 8L;
                ForeignMemory.putInt(base, TYPE_ARRAY);
                ForeignMemory.putInt(base + 4L, length);
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
            ForeignMemory.putInt(base, TYPE_MATRIX);
            ForeignMemory.putInt(base + 4L, length);
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

            long nextRawHead = ForeignMemory.getLong(rawHead);
            long nextGen = ((oldTagged >>> 48) + 1L) & 0xFFFFL;
            long newTagged = (nextGen << 48) | (nextRawHead & 0x0000FFFFFFFFFFFFL);

            if(headVH.compareAndSet(oldTagged, newTagged)) {
                long base = rawHead - 8L;
                ForeignMemory.putInt(base, TYPE_MATRIX);
                ForeignMemory.putInt(base + 4L, length);
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
        ForeignMemory.copy(oldPointer, newPointer, (elementsToCopy * 8L));
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

        ForeignMemory.putInt(base, 0);
        ForeignMemory.putInt(base + 4L, -1);

        if(TypeRegister.isSingleton(type)) {
            while(true) {
                long oldTagged = singletonFreeHead;
                long oldRawHead = oldTagged & 0x0000FFFFFFFFFFFFL;

                ForeignMemory.putLong(pointer, oldRawHead);

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

                ForeignMemory.putLong(pointer, oldRawHead);

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

                ForeignMemory.putLong(pointer, oldRawHead);

                long nextGen = ((oldTagged >>> 48) + 1L) & 0xFFFFL;
                long newTagged = (nextGen << 48) | (pointer & 0x0000FFFFFFFFFFFFL);

                if(headVH.compareAndSet(oldTagged, newTagged)) return;
            }
        }
    }
public static double get(long pointer) {
        if (pointer == 0L) throw new NullPointerException("Accessing NULL off-heap pointer!");
        return ForeignMemory.getDouble(pointer);
    }

    public static double get(long pointer, int index) { 
        checkBounds(pointer, index);
        return ForeignMemory.getDouble(pointer + (index * 8L)); 
    }

    public static long getPointer(long matrixPointer, int index) { 
        if (matrixPointer == 0L) throw new NullPointerException("Accessing NULL matrix pointer!");
        if(!isPointer(matrixPointer)) throw new IllegalArgumentException("Expected Pointer Array (Matrix), but got Type: 0x" + Integer.toHexString(type(matrixPointer)).toUpperCase());
        checkBounds(matrixPointer, index);
        return ForeignMemory.getLong(matrixPointer + (index * 8L)); 
    }

    public static void set(long pointer, double value) {
        if (pointer == 0L) throw new NullPointerException("Writing to NULL off-heap pointer!");
        ForeignMemory.putDouble(pointer, value);
    }

    public static void set(long pointer, int index, double value) { 
        checkBounds(pointer, index);
        ForeignMemory.putDouble(pointer + (index * 8L), value); 
    }

    @Volatile
    public static double getVolatile(long pointer) {
        if (pointer == 0L) throw new NullPointerException("Reading from NULL off-heap pointer!");
        return ForeignMemory.getDoubleVolatile(pointer);
    }

    @Volatile
    public static void setVolatile(long pointer, double value) {
        if (pointer == 0L) throw new NullPointerException("Writing to NULL off-heap pointer!");
        ForeignMemory.putDoubleVolatile(pointer, value);
    }

    public static boolean compareAndSet(long pointer, double expected, double value) {
        if (pointer == 0L) throw new NullPointerException("Writing to NULL off-heap pointer!");
        return ForeignMemory.compareAndSetDouble(pointer, expected, value);
    }

    public static void setPointer(long matrixPointer, int index, long targetPointer) { 
        if (matrixPointer == 0L) throw new NullPointerException("Writing to NULL matrix pointer!");
        if(!isPointer(matrixPointer)) throw new IllegalArgumentException("Expected Pointer Array (Matrix), but got Type: 0x" + Integer.toHexString(type(matrixPointer)).toUpperCase());
        checkBounds(matrixPointer, index);
        ForeignMemory.putLong(matrixPointer + (index * 8L), targetPointer); 
    }

    private static void checkBounds(long pointer, int index) {
        if (pointer == 0L) throw new NullPointerException("Checking bounds on NULL off-heap pointer!");
        int len = length(pointer);
        if(index < 0 || index >= len) throw new IndexOutOfBoundsException("Index " + index + " out of bounds for off-heap double length " + len + " (Ptr: 0x" + java.lang.Long.toHexString(pointer).toUpperCase() + ", Type: 0x" + Integer.toHexString(type(pointer)).toUpperCase() + ")");
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

    // --- AUTOGENERATED UNSAFE & VOLATILE VARIANTS ---

    @Unsafe
    public static double unsafeGet(long pointer) {
        return ForeignMemory.getDouble(pointer);
    }

    @Unsafe
    public static double unsafeGet(long pointer, int index) {
        return ForeignMemory.getDouble(pointer + (index * 8L));
    }

    @Unsafe
    public static long unsafeGetPointer(long matrixPointer, int index) {
        return ForeignMemory.getLong(matrixPointer + (index * 8L));
    }

    @Unsafe
    public static void unsafeSet(long pointer, double value) {
        ForeignMemory.putDouble(pointer, value);
    }

    @Unsafe
    public static void unsafeSet(long pointer, int index, double value) {
        ForeignMemory.putDouble(pointer + (index * 8L), value);
    }

    @Unsafe
    public static void unsafeSetPointer(long matrixPointer, int index, long targetPointer) {
        ForeignMemory.putLong(matrixPointer + (index * 8L), targetPointer);
    }

    @Volatile
    public static double getVolatile(long pointer, int index) {
        checkBounds(pointer, index);
        return ForeignMemory.getDoubleVolatile(pointer + (index * 8L));
    }

    @Volatile
    public static long getPointerVolatile(long matrixPointer, int index) {
        if(matrixPointer == 0L) throw new NullPointerException("Accessing NULL matrix pointer!");
        checkBounds(matrixPointer, index);
        return ForeignMemory.getLongVolatile(matrixPointer + (index * 8L));
    }

    @Volatile
    public static void setVolatile(long pointer, int index, double value) {
        checkBounds(pointer, index);
        ForeignMemory.putDoubleVolatile(pointer + (index * 8L), value);
    }

    @Volatile
    public static void setPointerVolatile(long matrixPointer, int index, long targetPointer) {
        if(matrixPointer == 0L) throw new NullPointerException("Writing to NULL matrix pointer!");
        checkBounds(matrixPointer, index);
        ForeignMemory.putLongVolatile(matrixPointer + (index * 8L), targetPointer);
    }

    @Unsafe
    @Volatile
    public static double unsafeVolatileGet(long pointer) {
        return ForeignMemory.getDoubleVolatile(pointer);
    }

    @Unsafe
    @Volatile
    public static double unsafeVolatileGet(long pointer, int index) {
        return ForeignMemory.getDoubleVolatile(pointer + (index * 8L));
    }

    @Unsafe
    @Volatile
    public static long unsafeVolatileGetPointer(long matrixPointer, int index) {
        return ForeignMemory.getLongVolatile(matrixPointer + (index * 8L));
    }

    @Unsafe
    @Volatile
    public static void unsafeVolatileSet(long pointer, double value) {
        ForeignMemory.putDoubleVolatile(pointer, value);
    }

    @Unsafe
    @Volatile
    public static void unsafeVolatileSet(long pointer, int index, double value) {
        ForeignMemory.putDoubleVolatile(pointer + (index * 8L), value);
    }

    @Unsafe
    @Volatile
    public static void unsafeVolatileSetPointer(long matrixPointer, int index, long targetPointer) {
        ForeignMemory.putLongVolatile(matrixPointer + (index * 8L), targetPointer);
    }

}
