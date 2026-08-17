package audio;

import annotation.Draft;
import bit.Bit64;
import nio.ForeignMemory;
import oop.TypeRegister;

/**
 * Off-heap AudioSource component manager conforming to the Anti Architecture.
 * The engine pointer's data slot stores a pointer to an off-heap struct holding
 * the Source properties (AL Source ID, AL Buffer ID, Pitch, Gain, Looping).
 */
@Draft
public final class AudioSource
{
    public static final int CLASS_ID = TypeRegister.ID_AUDIO_SOURCE;
    public static final int TYPE_SINGLETON = TypeRegister.AUDIO_SOURCE_SINGLETON;

    private static final long STRUCT_SIZE = 20L; // AL Source ID(4) + AL Buffer ID(4) + Pitch(4) + Gain(4) + Looping(4)

    private AudioSource() {}

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
        ForeignMemory.setInt(struct, 0);         // AL Source ID
        ForeignMemory.setInt(struct + 4L, 0);    // AL Buffer ID
        ForeignMemory.setFloat(struct + 8L, 1.0f); // Pitch
        ForeignMemory.setFloat(struct + 12L, 1.0f); // Gain
        ForeignMemory.setInt(struct + 16L, 0);   // Looping (0 = false)

        return enginePtr;
    }

    public static void free(long pointer)
    {
        if(pointer == 0L) return;

        long base = pointer - 8L;
        int type = ForeignMemory.getInt(base);
        if(type != TYPE_SINGLETON) {
            throw new IllegalStateException("Invalid AudioSource pointer: 0x" + Long.toHexString(pointer).toUpperCase());
        }

        long struct = ForeignMemory.getLong(pointer);
        if(struct != 0L) {
            ForeignMemory.freeNative(struct);
        }
        Bit64.free(pointer);
    }
}
