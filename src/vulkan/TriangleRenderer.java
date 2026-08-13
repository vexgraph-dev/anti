package vulkan;

import annotation.Draft;
import annotation.Intention;
import lang.Mat4;
import lang.Vec4;
import org.lwjgl.system.MemoryStack;
import org.lwjgl.system.MemoryUtil;
import org.lwjgl.vulkan.VkAttachmentDescription;
import org.lwjgl.vulkan.VkClearColorValue;
import org.lwjgl.vulkan.VkClearValue;
import org.lwjgl.vulkan.VkDevice;
import org.lwjgl.vulkan.VkAttachmentReference;
import org.lwjgl.vulkan.VkCommandBufferBeginInfo;
import org.lwjgl.vulkan.VkExtent2D;
import org.lwjgl.vulkan.VkGraphicsPipelineCreateInfo;
import org.lwjgl.vulkan.VkOffset2D;
import org.lwjgl.vulkan.VkPipelineColorBlendAttachmentState;
import org.lwjgl.vulkan.VkPipelineColorBlendStateCreateInfo;
import org.lwjgl.vulkan.VkPipelineDynamicStateCreateInfo;
import org.lwjgl.vulkan.VkPipelineInputAssemblyStateCreateInfo;
import org.lwjgl.vulkan.VkPipelineMultisampleStateCreateInfo;
import org.lwjgl.vulkan.VkPipelineRasterizationStateCreateInfo;
import org.lwjgl.vulkan.VkPipelineShaderStageCreateInfo;
import org.lwjgl.vulkan.VkPipelineVertexInputStateCreateInfo;
import org.lwjgl.vulkan.VkPipelineViewportStateCreateInfo;
import org.lwjgl.vulkan.VkPushConstantRange;
import org.lwjgl.vulkan.VkRect2D;
import org.lwjgl.vulkan.VkRenderPassCreateInfo;
import org.lwjgl.vulkan.VkSubpassDependency;
import org.lwjgl.vulkan.VkSubpassDescription;
import org.lwjgl.vulkan.VkViewport;
import primitive.Long;

import java.io.IOException;
import java.io.InputStream;
import java.nio.LongBuffer;
import java.nio.ByteBuffer;
import java.nio.FloatBuffer;
import java.nio.file.Files;
import java.nio.file.Path;

import static org.lwjgl.vulkan.VK10.*;

/**
 * First forward-rendering slice for the Hello Triangle path.
 * Owns the swapchain-dependent render attachments and frame synchronization.
 */
@Draft
@Intention("Builds the smallest Vulkan color-only render path before shader and draw submission integration.")
public final class TriangleRenderer {

    private static long offscreenImages;
    private static long framebuffers;
    private static long renderPass;
    private static long commandPool;
    private static long blitCommandPool;
    private static long vertexShader;
    private static long fragmentShader;
    private static long pipelineLayout;
    private static long pipeline;
    private static int offscreenWidth;
    private static int offscreenHeight;
    private static boolean initialized;
    private static final boolean staticBackground = System.getProperty("anti.static") != null;
    private static int offscreenImageCount;

    // --- TEXTURED PICTURE (@Draft, pending review) ---
    // A second graphics pipeline renders the darling.Panel's resolved rect with
    // the picture texture bound (set 0 = combined image sampler). A single
    // VKTexture (sunflower.png) + a single Panel slot is hard-wired for now so
    // texturing can be assessed before generic per-node image payload wiring.
    private static long imageQuadVertexShader;
    private static long imageQuadFragmentShader;
    private static long imageQuadPipelineLayout;
    private static long imageQuadPipeline;
    private static long imageQuadSetLayout;
    private static long pictureNode;

    // Scratch projection: the darling.Canvas ortho matrix rebuilt per draw
    // record and pushed to the image_quad shader (pinned once, never per-frame).
    private static long canvasProj;

    private TriangleRenderer() {}

