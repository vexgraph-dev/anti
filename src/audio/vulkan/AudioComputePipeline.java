package audio.vulkan;

import annotation.Draft;
import annotation.Intention;
import annotation.Volatile;
import nio.ForeignMemory;
import org.lwjgl.system.MemoryStack;
import org.lwjgl.vulkan.VkComputePipelineCreateInfo;
import org.lwjgl.vulkan.VkDescriptorBufferInfo;
import org.lwjgl.vulkan.VkDescriptorPoolCreateInfo;
import org.lwjgl.vulkan.VkDescriptorPoolSize;
import org.lwjgl.vulkan.VkDescriptorSetAllocateInfo;
import org.lwjgl.vulkan.VkDescriptorSetLayoutBinding;
import org.lwjgl.vulkan.VkDescriptorSetLayoutCreateInfo;
import org.lwjgl.vulkan.VkDevice;
import org.lwjgl.vulkan.VkPhysicalDevice;
import org.lwjgl.vulkan.VkPipelineCacheCreateInfo;
import org.lwjgl.vulkan.VkPipelineLayoutCreateInfo;
import org.lwjgl.vulkan.VkPipelineShaderStageCreateInfo;
import org.lwjgl.vulkan.VkPushConstantRange;
import org.lwjgl.vulkan.VkShaderModuleCreateInfo;
import org.lwjgl.vulkan.VkWriteDescriptorSet;

import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.LongBuffer;
import java.nio.file.Files;
import java.nio.file.Paths;

import static org.lwjgl.vulkan.VK10.*;

/**
 * Manages the Vulkan compute pipeline for GPU-side audio mixing.
 *
 * Pipeline layout:
 *   - Descriptor set 0, bindings 0..7  : read-only SSBO (one per input AudioComputeBuffer layer)
 *   - Descriptor set 0, binding 8      : write-only SSBO (master mix output)
 *   - Push constants (40 bytes total)  : numSamples (int), numLayers (int), gains[8] (float[8])
 *
 * Shader: audio_mix.comp → audio_mix.spv (Vulkan 1.0, GLSL 450)
 * Compile with: glslc src/audio/vulkan/audio_mix.comp -o src/audio/vulkan/audio_mix.spv
 *
 * This class is single-instance (not pooled) — one pipeline serves the entire audio system.
 * @Volatile because it mutates Vulkan device state on construction and destruction.
 */
@Draft
@Intention("Vulkan compute pipeline for zero-copy GPU audio mixing with explicit host→shader memory barriers")
@Volatile
@SuppressWarnings("all") // this is so that the whole thing dosnt warn about Autocloseables :roll:
public final class AudioComputePipeline {

    /** Maximum number of simultaneous input layers supported by the GLSL shader. */
    public static final int MAX_LAYERS = 8;

    /** Total bindings: 8 input layers + 1 master output. */
    private static final int TOTAL_BINDINGS = MAX_LAYERS + 1;

    /** Push constant block size in bytes: int numSamples + int numLayers + float[8] gains = 40 bytes */
    private static final int PUSH_CONSTANT_SIZE = 4 + 4 + (8 * 4); // 40 bytes

    // --- Vulkan handles (raw longs — no heap wrappers) ---
    private final VkDevice device;
    private final long shaderModule;
    private final long descriptorSetLayout;
    private final long pipelineLayout;
    private final long pipeline;
    private final long descriptorPool;
    private long descriptorSet; // written once per bind call

    // Off-heap scratch for push constant data — 40 bytes, reused every dispatch
    // Layout: [numSamples(4), numLayers(4), gain0(4), gain1(4)..gain7(4)]
    private final long pushConstantPtr;

    private boolean destroyed = false;

