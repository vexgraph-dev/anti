package vulkan;

import org.lwjgl.vulkan.VkDevice;
import org.lwjgl.vulkan.VkSemaphoreCreateInfo;
import org.lwjgl.system.MemoryStack;
import java.nio.LongBuffer;
import static org.lwjgl.vulkan.VK10.*;


import annotation.Unsafe;
import annotation.Volatile;

import annotation.Required;
import nio.ForeignMemory;
import oop.TypeRegister;

import java.lang.foreign.Arena;
import java.lang.invoke.MethodHandles;
import java.lang.invoke.VarHandle;

public final class Semaphore {

    @Required
    public static final int CLASS_ID = TypeRegister.ID_SEMAPHORE;

    public static final long MAX_VALUE = 9223372036854775807L;
    public static final long MIN_VALUE = -9223372036854775808L;

    public static final int TYPE_SINGLETON = TypeRegister.SEMAPHORE_SINGLETON; // 0xAA000002
    public static final int TYPE_ARRAY     = TypeRegister.SEMAPHORE_ARRAY;     // 0xBB000002
    public static final int TYPE_MATRIX    = TypeRegister.SEMAPHORE_POINTER;   // 0xCC000002

    private static final int DEFAULT_CAPACITY = 1024;

    // Memory Block Sizes (Including 8-byte headers: 4B typeId + 4B length)
    private static final long SINGLETON_SLOT_SIZE = 16L; // 8B header + 8B data
    private static final long POOLED_ARRAY_SIZE = 8L + (DEFAULT_CAPACITY * 8L);  // 8200 Bytes
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
            SINGLETON_FREE_HEAD_VH = lookup.findStaticVarHandle(Semaphore.class, "singletonFreeHead", long.class);
            ARRAY_FREE_HEAD_VH = lookup.findStaticVarHandle(Semaphore.class, "arrayFreeHead", long.class);
            MATRIX_FREE_HEAD_VH = lookup.findStaticVarHandle(Semaphore.class, "matrixFreeHead", long.class);

