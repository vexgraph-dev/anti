package buffers;

import annotation.Draft;
import annotation.Required;
import annotation.Unsafe;
import annotation.Volatile;
import nio.ForeignMemory;
import oop.TypeRegister;
import oop.Inheritance;

import java.lang.foreign.Arena;
import java.lang.invoke.MethodHandles;
import java.lang.invoke.VarHandle;

@Draft
public final class Buffer {

    @Required
    public static final int CLASS_ID = TypeRegister.ID_BUFFER;

    private static final int DEFAULT_CAPACITY = 1024;
    private static final int BUCKET_8 = 8;
    private static final int BUCKET_32 = 32;
    private static final int BUCKET_128 = 128;
    private static final int BUCKET_512 = 512;

    private static final VarHandle ARRAY_FREE_HEAD_8_VH;
    private static final VarHandle ARRAY_FREE_HEAD_32_VH;
    private static final VarHandle ARRAY_FREE_HEAD_128_VH;
    private static final VarHandle ARRAY_FREE_HEAD_512_VH;

    private static final VarHandle ARRAY_EXPANDING_8_VH;
    private static final VarHandle ARRAY_EXPANDING_32_VH;
    private static final VarHandle ARRAY_EXPANDING_128_VH;
    private static final VarHandle ARRAY_EXPANDING_512_VH;

    private static volatile int arrayExpanding8 = 0;
    private static volatile int arrayExpanding32 = 0;
    private static volatile int arrayExpanding128 = 0;
    private static volatile int arrayExpanding512 = 0;

    private static Arena poolArena;
    private static volatile boolean active;

    private static volatile long arrayFreeHead8;
    private static volatile long arrayFreeHead32;
    private static volatile long arrayFreeHead128;
    private static volatile long arrayFreeHead512;

    static {
        try {
            MethodHandles.Lookup lookup = MethodHandles.lookup();
            ARRAY_FREE_HEAD_8_VH = lookup.findStaticVarHandle(Buffer.class, "arrayFreeHead8", long.class);
            ARRAY_FREE_HEAD_32_VH = lookup.findStaticVarHandle(Buffer.class, "arrayFreeHead32", long.class);
            ARRAY_FREE_HEAD_128_VH = lookup.findStaticVarHandle(Buffer.class, "arrayFreeHead128", long.class);
            ARRAY_FREE_HEAD_512_VH = lookup.findStaticVarHandle(Buffer.class, "arrayFreeHead512", long.class);

            ARRAY_EXPANDING_8_VH = lookup.findStaticVarHandle(Buffer.class, "arrayExpanding8", int.class);
            ARRAY_EXPANDING_32_VH = lookup.findStaticVarHandle(Buffer.class, "arrayExpanding32", int.class);
            ARRAY_EXPANDING_128_VH = lookup.findStaticVarHandle(Buffer.class, "arrayExpanding128", int.class);
            ARRAY_EXPANDING_512_VH = lookup.findStaticVarHandle(Buffer.class, "arrayExpanding512", int.class);
        } catch (ReflectiveOperationException e) {
            throw new ExceptionInInitializerError(e);
        }

        poolArena = Arena.ofShared();
        active = true;

        expandArrayPool(BUCKET_8, ARRAY_FREE_HEAD_8_VH);
        expandArrayPool(BUCKET_32, ARRAY_FREE_HEAD_32_VH);
        expandArrayPool(BUCKET_128, ARRAY_FREE_HEAD_128_VH);
        expandArrayPool(BUCKET_512, ARRAY_FREE_HEAD_512_VH);
    }

    private static void checkActive() {
        if (!active)
            throw new IllegalStateException("Buffer subsystem is not active!");
    }

    public static void freeAll() {
        if (active) {
            active = false;
            if (poolArena != null && poolArena.scope().isAlive())
                poolArena.close();
        }
    }

    public static boolean isBufferClass(int classId) {
        return classId >= 0x000050 && classId <= 0x000063;
    }