    /**
     * Constructs and fully initialises the audio compute pipeline.
     *
     * @param device         active VkDevice
     * @param physicalDevice VkPhysicalDevice (for memory queries in AudioComputeBuffer)
     * @param spvPath        path to the compiled audio_mix.spv SPIR-V blob
     */
    public AudioComputePipeline(VkDevice device, VkPhysicalDevice physicalDevice, String spvPath) {
        this.device = device;
        // Off-heap push constant scratch block — lives for the lifetime of the pipeline
        this.pushConstantPtr = ForeignMemory.allocateNative(PUSH_CONSTANT_SIZE);
        try (MemoryStack stack = MemoryStack.stackPush()) {
            this.shaderModule        = createShaderModule(device, spvPath, stack);
            this.descriptorSetLayout = createDescriptorSetLayout(device, stack);
            this.pipelineLayout      = createPipelineLayout(device, stack);
            this.descriptorPool      = createDescriptorPool(device, stack);
            this.descriptorSet       = allocateDescriptorSet(device, stack);
            this.pipeline            = createComputePipeline(device, stack);
        }
    }

    // ---------------------------------------------------------------------------
    // PUBLIC API
    // ---------------------------------------------------------------------------

    /**
     * Binds N input AudioComputeBuffer slots and one master output slot to the
     * descriptor set.  Call this once per unique buffer configuration, not every frame.
     *
     * @param inputSlots  array of AudioComputeBuffer slot pointers (length == numLayers)
     * @param outputSlot  AudioComputeBuffer slot pointer for the master mix output
     */
    public void bind(long[] inputSlots, long outputSlot) {
        if (destroyed) throw new IllegalStateException("AudioComputePipeline already destroyed");
        int count = Math.min(inputSlots.length, MAX_LAYERS);
        try (MemoryStack stack = MemoryStack.stackPush()) {
            VkWriteDescriptorSet.Buffer writes = VkWriteDescriptorSet.calloc(count + 1, stack);
            // Input layer bindings
            for (int i = 0; i < count; i++) {
                long vkBuf    = AudioComputeBuffer.getVkBuffer(inputSlots[i]);
                long sizeBytes = AudioComputeBuffer.getSizeBytes(inputSlots[i]);
                VkDescriptorBufferInfo.Buffer bufInfo = VkDescriptorBufferInfo.calloc(1, stack)
                        .buffer(vkBuf).offset(0L).range(sizeBytes);
                writes.get(i)
                        .sType(VK_STRUCTURE_TYPE_WRITE_DESCRIPTOR_SET)
                        .dstSet(descriptorSet)
                        .dstBinding(i)
                        .descriptorCount(1)
                        .descriptorType(VK_DESCRIPTOR_TYPE_STORAGE_BUFFER)
                        .pBufferInfo(bufInfo);
            }
            // Master output binding (binding = 8)
            long outVkBuf    = AudioComputeBuffer.getVkBuffer(outputSlot);
            long outSizeBytes = AudioComputeBuffer.getSizeBytes(outputSlot);
            VkDescriptorBufferInfo.Buffer outBufInfo = VkDescriptorBufferInfo.calloc(1, stack)
                    .buffer(outVkBuf).offset(0L).range(outSizeBytes);
            writes.get(count)
                    .sType(VK_STRUCTURE_TYPE_WRITE_DESCRIPTOR_SET)
                    .dstSet(descriptorSet)
                    .dstBinding(MAX_LAYERS)
                    .descriptorCount(1)
                    .descriptorType(VK_DESCRIPTOR_TYPE_STORAGE_BUFFER)
                    .pBufferInfo(outBufInfo);
            vkUpdateDescriptorSets(device, writes, null);
        }
    }

    /**
     * Returns the raw Vulkan pipeline handle for use in command buffer recording.
     */
    public long getPipeline()          { return pipeline; }

    /** Returns the pipeline layout handle for vkCmdPushConstants + vkCmdBindDescriptorSets. */
    public long getPipelineLayout()    { return pipelineLayout; }

    /** Returns the bound descriptor set handle. */
    public long getDescriptorSet()     { return descriptorSet; }

    /** Returns the off-heap pointer to the push constant scratch block (40 bytes). */
    public long getPushConstantPtr()   { return pushConstantPtr; }

