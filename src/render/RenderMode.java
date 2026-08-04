package render;

import annotation.Draft;
import annotation.Intention;

/**
 * Defines the rendering pipeline strategy (Forward, Deferred, Tile-Based Deferred, Clustered Forward).
 * Configures Vulkan RenderPass subpasses and off-heap attachment memory flags accordingly.
 */
@Draft
@Intention("Encapsulates pipeline execution strategies including Apple Silicon TBDR tile-memory attachments.")
public enum RenderMode {

    /**
     * Standard Forward Rendering.
     * Single render pass rendering geometry directly with lit shaders.
     * Best for simple scenes, alpha transparency, and low-end hardware.
     */
    FORWARD(0, "Forward"),

    /**
     * Classic Desktop Deferred Rendering.
     * Pass 1: Write Position, Normal, Albedo, and Specular to VRAM G-Buffer attachments.
     * Pass 2: Screen-space deferred lighting shader pass reading G-Buffer textures.
     * Best for desktop dGPUs (NVIDIA / AMD) with high VRAM bandwidth and hundreds of dynamic lights.
     */
    DEFERRED(1, "Deferred"),

    /**
     * Apple Silicon UMA Tile-Based Deferred Rendering (TBDR).
     * Uses Vulkan RenderPass Subpasses.
     * G-Buffer and Depth attachments are flagged as LAZILY_ALLOCATED + TRANSIENT_ATTACHMENT.
     * Attachments live 100% inside GPU on-chip SRAM tile cache and are never written back to RAM.
     */
    TILE_BASED_DEFERRED(2, "Tile-Based Deferred (TBDR)"),

    /**
     * Clustered Forward (+) Rendering.
     * Compute Pass: Subdivides view frustum into 3D grid clusters (16x9x24) and culls lights.
     * Forward Pass: Shaders lookup light index lists from off-heap cluster buffers.
     * Best for handling thousands of dynamic lights with alpha blending support on all platforms.
     */
    CLUSTERED_FORWARD(3, "Clustered Forward");

    private final int id;
    private final String displayName;

    RenderMode(int id, String displayName) {
        this.id = id;
        this.displayName = displayName;
    }

    public int getId() {
        return id;
    }

    public String getDisplayName() {
        return displayName;
    }

    /**
     * Returns true if this render mode uses on-chip SRAM tile memory (Apple Silicon UMA optimization).
     */
    public boolean isTileBased() {
        return this == TILE_BASED_DEFERRED;
    }

    /**
     * Returns true if this render mode writes G-Buffer targets for deferred shading.
     */
    public boolean isDeferred() {
        return this == DEFERRED || this == TILE_BASED_DEFERRED;
    }

    /**
     * Returns true if this render mode uses a compute pass for light clustering.
     */
    public boolean isClustered() {
        return this == CLUSTERED_FORWARD;
    }

    /**
     * Returns the recommended Vulkan image usage flags for transient G-Buffer attachments in this mode.
     *
     * @param isTransientAttachment true if the attachment can live purely in on-chip SRAM tile cache.
     * @return Vulkan usage bitmask integer.
     */
    public int getAttachmentUsageFlags(boolean isTransientAttachment) {
        if (isTileBased() && isTransientAttachment) {
            // VK_IMAGE_USAGE_COLOR_ATTACHMENT_BIT (0x10) | VK_IMAGE_USAGE_INPUT_ATTACHMENT_BIT (0x100) | VK_IMAGE_USAGE_TRANSIENT_ATTACHMENT_BIT (0x40)
            return 0x10 | 0x100 | 0x40;
        }
        // VK_IMAGE_USAGE_COLOR_ATTACHMENT_BIT (0x10) | VK_IMAGE_USAGE_SAMPLED_BIT (0x04)
        return 0x10 | 0x04;
    }

    /**
     * Automatically selects the optimal default rendering pipeline mode for the current operating system and GPU architecture.
     */
    public static RenderMode autoSelect() {
        String os = System.getProperty("os.name").toLowerCase();
        if (os.contains("mac")) {
            return TILE_BASED_DEFERRED; // Apple Silicon UMA TBDR optimization
        }
        return DEFERRED; // Desktop dGPU default
    }
}
