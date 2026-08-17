package vulkan;

import annotation.Draft;
import annotation.Intention;
import annotation.Required;
import annotation.Unsafe;
import annotation.Volatile;
import bit.Bit64;
import nio.ForeignMemory;
import oop.TypeRegister;
import org.lwjgl.stb.STBImage;
import org.lwjgl.stb.STBImageResize;
import org.lwjgl.system.MemoryStack;
import org.lwjgl.system.MemoryUtil;
import org.lwjgl.vulkan.*;

import java.nio.ByteBuffer;
import java.nio.LongBuffer;

import static org.lwjgl.vulkan.VK10.*;

/**
 * Textured image factory (@Draft). Decodes + resizes an image file with STB,
 * uploads it into a device-local VkImage through a host-visible staging buffer,
 * then creates the matching sampler and a single combined-image-sampler
 * descriptor set. The engine pointer's data slot stores a pointer to an
 * off-heap struct:
 *   [image | memoryEnginePtr | viewEnginePtr | sampler |
 *    descriptorSetLayout | descriptorPool | descriptorSet |
 *    width | height]
 *
 * REVIEW NOTE: this keeps every GPU handle raw inside one struct; it does not
 * yet integrate with the off-screen pipeline (that happens in TriangleRenderer
 * as a second pipeline + a setPicture() entry point).
 */
@Draft
@Intention("Full texture-on-Panel pipeline: STB decode + resize, staging upload, sampler, descriptor set.")
public final class VKTexture {

    @Required
    public static final int CLASS_ID = TypeRegister.ID_VK_TEXTURE;

    public static final int TYPE_SINGLETON = TypeRegister.VK_TEXTURE_SINGLETON;
    public static final int TYPE_ARRAY     = TypeRegister.VK_TEXTURE_ARRAY;
    public static final int TYPE_POINTER   = TypeRegister.VK_TEXTURE_POINTER;

    private static final long STRUCT_SIZE = 64L;
    // image=0, memoryEnginePtr=8, viewEnginePtr=16, sampler=24,
    // descriptorSetLayout=32, descriptorPool=40, descriptorSet=48,
    // width=56, height=60

    private VKTexture() {}

    public static void freeAll() {
        // Bit64.freeAll() manages the shared singleton slot arena.
    }

    private static long allocateSingleton() {
        return Bit64.allocateSingleton(TYPE_SINGLETON);
    }

    public static void free(long pointer) {
        if (pointer == 0L) return;
        int type = ForeignMemory.getInt(pointer - 8L);
        if (type == 0 || !TypeRegister.isSingleton(type)) {
            throw new IllegalStateException("Double free or corrupt off-heap pointer: 0x" + java.lang.Long.toHexString(pointer).toUpperCase());
        }
        long struct = ForeignMemory.getLong(pointer);
        if (struct != 0L) {
            ForeignMemory.freeNative(struct);
        }
        Bit64.free(pointer);
    }

    // --- STRUCT ACCESSORS ---

    private static long struct(long enginePtr) {
        long struct = ForeignMemory.getLong(enginePtr);
        if (struct == 0L) throw new IllegalStateException("VKTexture pointer not initialized: 0x" + java.lang.Long.toHexString(enginePtr).toUpperCase());
        return struct;
    }

    /** Raw VkImage handle. */
    public static long getImage(long enginePtr) {
        return ForeignMemory.getLong(struct(enginePtr));
    }

    /** Engine pointer (VKDeviceMemory) backing the image. */
    public static long getMemory(long enginePtr) {
        return ForeignMemory.getLong(struct(enginePtr) + 8L);
    }

    /** Engine pointer (VKImageView) matching the image. */
    public static long getView(long enginePtr) {
        return ForeignMemory.getLong(struct(enginePtr) + 16L);
    }

    /** Raw VkSampler handle. */
    public static long getSampler(long enginePtr) {
        return ForeignMemory.getLong(struct(enginePtr) + 24L);
    }

    /** Raw VkDescriptorSetLayout handle (set 0 = combined image sampler). */
    public static long getDescriptorSetLayout(long enginePtr) {
        return ForeignMemory.getLong(struct(enginePtr) + 32L);
    }

