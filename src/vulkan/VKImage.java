package vulkan;

import annotation.Draft;
import annotation.Required;
import annotation.Unsafe;
import annotation.Volatile;
import image.Image;
import nio.ForeignMemory;
import oop.TypeRegister;

import org.lwjgl.vulkan.VkDevice;
import org.lwjgl.vulkan.VkImageCreateInfo;
import org.lwjgl.vulkan.VkMemoryRequirements;
import org.lwjgl.vulkan.VkPhysicalDeviceMemoryProperties;
import org.lwjgl.system.MemoryStack;

import java.lang.foreign.Arena;
import java.lang.invoke.MethodHandles;
import java.lang.invoke.VarHandle;
import java.nio.LongBuffer;

import static org.lwjgl.vulkan.VK10.*;

/**
 * Off-screen color image factory. Creates a device-local VkImage bound to its
 * own memory and a matching image view, so the engine can render into a private
 * texture instead of a swapchain image. The engine pointer's data slot stores a
 * pointer to an off-heap struct holding [imageHandle, memoryEnginePtr, viewEnginePtr].
 */
@Draft
public final class VKImage {

    @Required
    public static final int CLASS_ID = TypeRegister.ID_VK_IMAGE;

    public static final int TYPE_SINGLETON = TypeRegister.VK_IMAGE_SINGLETON;
    public static final int TYPE_ARRAY     = TypeRegister.VK_IMAGE_ARRAY;
    public static final int TYPE_POINTER   = TypeRegister.VK_IMAGE_POINTER;

    private static final int DEFAULT_CAPACITY = 1024;
    private static final long SINGLETON_SLOT_SIZE = 16L; // 8B header + 8B data

    private static final long STRUCT_SIZE = 24L; // image | memoryEnginePtr | viewEnginePtr

    private static final VarHandle SINGLETON_FREE_HEAD_VH;
    private static final VarHandle SINGLETON_EXPANDING_VH;

    private static volatile int singletonExpanding = 0;
    private static volatile long singletonFreeHead;
    private static Arena poolArena;
    private static volatile boolean active;

    static {
        try {
            MethodHandles.Lookup lookup = MethodHandles.lookup();
            SINGLETON_FREE_HEAD_VH = lookup.findStaticVarHandle(VKImage.class, "singletonFreeHead", long.class);
            SINGLETON_EXPANDING_VH = lookup.findStaticVarHandle(VKImage.class, "singletonExpanding", int.class);
        } catch (ReflectiveOperationException e) {
            throw new ExceptionInInitializerError(e);
        }
        poolArena = Arena.ofShared();
        active = true;
        expandSingletonPool();
    }

    private VKImage() {}

    private static void checkActive() {
        if (!active) throw new IllegalStateException("VKImage subsystem is not active!");
    }

    public static void freeAll() {
        if (active) {
            active = false;
            if (poolArena != null && poolArena.scope().isAlive()) {
                poolArena.close();
            }
        }
    }

    private static void expandSingletonPool() {
        long totalBytes = DEFAULT_CAPACITY * SINGLETON_SLOT_SIZE;
        long baseAddress = poolArena.allocate(totalBytes, 8).address();
        for (int i = 0; i < DEFAULT_CAPACITY; i++) {
            long currentBlock = baseAddress + (i * SINGLETON_SLOT_SIZE);
            long userPtr = currentBlock + 8L;
            while (true) {
                long oldTagged = singletonFreeHead;
                long oldRawHead = oldTagged & 0x0000FFFFFFFFFFFFL;
                ForeignMemory.setLong(userPtr, oldRawHead);
                long nextGen = ((oldTagged >>> 48) + 1L) & 0xFFFFL;
                long newTagged = (nextGen << 48) | (userPtr & 0x0000FFFFFFFFFFFFL);
                if (SINGLETON_FREE_HEAD_VH.compareAndSet(oldTagged, newTagged)) break;
            }
        }
    }

    private static long allocateSingleton() {
        checkActive();
        while (true) {
            long oldTagged = singletonFreeHead;
            long rawHead = oldTagged & 0x0000FFFFFFFFFFFFL;
            if (rawHead == 0L) {
                if (SINGLETON_EXPANDING_VH.compareAndSet(0, 1)) {
                    expandSingletonPool();
                    SINGLETON_EXPANDING_VH.setVolatile(0);
                } else {
                    Thread.onSpinWait();
                }
                continue;
            }
            long nextRawHead = ForeignMemory.getLong(rawHead);
            long nextGen = ((oldTagged >>> 48) + 1L) & 0xFFFFL;
            long newTagged = (nextGen << 48) | (nextRawHead & 0x0000FFFFFFFFFFFFL);
            if (SINGLETON_FREE_HEAD_VH.compareAndSet(oldTagged, newTagged)) {
                long base = rawHead - 8L;
                ForeignMemory.setInt(base, TYPE_SINGLETON);
                ForeignMemory.setInt(base + 4L, 1);
                ForeignMemory.setLong(rawHead, 0L);
                return rawHead;
            }
        }
    }

