package engine;

/**
 * Architectural Engine Constraints and Resource Limits.
 * Defines static final defaults based on an 8 GB system memory baseline,
 * with static getter/setter methods for runtime configuration and hardware auto-scaling.
 */
public final class Constraints
{
    // =========================================================================================
    // DEFAULT STATIC FINAL CONSTRAINTS (8 GB SYSTEM MEMORY BASELINE)
    // =========================================================================================
    public static final int DEFAULT_MAX_ENTITIES              = 1_000_000;        // 1 Million Entities
    public static final int DEFAULT_MAX_TRIANGLES             = 100_000_000;      // 100 Million Triangles
    public static final int DEFAULT_MAX_TEXTURES              = 16_384;           // 16,384 Textures
    public static final int DEFAULT_MAX_SHADERS               = 4_096;            // 4,096 Shader Pipelines
    public static final int DEFAULT_MAX_LIGHTS                = 65_536;           // 65,536 Dynamic Lights
    public static final int DEFAULT_MAX_AUDIO_SOURCES         = 4_096;            // 4,096 Audio Sources
    public static final int DEFAULT_MAX_DRAW_CALLS_PER_FRAME  = 262_144;          // 262,144 Draw Calls per Frame
    public static final long DEFAULT_MAX_OFFHEAP_MEMORY_BYTES = 8L * 1024L * 1024L * 1024L; // 8 GB Off-Heap RAM
    public static final int DEFAULT_MAX_THREADS               = 64;               // 64 Concurrent Worker Threads

    // =========================================================================================
    // DYNAMIC RUNTIME CONSTRAINTS
    // =========================================================================================
    private static int maxEntities              = DEFAULT_MAX_ENTITIES;
    private static int maxTriangles             = DEFAULT_MAX_TRIANGLES;
    private static int maxTextures              = DEFAULT_MAX_TEXTURES;
    private static int maxShaders               = DEFAULT_MAX_SHADERS;
    private static int maxLights                = DEFAULT_MAX_LIGHTS;
    private static int maxAudioSources         = DEFAULT_MAX_AUDIO_SOURCES;
    private static int maxDrawCallsPerFrame  = DEFAULT_MAX_DRAW_CALLS_PER_FRAME;
    private static long maxOffHeapMemoryBytes = DEFAULT_MAX_OFFHEAP_MEMORY_BYTES;
    private static int maxThreads               = DEFAULT_MAX_THREADS;

    private Constraints() {}

    // =========================================================================================
    // STATIC GETTERS & SETTERS
    // =========================================================================================

    public static int getMaxEntities()
    {
        return maxEntities;
    }

    public static void setMaxEntities(int count)
    {
        if (count > 0) maxEntities = count;
    }

    public static int getMaxTriangles()
    {
        return maxTriangles;
    }

    public static void setMaxTriangles(int count)
    {
        if (count > 0) maxTriangles = count;
    }

    public static int getMaxTextures()
    {
        return maxTextures;
    }

    public static void setMaxTextures(int count)
    {
        if (count > 0) maxTextures = count;
    }

    public static int getMaxShaders()
    {
        return maxShaders;
    }

    public static void setMaxShaders(int count)
    {
        if (count > 0) maxShaders = count;
    }

    public static int getMaxLights()
    {
        return maxLights;
    }

    public static void setMaxLights(int count)
    {
        if (count > 0) maxLights = count;
    }

    public static int getMaxAudioSources()
    {
        return maxAudioSources;
    }

    public static void setMaxAudioSources(int count)
    {
        if (count > 0) maxAudioSources = count;
    }

    public static int getMaxDrawCallsPerFrame()
    {
        return maxDrawCallsPerFrame;
    }

    public static void setMaxDrawCallsPerFrame(int count)
    {
        if (count > 0) maxDrawCallsPerFrame = count;
    }

    public static long getMaxOffHeapMemoryBytes()
    {
        return maxOffHeapMemoryBytes;
    }

    public static void setMaxOffHeapMemoryBytes(long bytes)
    {
        if (bytes > 0L) maxOffHeapMemoryBytes = bytes;
    }

    public static int getMaxThreads()
    {
        return maxThreads;
    }

    public static void setMaxThreads(int count)
    {
        if (count > 0) maxThreads = count;
    }

    /**
     * Dynamically scales all engine constraints based on detected physical system RAM.
     * @param ramBytes Total physical memory in bytes.
     */
    public static void scaleToSystemMemory(long ramBytes)
    {
        double factor = (double) ramBytes / (double) DEFAULT_MAX_OFFHEAP_MEMORY_BYTES;
        if (factor < 0.25) factor = 0.25; // Floor at 2 GB scale

        maxOffHeapMemoryBytes = ramBytes;
        maxEntities           = (int) (DEFAULT_MAX_ENTITIES * factor);
        maxTriangles          = (int) (DEFAULT_MAX_TRIANGLES * factor);
        maxTextures           = (int) (DEFAULT_MAX_TEXTURES * factor);
        maxShaders            = (int) (DEFAULT_MAX_SHADERS * factor);
        maxLights             = (int) (DEFAULT_MAX_LIGHTS * factor);
        maxAudioSources        = (int) (DEFAULT_MAX_AUDIO_SOURCES * factor);
        maxDrawCallsPerFrame = (int) (DEFAULT_MAX_DRAW_CALLS_PER_FRAME * factor);
    }

    /**
     * Resets all runtime constraints back to the 8 GB default baseline.
     */
    public static void resetToDefaults()
    {
        maxEntities              = DEFAULT_MAX_ENTITIES;
        maxTriangles             = DEFAULT_MAX_TRIANGLES;
        maxTextures              = DEFAULT_MAX_TEXTURES;
        maxShaders               = DEFAULT_MAX_SHADERS;
        maxLights                = DEFAULT_MAX_LIGHTS;
        maxAudioSources         = DEFAULT_MAX_AUDIO_SOURCES;
        maxDrawCallsPerFrame  = DEFAULT_MAX_DRAW_CALLS_PER_FRAME;
        maxOffHeapMemoryBytes = DEFAULT_MAX_OFFHEAP_MEMORY_BYTES;
        maxThreads               = DEFAULT_MAX_THREADS;
    }

    public static String getSummary()
    {
        return "Engine Constraints Summary:\n" +
            "  - Max Entities:            " + maxEntities + "\n" +
            "  - Max Triangles:           " + maxTriangles + "\n" +
            "  - Max Textures:            " + maxTextures + "\n" +
            "  - Max Shaders:             " + maxShaders + "\n" +
            "  - Max Lights:              " + maxLights + "\n" +
            "  - Max Audio Sources:       " + maxAudioSources + "\n" +
            "  - Max Draw Calls / Frame:  " + maxDrawCallsPerFrame + "\n" +
            "  - Max Off-Heap RAM:        " + (maxOffHeapMemoryBytes / (1024 * 1024)) + " MB\n" +
            "  - Max Worker Threads:      " + maxThreads;
    }
}