    /** Raw VkDescriptorPool handle. */
    public static long getDescriptorPool(long enginePtr) {
        return ForeignMemory.getLong(struct(enginePtr) + 40L);
    }

    /** Raw VkDescriptorSet handle. */
    public static long getDescriptorSet(long enginePtr) {
        return ForeignMemory.getLong(struct(enginePtr) + 48L);
    }

    public static int getWidth(long enginePtr) {
        return ForeignMemory.getInt(struct(enginePtr) + 56L);
    }

    public static int getHeight(long enginePtr) {
        return ForeignMemory.getInt(struct(enginePtr) + 60L);
    }

    // --- VULKAN FACTORY ---

    /**
     * Decodes {@code path} with STBImage (forced to RGBA), resizes with
     * STBImageResize so the larger side is a power of two (any size beyond
     * {@code maxDimension} is downscaled), then uploads into a device-local
     * image and builds sampler + descriptor set.
     */
    public static long create(VkDevice device, VkQueue graphicsQueue, int queueFamilyIndex, String path, int maxDimension) {
        long enginePtr = Bit64.allocateSingleton(TYPE_SINGLETON);
        long struct = ForeignMemory.allocateNative(STRUCT_SIZE);
        ForeignMemory.setLong(enginePtr, struct);
        try (MemoryStack stack = MemoryStack.stackPush()) {
            int[] w = new int[1], h = new int[1], channels = new int[1];
            ByteBuffer pixels = STBImage.stbi_load(path, w, h, channels, 4);
            if (pixels == null) {
                throw new IllegalStateException("VKTexture: failed to decode " + path + ": " + STBImage.stbi_failure_reason());
            }
            boolean stbiOwned = true;
            try {
                int srcW = w[0], srcH = h[0];
                int dstW = nextPow2(srcW);
                int dstH = nextPow2(srcH);
                if (maxDimension > 0 && (dstW > maxDimension || dstH > maxDimension)) {
                    float scale = (float) maxDimension / Math.max(srcW, srcH);
                    if (scale < 1.0f) {
                        dstW = Math.max(1, (int) (srcW * scale));
                        dstH = Math.max(1, (int) (srcH * scale));
                    }
                }

                if (dstW != srcW || dstH != srcH) {
                    ByteBuffer resized = MemoryUtil.memAlloc(dstW * dstH * 4);
                    STBImageResize.stbir_resize_uint8_linear(
                            pixels, srcW, srcH, srcW * 4,
                            resized, dstW, dstH, dstW * 4, 4);
                    STBImage.stbi_image_free(pixels);
                    pixels = resized;
                    stbiOwned = false;
                }

                createImage(device, graphicsQueue, queueFamilyIndex, struct, dstW, dstH, pixels);
            } finally {
                if (pixels != null) {
                    if (stbiOwned) {
                        STBImage.stbi_image_free(pixels);
                    } else {
                        MemoryUtil.memFree(pixels);
                    }
                }
            }
        }
        return enginePtr;
    }

    private static int nextPow2(int v) {
        if (v <= 1) return 1;
        return Integer.highestOneBit(v - 1) << 1;
    }

    private static int findHostVisibleMemoryType(VkDevice device, int typeBits, MemoryStack stack) {
        VkPhysicalDeviceMemoryProperties memProps = VkPhysicalDeviceMemoryProperties.calloc(stack);
        vkGetPhysicalDeviceMemoryProperties(Vulkan.getPhysicalDevice(), memProps);
        for (int i = 0; i < memProps.memoryTypeCount(); i++) {
            if ((typeBits & (1 << i)) != 0 &&
                (memProps.memoryTypes(i).propertyFlags() &
                 (VK_MEMORY_PROPERTY_HOST_VISIBLE_BIT | VK_MEMORY_PROPERTY_HOST_COHERENT_BIT)) ==
                 (VK_MEMORY_PROPERTY_HOST_VISIBLE_BIT | VK_MEMORY_PROPERTY_HOST_COHERENT_BIT)) {
                return i;
            }
        }
        throw new RuntimeException("VKTexture: no suitable host-visible memory type found");
    }

