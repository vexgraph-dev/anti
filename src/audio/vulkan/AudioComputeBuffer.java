package audio.vulkan;

import annotation.Draft;
import annotation.HotCode;
import annotation.Intention;
import annotation.Required;
import annotation.Volatile;
import bit.Bit64;
import nio.ForeignMemory;
import oop.TypeRegister;
import org.lwjgl.system.MemoryStack;
import org.lwjgl.vulkan.VkBufferCreateInfo;
import org.lwjgl.vulkan.VkDevice;
import org.lwjgl.vulkan.VkMemoryAllocateInfo;
import org.lwjgl.vulkan.VkMemoryRequirements;
import org.lwjgl.vulkan.VkPhysicalDevice;
import org.lwjgl.vulkan.VkPhysicalDeviceMemoryProperties;

import java.nio.LongBuffer;

import static org.lwjgl.vulkan.VK10.*;

/**
 * Off-heap pool for Vulkan HOST_VISIBLE | HOST_COHERENT GPU audio staging buffers.
 *
 * On Apple Silicon (unified memory) + MoltenVK, HOST_VISIBLE|HOST_COHERENT memory
 * IS the same physical DRAM as CPU arena memory — binding one of these buffers to a
 * compute shader and writing to it from the CPU is a true zero-copy path.
 *
 * The engine pointer's data slot stores a pointer to an off-heap struct holding
 * four long handles:
 *   [0] vkBufferHandle  — the VkBuffer handle (long)
 *   [1] vkMemoryHandle  — the VkDeviceMemory handle (long)
 *   [2] mappedAddress   — CPU-visible mapped pointer (long, raw off-heap address)
 *   [3] sizeInBytes     — allocation size in bytes (long)
 */
@Draft
@Intention("Off-heap lockless pool of HOST_VISIBLE Vulkan buffers for zero-copy GPU audio DSP")
@Volatile
@HotCode
public final class AudioComputeBuffer {

    @Required
    public static final int CLASS_ID = TypeRegister.ID_AUDIO_COMPUTE_BUFFER;
    public static final int TYPE_SINGLETON = TypeRegister.AUDIO_COMPUTE_BUFFER_SINGLETON;

    // Struct field offsets within the 32B payload
    public static final long OFFSET_VK_BUFFER  = 0L;   // VkBuffer handle (long)
    public static final long OFFSET_VK_MEMORY  = 8L;   // VkDeviceMemory handle (long)
    public static final long OFFSET_MAPPED_PTR = 16L;  // CPU-side mapped address (long)
    public static final long OFFSET_SIZE_BYTES = 24L;  // allocation size in bytes (long)

    private static final long STRUCT_SIZE = 32L;

    private AudioComputeBuffer() {}

    public static void freeAll() {
        // Bit64.freeAll() manages the shared singleton slot arena.
    }

    private static long struct(long ptr) {
        return ForeignMemory.getLong(ptr);
    }