    private static void expandArrayPool(int bucketSize, VarHandle freeHeadVH) {
        long slotSize = 24L + (bucketSize * 8L);
        long totalBytes = DEFAULT_CAPACITY * slotSize;
        long baseAddress = poolArena.allocate(totalBytes, 8).address();

        for (int i = 0; i < DEFAULT_CAPACITY; i++) {
            long currentBlock = baseAddress + (i * slotSize);
            long userPtr = currentBlock + 24L;
            while (true) {
                long oldTagged = (long) freeHeadVH.getVolatile();
                long oldRawHead = oldTagged & 0x0000FFFFFFFFFFFFL;
                ForeignMemory.setLong(userPtr, oldRawHead);
                long nextGen = ((oldTagged >>> 48) + 1L) & 0xFFFFL;
                long newTagged = (nextGen << 48) | (userPtr & 0x0000FFFFFFFFFFFFL);
                if (freeHeadVH.compareAndSet(oldTagged, newTagged))
                    break;
            }
        }
    }

    public static long allocate(int classId, int width, int height, int channels) {
        if (!Inheritance.isSubclassOf(classId, TypeRegister.ID_BUFFER))
            throw new IllegalArgumentException("Class ID 0x" + Integer.toHexString(classId) + " is not a subclass of Buffer");
        checkActive();

        int length = width * height * channels;
        VarHandle headVH;
        VarHandle expandingVH;
        int bucketSize;

        if (length <= BUCKET_8) {
            headVH = ARRAY_FREE_HEAD_8_VH;
            expandingVH = ARRAY_EXPANDING_8_VH;
            bucketSize = BUCKET_8;
        } else if (length <= BUCKET_32) {
            headVH = ARRAY_FREE_HEAD_32_VH;
            expandingVH = ARRAY_EXPANDING_32_VH;
            bucketSize = BUCKET_32;
        } else if (length <= BUCKET_128) {
            headVH = ARRAY_FREE_HEAD_128_VH;
            expandingVH = ARRAY_EXPANDING_128_VH;
            bucketSize = BUCKET_128;
        } else if (length <= BUCKET_512) {
            headVH = ARRAY_FREE_HEAD_512_VH;
            expandingVH = ARRAY_EXPANDING_512_VH;
            bucketSize = BUCKET_512;
        } else {
            long totalBytes = 24L + (length * 8L);
            long base = ForeignMemory.allocateNative(totalBytes);
            long userPtr = base + 24L;
            ForeignMemory.setInt(userPtr - 20L, width);
            ForeignMemory.setInt(userPtr - 16L, height);
            ForeignMemory.setInt(userPtr - 12L, channels);
            int type = TypeRegister.FORM_ARRAY | classId;
            ForeignMemory.setInt(userPtr - 8L, type);
            ForeignMemory.setInt(userPtr - 4L, length);
            return userPtr;
        }

        while (true) {
            long oldTagged = (long) headVH.getVolatile();
            long rawHead = oldTagged & 0x0000FFFFFFFFFFFFL;

            if (rawHead == 0L) {
                int exp = (int) expandingVH.getVolatile();
                if (exp == 0 && expandingVH.compareAndSet(0, 1)) {
                    expandArrayPool(bucketSize, headVH);
                    expandingVH.setVolatile(0);
                } else
                    Thread.onSpinWait();
                continue;
            }

            long nextRawHead = ForeignMemory.getLong(rawHead);
            long nextGen = ((oldTagged >>> 48) + 1L) & 0xFFFFL;
            long newTagged = (nextGen << 48) | (nextRawHead & 0x0000FFFFFFFFFFFFL);

            if (headVH.compareAndSet(oldTagged, newTagged)) {
                ForeignMemory.setInt(rawHead - 20L, width);
                ForeignMemory.setInt(rawHead - 16L, height);
                ForeignMemory.setInt(rawHead - 12L, channels);
                int type = TypeRegister.FORM_ARRAY | classId;
                ForeignMemory.setInt(rawHead - 8L, type);
                ForeignMemory.setInt(rawHead - 4L, length);
                return rawHead;
            }
        }
    }

    public static long expand(long oldPointer, int newWidth, int newHeight) {
        checkActive();
        if (oldPointer == 0L)
            throw new NullPointerException("Expanding NULL old pointer!");
        int type = type(oldPointer);
        int classId = TypeRegister.getClassId(type);
        int oldLength = length(oldPointer);
        int channels = channels(oldPointer);
        long newPointer = allocate(classId, newWidth, newHeight, channels);

        int newLength = newWidth * newHeight * channels;
        int elementsToCopy = Math.min(oldLength, newLength);
        ForeignMemory.copy(oldPointer, newPointer, elementsToCopy * 8L);
        free(oldPointer);
        return newPointer;
    }