    private static int findDeviceLocalMemoryType(VkDevice device, int typeBits, MemoryStack stack) {
        VkPhysicalDeviceMemoryProperties memProps = VkPhysicalDeviceMemoryProperties.calloc(stack);
        vkGetPhysicalDeviceMemoryProperties(Vulkan.getPhysicalDevice(), memProps);
        for (int i = 0; i < memProps.memoryTypeCount(); i++) {
            if ((typeBits & (1 << i)) != 0 &&
                (memProps.memoryTypes(i).propertyFlags() & VK_MEMORY_PROPERTY_DEVICE_LOCAL_BIT) == VK_MEMORY_PROPERTY_DEVICE_LOCAL_BIT) {
                return i;
            }
        }
        throw new RuntimeException("VKTexture: no suitable device-local memory type found");
    }

    private static void createImage(VkDevice device, VkQueue queue, int family, long struct, int width, int height, ByteBuffer rgba) {
        int format = VK_FORMAT_R8G8B8A8_UNORM;

        try (MemoryStack stack = MemoryStack.stackPush()) {
            // 1. create device-local VkImage (transfer dst + sampled)
            VkImageCreateInfo imageInfo = VkImageCreateInfo.calloc(stack)
                    .sType(VK_STRUCTURE_TYPE_IMAGE_CREATE_INFO)
                    .imageType(VK_IMAGE_TYPE_2D)
                    .format(format)
                    .mipLevels(1)
                    .arrayLayers(1)
                    .samples(VK_SAMPLE_COUNT_1_BIT)
                    .tiling(VK_IMAGE_TILING_OPTIMAL)
                    .usage(VK_IMAGE_USAGE_TRANSFER_DST_BIT | VK_IMAGE_USAGE_SAMPLED_BIT)
                    .sharingMode(VK_SHARING_MODE_EXCLUSIVE)
                    .initialLayout(VK_IMAGE_LAYOUT_UNDEFINED);
            imageInfo.extent().set(width, height, 1);

            LongBuffer pImage = stack.mallocLong(1);
            if (vkCreateImage(device, imageInfo, null, pImage) != VK_SUCCESS) {
                throw new RuntimeException("VKTexture: failed to create image");
            }
            long image = pImage.get(0);

            VkMemoryRequirements memReq = VkMemoryRequirements.calloc(stack);
            vkGetImageMemoryRequirements(device, image, memReq);
            long imageMemory = VKDeviceMemory.allocate(device, memReq.size(),
                    findDeviceLocalMemoryType(device, memReq.memoryTypeBits(), stack));
            VKDeviceMemory.bindImage(imageMemory, device, image, 0);

            // 2. staging buffer (host-visible)
            long size = (long) width * height * 4L;
            long stagingBuffer = VKBuffer.create(device, size,
                    VK_BUFFER_USAGE_TRANSFER_SRC_BIT, VK_SHARING_MODE_EXCLUSIVE);
            VkMemoryRequirements bufReq = VkMemoryRequirements.calloc(stack);
            vkGetBufferMemoryRequirements(device, VKBuffer.get(stagingBuffer), bufReq);
            long stagingMemory = VKDeviceMemory.allocate(device, bufReq.size(),
                    findHostVisibleMemoryType(device, bufReq.memoryTypeBits(), stack));
            VKDeviceMemory.bindBuffer(stagingMemory, device, stagingBuffer, 0);

            long mapped = VKDeviceMemory.map(stagingMemory, device, 0, size);
            try {
                MemoryUtil.memCopy(MemoryUtil.memAddress(rgba), mapped, size);
            } finally {
                VKDeviceMemory.unmap(stagingMemory, device);
            }

            // 3. one-shot transfer: transition -> copy -> transition
            long poolPtr = CommandPool.create(device, family);
            long cmdPtr = CommandBuffer.create(device, poolPtr);
            long fencePtr = Fence.create(device, false);
            try {
                long cmdHandle = CommandBuffer.get(cmdPtr);
                org.lwjgl.vulkan.VkCommandBuffer cmd = new org.lwjgl.vulkan.VkCommandBuffer(cmdHandle, device);
                VkCommandBufferBeginInfo beginInfo = VkCommandBufferBeginInfo.calloc(stack)
                        .sType(VK_STRUCTURE_TYPE_COMMAND_BUFFER_BEGIN_INFO)
                        .flags(VK_COMMAND_BUFFER_USAGE_ONE_TIME_SUBMIT_BIT);
                if (vkBeginCommandBuffer(cmd, beginInfo) != VK_SUCCESS) {
                    throw new RuntimeException("VKTexture: failed to begin transfer command buffer");
                }

                VkImageMemoryBarrier.Buffer toTransfer = VkImageMemoryBarrier.malloc(1, stack)
                        .sType(VK_STRUCTURE_TYPE_IMAGE_MEMORY_BARRIER)
                        .srcAccessMask(0)
                        .dstAccessMask(VK_ACCESS_TRANSFER_WRITE_BIT)
                        .oldLayout(VK_IMAGE_LAYOUT_UNDEFINED)
                        .newLayout(VK_IMAGE_LAYOUT_TRANSFER_DST_OPTIMAL)
                        .srcQueueFamilyIndex(VK_QUEUE_FAMILY_IGNORED)
                        .dstQueueFamilyIndex(VK_QUEUE_FAMILY_IGNORED)
                        .image(image)
                        .subresourceRange(VkImageSubresourceRange.malloc(stack)
                                .aspectMask(VK_IMAGE_ASPECT_COLOR_BIT)
                                .baseMipLevel(0)
                                .levelCount(1)
                                .baseArrayLayer(0)
                                .layerCount(1));
                vkCmdPipelineBarrier(cmd,
                        VK_PIPELINE_STAGE_TOP_OF_PIPE_BIT, VK_PIPELINE_STAGE_TRANSFER_BIT,
                        0, null, null, toTransfer);

                VkBufferImageCopy.Buffer region = VkBufferImageCopy.malloc(1, stack)
                        .bufferOffset(0)
                        .bufferRowLength(0)
                        .bufferImageHeight(0)
                        .imageSubresource(VkImageSubresourceLayers.malloc(stack)
                                .aspectMask(VK_IMAGE_ASPECT_COLOR_BIT)
                                .mipLevel(0)
                                .baseArrayLayer(0)
                                .layerCount(1))
                        .imageOffset(VkOffset3D.malloc(stack).set(0, 0, 0))
                        .imageExtent(VkExtent3D.malloc(stack).set(width, height, 1));
                vkCmdCopyBufferToImage(cmd, VKBuffer.get(stagingBuffer), image,
                        VK_IMAGE_LAYOUT_TRANSFER_DST_OPTIMAL, region);

                VkImageMemoryBarrier.Buffer toShader = VkImageMemoryBarrier.malloc(1, stack)
                        .sType(VK_STRUCTURE_TYPE_IMAGE_MEMORY_BARRIER)
                        .srcAccessMask(VK_ACCESS_TRANSFER_WRITE_BIT)
                        .dstAccessMask(VK_ACCESS_SHADER_READ_BIT)
                        .oldLayout(VK_IMAGE_LAYOUT_TRANSFER_DST_OPTIMAL)
                        .newLayout(VK_IMAGE_LAYOUT_SHADER_READ_ONLY_OPTIMAL)
                        .srcQueueFamilyIndex(VK_QUEUE_FAMILY_IGNORED)
                        .dstQueueFamilyIndex(VK_QUEUE_FAMILY_IGNORED)
                        .image(image)
                        .subresourceRange(VkImageSubresourceRange.malloc(stack)
                                .aspectMask(VK_IMAGE_ASPECT_COLOR_BIT)
                                .baseMipLevel(0)
                                .levelCount(1)
                                .baseArrayLayer(0)
                                .layerCount(1));
                vkCmdPipelineBarrier(cmd,
                        VK_PIPELINE_STAGE_TRANSFER_BIT, VK_PIPELINE_STAGE_FRAGMENT_SHADER_BIT,
                        0, null, null, toShader);

                if (vkEndCommandBuffer(cmd) != VK_SUCCESS) {
                    throw new RuntimeException("VKTexture: failed to end transfer command buffer");
                }

                VkSubmitInfo submitInfo = VkSubmitInfo.calloc(stack)
                        .sType(VK_STRUCTURE_TYPE_SUBMIT_INFO)
                        .pCommandBuffers(stack.pointers(cmdHandle));
                if (vkQueueSubmit(queue, submitInfo, Fence.get(fencePtr)) != VK_SUCCESS) {
                    throw new RuntimeException("VKTexture: failed to submit transfer");
                }
                vkWaitForFences(device, Fence.get(fencePtr), true, Long.MAX_VALUE);
            } finally {
                Fence.destroy(fencePtr, device);
                CommandBuffer.destroy(cmdPtr, device, poolPtr);
                CommandPool.destroy(poolPtr, device);
                VKDeviceMemory.destroy(stagingMemory, device);
                VKBuffer.destroy(stagingBuffer, device);
            }

            long viewEnginePtr = VKImageView.create(device, image, format, VK_IMAGE_ASPECT_COLOR_BIT);
            long sampler = createSampler(device);
            long setLayout = createDescriptorSetLayout(device);
            long descriptorPool = createDescriptorPool(device);
            long descriptorSet = allocateDescriptorSet(device, descriptorPool, setLayout,
                    sampler, VKImageView.get(viewEnginePtr), image);

            ForeignMemory.setLong(struct, image);
            ForeignMemory.setLong(struct + 8L, imageMemory);
            ForeignMemory.setLong(struct + 16L, viewEnginePtr);
            ForeignMemory.setLong(struct + 24L, sampler);
            ForeignMemory.setLong(struct + 32L, setLayout);
            ForeignMemory.setLong(struct + 40L, descriptorPool);
            ForeignMemory.setLong(struct + 48L, descriptorSet);
            ForeignMemory.setInt(struct + 56L, width);
            ForeignMemory.setInt(struct + 60L, height);
        }
    }