    public static void free(long pointer) {
        checkActive();
        if (pointer == 0L) return;
        int type = ForeignMemory.getInt(pointer - 8L);
        if (type == 0 || !TypeRegister.isSingleton(type)) {
            throw new IllegalStateException("Double free or corrupt off-heap pointer: 0x" + java.lang.Long.toHexString(pointer).toUpperCase());
        }
        ForeignMemory.setInt(pointer - 8L, 0);
        ForeignMemory.setInt(pointer - 4L, -1);
        while (true) {
            long oldTagged = singletonFreeHead;
            long oldRawHead = oldTagged & 0x0000FFFFFFFFFFFFL;
            ForeignMemory.setLong(pointer, oldRawHead);
            long nextGen = ((oldTagged >>> 48) + 1L) & 0xFFFFL;
            long newTagged = (nextGen << 48) | (pointer & 0x0000FFFFFFFFFFFFL);
            if (SINGLETON_FREE_HEAD_VH.compareAndSet(oldTagged, newTagged)) return;
        }
    }

    /** Raw VkImage handle held by this engine pointer. */
    public static long getImage(long enginePtr) {
        return Image.getTexture(enginePtr);
    }

    /** Engine pointer (VKDeviceMemory) bound to this image's backing memory. */
    public static long getMemory(long enginePtr) {
        long struct = ForeignMemory.getLong(enginePtr);
        return struct == 0L ? 0L : ForeignMemory.getLong(struct + 8L);
    }

    /** Engine pointer (VKImageView) matching this image. */
    public static long getView(long enginePtr) {
        long struct = ForeignMemory.getLong(enginePtr);
        return struct == 0L ? 0L : ForeignMemory.getLong(struct + 16L);
    }

    private static int findMemoryType(VkDevice device, int typeBits, int properties, MemoryStack stack) {
        VkPhysicalDeviceMemoryProperties memProps = VkPhysicalDeviceMemoryProperties.calloc(stack);
        vkGetPhysicalDeviceMemoryProperties(Vulkan.getPhysicalDevice(), memProps);
        for (int i = 0; i < memProps.memoryTypeCount(); i++) {
            if ((typeBits & (1 << i)) != 0 &&
                (memProps.memoryTypes(i).propertyFlags() & properties) == properties) {
                return i;
            }
        }
        throw new RuntimeException("VKImage: no suitable device-local memory type found");
    }

    /**
     * Creates a device-local off-screen color image, allocates + binds its memory,
     * and creates a matching image view. The image starts in VK_IMAGE_LAYOUT_UNDEFINED.
     */
    public static long create(VkDevice device, int width, int height, int format, int usage) {
        long enginePtr = allocateSingleton();
        long struct = poolArena.allocate(STRUCT_SIZE, 8).address();
        ForeignMemory.setLong(enginePtr, struct);

        try (MemoryStack stack = MemoryStack.stackPush()) {
            VkImageCreateInfo imageInfo = VkImageCreateInfo.calloc(stack)
                    .sType(VK_STRUCTURE_TYPE_IMAGE_CREATE_INFO)
                    .imageType(VK_IMAGE_TYPE_2D)
                    .format(format)
                    .mipLevels(1)
                    .arrayLayers(1)
                    .samples(VK_SAMPLE_COUNT_1_BIT)
                    .tiling(VK_IMAGE_TILING_OPTIMAL)
                    .usage(usage)
                    .sharingMode(VK_SHARING_MODE_EXCLUSIVE)
                    .initialLayout(VK_IMAGE_LAYOUT_UNDEFINED);
            imageInfo.extent().set(width, height, 1);

            LongBuffer pImage = stack.mallocLong(1);
            if (vkCreateImage(device, imageInfo, null, pImage) != VK_SUCCESS) {
                throw new RuntimeException("Failed to create off-screen VkImage!");
            }
            long image = pImage.get(0);

            VkMemoryRequirements memReq = VkMemoryRequirements.calloc(stack);
            vkGetImageMemoryRequirements(device, image, memReq);

            long memoryEnginePtr = VKDeviceMemory.allocate(device, memReq.size(),
                    findMemoryType(device, memReq.memoryTypeBits(),
                            VK_MEMORY_PROPERTY_DEVICE_LOCAL_BIT, stack));

            VKDeviceMemory.bindImage(memoryEnginePtr, device, image, 0);

            long viewEnginePtr = VKImageView.create(device, image, format, VK_IMAGE_ASPECT_COLOR_BIT);

            ForeignMemory.setLong(struct, image);
            ForeignMemory.setLong(struct + 8L, memoryEnginePtr);
            ForeignMemory.setLong(struct + 16L, viewEnginePtr);
        }
        return enginePtr;
    }

    public static void destroy(long enginePtr, VkDevice device) {
        if (enginePtr == 0L) return;
        long struct = ForeignMemory.getLong(enginePtr);
        if (struct != 0L) {
            long image = ForeignMemory.getLong(struct);
            long memoryEnginePtr = ForeignMemory.getLong(struct + 8L);
            long viewEnginePtr = ForeignMemory.getLong(struct + 16L);
            VKImageView.destroy(viewEnginePtr, device);
            VKDeviceMemory.destroy(memoryEnginePtr, device);
            if (image != 0L) {
                vkDestroyImage(device, image, null);
            }
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
