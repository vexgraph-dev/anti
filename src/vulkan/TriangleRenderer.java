package vulkan;

import annotation.Draft;
import annotation.Intention;
import org.lwjgl.system.MemoryStack;
import org.lwjgl.system.MemoryUtil;
import org.lwjgl.vulkan.VkAttachmentDescription;
import org.lwjgl.vulkan.VkAttachmentReference;
import org.lwjgl.vulkan.VkCommandBufferBeginInfo;
import org.lwjgl.vulkan.VkGraphicsPipelineCreateInfo;
import org.lwjgl.vulkan.VkPipelineColorBlendAttachmentState;
import org.lwjgl.vulkan.VkPipelineColorBlendStateCreateInfo;
import org.lwjgl.vulkan.VkPipelineInputAssemblyStateCreateInfo;
import org.lwjgl.vulkan.VkPipelineMultisampleStateCreateInfo;
import org.lwjgl.vulkan.VkPipelineRasterizationStateCreateInfo;
import org.lwjgl.vulkan.VkPipelineShaderStageCreateInfo;
import org.lwjgl.vulkan.VkPipelineVertexInputStateCreateInfo;
import org.lwjgl.vulkan.VkPipelineViewportStateCreateInfo;
import org.lwjgl.vulkan.VkRenderPassCreateInfo;
import org.lwjgl.vulkan.VkShaderModuleCreateInfo;
import org.lwjgl.vulkan.VkSubpassDependency;
import org.lwjgl.vulkan.VkSubpassDescription;
import primitive.Long;

import java.io.IOException;
import java.nio.LongBuffer;
import java.nio.ByteBuffer;
import java.nio.file.Files;
import java.nio.file.Path;

import static org.lwjgl.vulkan.KHRSwapchain.VK_IMAGE_LAYOUT_PRESENT_SRC_KHR;
import static org.lwjgl.vulkan.VK10.*;

/**
 * First forward-rendering slice for the Hello Triangle path.
 * Owns the swapchain-dependent render attachments and frame synchronization.
 */
@Draft
@Intention("Builds the smallest Vulkan color-only render path before shader and draw submission integration.")
public final class TriangleRenderer {

    private static long imageViews;
    private static long framebuffers;
    private static long renderPass;
    private static long commandPool;
    private static long vertexShader;
    private static long fragmentShader;
    private static long pipelineLayout;
    private static long pipeline;
    private static boolean initialized;

    private TriangleRenderer() {}