    public static void init() {
        if (initialized) return;

        var device = Vulkan.getDevice();
        int format = Vulkan.getSwapchainFormat();

        try (
            MemoryStack stack = MemoryStack.stackPush();
            VkAttachmentDescription.Buffer attachments = VkAttachmentDescription.calloc(1, stack)
                    .format(format)
                    .samples(VK_SAMPLE_COUNT_1_BIT)
                    .loadOp(VK_ATTACHMENT_LOAD_OP_CLEAR)
                    .storeOp(VK_ATTACHMENT_STORE_OP_STORE)
                    .stencilLoadOp(VK_ATTACHMENT_LOAD_OP_DONT_CARE)
                    .stencilStoreOp(VK_ATTACHMENT_STORE_OP_DONT_CARE)
                    .initialLayout(VK_IMAGE_LAYOUT_UNDEFINED)
                    .finalLayout(VK_IMAGE_LAYOUT_TRANSFER_SRC_OPTIMAL);

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
                    .pDependencies(dependency)
        ) {
            renderPass = RenderPass.create(device, createInfo);
        }

        vertexShader = createShaderModule("hello_triangle.vert.spv");
        fragmentShader = createShaderModule("hello_triangle.frag.spv");
        createGraphicsPipeline();

        commandPool = CommandPool.create(device, Vulkan.getGraphicsQueueFamilyIndex());
        blitCommandPool = CommandPool.create(device, Vulkan.getGraphicsQueueFamilyIndex());
        createOffscreenAttachments(device);
        canvasProj = Mat4.allocate();
        Renderer.init(device, commandPool, blitCommandPool, offscreenImageCount);
        recordCommandBuffers();
        initialized = true;
        System.out.println("Hello Triangle graphics pipeline ready.");
    }

    /**
     * Binds a darling.Picture node to render as the textured picture. The
     * picture's rect is resolved per frame and its bound image.Image's texture
     * is drawn into it. @Draft: still a single hard-wired slot.
     */
    public static void setPicture(long picturePtr) {
        if (!initialized) return;
        var device = Vulkan.getDevice();
        pictureNode = picturePtr;
        imageQuadVertexShader = createShaderModule("image_quad.vert.spv");
        imageQuadFragmentShader = createShaderModule("image_quad.frag.spv");
        createImageQuadPipeline();
        long imagePtr = darling.Picture.getImage(pictureNode);
        System.out.println("Picture node set: img=" + (imagePtr != 0L
                ? (image.Image.getWidth(imagePtr) + "x" + image.Image.getHeight(imagePtr)) : "none"));
    }

    /** Creates off-screen color images and framebuffers the draw thread renders into. */
    private static void createOffscreenAttachments(VkDevice device) {
        offscreenImageCount = Math.max(Vulkan.getSwapchainImageCount(), Renderer.MAX_FRAMES_IN_FLIGHT);
        int format = Vulkan.getSwapchainFormat();

        // Render into a FIXED max-resolution buffer (the main screen in backing pixels),
        // not the swapchain size. The present pass scales this into whatever the window
        // currently occupies, so window/fullscreen resizes never re-create the render
        // targets (the viewport must NOT be derived from the swapchain extent).
        long screenSize = window.Window.getScreenBackingSize();
        int w = (int) (screenSize >>> 32);
        int h = (int) (screenSize & 0xFFFFFFFFL);
        if (w <= 0 || h <= 0) {
            w = Vulkan.getSwapchainWidth();
            h = Vulkan.getSwapchainHeight();
        }
        offscreenWidth = w;
        offscreenHeight = h;

        offscreenImages = Long.allocateArray(offscreenImageCount);
        framebuffers = Long.allocateArray(offscreenImageCount);
        try (MemoryStack stack = MemoryStack.stackPush()) {
            for (int i = 0; i < offscreenImageCount; i++) {
                long image = VKImage.create(device, w, h, format,
                        VK_IMAGE_USAGE_COLOR_ATTACHMENT_BIT | VK_IMAGE_USAGE_TRANSFER_SRC_BIT);
                Long.set(offscreenImages, i, image);

                LongBuffer attachment = stack.mallocLong(1);
                attachment.put(0, VKImageView.get(VKImage.getView(image)));
                Long.set(framebuffers, i, VKFramebuffer.create(device, renderPass, attachment, w, h));
            }
        }
    }

