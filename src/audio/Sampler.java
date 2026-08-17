package audio;

import annotation.Draft;
import annotation.Intention;
import annotation.Required;
import annotation.Unsafe;
import annotation.Volatile;
import bit.Bit64;
import nio.ForeignMemory;
import oop.TypeRegister;

/**
 * Metadata entity representing a single track sampler or voice layer.
 * The engine pointer's data slot stores a pointer to an off-heap struct holding
 * the sampler properties (id, type, flags, volume, pitch, playhead, layer, filter).
 */
@Draft
@Intention("Off-heap Sampler ECS metadata entity supporting safe, volatile, and unsafe variants")
public final class Sampler
{
    @Required
    public static final int CLASS_ID = TypeRegister.ID_SAMPLER;
    public static final int TYPE_SINGLETON = TypeRegister.SAMPLER_SINGLETON;

    // Offsets within the sampler payload
    public static final long OFFSET_ID = 0L;             // Int32 (4 bytes)
    public static final long OFFSET_TYPE = 4L;           // Int32 (4 bytes)
    public static final long OFFSET_FLAGS = 8L;          // Int32 (4 bytes)
    public static final long OFFSET_VOLUME = 12L;        // Float32 (4 bytes)
    public static final long OFFSET_PITCH = 16L;         // Float32 (4 bytes)
    public static final long OFFSET_PADDING = 20L;       // Int32 Padding (4 bytes)
    public static final long OFFSET_PLAYHEAD = 24L;      // Float64 (8 bytes)
    public static final long OFFSET_BUFFER_LAYER = 32L;  // Address/Pointer (8 bytes)
    public static final long OFFSET_FILTER_STATE = 40L;  // Address/Pointer (8 bytes)

    private static final long STRUCT_SIZE = 48L;

    // Sampler Types
    public static final int TYPE_INSTRUMENT = 1;
    public static final int TYPE_VOCAL = 2;
    public static final int TYPE_OTHER = 3;

    // State flags
    public static final int FLAG_ACTIVE = 1;
    public static final int FLAG_MUTED = 2;
    public static final int FLAG_SOLO = 4;

    private Sampler() {}

    public static void freeAll()
    {
        // Bit64.freeAll() manages the shared singleton slot arena.
    }

    private static long struct(long ptr) {
        return ForeignMemory.getLong(ptr);
    }

    public static long allocate(int id, int samplerType)
    {
        long enginePtr = Bit64.allocateSingleton(TYPE_SINGLETON);
        long struct = ForeignMemory.allocateNative(STRUCT_SIZE);
        ForeignMemory.setLong(enginePtr, struct);

        // Initialize default audio fields
        ForeignMemory.setInt(struct + OFFSET_ID, id);
        ForeignMemory.setInt(struct + OFFSET_TYPE, samplerType);
        ForeignMemory.setInt(struct + OFFSET_FLAGS, FLAG_ACTIVE);
        ForeignMemory.setFloat(struct + OFFSET_VOLUME, 1.0f);
        ForeignMemory.setFloat(struct + OFFSET_PITCH, 1.0f);
        ForeignMemory.setInt(struct + OFFSET_PADDING, 0);
        ForeignMemory.setDouble(struct + OFFSET_PLAYHEAD, 0.0);
        ForeignMemory.setLong(struct + OFFSET_BUFFER_LAYER, 0L);
        ForeignMemory.setLong(struct + OFFSET_FILTER_STATE, 0L);

        return enginePtr;
    }

    public static void free(long pointer)
    {
        if(pointer == 0L) return;

        long base = pointer - 8L;
        int type = ForeignMemory.getInt(base);
        if(type != TYPE_SINGLETON) {
            throw new IllegalStateException("Invalid Sampler pointer: 0x" + Long.toHexString(pointer).toUpperCase());
        }

        long struct = ForeignMemory.getLong(pointer);
        if(struct != 0L) {
            ForeignMemory.freeNative(struct);
        }
        Bit64.free(pointer);
    }