    public static void init() {
        if (initialized) return;

        var device = Vulkan.getDevice();
        int imageCount = Vulkan.getSwapchainImageCount();
        int format = Vulkan.getSwapchainFormat();

        imageViews = Long.allocateArray(imageCount);
        for (int i = 0; i < imageCount; i++) {
            long image = Long.get(Vulkan.getSwapchainImages(), i);
            Long.set(imageViews, i, VKImageView.create(device, image, format, VK_IMAGE_ASPECT_COLOR_BIT));
        }

        try (MemoryStack stack = MemoryStack.stackPush()) {
            VkAttachmentDescription.Buffer attachments = VkAttachmentDescription.calloc(1, stack)
                    .format(format)
                    .samples(VK_SAMPLE_COUNT_1_BIT)
                    .loadOp(VK_ATTACHMENT_LOAD_OP_CLEAR)
                    .storeOp(VK_ATTACHMENT_STORE_OP_STORE)
                    .stencilLoadOp(VK_ATTACHMENT_LOAD_OP_DONT_CARE)
                    .stencilStoreOp(VK_ATTACHMENT_STORE_OP_DONT_CARE)
                    .initialLayout(VK_IMAGE_LAYOUT_UNDEFINED)
                    .finalLayout(VK_IMAGE_LAYOUT_PRESENT_SRC_KHR);

            VkAttachmentReference.Buffer colorReference = VkAttachmentReference.calloc(1, stack)
                    .attachment(0)
                    .layout(VK_IMAGE_LAYOUT_COLOR_ATTACHMENT_OPTIMAL);

            VkSubpassDescription.Buffer subpass = VkSubpassDescription.calloc(1, stack)
                    .pipelineBindPoint(VK_PIPELINE_BIND_POINT_GRAPHICS)
                    .colorAttachmentCount(1)
                    .pColorAttachments(colorReference);

            VkSubpassDependency.Buffer dependency = VkSubpassDependency.calloc(1, stack)
                    .srcSubpass(VK_SUBPASS_EXTERNAL)
                    .dstSubpass(0)
                    .srcStageMask(VK_PIPELINE_STAGE_COLOR_ATTACHMENT_OUTPUT_BIT)
                    .dstStageMask(VK_PIPELINE_STAGE_COLOR_ATTACHMENT_OUTPUT_BIT)
                    .dstAccessMask(VK_ACCESS_COLOR_ATTACHMENT_WRITE_BIT);

            VkRenderPassCreateInfo createInfo = VkRenderPassCreateInfo.calloc(stack)
                    .sType(VK_STRUCTURE_TYPE_RENDER_PASS_CREATE_INFO)
                    .pAttachments(attachments)
                    .pSubpasses(subpass)
                    .pDependencies(dependency);

            renderPass = RenderPass.create(device, createInfo);

            framebuffers = Long.allocateArray(imageCount);
            for (int i = 0; i < imageCount; i++) {
                LongBuffer attachment = stack.mallocLong(1);
                attachment.put(0, VKImageView.get(Long.get(imageViews, i)));
                Long.set(framebuffers, i, VKFramebuffer.create(
                        device,
                        renderPass,
                        attachment,
                        Vulkan.getSwapchainWidth(),
                        Vulkan.getSwapchainHeight()
                ));
            }
        }

        vertexShader = createShaderModule("hello_triangle.vert.spv");
        fragmentShader = createShaderModule("hello_triangle.frag.spv");
        createGraphicsPipeline();

        commandPool = CommandPool.create(device, Vulkan.getGraphicsQueueFamilyIndex());
        Renderer.init(device, commandPool);
        recordCommandBuffers();
        initialized = true;
        System.out.println("Hello Triangle graphics pipeline ready.");
    }

    private static long createShaderModule(String name) {
        Path sourcePath = Path.of("src", "vulkan", "spv", name);
        if (!Files.exists(sourcePath)) {
            sourcePath = Path.of("out", "production", "anti", "vulkan", "spv", name);
        }
        try {
            byte[] bytes = Files.readAllBytes(sourcePath);
            System.out.println("Loading triangle shader module: " + name);
            ByteBuffer code = MemoryUtil.memAlloc(bytes.length).put(bytes).flip();
            try {
                long module = VKShaderModule.create(Vulkan.getDevice(), code);
                System.out.println("Triangle shader module ready: " + name);
                return module;
            } finally {
                MemoryUtil.memFree(code);
            }
        } catch (IOException e) {
            throw new IllegalStateException("Unable to load triangle shader: " + sourcePath, e);
        }
    }

