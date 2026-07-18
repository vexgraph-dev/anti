package primitive;

import nio.ForeignMemory;
import nio.MemoryRegistry;

import java.lang.foreign.Arena;
import java.lang.invoke.MethodHandles;
import java.lang.invoke.VarHandle;

public final class Int64Fp64 {

    public static final int TYPE_SINGLETON = 0;
    public static final int TYPE_ARRAY = 1;
    public static final int TYPE_MATRIX = 2;

    private static final int DEFAULT_CAPACITY = 1024;

    // Memory Block Sizes (8B header + 16B payload per element: 8B long int + 8B long frac)
    private static final long SINGLETON_SLOT_SIZE = 24L; 
    private static final long POOLED_ARRAY_SIZE = 8L + (DEFAULT_CAPACITY * 16L);  // 16392 Bytes
    private static final long POOLED_MATRIX_SIZE = 8L + (DEFAULT_CAPACITY * 8L);  // 8200 Bytes

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
            SINGLETON_FREE_HEAD_VH = lookup.findStaticVarHandle(Int64Fp64.class, "singletonFreeHead", long.class);
            ARRAY_FREE_HEAD_VH = lookup.findStaticVarHandle(Int64Fp64.class, "arrayFreeHead", long.class);
            MATRIX_FREE_HEAD_VH = lookup.findStaticVarHandle(Int64Fp64.class, "matrixFreeHead", long.class);