    /**
     * Allocates a VkBuffer + VkDeviceMemory of the given byte size on HOST_VISIBLE|HOST_COHERENT
     * memory and maps it permanently. Returns the off-heap engine pointer to the slot.
     *
     * On Apple Silicon (MoltenVK unified memory) this is a true zero-copy CPU↔GPU buffer.
     *
     * @param device         the active VkDevice
     * @param physicalDevice the VkPhysicalDevice (for memory type queries)
     * @param sizeBytes      size of the PCM buffer in bytes
     * @param usage          VkBufferUsageFlags — caller must include VK_BUFFER_USAGE_STORAGE_BUFFER_BIT
     */
    public static long create(VkDevice device, VkPhysicalDevice physicalDevice, long sizeBytes, int usage) {
        long enginePtr = Bit64.allocateSingleton(TYPE_SINGLETON);
        long struct = ForeignMemory.allocateNative(STRUCT_SIZE);
        ForeignMemory.setLong(enginePtr, struct);
        try (MemoryStack stack = MemoryStack.stackPush()) {

            // 1. Create VkBuffer
            VkBufferCreateInfo bufInfo = VkBufferCreateInfo.calloc(stack)
                    .sType(VK_STRUCTURE_TYPE_BUFFER_CREATE_INFO)
                    .size(sizeBytes)
                    .usage(usage)
                    .sharingMode(VK_SHARING_MODE_EXCLUSIVE);

            LongBuffer pBuffer = stack.mallocLong(1);
            if (vkCreateBuffer(device, bufInfo, null, pBuffer) != VK_SUCCESS) {
                throw new RuntimeException("AudioComputeBuffer: vkCreateBuffer failed (size=" + sizeBytes + ")");
            }
            long vkBuf = pBuffer.get(0);

            // 2. Query memory requirements
            VkMemoryRequirements memReq = VkMemoryRequirements.calloc(stack);
            vkGetBufferMemoryRequirements(device, vkBuf, memReq);

            // 3. Find HOST_VISIBLE | HOST_COHERENT memory type index
            int memTypeIndex = findMemoryType(
                    physicalDevice,
                    memReq.memoryTypeBits(),
                    VK_MEMORY_PROPERTY_HOST_VISIBLE_BIT | VK_MEMORY_PROPERTY_HOST_COHERENT_BIT,
                    stack
            );

            // 4. Allocate VkDeviceMemory
            VkMemoryAllocateInfo allocInfo = VkMemoryAllocateInfo.calloc(stack)
                    .sType(VK_STRUCTURE_TYPE_MEMORY_ALLOCATE_INFO)
                    .allocationSize(memReq.size())
                    .memoryTypeIndex(memTypeIndex);

            LongBuffer pMemory = stack.mallocLong(1);
            if (vkAllocateMemory(device, allocInfo, null, pMemory) != VK_SUCCESS) {
                vkDestroyBuffer(device, vkBuf, null);
                throw new RuntimeException("AudioComputeBuffer: vkAllocateMemory failed");
            }
            long vkMem = pMemory.get(0);

            // 5. Bind buffer to memory
            vkBindBufferMemory(device, vkBuf, vkMem, 0L);

            // 6. Map permanently — gives us a raw CPU pointer into GPU-accessible DRAM
            org.lwjgl.PointerBuffer pMapped = stack.mallocPointer(1);
            if (vkMapMemory(device, vkMem, 0L, sizeBytes, 0, pMapped) != VK_SUCCESS) {
                vkFreeMemory(device, vkMem, null);
                vkDestroyBuffer(device, vkBuf, null);
                throw new RuntimeException("AudioComputeBuffer: vkMapMemory failed");
            }
            long mappedAddr = pMapped.get(0);

            // 7. Store into struct payload
            ForeignMemory.setLong(struct + OFFSET_VK_BUFFER,  vkBuf);
            ForeignMemory.setLong(struct + OFFSET_VK_MEMORY,  vkMem);
            ForeignMemory.setLong(struct + OFFSET_MAPPED_PTR, mappedAddr);
            ForeignMemory.setLong(struct + OFFSET_SIZE_BYTES, sizeBytes);
        }
        return enginePtr;
    }

    /**
     * Unmaps, destroys, and returns the slot to the shared pool.
     * The mapped CPU address becomes invalid after this call.
     */
    public static void destroy(long slot, VkDevice device) {
        if (slot == 0L) return;
        long struct = ForeignMemory.getLong(slot);
        if (struct != 0L) {
            long vkBuf = ForeignMemory.getLong(struct + OFFSET_VK_BUFFER);
            long vkMem = ForeignMemory.getLong(struct + OFFSET_VK_MEMORY);
            if (vkMem != 0L) vkUnmapMemory(device, vkMem);
            if (vkBuf != 0L) vkDestroyBuffer(device, vkBuf, null);
            if (vkMem != 0L) vkFreeMemory(device, vkMem, null);
            ForeignMemory.freeNative(struct);
        }
        Bit64.free(slot);
    }

    // --- Accessors ---

    /** Returns the raw VkBuffer handle (long) for descriptor binding. */
    public static long getVkBuffer(long slot)  { return ForeignMemory.getLong(struct(slot) + OFFSET_VK_BUFFER);  }

    /** Returns the CPU-mapped raw address — write PCM floats here directly. */
    public static long getMappedPtr(long slot) { return ForeignMemory.getLong(struct(slot) + OFFSET_MAPPED_PTR); }

    /** Returns the size in bytes of this buffer. */
    public static long getSizeBytes(long slot) { return ForeignMemory.getLong(struct(slot) + OFFSET_SIZE_BYTES); }

    // --- Memory type helper ---

    private static int findMemoryType(VkPhysicalDevice physicalDevice, int typeBits, int properties, MemoryStack stack) {
        VkPhysicalDeviceMemoryProperties memProps = VkPhysicalDeviceMemoryProperties.calloc(stack);
        vkGetPhysicalDeviceMemoryProperties(physicalDevice, memProps);
        for (int i = 0; i < memProps.memoryTypeCount(); i++) {
            if ((typeBits & (1 << i)) != 0 &&
                (memProps.memoryTypes(i).propertyFlags() & properties) == properties) {
                return i;
            }
        }
        throw new RuntimeException("AudioComputeBuffer: no suitable HOST_VISIBLE|HOST_COHERENT memory type found");
    }
}