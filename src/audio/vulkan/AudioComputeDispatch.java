package audio.vulkan;

import annotation.Draft;
import annotation.HotCode;
import annotation.Intention;
import annotation.Volatile;
import audio.AudioBufferLayer;
import nio.ForeignMemory;
import org.lwjgl.system.MemoryStack;
import org.lwjgl.vulkan.VkCommandBuffer;
import org.lwjgl.vulkan.VkCommandBufferAllocateInfo;
import org.lwjgl.vulkan.VkCommandBufferBeginInfo;
import org.lwjgl.vulkan.VkDevice;
import org.lwjgl.vulkan.VkFenceCreateInfo;
import org.lwjgl.vulkan.VkMemoryBarrier;
import org.lwjgl.vulkan.VkPhysicalDevice;
import org.lwjgl.vulkan.VkQueue;
import org.lwjgl.vulkan.VkSubmitInfo;

import java.nio.LongBuffer;
import org.lwjgl.PointerBuffer;

import static org.lwjgl.vulkan.VK10.*;

/**
 * Orchestrates the full GPU audio mix dispatch cycle:
 *
 *   ① CPU writes PCM floats into AudioComputeBuffer.getMappedPtr() (this frame's write slots)
 *   ② AudioBufferLayer.swapActive() for each layer — read ptr now has fresh data
 *   ③ [AudioComputeDispatch.dispatch()] →
 *        a. Record command buffer:
 *             - vkCmdPipelineBarrier (HOST_WRITE → SHADER_READ)  ← the missing zipper lock
 *             - vkCmdBindPipeline (compute)
 *             - vkCmdBindDescriptorSets
 *             - vkCmdPushConstants  (numSamples, numLayers, gains[])
 *             - vkCmdDispatch       (ceil(numSamples / 64) workgroups)
 *             - vkCmdPipelineBarrier (SHADER_WRITE → HOST_READ)  ← fence for output
 *        b. vkQueueSubmit + vkWaitForFences
 *   ④ CPU reads master mix from AudioComputeBuffer.getMappedPtr(masterSlot)
 *   ⑤ Hand pointer to OpenAL alBufferData() → play
 *
 * This class owns and manages:
 *   - One VkCommandBuffer from the provided pool
 *   - One VkFence (unsignaled at start, reset each dispatch)
 *   - One AudioComputePipeline (shader + descriptors)
 *   - N+1 AudioComputeBuffer slots (N input layers + 1 master output)
 */
@Draft
@Intention("Full GPU audio dispatch cycle with correct HOST_WRITE→SHADER_READ barriers enforcing the double-buffer zipper contract")
@Volatile
@HotCode
public final class AudioComputeDispatch {

    // --- Vulkan state ---
    private final VkDevice     device;
    private final VkQueue      computeQueue;
    private final long         commandPool;
    private final VkCommandBuffer commandBuffer;
    private final long         fence;

    // --- Audio pipeline + buffers ---
    private final AudioComputePipeline pipeline;

    // inputSlots[i] = AudioComputeBuffer slot for layer i (HOST_VISIBLE Vulkan buffer)
    // masterSlot    = AudioComputeBuffer slot for the mixed output
    private final long[] inputSlots;
    private final long   masterSlot;

    private final int capacityInSamples; // PCM samples (stereo pair count)
    private final int numSamples;        // = capacityInSamples * 2 (total floats, stereo interleaved)

    private boolean destroyed = false;

    /**
     * Constructs the dispatch controller and allocates all GPU resources.
     *
     * @param device              active VkDevice
     * @param physicalDevice      VkPhysicalDevice
     * @param computeQueue        a queue that supports compute operations
     * @param commandPool         command pool for recording
     * @param spvPath             path to audio_mix.spv
     * @param numLayers           number of input AudioBufferLayer tracks (1..8)
     * @param capacityInSamples   samples per channel per buffer frame (e.g. 512)
     */
    public AudioComputeDispatch(
            VkDevice device,
            VkPhysicalDevice physicalDevice,
            VkQueue computeQueue,
            long commandPool,
            String spvPath,
            int numLayers,
            int capacityInSamples
    ) {
        this.device            = device;
        this.computeQueue      = computeQueue;
        this.commandPool       = commandPool;
        this.capacityInSamples = capacityInSamples;
        this.numSamples        = capacityInSamples * 2; // stereo interleaved floats

        // Bytes per buffer = numSamples * sizeof(float32) = numSamples * 4
        long bufferBytes = (long) numSamples * 4L;
        int bufUsage = VK_BUFFER_USAGE_STORAGE_BUFFER_BIT;

        // Allocate HOST_VISIBLE|HOST_COHERENT Vulkan buffers — one per layer + one master
        inputSlots = new long[numLayers];
        for (int i = 0; i < numLayers; i++) {
            inputSlots[i] = AudioComputeBuffer.create(device, physicalDevice, bufferBytes, bufUsage);
        }
        masterSlot = AudioComputeBuffer.create(device, physicalDevice, bufferBytes, bufUsage);

        // Build compute pipeline and bind all buffers to the descriptor set
        pipeline = new AudioComputePipeline(device, physicalDevice, spvPath);
        pipeline.bind(inputSlots, masterSlot);

        // Allocate command buffer
        commandBuffer = allocateCommandBuffer(device, commandPool);

        // Create unsignaled fence — we'll reset + wait each dispatch
        fence = createFence(device);
    }

