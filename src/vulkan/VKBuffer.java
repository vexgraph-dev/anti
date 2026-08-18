package vulkan;

import annotation.Draft;

import org.lwjgl.vulkan.VkDevice;
import org.lwjgl.vulkan.VkBufferCreateInfo;
import org.lwjgl.system.MemoryStack;
import java.nio.LongBuffer;
import static org.lwjgl.vulkan.VK10.*;


import annotation.Unsafe;
import annotation.Volatile;

import annotation.Required;
import nio.ForeignMemory;
import oop.TypeRegister;
import bit.Bit64;


import nio.StringLookup;
@Draft
public final class VKBuffer {

    @Required
    public static final int CLASS_ID = TypeRegister.ID_VK_BUFFER;

    public static final long MAX_VALUE = 9223372036854775807L;
    public static final long MIN_VALUE = -9223372036854775808L;

    public static final int TYPE_SINGLETON = TypeRegister.VK_BUFFER_SINGLETON; // 0xAA000002
    public static final int TYPE_ARRAY     = TypeRegister.VK_BUFFER_ARRAY;     // 0xBB000002
    public static final int TYPE_MATRIX    = TypeRegister.VK_BUFFER_POINTER;   // 0xCC000002

    private VKBuffer() {}

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
        if (pointer == 0L) throw new NullPointerException(StringLookup.getJavaString(31));
        ForeignMemory.setLong(pointer, value);
    }

    public static void set(long pointer, int index, long value) { 
        checkBounds(pointer, index);
        ForeignMemory.setLong(pointer + (index * 8L), value); 
    }

    @Volatile
    public static long getVolatile(long pointer) {
        if (pointer == 0L) throw new NullPointerException(StringLookup.getJavaString(32));
        return ForeignMemory.getVolatileLong(pointer);
    }

    @Volatile
    public static void setVolatile(long pointer, long value) {
        if (pointer == 0L) throw new NullPointerException(StringLookup.getJavaString(31));
        ForeignMemory.setVolatileLong(pointer, value);
    }

    public static boolean compareAndSet(long pointer, long expected, long value) {
        if (pointer == 0L) throw new NullPointerException(StringLookup.getJavaString(31));
        return ForeignMemory.compareAndSetLong(pointer, expected, value);
    }

    public static long getAndSet(long pointer, long value) {
        if (pointer == 0L) throw new NullPointerException(StringLookup.getJavaString(31));
        return ForeignMemory.getAndSetLong(pointer, value);
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
    public static long create(VkDevice device, long size, int usage, int sharingMode) {
        long enginePtr = allocateSingleton();
        try (MemoryStack stack = MemoryStack.stackPush()) {
            VkBufferCreateInfo bufferInfo = VkBufferCreateInfo.calloc(stack)
                    .sType(VK_STRUCTURE_TYPE_BUFFER_CREATE_INFO)
                    .size(size)
                    .usage(usage)
                    .sharingMode(sharingMode);

            LongBuffer pBuffer = stack.mallocLong(1);
            if (vkCreateBuffer(device, bufferInfo, null, pBuffer) != VK_SUCCESS) {
                throw new RuntimeException(StringLookup.getJavaString(838));
            }
            set(enginePtr, pBuffer.get(0));
        }
        return enginePtr;
    }

    public static void destroy(long enginePtr, VkDevice device) {
        if(enginePtr == 0L) return;
        long vkHandle = get(enginePtr);
        if(vkHandle != 0L) {
            vkDestroyBuffer(device, vkHandle, null);
            set(enginePtr, 0L);
        }
        free(enginePtr);
    }

}