    // --- VOLATILE SAFE ACCESSORS ---

    @Volatile
    public static int getVolatileFlags(long samplerPtr)
    {
        return ForeignMemory.getVolatileInt(struct(samplerPtr) + OFFSET_FLAGS);
    }

    @Volatile
    public static void setVolatileFlags(long samplerPtr, int flags)
    {
        ForeignMemory.setVolatileInt(struct(samplerPtr) + OFFSET_FLAGS, flags);
    }

    @Volatile
    public static float getVolatileVolume(long samplerPtr)
    {
        return ForeignMemory.getVolatileFloat(struct(samplerPtr) + OFFSET_VOLUME);
    }

    @Volatile
    public static void setVolatileVolume(long samplerPtr, float volume)
    {
        ForeignMemory.setVolatileFloat(struct(samplerPtr) + OFFSET_VOLUME, volume);
    }

    @Volatile
    public static float getVolatilePitch(long samplerPtr)
    {
        return ForeignMemory.getVolatileFloat(struct(samplerPtr) + OFFSET_PITCH);
    }

    @Volatile
    public static void setVolatilePitch(long samplerPtr, float pitch)
    {
        ForeignMemory.setVolatileFloat(struct(samplerPtr) + OFFSET_PITCH, pitch);
    }

    @Volatile
    public static double getVolatilePlayhead(long samplerPtr)
    {
        return ForeignMemory.getVolatileDouble(struct(samplerPtr) + OFFSET_PLAYHEAD);
    }

    @Volatile
    public static void setVolatilePlayhead(long samplerPtr, double playhead)
    {
        ForeignMemory.setVolatileDouble(struct(samplerPtr) + OFFSET_PLAYHEAD, playhead);
    }

    @Volatile
    public static long getVolatileBufferLayer(long samplerPtr)
    {
        return ForeignMemory.getVolatileLong(struct(samplerPtr) + OFFSET_BUFFER_LAYER);
    }

    @Volatile
    public static void setVolatileBufferLayer(long samplerPtr, long layerPtr)
    {
        ForeignMemory.setVolatileLong(struct(samplerPtr) + OFFSET_BUFFER_LAYER, layerPtr);
    }

    @Volatile
    public static long getVolatileFilterState(long samplerPtr)
    {
        return ForeignMemory.getVolatileLong(struct(samplerPtr) + OFFSET_FILTER_STATE);
    }

    @Volatile
    public static void setVolatileFilterState(long samplerPtr, long filterPtr)
    {
        ForeignMemory.setVolatileLong(struct(samplerPtr) + OFFSET_FILTER_STATE, filterPtr);
    }

    // --- UNSAFE RAW ACCESSORS (Bypasses checks for extreme speed) ---

    @Unsafe
    public static float getUnsafeVolume(long samplerPtr)
    {
        return ForeignMemory.getFloat(struct(samplerPtr) + OFFSET_VOLUME);
    }

    @Unsafe
    public static void setUnsafeVolume(long samplerPtr, float volume)
    {
        ForeignMemory.setFloat(struct(samplerPtr) + OFFSET_VOLUME, volume);
    }

    @Unsafe
    public static float getUnsafePitch(long samplerPtr)
    {
        return ForeignMemory.getFloat(struct(samplerPtr) + OFFSET_PITCH);
    }

    @Unsafe
    public static void setUnsafePitch(long samplerPtr, float pitch)
    {
        ForeignMemory.setFloat(struct(samplerPtr) + OFFSET_PITCH, pitch);
    }

    @Unsafe
    public static double getUnsafePlayhead(long samplerPtr)
    {
        return ForeignMemory.getDouble(struct(samplerPtr) + OFFSET_PLAYHEAD);
    }

    @Unsafe
    public static void setUnsafePlayhead(long samplerPtr, double playhead)
    {
        ForeignMemory.setDouble(struct(samplerPtr) + OFFSET_PLAYHEAD, playhead);
    }
}