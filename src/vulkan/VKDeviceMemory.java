package vulkan;

import annotation.Draft;

import org.lwjgl.vulkan.VkDevice;
import org.lwjgl.vulkan.VkMemoryAllocateInfo;
import org.lwjgl.system.MemoryStack;
import org.lwjgl.PointerBuffer;
import java.nio.LongBuffer;
import static org.lwjgl.vulkan.VK10.*;


import annotation.Unsafe;
import annotation.Volatile;

import annotation.Required;
import nio.ForeignMemory;
import oop.TypeRegister;
import primitive.Long;

import java.lang.foreign.Arena;
import java.lang.invoke.MethodHandles;
import java.lang.invoke.VarHandle;

@Draft
public final class VKDeviceMemory {

    @Required
    public static final int CLASS_ID = TypeRegister.ID_VK_DEVICE_MEMORY;

    public static final long MAX_VALUE = 9223372036854775807L;
    public static final long MIN_VALUE = -9223372036854775808L;

    public static final int TYPE_SINGLETON = TypeRegister.VK_DEVICE_MEMORY_SINGLETON; // 0xAA000002
    public static final int TYPE_ARRAY     = TypeRegister.VK_DEVICE_MEMORY_ARRAY;     // 0xBB000002
    public static final int TYPE_MATRIX    = TypeRegister.VK_DEVICE_MEMORY_POINTER;   // 0xCC000002

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
            SINGLETON_FREE_HEAD_VH = lookup.findStaticVarHandle(VKDeviceMemory.class, "singletonFreeHead", long.class);
            ARRAY_FREE_HEAD_VH = lookup.findStaticVarHandle(VKDeviceMemory.class, "arrayFreeHead", long.class);
            MATRIX_FREE_HEAD_VH = lookup.findStaticVarHandle(VKDeviceMemory.class, "matrixFreeHead", long.class);

