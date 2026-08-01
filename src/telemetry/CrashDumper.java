package telemetry;

import annotation.Draft;
import annotation.Intention;
import nio.ForeignMemory;

import java.io.FileOutputStream;
import java.io.PrintWriter;
import java.io.StringWriter;
import java.nio.charset.StandardCharsets;
import java.util.concurrent.locks.LockSupport;

/**
 * High-performance, zero-overhead telemetry dumper.
 * Tracks off-heap engine telemetry (frame count, last pointer, etc.) using a 1KB native memory block.
 * Uses a background daemon thread that periodically flushes state to disk every 1 second.
 * Supports direct exception dumping to capture stack traces alongside telemetry.
 */
@Draft
@Intention("Asynchronous off-heap telemetry dumper that flushes status periodically using a daemon thread and captures exceptions")
public final class CrashDumper
{
    private static final String DUMP_FILE_PATH = "crash-dump.txt";

    // Pre-allocated 1KB off-heap telemetry block
    public static final long TELEMETRY_BLOCK;
    
    // Telemetry fields offsets
    public static final long OFFSET_FRAME = 0L;              // 8 bytes
    public static final long OFFSET_LAST_PTR = 8L;           // 8 bytes
    public static final long OFFSET_STATE = 16L;             // 4 bytes
    public static final long OFFSET_DISPLAY_COUNT = 20L;     // 4 bytes
    public static final long OFFSET_ACTIVE_PIPELINES = 24L;  // 4 bytes
    public static final long OFFSET_ACTIVE_THREADS = 28L;    // 4 bytes
    public static final long OFFSET_ALLOC_COUNT = 32L;       // 8 bytes

    static
    {
        TELEMETRY_BLOCK = ForeignMemory.allocateNative(1024L);
        // Zero out block
        for (long i = 0; i < 1024L; i++)
        {
            ForeignMemory.putByte(TELEMETRY_BLOCK + i, (byte)0);
        }
    }

    private CrashDumper() {}

    /**
     * Spawns the background daemon thread to flush telemetry state every 1 second.
     */
    public static void init()
    {
        Thread daemon = Thread.ofPlatform().daemon(true).name("Anti-Telemetry-Daemon").unstarted(() -> {
            while (true)
            {
                LockSupport.parkNanos(1_000_000_000L); // Park for exactly 1 second
                flushTelemetryToFile(null);
            }
        });
        daemon.start();
        System.out.println("[Telemetry] Asynchronous CrashDumper daemon started.");
    }

    /**
     * Updates telemetry values inside the pre-allocated off-heap segment.
     */
    public static void updateFrame(long frame)
    {
        ForeignMemory.putLong(TELEMETRY_BLOCK + OFFSET_FRAME, frame);
    }

    public static void updateLastPointer(long ptr)
    {
        ForeignMemory.putLong(TELEMETRY_BLOCK + OFFSET_LAST_PTR, ptr);
    }

    public static void updateState(int state)
    {
        ForeignMemory.putInt(TELEMETRY_BLOCK + OFFSET_STATE, state);
    }

    public static void updateDisplayCount(int count)
    {
        ForeignMemory.putInt(TELEMETRY_BLOCK + OFFSET_DISPLAY_COUNT, count);
    }

    public static void updateActivePipelines(int count)
    {
        ForeignMemory.putInt(TELEMETRY_BLOCK + OFFSET_ACTIVE_PIPELINES, count);
    }

    public static void updateActiveThreads(int count)
    {
        ForeignMemory.putInt(TELEMETRY_BLOCK + OFFSET_ACTIVE_THREADS, count);
    }

    public static void updateAllocationsCount(long count)
    {
        ForeignMemory.putLong(TELEMETRY_BLOCK + OFFSET_ALLOC_COUNT, count);
    }

    /**
     * Dumps a Java exception alongside current telemetry details.
     */
    public static void dumpException(Throwable t)
    {
        flushTelemetryToFile(t);
    }

    /**
     * Flushes the pre-allocated telemetry block to the dump file.
     * If an exception is provided, appends the full stack trace to the dump.
     */
    private static synchronized void flushTelemetryToFile(Throwable exception)
    {
        String filename = DUMP_FILE_PATH;
        if (exception != null)
        {
            java.time.LocalDateTime now = java.time.LocalDateTime.now();
            java.time.format.DateTimeFormatter dtf = java.time.format.DateTimeFormatter.ofPattern("yyyy-MM-dd_HHmmss");
            filename = "crash-dump_" + now.format(dtf) + ".txt";
        }

        try (FileOutputStream fos = new FileOutputStream(filename, false))
        {
            long frame = ForeignMemory.getLong(TELEMETRY_BLOCK + OFFSET_FRAME);
            long lastPtr = ForeignMemory.getLong(TELEMETRY_BLOCK + OFFSET_LAST_PTR);
            int state = ForeignMemory.getInt(TELEMETRY_BLOCK + OFFSET_STATE);
            int displayCount = ForeignMemory.getInt(TELEMETRY_BLOCK + OFFSET_DISPLAY_COUNT);
            int pipelines = ForeignMemory.getInt(TELEMETRY_BLOCK + OFFSET_ACTIVE_PIPELINES);
            int threads = ForeignMemory.getInt(TELEMETRY_BLOCK + OFFSET_ACTIVE_THREADS);
            long allocs = ForeignMemory.getLong(TELEMETRY_BLOCK + OFFSET_ALLOC_COUNT);

            StringBuilder sb = new StringBuilder();
            sb.append("========================================\n");
            sb.append("         ANTI ENGINE TELEMETRY DUMP     \n");
            sb.append("========================================\n");
            sb.append("Engine State Code   : ").append(state).append("\n");
            sb.append("Frame Count         : ").append(frame).append("\n");
            sb.append("Last Active Pointer : 0x").append(Long.toHexString(lastPtr).toUpperCase()).append("\n");
            sb.append("Display Count       : ").append(displayCount).append("\n");
            sb.append("Active Pipelines    : ").append(pipelines).append("\n");
            sb.append("Active Threads      : ").append(threads).append("\n");
            sb.append("Total Allocations   : ").append(allocs).append("\n");

            if (exception != null)
            {
                sb.append("\n========================================\n");
                sb.append("         CAUGHT RUNTIME EXCEPTION       \n");
                sb.append("========================================\n");
                StringWriter sw = new StringWriter();
                try (PrintWriter pw = new PrintWriter(sw))
                {
                    exception.printStackTrace(pw);
                }
                sb.append(sw.toString());
            }

            fos.write(sb.toString().getBytes(StandardCharsets.UTF_8));
            fos.flush();
        }
        catch (Throwable ignored) {}
    }
}
