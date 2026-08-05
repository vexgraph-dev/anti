package audio;

import annotation.Draft;
import annotation.Intention;
import annotation.Required;
import annotation.Unsafe;
import annotation.Volatile;
import nio.ForeignMemory;
import oop.TypeRegister;

import java.lang.foreign.Arena;
import java.lang.invoke.MethodHandles;
import java.lang.invoke.VarHandle;

/**
 * Metadata entity representing a single track sampler or voice layer.
 * Manages properties off-heap in a cache-aligned contiguous memory slot.
 */
@Draft
@Intention("Off-heap Sampler ECS metadata entity supporting safe, volatile, and unsafe variants")
public final class Sampler
{
    @Required
    public static final int CLASS_ID = TypeRegister.ID_SAMPLER;
    public static final int TYPE_SINGLETON = TypeRegister.SAMPLER_SINGLETON;

    private static final int DEFAULT_CAPACITY = 256;
    private static final long SINGLETON_SLOT_SIZE = 64L; // Header (8B) + Stride Data (56B)

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

    // Sampler Types
    public static final int TYPE_INSTRUMENT = 1;
    public static final int TYPE_VOCAL = 2;
    public static final int TYPE_OTHER = 3;

    // State flags
    public static final int FLAG_ACTIVE = 1;
    public static final int FLAG_MUTED = 2;
    public static final int FLAG_SOLO = 4;

    private static final VarHandle SINGLETON_FREE_HEAD_VH;
    private static final VarHandle SINGLETON_EXPANDING_VH;
    private static volatile int singletonExpanding = 0;

    private static Arena poolArena;
    private static volatile boolean active;
    private static volatile long singletonFreeHead;

    static {
        try {
            MethodHandles.Lookup lookup = MethodHandles.lookup();
            SINGLETON_FREE_HEAD_VH = lookup.findStaticVarHandle(Sampler.class, "singletonFreeHead", long.class);
            SINGLETON_EXPANDING_VH = lookup.findStaticVarHandle(Sampler.class, "singletonExpanding", int.class);
        }
        catch(ReflectiveOperationException e) {
            throw new ExceptionInInitializerError(e);
        }

        poolArena = Arena.ofShared();
        active = true;

        expandSingletonPool();
    }

    private Sampler() {}

    private static void checkActive()
    {
        if(!active) throw new IllegalStateException("Sampler subsystem is not active!");
    }

    public static void freeAll()
    {
        if(active) {
            active = false;
            if(poolArena != null && poolArena.scope().isAlive()) {
                poolArena.close();
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

                ForeignMemory.setLong(userPtr, oldRawHead);

                long nextGen = ((oldTagged >>> 48) + 1L) & 0xFFFFL;
                long newTagged = (nextGen << 48) | (userPtr & 0x0000FFFFFFFFFFFFL);

                if(SINGLETON_FREE_HEAD_VH.compareAndSet(oldTagged, newTagged)) break;
            }
        }
    }

    public static long allocate(int id, int samplerType)
    {
        checkActive();
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
                ForeignMemory.setInt(base, TYPE_SINGLETON);
                ForeignMemory.setInt(base + 4L, 1);
                
                // Initialize default audio fields
                ForeignMemory.setInt(rawHead + OFFSET_ID, id);
                ForeignMemory.setInt(rawHead + OFFSET_TYPE, samplerType);
                ForeignMemory.setInt(rawHead + OFFSET_FLAGS, FLAG_ACTIVE);
                ForeignMemory.setFloat(rawHead + OFFSET_VOLUME, 1.0f);
                ForeignMemory.setFloat(rawHead + OFFSET_PITCH, 1.0f);
                ForeignMemory.setInt(rawHead + OFFSET_PADDING, 0);
                ForeignMemory.setDouble(rawHead + OFFSET_PLAYHEAD, 0.0);
                ForeignMemory.setLong(rawHead + OFFSET_BUFFER_LAYER, 0L);
                ForeignMemory.setLong(rawHead + OFFSET_FILTER_STATE, 0L);
                
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
            throw new IllegalStateException("Invalid Sampler pointer: 0x" + Long.toHexString(pointer).toUpperCase());
        }

        // Reset header
        ForeignMemory.setInt(base, 0);
        ForeignMemory.setInt(base + 4L, -1);

