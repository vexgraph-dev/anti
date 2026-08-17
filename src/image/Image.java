package image;

import annotation.Draft;
import annotation.Intention;
import annotation.Required;
import bit.Bit64;
import nio.ForeignMemory;
import oop.TypeRegister;

/**
 * Off-heap image asset (@Draft). Thin resource wrapper over a {@link vulkan.VKTexture}:
 * decodes + uploads the file once, then exposes the texture, its descriptor set
 * and the intrinsic pixel dimensions. darling.Picture nodes reference an Image
 * pointer, so the layout layer never depends on the vulkan package directly.
 *
 * The engine pointer's data slot stores the owning VKTexture engine pointer
 * directly (8B payload fits the singleton slot; no separate struct).
 *
 * REVIEW NOTE: no reference counting / shared ownership yet — destroying an
 * Image while a Picture still references it leaves a dangling pointer.
 */
@Draft
@Intention("Image asset resource: wraps a VKTexture (decode + upload + descriptor set) for darling.Picture nodes.")
public final class Image {

    @Required
    public static final int CLASS_ID = TypeRegister.ID_IMAGE;

    public static final int TYPE_SINGLETON = TypeRegister.IMAGE_SINGLETON;
    public static final int TYPE_ARRAY     = TypeRegister.IMAGE_ARRAY;
    public static final int TYPE_POINTER   = TypeRegister.IMAGE_POINTER;

    private Image() {}

    public static void freeAll() {
        // Bit64.freeAll() manages the shared singleton slot arena.
    }

    // --- FACTORY ---

    /**
     * Decodes and uploads {@code path} (see vulkan.VKTexture), returning an Image
     * asset pointer that owns the resulting GPU texture.
     */
    public static long allocate(String path, int maxDimension) {
        long enginePtr = Bit64.allocateSingleton(TYPE_SINGLETON);
        long textureEnginePtr = vulkan.VKTexture.create(
                vulkan.Vulkan.getDevice(),
                vulkan.Vulkan.getGraphicsQueue(),
                vulkan.Vulkan.getGraphicsQueueFamilyIndex(),
                path, maxDimension);
        ForeignMemory.setLong(enginePtr, textureEnginePtr);
        return enginePtr;
    }

    public static long getTexture(long enginePtr) {
        return ForeignMemory.getLong(enginePtr);
    }

    /** Intrinsic decoded width in pixels. */
    public static int getWidth(long enginePtr) {
        return vulkan.VKTexture.getWidth(getTexture(enginePtr));
    }

    /** Intrinsic decoded height in pixels. */
    public static int getHeight(long enginePtr) {
        return vulkan.VKTexture.getHeight(getTexture(enginePtr));
    }

    /** Raw VkDescriptorSetLayout handle (set 0 = combined image sampler). */
    public static long getDescriptorSetLayout(long enginePtr) {
        return vulkan.VKTexture.getDescriptorSetLayout(getTexture(enginePtr));
    }

    /** Raw VkDescriptorSet handle. */
    public static long getDescriptorSet(long enginePtr) {
        return vulkan.VKTexture.getDescriptorSet(getTexture(enginePtr));
    }

    public static void destroy(long enginePtr) {
        if (enginePtr == 0L) return;
        long textureEnginePtr = ForeignMemory.getLong(enginePtr);
        if (textureEnginePtr != 0L) {
            vulkan.VKTexture.destroy(textureEnginePtr, null);
            ForeignMemory.setLong(enginePtr, 0L);
        }
        Bit64.free(enginePtr);
    }

    public static int classId() {
        return CLASS_ID;
    }
}
