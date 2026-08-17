package vulkan;

import annotation.Draft;

import org.lwjgl.vulkan.VkDevice;
import org.lwjgl.vulkan.VkGraphicsPipelineCreateInfo;
import org.lwjgl.system.MemoryStack;
import java.nio.LongBuffer;
import static org.lwjgl.vulkan.VK10.*;


import annotation.Unsafe;
import annotation.Volatile;

import annotation.Required;
import nio.ForeignMemory;
import oop.TypeRegister;
import bit.Bit64;


@Draft
public final class VKPipeline {

    @Required
    public static final int CLASS_ID = TypeRegister.ID_VK_PIPELINE;

    public static final long MAX_VALUE = 9223372036854775807L;
    public static final long MIN_VALUE = -9223372036854775808L;

    public static final int TYPE_SINGLETON = TypeRegister.VK_PIPELINE_SINGLETON; // 0xAA000002
    public static final int TYPE_ARRAY     = TypeRegister.VK_PIPELINE_ARRAY;     // 0xBB000002
    public static final int TYPE_MATRIX    = TypeRegister.VK_PIPELINE_POINTER;   // 0xCC000002

    private VKPipeline() {}

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
        if (matrixPointer == 0L) throw new NullPointerException("Accessing NULL matrix pointer!");
        if (!isPointer(matrixPointer)) {
            throw new IllegalArgumentException("Expected Pointer Array (Matrix), but got Type: 0x" + Integer.toHexString(type(matrixPointer)).toUpperCase());
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
        return ForeignMemory.getVolatileLong(pointer);
    }

    @Volatile
    public static void setVolatile(long pointer, long value) {
        ForeignMemory.setVolatileLong(pointer, value);
    }

    public static boolean compareAndSet(long pointer, long expected, long value) {
        return ForeignMemory.compareAndSetLong(pointer, expected, value);
    }

    public static long getAndSet(long pointer, long value) {
        return ForeignMemory.getAndSetLong(pointer, value);
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
    public static long getUnsafe(long pointer) {
        return ForeignMemory.getLong(pointer);
    }

    @Unsafe
    public static long getUnsafe(long pointer, int index) {
        return ForeignMemory.getLong(pointer + (index * 8L));
    }

    @Unsafe
    public static long getUnsafePointer(long matrixPointer, int index) {
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
    public static void setUnsafePointer(long matrixPointer, int index, long targetPointer) {
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
    public static long createGraphicsPipeline(VkDevice device, long pipelineCache, VkGraphicsPipelineCreateInfo.Buffer createInfo) {
        long enginePtr = allocateSingleton();
        try (MemoryStack stack = MemoryStack.stackPush()) {
            LongBuffer pPipelines = stack.mallocLong(1);
            if (vkCreateGraphicsPipelines(device, pipelineCache, createInfo, null, pPipelines) != VK_SUCCESS) {
                throw new RuntimeException("Failed to create VKPipeline!");
            }
            set(enginePtr, pPipelines.get(0));
        }
        return enginePtr;
    }

    // danger, clean, and must be freed.
    public static void destroy(long enginePtr, VkDevice device) {
        if(enginePtr == 0L) return;
        long vkHandle = get(enginePtr);
        if(vkHandle != 0L) {
            vkDestroyPipeline(device, vkHandle, null);
            set(enginePtr, 0L);
        }
        free(enginePtr);
    }

}
