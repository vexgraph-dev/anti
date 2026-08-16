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
import org.lwjgl.vulkan.VkCommandBuffer;
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

    // The active scene root (a darling.Scene / Scene2D / Scene3D node). Its
    // virtual size drives the offscreen render target and its background color
    // is the render-pass clear color; the present pass scales the offscreen
    // into the real window, so scene re-rendering is decoupled from window
    // resize. 0 = no scene set (fall back to screen backing size).
    private static long sceneNode;
    private static long scene3DNode;
    private static long boundWindowPtr;

    private static long darlingPanelVertexShader;
    private static long darlingPanelFragmentShader;
    private static long darlingPanelPipelineLayout;
    private static long darlingPanelPipeline;
    private static long rootUiNode;

    public static void setWindow(long window) {
        boundWindowPtr = window;
    }

    public static void setScene3D(long scene3DPtr) {
        scene3DNode = scene3DPtr;
    }

    public static long getScene3D() {
        return scene3DNode;
    }

    public static void setRootUi(long rootPtr) {
        rootUiNode = rootPtr;
    }

    public static long getRootUi() {
        return rootUiNode;
    }

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

        darlingPanelVertexShader = createShaderModule("darling_panel_vert.spv");
        darlingPanelFragmentShader = createShaderModule("darling_panel_frag.spv");
        createDarlingPanelPipeline();

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

    /**
     * Binds the active scene root. Its virtual size drives the offscreen render
     * target (fixed resolution the scene renders into) and its background color
     * is the render-pass clear color. Must be called before init() so the
     * offscreen attachments are sized to the scene.
     */
    public static void setScene(long scenePtr) {
        if (scenePtr == 0L) throw new IllegalArgumentException("Scene pointer must not be NULL");
        sceneNode = scenePtr;
        if (darling.Scene.classId(scenePtr) == darling.Scene3D.CLASS_ID) {
            scene3DNode = scenePtr;
        }
        System.out.println("Scene node set: class=" + darling.Scene.classId(scenePtr)
                + " virtual=" + darling.Scene.getVirtualWidth(scenePtr) + "x"
                + darling.Scene.getVirtualHeight(scenePtr));
    }

    /** Creates off-screen color images and framebuffers the draw thread renders into. */
    private static void createOffscreenAttachments(VkDevice device) {
        offscreenImageCount = Math.max(Vulkan.getSwapchainImageCount(), Renderer.MAX_FRAMES_IN_FLIGHT);
        int format = Vulkan.getSwapchainFormat();

        // Offscreen render targets pinned at max screen backing resolution, so
        // live resizing and window changes never reallocate GPU attachments or framebuffers.
        long screenSize = window.Window.getScreenBackingSize();
        int w = (int) (screenSize >>> 32);
        int h = (int) (screenSize & 0xFFFFFFFFL);
        if (w <= 0 || h <= 0) {
            w = Vulkan.getSwapchainWidth();
            h = Vulkan.getSwapchainHeight();
        }
        offscreenWidth = w;
        offscreenHeight = h;
        System.out.println("[offscreen] target=" + w + "x" + h
                + " swapchain=" + Vulkan.getSwapchainWidth() + "x" + Vulkan.getSwapchainHeight()
                + " dpi=" + (boundWindowPtr != 0L ? (float) window.Window.getBackingScaleFactor(boundWindowPtr) : 1.0f));

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

    /** Mapping mode of the active scene node (MODE_STRETCH/FIT/PIXEL); 0 = STRETCH when no scene. */
    public static int getSceneMode() {
        if (sceneNode == 0L) return darling.Scene.MODE_STRETCH;
        return darling.Scene.getMode(sceneNode);
    }

    /** Background color (0xAARRGGBB) of the active scene node; 0 = clear when no scene. */
    public static int getSceneBackground() {
        if (sceneNode == 0L) return 0;
        return darling.Scene.getBackgroundColor(sceneNode);
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

    private static void createDarlingPanelPipeline() {
        if (darlingPanelPipeline != 0L) return;
        try (
            MemoryStack stack = MemoryStack.stackPush();

            VkPipelineShaderStageCreateInfo.Buffer stages = VkPipelineShaderStageCreateInfo.calloc(2, stack);
            VkPipelineShaderStageCreateInfo info0 = stages.get(0).sType(VK_STRUCTURE_TYPE_PIPELINE_SHADER_STAGE_CREATE_INFO);
            VkPipelineShaderStageCreateInfo info1 = stages.get(1).sType(VK_STRUCTURE_TYPE_PIPELINE_SHADER_STAGE_CREATE_INFO);

            VkPushConstantRange.Buffer pushRanges = VkPushConstantRange.malloc(1, stack);
            VkPushConstantRange pushRange0 = pushRanges.get(0)
                    .stageFlags(VK_SHADER_STAGE_VERTEX_BIT | VK_SHADER_STAGE_FRAGMENT_BIT)
                    .offset(0).size(112)
                    // proj(64) + rect(16) + color(16) + style(16) = 112B
        ) {
            info0.stage(VK_SHADER_STAGE_VERTEX_BIT)
                .module(VKShaderModule.get(darlingPanelVertexShader))
                .pName(stack.UTF8("main"));
            info1.stage(VK_SHADER_STAGE_FRAGMENT_BIT)
                .module(VKShaderModule.get(darlingPanelFragmentShader))
                .pName(stack.UTF8("main"));

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
                    .cullMode(VK_CULL_MODE_NONE)
                    .frontFace(VK_FRONT_FACE_CLOCKWISE)
                    .depthBiasEnable(false);

            VkPipelineMultisampleStateCreateInfo multisampling = VkPipelineMultisampleStateCreateInfo.calloc(stack)
                    .sType(VK_STRUCTURE_TYPE_PIPELINE_MULTISAMPLE_STATE_CREATE_INFO)
                    .rasterizationSamples(VK_SAMPLE_COUNT_1_BIT)
                    .sampleShadingEnable(false);

            VkPipelineColorBlendAttachmentState.Buffer colorAttachment = VkPipelineColorBlendAttachmentState.calloc(1, stack)
                    .colorWriteMask(VK_COLOR_COMPONENT_R_BIT | VK_COLOR_COMPONENT_G_BIT | VK_COLOR_COMPONENT_B_BIT | VK_COLOR_COMPONENT_A_BIT)
                    .blendEnable(true)
                    .srcColorBlendFactor(VK_BLEND_FACTOR_SRC_ALPHA)
                    .dstColorBlendFactor(VK_BLEND_FACTOR_ONE_MINUS_SRC_ALPHA)
                    .colorBlendOp(VK_BLEND_OP_ADD)
                    .srcAlphaBlendFactor(VK_BLEND_FACTOR_ONE)
                    .dstAlphaBlendFactor(VK_BLEND_FACTOR_ONE_MINUS_SRC_ALPHA)
                    .alphaBlendOp(VK_BLEND_OP_ADD);

            VkPipelineColorBlendStateCreateInfo colorBlending = VkPipelineColorBlendStateCreateInfo.calloc(stack)
                    .sType(VK_STRUCTURE_TYPE_PIPELINE_COLOR_BLEND_STATE_CREATE_INFO)
                    .logicOpEnable(false)
                    .logicOp(VK_LOGIC_OP_COPY)
                    .attachmentCount(1)
                    .pAttachments(colorAttachment)
                    .blendConstants(stack.floats(0.0f, 0.0f, 0.0f, 0.0f));

            darlingPanelPipelineLayout = VKPipelineLayout.create(Vulkan.getDevice(),
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
                    .layout(VKPipelineLayout.get(darlingPanelPipelineLayout))
                    .renderPass(RenderPass.get(renderPass))
                    .subpass(0);

            darlingPanelPipeline = VKPipeline.createGraphicsPipeline(Vulkan.getDevice(), VK_NULL_HANDLE, pipelineInfo);
            System.out.println("Darling Panel pipeline created.");
        }
    }

    private static void renderContainerTree(VkCommandBuffer command, MemoryStack stack,
                                           long nodePtr, float parentX, float parentY,
                                           float parentW, float parentH,
                                           int clipX, int clipY, int clipW, int clipH,
                                           long projMat, float dpi,
                                           FloatBuffer pushData, VkRect2D.Buffer scissorBuf,
                                           VkViewport.Buffer vpBuffer) {
        if (nodePtr == 0L || !darling.Container.isVisible(nodePtr)) return;

        long rect = Vec4.allocate();
        try {
            int cls = darling.Container.classId(nodePtr);
            if (cls == darling.Picture.CLASS_ID) {
                darling.Picture.resolve(nodePtr, parentX, parentY, parentW, parentH, rect);
            } else {
                darling.Container.resolve(nodePtr, parentX, parentY, parentW, parentH, rect);
            }

            float rx = Vec4.getX(rect);
            float ry = Vec4.getY(rect);
            float rw = Vec4.getZ(rect);
            float rh = Vec4.getW(rect);

            int sx = (int) Math.round(rx * dpi);
            int sy = (int) Math.round(ry * dpi);
            int sw = (int) Math.round(rw * dpi);
            int sh = (int) Math.round(rh * dpi);

            int curClipX0 = Math.max(clipX, sx);
            int curClipY0 = Math.max(clipY, sy);
            int curClipX1 = Math.min(clipX + clipW, sx + sw);
            int curClipY1 = Math.min(clipY + clipH, sy + sh);
            int childClipW = Math.max(0, curClipX1 - curClipX0);
            int childClipH = Math.max(0, curClipY1 - curClipY0);

            if (oop.TypeRegister.isA(cls, darling.Panel.CLASS_ID)) {
                int color = darling.Panel.getBackgroundColor(nodePtr);
                int alpha = (color >>> 24) & 0xFF;
                if (alpha > 0) {
                    float a = alpha / 255.0f;
                    float r = ((color >>> 16) & 0xFF) / 255.0f;
                    float g = ((color >>> 8) & 0xFF) / 255.0f;
                    float b = (color & 0xFF) / 255.0f;

                    vkCmdBindPipeline(command, VK_PIPELINE_BIND_POINT_GRAPHICS, VKPipeline.get(darlingPanelPipeline));
                    vkCmdSetViewport(command, 0, vpBuffer);

                    int finalClipX = Math.max(0, Math.min(offscreenWidth, clipX));
                    int finalClipY = Math.max(0, Math.min(offscreenHeight, clipY));
                    int finalClipW = Math.max(0, Math.min(offscreenWidth - finalClipX, clipW));
                    int finalClipH = Math.max(0, Math.min(offscreenHeight - finalClipY, clipH));

                    scissorBuf.get(0).offset().set(finalClipX, finalClipY);
                    scissorBuf.get(0).extent().set(finalClipW, finalClipH);
                    vkCmdSetScissor(command, 0, scissorBuf);

                    pushData.position(0);
                    for (int i = 0; i < 16; i++) pushData.put(Mat4.getRaw(projMat, i));
                    pushData.put(rx).put(ry).put(rw).put(rh);
                    pushData.put(r).put(g).put(b).put(a);
                    pushData.put(0.0f).put(0.0f).put(0.0f).put(0.0f);
                    pushData.position(0).limit(28);

                    vkCmdPushConstants(command, VKPipelineLayout.get(darlingPanelPipelineLayout),
                            VK_SHADER_STAGE_VERTEX_BIT | VK_SHADER_STAGE_FRAGMENT_BIT, 0, pushData);

                    vkCmdDraw(command, 6, 1, 0, 0);
                }

                boolean clipChildren = darling.Container.isClipChildren(nodePtr);
                int nextClipX = clipChildren ? curClipX0 : clipX;
                int nextClipY = clipChildren ? curClipY0 : clipY;
                int nextClipW = clipChildren ? childClipW : clipW;
                int nextClipH = clipChildren ? childClipH : clipH;

                int n = darling.Panel.childCount(nodePtr);
                for (int i = 0; i < n; i++) {
                    long childPtr = darling.Panel.getChild(nodePtr, i);
                    renderContainerTree(command, stack, childPtr, rx, ry, rw, rh,
                            nextClipX, nextClipY, nextClipW, nextClipH,
                            projMat, dpi, pushData, scissorBuf, vpBuffer);
                }
            }
        } finally {
            Vec4.free(rect);
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

    @Intention("this comment is for determining the background color")
    static void recordCommandBuffer(long commandBuffer, int imageIndex, float time) {
        if (commandBuffer == VK_NULL_HANDLE) {
            throw new IllegalStateException("Triangle command buffer handle is NULL at index " + imageIndex);
        }

        // -Danti.static=1 renders a static background (time pinned to 0) so the "stretch
        // feeling" during a drag can be A/B isolated from the demo shader's self-animating
        // gradient + bouncing triangle.
        float bgTime = staticBackground ? 0f : time;

        long contentSize = boundWindowPtr != 0L ? window.Window.getContentSize(boundWindowPtr) : 0L;
        int rawW = contentSize != 0L ? (int) (contentSize >>> 32) : offscreenWidth;
        int rawH = contentSize != 0L ? (int) (contentSize & 0xFFFFFFFFL) : offscreenHeight;
        if (rawW <= 0) rawW = offscreenWidth;
        if (rawH <= 0) rawH = offscreenHeight;
        final int currentW = Math.min(rawW, offscreenWidth);
        final int currentH = Math.min(rawH, offscreenHeight);

        // Scene background (0xAARRGGBB from the Panel payload) becomes the clear
        // color; the scene is the fixed-res render target, so the clear is scene
        // size, not window size. The offscreen image is B8G8R8A8 (same as the
        // swapchain), so clear components must be written in BGRA surface order:
        // float32(0)=B, (1)=G, (2)=R, (3)=A.
        int bg = sceneNode != 0L ? darling.Scene.getBackgroundColor(sceneNode) : 0;
        float bgB = (bg & 0xFF) / 255f;
        float bgG = ((bg >>> 8) & 0xFF) / 255f;
        float bgR = ((bg >>> 16) & 0xFF) / 255f;
        float bgA = ((bg >>> 24) & 0xFF) / 255f;
        if (bg == 0) {
            bgA = 1.0f; // opaque solid black
        }

        try (
                MemoryStack stack = MemoryStack.stackPush();
                VkClearValue.Buffer clearValues = VkClearValue.calloc(1, stack);
                VkClearColorValue clearColorValue0 = clearValues.get(0).color()
                        // background clear color (BGRA surface order)
                        .float32(0, bgB)
                        .float32(1, bgG)
                        .float32(2, bgR)
                        .float32(3, bgA);

                // Viewport and scissor covering the master screen buffer
                VkViewport.Buffer vpBuffer = VkViewport.calloc(1, stack);
                VkViewport _viewport = vpBuffer.get(0).set(0.0f, 0.0f, offscreenWidth, offscreenHeight, 0.0f, 1.0f);

                VkRect2D.Buffer scissor = VkRect2D.calloc(1, stack);
                VkOffset2D scissorOffset = scissor.get(0).offset().set(0, 0);
                VkExtent2D scissorExtent = scissor.get(0).extent().set(offscreenWidth, offscreenHeight);
                VkCommandBufferBeginInfo beginInfo = VkCommandBufferBeginInfo.calloc(stack)
                        .sType(VK_STRUCTURE_TYPE_COMMAND_BUFFER_BEGIN_INFO);
                org.lwjgl.vulkan.VkRenderPassBeginInfo renderBegin = org.lwjgl.vulkan.VkRenderPassBeginInfo.calloc(stack)
                        .sType(VK_STRUCTURE_TYPE_RENDER_PASS_BEGIN_INFO)
                        .renderPass(RenderPass.get(renderPass))
                        .framebuffer(VKFramebuffer.get(Long.get(framebuffers, imageIndex)))
                        .renderArea(r -> r.offset(o -> o.set(0, 0)).extent(e -> e.set(offscreenWidth, offscreenHeight)))
                        .pClearValues(clearValues)
        ) {
            org.lwjgl.vulkan.VkCommandBuffer command = new org.lwjgl.vulkan.VkCommandBuffer(commandBuffer, Vulkan.getDevice());
            if (vkBeginCommandBuffer(command, beginInfo) != VK_SUCCESS) {
                throw new IllegalStateException("Failed to begin triangle command buffer.");
            }

            float dpi = boundWindowPtr != 0L ? (float) window.Window.getBackingScaleFactor(boundWindowPtr) : 1.0f;
            darling.Canvas.setDpiScale(dpi);

            // Resolve 3D scene container bounds inside the Canvas
            float scene3Dx = 0f;
            float scene3Dy = 0f;
            float scene3Dw = currentW;
            float scene3Dh = currentH;

            long activeScene = scene3DNode != 0L ? scene3DNode : sceneNode;
            if (activeScene != 0L) {
                long rect3D = Vec4.allocate();
                try {
                    darling.Canvas.resolveRoot(activeScene, currentW, currentH, rect3D);
                    scene3Dx = Vec4.getX(rect3D) * dpi;
                    scene3Dy = Vec4.getY(rect3D) * dpi;
                    scene3Dw = Vec4.getZ(rect3D) * dpi;
                    scene3Dh = Vec4.getW(rect3D) * dpi;
                } finally {
                    Vec4.free(rect3D);
                }
            }
            if (scene3Dw <= 0f) scene3Dw = currentW;
            if (scene3Dh <= 0f) scene3Dh = currentH;

            VkViewport.Buffer vpBuffer3D = VkViewport.calloc(1, stack);
            vpBuffer3D.get(0).set(scene3Dx, scene3Dy, scene3Dw, scene3Dh, 0.0f, 1.0f);

            VkRect2D.Buffer scissor3D = VkRect2D.calloc(1, stack);
            scissor3D.get(0).offset().set((int) Math.max(0, scene3Dx), (int) Math.max(0, scene3Dy));
            scissor3D.get(0).extent().set((int) Math.min(offscreenWidth, scene3Dw), (int) Math.min(offscreenHeight, scene3Dh));

            vkCmdBeginRenderPass(command, renderBegin, VK_SUBPASS_CONTENTS_INLINE);
            vkCmdBindPipeline(command, VK_PIPELINE_BIND_POINT_GRAPHICS, VKPipeline.get(pipeline));

            // Push the animation time so the draw thread's per-frame re-records visibly animate.
            vkCmdPushConstants(command, VKPipelineLayout.get(pipelineLayout),
                    VK_SHADER_STAGE_VERTEX_BIT, 0, stack.floats(bgTime));

            // Setting the 3D scene container viewport and scissor (anchored inside the 2D canvas)
            vkCmdSetViewport(command, 0, vpBuffer3D);
            vkCmdSetScissor(command, 0, scissor3D);

            vkCmdDraw(command, 3, 1, 0, 0);

            // Textured picture: draw the darling.Picture's resolved rect inside the master canvas
            if (pictureNode != 0L) {
                long imagePtr = darling.Picture.getImage(pictureNode);
                if (imagePtr != 0L) {
                    long rect = Vec4.allocate();
                    long crop = primitive.Float.allocateArray(4);
                    try {
                        FloatBuffer pushData = stack.mallocFloat(24);
                        darling.Canvas.resolveRoot(pictureNode, currentW, currentH, rect);
                        logResizeIfChanged(currentW, currentH, rect);
                        darling.Picture.getCrop(pictureNode, crop);
                        vkCmdBindPipeline(command, VK_PIPELINE_BIND_POINT_GRAPHICS, VKPipeline.get(imageQuadPipeline));
                        vkCmdBindDescriptorSets(command, VK_PIPELINE_BIND_POINT_GRAPHICS,
                                VKPipelineLayout.get(imageQuadPipelineLayout), 0, stack.longs(
                                        image.Image.getDescriptorSet(imagePtr)), null);
                        // proj(16) + rect(4) + crop(4) = 24 floats, one push.
                        // Projection spans the ACTUAL render target (the fixed offscreen
                        // framebuffer), so canvas units map 1:1 to framebuffer pixels:
                        // c -> c*dpi. The live window size is only the snip window on top
                        // of it, so the picture never re-scales when the window resizes.
                        darling.Canvas.buildProjection(canvasProj, offscreenWidth, offscreenHeight);
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

            // Render UI container tree (sceneNode children and rootUiNode) into the
            // SAME master offscreen buffer as the 3D scene and picture — one unified
            // single-pass target (offscreenWidth x offscreenHeight).
            // and scene all share the single stable canvasProj and never skew. The
            // projection spans the fixed offscreen buffer; nodes resolve against the
            // live visible canvas (currentW/dpi), which the draw thread re-records
            // every frame, so anchored panels track the window edge without any
            // present-time second pass.
            if (sceneNode != 0L || rootUiNode != 0L) {
                darling.Canvas.buildProjection(canvasProj, offscreenWidth, offscreenHeight);
                FloatBuffer panelPushData = stack.mallocFloat(28);
                VkRect2D.Buffer panelScissor = VkRect2D.calloc(1, stack);

                float winW = (dpi > 0f) ? (currentW / dpi) : currentW;
                float winH = (dpi > 0f) ? (currentH / dpi) : currentH;

                if (System.getProperty("anti.debug.panel") != null) {
                    long dbgRect = Vec4.allocate();
                    try {
                        long node = rootUiNode != 0L ? rootUiNode : sceneNode;
                        darling.Container.resolve(node, 0f, 0f, winW, winH, dbgRect);
                        float rx = Vec4.getX(dbgRect), ry = Vec4.getY(dbgRect);
                        float rw = Vec4.getZ(dbgRect), rh = Vec4.getW(dbgRect);
                        System.out.println("[panel] win=" + currentW + "x" + currentH
                                + " canvas=" + winW + "x" + winH
                                + " rect(canvas)=(" + rx + "," + ry + "," + rw + "," + rh + ")"
                                + " rect(px)=(" + (int) Math.round(rx * dpi) + "," + (int) Math.round(ry * dpi) + ","
                                + (int) Math.round(rw * dpi) + "," + (int) Math.round(rh * dpi) + ")"
                                + " offscreen=" + offscreenWidth + "x" + offscreenHeight);
                    } finally {
                        Vec4.free(dbgRect);
                    }
                }

                if (sceneNode != 0L && oop.TypeRegister.isA(darling.Container.classId(sceneNode), darling.Panel.CLASS_ID)) {
                    int childCount = darling.Panel.childCount(sceneNode);
                    for (int i = 0; i < childCount; i++) {
                        long childPtr = darling.Panel.getChild(sceneNode, i);
                        // Clip to the FULL master offscreen, not the live window: a
                        // top-left panel must be fully painted into the master buffer
                        // even where the current window crop doesn't reach, so expanding
                        // the window reveals already-painted pixels instantly (no
                        // paint-to-catch-up gap). Layout still resolves against the live
                        // window (winW/winH) so anchored panels track the edge.
                        renderContainerTree(command, stack, childPtr, 0f, 0f, winW, winH,
                                0, 0, offscreenWidth, offscreenHeight,
                                canvasProj, dpi, panelPushData, panelScissor, vpBuffer);
                    }
                }
                if (rootUiNode != 0L) {
                    renderContainerTree(command, stack, rootUiNode, 0f, 0f, winW, winH,
                            0, 0, offscreenWidth, offscreenHeight,
                            canvasProj, dpi, panelPushData, panelScissor, vpBuffer);
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

        if (darlingPanelPipeline != 0L) VKPipeline.destroy(darlingPanelPipeline, device);
        if (darlingPanelPipelineLayout != 0L) VKPipelineLayout.destroy(darlingPanelPipelineLayout, device);
        if (darlingPanelFragmentShader != 0L) VKShaderModule.destroy(darlingPanelFragmentShader, device);
        if (darlingPanelVertexShader != 0L) VKShaderModule.destroy(darlingPanelVertexShader, device);

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
        darlingPanelPipeline = 0L;
        darlingPanelPipelineLayout = 0L;
        darlingPanelFragmentShader = 0L;
        darlingPanelVertexShader = 0L;
        pictureNode = 0L;
        rootUiNode = 0L;
        initialized = false;
    }
}
