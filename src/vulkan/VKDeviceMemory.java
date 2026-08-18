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
import bit.Bit64;
import primitive.Long;


import nio.StringLookup;
@Draft
public final class VKDeviceMemory {

    @Required
    public static final int CLASS_ID = TypeRegister.ID_VK_DEVICE_MEMORY;

    public static final long MAX_VALUE = 9223372036854775807L;
    public static final long MIN_VALUE = -9223372036854775808L;

    public static final int TYPE_SINGLETON = TypeRegister.VK_DEVICE_MEMORY_SINGLETON; // 0xAA000002
    public static final int TYPE_ARRAY     = TypeRegister.VK_DEVICE_MEMORY_ARRAY;     // 0xBB000002
    public static final int TYPE_MATRIX    = TypeRegister.VK_DEVICE_MEMORY_POINTER;   // 0xCC000002

    private VKDeviceMemory() {}

    public static void freeAll() {
        // Bit64.freeAll() manages the shared singleton, array, and matrix slot arenas.
    }

    // --- ALLOCATION LAYER ---
    public static long allocateSingleton() {
        return Bit64.allocateSingleton(TYPE_SINGLETON);
    }

    public static long allocateArray(int length) {
        return Bit64.allocateArray(TYPE_ARRAY, length);
    }

    public static long allocateMatrix(int length) {
        return Bit64.allocateMatrix(TYPE_MATRIX, length);
    }

    // --- MUTATING EXPANSION LAYER ---
    public static long expandArray(long oldPointer, int newLength) {
        if (oldPointer == 0L) return allocateArray(newLength);
        int oldLength = length(oldPointer);
        long newPointer = allocateArray(newLength);
        int elementsToCopy = Math.min(oldLength, newLength);
        ForeignMemory.copy(oldPointer, newPointer, elementsToCopy * 8L);
        free(oldPointer);
        return newPointer;
    }

    public static long expandMatrix(long oldPointer, int newLength) {
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
        Bit64.free(pointer);
    }

    // --- DATA ACCESSORS & BOUNDS CHECKS ---
    public static long get(long pointer) {
        return ForeignMemory.getLong(pointer);
    }

    public static long get(long pointer, int index) { 
        checkBounds(pointer, index);
        return ForeignMemory.getLong(pointer + (index * 8L)); 
    }

    public static long getPointer(long matrixPointer, int index) { 
        if (matrixPointer == 0L) throw new NullPointerException(StringLookup.getJavaString(260));
        if (!isPointer(matrixPointer)) {
            throw new IllegalArgumentException(StringLookup.getJavaString(263) + Integer.toHexString(type(matrixPointer)).toUpperCase());
        }
        checkBounds(matrixPointer, index);
        return ForeignMemory.getLong(matrixPointer + (index * 8L)); 
    }

    public static void set(long pointer, long value) {
        ForeignMemory.setLong(pointer, value);
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
        if (matrixPointer == 0L) throw new NullPointerException(StringLookup.getJavaString(262));
        if (!isPointer(matrixPointer)) {
            throw new IllegalArgumentException(StringLookup.getJavaString(263) + Integer.toHexString(type(matrixPointer)).toUpperCase());
        }
        checkBounds(matrixPointer, index);
        ForeignMemory.setLong(matrixPointer + (index * 8L), targetPointer); 
    }

    private static void checkBounds(long pointer, int index) {
        if (pointer == 0L) throw new NullPointerException(StringLookup.getJavaString(33));
        int len = length(pointer);
        if (index < 0 || index >= len) {
            throw new IndexOutOfBoundsException(StringLookup.getJavaString(34) + index + StringLookup.getJavaString(834) + len + StringLookup.getJavaString(36) + java.lang.Long.toHexString(pointer).toUpperCase() + StringLookup.getJavaString(37) + Integer.toHexString(type(pointer)).toUpperCase() + StringLookup.getJavaString(18));
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
        if(matrixPointer == 0L) throw new NullPointerException(StringLookup.getJavaString(260));
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
        if(matrixPointer == 0L) throw new NullPointerException(StringLookup.getJavaString(262));
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
                throw new RuntimeException(StringLookup.getJavaString(840));
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
            throw new RuntimeException(StringLookup.getJavaString(841));
        }
    }

    /** Binds this memory to a raw VkImage handle (see VKImage.getImage). */
    public static void bindImage(long enginePtr, VkDevice device, long vkImage, long offset) {
        if (enginePtr == 0L || vkImage == 0L) return;
        long vkMemory = get(enginePtr);
        if (vkBindImageMemory(device, vkImage, vkMemory, offset) != VK_SUCCESS) {
            throw new RuntimeException(StringLookup.getJavaString(842));
        }
    }

    public static long map(long enginePtr, VkDevice device, long offset, long size) {
        if (enginePtr == 0L) return 0L;
        long vkMemory = get(enginePtr);
        try (MemoryStack stack = MemoryStack.stackPush()) {
            PointerBuffer pData = stack.mallocPointer(1);
            if (vkMapMemory(device, vkMemory, offset, size, 0, pData) != VK_SUCCESS) {
                throw new RuntimeException(StringLookup.getJavaString(843));
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
