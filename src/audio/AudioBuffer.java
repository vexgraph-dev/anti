package audio;

import annotation.Draft;
import annotation.Unsafe;
import annotation.Volatile;
import bit.Bit64;
import nio.ForeignMemory;
import oop.TypeRegister;

import nio.StringLookup;
/**
 * Off-heap AudioBuffer component manager conforming to the Anti Architecture.
 * The engine pointer's data slot stores a pointer to an off-heap struct holding
 * the Buffer properties (AL Buffer ID, Sample Rate, Format, Size).
 */
@Draft
public final class AudioBuffer
{
    public static final int CLASS_ID = TypeRegister.ID_AUDIO_BUFFER;
    public static final int TYPE_SINGLETON = TypeRegister.AUDIO_BUFFER_SINGLETON;

    private static final long STRUCT_SIZE = 16L; // AL Buffer ID(4) + Sample Rate(4) + Format(4) + Size(4)

    private AudioBuffer() {}

    public static void freeAll()
    {
        // Bit64.freeAll() manages the shared singleton slot arena.
    }

    public static long allocate()
    {
        long enginePtr = Bit64.allocateSingleton(TYPE_SINGLETON);
        long struct = ForeignMemory.allocateNative(STRUCT_SIZE);
        ForeignMemory.setLong(enginePtr, struct);

        // Initialize default audio fields
        ForeignMemory.setInt(struct, 0);        // AL Buffer ID
        ForeignMemory.setInt(struct + 4L, 0);   // Sample Rate
        ForeignMemory.setInt(struct + 8L, 0);   // Format
        ForeignMemory.setInt(struct + 12L, 0);  // Size

        return enginePtr;
    }

    public static void free(long pointer)
    {
        if(pointer == 0L) return;

        long base = pointer - 8L;
        int type = ForeignMemory.getInt(base);
        if(type != TYPE_SINGLETON) {
            throw new IllegalStateException(StringLookup.getJavaString(583) + Long.toHexString(pointer).toUpperCase());
        }

        long struct = ForeignMemory.getLong(pointer);
        if(struct != 0L) {
            ForeignMemory.freeNative(struct);
        }
        Bit64.free(pointer);
    }
}