            SINGLETON_EXPANDING_VH = lookup.findStaticVarHandle(Semaphore.class, "singletonExpanding", int.class);
            ARRAY_EXPANDING_VH = lookup.findStaticVarHandle(Semaphore.class, "arrayExpanding", int.class);
            MATRIX_EXPANDING_VH = lookup.findStaticVarHandle(Semaphore.class, "matrixExpanding", int.class);
        } catch (ReflectiveOperationException e) {
            throw new ExceptionInInitializerError(e);
        }

        poolArena = Arena.ofShared();
        active = true;


        expandSingletonPool();
        expandArrayPool();
        expandMatrixPool();
    }

    private Semaphore() {}

    private static void checkActive() {
        if (!active) throw new IllegalStateException("Semaphore subsystem is not active!");
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
                ForeignMemory.putInt(base, TYPE_SINGLETON);
                ForeignMemory.putInt(base + 4L, 1);
                ForeignMemory.putLong(rawHead, 0L);
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
                    ForeignMemory.putInt(base, TYPE_ARRAY);
                    ForeignMemory.putInt(base + 4L, length);
                    return rawHead;
                }
            }
        } else {
            // Oversized: Pure FFM C malloc downcall (0% GC)
            long totalBytes = 8L + (length * 8L);
            long alignedBytes = (totalBytes + 7L) & ~7L;
            long base = ForeignMemory.allocateNative(alignedBytes);
            ForeignMemory.putInt(base, TYPE_ARRAY);
            ForeignMemory.putInt(base + 4L, length);
            return base + 8L;
        }
    }

    public static long allocateMatrix(int length) {
        checkActive();
        if (length <= DEFAULT_CAPACITY) {
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
                    long base = rawHead - 8L;
                    ForeignMemory.putInt(base, TYPE_MATRIX);
                    ForeignMemory.putInt(base + 4L, length);
                    return rawHead;
                }
            }
        } else {
            // Oversized: Pure FFM C malloc downcall (0% GC)
            long totalBytes = 8L + (length * 8L);
            long base = ForeignMemory.allocateNative(totalBytes);
            ForeignMemory.putInt(base, TYPE_MATRIX);
            ForeignMemory.putInt(base + 4L, length);
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
        ForeignMemory.copy(oldPointer, newPointer, elementsToCopy * 8L);
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

        ForeignMemory.putInt(base, 0);
        ForeignMemory.putInt(base + 4L, -1);

        if (TypeRegister.isSingleton(type)) {
            while (true) {
                long oldTagged = singletonFreeHead;
                long oldRawHead = oldTagged & 0x0000FFFFFFFFFFFFL;

                ForeignMemory.putLong(pointer, oldRawHead);

                long nextGen = ((oldTagged >>> 48) + 1L) & 0xFFFFL;
                long newTagged = (nextGen << 48) | (pointer & 0x0000FFFFFFFFFFFFL);

                if (SINGLETON_FREE_HEAD_VH.compareAndSet(oldTagged, newTagged)) return;
            }
        } else if (TypeRegister.isArray(type)) {
            if (length > DEFAULT_CAPACITY) {
                // Oversized: Free back to OS immediately via C free()
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
        } else if (TypeRegister.isPointer(type)) {
            if (length > DEFAULT_CAPACITY) {
                // Oversized: Free back to OS immediately via C free()
                ForeignMemory.freeNative(base);
                return;
            }
            while (true) {
                long oldTagged = matrixFreeHead;
                long oldRawHead = oldTagged & 0x0000FFFFFFFFFFFFL;

                ForeignMemory.putLong(pointer, oldRawHead);

                long nextGen = ((oldTagged >>> 48) + 1L) & 0xFFFFL;
                long newTagged = (nextGen << 48) | (pointer & 0x0000FFFFFFFFFFFFL);

                if (MATRIX_FREE_HEAD_VH.compareAndSet(oldTagged, newTagged)) return;
            }
        }
    }

    // --- DATA ACCESSORS & BOUNDS CHECKS ---
    public static long get(long pointer) {
        return primitive.Long.get(pointer);
    }

    public static long get(long pointer, int index) { 
        checkBounds(pointer, index);
        return ForeignMemory.getLong(pointer + (index * 8L)); 
    }

    public static long getPointer(long matrixPointer, int index) { 
        if (matrixPointer == 0L) throw new NullPointerException("Accessing NULL matrix pointer!");
        if (!isPointer(matrixPointer)) {
            throw new IllegalArgumentException("Expected Pointer Array (Matrix), but got Type: 0x" + Integer.toHexString(type(matrixPointer)).toUpperCase());
        }
        checkBounds(matrixPointer, index);
        return ForeignMemory.getLong(matrixPointer + (index * 8L)); 
    }

    public static void set(long pointer, long value) {
        if (pointer == 0L) throw new NullPointerException("Writing to NULL off-heap pointer!");
        ForeignMemory.putLong(pointer, value);
    }

    public static void set(long pointer, int index, long value) { 
        checkBounds(pointer, index);
        ForeignMemory.putLong(pointer + (index * 8L), value); 
    }

    @Volatile
    public static long getVolatile(long pointer) {
        if (pointer == 0L) throw new NullPointerException("Reading from NULL off-heap pointer!");
        return ForeignMemory.getLongVolatile(pointer);
    }

    @Volatile
    public static void setVolatile(long pointer, long value) {
        if (pointer == 0L) throw new NullPointerException("Writing to NULL off-heap pointer!");
        ForeignMemory.putLongVolatile(pointer, value);
    }

    public static boolean compareAndSet(long pointer, long expected, long value) {
        if (pointer == 0L) throw new NullPointerException("Writing to NULL off-heap pointer!");
        return ForeignMemory.compareAndSetLong(pointer, expected, value);
    }

    public static long getAndSet(long pointer, long value) {
        if (pointer == 0L) throw new NullPointerException("Writing to NULL off-heap pointer!");
        return ForeignMemory.getAndSetLong(pointer, value);
    }

    public static void setPointer(long matrixPointer, int index, long targetPointer) { 
        if (matrixPointer == 0L) throw new NullPointerException("Writing to NULL matrix pointer!");
        if (!isPointer(matrixPointer)) {
            throw new IllegalArgumentException("Expected Pointer Array (Matrix), but got Type: 0x" + Integer.toHexString(type(matrixPointer)).toUpperCase());
        }
        checkBounds(matrixPointer, index);
        ForeignMemory.putLong(matrixPointer + (index * 8L), targetPointer); 
    }

    private static void checkBounds(long pointer, int index) {
        if (pointer == 0L) throw new NullPointerException("Checking bounds on NULL off-heap pointer!");
        int len = length(pointer);
        if (index < 0 || index >= len) {
            throw new IndexOutOfBoundsException("Index " + index + " out of bounds for off-heap long length " + len + " (Ptr: 0x" + java.lang.Long.toHexString(pointer).toUpperCase() + ", Type: 0x" + Integer.toHexString(type(pointer)).toUpperCase() + ")");
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

    // --- AUTOGENERATED UNSAFE & VOLATILE VARIANTS ---

    @Unsafe
    public static long unsafeGet(long pointer) {
        return ForeignMemory.getLong(pointer);
    }

    @Unsafe
    public static long unsafeGet(long pointer, int index) {
        return ForeignMemory.getLong(pointer + (index * 8L));
    }

    @Unsafe
    public static long unsafeGetPointer(long matrixPointer, int index) {
        return ForeignMemory.getLong(matrixPointer + (index * 8L));
    }

    @Unsafe
    public static void unsafeSet(long pointer, long value) {
        ForeignMemory.putLong(pointer, value);
    }

    @Unsafe
    public static void unsafeSet(long pointer, int index, long value) {
        ForeignMemory.putLong(pointer + (index * 8L), value);
    }

    @Unsafe
    public static void unsafeSetPointer(long matrixPointer, int index, long targetPointer) {
        ForeignMemory.putLong(matrixPointer + (index * 8L), targetPointer);
    }

    @Volatile
    public static long getVolatile(long pointer, int index) {
        checkBounds(pointer, index);
        return ForeignMemory.getLongVolatile(pointer + (index * 8L));
    }

    @Volatile
    public static long getPointerVolatile(long matrixPointer, int index) {
        if(matrixPointer == 0L) throw new NullPointerException("Accessing NULL matrix pointer!");
        checkBounds(matrixPointer, index);
        return ForeignMemory.getLongVolatile(matrixPointer + (index * 8L));
    }

    @Volatile
    public static void setVolatile(long pointer, int index, long value) {
        checkBounds(pointer, index);
        ForeignMemory.putLongVolatile(pointer + (index * 8L), value);
    }

    @Volatile
    public static void setPointerVolatile(long matrixPointer, int index, long targetPointer) {
        if(matrixPointer == 0L) throw new NullPointerException("Writing to NULL matrix pointer!");
        checkBounds(matrixPointer, index);
        ForeignMemory.putLongVolatile(matrixPointer + (index * 8L), targetPointer);
    }

    @Unsafe
    @Volatile
    public static long unsafeVolatileGet(long pointer) {
        return ForeignMemory.getLongVolatile(pointer);
    }

    @Unsafe
    @Volatile
    public static long unsafeVolatileGet(long pointer, int index) {
        return ForeignMemory.getLongVolatile(pointer + (index * 8L));
    }

    @Unsafe
    @Volatile
    public static long unsafeVolatileGetPointer(long matrixPointer, int index) {
        return ForeignMemory.getLongVolatile(matrixPointer + (index * 8L));
    }

    @Unsafe
    @Volatile
    public static void unsafeVolatileSet(long pointer, long value) {
        ForeignMemory.putLongVolatile(pointer, value);
    }

    @Unsafe
    @Volatile
    public static void unsafeVolatileSet(long pointer, int index, long value) {
        ForeignMemory.putLongVolatile(pointer + (index * 8L), value);
    }

    @Unsafe
    @Volatile
    public static void unsafeVolatileSetPointer(long matrixPointer, int index, long targetPointer) {
        ForeignMemory.putLongVolatile(matrixPointer + (index * 8L), targetPointer);
    }


    // --- VULKAN FACTORY ---
    public static long create(VkDevice device) {
        long enginePtr = allocateSingleton();
        try (MemoryStack stack = MemoryStack.stackPush()) {
            VkSemaphoreCreateInfo createInfo = VkSemaphoreCreateInfo.calloc(stack)
                    .sType(VK_STRUCTURE_TYPE_SEMAPHORE_CREATE_INFO);
            LongBuffer pSemaphore = stack.mallocLong(1);
            if (vkCreateSemaphore(device, createInfo, null, pSemaphore) != VK_SUCCESS) {
                throw new RuntimeException("Failed to create Vulkan Semaphore!");
            }
            set(enginePtr, pSemaphore.get(0));
        }
        return enginePtr;
    }

    public static void destroy(long enginePtr, VkDevice device) {
        if(enginePtr == 0L) return;
        long vkHandle = get(enginePtr);
        if(vkHandle != 0L) {
            vkDestroySemaphore(device, vkHandle, null);
            set(enginePtr, 0L);
        }
        free(enginePtr);
    }

}