    private static long createSampler(VkDevice device) {
        try (MemoryStack stack = MemoryStack.stackPush()) {
            VkSamplerCreateInfo info = VkSamplerCreateInfo.calloc(stack)
                    .sType(VK_STRUCTURE_TYPE_SAMPLER_CREATE_INFO)
                    .magFilter(VK_FILTER_LINEAR)
                    .minFilter(VK_FILTER_LINEAR)
                    .mipmapMode(VK_SAMPLER_MIPMAP_MODE_NEAREST)
                    .addressModeU(VK_SAMPLER_ADDRESS_MODE_CLAMP_TO_EDGE)
                    .addressModeV(VK_SAMPLER_ADDRESS_MODE_CLAMP_TO_EDGE)
                    .addressModeW(VK_SAMPLER_ADDRESS_MODE_CLAMP_TO_EDGE)
                    .mipLodBias(0.0f)
                    .anisotropyEnable(false)
                    .maxAnisotropy(1.0f)
                    .compareEnable(false)
                    .minLod(0.0f)
                    .maxLod(1.0f);
            LongBuffer pSampler = stack.mallocLong(1);
            if (vkCreateSampler(device, info, null, pSampler) != VK_SUCCESS) {
                throw new RuntimeException("VKTexture: failed to create sampler");
            }
            return pSampler.get(0);
        }
    }