    private static void createGraphicsPipeline() {
        System.out.println("Creating Hello Triangle graphics pipeline...");
        try (MemoryStack stack = MemoryStack.stackPush()) {
            VkPipelineShaderStageCreateInfo.Buffer stages = VkPipelineShaderStageCreateInfo.calloc(2, stack);
            stages.get(0)
                    .sType(VK_STRUCTURE_TYPE_PIPELINE_SHADER_STAGE_CREATE_INFO)
                    .stage(VK_SHADER_STAGE_VERTEX_BIT)
                    .module(VKShaderModule.get(vertexShader))
                    .pName(stack.UTF8("main"));
            stages.get(1)
                    .sType(VK_STRUCTURE_TYPE_PIPELINE_SHADER_STAGE_CREATE_INFO)
                    .stage(VK_SHADER_STAGE_FRAGMENT_BIT)
                    .module(VKShaderModule.get(fragmentShader))
                    .pName(stack.UTF8("main"));

            VkPipelineVertexInputStateCreateInfo vertexInput = VkPipelineVertexInputStateCreateInfo.calloc(stack)
                    .sType(VK_STRUCTURE_TYPE_PIPELINE_VERTEX_INPUT_STATE_CREATE_INFO);
            VkPipelineInputAssemblyStateCreateInfo inputAssembly = VkPipelineInputAssemblyStateCreateInfo.calloc(stack)
                    .sType(VK_STRUCTURE_TYPE_PIPELINE_INPUT_ASSEMBLY_STATE_CREATE_INFO)
                    .topology(VK_PRIMITIVE_TOPOLOGY_TRIANGLE_LIST)
                    .primitiveRestartEnable(false);

            org.lwjgl.vulkan.VkViewport.Buffer viewport = org.lwjgl.vulkan.VkViewport.calloc(1, stack);
            viewport.get(0).set(0.0f, 0.0f, Vulkan.getSwapchainWidth(), Vulkan.getSwapchainHeight(), 0.0f, 1.0f);
            org.lwjgl.vulkan.VkRect2D.Buffer scissor = org.lwjgl.vulkan.VkRect2D.calloc(1, stack);
            scissor.get(0).extent().set(Vulkan.getSwapchainWidth(), Vulkan.getSwapchainHeight());

            VkPipelineViewportStateCreateInfo viewportState = VkPipelineViewportStateCreateInfo.calloc(stack)
                    .sType(VK_STRUCTURE_TYPE_PIPELINE_VIEWPORT_STATE_CREATE_INFO)
                    .viewportCount(1)
                    .pViewports(viewport)
                    .scissorCount(1)
                    .pScissors(scissor);

            VkPipelineRasterizationStateCreateInfo rasterizer = VkPipelineRasterizationStateCreateInfo.calloc(stack)
                    .sType(VK_STRUCTURE_TYPE_PIPELINE_RASTERIZATION_STATE_CREATE_INFO)
                    .depthClampEnable(false)
                    .rasterizerDiscardEnable(false)
                    .polygonMode(VK_POLYGON_MODE_FILL)
                    .lineWidth(1.0f)
                    .cullMode(VK_CULL_MODE_BACK_BIT)
                    .frontFace(VK_FRONT_FACE_CLOCKWISE)
                    .depthBiasEnable(false);

            VkPipelineMultisampleStateCreateInfo multisampling = VkPipelineMultisampleStateCreateInfo.calloc(stack)
                    .sType(VK_STRUCTURE_TYPE_PIPELINE_MULTISAMPLE_STATE_CREATE_INFO)
                    .rasterizationSamples(VK_SAMPLE_COUNT_1_BIT)
                    .sampleShadingEnable(false);

            VkPipelineColorBlendAttachmentState.Buffer colorAttachment = VkPipelineColorBlendAttachmentState.calloc(1, stack)
                    .colorWriteMask(VK_COLOR_COMPONENT_R_BIT | VK_COLOR_COMPONENT_G_BIT | VK_COLOR_COMPONENT_B_BIT | VK_COLOR_COMPONENT_A_BIT)
                    .blendEnable(false);
            VkPipelineColorBlendStateCreateInfo colorBlending = VkPipelineColorBlendStateCreateInfo.calloc(stack)
                    .sType(VK_STRUCTURE_TYPE_PIPELINE_COLOR_BLEND_STATE_CREATE_INFO)
                    .logicOpEnable(false)
                    .logicOp(VK_LOGIC_OP_COPY)
                    .attachmentCount(1)
                    .pAttachments(colorAttachment)
                    .blendConstants(stack.floats(0.0f, 0.0f, 0.0f, 0.0f));

            pipelineLayout = VKPipelineLayout.create(Vulkan.getDevice(),
                    org.lwjgl.vulkan.VkPipelineLayoutCreateInfo.calloc(stack)
                            .sType(VK_STRUCTURE_TYPE_PIPELINE_LAYOUT_CREATE_INFO));

            VkGraphicsPipelineCreateInfo.Buffer pipelineInfo = VkGraphicsPipelineCreateInfo.calloc(1, stack)
                    .sType(VK_STRUCTURE_TYPE_GRAPHICS_PIPELINE_CREATE_INFO)
                    .pStages(stages)
                    .pVertexInputState(vertexInput)
                    .pInputAssemblyState(inputAssembly)
                    .pViewportState(viewportState)
                    .pRasterizationState(rasterizer)
                    .pMultisampleState(multisampling)
                    .pColorBlendState(colorBlending)
                    .layout(VKPipelineLayout.get(pipelineLayout))
                    .renderPass(RenderPass.get(renderPass))
                    .subpass(0);

            pipeline = VKPipeline.createGraphicsPipeline(Vulkan.getDevice(), VK_NULL_HANDLE, pipelineInfo);
            System.out.println("Hello Triangle graphics pipeline created.");
        }
    }