    /**
     * Writes numSamples, numLayers, and gains into the off-heap push constant block.
     * Call before each dispatch to update the per-frame parameters.
     */
    public void writePushConstants(int numSamples, int numLayers, float[] gains) {
        ForeignMemory.setInt(pushConstantPtr,      numSamples);
        ForeignMemory.setInt(pushConstantPtr + 4L, numLayers);
        int n = Math.min(gains.length, MAX_LAYERS);
        for (int i = 0; i < n; i++) {
            ForeignMemory.setFloat(pushConstantPtr + 8L + (i * 4L), gains[i]);
        }
        // Zero-fill unused gain slots
        for (int i = n; i < MAX_LAYERS; i++) {
            ForeignMemory.setFloat(pushConstantPtr + 8L + (i * 4L), 0.0f);
        }
    }

    /**
     * Destroys all Vulkan objects owned by this pipeline and frees the push constant block.
     * Must be called before application shutdown.
     */
    public void destroy() {
        if (destroyed) return;
        destroyed = true;
        ForeignMemory.freeNative(pushConstantPtr);
        vkDestroyPipeline(device, pipeline, null);
        vkDestroyPipelineLayout(device, pipelineLayout, null);
        vkDestroyDescriptorPool(device, descriptorPool, null);
        vkDestroyDescriptorSetLayout(device, descriptorSetLayout, null);
        vkDestroyShaderModule(device, shaderModule, null);
    }

    // ---------------------------------------------------------------------------
    // PRIVATE INIT HELPERS — all use MemoryStack, zero heap allocs
    // ---------------------------------------------------------------------------

    private static long createShaderModule(VkDevice device, String spvPath, MemoryStack stack) {
        // Load SPIR-V blob off-heap via raw file read
        byte[] spvBytes;
        try { spvBytes = Files.readAllBytes(Paths.get(spvPath)); }
        catch (IOException e) { throw new RuntimeException("Failed to load audio compute SPIR-V: " + spvPath, e); }

        // Copy into a stack ByteBuffer that Vulkan can read from
        ByteBuffer spvBuf = stack.malloc(spvBytes.length);
        spvBuf.put(spvBytes).flip();

        VkShaderModuleCreateInfo createInfo = VkShaderModuleCreateInfo.calloc(stack)
                .sType(VK_STRUCTURE_TYPE_SHADER_MODULE_CREATE_INFO)
                .pCode(spvBuf);

        LongBuffer pModule = stack.mallocLong(1);
        if (vkCreateShaderModule(device, createInfo, null, pModule) != VK_SUCCESS) {
            throw new RuntimeException("Failed to create audio compute VkShaderModule");
        }
        return pModule.get(0);
    }

    private static long createDescriptorSetLayout(VkDevice device, MemoryStack stack) {
        // 9 bindings: 8 input SSBOs (readonly) + 1 output SSBO (writeonly)
        VkDescriptorSetLayoutBinding.Buffer bindings = VkDescriptorSetLayoutBinding.calloc(TOTAL_BINDINGS, stack);
        for (int i = 0; i < TOTAL_BINDINGS; i++) {
            bindings.get(i)
                    .binding(i)
                    .descriptorType(VK_DESCRIPTOR_TYPE_STORAGE_BUFFER)
                    .descriptorCount(1)
                    .stageFlags(VK_SHADER_STAGE_COMPUTE_BIT);
        }
        VkDescriptorSetLayoutCreateInfo layoutInfo = VkDescriptorSetLayoutCreateInfo.calloc(stack)
                .sType(VK_STRUCTURE_TYPE_DESCRIPTOR_SET_LAYOUT_CREATE_INFO)
                .pBindings(bindings);

        LongBuffer pLayout = stack.mallocLong(1);
        if (vkCreateDescriptorSetLayout(device, layoutInfo, null, pLayout) != VK_SUCCESS) {
            throw new RuntimeException("Failed to create audio compute descriptor set layout");
        }
        return pLayout.get(0);
    }

