package audio;

import annotation.Draft;
import annotation.Intention;
import annotation.Required;
import annotation.Volatile;
import bit.Bit64;
import nio.ForeignMemory;
import oop.TypeRegister;

import java.lang.foreign.Arena;

import nio.StringLookup;
/**
 * Off-heap manager for the triple-buffered Audio Layer Swapchain.
 * Manages three distinct buses (Below-Active, Active, Above-Active) to support
 * Photoshop-style audio layer mixing with zero runtime heap allocation.
 *
 * The engine pointer's data slot stores a pointer to an off-heap struct holding
 * the layer descriptors (capacity + six PCM bus pointers). The actual PCM sample
 * layers live in a separate bufferArena closed by freeAll().
 */
@Draft
@Intention("Triple-buffered off-heap audio layer swapchain for high-performance canvas mixing")
public final class AudioBufferLayer
{
    @Required
    public static final int CLASS_ID = TypeRegister.ID_AUDIO_BUFFER_LAYER;
    public static final int TYPE_SINGLETON = TypeRegister.AUDIO_BUFFER_LAYER_SINGLETON;

    // Struct field offsets
    public static final long OFFSET_CAPACITY = 0L;             // Int32
    public static final long OFFSET_PADDING = 4L;              // Padding (4B)
    public static final long OFFSET_BELOW_READ = 8L;           // Address (8B)
    public static final long OFFSET_BELOW_WRITE = 16L;         // Address (8B)
    public static final long OFFSET_ACTIVE_READ = 24L;         // Address (8B)
    public static final long OFFSET_ACTIVE_WRITE = 32L;        // Address (8B)
    public static final long OFFSET_ABOVE_READ = 40L;          // Address (8B)
    public static final long OFFSET_ABOVE_WRITE = 48L;         // Address (8B)

    private static final long STRUCT_SIZE = 56L;

    private static Arena bufferArena; // Arena to allocate the actual PCM sample layers

    static {
        bufferArena = Arena.ofShared();
    }

    private AudioBufferLayer() {}

    public static void freeAll()
    {
        // Bit64.freeAll() manages the shared singleton slot arena.
        if(bufferArena != null && bufferArena.scope().isAlive()) {
            bufferArena.close();
        }
    }

    private static long struct(long ptr) {
        return ForeignMemory.getLong(ptr);
    }

    /**
     * Allocates an AudioBufferLayer instance and its associated double-buffered PCM float arrays.
     * @param capacityInSamples The size of each buffer in samples (e.g. 512 samples).
     */
    public static long allocate(int capacityInSamples)
    {
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

        long enginePtr = Bit64.allocateSingleton(TYPE_SINGLETON);
        long struct = ForeignMemory.allocateNative(STRUCT_SIZE);
        ForeignMemory.setLong(enginePtr, struct);

        // Populate descriptors
        ForeignMemory.setInt(struct + OFFSET_CAPACITY, capacityInSamples);
        ForeignMemory.setLong(struct + OFFSET_BELOW_READ, belowA);
        ForeignMemory.setLong(struct + OFFSET_BELOW_WRITE, belowB);

        ForeignMemory.setLong(struct + OFFSET_ACTIVE_READ, activeA);
        ForeignMemory.setLong(struct + OFFSET_ACTIVE_WRITE, activeB);

        ForeignMemory.setLong(struct + OFFSET_ABOVE_READ, aboveA);
        ForeignMemory.setLong(struct + OFFSET_ABOVE_WRITE, aboveB);

        return enginePtr;
    }

    public static void free(long pointer)
    {
        if(pointer == 0L) return;

        long base = pointer - 8L;
        int type = ForeignMemory.getInt(base);
        if(type != TYPE_SINGLETON) {
            throw new IllegalStateException(StringLookup.getJavaString(585) + Long.toHexString(pointer).toUpperCase());
        }

        long struct = ForeignMemory.getLong(pointer);
        if(struct != 0L) {
            ForeignMemory.freeNative(struct);
        }
        Bit64.free(pointer);
    }

    // --- ATOMIC AT-PLAY SWAP OPERATIONS ---

    @Volatile
    public static void swapBelow(long layerPtr)
    {
        long read = ForeignMemory.getVolatileLong(struct(layerPtr) + OFFSET_BELOW_READ);
        long write = ForeignMemory.getVolatileLong(struct(layerPtr) + OFFSET_BELOW_WRITE);

        ForeignMemory.setVolatileLong(struct(layerPtr) + OFFSET_BELOW_READ, write);
        ForeignMemory.setVolatileLong(struct(layerPtr) + OFFSET_BELOW_WRITE, read);
    }

    @Volatile
    public static void swapActive(long layerPtr)
    {
        long read = ForeignMemory.getVolatileLong(struct(layerPtr) + OFFSET_ACTIVE_READ);
        long write = ForeignMemory.getVolatileLong(struct(layerPtr) + OFFSET_ACTIVE_WRITE);

        ForeignMemory.setVolatileLong(struct(layerPtr) + OFFSET_ACTIVE_READ, write);
        ForeignMemory.setVolatileLong(struct(layerPtr) + OFFSET_ACTIVE_WRITE, read);
    }

    @Volatile
    public static void swapAbove(long layerPtr)
    {
        long read = ForeignMemory.getVolatileLong(struct(layerPtr) + OFFSET_ABOVE_READ);
        long write = ForeignMemory.getVolatileLong(struct(layerPtr) + OFFSET_ABOVE_WRITE);

        ForeignMemory.setVolatileLong(struct(layerPtr) + OFFSET_ABOVE_READ, write);
        ForeignMemory.setVolatileLong(struct(layerPtr) + OFFSET_ABOVE_WRITE, read);
    }

    // --- ACCESSORS ---

    public static int getCapacity(long layerPtr)
    {
        return ForeignMemory.getInt(struct(layerPtr) + OFFSET_CAPACITY);
    }

    @Volatile
    public static long getBelowReadPtr(long layerPtr)
    {
        return ForeignMemory.getVolatileLong(struct(layerPtr) + OFFSET_BELOW_READ);
    }

    @Volatile
    public static long getBelowWritePtr(long layerPtr)
    {
        return ForeignMemory.getVolatileLong(struct(layerPtr) + OFFSET_BELOW_WRITE);
    }

    @Volatile
    public static long getActiveReadPtr(long layerPtr)
    {
        return ForeignMemory.getVolatileLong(struct(layerPtr) + OFFSET_ACTIVE_READ);
    }

    @Volatile
    public static long getActiveWritePtr(long layerPtr)
    {
        return ForeignMemory.getVolatileLong(struct(layerPtr) + OFFSET_ACTIVE_WRITE);
    }

    @Volatile
    public static long getAboveReadPtr(long layerPtr)
    {
        return ForeignMemory.getVolatileLong(struct(layerPtr) + OFFSET_ABOVE_READ);
    }

    @Volatile
    public static long getAboveWritePtr(long layerPtr)
    {
        return ForeignMemory.getVolatileLong(struct(layerPtr) + OFFSET_ABOVE_WRITE);
    }
}