    public static void free(long pointer) {
        checkActive();
        if (pointer == 0L)
            return;

        int type = type(pointer);
        int classId = TypeRegister.getClassId(type);
        if (!Inheritance.isSubclassOf(classId, TypeRegister.ID_BUFFER))
            throw new IllegalStateException("Not a buffer class or corrupt pointer: 0x" + java.lang.Long.toHexString(pointer).toUpperCase());

        int length = length(pointer);
        long base = pointer - 24L;

        ForeignMemory.setInt(base, 0);
        ForeignMemory.setInt(base + 4L, 0);
        ForeignMemory.setInt(base + 8L, 0);
        ForeignMemory.setInt(base + 12L, 0);
        ForeignMemory.setInt(base + 16L, 0);
        ForeignMemory.setInt(base + 20L, -1);

        if (length > BUCKET_512) {
            ForeignMemory.freeNative(base);
            return;
        }

        VarHandle headVH;
        if (length <= BUCKET_8)
            headVH = ARRAY_FREE_HEAD_8_VH;
        else if (length <= BUCKET_32)
            headVH = ARRAY_FREE_HEAD_32_VH;
        else if (length <= BUCKET_128)
            headVH = ARRAY_FREE_HEAD_128_VH;
        else
            headVH = ARRAY_FREE_HEAD_512_VH;

        while (true) {
            long oldTagged = (long) headVH.getVolatile();
            long oldRawHead = oldTagged & 0x0000FFFFFFFFFFFFL;
            ForeignMemory.setLong(pointer, oldRawHead);
            long nextGen = ((oldTagged >>> 48) + 1L) & 0xFFFFL;
            long newTagged = (nextGen << 48) | (pointer & 0x0000FFFFFFFFFFFFL);
            if (headVH.compareAndSet(oldTagged, newTagged))
                return;
        }
    }

    public static long get(long pointer) {
        if (pointer == 0L)
            throw new NullPointerException("Accessing NULL off-heap pointer!");
        int classId = classId(pointer);
        if (!Inheritance.isSubclassOf(classId, TypeRegister.ID_BUFFER))
            throw new IllegalArgumentException("Pointer 0x" + java.lang.Long.toHexString(pointer).toUpperCase() + " is Class ID " + classId + ", expected a subclass of Buffer");
        return ForeignMemory.getLong(pointer);
    }

    public static long get(long pointer, int index) {
        checkBounds(pointer, index);
        int classId = classId(pointer);
        if (!Inheritance.isSubclassOf(classId, TypeRegister.ID_BUFFER))
            throw new IllegalArgumentException("Pointer 0x" + java.lang.Long.toHexString(pointer).toUpperCase() + " is Class ID " + classId + ", expected a subclass of Buffer");
        return ForeignMemory.getLong(pointer + (index * 8L));
    }

    public static void set(long pointer, long value) {
        if (pointer == 0L)
            throw new NullPointerException("Writing to NULL off-heap pointer!");
        int classId = classId(pointer);
        if (!Inheritance.isSubclassOf(classId, TypeRegister.ID_BUFFER))
            throw new IllegalArgumentException("Pointer 0x" + java.lang.Long.toHexString(pointer).toUpperCase() + " is Class ID " + classId + ", expected a subclass of Buffer");
        ForeignMemory.setLong(pointer, value);
    }

    public static void set(long pointer, int index, long value) {
        checkBounds(pointer, index);
        int classId = classId(pointer);
        if (!Inheritance.isSubclassOf(classId, TypeRegister.ID_BUFFER))
            throw new IllegalArgumentException("Pointer 0x" + java.lang.Long.toHexString(pointer).toUpperCase() + " is Class ID " + classId + ", expected a subclass of Buffer");
        ForeignMemory.setLong(pointer + (index * 8L), value);
    }

    @Volatile
    public static long getVolatile(long pointer) {
        if (pointer == 0L)
            throw new NullPointerException("Reading from NULL off-heap pointer!");
        int classId = classId(pointer);
        if (!Inheritance.isSubclassOf(classId, TypeRegister.ID_BUFFER))
            throw new IllegalArgumentException("Pointer 0x" + java.lang.Long.toHexString(pointer).toUpperCase() + " is Class ID " + classId + ", expected a subclass of Buffer");
        return ForeignMemory.getVolatileLong(pointer);
    }

