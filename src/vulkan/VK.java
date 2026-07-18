//package vulkan;
//
//import hardware.VulkanContext;
//import org.lwjgl.PointerBuffer;
//import org.lwjgl.system.MemoryStack;
//import org.lwjgl.system.MemoryUtil;
//import org.lwjgl.vulkan.*;
//import renderer.MasterRenderer;
//
//import java.nio.ByteBuffer;
//import java.nio.FloatBuffer;
//import java.nio.IntBuffer;
//import java.nio.LongBuffer;
//
//import static org.lwjgl.system.MemoryStack.stackPush;
//import static org.lwjgl.vulkan.KHRSurface.VK_COLOR_SPACE_SRGB_NONLINEAR_KHR;
//import static org.lwjgl.vulkan.KHRSurface.VK_COMPOSITE_ALPHA_OPAQUE_BIT_KHR;
//import static org.lwjgl.vulkan.KHRSwapchain.VK_IMAGE_LAYOUT_PRESENT_SRC_KHR;
//import static org.lwjgl.vulkan.KHRSwapchain.VK_STRUCTURE_TYPE_PRESENT_INFO_KHR;
//import static org.lwjgl.vulkan.KHRSwapchain.VK_STRUCTURE_TYPE_SWAPCHAIN_CREATE_INFO_KHR;
//import static org.lwjgl.vulkan.VK10.*;
//import static org.lwjgl.vulkan.VK10.VK_ACCESS_COLOR_ATTACHMENT_WRITE_BIT;
//import static org.lwjgl.vulkan.VK10.VK_ACCESS_DEPTH_STENCIL_ATTACHMENT_WRITE_BIT;
//import static org.lwjgl.vulkan.VK10.VK_ACCESS_SHADER_READ_BIT;
//import static org.lwjgl.vulkan.VK10.VK_ACCESS_TRANSFER_WRITE_BIT;
//import static org.lwjgl.vulkan.VK10.VK_ATTACHMENT_LOAD_OP_CLEAR;
//import static org.lwjgl.vulkan.VK10.VK_ATTACHMENT_LOAD_OP_DONT_CARE;
//import static org.lwjgl.vulkan.VK10.VK_ATTACHMENT_STORE_OP_DONT_CARE;
//import static org.lwjgl.vulkan.VK10.VK_ATTACHMENT_STORE_OP_STORE;
//import static org.lwjgl.vulkan.VK10.VK_BORDER_COLOR_INT_OPAQUE_BLACK;
//import static org.lwjgl.vulkan.VK10.VK_COMMAND_BUFFER_LEVEL_PRIMARY;
//import static org.lwjgl.vulkan.VK10.VK_COMMAND_BUFFER_USAGE_ONE_TIME_SUBMIT_BIT;
//import static org.lwjgl.vulkan.VK10.VK_COMPARE_OP_ALWAYS;
//import static org.lwjgl.vulkan.VK10.VK_DEPENDENCY_BY_REGION_BIT;
//import static org.lwjgl.vulkan.VK10.VK_FILTER_LINEAR;
//import static org.lwjgl.vulkan.VK10.VK_FORMAT_D32_SFLOAT;
//import static org.lwjgl.vulkan.VK10.VK_IMAGE_ASPECT_COLOR_BIT;
//import static org.lwjgl.vulkan.VK10.VK_IMAGE_ASPECT_DEPTH_BIT;
//import static org.lwjgl.vulkan.VK10.VK_IMAGE_LAYOUT_DEPTH_STENCIL_ATTACHMENT_OPTIMAL;
//import static org.lwjgl.vulkan.VK10.VK_IMAGE_LAYOUT_SHADER_READ_ONLY_OPTIMAL;
//import static org.lwjgl.vulkan.VK10.VK_IMAGE_LAYOUT_TRANSFER_DST_OPTIMAL;
//import static org.lwjgl.vulkan.VK10.VK_IMAGE_LAYOUT_UNDEFINED;
//import static org.lwjgl.vulkan.VK10.VK_IMAGE_TILING_OPTIMAL;
//import static org.lwjgl.vulkan.VK10.VK_IMAGE_TYPE_2D;
//import static org.lwjgl.vulkan.VK10.VK_IMAGE_USAGE_COLOR_ATTACHMENT_BIT;
//import static org.lwjgl.vulkan.VK10.VK_IMAGE_VIEW_TYPE_2D;
//import static org.lwjgl.vulkan.VK10.VK_NULL_HANDLE;
//import static org.lwjgl.vulkan.VK10.VK_PIPELINE_STAGE_COLOR_ATTACHMENT_OUTPUT_BIT;
//import static org.lwjgl.vulkan.VK10.VK_PIPELINE_STAGE_EARLY_FRAGMENT_TESTS_BIT;
//import static org.lwjgl.vulkan.VK10.VK_PIPELINE_STAGE_FRAGMENT_SHADER_BIT;
//import static org.lwjgl.vulkan.VK10.VK_PIPELINE_STAGE_LATE_FRAGMENT_TESTS_BIT;
//import static org.lwjgl.vulkan.VK10.VK_PIPELINE_STAGE_TOP_OF_PIPE_BIT;
//import static org.lwjgl.vulkan.VK10.VK_PIPELINE_STAGE_TRANSFER_BIT;
//import static org.lwjgl.vulkan.VK10.VK_QUEUE_FAMILY_IGNORED;
//import static org.lwjgl.vulkan.VK10.VK_SAMPLER_ADDRESS_MODE_REPEAT;
//import static org.lwjgl.vulkan.VK10.VK_SAMPLER_MIPMAP_MODE_LINEAR;
//import static org.lwjgl.vulkan.VK10.VK_SAMPLE_COUNT_1_BIT;
//import static org.lwjgl.vulkan.VK10.VK_SHARING_MODE_EXCLUSIVE;
//import static org.lwjgl.vulkan.VK10.VK_STRUCTURE_TYPE_BUFFER_CREATE_INFO;
//import static org.lwjgl.vulkan.VK10.VK_STRUCTURE_TYPE_COMMAND_BUFFER_ALLOCATE_INFO;
//import static org.lwjgl.vulkan.VK10.VK_STRUCTURE_TYPE_COMMAND_BUFFER_BEGIN_INFO;
//import static org.lwjgl.vulkan.VK10.VK_STRUCTURE_TYPE_IMAGE_CREATE_INFO;
//import static org.lwjgl.vulkan.VK10.VK_STRUCTURE_TYPE_IMAGE_MEMORY_BARRIER;
//import static org.lwjgl.vulkan.VK10.VK_STRUCTURE_TYPE_IMAGE_VIEW_CREATE_INFO;
//import static org.lwjgl.vulkan.VK10.VK_STRUCTURE_TYPE_MEMORY_ALLOCATE_INFO;
//import static org.lwjgl.vulkan.VK10.VK_STRUCTURE_TYPE_RENDER_PASS_BEGIN_INFO;
//import static org.lwjgl.vulkan.VK10.VK_STRUCTURE_TYPE_SAMPLER_CREATE_INFO;
//import static org.lwjgl.vulkan.VK10.VK_STRUCTURE_TYPE_SUBMIT_INFO;
//import static org.lwjgl.vulkan.VK10.VK_SUBPASS_EXTERNAL;
//import static org.lwjgl.vulkan.VK10.VK_SUCCESS;
//import static org.lwjgl.vulkan.VK10.vkAllocateCommandBuffers;
//import static org.lwjgl.vulkan.VK10.vkAllocateMemory;
//import static org.lwjgl.vulkan.VK10.vkBeginCommandBuffer;
//import static org.lwjgl.vulkan.VK10.vkBindBufferMemory;
//import static org.lwjgl.vulkan.VK10.vkCmdCopyBufferToImage;
//import static org.lwjgl.vulkan.VK10.vkCmdPipelineBarrier;
//import static org.lwjgl.vulkan.VK10.vkCreateBuffer;
//import static org.lwjgl.vulkan.VK10.vkCreateImage;
//import static org.lwjgl.vulkan.VK10.vkCreateImageView;
//import static org.lwjgl.vulkan.VK10.vkCreateSampler;
//import static org.lwjgl.vulkan.VK10.vkEndCommandBuffer;
//import static org.lwjgl.vulkan.VK10.vkFreeCommandBuffers;
//import static org.lwjgl.vulkan.VK10.vkGetBufferMemoryRequirements;
//import static org.lwjgl.vulkan.VK10.vkGetImageMemoryRequirements;
//import static org.lwjgl.vulkan.VK10.vkGetPhysicalDeviceMemoryProperties;
//import static org.lwjgl.vulkan.VK10.vkQueueSubmit;
//import static org.lwjgl.vulkan.VK10.vkQueueWaitIdle;
//
///**
// * Boilerplate wrapper for Vulkan memory and image operations.
// */
//public class VK
//{
//
//    public static IntBuffer allocInt(int size)
//    {
//        return MemoryUtil.memAllocInt(1);
//    }
//
//    public static FloatBuffer allocFloat(float size)
//    {
//        return MemoryUtil.memAllocFloat(1);
//    }
//
//    public static PointerBuffer allocPointer(int size)
//    {
//        return MemoryUtil.memAllocPointer(1);
//    }
//
//    public static LongBuffer allocLong(int size)
//    {
//        return MemoryUtil.memAllocLong(1);
//    }
//
//    public static ByteBuffer allocByte(int size)
//    {
//        return MemoryUtil.memAlloc(1);
//    }
//
//
//    public static VkCommandBufferBeginInfo createBeginInfo()
//    {
//        return VkCommandBufferBeginInfo.calloc().sType(VK_STRUCTURE_TYPE_COMMAND_BUFFER_BEGIN_INFO);
//    }
//
//    /**
//     * Finds the exact right type of RAM (e.g., CPU-visible vs GPU-local) on your specific hardware.
//     */
//    public static int findMemoryType(int typeFilter, int properties) {
//        VkPhysicalDeviceMemoryProperties memProperties = VkPhysicalDeviceMemoryProperties.calloc();
//        // ---> THE FIX: Use VulkanContext
//        vkGetPhysicalDeviceMemoryProperties(VulkanContext.getPhysicalDevice(), memProperties);
//
//        for (int i = 0; i < memProperties.memoryTypeCount(); i++) {
//            if ((typeFilter & (1 << i)) != 0 && (memProperties.memoryTypes(i).propertyFlags() & properties) == properties) {
//                memProperties.free();
//                return i;
//            }
//        }
//        memProperties.free();
//        throw new RuntimeException("Failed to find suitable memory type!");
//    }
//
//    public static long createBuffer(long size, int usage) {
//        try (MemoryStack stack = stackPush()) {
//            VkBufferCreateInfo bufferInfo = VkBufferCreateInfo.calloc(stack);
//            bufferInfo.sType(VK_STRUCTURE_TYPE_BUFFER_CREATE_INFO);
//            bufferInfo.size(size);
//            bufferInfo.usage(usage);
//            bufferInfo.sharingMode(VK_SHARING_MODE_EXCLUSIVE);
//
//            LongBuffer pBuffer = stack.mallocLong(1);
//            // ---> THE FIX: Use VulkanContext
//            if (vkCreateBuffer(VulkanContext.getDevice(), bufferInfo, null, pBuffer) != VK_SUCCESS) {
//                throw new RuntimeException("Failed to create Vulkan Buffer!");
//            }
//            return pBuffer.get(0);
//        }
//    }
//
//    public static long allocateBufferMemory(long buffer, int properties) {
//        try (MemoryStack stack = stackPush()) {
//            VkMemoryRequirements memRequirements = VkMemoryRequirements.calloc(stack);
//            vkGetBufferMemoryRequirements(VulkanContext.getDevice(), buffer, memRequirements);
//
//            VkMemoryAllocateInfo allocInfo = VkMemoryAllocateInfo.calloc(stack);
//            allocInfo.sType(VK_STRUCTURE_TYPE_MEMORY_ALLOCATE_INFO);
//            allocInfo.allocationSize(memRequirements.size());
//            allocInfo.memoryTypeIndex(findMemoryType(memRequirements.memoryTypeBits(), properties));
//
//            LongBuffer pBufferMemory = stack.mallocLong(1);
//            if (vkAllocateMemory(VulkanContext.getDevice(), allocInfo, null, pBufferMemory) != VK_SUCCESS) {
//                throw new RuntimeException("Failed to allocate Buffer Memory!");
//            }
//            vkBindBufferMemory(VulkanContext.getDevice(), buffer, pBufferMemory.get(0), 0);
//            return pBufferMemory.get(0);
//        }
//    }
//
//    public static long createImage(int width, int height, int format, int tiling, int usage) {
//        try (MemoryStack stack = stackPush()) {
//            VkImageCreateInfo imageInfo = VkImageCreateInfo.calloc(stack);
//            imageInfo.sType(VK_STRUCTURE_TYPE_IMAGE_CREATE_INFO);
//            imageInfo.imageType(VK_IMAGE_TYPE_2D);
//            imageInfo.extent().width(width).height(height).depth(1);
//            imageInfo.mipLevels(1);
//            imageInfo.arrayLayers(1);
//            imageInfo.format(format);
//            imageInfo.tiling(tiling);
//            imageInfo.initialLayout(VK_IMAGE_LAYOUT_UNDEFINED);
//            imageInfo.usage(usage);
//            imageInfo.samples(VK_SAMPLE_COUNT_1_BIT);
//            imageInfo.sharingMode(VK_SHARING_MODE_EXCLUSIVE);
//
//            LongBuffer pImage = stack.mallocLong(1);
//            if (vkCreateImage(VulkanContext.getDevice(), imageInfo, null, pImage) != VK_SUCCESS) {
//                throw new RuntimeException("Failed to create Vulkan Image!");
//            }
//            return pImage.get(0);
//        }
//    }
//
//    public static long allocateImageMemory(long image, int properties) {
//        try (MemoryStack stack = stackPush()) {
//            VkMemoryRequirements memRequirements = VkMemoryRequirements.calloc(stack);
//            vkGetImageMemoryRequirements(VulkanContext.getDevice(), image, memRequirements);
//
//            VkMemoryAllocateInfo allocInfo = VkMemoryAllocateInfo.calloc(stack);
//            allocInfo.sType(VK_STRUCTURE_TYPE_MEMORY_ALLOCATE_INFO);
//            allocInfo.allocationSize(memRequirements.size());
//            allocInfo.memoryTypeIndex(findMemoryType(memRequirements.memoryTypeBits(), properties));
//
//            LongBuffer pImageMemory = stack.mallocLong(1);
//            if (vkAllocateMemory(VulkanContext.getDevice(), allocInfo, null, pImageMemory) != VK_SUCCESS) {
//                throw new RuntimeException("Failed to allocate Image Memory!");
//            }
//            return pImageMemory.get(0);
//        }
//    }
//
//    public static long createImageView(long image, int format) {
//        try (MemoryStack stack = stackPush()) {
//            VkImageViewCreateInfo viewInfo = VkImageViewCreateInfo.calloc(stack);
//            viewInfo.sType(VK_STRUCTURE_TYPE_IMAGE_VIEW_CREATE_INFO);
//            viewInfo.image(image);
//            viewInfo.viewType(VK_IMAGE_VIEW_TYPE_2D);
//            viewInfo.format(format);
//
//            // ---> THE FIX: Dynamically assign the aspect mask!
//            int aspectFlags = (format == VK_FORMAT_D32_SFLOAT) ? VK_IMAGE_ASPECT_DEPTH_BIT : VK_IMAGE_ASPECT_COLOR_BIT;
//
//            viewInfo.subresourceRange().aspectMask(aspectFlags);
//            viewInfo.subresourceRange().baseMipLevel(0);
//            viewInfo.subresourceRange().levelCount(1);
//            viewInfo.subresourceRange().baseArrayLayer(0);
//            viewInfo.subresourceRange().layerCount(1);
//
//            LongBuffer pImageView = stack.mallocLong(1);
//            if (vkCreateImageView(VulkanContext.getDevice(), viewInfo, null, pImageView) != VK_SUCCESS) {
//                throw new RuntimeException("Failed to create texture image view!");
//            }
//            return pImageView.get(0);
//        }
//    }
//
//    public static long createTextureSampler() {
//        try (MemoryStack stack = stackPush()) {
//            VkSamplerCreateInfo samplerInfo = VkSamplerCreateInfo.calloc(stack);
//            samplerInfo.sType(VK_STRUCTURE_TYPE_SAMPLER_CREATE_INFO);
//            samplerInfo.magFilter(VK_FILTER_LINEAR);
//            samplerInfo.minFilter(VK_FILTER_LINEAR);
//            samplerInfo.addressModeU(VK_SAMPLER_ADDRESS_MODE_REPEAT);
//            samplerInfo.addressModeV(VK_SAMPLER_ADDRESS_MODE_REPEAT);
//            samplerInfo.addressModeW(VK_SAMPLER_ADDRESS_MODE_REPEAT);
//            samplerInfo.anisotropyEnable(false);
//            samplerInfo.maxAnisotropy(1.0f);
//            samplerInfo.borderColor(VK_BORDER_COLOR_INT_OPAQUE_BLACK);
//            samplerInfo.unnormalizedCoordinates(false);
//            samplerInfo.compareEnable(false);
//            samplerInfo.compareOp(VK_COMPARE_OP_ALWAYS);
//            samplerInfo.mipmapMode(VK_SAMPLER_MIPMAP_MODE_LINEAR);
//
//            LongBuffer pSampler = stack.mallocLong(1);
//            if (vkCreateSampler(VulkanContext.getDevice(), samplerInfo, null, pSampler) != VK_SUCCESS) {
//                throw new RuntimeException("Failed to create texture sampler!");
//            }
//            return pSampler.get(0);
//        }
//    }
//
//    // --- COMMAND BUFFER HELPERS ---
//    // Note: You must expose a 'CommandPool' variable from your MasterRenderer.java for these to work.
//
//    public static VkCommandBuffer beginSingleTimeCommands() {
//        try (MemoryStack stack = stackPush()) {
//            VkCommandBufferAllocateInfo allocInfo = VkCommandBufferAllocateInfo.calloc(stack);
//            allocInfo.sType(VK_STRUCTURE_TYPE_COMMAND_BUFFER_ALLOCATE_INFO);
//            allocInfo.level(VK_COMMAND_BUFFER_LEVEL_PRIMARY);
//            allocInfo.commandPool(MasterRenderer.getCommandPool());
//            allocInfo.commandBufferCount(1);
//
//            PointerBuffer pCommandBuffer = stack.mallocPointer(1);
//            vkAllocateCommandBuffers(VulkanContext.getDevice(), allocInfo, pCommandBuffer);
//
//            VkCommandBuffer commandBuffer = new VkCommandBuffer(pCommandBuffer.get(0), VulkanContext.getDevice());
//
//            VkCommandBufferBeginInfo beginInfo = VkCommandBufferBeginInfo.calloc(stack);
//            beginInfo.sType(VK_STRUCTURE_TYPE_COMMAND_BUFFER_BEGIN_INFO);
//            beginInfo.flags(VK_COMMAND_BUFFER_USAGE_ONE_TIME_SUBMIT_BIT);
//
//            vkBeginCommandBuffer(commandBuffer, beginInfo);
//            return commandBuffer;
//        }
//    }
//
//    public static void endSingleTimeCommands(VkCommandBuffer commandBuffer) {
//        try (MemoryStack stack = stackPush()) {
//            vkEndCommandBuffer(commandBuffer);
//
//            VkSubmitInfo.Buffer submitInfo = VkSubmitInfo.calloc(1, stack);
//            submitInfo.sType(VK_STRUCTURE_TYPE_SUBMIT_INFO);
//            submitInfo.pCommandBuffers(stack.pointers(commandBuffer));
//
//            vkQueueSubmit(VulkanContext.getGraphicsQueue(), submitInfo, VK_NULL_HANDLE);
//            vkQueueWaitIdle(VulkanContext.getGraphicsQueue());
//
//            vkFreeCommandBuffers(VulkanContext.getDevice(), MasterRenderer.getCommandPool(), commandBuffer);
//        }
//    }
//
//    public static void transitionImageLayout(VkCommandBuffer cmd, long image, int format, int oldLayout, int newLayout) {
//        try (MemoryStack stack = stackPush()) {
//            VkImageMemoryBarrier.Buffer barrier = VkImageMemoryBarrier.calloc(1, stack);
//            barrier.sType(VK_STRUCTURE_TYPE_IMAGE_MEMORY_BARRIER);
//            barrier.oldLayout(oldLayout);
//            barrier.newLayout(newLayout);
//            barrier.srcQueueFamilyIndex(VK_QUEUE_FAMILY_IGNORED);
//            barrier.dstQueueFamilyIndex(VK_QUEUE_FAMILY_IGNORED);
//            barrier.image(image);
//            barrier.subresourceRange().aspectMask(VK_IMAGE_ASPECT_COLOR_BIT);
//            barrier.subresourceRange().baseMipLevel(0);
//            barrier.subresourceRange().levelCount(1);
//            barrier.subresourceRange().baseArrayLayer(0);
//            barrier.subresourceRange().layerCount(1);
//
//            int sourceStage;
//            int destinationStage;
//
//            if (oldLayout == VK_IMAGE_LAYOUT_UNDEFINED && newLayout == VK_IMAGE_LAYOUT_TRANSFER_DST_OPTIMAL) {
//                barrier.srcAccessMask(0);
//                barrier.dstAccessMask(VK_ACCESS_TRANSFER_WRITE_BIT);
//                sourceStage = VK_PIPELINE_STAGE_TOP_OF_PIPE_BIT;
//                destinationStage = VK_PIPELINE_STAGE_TRANSFER_BIT;
//            } else if (oldLayout == VK_IMAGE_LAYOUT_TRANSFER_DST_OPTIMAL && newLayout == VK_IMAGE_LAYOUT_SHADER_READ_ONLY_OPTIMAL) {
//                barrier.srcAccessMask(VK_ACCESS_TRANSFER_WRITE_BIT);
//                barrier.dstAccessMask(VK_ACCESS_SHADER_READ_BIT);
//                sourceStage = VK_PIPELINE_STAGE_TRANSFER_BIT;
//                destinationStage = VK_PIPELINE_STAGE_FRAGMENT_SHADER_BIT;
//            } else {
//                throw new IllegalArgumentException("Unsupported layout transition!");
//            }
//
//            vkCmdPipelineBarrier(cmd, sourceStage, destinationStage, 0, null, null, barrier);
//        }
//    }
//
//    public static void copyBufferToImage(VkCommandBuffer cmd, long buffer, long image, int width, int height) {
//        try (MemoryStack stack = stackPush()) {
//            VkBufferImageCopy.Buffer region = VkBufferImageCopy.calloc(1, stack);
//            region.bufferOffset(0);
//            region.bufferRowLength(0);
//            region.bufferImageHeight(0);
//            region.imageSubresource().aspectMask(VK_IMAGE_ASPECT_COLOR_BIT);
//            region.imageSubresource().mipLevel(0);
//            region.imageSubresource().baseArrayLayer(0);
//            region.imageSubresource().layerCount(1);
//            region.imageOffset().set(0, 0, 0);
//            region.imageExtent().set(width, height, 1);
//
//            vkCmdCopyBufferToImage(cmd, buffer, image, VK_IMAGE_LAYOUT_TRANSFER_DST_OPTIMAL, region);
//        }
//    }
//
//    public static VkClearValue.Buffer createClearValues()
//    {
//        VkClearValue.Buffer value = VkClearValue.calloc(2);
//
//        // Color Clear
//        value.get(0).color()
//                .float32(0, 1)
//                .float32(1, 1)
//                .float32(2, 1)
//                .float32(3, 1);
//
//        // Depth Clear
//        value.get(1).depthStencil()
//                .depth(1.0f)
//                .stencil(0);
//
//        return value;
//    }
//
//    public static VkRect2D createRenderArea()
//    {
//        return VkRect2D.calloc();
//    }
//
//    public static VkRenderPassBeginInfo createRenderPassInfo(long renderPass, VkClearValue.Buffer clearValues)
//    {
//        return VkRenderPassBeginInfo.calloc()
//                                    .sType(VK_STRUCTURE_TYPE_RENDER_PASS_BEGIN_INFO)
//                                    .renderPass(renderPass)
//                                    .pClearValues(clearValues);
//    }
//
//    public static VkSubmitInfo createSubmitInfo(IntBuffer mask)
//    {
//        return  VkSubmitInfo.calloc()
//                .sType(VK_STRUCTURE_TYPE_SUBMIT_INFO)
//                .waitSemaphoreCount(1)
//                .pWaitDstStageMask(mask);
//    }
//
//    public static VkPresentInfoKHR createPresentInfo(LongBuffer pSwapchains, IntBuffer pImageIndex)
//    {
//        return VkPresentInfoKHR.calloc()
//                .sType(VK_STRUCTURE_TYPE_PRESENT_INFO_KHR)
//                .swapchainCount(1)
//                .pSwapchains(pSwapchains)
//                .pImageIndices(pImageIndex);
//    }
//
//    public static VkSwapchainCreateInfoKHR createCreateInfo(MemoryStack stack, long surface, int swapchainImageFormat, VkExtent2D swapchainExtent, int presentMode, VkSurfaceCapabilitiesKHR capabilities, int imageCount)
//    {
//        return  VkSwapchainCreateInfoKHR.calloc(stack).sType(VK_STRUCTURE_TYPE_SWAPCHAIN_CREATE_INFO_KHR)
//         .surface(surface)
//         .minImageCount(imageCount)
//         .imageFormat(swapchainImageFormat)
//         .imageColorSpace(VK_COLOR_SPACE_SRGB_NONLINEAR_KHR)
//         .imageExtent(swapchainExtent)
//         .imageArrayLayers(1)
//         .imageUsage(VK_IMAGE_USAGE_COLOR_ATTACHMENT_BIT)
//         .presentMode(presentMode)
//         .preTransform(capabilities.currentTransform())
//         .compositeAlpha(VK_COMPOSITE_ALPHA_OPAQUE_BIT_KHR)
//         .clipped(true)
//         .oldSwapchain(VK_NULL_HANDLE);
//    }
//
//    public static void modifyDependencies(MemoryStack stack, VkSubpassDependency.Buffer dependencies)
//    {
//        // Dependency 0: Coming IN
//        dependencies.get(0)
//                    .srcSubpass(VK_SUBPASS_EXTERNAL).dstSubpass(0)
//                    .srcStageMask(VK_PIPELINE_STAGE_FRAGMENT_SHADER_BIT)
//                    .dstStageMask(VK_PIPELINE_STAGE_COLOR_ATTACHMENT_OUTPUT_BIT | VK_PIPELINE_STAGE_EARLY_FRAGMENT_TESTS_BIT)
//                    .srcAccessMask(VK_ACCESS_SHADER_READ_BIT)
//                    .dstAccessMask(VK_ACCESS_COLOR_ATTACHMENT_WRITE_BIT | VK_ACCESS_DEPTH_STENCIL_ATTACHMENT_WRITE_BIT)
//                    .dependencyFlags(VK_DEPENDENCY_BY_REGION_BIT);
//
//        // Dependency 1: Going OUT
//        dependencies.get(1)
//                    .srcSubpass(0)
//                    .dstSubpass(VK_SUBPASS_EXTERNAL)
//                    .srcStageMask(VK_PIPELINE_STAGE_COLOR_ATTACHMENT_OUTPUT_BIT | VK_PIPELINE_STAGE_LATE_FRAGMENT_TESTS_BIT)
//                    .dstStageMask(VK_PIPELINE_STAGE_FRAGMENT_SHADER_BIT)
//                    .srcAccessMask(VK_ACCESS_COLOR_ATTACHMENT_WRITE_BIT | VK_ACCESS_DEPTH_STENCIL_ATTACHMENT_WRITE_BIT)
//                    .dstAccessMask(VK_ACCESS_SHADER_READ_BIT)
//                    .dependencyFlags(VK_DEPENDENCY_BY_REGION_BIT);
//
//    }
//
//    public static void modifyBufferAttachments(VkAttachmentDescription.Buffer attachments, int swapchainImageFormat)
//    {
//
//        // 0: Color Attachment
//        attachments.get(0).format(swapchainImageFormat).samples(VK_SAMPLE_COUNT_1_BIT)
//                .loadOp(VK_ATTACHMENT_LOAD_OP_CLEAR).storeOp(VK_ATTACHMENT_STORE_OP_STORE)
//                .stencilLoadOp(VK_ATTACHMENT_LOAD_OP_DONT_CARE).stencilStoreOp(VK_ATTACHMENT_STORE_OP_DONT_CARE)
//                .initialLayout(VK_IMAGE_LAYOUT_UNDEFINED).finalLayout(VK_IMAGE_LAYOUT_PRESENT_SRC_KHR);
//
//        // 1: Depth Attachment
//        attachments.get(1).format(VK_FORMAT_D32_SFLOAT).samples(VK_SAMPLE_COUNT_1_BIT)
//                .loadOp(VK_ATTACHMENT_LOAD_OP_CLEAR).storeOp(VK_ATTACHMENT_STORE_OP_DONT_CARE) // Don't need to save depth after rendering
//                .stencilLoadOp(VK_ATTACHMENT_LOAD_OP_DONT_CARE).stencilStoreOp(VK_ATTACHMENT_STORE_OP_DONT_CARE)
//                .initialLayout(VK_IMAGE_LAYOUT_UNDEFINED).finalLayout(VK_IMAGE_LAYOUT_DEPTH_STENCIL_ATTACHMENT_OPTIMAL);
//    }
//
//    public static VkAttachmentDescription.Buffer createColorAttachmentBuffer(MemoryStack stack, int format)
//    {
//        return VkAttachmentDescription.calloc(1, stack)
//        .format(format)
//        .samples(VK_SAMPLE_COUNT_1_BIT)
//        .loadOp(VK_ATTACHMENT_LOAD_OP_CLEAR)
//        .storeOp(VK_ATTACHMENT_STORE_OP_STORE)
//        .stencilLoadOp(VK_ATTACHMENT_LOAD_OP_DONT_CARE)
//        .stencilStoreOp(VK_ATTACHMENT_STORE_OP_DONT_CARE)
//        .initialLayout(VK_IMAGE_LAYOUT_UNDEFINED)
//        .finalLayout(VK_IMAGE_LAYOUT_PRESENT_SRC_KHR);
//    }
//
//    public static VkImageCreateInfo createImageInfo(MemoryStack stack, int width, int height, int format, int usage) {
//        return VkImageCreateInfo.calloc(stack)
//                .sType(VK_STRUCTURE_TYPE_IMAGE_CREATE_INFO)
//                .imageType(VK_IMAGE_TYPE_2D)
//                .extent(VkExtent3D.calloc(stack).set(width, height, 1))
//                .mipLevels(1).arrayLayers(1)
//                .format(format)
//                .tiling(VK_IMAGE_TILING_OPTIMAL)
//                .initialLayout(VK_IMAGE_LAYOUT_UNDEFINED)
//                .usage(usage)
//                .samples(VK_SAMPLE_COUNT_1_BIT)
//                .sharingMode(VK_SHARING_MODE_EXCLUSIVE);
//    }
//}