            SINGLETON_EXPANDING_VH = lookup.findStaticVarHandle(Int64Fp64.class, "singletonExpanding", int.class);
            ARRAY_EXPANDING_VH = lookup.findStaticVarHandle(Int64Fp64.class, "arrayExpanding", int.class);
            MATRIX_EXPANDING_VH = lookup.findStaticVarHandle(Int64Fp64.class, "matrixExpanding", int.class);
        } catch (ReflectiveOperationException e) {
            throw new ExceptionInInitializerError(e);
        }

        poolArena = Arena.ofShared();
        active = true;

        MemoryRegistry.register(Int64Fp64::freeAll);

        expandSingletonPool();
        expandArrayPool();
        expandMatrixPool();
    }

    private Int64Fp64() {}

    private static void checkActive() {
        if (!active) throw new IllegalStateException("Int64Fp64 subsystem is not active!");
    }

    public static void freeAll() {
        if (active) {
            active = false;
            if (poolArena != null && poolArena.scope().isAlive()) {
                poolArena.close();
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
                long base = rawHead - 8L;
                ForeignMemory.putInt(base, 1);
                ForeignMemory.putInt(base + 4L, TYPE_SINGLETON);
                ForeignMemory.putLong(rawHead, 0L);
                ForeignMemory.putLong(rawHead + 8L, 0L);
                return rawHead;
            }
        }
    }

    public static long allocateArray(int length) {
        checkActive();
        if (length <= DEFAULT_CAPACITY) {
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
                    long base = rawHead - 8L;
                    ForeignMemory.putInt(base, length);
                    ForeignMemory.putInt(base + 4L, TYPE_ARRAY);
                    return rawHead;
                }
            }
        } else {
            // Oversized: Pure FFM C malloc downcall (0% GC)
            long totalBytes = 8L + (length * 16L);
            long alignedBytes = (totalBytes + 7L) & ~7L;
            long base = ForeignMemory.allocateNative(alignedBytes);
            ForeignMemory.putInt(base, length);
            ForeignMemory.putInt(base + 4L, TYPE_ARRAY);
            return base + 8L;
        }
    }

    // --- RECYCLING LAYER ---
    public static void free(long pointer) {
        checkActive();
        if (pointer == 0L) return;

        int type = type(pointer);
        int length = length(pointer);
        long base = pointer - 8L;

        ForeignMemory.putInt(base, 0);
        ForeignMemory.putInt(base + 4L, -1);

        if (type == TYPE_SINGLETON) {
            while (true) {
                long oldTagged = singletonFreeHead;
                long oldRawHead = oldTagged & 0x0000FFFFFFFFFFFFL;

                ForeignMemory.putLong(pointer, oldRawHead);

                long nextGen = ((oldTagged >>> 48) + 1L) & 0xFFFFL;
                long newTagged = (nextGen << 48) | (pointer & 0x0000FFFFFFFFFFFFL);

                if (SINGLETON_FREE_HEAD_VH.compareAndSet(oldTagged, newTagged)) return;
            }
        } else if (type == TYPE_ARRAY) {
            if (length > DEFAULT_CAPACITY) {
                ForeignMemory.freeNative(base);
                return;
            }
            while (true) {
                long oldTagged = arrayFreeHead;
                long oldRawHead = oldTagged & 0x0000FFFFFFFFFFFFL;

                ForeignMemory.putLong(pointer, oldRawHead);

                long nextGen = ((oldTagged >>> 48) + 1L) & 0xFFFFL;
                long newTagged = (nextGen << 48) | (pointer & 0x0000FFFFFFFFFFFFL);

                if (ARRAY_FREE_HEAD_VH.compareAndSet(oldTagged, newTagged)) return;
            }
        }
    }

    // --- MATH & CONVERSION LOGIC ---
    public static double toDouble(long integerPart, long fractionPart) {
        double frac = (fractionPart & 0x7FFFFFFFFFFFFFFFL);
        if (fractionPart < 0) {
            frac += 9223372036854775808.0;
        }
        return integerPart + (frac / 18446744073709551616.0);
    }

    public static void setFromDouble(long pointer, double value) {
        long integerPart = (long) Math.floor(value);
        double frac = value - integerPart;
        long fractionPart;
        if (frac < 0.5) {
            fractionPart = (long) (frac * 18446744073709551616.0);
        } else {
            fractionPart = (long) ((frac - 0.5) * 18446744073709551616.0) + Long.MIN_VALUE;
        }
        set(pointer, integerPart, fractionPart);
    }

    public static void setFromDouble(long pointer, int index, double value) {
        long integerPart = (long) Math.floor(value);
        double frac = value - integerPart;
        long fractionPart;
        if (frac < 0.5) {
            fractionPart = (long) (frac * 18446744073709551616.0);
        } else {
            fractionPart = (long) ((frac - 0.5) * 18446744073709551616.0) + Long.MIN_VALUE;
        }
        set(pointer, index, integerPart, fractionPart);
    }

    public static void add(long aPtr, long bPtr, long resultPtr) {
        long aInt = getInteger(aPtr);
        long aFrac = getFraction(aPtr);
        long bInt = getInteger(bPtr);
        long bFrac = getFraction(bPtr);

        long sumF = aFrac + bFrac;
        long carry = (Long.compareUnsigned(sumF, aFrac) < 0) ? 1L : 0L;
        set(resultPtr, aInt + bInt + carry, sumF);
    }

    public static void sub(long aPtr, long bPtr, long resultPtr) {
        long aInt = getInteger(aPtr);
        long aFrac = getFraction(aPtr);
        long bInt = getInteger(bPtr);
        long bFrac = getFraction(bPtr);

        long diffF = aFrac - bFrac;
        long borrow = (Long.compareUnsigned(aFrac, bFrac) < 0) ? 1L : 0L;
        set(resultPtr, aInt - bInt - borrow, diffF);
    }

    public static void mul(long aPtr, long bPtr, long resultPtr) {
        double v1 = toDouble(getInteger(aPtr), getFraction(aPtr));
        double v2 = toDouble(getInteger(bPtr), getFraction(bPtr));
        setFromDouble(resultPtr, v1 * v2);
    }

    public static void div(long aPtr, long bPtr, long resultPtr) {
        double v1 = toDouble(getInteger(aPtr), getFraction(aPtr));
        double v2 = toDouble(getInteger(bPtr), getFraction(bPtr));
        setFromDouble(resultPtr, v1 / v2);
    }

    // --- DATA ACCESSORS & BOUNDS CHECKS ---
    public static long getInteger(long pointer) { return ForeignMemory.getLong(pointer); }
    public static long getFraction(long pointer) { return ForeignMemory.getLong(pointer + 8L); }

    public static long getInteger(long pointer, int index) { 
        checkBounds(pointer, index);
        return ForeignMemory.getLong(pointer + (index * 16L)); 
    }
    public static long getFraction(long pointer, int index) { 
        checkBounds(pointer, index);
        return ForeignMemory.getLong(pointer + (index * 16L) + 8L); 
    }

    public static void set(long pointer, long integerPart, long fractionPart) { 
        ForeignMemory.putLong(pointer, integerPart); 
        ForeignMemory.putLong(pointer + 8L, fractionPart);
    }

    public static void set(long pointer, int index, long integerPart, long fractionPart) { 
        checkBounds(pointer, index);
        long offset = pointer + (index * 16L);
        ForeignMemory.putLong(offset, integerPart);
        ForeignMemory.putLong(offset + 8L, fractionPart);
    }

    private static void checkBounds(long pointer, int index) {
        int len = length(pointer);
        if (index < 0 || index >= len) {
            throw new IndexOutOfBoundsException("Index " + index + " out of bounds for off-heap Int64Fp64 length " + len);
        }
    }

    public static int length(long pointer) { return ForeignMemory.getInt(pointer - 8L); }
    public static int type(long pointer) { return ForeignMemory.getInt(pointer - 4L); }
}