    // ---------------------------------------------------------------------------
    // PUBLIC API
    // ---------------------------------------------------------------------------

    /**
     * Returns the CPU-mapped write address for input layer [index].
     *
     * The caller (DrawThread / ScriptingThread / AudioSystem) writes fresh PCM
     * float32 stereo data here each frame before calling dispatch().
     *
     * Layout: [L0, R0, L1, R1, ... L(n-1), R(n-1)]  (interleaved, numSamples floats total)
     */
    public long getInputWritePtr(int index) {
        return AudioComputeBuffer.getMappedPtr(inputSlots[index]);
    }

    /**
     * Returns the CPU-visible address of the master mix output buffer.
     * Valid to read AFTER dispatch() returns.
     */
    public long getMasterReadPtr() {
        return AudioComputeBuffer.getMappedPtr(masterSlot);
    }

    /**
     * Full GPU audio mix dispatch cycle — records, submits, and blocks until done.
     *
     * Call sequence per audio frame:
     *  1. Write PCM data into getInputWritePtr(i) for each layer.
     *  2. Call dispatch(numLayers, gains).
     *  3. Read from getMasterReadPtr() and hand to OpenAL.
     *
     * The HOST_WRITE→SHADER_READ barrier inside ensures the GPU never reads
     * stale cache lines from a partially-written input buffer.
     * The SHADER_WRITE→HOST_READ barrier ensures getMasterReadPtr() is fully
     * coherent before this method returns.
     *
     * @param numLayers active layer count (must be <= inputSlots.length)
     * @param gains     per-layer linear gain array (length == numLayers)
     */
    @Volatile
    public void dispatch(int numLayers, float[] gains) {
        if (destroyed) throw new IllegalStateException("AudioComputeDispatch already destroyed");
        if (numLayers < 1 || numLayers > inputSlots.length) {
            throw new IllegalArgumentException("numLayers must be in [1.." + inputSlots.length + "]");
        }

        try (MemoryStack stack = MemoryStack.stackPush()) {

            // --- ① Write push constants into off-heap scratch block ---
            pipeline.writePushConstants(numSamples, numLayers, gains);

            // --- ② Record command buffer ---
            VkCommandBufferBeginInfo beginInfo = VkCommandBufferBeginInfo.calloc(stack)
                    .sType(VK_STRUCTURE_TYPE_COMMAND_BUFFER_BEGIN_INFO)
                    .flags(VK_COMMAND_BUFFER_USAGE_ONE_TIME_SUBMIT_BIT);

            vkResetCommandBuffer(commandBuffer, 0);
            vkBeginCommandBuffer(commandBuffer, beginInfo);

            // HOST_WRITE → SHADER_READ barrier
            // This is the "zipper lock" — it flushes any pending CPU writes to the input
            // buffers before the compute shader is allowed to read them.
            // On HOST_COHERENT memory this is a no-op at the hardware level, but Vulkan
            // requires it semantically and MoltenVK translates it to Metal useResource.
            VkMemoryBarrier.Buffer hostToShaderBarrier = VkMemoryBarrier.calloc(1, stack)
                    .sType(VK_STRUCTURE_TYPE_MEMORY_BARRIER)
                    .srcAccessMask(VK_ACCESS_HOST_WRITE_BIT)
                    .dstAccessMask(VK_ACCESS_SHADER_READ_BIT);

            vkCmdPipelineBarrier(
                    commandBuffer,
                    VK_PIPELINE_STAGE_HOST_BIT,           // src: CPU just finished writing
                    VK_PIPELINE_STAGE_COMPUTE_SHADER_BIT, // dst: compute shader about to read
                    0, hostToShaderBarrier, null, null
            );

            // Bind compute pipeline
            vkCmdBindPipeline(commandBuffer, VK_PIPELINE_BIND_POINT_COMPUTE, pipeline.getPipeline());

            // Bind descriptor set (all SSBO bindings)
            LongBuffer pSet = stack.longs(pipeline.getDescriptorSet());
            vkCmdBindDescriptorSets(
                    commandBuffer,
                    VK_PIPELINE_BIND_POINT_COMPUTE,
                    pipeline.getPipelineLayout(),
                    0, pSet, null
            );

            // Upload push constants from the off-heap scratch block
            // Using MemoryUtil to wrap the raw pointer as a ByteBuffer (zero copy)
            java.nio.ByteBuffer pushBuf = org.lwjgl.system.MemoryUtil.memByteBuffer(
                    pipeline.getPushConstantPtr(), 40
            );
            vkCmdPushConstants(
                    commandBuffer,
                    pipeline.getPipelineLayout(),
                    VK_SHADER_STAGE_COMPUTE_BIT,
                    0, pushBuf
            );

            // Dispatch: one workgroup per 64 floats, rounded up
            int workgroups = (numSamples + 63) / 64;
            vkCmdDispatch(commandBuffer, workgroups, 1, 1);

            // SHADER_WRITE → HOST_READ barrier
            // Ensures the master mix output is fully visible to the CPU before we read it.
            VkMemoryBarrier.Buffer shaderToHostBarrier = VkMemoryBarrier.calloc(1, stack)
                    .sType(VK_STRUCTURE_TYPE_MEMORY_BARRIER)
                    .srcAccessMask(VK_ACCESS_SHADER_WRITE_BIT)
                    .dstAccessMask(VK_ACCESS_HOST_READ_BIT);

            vkCmdPipelineBarrier(
                    commandBuffer,
                    VK_PIPELINE_STAGE_COMPUTE_SHADER_BIT, // src: compute shader just finished writing
                    VK_PIPELINE_STAGE_HOST_BIT,           // dst: CPU about to read master mix
                    0, shaderToHostBarrier, null, null
            );

            vkEndCommandBuffer(commandBuffer);

            // --- ③ Submit + wait ---
            vkResetFences(device, fence);

            PointerBuffer pCmd = stack.pointers(commandBuffer);
            VkSubmitInfo submitInfo = VkSubmitInfo.calloc(stack)
                    .sType(VK_STRUCTURE_TYPE_SUBMIT_INFO)
                    .pCommandBuffers(pCmd);

            if (vkQueueSubmit(computeQueue, submitInfo, fence) != VK_SUCCESS) {
                throw new RuntimeException("AudioComputeDispatch: vkQueueSubmit failed");
            }

            // CPU blocks here until GPU signals the fence — after this returns,
            // getMasterReadPtr() is safe to read with no further synchronisation.
            vkWaitForFences(device, fence, true, Long.MAX_VALUE);
        }
    }

