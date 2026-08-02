package audio;

import annotation.Draft;
import nio.ForeignMemory;
import org.lwjgl.openal.AL;
import org.lwjgl.openal.AL10;
import org.lwjgl.openal.ALC;
import org.lwjgl.openal.ALC10;
import org.lwjgl.openal.ALCCapabilities;
import org.lwjgl.openal.ALCapabilities;

import java.nio.ByteBuffer;
import java.nio.IntBuffer;

import static org.lwjgl.openal.AL10.*;
import static org.lwjgl.openal.ALC10.*;

/**
 * Centrally managed, off-heap OpenAL Audio System conforming to the Anti Philosophy.
 * Exposes zero-GC static downcalls to play, pause, stop, and configure pooled sound sources.
 */
@Draft
public final class AudioSystem
{
    private static long deviceHandle = 0L;
    private static long contextHandle = 0L;
    private static volatile boolean initialized = false;

    private AudioSystem() {}

    /**
     * Initializes the OpenAL device and context.
     */
    public static synchronized void init()
    {
        if (initialized) return;

        // 1. Open Default Audio Device
        deviceHandle = alcOpenDevice((ByteBuffer) null);
        if (deviceHandle == 0L)
        {
            throw new RuntimeException("Failed to open default OpenAL device.");
        }

        // 2. Create Context
        contextHandle = alcCreateContext(deviceHandle, (IntBuffer) null);
        if (contextHandle == 0L)
        {
            alcCloseDevice(deviceHandle);
            throw new RuntimeException("Failed to create OpenAL context.");
        }

        // 3. Make Context Current
        if (!alcMakeContextCurrent(contextHandle))
        {
            alcDestroyContext(contextHandle);
            alcCloseDevice(deviceHandle);
            throw new RuntimeException("Failed to make OpenAL context current.");
        }

        // 4. Initialize LWJGL AL Capabilities
        ALCCapabilities alcCapabilities = ALC.createCapabilities(deviceHandle);
        ALCapabilities alCapabilities = AL.createCapabilities(alcCapabilities);

        initialized = true;
        System.out.println("[AudioSystem] OpenAL Subsystem initialized successfully.");
    }

    /**
     * Shuts down the OpenAL context and closes the audio device.
     */
    public static synchronized void shutdown()
    {
        if (!initialized) return;

        alcMakeContextCurrent(0L);
        if (contextHandle != 0L)
        {
            alcDestroyContext(contextHandle);
            contextHandle = 0L;
        }
        if (deviceHandle != 0L)
        {
            alcCloseDevice(deviceHandle);
            deviceHandle = 0L;
        }

        initialized = false;
        System.out.println("[AudioSystem] OpenAL Subsystem terminated.");
    }

    /**
     * Allocates a pooled source pointer and generates a native OpenAL source.
     */
    public static long allocateSource()
    {
        if (!initialized) init();

        long sourcePtr = AudioSource.allocate();
        int alSourceId = alGenSources();
        
        // Store AL Source ID at offset +0
        ForeignMemory.putInt(sourcePtr, alSourceId);

        return sourcePtr;
    }

    /**
     * Deletes the OpenAL source and returns the slot to the pool.
     */
    public static void freeSource(long sourcePtr)
    {
        if (sourcePtr == 0L) return;

        int alSourceId = ForeignMemory.getInt(sourcePtr);
        if (alSourceId != 0)
        {
            alDeleteSources(alSourceId);
        }

        AudioSource.free(sourcePtr);
    }

    /**
     * Allocates a pooled buffer pointer and generates a native OpenAL buffer.
     */
    public static long allocateBuffer()
    {
        if (!initialized) init();

        long bufferPtr = AudioBuffer.allocate();
        int alBufferId = alGenBuffers();
        
        // Store AL Buffer ID at offset +0
        ForeignMemory.putInt(bufferPtr, alBufferId);

        return bufferPtr;
    }

    /**
     * Deletes the OpenAL buffer and returns the slot to the pool.
     */
    public static void freeBuffer(long bufferPtr)
    {
        if (bufferPtr == 0L) return;

        int alBufferId = ForeignMemory.getInt(bufferPtr);
        if (alBufferId != 0)
        {
            alDeleteBuffers(alBufferId);
        }

        AudioBuffer.free(bufferPtr);
    }

    /**
     * Binds an AudioBuffer to an AudioSource.
     */
    public static void setSourceBuffer(long sourcePtr, long bufferPtr)
    {
        if (sourcePtr == 0L) return;

        int alSourceId = ForeignMemory.getInt(sourcePtr);
        int alBufferId = bufferPtr != 0L ? ForeignMemory.getInt(bufferPtr) : 0;

        alSourcei(alSourceId, AL_BUFFER, alBufferId);
        
        // Update off-heap fields
        ForeignMemory.putInt(sourcePtr + 4L, alBufferId);
    }

    /**
     * Configures source pitch.
     */
    public static void setPitch(long sourcePtr, float pitch)
    {
        if (sourcePtr == 0L) return;

        int alSourceId = ForeignMemory.getInt(sourcePtr);
        alSourcef(alSourceId, AL_PITCH, pitch);
        
        // Update off-heap fields
        ForeignMemory.putFloat(sourcePtr + 8L, pitch);
    }

    /**
     * Configures source gain (volume).
     */
    public static void setGain(long sourcePtr, float gain)
    {
        if (sourcePtr == 0L) return;

        int alSourceId = ForeignMemory.getInt(sourcePtr);
        alSourcef(alSourceId, AL_GAIN, gain);
        
        // Update off-heap fields
        ForeignMemory.putFloat(sourcePtr + 12L, gain);
    }

    /**
     * Configures source looping.
     */
    public static void setLooping(long sourcePtr, boolean looping)
    {
        if (sourcePtr == 0L) return;

        int alSourceId = ForeignMemory.getInt(sourcePtr);
        alSourcei(alSourceId, AL_LOOPING, looping ? AL_TRUE : AL_FALSE);
        
        // Update off-heap fields
        ForeignMemory.putInt(sourcePtr + 16L, looping ? 1 : 0);
    }

    /**
     * Starts audio playback on the source.
     */
    public static void play(long sourcePtr)
    {
        if (sourcePtr == 0L) return;
        int alSourceId = ForeignMemory.getInt(sourcePtr);
        alSourcePlay(alSourceId);
    }

    /**
     * Pauses audio playback on the source.
     */
    public static void pause(long sourcePtr)
    {
        if (sourcePtr == 0L) return;
        int alSourceId = ForeignMemory.getInt(sourcePtr);
        alSourcePause(alSourceId);
    }

    /**
     * Stops audio playback on the source.
     */
    public static void stop(long sourcePtr)
    {
        if (sourcePtr == 0L) return;
        int alSourceId = ForeignMemory.getInt(sourcePtr);
        alSourceStop(alSourceId);
    }

    /**
     * Checks if the source is currently playing.
     */
    public static boolean isPlaying(long sourcePtr)
    {
        if (sourcePtr == 0L) return false;
        int alSourceId = ForeignMemory.getInt(sourcePtr);
        return alGetSourcei(alSourceId, AL_SOURCE_STATE) == AL_PLAYING;
    }

    /**
     * Returns the internal OpenAL buffer ID for a buffer pointer.
     */
    public static int getBufferAlId(long bufferPtr)
    {
        if (bufferPtr == 0L) return 0;
        return ForeignMemory.getInt(bufferPtr);
    }
}
