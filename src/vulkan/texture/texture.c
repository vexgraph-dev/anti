#include "texture.h"
#include "../../io/vfs.h"
#include "../vk.h"
#include <stdio.h>
#include <stdlib.h>
#include <string.h>
#include "../../system/image_mac.h"

#include <vulkan/vulkan.h>

#define MAX_BINDLESS_TEXTURES 1024

// Vulkan Core State
static VkInstance s_instance;
static PFN_vkGetInstanceProcAddr s_gpa;
static VkPhysicalDevice s_phys;
static VkDevice s_device;
static VkQueue s_queue;
static uint32_t s_queueFamily;

// Bindless Registry Arrays
static VkImage s_images[MAX_BINDLESS_TEXTURES];
static VkDeviceMemory s_memories[MAX_BINDLESS_TEXTURES];
static VkImageView s_views[MAX_BINDLESS_TEXTURES];
static VkSampler s_samplers[MAX_BINDLESS_TEXTURES];
static int s_textureCount = 0;

static VkDescriptorPool s_descPool;
static VkDescriptorSetLayout s_descLayout;
static VkDescriptorSet s_bindlessSet;
static VkCommandPool s_cmdPool;

#define VK_LOAD(name) \
    static PFN_vk##name name##_fn; \
    name##_fn = (PFN_vk##name)s_gpa(s_instance, "vk" #name);

static uint32_t findMemoryType(uint32_t typeFilter, VkMemoryPropertyFlags properties) {
    VK_LOAD(GetPhysicalDeviceMemoryProperties)
    VkPhysicalDeviceMemoryProperties memProperties;
    GetPhysicalDeviceMemoryProperties_fn(s_phys, &memProperties);

    for (uint32_t i = 0; i < memProperties.memoryTypeCount; i++) {
        if ((typeFilter & (1 << i)) && (memProperties.memoryTypes[i].propertyFlags & properties) == properties) {
            return i;
        }
    }
    return UINT32_MAX;
}