    private static long createDescriptorSetLayout(VkDevice device) {
        try (MemoryStack stack = MemoryStack.stackPush()) {
            VkDescriptorSetLayoutBinding binding = VkDescriptorSetLayoutBinding.calloc(stack)
                    .binding(0)
                    .descriptorType(VK_DESCRIPTOR_TYPE_COMBINED_IMAGE_SAMPLER)
                    .descriptorCount(1)
                    .stageFlags(VK_SHADER_STAGE_FRAGMENT_BIT);
            VkDescriptorSetLayoutCreateInfo info = VkDescriptorSetLayoutCreateInfo.calloc(stack)
                    .sType(VK_STRUCTURE_TYPE_DESCRIPTOR_SET_LAYOUT_CREATE_INFO)
                    .pBindings(VkDescriptorSetLayoutBinding.calloc(1, stack).put(0, binding));
            LongBuffer pLayout = stack.mallocLong(1);
            if (vkCreateDescriptorSetLayout(device, info, null, pLayout) != VK_SUCCESS) {
                throw new RuntimeException("VKTexture: failed to create descriptor set layout");
            }
            return pLayout.get(0);
        }
    }

    private static long createDescriptorPool(VkDevice device) {
        try (MemoryStack stack = MemoryStack.stackPush()) {
            VkDescriptorPoolSize size = VkDescriptorPoolSize.calloc(stack)
                    .type(VK_DESCRIPTOR_TYPE_COMBINED_IMAGE_SAMPLER)
                    .descriptorCount(1);
            VkDescriptorPoolCreateInfo info = VkDescriptorPoolCreateInfo.calloc(stack)
                    .sType(VK_STRUCTURE_TYPE_DESCRIPTOR_POOL_CREATE_INFO)
                    .maxSets(1)
                    .pPoolSizes(VkDescriptorPoolSize.calloc(1, stack).put(0, size));
            LongBuffer pPool = stack.mallocLong(1);
            if (vkCreateDescriptorPool(device, info, null, pPool) != VK_SUCCESS) {
                throw new RuntimeException("VKTexture: failed to create descriptor pool");
            }
            return pPool.get(0);
        }
    }