            SINGLETON_EXPANDING_VH = lookup.findStaticVarHandle(VKDeviceMemory.class, "singletonExpanding", int.class);
            ARRAY_EXPANDING_VH = lookup.findStaticVarHandle(VKDeviceMemory.class, "arrayExpanding", int.class);
            MATRIX_EXPANDING_VH = lookup.findStaticVarHandle(VKDeviceMemory.class, "matrixExpanding", int.class);
        } catch (ReflectiveOperationException e) {
            throw new ExceptionInInitializerError(e);
        }

        poolArena = Arena.ofShared();
        active = true;


        expandSingletonPool();
        expandArrayPool();
        expandMatrixPool();
    }

    private VKDeviceMemory() {}

    private static void checkActive() {
        if (!active) throw new IllegalStateException("VKDeviceMemory subsystem is not active!");
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

                ForeignMemory.setLong(userPtr, oldRawHead);

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

                ForeignMemory.setLong(userPtr, oldRawHead);

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

                ForeignMemory.setLong(userPtr, oldRawHead);

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
                ForeignMemory.setInt(base, TYPE_SINGLETON);
                ForeignMemory.setInt(base + 4L, 1);
                ForeignMemory.setLong(rawHead, 0L);
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
                    ForeignMemory.setInt(base, TYPE_ARRAY);
                    ForeignMemory.setInt(base + 4L, length);
                    return rawHead;
                }
            }
        } else {
            // Oversized: Pure FFM C malloc downcall (0% GC)
            long totalBytes = 8L + (length * 8L);
            long alignedBytes = (totalBytes + 7L) & ~7L;
            long base = ForeignMemory.allocateNative(alignedBytes);
            ForeignMemory.setInt(base, TYPE_ARRAY);
            ForeignMemory.setInt(base + 4L, length);
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
                    ForeignMemory.setInt(base, TYPE_MATRIX);
                    ForeignMemory.setInt(base + 4L, length);
                    return rawHead;
                }
            }
        } else {
            // Oversized: Pure FFM C malloc downcall (0% GC)
            long totalBytes = 8L + (length * 8L);
            long base = ForeignMemory.allocateNative(totalBytes);
            ForeignMemory.setInt(base, TYPE_MATRIX);
            ForeignMemory.setInt(base + 4L, length);
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

        ForeignMemory.setInt(base, 0);
        ForeignMemory.setInt(base + 4L, -1);

        if (TypeRegister.isSingleton(type)) {
            while (true) {
                long oldTagged = singletonFreeHead;
                long oldRawHead = oldTagged & 0x0000FFFFFFFFFFFFL;

                ForeignMemory.setLong(pointer, oldRawHead);

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

                ForeignMemory.setLong(pointer, oldRawHead);

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

                ForeignMemory.setLong(pointer, oldRawHead);

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
        Long.set(pointer, value);
    }

    public static void set(long pointer, int index, long value) { 
        checkBounds(pointer, index);
        ForeignMemory.setLong(pointer + (index * 8L), value); 
    }

    @Volatile
    public static long getVolatile(long pointer) {
        return Long.getVolatile(pointer);
    }

    @Volatile
    public static void setVolatile(long pointer, long value) {
        Long.setVolatile(pointer, value);
    }

    public static boolean compareAndSet(long pointer, long expected, long value) {
        return Long.compareAndSet(pointer, expected, value);
    }

    public static long getAndSet(long pointer, long value) {
        return Long.getAndSet(pointer, value);
    }

    public static void setPointer(long matrixPointer, int index, long targetPointer) { 
        if (matrixPointer == 0L) throw new NullPointerException("Writing to NULL matrix pointer!");
        if (!isPointer(matrixPointer)) {
            throw new IllegalArgumentException("Expected Pointer Array (Matrix), but got Type: 0x" + Integer.toHexString(type(matrixPointer)).toUpperCase());
        }
        checkBounds(matrixPointer, index);
        ForeignMemory.setLong(matrixPointer + (index * 8L), targetPointer); 
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
    public static void setUnsafe(long pointer, long value) {
        ForeignMemory.setLong(pointer, value);
    }

    @Unsafe
    public static void setUnsafe(long pointer, int index, long value) {
        ForeignMemory.setLong(pointer + (index * 8L), value);
    }

    @Unsafe
    public static void unsafeSetPointer(long matrixPointer, int index, long targetPointer) {
        ForeignMemory.setLong(matrixPointer + (index * 8L), targetPointer);
    }

    @Volatile
    public static long getVolatile(long pointer, int index) {
        checkBounds(pointer, index);
        return ForeignMemory.getVolatileLong(pointer + (index * 8L));
    }

    @Volatile
    public static long getPointerVolatile(long matrixPointer, int index) {
        if(matrixPointer == 0L) throw new NullPointerException("Accessing NULL matrix pointer!");
        checkBounds(matrixPointer, index);
        return ForeignMemory.getVolatileLong(matrixPointer + (index * 8L));
    }

    @Volatile
    public static void setVolatile(long pointer, int index, long value) {
        checkBounds(pointer, index);
        ForeignMemory.setVolatileLong(pointer + (index * 8L), value);
    }

    @Volatile
    public static void setPointerVolatile(long matrixPointer, int index, long targetPointer) {
        if(matrixPointer == 0L) throw new NullPointerException("Writing to NULL matrix pointer!");
        checkBounds(matrixPointer, index);
        ForeignMemory.setVolatileLong(matrixPointer + (index * 8L), targetPointer);
    }

    @Unsafe
    @Volatile
    public static long getUnsafeVolatile(long pointer) {
        return ForeignMemory.getVolatileLong(pointer);
    }

    @Unsafe
    @Volatile
    public static long getUnsafeVolatile(long pointer, int index) {
        return ForeignMemory.getVolatileLong(pointer + (index * 8L));
    }

    @Unsafe
    @Volatile
    public static long getUnsafeVolatilePointer(long matrixPointer, int index) {
        return ForeignMemory.getVolatileLong(matrixPointer + (index * 8L));
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

    @Unsafe
    @Volatile
    public static void setUnsafeVolatilePointer(long matrixPointer, int index, long targetPointer) {
        ForeignMemory.setVolatileLong(matrixPointer + (index * 8L), targetPointer);
    }


    // --- VULKAN FACTORY ---
    public static long allocate(VkDevice device, long size, int memoryTypeIndex) {
        long enginePtr = allocateSingleton();
        try (MemoryStack stack = MemoryStack.stackPush()) {
            VkMemoryAllocateInfo allocInfo = VkMemoryAllocateInfo.calloc(stack)
                    .sType(VK_STRUCTURE_TYPE_MEMORY_ALLOCATE_INFO)
                    .allocationSize(size)
                    .memoryTypeIndex(memoryTypeIndex);

            LongBuffer pMemory = stack.mallocLong(1);
            if (vkAllocateMemory(device, allocInfo, null, pMemory) != VK_SUCCESS) {
                throw new RuntimeException("Failed to allocate VKDeviceMemory!");
            }
            set(enginePtr, pMemory.get(0));
        }
        return enginePtr;
    }

    public static void bindBuffer(long enginePtr, VkDevice device, long engineBufferPtr, long offset) {
        if(enginePtr == 0L || engineBufferPtr == 0L) return;
        long vkMemory = get(enginePtr);
        long vkBuffer = VKBuffer.get(engineBufferPtr);
        
        if (vkBindBufferMemory(device, vkBuffer, vkMemory, offset) != VK_SUCCESS) {
            throw new RuntimeException("Failed to bind VKBuffer to VKDeviceMemory!");
        }
    }

    public static long map(long enginePtr, VkDevice device, long offset, long size) {
        if (enginePtr == 0L) return 0L;
        long vkMemory = get(enginePtr);
        try (MemoryStack stack = MemoryStack.stackPush()) {
            PointerBuffer pData = stack.mallocPointer(1);
            if (vkMapMemory(device, vkMemory, offset, size, 0, pData) != VK_SUCCESS) {
                throw new RuntimeException("Failed to map VKDeviceMemory!");
            }
            return pData.get(0);
        }
    }

    public static void unmap(long enginePtr, VkDevice device) {
        if (enginePtr == 0L) return;
        long vkMemory = get(enginePtr);
        vkUnmapMemory(device, vkMemory);
    }

    public static void destroy(long enginePtr, VkDevice device) {
        if(enginePtr == 0L) return;
        long vkHandle = get(enginePtr);
        if(vkHandle != 0L) {
            vkFreeMemory(device, vkHandle, null);
            set(enginePtr, 0L);
        }
        free(enginePtr);
    }

}
