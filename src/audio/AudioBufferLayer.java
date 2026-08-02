package audio;

import annotation.Draft;
import annotation.Intention;
import annotation.Required;
import annotation.Volatile;
import nio.ForeignMemory;
import oop.TypeRegister;

import java.lang.foreign.Arena;
import java.lang.invoke.MethodHandles;
import java.lang.invoke.VarHandle;

/**
 * Off-heap manager for the triple-buffered Audio Layer Swapchain.
 * Manages three distinct buses (Below-Active, Active, Above-Active) to support
 * Photoshop-style audio layer mixing with zero runtime heap allocation.
 */
@Draft
@Intention("Triple-buffered off-heap audio layer swapchain for high-performance canvas mixing")
public final class AudioBufferLayer
{
    @Required
    public static final int CLASS_ID = TypeRegister.ID_AUDIO_BUFFER_LAYER;
    public static final int TYPE_SINGLETON = TypeRegister.AUDIO_BUFFER_LAYER_SINGLETON;

    private static final int DEFAULT_CAPACITY = 256;
    private static final long SINGLETON_SLOT_SIZE = 64L; // Header (8B) + Stride Data (56B)

    // Struct field offsets
    public static final long OFFSET_CAPACITY = 0L;             // Int32
    public static final long OFFSET_BELOW_READ = 4L;           // Address (8B)
    public static final long OFFSET_BELOW_WRITE = 12L;         // Address (8B)
    public static final long OFFSET_ACTIVE_READ = 20L;         // Address (8B)
    public static final long OFFSET_ACTIVE_WRITE = 28L;        // Address (8B)
    public static final long OFFSET_ABOVE_READ = 36L;          // Address (8B)
    public static final long OFFSET_ABOVE_WRITE = 44L;         // Address (8B)

    private static final VarHandle SINGLETON_FREE_HEAD_VH;
    private static final VarHandle SINGLETON_EXPANDING_VH;
    private static volatile int singletonExpanding = 0;

    private static Arena poolArena;
    private static Arena bufferArena; // Arena to allocate the actual PCM sample layers
    private static volatile boolean active;
    private static volatile long singletonFreeHead;

    static {
        try {
            MethodHandles.Lookup lookup = MethodHandles.lookup();
            SINGLETON_FREE_HEAD_VH = lookup.findStaticVarHandle(AudioBufferLayer.class, "singletonFreeHead", long.class);
            SINGLETON_EXPANDING_VH = lookup.findStaticVarHandle(AudioBufferLayer.class, "singletonExpanding", int.class);
        }
        catch(ReflectiveOperationException e) {
            throw new ExceptionInInitializerError(e);
        }

        poolArena = Arena.ofShared();
        bufferArena = Arena.ofShared();
        active = true;

        expandSingletonPool();
    }

    private AudioBufferLayer() {}

    private static void checkActive()
    {
        if(!active) throw new IllegalStateException("AudioBufferLayer subsystem is not active!");
    }

    public static void freeAll()
    {
        if(active) {
            active = false;
            if(poolArena != null && poolArena.scope().isAlive()) {
                poolArena.close();
            }
            if(bufferArena != null && bufferArena.scope().isAlive()) {
                bufferArena.close();
            }
        }
    }

    private static void expandSingletonPool()
    {
        long totalBytes = DEFAULT_CAPACITY * SINGLETON_SLOT_SIZE;
        long baseAddress = poolArena.allocate(totalBytes, 8).address();

        for(int i = 0; i < DEFAULT_CAPACITY; i++) {
            long currentBlock = baseAddress + (i * SINGLETON_SLOT_SIZE);
            long userPtr = currentBlock + 8L;

            while(true) {
                long oldTagged = singletonFreeHead;
                long oldRawHead = oldTagged & 0x0000FFFFFFFFFFFFL;

                ForeignMemory.putLong(userPtr, oldRawHead);

                long nextGen = ((oldTagged >>> 48) + 1L) & 0xFFFFL;
                long newTagged = (nextGen << 48) | (userPtr & 0x0000FFFFFFFFFFFFL);

                if(SINGLETON_FREE_HEAD_VH.compareAndSet(oldTagged, newTagged)) break;
            }
        }
    }