    @Volatile
    public static void setVolatile(long pointer, long value) {
        if (pointer == 0L)
            throw new NullPointerException("Writing to NULL off-heap pointer!");
        int classId = classId(pointer);
        if (!Inheritance.isSubclassOf(classId, TypeRegister.ID_BUFFER))
            throw new IllegalArgumentException("Pointer 0x" + java.lang.Long.toHexString(pointer).toUpperCase() + " is Class ID " + classId + ", expected a subclass of Buffer");
        ForeignMemory.setVolatileLong(pointer, value);
    }

    public static boolean compareAndSet(long pointer, long expected, long value) {
        if (pointer == 0L)
            throw new NullPointerException("Writing to NULL off-heap pointer!");
        int classId = classId(pointer);
        if (!Inheritance.isSubclassOf(classId, TypeRegister.ID_BUFFER))
            throw new IllegalArgumentException("Pointer 0x" + java.lang.Long.toHexString(pointer).toUpperCase() + " is Class ID " + classId + ", expected a subclass of Buffer");
        return ForeignMemory.compareAndSetLong(pointer, expected, value);
    }

    public static long getAndSet(long pointer, long value) {
        if (pointer == 0L)
            throw new NullPointerException("Writing to NULL off-heap pointer!");
        int classId = classId(pointer);
        if (!Inheritance.isSubclassOf(classId, TypeRegister.ID_BUFFER))
            throw new IllegalArgumentException("Pointer 0x" + java.lang.Long.toHexString(pointer).toUpperCase() + " is Class ID " + classId + ", expected a subclass of Buffer");
        return ForeignMemory.getAndSetLong(pointer, value);
    }

    private static void checkBounds(long pointer, int index) {
        if (pointer == 0L)
            throw new NullPointerException("Checking bounds on NULL off-heap pointer!");
        int len = length(pointer);
        if (index < 0 || index >= len)
            throw new IndexOutOfBoundsException("Index " + index + " out of bounds for off-heap length " + len + " (Ptr: 0x" + java.lang.Long.toHexString(pointer).toUpperCase() + ", Type: 0x" + Integer.toHexString(type(pointer)).toUpperCase() + ")");
    }

    public static int width(long pointer) {
        return ForeignMemory.getInt(pointer - 20L);
    }

    public static int height(long pointer) {
        return ForeignMemory.getInt(pointer - 16L);
    }

    public static int channels(long pointer) {
        return ForeignMemory.getInt(pointer - 12L);
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

    @Unsafe
    public static long unsafeGet(long pointer) {
        return ForeignMemory.getLong(pointer);
    }

    @Unsafe
    public static long unsafeGet(long pointer, int index) {
        return ForeignMemory.getLong(pointer + (index * 8L));
    }

    @Unsafe
    public static void setUnsafe(long pointer, long value) {
        ForeignMemory.setLong(pointer, value);
    }

    @Unsafe
    public static void setUnsafe(long pointer, int index, long value) {
        ForeignMemory.setLong(pointer + (index * 8L), value);
    }

    @Volatile
    public static long getVolatile(long pointer, int index) {
        checkBounds(pointer, index);
        return ForeignMemory.getVolatileLong(pointer + (index * 8L));
    }

    @Volatile
    public static void setVolatile(long pointer, int index, long value) {
        checkBounds(pointer, index);
        ForeignMemory.setVolatileLong(pointer + (index * 8L), value);
    }

    @Unsafe
    @Volatile
    public static long unsafeVolatileGet(long pointer) {
        return ForeignMemory.getVolatileLong(pointer);
    }

    @Unsafe
    @Volatile
    public static long unsafeVolatileGet(long pointer, int index) {
        return ForeignMemory.getVolatileLong(pointer + (index * 8L));
    }

    @Unsafe
    @Volatile
    public static void setUnsafeVolatile(long pointer, long value) {
        ForeignMemory.setVolatileLong(pointer, value);
    }

    @Unsafe
    @Volatile
    public static void setUnsafeVolatile(long pointer, int index, long value) {
        ForeignMemory.setVolatileLong(pointer + (index * 8L), value);
    }
}