    private long createPipelineLayout(VkDevice device, MemoryStack stack) {
        VkPushConstantRange.Buffer pushRange = VkPushConstantRange.calloc(1, stack)
                .stageFlags(VK_SHADER_STAGE_COMPUTE_BIT)
                .offset(0)
                .size(PUSH_CONSTANT_SIZE);

        LongBuffer pSetLayout = stack.longs(descriptorSetLayout);

        VkPipelineLayoutCreateInfo layoutInfo = VkPipelineLayoutCreateInfo.calloc(stack)
                .sType(VK_STRUCTURE_TYPE_PIPELINE_LAYOUT_CREATE_INFO)
                .pSetLayouts(pSetLayout)
                .pPushConstantRanges(pushRange);

        LongBuffer pLayout = stack.mallocLong(1);
        if (vkCreatePipelineLayout(device, layoutInfo, null, pLayout) != VK_SUCCESS) {
            throw new RuntimeException("Failed to create audio compute pipeline layout");
        }
        return pLayout.get(0);
    }

    private static long createDescriptorPool(VkDevice device, MemoryStack stack) {
        VkDescriptorPoolSize.Buffer poolSize = VkDescriptorPoolSize.calloc(1, stack)
                .type(VK_DESCRIPTOR_TYPE_STORAGE_BUFFER)
                .descriptorCount(TOTAL_BINDINGS);

        VkDescriptorPoolCreateInfo poolInfo = VkDescriptorPoolCreateInfo.calloc(stack)
                .sType(VK_STRUCTURE_TYPE_DESCRIPTOR_POOL_CREATE_INFO)
                .pPoolSizes(poolSize)
                .maxSets(1);

        LongBuffer pPool = stack.mallocLong(1);
        if (vkCreateDescriptorPool(device, poolInfo, null, pPool) != VK_SUCCESS) {
            throw new RuntimeException("Failed to create audio compute descriptor pool");
        }
        return pPool.get(0);
    }

    private long allocateDescriptorSet(VkDevice device, MemoryStack stack) {
        LongBuffer pLayout = stack.longs(descriptorSetLayout);
        VkDescriptorSetAllocateInfo allocInfo = VkDescriptorSetAllocateInfo.calloc(stack)
                .sType(VK_STRUCTURE_TYPE_DESCRIPTOR_SET_ALLOCATE_INFO)
                .descriptorPool(descriptorPool)
                .pSetLayouts(pLayout);

        LongBuffer pSet = stack.mallocLong(1);
        if (vkAllocateDescriptorSets(device, allocInfo, pSet) != VK_SUCCESS) {
            throw new RuntimeException("Failed to allocate audio compute descriptor set");
        }
        return pSet.get(0);
    }

    private long createComputePipeline(VkDevice device, MemoryStack stack) {
        // Empty pipeline cache — no persistent caching for now
        VkPipelineCacheCreateInfo cacheInfo = VkPipelineCacheCreateInfo.calloc(stack)
                .sType(VK_STRUCTURE_TYPE_PIPELINE_CACHE_CREATE_INFO);
        LongBuffer pCache = stack.mallocLong(1);
        vkCreatePipelineCache(device, cacheInfo, null, pCache);
        long pipelineCache = pCache.get(0);

        VkPipelineShaderStageCreateInfo stageInfo = VkPipelineShaderStageCreateInfo.calloc(stack)
                .sType(VK_STRUCTURE_TYPE_PIPELINE_SHADER_STAGE_CREATE_INFO)
                .stage(VK_SHADER_STAGE_COMPUTE_BIT)
                .module(shaderModule)
                .pName(stack.UTF8("main"));

        VkComputePipelineCreateInfo.Buffer pipelineInfo = VkComputePipelineCreateInfo.calloc(1, stack)
                .sType(VK_STRUCTURE_TYPE_COMPUTE_PIPELINE_CREATE_INFO)
                .stage(stageInfo)
                .layout(pipelineLayout);

        LongBuffer pPipeline = stack.mallocLong(1);
        if (vkCreateComputePipelines(device, pipelineCache, pipelineInfo, null, pPipeline) != VK_SUCCESS) {
            throw new RuntimeException("Failed to create audio compute VkPipeline");
        }
        vkDestroyPipelineCache(device, pipelineCache, null);
        return pPipeline.get(0);
    }
}