    /**
     * Allocates an AudioBufferLayer instance and its associated double-buffered PCM float arrays.
     * @param capacityInSamples The size of each buffer in samples (e.g. 512 samples).
     */
    public static long allocate(int capacityInSamples)
    {
        checkActive();
        
        long bufferBytes = capacityInSamples * 2L * 4L; // Stereo (2 channels) * Float32 (4 bytes)

        // Allocate the double buffers
        long belowA = bufferArena.allocate(bufferBytes, 32).address();
        long belowB = bufferArena.allocate(bufferBytes, 32).address();
        
        long activeA = bufferArena.allocate(bufferBytes, 32).address();
        long activeB = bufferArena.allocate(bufferBytes, 32).address();
        
        long aboveA = bufferArena.allocate(bufferBytes, 32).address();
        long aboveB = bufferArena.allocate(bufferBytes, 32).address();

        // Clear all arrays
        ForeignMemory.setMemory(belowA, bufferBytes, (byte) 0);
        ForeignMemory.setMemory(belowB, bufferBytes, (byte) 0);
        ForeignMemory.setMemory(activeA, bufferBytes, (byte) 0);
        ForeignMemory.setMemory(activeB, bufferBytes, (byte) 0);
        ForeignMemory.setMemory(aboveA, bufferBytes, (byte) 0);
        ForeignMemory.setMemory(aboveB, bufferBytes, (byte) 0);

        while(true) {
            long oldTagged = singletonFreeHead;
            long rawHead = oldTagged & 0x0000FFFFFFFFFFFFL;

            if(rawHead == 0L) {
                if(SINGLETON_EXPANDING_VH.compareAndSet(0, 1)) {
                    expandSingletonPool();
                    SINGLETON_EXPANDING_VH.setVolatile(0);
                }
                else {
                    Thread.onSpinWait();
                }
                continue;
            }

            long nextRawHead = ForeignMemory.getLong(rawHead);
            long nextGen = ((oldTagged >>> 48) + 1L) & 0xFFFFL;
            long newTagged = (nextGen << 48) | (nextRawHead & 0x0000FFFFFFFFFFFFL);

            if(SINGLETON_FREE_HEAD_VH.compareAndSet(oldTagged, newTagged)) {
                long base = rawHead - 8L;
                ForeignMemory.putInt(base, TYPE_SINGLETON);
                ForeignMemory.putInt(base + 4L, 1);
                
                // Populate descriptors
                ForeignMemory.putInt(rawHead + OFFSET_CAPACITY, capacityInSamples);
                ForeignMemory.putLong(rawHead + OFFSET_BELOW_READ, belowA);
                ForeignMemory.putLong(rawHead + OFFSET_BELOW_WRITE, belowB);
                
                ForeignMemory.putLong(rawHead + OFFSET_ACTIVE_READ, activeA);
                ForeignMemory.putLong(rawHead + OFFSET_ACTIVE_WRITE, activeB);
                
                ForeignMemory.putLong(rawHead + OFFSET_ABOVE_READ, aboveA);
                ForeignMemory.putLong(rawHead + OFFSET_ABOVE_WRITE, aboveB);
                
                return rawHead;
            }
        }
    }

    public static void free(long pointer)
    {
        checkActive();
        if(pointer == 0L) return;

        long base = pointer - 8L;
        int type = ForeignMemory.getInt(base);
        if(type != TYPE_SINGLETON) {
            throw new IllegalStateException("Invalid AudioBufferLayer pointer: 0x" + Long.toHexString(pointer).toUpperCase());
        }

        // Reset header
        ForeignMemory.putInt(base, 0);
        ForeignMemory.putInt(base + 4L, -1);

        while(true) {
            long oldTagged = singletonFreeHead;
            long oldRawHead = oldTagged & 0x0000FFFFFFFFFFFFL;

            ForeignMemory.putLong(pointer, oldRawHead);

            long nextGen = ((oldTagged >>> 48) + 1L) & 0xFFFFL;
            long newTagged = (nextGen << 48) | (pointer & 0x0000FFFFFFFFFFFFL);

            if(SINGLETON_FREE_HEAD_VH.compareAndSet(oldTagged, newTagged)) return;
        }
    }

    // --- ATOMIC AT-PLAY SWAP OPERATIONS ---

    @Volatile
    public static void swapBelow(long layerPtr)
    {
        long read = ForeignMemory.getLongVolatile(layerPtr + OFFSET_BELOW_READ);
        long write = ForeignMemory.getLongVolatile(layerPtr + OFFSET_BELOW_WRITE);
        
        ForeignMemory.putLongVolatile(layerPtr + OFFSET_BELOW_READ, write);
        ForeignMemory.putLongVolatile(layerPtr + OFFSET_BELOW_WRITE, read);
    }

    @Volatile
    public static void swapActive(long layerPtr)
    {
        long read = ForeignMemory.getLongVolatile(layerPtr + OFFSET_ACTIVE_READ);
        long write = ForeignMemory.getLongVolatile(layerPtr + OFFSET_ACTIVE_WRITE);
        
        ForeignMemory.putLongVolatile(layerPtr + OFFSET_ACTIVE_READ, write);
        ForeignMemory.putLongVolatile(layerPtr + OFFSET_ACTIVE_WRITE, read);
    }

    @Volatile
    public static void swapAbove(long layerPtr)
    {
        long read = ForeignMemory.getLongVolatile(layerPtr + OFFSET_ABOVE_READ);
        long write = ForeignMemory.getLongVolatile(layerPtr + OFFSET_ABOVE_WRITE);
        
        ForeignMemory.putLongVolatile(layerPtr + OFFSET_ABOVE_READ, write);
        ForeignMemory.putLongVolatile(layerPtr + OFFSET_ABOVE_WRITE, read);
    }

    // --- ACCESSORS ---

    public static int getCapacity(long layerPtr)
    {
        return ForeignMemory.getInt(layerPtr + OFFSET_CAPACITY);
    }

    @Volatile
    public static long getBelowReadPtr(long layerPtr)
    {
        return ForeignMemory.getLongVolatile(layerPtr + OFFSET_BELOW_READ);
    }

    @Volatile
    public static long getBelowWritePtr(long layerPtr)
    {
        return ForeignMemory.getLongVolatile(layerPtr + OFFSET_BELOW_WRITE);
    }

    @Volatile
    public static long getActiveReadPtr(long layerPtr)
    {
        return ForeignMemory.getLongVolatile(layerPtr + OFFSET_ACTIVE_READ);
    }

    @Volatile
    public static long getActiveWritePtr(long layerPtr)
    {
        return ForeignMemory.getLongVolatile(layerPtr + OFFSET_ACTIVE_WRITE);
    }

    @Volatile
    public static long getAboveReadPtr(long layerPtr)
    {
        return ForeignMemory.getLongVolatile(layerPtr + OFFSET_ABOVE_READ);
    }

    @Volatile
    public static long getAboveWritePtr(long layerPtr)
    {
        return ForeignMemory.getLongVolatile(layerPtr + OFFSET_ABOVE_WRITE);
    }
}