    private static void recordCommandBuffers() {
        for (int i = 0; i < Vulkan.getSwapchainImageCount(); i++) {
            recordCommandBuffer(Renderer.getCommandBuffer(i), i);
        }
    }

    static void recordCommandBuffer(long commandBuffer, int imageIndex) {
        if (commandBuffer == VK_NULL_HANDLE) {
            throw new IllegalStateException("Triangle command buffer handle is NULL at index " + imageIndex);
        }
        System.out.println("Recording triangle command buffer: 0x" + java.lang.Long.toHexString(commandBuffer));
        try (MemoryStack stack = MemoryStack.stackPush()) {
            org.lwjgl.vulkan.VkCommandBuffer command = new org.lwjgl.vulkan.VkCommandBuffer(commandBuffer, Vulkan.getDevice());
            VkCommandBufferBeginInfo beginInfo = VkCommandBufferBeginInfo.calloc(stack)
                    .sType(VK_STRUCTURE_TYPE_COMMAND_BUFFER_BEGIN_INFO);
            if (vkBeginCommandBuffer(command, beginInfo) != VK_SUCCESS) {
                throw new IllegalStateException("Failed to begin triangle command buffer.");
            }

            org.lwjgl.vulkan.VkRenderPassBeginInfo renderBegin = org.lwjgl.vulkan.VkRenderPassBeginInfo.calloc(stack)
                    .sType(VK_STRUCTURE_TYPE_RENDER_PASS_BEGIN_INFO)
                    .renderPass(RenderPass.get(renderPass))
                    .framebuffer(VKFramebuffer.get(Long.get(framebuffers, imageIndex)))
                    .renderArea(r -> r.offset(o -> o.set(0, 0)).extent(e -> e.set(Vulkan.getSwapchainWidth(), Vulkan.getSwapchainHeight())));
            org.lwjgl.vulkan.VkClearValue.Buffer clearValues = org.lwjgl.vulkan.VkClearValue.calloc(1, stack);
            clearValues.get(0).color().float32(0, 0.03f).float32(1, 0.03f).float32(2, 0.08f).float32(3, 1.0f);
            renderBegin.pClearValues(clearValues);

            vkCmdBeginRenderPass(command, renderBegin, VK_SUBPASS_CONTENTS_INLINE);
            vkCmdBindPipeline(command, VK_PIPELINE_BIND_POINT_GRAPHICS, VKPipeline.get(pipeline));
            vkCmdDraw(command, 3, 1, 0, 0);
            vkCmdEndRenderPass(command);

            if (vkEndCommandBuffer(command) != VK_SUCCESS) {
                throw new IllegalStateException("Failed to end triangle command buffer.");
            }
        }
    }

    public static void destroy() {
        if (!initialized) return;

        var device = Vulkan.getDevice();
        vkDeviceWaitIdle(device);
        Renderer.destroy(device, commandPool);
        CommandPool.destroy(commandPool, device);

        VKPipeline.destroy(pipeline, device);
        VKPipelineLayout.destroy(pipelineLayout, device);
        VKShaderModule.destroy(fragmentShader, device);
        VKShaderModule.destroy(vertexShader, device);

        for (int i = 0; i < Vulkan.getSwapchainImageCount(); i++) {
            VKFramebuffer.destroy(Long.get(framebuffers, i), device);
            VKImageView.destroy(Long.get(imageViews, i), device);
        }

        RenderPass.destroy(renderPass, device);
        Long.free(framebuffers);
        Long.free(imageViews);
        framebuffers = 0L;
        imageViews = 0L;
        renderPass = 0L;
        commandPool = 0L;
        pipeline = 0L;
        pipelineLayout = 0L;
        fragmentShader = 0L;
        vertexShader = 0L;
        initialized = false;
    }
}
