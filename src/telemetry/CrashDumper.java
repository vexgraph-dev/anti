package telemetry;

import annotation.Draft;
import annotation.Intention;
import nio.ForeignMemory;

import java.io.FileOutputStream;
import java.io.PrintWriter;
import java.io.StringWriter;
import java.nio.charset.StandardCharsets;
import java.util.concurrent.locks.LockSupport;

import nio.StringLookup;
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
    private static final String DUMP_FILE_PATH = StringLookup.getJavaString(1009);

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
            ForeignMemory.setByte(TELEMETRY_BLOCK + i, (byte)0);
        }
    }

    private CrashDumper() {}

    /**
     * Spawns the background daemon thread to flush telemetry state every 1 second.
     */
    public static void init()
    {
        Thread daemon = Thread.ofPlatform().daemon(true).name(StringLookup.getJavaString(1010)).unstarted(() -> {
            while (true)
            {
                LockSupport.parkNanos(1_000_000_000L); // Park for exactly 1 second
                flushTelemetryToFile(null);
            }
        });
        daemon.start();
        System.out.println(StringLookup.getJavaString(1011));
    }

    /**
     * Updates telemetry values inside the pre-allocated off-heap segment.
     */
    public static void updateFrame(long frame)
    {
        ForeignMemory.setLong(TELEMETRY_BLOCK + OFFSET_FRAME, frame);
    }

    public static void updateLastPointer(long ptr)
    {
        ForeignMemory.setLong(TELEMETRY_BLOCK + OFFSET_LAST_PTR, ptr);
    }

    public static void updateState(int state)
    {
        ForeignMemory.setInt(TELEMETRY_BLOCK + OFFSET_STATE, state);
    }

    public static void updateDisplayCount(int count)
    {
        ForeignMemory.setInt(TELEMETRY_BLOCK + OFFSET_DISPLAY_COUNT, count);
    }

    public static void updateActivePipelines(int count)
    {
        ForeignMemory.setInt(TELEMETRY_BLOCK + OFFSET_ACTIVE_PIPELINES, count);
    }

    public static void updateActiveThreads(int count)
    {
        ForeignMemory.setInt(TELEMETRY_BLOCK + OFFSET_ACTIVE_THREADS, count);
    }

    public static void updateAllocationsCount(long count)
    {
        ForeignMemory.setLong(TELEMETRY_BLOCK + OFFSET_ALLOC_COUNT, count);
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
            java.time.format.DateTimeFormatter dtf = java.time.format.DateTimeFormatter.ofPattern(StringLookup.getJavaString(1012));
            filename = StringLookup.getJavaString(1013) + now.format(dtf) + StringLookup.getJavaString(1014);
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
            sb.append(StringLookup.getJavaString(1015));
            sb.append(StringLookup.getJavaString(1016));
            sb.append(StringLookup.getJavaString(1015));
            sb.append(StringLookup.getJavaString(1017)).append(state).append(StringLookup.getJavaString(102));
            sb.append(StringLookup.getJavaString(1018)).append(frame).append(StringLookup.getJavaString(102));
            sb.append(StringLookup.getJavaString(1019)).append(Long.toHexString(lastPtr).toUpperCase()).append(StringLookup.getJavaString(102));
            sb.append(StringLookup.getJavaString(1020)).append(displayCount).append(StringLookup.getJavaString(102));
            sb.append(StringLookup.getJavaString(1021)).append(pipelines).append(StringLookup.getJavaString(102));
            sb.append(StringLookup.getJavaString(1022)).append(threads).append(StringLookup.getJavaString(102));
            sb.append(StringLookup.getJavaString(1023)).append(allocs).append(StringLookup.getJavaString(102));

            if (exception != null)
            {
                sb.append(StringLookup.getJavaString(1024));
                sb.append(StringLookup.getJavaString(1025));
                sb.append(StringLookup.getJavaString(1015));
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
