package image;

import annotation.Draft;
import annotation.Intention;
import annotation.Required;
import nio.ForeignMemory;
import oop.TypeRegister;
import org.lwjgl.vulkan.VkDevice;

import java.lang.foreign.Arena;
import java.lang.invoke.MethodHandles;
import java.lang.invoke.VarHandle;

/**
 * Off-heap image asset (@Draft). Thin resource wrapper over a {@link vulkan.VKTexture}:
 * decodes + uploads the file once, then exposes the texture, its descriptor set
 * and the intrinsic pixel dimensions. darling.Picture nodes reference an Image
 * pointer, so the layout layer never depends on the vulkan package directly.
 *
 * The engine pointer's data slot stores a pointer to an off-heap struct holding
 * the owning VKTexture engine pointer.
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

    private static final int DEFAULT_CAPACITY = 256;
    private static final long SINGLETON_SLOT_SIZE = 16L; // 8B header + 8B data

    private static final long STRUCT_SIZE = 8L; // textureEnginePtr

    private static final VarHandle SINGLETON_FREE_HEAD_VH;
    private static final VarHandle SINGLETON_EXPANDING_VH;

    private static volatile int singletonExpanding = 0;
    private static volatile long singletonFreeHead;
    private static Arena poolArena;
    private static volatile boolean active;

    static {
        try {
            MethodHandles.Lookup lookup = MethodHandles.lookup();
            SINGLETON_FREE_HEAD_VH = lookup.findStaticVarHandle(Image.class, "singletonFreeHead", long.class);
            SINGLETON_EXPANDING_VH = lookup.findStaticVarHandle(Image.class, "singletonExpanding", int.class);
        } catch (ReflectiveOperationException e) {
            throw new ExceptionInInitializerError(e);
        }
        poolArena = Arena.ofShared();
        active = true;
        expandSingletonPool();
    }

    private Image() {}

    private static void checkActive() {
        if (!active) throw new IllegalStateException("Image subsystem is not active!");
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

    // --- FACTORY ---

    /**
     * Decodes and uploads {@code path} (see vulkan.VKTexture), returning an Image
     * asset pointer that owns the resulting GPU texture.
     */
    public static long allocate(String path, int maxDimension) {
        long enginePtr = allocateSingleton();
        long struct = poolArena.allocate(STRUCT_SIZE, 8).address();
        ForeignMemory.setLong(enginePtr, struct);
        long textureEnginePtr = vulkan.VKTexture.create(
                vulkan.Vulkan.getDevice(),
                vulkan.Vulkan.getGraphicsQueue(),
                vulkan.Vulkan.getGraphicsQueueFamilyIndex(),
                path, maxDimension);
        ForeignMemory.setLong(struct, textureEnginePtr);
        return enginePtr;
    }

    public static long getTexture(long enginePtr) {
        long struct = ForeignMemory.getLong(enginePtr);
        return struct == 0L ? 0L : ForeignMemory.getLong(struct);
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
        long struct = ForeignMemory.getLong(enginePtr);
        if (struct != 0L) {
            long textureEnginePtr = ForeignMemory.getLong(struct);
            vulkan.VKTexture.destroy(textureEnginePtr, null);
            ForeignMemory.setLong(struct, 0L);
        }
        ForeignMemory.setLong(enginePtr, 0L);
        free(enginePtr);
    }

    public static int classId() {
        return CLASS_ID;
    }
}