        while(true) {
            long oldTagged = singletonFreeHead;
            long oldRawHead = oldTagged & 0x0000FFFFFFFFFFFFL;

            ForeignMemory.setLong(pointer, oldRawHead);

            long nextGen = ((oldTagged >>> 48) + 1L) & 0xFFFFL;
            long newTagged = (nextGen << 48) | (pointer & 0x0000FFFFFFFFFFFFL);

            if(SINGLETON_FREE_HEAD_VH.compareAndSet(oldTagged, newTagged)) return;
        }
    }

    // --- VOLATILE SAFE ACCESSORS ---

    @Volatile
    public static int getVolatileFlags(long samplerPtr)
    {
        return ForeignMemory.getVolatileInt(samplerPtr + OFFSET_FLAGS);
    }

    @Volatile
    public static void setVolatileFlags(long samplerPtr, int flags)
    {
        ForeignMemory.setVolatileInt(samplerPtr + OFFSET_FLAGS, flags);
    }

    @Volatile
    public static float getVolatileVolume(long samplerPtr)
    {
        return ForeignMemory.getVolatileFloat(samplerPtr + OFFSET_VOLUME);
    }

    @Volatile
    public static void setVolatileVolume(long samplerPtr, float volume)
    {
        ForeignMemory.setVolatileFloat(samplerPtr + OFFSET_VOLUME, volume);
    }

    @Volatile
    public static float getVolatilePitch(long samplerPtr)
    {
        return ForeignMemory.getVolatileFloat(samplerPtr + OFFSET_PITCH);
    }

    @Volatile
    public static void setVolatilePitch(long samplerPtr, float pitch)
    {
        ForeignMemory.setVolatileFloat(samplerPtr + OFFSET_PITCH, pitch);
    }

    @Volatile
    public static double getVolatilePlayhead(long samplerPtr)
    {
        return ForeignMemory.getVolatileDouble(samplerPtr + OFFSET_PLAYHEAD);
    }

    @Volatile
    public static void setVolatilePlayhead(long samplerPtr, double playhead)
    {
        ForeignMemory.setVolatileDouble(samplerPtr + OFFSET_PLAYHEAD, playhead);
    }

    @Volatile
    public static long getVolatileBufferLayer(long samplerPtr)
    {
        return ForeignMemory.getVolatileLong(samplerPtr + OFFSET_BUFFER_LAYER);
    }

    @Volatile
    public static void setVolatileBufferLayer(long samplerPtr, long layerPtr)
    {
        ForeignMemory.setVolatileLong(samplerPtr + OFFSET_BUFFER_LAYER, layerPtr);
    }

    @Volatile
    public static long getVolatileFilterState(long samplerPtr)
    {
        return ForeignMemory.getVolatileLong(samplerPtr + OFFSET_FILTER_STATE);
    }

    @Volatile
    public static void setVolatileFilterState(long samplerPtr, long filterPtr)
    {
        ForeignMemory.setVolatileLong(samplerPtr + OFFSET_FILTER_STATE, filterPtr);
    }

    // --- UNSAFE RAW ACCESSORS (Bypasses checks for extreme speed) ---

    @Unsafe
    public static float getUnsafeVolume(long samplerPtr)
    {
        return ForeignMemory.getFloat(samplerPtr + OFFSET_VOLUME);
    }

    @Unsafe
    public static void setUnsafeVolume(long samplerPtr, float volume)
    {
        ForeignMemory.setFloat(samplerPtr + OFFSET_VOLUME, volume);
    }

    @Unsafe
    public static float getUnsafePitch(long samplerPtr)
    {
        return ForeignMemory.getFloat(samplerPtr + OFFSET_PITCH);
    }

    @Unsafe
    public static void setUnsafePitch(long samplerPtr, float pitch)
    {
        ForeignMemory.setFloat(samplerPtr + OFFSET_PITCH, pitch);
    }

    @Unsafe
    public static double getUnsafePlayhead(long samplerPtr)
    {
        return ForeignMemory.getDouble(samplerPtr + OFFSET_PLAYHEAD);
    }

    @Unsafe
    public static void setUnsafePlayhead(long samplerPtr, double playhead)
    {
        ForeignMemory.setDouble(samplerPtr + OFFSET_PLAYHEAD, playhead);
    }
}