bool Texture_initModule(void *instance, void *gpa, void *phys, void *device, void *queue, uint32_t queueFamily) {
    s_instance = (VkInstance)instance;
    s_gpa = (PFN_vkGetInstanceProcAddr)gpa;
    s_phys = (VkPhysicalDevice)phys;
    s_device = (VkDevice)device;
    s_queue = (VkQueue)queue;
    s_queueFamily = queueFamily;
    
    VK_LOAD(CreateDescriptorSetLayout)
    VK_LOAD(CreateDescriptorPool)
    VK_LOAD(AllocateDescriptorSets)
    VK_LOAD(CreateCommandPool)

    // 1. Create Descriptor Set Layout (Bindless)
    VkDescriptorSetLayoutBinding binding = {0};
    binding.binding = 0;
    binding.descriptorType = VK_DESCRIPTOR_TYPE_COMBINED_IMAGE_SAMPLER;
    binding.descriptorCount = MAX_BINDLESS_TEXTURES;
    binding.stageFlags = VK_SHADER_STAGE_FRAGMENT_BIT;

    VkDescriptorBindingFlags bindlessFlags = VK_DESCRIPTOR_BINDING_PARTIALLY_BOUND_BIT | VK_DESCRIPTOR_BINDING_VARIABLE_DESCRIPTOR_COUNT_BIT;
    VkDescriptorSetLayoutBindingFlagsCreateInfo layoutFlags = { .sType = VK_STRUCTURE_TYPE_DESCRIPTOR_SET_LAYOUT_BINDING_FLAGS_CREATE_INFO };
    layoutFlags.bindingCount = 1;
    layoutFlags.pBindingFlags = &bindlessFlags;

    VkDescriptorSetLayoutCreateInfo layoutInfo = { .sType = VK_STRUCTURE_TYPE_DESCRIPTOR_SET_LAYOUT_CREATE_INFO };
    layoutInfo.pNext = &layoutFlags;
    layoutInfo.bindingCount = 1;
    layoutInfo.pBindings = &binding;
    
    if (CreateDescriptorSetLayout_fn(s_device, &layoutInfo, NULL, &s_descLayout) != VK_SUCCESS) {
        printf("Failed to create bindless descriptor set layout\n");
        return false;
    }

    // 2. Create Descriptor Pool
    VkDescriptorPoolSize poolSize = {0};
    poolSize.type = VK_DESCRIPTOR_TYPE_COMBINED_IMAGE_SAMPLER;
    poolSize.descriptorCount = MAX_BINDLESS_TEXTURES;

    VkDescriptorPoolCreateInfo poolInfo = { .sType = VK_STRUCTURE_TYPE_DESCRIPTOR_POOL_CREATE_INFO };
    poolInfo.poolSizeCount = 1;
    poolInfo.pPoolSizes = &poolSize;
    poolInfo.maxSets = 1;

    if (CreateDescriptorPool_fn(s_device, &poolInfo, NULL, &s_descPool) != VK_SUCCESS) {
        printf("Failed to create bindless descriptor pool\n");
        return false;
    }

    // 3. Allocate the single giant bindless set
    uint32_t maxDescCount = MAX_BINDLESS_TEXTURES;
    VkDescriptorSetVariableDescriptorCountAllocateInfo variableAllocInfo = { .sType = VK_STRUCTURE_TYPE_DESCRIPTOR_SET_VARIABLE_DESCRIPTOR_COUNT_ALLOCATE_INFO };
    variableAllocInfo.descriptorSetCount = 1;
    variableAllocInfo.pDescriptorCounts = &maxDescCount;

    VkDescriptorSetAllocateInfo allocInfo = { .sType = VK_STRUCTURE_TYPE_DESCRIPTOR_SET_ALLOCATE_INFO };
    allocInfo.pNext = &variableAllocInfo;
    allocInfo.descriptorPool = s_descPool;
    allocInfo.descriptorSetCount = 1;
    allocInfo.pSetLayouts = &s_descLayout;

    if (AllocateDescriptorSets_fn(s_device, &allocInfo, &s_bindlessSet) != VK_SUCCESS) {
        printf("Failed to allocate bindless descriptor set\n");
        return false;
    }

    // 4. Create Command Pool for transient transfers
    VkCommandPoolCreateInfo cpInfo = { .sType = VK_STRUCTURE_TYPE_COMMAND_POOL_CREATE_INFO };
    cpInfo.flags = VK_COMMAND_POOL_CREATE_TRANSIENT_BIT;
    cpInfo.queueFamilyIndex = s_queueFamily;
    
    if (CreateCommandPool_fn(s_device, &cpInfo, NULL, &s_cmdPool) != VK_SUCCESS) {
        printf("Failed to create texture transfer command pool\n");
        return false;
    }

    return true;
}

void Texture_shutdown(void) {
    VK_LOAD(DestroySampler)
    VK_LOAD(DestroyImageView)
    VK_LOAD(DestroyImage)
    VK_LOAD(FreeMemory)
    VK_LOAD(DestroyDescriptorPool)
    VK_LOAD(DestroyDescriptorSetLayout)
    VK_LOAD(DestroyCommandPool)

    for (int i = 0; i < s_textureCount; i++) {
        DestroySampler_fn(s_device, s_samplers[i], NULL);
        DestroyImageView_fn(s_device, s_views[i], NULL);
        DestroyImage_fn(s_device, s_images[i], NULL);
        FreeMemory_fn(s_device, s_memories[i], NULL);
    }
    
    DestroyCommandPool_fn(s_device, s_cmdPool, NULL);
    DestroyDescriptorPool_fn(s_device, s_descPool, NULL);
    DestroyDescriptorSetLayout_fn(s_device, s_descLayout, NULL);
    
    s_textureCount = 0;
}