    private static long allocateDescriptorSet(VkDevice device, long pool, long layout,
                                              long sampler, long viewEngineRawHdle, long image) {
        try (MemoryStack stack = MemoryStack.stackPush()) {
            LongBuffer layouts = stack.mallocLong(1);
            layouts.put(0, layout);
            VkDescriptorSetAllocateInfo allocInfo = VkDescriptorSetAllocateInfo.calloc(stack)
                    .sType(VK_STRUCTURE_TYPE_DESCRIPTOR_SET_ALLOCATE_INFO)
                    .descriptorPool(pool)
                    .pSetLayouts(layouts);

            LongBuffer pSet = stack.mallocLong(1);
            if (vkAllocateDescriptorSets(device, allocInfo, pSet) != VK_SUCCESS) {
                throw new RuntimeException("VKTexture: failed to allocate descriptor set");
            }
            long set = pSet.get(0);

            VkDescriptorImageInfo.Buffer imageInfo = VkDescriptorImageInfo.calloc(1, stack)
                    .sampler(sampler)
                    .imageView(viewEngineRawHdle)
                    .imageLayout(VK_IMAGE_LAYOUT_SHADER_READ_ONLY_OPTIMAL);
            VkWriteDescriptorSet.Buffer write = VkWriteDescriptorSet.calloc(1, stack)
                    .sType(VK_STRUCTURE_TYPE_WRITE_DESCRIPTOR_SET)
                    .dstSet(set)
                    .dstBinding(0)
                    .dstArrayElement(0)
                    .descriptorCount(1)
                    .descriptorType(VK_DESCRIPTOR_TYPE_COMBINED_IMAGE_SAMPLER)
                    .pImageInfo(imageInfo);
            vkUpdateDescriptorSets(device, write, null);
            return set;
        }
    }

    public static void destroy(long enginePtr, VkDevice device) {
        if (enginePtr == 0L) return;
        long struct = ForeignMemory.getLong(enginePtr);
        if (struct != 0L) {
            VkDevice dev = device != null ? device : Vulkan.getDevice();
            long setLayout = ForeignMemory.getLong(struct + 32L);
            long pool = ForeignMemory.getLong(struct + 40L);
            long sampler = ForeignMemory.getLong(struct + 24L);
            long viewEnginePtr = ForeignMemory.getLong(struct + 16L);
            long imageMemory = ForeignMemory.getLong(struct + 8L);
            long image = ForeignMemory.getLong(struct);

            if (setLayout != 0L) vkDestroyDescriptorSetLayout(dev, setLayout, null);
            if (pool != 0L) vkDestroyDescriptorPool(dev, pool, null);
            if (sampler != 0L) vkDestroySampler(dev, sampler, null);
            VKImageView.destroy(viewEnginePtr, dev);
            VKDeviceMemory.destroy(imageMemory, dev);
            if (image != 0L) vkDestroyImage(dev, image, null);
        }
        ForeignMemory.setLong(enginePtr, 0L);
        free(enginePtr);
    }

    public static int classId() {
        return CLASS_ID;
    }

    @Unsafe
    @Volatile
    public static long getUnsafeVolatile(long pointer) {
        return ForeignMemory.getVolatileLong(pointer);
    }
}