    /**
     * Destroys all GPU resources owned by this dispatch controller.
     * Must be called before AudioComputeBuffer.freeAll() and device destruction.
     */
    public void destroy() {
        if (destroyed) return;
        destroyed = true;
        vkDestroyFence(device, fence, null);
        vkFreeCommandBuffers(device, commandPool, commandBuffer);
        pipeline.destroy();
        for (long slot : inputSlots) AudioComputeBuffer.destroy(slot, device);
        AudioComputeBuffer.destroy(masterSlot, device);
    }

    // ---------------------------------------------------------------------------
    // PRIVATE INIT HELPERS
    // ---------------------------------------------------------------------------

    private static VkCommandBuffer allocateCommandBuffer(VkDevice device, long commandPool) {
        try (MemoryStack stack = MemoryStack.stackPush()) {
            VkCommandBufferAllocateInfo allocInfo = VkCommandBufferAllocateInfo.calloc(stack)
                    .sType(VK_STRUCTURE_TYPE_COMMAND_BUFFER_ALLOCATE_INFO)
                    .commandPool(commandPool)
                    .level(VK_COMMAND_BUFFER_LEVEL_PRIMARY)
                    .commandBufferCount(1);
            PointerBuffer pBuf = stack.mallocPointer(1);
            if (vkAllocateCommandBuffers(device, allocInfo, pBuf) != VK_SUCCESS) {
                throw new RuntimeException("AudioComputeDispatch: failed to allocate command buffer");
            }
            return new VkCommandBuffer(pBuf.get(0), device);
        }
    }

    private static long createFence(VkDevice device) {
        try (MemoryStack stack = MemoryStack.stackPush()) {
            // Start unsignaled — dispatch will reset it before each submit
            VkFenceCreateInfo createInfo = VkFenceCreateInfo.calloc(stack)
                    .sType(VK_STRUCTURE_TYPE_FENCE_CREATE_INFO);
            LongBuffer pFence = stack.mallocLong(1);
            if (vkCreateFence(device, createInfo, null, pFence) != VK_SUCCESS) {
                throw new RuntimeException("AudioComputeDispatch: failed to create fence");
            }
            return pFence.get(0);
        }
    }
}