int32_t Texture_load(const char *vfsPath) {
    if (s_textureCount >= MAX_BINDLESS_TEXTURES) {
        printf("Texture limit reached!\n");
        return -1;
    }

    FILE *f = fopen(vfsPath, "rb");
    if (!f) {
        printf("Failed to read texture file: %s\n", vfsPath);
        return -1;
    }
    fseek(f, 0, SEEK_END);
    size_t fileSize = ftell(f);
    fseek(f, 0, SEEK_SET);
    void *fileData = malloc(fileSize);
    if (!fileData) { fclose(f); return -1; }
    fread(fileData, 1, fileSize, f);
    fclose(f);

    // Decode using platform-specific image decoder
    size_t width = 0, height = 0;
    void *rgbaData = ImageMac_decode(fileData, fileSize, &width, &height);
    free(fileData);

    if (!rgbaData) {
        printf("Failed to decode image: %s\n", vfsPath);
        return -1;
    }

    size_t imageSize = width * height * 4;
    printf("Decoded texture: %s (%zux%zu)\n", vfsPath, width, height);

    VK_LOAD(CreateBuffer)
    VK_LOAD(GetBufferMemoryRequirements)
    VK_LOAD(AllocateMemory)
    VK_LOAD(BindBufferMemory)
    VK_LOAD(MapMemory)
    VK_LOAD(UnmapMemory)
    VK_LOAD(DestroyBuffer)
    VK_LOAD(FreeMemory)
    
    // 1. Create Staging Buffer
    VkBufferCreateInfo bufferInfo = { .sType = VK_STRUCTURE_TYPE_BUFFER_CREATE_INFO };
    bufferInfo.size = imageSize;
    bufferInfo.usage = VK_BUFFER_USAGE_TRANSFER_SRC_BIT;
    bufferInfo.sharingMode = VK_SHARING_MODE_EXCLUSIVE;

    VkBuffer stagingBuffer;
    VkDeviceMemory stagingBufferMemory;
    if (CreateBuffer_fn(s_device, &bufferInfo, NULL, &stagingBuffer) != VK_SUCCESS) {
        free(rgbaData); return -1;
    }

    VkMemoryRequirements memReqs;
    GetBufferMemoryRequirements_fn(s_device, stagingBuffer, &memReqs);

    VkMemoryAllocateInfo allocInfo = { .sType = VK_STRUCTURE_TYPE_MEMORY_ALLOCATE_INFO };
    allocInfo.allocationSize = memReqs.size;
    allocInfo.memoryTypeIndex = findMemoryType(memReqs.memoryTypeBits, VK_MEMORY_PROPERTY_HOST_VISIBLE_BIT | VK_MEMORY_PROPERTY_HOST_COHERENT_BIT);

    if (AllocateMemory_fn(s_device, &allocInfo, NULL, &stagingBufferMemory) != VK_SUCCESS) {
        free(rgbaData); return -1;
    }
    BindBufferMemory_fn(s_device, stagingBuffer, stagingBufferMemory, 0);

    void* data;
    MapMemory_fn(s_device, stagingBufferMemory, 0, imageSize, 0, &data);
    memcpy(data, rgbaData, imageSize);
    UnmapMemory_fn(s_device, stagingBufferMemory);
    free(rgbaData);

    // 2. Create VkImage
    VK_LOAD(CreateImage)
    VK_LOAD(GetImageMemoryRequirements)
    VK_LOAD(BindImageMemory)

    VkImageCreateInfo imageInfo = { .sType = VK_STRUCTURE_TYPE_IMAGE_CREATE_INFO };
    imageInfo.imageType = VK_IMAGE_TYPE_2D;
    imageInfo.extent.width = (uint32_t)width;
    imageInfo.extent.height = (uint32_t)height;
    imageInfo.extent.depth = 1;
    imageInfo.mipLevels = 1;
    imageInfo.arrayLayers = 1;
    imageInfo.format = VK_FORMAT_R8G8B8A8_SRGB; // CoreGraphics output is SRGB generally
    imageInfo.tiling = VK_IMAGE_TILING_OPTIMAL;
    imageInfo.initialLayout = VK_IMAGE_LAYOUT_UNDEFINED;
    imageInfo.usage = VK_IMAGE_USAGE_TRANSFER_DST_BIT | VK_IMAGE_USAGE_SAMPLED_BIT;
    imageInfo.sharingMode = VK_SHARING_MODE_EXCLUSIVE;
    imageInfo.samples = VK_SAMPLE_COUNT_1_BIT;

    VkImage textureImage;
    VkDeviceMemory textureImageMemory;
    if (CreateImage_fn(s_device, &imageInfo, NULL, &textureImage) != VK_SUCCESS) {
        return -1;
    }

    GetImageMemoryRequirements_fn(s_device, textureImage, &memReqs);
    allocInfo.allocationSize = memReqs.size;
    allocInfo.memoryTypeIndex = findMemoryType(memReqs.memoryTypeBits, VK_MEMORY_PROPERTY_DEVICE_LOCAL_BIT);

    if (AllocateMemory_fn(s_device, &allocInfo, NULL, &textureImageMemory) != VK_SUCCESS) {
        return -1;
    }
    BindImageMemory_fn(s_device, textureImage, textureImageMemory, 0);

    // 3. Command Buffer for layout transitions and copy
    VK_LOAD(AllocateCommandBuffers)
    VK_LOAD(BeginCommandBuffer)
    VK_LOAD(EndCommandBuffer)
    VK_LOAD(CmdPipelineBarrier)
    VK_LOAD(CmdCopyBufferToImage)
    VK_LOAD(QueueSubmit)
    VK_LOAD(QueueWaitIdle)
    VK_LOAD(FreeCommandBuffers)

    VkCommandBufferAllocateInfo cmdAllocInfo = { .sType = VK_STRUCTURE_TYPE_COMMAND_BUFFER_ALLOCATE_INFO };
    cmdAllocInfo.level = VK_COMMAND_BUFFER_LEVEL_PRIMARY;
    cmdAllocInfo.commandPool = s_cmdPool;
    cmdAllocInfo.commandBufferCount = 1;

    VkCommandBuffer commandBuffer;
    AllocateCommandBuffers_fn(s_device, &cmdAllocInfo, &commandBuffer);

    VkCommandBufferBeginInfo beginInfo = { .sType = VK_STRUCTURE_TYPE_COMMAND_BUFFER_BEGIN_INFO };
    beginInfo.flags = VK_COMMAND_BUFFER_USAGE_ONE_TIME_SUBMIT_BIT;
    BeginCommandBuffer_fn(commandBuffer, &beginInfo);

    VkImageMemoryBarrier barrier = { .sType = VK_STRUCTURE_TYPE_IMAGE_MEMORY_BARRIER };
    barrier.oldLayout = VK_IMAGE_LAYOUT_UNDEFINED;
    barrier.newLayout = VK_IMAGE_LAYOUT_TRANSFER_DST_OPTIMAL;
    barrier.srcQueueFamilyIndex = VK_QUEUE_FAMILY_IGNORED;
    barrier.dstQueueFamilyIndex = VK_QUEUE_FAMILY_IGNORED;
    barrier.image = textureImage;
    barrier.subresourceRange.aspectMask = VK_IMAGE_ASPECT_COLOR_BIT;
    barrier.subresourceRange.baseMipLevel = 0;
    barrier.subresourceRange.levelCount = 1;
    barrier.subresourceRange.baseArrayLayer = 0;
    barrier.subresourceRange.layerCount = 1;
    barrier.srcAccessMask = 0;
    barrier.dstAccessMask = VK_ACCESS_TRANSFER_WRITE_BIT;

    CmdPipelineBarrier_fn(commandBuffer, VK_PIPELINE_STAGE_TOP_OF_PIPE_BIT, VK_PIPELINE_STAGE_TRANSFER_BIT, 0,
                          0, NULL, 0, NULL, 1, &barrier);

    VkBufferImageCopy region = {0};
    region.imageSubresource.aspectMask = VK_IMAGE_ASPECT_COLOR_BIT;
    region.imageSubresource.mipLevel = 0;
    region.imageSubresource.baseArrayLayer = 0;
    region.imageSubresource.layerCount = 1;
    region.imageExtent.width = (uint32_t)width;
    region.imageExtent.height = (uint32_t)height;
    region.imageExtent.depth = 1;

    CmdCopyBufferToImage_fn(commandBuffer, stagingBuffer, textureImage, VK_IMAGE_LAYOUT_TRANSFER_DST_OPTIMAL, 1, &region);

    barrier.oldLayout = VK_IMAGE_LAYOUT_TRANSFER_DST_OPTIMAL;
    barrier.newLayout = VK_IMAGE_LAYOUT_SHADER_READ_ONLY_OPTIMAL;
    barrier.srcAccessMask = VK_ACCESS_TRANSFER_WRITE_BIT;
    barrier.dstAccessMask = VK_ACCESS_SHADER_READ_BIT;

    CmdPipelineBarrier_fn(commandBuffer, VK_PIPELINE_STAGE_TRANSFER_BIT, VK_PIPELINE_STAGE_FRAGMENT_SHADER_BIT, 0,
                          0, NULL, 0, NULL, 1, &barrier);

    EndCommandBuffer_fn(commandBuffer);

    VkSubmitInfo submitInfo = { .sType = VK_STRUCTURE_TYPE_SUBMIT_INFO };
    submitInfo.commandBufferCount = 1;
    submitInfo.pCommandBuffers = &commandBuffer;

    QueueSubmit_fn(s_queue, 1, &submitInfo, VK_NULL_HANDLE);
    QueueWaitIdle_fn(s_queue);
    FreeCommandBuffers_fn(s_device, s_cmdPool, 1, &commandBuffer);

    DestroyBuffer_fn(s_device, stagingBuffer, NULL);
    FreeMemory_fn(s_device, stagingBufferMemory, NULL);

    // 4. Create ImageView & Sampler
    VK_LOAD(CreateImageView)
    VK_LOAD(CreateSampler)

    VkImageViewCreateInfo viewInfo = { .sType = VK_STRUCTURE_TYPE_IMAGE_VIEW_CREATE_INFO };
    viewInfo.image = textureImage;
    viewInfo.viewType = VK_IMAGE_VIEW_TYPE_2D;
    viewInfo.format = VK_FORMAT_R8G8B8A8_SRGB;
    viewInfo.subresourceRange.aspectMask = VK_IMAGE_ASPECT_COLOR_BIT;
    viewInfo.subresourceRange.baseMipLevel = 0;
    viewInfo.subresourceRange.levelCount = 1;
    viewInfo.subresourceRange.baseArrayLayer = 0;
    viewInfo.subresourceRange.layerCount = 1;

    VkImageView textureImageView;
    if (CreateImageView_fn(s_device, &viewInfo, NULL, &textureImageView) != VK_SUCCESS) {
        return -1;
    }

    VkSamplerCreateInfo samplerInfo = { .sType = VK_STRUCTURE_TYPE_SAMPLER_CREATE_INFO };
    samplerInfo.magFilter = VK_FILTER_LINEAR;
    samplerInfo.minFilter = VK_FILTER_LINEAR;
    samplerInfo.addressModeU = VK_SAMPLER_ADDRESS_MODE_REPEAT;
    samplerInfo.addressModeV = VK_SAMPLER_ADDRESS_MODE_REPEAT;
    samplerInfo.addressModeW = VK_SAMPLER_ADDRESS_MODE_REPEAT;
    samplerInfo.anisotropyEnable = VK_FALSE;
    samplerInfo.borderColor = VK_BORDER_COLOR_INT_OPAQUE_BLACK;
    samplerInfo.unnormalizedCoordinates = VK_FALSE;
    samplerInfo.compareEnable = VK_FALSE;
    samplerInfo.mipmapMode = VK_SAMPLER_MIPMAP_MODE_LINEAR;

    VkSampler textureSampler;
    if (CreateSampler_fn(s_device, &samplerInfo, NULL, &textureSampler) != VK_SUCCESS) {
        return -1;
    }

    // 5. Update Bindless Descriptor Set
    VK_LOAD(UpdateDescriptorSets)
    VkDescriptorImageInfo descImageInfo = {0};
    descImageInfo.imageLayout = VK_IMAGE_LAYOUT_SHADER_READ_ONLY_OPTIMAL;
    descImageInfo.imageView = textureImageView;
    descImageInfo.sampler = textureSampler;

    VkWriteDescriptorSet descriptorWrite = { .sType = VK_STRUCTURE_TYPE_WRITE_DESCRIPTOR_SET };
    descriptorWrite.dstSet = s_bindlessSet;
    descriptorWrite.dstBinding = 0;
    descriptorWrite.dstArrayElement = s_textureCount;
    descriptorWrite.descriptorType = VK_DESCRIPTOR_TYPE_COMBINED_IMAGE_SAMPLER;
    descriptorWrite.descriptorCount = 1;
    descriptorWrite.pImageInfo = &descImageInfo;

    UpdateDescriptorSets_fn(s_device, 1, &descriptorWrite, 0, NULL);

    // 6. Record to Registry
    int32_t id = s_textureCount;
    s_images[id] = textureImage;
    s_memories[id] = textureImageMemory;
    s_views[id] = textureImageView;
    s_samplers[id] = textureSampler;
    
    s_textureCount++;
    printf("Successfully bound texture %s to ID %d\n", vfsPath, id);
    return id;
}

void *Texture_getDescriptorSet(void) {
    return s_bindlessSet;
}

void *Texture_getDescriptorSetLayout(void) {
    return s_descLayout;
}