    /** Frees off-screen images and framebuffers (must happen before the swapchain is destroyed). */
    private static void destroyOffscreenAttachments(VkDevice device) {
        for (int i = 0; i < offscreenImageCount; i++) {
            VKFramebuffer.destroy(Long.get(framebuffers, i), device);
            VKImage.destroy(Long.get(offscreenImages, i), device);
        }
        Long.free(framebuffers);
        Long.free(offscreenImages);
        framebuffers = 0L;
        offscreenImages = 0L;
        offscreenImageCount = 0;
        offscreenWidth = 0;
        offscreenHeight = 0;
    }

    /** Raw off-screen VkImage handle used as the blit source for the present pass. */
    public static long getOffscreenImageHandle(int index) {
        if (offscreenImages == 0L) return 0L;
        return VKImage.getImage(Long.get(offscreenImages, index));
    }

    public static int getOffscreenImageCount() {
        return offscreenImageCount;
    }

    public static int getOffscreenWidth() {
        return offscreenWidth;
    }

    public static int getOffscreenHeight() {
        return offscreenHeight;
    }

    /**
     * Recreates the swapchain with a different present mode and rebuilds every
     * swapchain-dependent resource. Must be invoked under the engine's native mutex
     * so it cannot race the render loop.
     */
    public static void setPresentMode(int mode) {
        if (!initialized) return;
        var device = Vulkan.getDevice();
        Renderer.pauseProducer();
        Renderer.pausePresent();
        try {
            vkDeviceWaitIdle(device);
            destroyOffscreenAttachments(device);
            Vulkan.setPresentMode(mode);
            createOffscreenAttachments(device);
            recordCommandBuffers();
            Renderer.resetInFlight();
            System.out.println("TriangleRenderer rebuilt for present mode: " + mode);
        } finally {
            Renderer.resumePresent();
            Renderer.resumeProducer();
        }
    }

    /** Recreates the swapchain at a new size, keeping the current present mode. The off-screen
     * render targets stay pinned at max screen resolution, so this is cheap: only the swapchain
     * (and thus the per-present blit region) changes. Must be called with the device idle. */
    public static void resize(int width, int height) {
        if (!initialized) return;
        var device = Vulkan.getDevice();
        Renderer.pauseProducer();
        Renderer.pausePresent();
        try {
            // vkDeviceWaitIdle(device); // REMOVED FOR SEAMLESS LIVE RESIZE
            Vulkan.resizeSwapchain(width, height);
            Renderer.resetInFlight();
            System.out.println("TriangleRenderer resized to " + width + "x" + height
                    + " (offscreen " + offscreenWidth + "x" + offscreenHeight + " pinned)");
        } finally {
            Renderer.resumePresent();
            Renderer.resumeProducer();
        }
    }

    private static long createShaderModule(String name) {
        String resourcePath = "/vulkan/spv/" + name;
        try (InputStream in = TriangleRenderer.class.getResourceAsStream(resourcePath)) {
            if (in != null) {
                byte[] bytes = in.readAllBytes();
                System.out.println("Loading triangle shader module from classpath: " + resourcePath);
                return buildShaderModule(name, bytes);
            }
        } catch (IOException ignored) {
        }
        Path sourcePath = Path.of("src", "vulkan", "spv", name);
        if (!Files.exists(sourcePath)) {
            sourcePath = Path.of("out", "production", "anti", "vulkan", "spv", name);
        }
        try {
            byte[] bytes = Files.readAllBytes(sourcePath);
            System.out.println("Loading triangle shader module: " + name);
            return buildShaderModule(name, bytes);
        } catch (IOException e) {
            throw new IllegalStateException("Unable to load triangle shader: " + sourcePath, e);
        }
    }

    private static long buildShaderModule(String name, byte[] bytes) {
        try {
            ByteBuffer code = MemoryUtil.memAlloc(bytes.length).put(bytes).flip();
            try {
                long module = VKShaderModule.create(Vulkan.getDevice(), code);
                System.out.println("Triangle shader module ready: " + name);
                return module;
            } finally {
                MemoryUtil.memFree(code);
            }
        } catch (Exception e) {
            throw new IllegalStateException("Unable to build triangle shader module: " + name, e);
        }
    }

    @Intention("this is how we handle things with autocloseables, methinks.")
    private static void createGraphicsPipeline() {        System.out.println("Creating Hello Triangle graphics pipeline...");
        try(
            MemoryStack stack = MemoryStack.stackPush();

            // stage
            VkPipelineShaderStageCreateInfo.Buffer stages = VkPipelineShaderStageCreateInfo.calloc(2, stack);

            // infos
            VkPipelineShaderStageCreateInfo info0 = stages.get(0).sType(VK_STRUCTURE_TYPE_PIPELINE_SHADER_STAGE_CREATE_INFO);
            VkPipelineShaderStageCreateInfo info1 = stages.get(1).sType(VK_STRUCTURE_TYPE_PIPELINE_SHADER_STAGE_CREATE_INFO);

            // push ranges
            VkPushConstantRange.Buffer pushRanges = VkPushConstantRange.malloc(1, stack);
            VkPushConstantRange pushRange0 = pushRanges.get(0).stageFlags(VK_SHADER_STAGE_VERTEX_BIT).offset(0).size(4)
        ) {

            info0.stage(VK_SHADER_STAGE_VERTEX_BIT)
                .module(VKShaderModule.get(vertexShader))
                .pName(stack.UTF8("main"));
            info1.stage(VK_SHADER_STAGE_FRAGMENT_BIT)
                .module(VKShaderModule.get(fragmentShader))
                .pName(stack.UTF8("main"));

            VkPipelineVertexInputStateCreateInfo vertexInput = VkPipelineVertexInputStateCreateInfo.calloc(stack)
                    .sType(VK_STRUCTURE_TYPE_PIPELINE_VERTEX_INPUT_STATE_CREATE_INFO);
            VkPipelineInputAssemblyStateCreateInfo inputAssembly = VkPipelineInputAssemblyStateCreateInfo.calloc(stack)
                    .sType(VK_STRUCTURE_TYPE_PIPELINE_INPUT_ASSEMBLY_STATE_CREATE_INFO)
                    .topology(VK_PRIMITIVE_TOPOLOGY_TRIANGLE_LIST)
                    .primitiveRestartEnable(false);

            // Viewport and scissor are made DYNAMIC so they survive swapchain resizes
            // (fullscreen toggling). Baking them here would keep the triangle clipped to
            // the original 800 x 600 dimensions after the window grows.
            VkPipelineViewportStateCreateInfo viewportState = VkPipelineViewportStateCreateInfo.calloc(stack)
                    .sType(VK_STRUCTURE_TYPE_PIPELINE_VIEWPORT_STATE_CREATE_INFO)
                    .viewportCount(1)
                    .scissorCount(1);
            VkPipelineDynamicStateCreateInfo dynamicState = VkPipelineDynamicStateCreateInfo.calloc(stack)
                    .sType(VK_STRUCTURE_TYPE_PIPELINE_DYNAMIC_STATE_CREATE_INFO)
                    .pDynamicStates(stack.ints(VK_DYNAMIC_STATE_VIEWPORT, VK_DYNAMIC_STATE_SCISSOR));

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
                            .sType(VK_STRUCTURE_TYPE_PIPELINE_LAYOUT_CREATE_INFO)
                            .pPushConstantRanges(pushRanges));

            VkGraphicsPipelineCreateInfo.Buffer pipelineInfo = VkGraphicsPipelineCreateInfo.calloc(1, stack)
                    .sType(VK_STRUCTURE_TYPE_GRAPHICS_PIPELINE_CREATE_INFO)
                    .pStages(stages)
                    .pVertexInputState(vertexInput)
                    .pInputAssemblyState(inputAssembly)
                    .pViewportState(viewportState)
                    .pRasterizationState(rasterizer)
                    .pMultisampleState(multisampling)
                    .pColorBlendState(colorBlending)
                    .pDynamicState(dynamicState)
                    .layout(VKPipelineLayout.get(pipelineLayout))
                    .renderPass(RenderPass.get(renderPass))
                    .subpass(0);

            pipeline = VKPipeline.createGraphicsPipeline(Vulkan.getDevice(), VK_NULL_HANDLE, pipelineInfo);
            System.out.println("Hello Triangle graphics pipeline created.");
        }
    }

    /**
     * Builds the textured-picture pipeline (@Draft pending review). Bindless-ish:
     * pipeline layout shares ONE descriptor set layout (set 0 = combined image
     * sampler) plus a 96-byte push-constant block { proj, rect, uvMin, uvMax }.
     */
    @Intention("Second pipeline draws the image quad at the Panel's resolved rect.")
    private static void createImageQuadPipeline() {
        System.out.println("Creating Image Quad (texture) pipeline...");
        try (
            MemoryStack stack = MemoryStack.stackPush();

            VkPipelineShaderStageCreateInfo.Buffer stages = VkPipelineShaderStageCreateInfo.calloc(2, stack);
            VkPipelineShaderStageCreateInfo info0 = stages.get(0).sType(VK_STRUCTURE_TYPE_PIPELINE_SHADER_STAGE_CREATE_INFO);
            VkPipelineShaderStageCreateInfo info1 = stages.get(1).sType(VK_STRUCTURE_TYPE_PIPELINE_SHADER_STAGE_CREATE_INFO);

            VkPushConstantRange.Buffer pushRanges = VkPushConstantRange.malloc(1, stack);
            VkPushConstantRange pushRange0 = pushRanges.get(0).stageFlags(VK_SHADER_STAGE_VERTEX_BIT).offset(0).size(96)
                    // proj(64) + rect(16) + uvMin(8) + uvMax(8) = 96B
        ) {
            info0.stage(VK_SHADER_STAGE_VERTEX_BIT)
                .module(VKShaderModule.get(imageQuadVertexShader))
                .pName(stack.UTF8("main"));
            info1.stage(VK_SHADER_STAGE_FRAGMENT_BIT)
                .module(VKShaderModule.get(imageQuadFragmentShader))
                .pName(stack.UTF8("main"));

            imageQuadSetLayout = image.Image.getDescriptorSetLayout(darling.Picture.getImage(pictureNode));

            VkPipelineVertexInputStateCreateInfo vertexInput = VkPipelineVertexInputStateCreateInfo.calloc(stack)
                    .sType(VK_STRUCTURE_TYPE_PIPELINE_VERTEX_INPUT_STATE_CREATE_INFO);
            VkPipelineInputAssemblyStateCreateInfo inputAssembly = VkPipelineInputAssemblyStateCreateInfo.calloc(stack)
                    .sType(VK_STRUCTURE_TYPE_PIPELINE_INPUT_ASSEMBLY_STATE_CREATE_INFO)
                    .topology(VK_PRIMITIVE_TOPOLOGY_TRIANGLE_LIST)
                    .primitiveRestartEnable(false);

            VkPipelineViewportStateCreateInfo viewportState = VkPipelineViewportStateCreateInfo.calloc(stack)
                    .sType(VK_STRUCTURE_TYPE_PIPELINE_VIEWPORT_STATE_CREATE_INFO)
                    .viewportCount(1)
                    .scissorCount(1);
            VkPipelineDynamicStateCreateInfo dynamicState = VkPipelineDynamicStateCreateInfo.calloc(stack)
                    .sType(VK_STRUCTURE_TYPE_PIPELINE_DYNAMIC_STATE_CREATE_INFO)
                    .pDynamicStates(stack.ints(VK_DYNAMIC_STATE_VIEWPORT, VK_DYNAMIC_STATE_SCISSOR));

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

            imageQuadPipelineLayout = VKPipelineLayout.create(Vulkan.getDevice(),
                    org.lwjgl.vulkan.VkPipelineLayoutCreateInfo.calloc(stack)
                            .sType(VK_STRUCTURE_TYPE_PIPELINE_LAYOUT_CREATE_INFO)
                            .setLayoutCount(1)
                            .pSetLayouts(stack.longs(imageQuadSetLayout))
                            .pPushConstantRanges(pushRanges));

            VkGraphicsPipelineCreateInfo.Buffer pipelineInfo = VkGraphicsPipelineCreateInfo.calloc(1, stack)
                    .sType(VK_STRUCTURE_TYPE_GRAPHICS_PIPELINE_CREATE_INFO)
                    .pStages(stages)
                    .pVertexInputState(vertexInput)
                    .pInputAssemblyState(inputAssembly)
                    .pViewportState(viewportState)
                    .pRasterizationState(rasterizer)
                    .pMultisampleState(multisampling)
                    .pColorBlendState(colorBlending)
                    .pDynamicState(dynamicState)
                    .layout(VKPipelineLayout.get(imageQuadPipelineLayout))
                    .renderPass(RenderPass.get(renderPass))
                    .subpass(0);

            imageQuadPipeline = VKPipeline.createGraphicsPipeline(Vulkan.getDevice(), VK_NULL_HANDLE, pipelineInfo);
            System.out.println("Image Quad (texture) pipeline created.");
        }
    }

    private static void recordCommandBuffers() {
        for (int i = 0; i < offscreenImageCount; i++) {
            recordCommandBuffer(Renderer.getCommandBuffer(i), i, 0.0f);
        }
    }

    /** Re-records the draw command buffer for a slot with the current animation time. */
    public static void recordDraw(int slot, float time) {
        recordCommandBuffer(Renderer.getCommandBuffer(slot), slot, time);
    }

    private static int lastLoggedW = -1, lastLoggedH = -1;
    private static float lastLoggedRectX, lastLoggedRectY, lastLoggedRectRW, lastLoggedRectRH;

    /** Debug: prints once whenever the framebuffer size or resolved picture rect changes. */
    private static void logResizeIfChanged(int fbW, int fbH, long rect) {
        float rx = Vec4.getX(rect), ry = Vec4.getY(rect), rw = Vec4.getZ(rect), rh = Vec4.getW(rect);
        if (fbW != lastLoggedW || fbH != lastLoggedH
                || rx != lastLoggedRectX || ry != lastLoggedRectY
                || rw != lastLoggedRectRW || rh != lastLoggedRectRH) {
            System.out.println("[resize] fb=" + fbW + "x" + fbH
                    + " rect=(" + rx + "," + ry + "," + rw + "," + rh + ")");
            lastLoggedW = fbW; lastLoggedH = fbH;
            lastLoggedRectX = rx; lastLoggedRectY = ry;
            lastLoggedRectRW = rw; lastLoggedRectRH = rh;
        }
    }

    /** Debug: one-line fb size + resolved picture rect for the window-title readout. */
    public static String dbgFbRect() {
        return "fb=" + Vulkan.getSwapchainWidth() + "x" + Vulkan.getSwapchainHeight()
                + " rect=(" + lastLoggedRectX + "," + lastLoggedRectY + ","
                + lastLoggedRectRW + "," + lastLoggedRectRH + ")";
    }

    static void recordCommandBuffer(long commandBuffer, int imageIndex, float time) {
        if (commandBuffer == VK_NULL_HANDLE) {
            throw new IllegalStateException("Triangle command buffer handle is NULL at index " + imageIndex);
        }

        // -Danti.static=1 renders a static background (time pinned to 0) so the "stretch
        // feeling" during a drag can be A/B isolated from the demo shader's self-animating
        // gradient + bouncing triangle.
        float bgTime = staticBackground ? 0f : time;

        int currentW = Vulkan.getSwapchainWidth();
        int currentH = Vulkan.getSwapchainHeight();
        
        try (
                MemoryStack stack = MemoryStack.stackPush();
                VkClearValue.Buffer clearValues = VkClearValue.calloc(1, stack);
                VkClearColorValue clearColorValue0 = clearValues.get(0).color()
                        // background color for the window (in RGBA)
                        .float32(0, 0f)
                        .float32(1, 0f)
                        .float32(2, 0f)
                        .float32(3, 1.0f);

                // Only shade the portion of the pinned framebuffer that actually matches
                // the current swapchain size! This prevents rendering 6M pixels for an 800x600 window.
                VkViewport.Buffer vpBuffer = VkViewport.calloc(1, stack);
                VkViewport _viewport = vpBuffer.get(0).set(0.0f, 0.0f, currentW, currentH, 0.0f, 1.0f);

                VkRect2D.Buffer scissor = VkRect2D.calloc(1, stack);
                VkOffset2D scissorOffset = scissor.get(0).offset().set(0, 0);
                VkExtent2D scissorExtent = scissor.get(0).extent().set(currentW, currentH);
                VkCommandBufferBeginInfo beginInfo = VkCommandBufferBeginInfo.calloc(stack)
                        .sType(VK_STRUCTURE_TYPE_COMMAND_BUFFER_BEGIN_INFO);
                org.lwjgl.vulkan.VkRenderPassBeginInfo renderBegin = org.lwjgl.vulkan.VkRenderPassBeginInfo.calloc(stack)
                        .sType(VK_STRUCTURE_TYPE_RENDER_PASS_BEGIN_INFO)
                        .renderPass(RenderPass.get(renderPass))
                        .framebuffer(VKFramebuffer.get(Long.get(framebuffers, imageIndex)))
                        .renderArea(r -> r.offset(o -> o.set(0, 0)).extent(e -> e.set(currentW, currentH)))
                        .pClearValues(clearValues)
        ) {
            org.lwjgl.vulkan.VkCommandBuffer command = new org.lwjgl.vulkan.VkCommandBuffer(commandBuffer, Vulkan.getDevice());
            if (vkBeginCommandBuffer(command, beginInfo) != VK_SUCCESS) {
                throw new IllegalStateException("Failed to begin triangle command buffer.");
            }

            vkCmdBeginRenderPass(command, renderBegin, VK_SUBPASS_CONTENTS_INLINE);
            vkCmdBindPipeline(command, VK_PIPELINE_BIND_POINT_GRAPHICS, VKPipeline.get(pipeline));

            // Push the animation time so the draw thread's per-frame re-records visibly animate.
            vkCmdPushConstants(command, VKPipelineLayout.get(pipelineLayout),
                    VK_SHADER_STAGE_VERTEX_BIT, 0, stack.floats(bgTime));

            // setting the viewport
            vkCmdSetViewport(command, 0, vpBuffer);

            // setting the scissor
            vkCmdSetScissor(command, 0, scissor);

            vkCmdDraw(command, 3, 1, 0, 0);

            // Textured picture (@Draft pending review): draw the darling.Picture's
            // resolved rect (AUTO dims derive from its image) with its texture bound.
            // The picture resolves inside the darling.Canvas virtual space, and the
            // canvas ortho projection is pushed so the rect is stable canvas units.
            if (pictureNode != 0L) {
                long imagePtr = darling.Picture.getImage(pictureNode);
                if (imagePtr != 0L) {
                    long rect = Vec4.allocate();
                    long crop = primitive.Float.allocateArray(4);
                    try {
                        // Stack-owned (freed when the MemoryStack closes); not an
                        // AutoCloseable, so it lives in the body, not the try header.
                        FloatBuffer pushData = stack.mallocFloat(24);
                        darling.Canvas.resolveRoot(pictureNode, currentW, currentH, rect);
                        logResizeIfChanged(currentW, currentH, rect);
                        darling.Picture.getCrop(pictureNode, crop);
                        vkCmdBindPipeline(command, VK_PIPELINE_BIND_POINT_GRAPHICS, VKPipeline.get(imageQuadPipeline));
                        vkCmdBindDescriptorSets(command, VK_PIPELINE_BIND_POINT_GRAPHICS,
                                VKPipelineLayout.get(imageQuadPipelineLayout), 0, stack.longs(
                                        image.Image.getDescriptorSet(imagePtr)), null);
                        // proj(16) + rect(4) + crop(4) = 24 floats, one push.
                        darling.Canvas.buildProjection(canvasProj, currentW, currentH);
                        int pc = 0;
                        for (int i = 0; i < 16; i++) pushData.put(pc++, Mat4.getRaw(canvasProj, i));
                        pushData.put(pc++, Vec4.getX(rect));
                        pushData.put(pc++, Vec4.getY(rect));
                        pushData.put(pc++, Vec4.getZ(rect));
                        pushData.put(pc++, Vec4.getW(rect));
                        pushData.put(pc++, primitive.Float.get(crop, 0));
                        pushData.put(pc++, primitive.Float.get(crop, 1));
                        pushData.put(pc++, primitive.Float.get(crop, 2));
                        pushData.put(pc++, primitive.Float.get(crop, 3));
                        vkCmdPushConstants(command, VKPipelineLayout.get(imageQuadPipelineLayout),
                                VK_SHADER_STAGE_VERTEX_BIT, 0, pushData.position(0).limit(24));
                        vkCmdSetViewport(command, 0, vpBuffer);
                        vkCmdSetScissor(command, 0, scissor);
                        vkCmdDraw(command, 6, 1, 0, 0);
                    } finally {
                        primitive.Float.free(crop);
                        Vec4.free(rect);
                    }
                }
            }

            vkCmdEndRenderPass(command);

            if (vkEndCommandBuffer(command) != VK_SUCCESS) {
                throw new IllegalStateException("Failed to end triangle command buffer.");
            }
        }
    }

    public static void destroy() {
        if (!initialized) return;

        var device = Vulkan.getDevice();
        Renderer.destroy(device, commandPool, blitCommandPool);
        CommandPool.destroy(commandPool, device);
        CommandPool.destroy(blitCommandPool, device);

        VKPipeline.destroy(pipeline, device);
        VKPipelineLayout.destroy(pipelineLayout, device);
        VKShaderModule.destroy(fragmentShader, device);
        VKShaderModule.destroy(vertexShader, device);

        if (imageQuadPipeline != 0L) VKPipeline.destroy(imageQuadPipeline, device);
        if (imageQuadPipelineLayout != 0L) VKPipelineLayout.destroy(imageQuadPipelineLayout, device);
        if (imageQuadFragmentShader != 0L) VKShaderModule.destroy(imageQuadFragmentShader, device);
        if (imageQuadVertexShader != 0L) VKShaderModule.destroy(imageQuadVertexShader, device);

        destroyOffscreenAttachments(device);

        if (canvasProj != 0L) Mat4.free(canvasProj);
        canvasProj = 0L;

        RenderPass.destroy(renderPass, device);
        renderPass = 0L;
        commandPool = 0L;
        blitCommandPool = 0L;
        pipeline = 0L;
        pipelineLayout = 0L;
        fragmentShader = 0L;
        vertexShader = 0L;
        imageQuadPipeline = 0L;
        imageQuadPipelineLayout = 0L;
        imageQuadSetLayout = 0L;
        imageQuadFragmentShader = 0L;
        imageQuadVertexShader = 0L;
        pictureNode = 0L;
        initialized = false;
    }
}
