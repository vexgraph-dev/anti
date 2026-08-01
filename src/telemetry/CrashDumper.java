package telemetry;

import annotation.Draft;
import annotation.Intention;
import nio.ForeignMemory;
import java.lang.foreign.*;
import java.lang.invoke.MethodHandle;
import java.lang.invoke.MethodHandles;
import java.lang.invoke.MethodType;

/**
 * High-performance, zero-overhead native crash handler and diagnostic dumper.
 * Intercepts OS signals (SIGSEGV, SIGBUS, SIGILL) via FFM upcall stubs.
 * Writes diagnostic telemetry and flushes memory info to disk without Java heap allocations.
 */
@Draft
@Intention("Pre-allocated off-heap signal handling system to intercept native segfaults safely without JVM heap corruption")
public final class CrashDumper
{
    private static final Linker LINKER = Linker.nativeLinker();
    private static final MethodHandle SIGNAL;
    private static final MethodHandle WRITE;
    private static final MethodHandle OPEN;
    private static final MethodHandle CLOSE;
    private static final MethodHandle EXIT;

    // Pre-allocated 1KB off-heap telemetry block to hold engine status safely
    public static final long TELEMETRY_BLOCK;
    
    // Telemetry fields offsets
    public static final long OFFSET_FRAME = 0L;              // 8 bytes
    public static final long OFFSET_LAST_PTR = 8L;           // 8 bytes
    public static final long OFFSET_STATE = 16L;             // 4 bytes
    public static final long OFFSET_DISPLAY_COUNT = 20L;     // 4 bytes
    public static final long OFFSET_ACTIVE_PIPELINES = 24L;  // 4 bytes
    public static final long OFFSET_ACTIVE_THREADS = 28L;    // 4 bytes
    public static final long OFFSET_ALLOC_COUNT = 32L;       // 8 bytes

    // Pre-allocated C-strings for signal handler prints
    private static final MemorySegment PATH_SEGMENT;
    private static final MemorySegment HEADER_MSG;
    private static final MemorySegment SIG_MSG;
    private static final MemorySegment FRAME_MSG;
    private static final MemorySegment LAST_PTR_MSG;
    private static final MemorySegment STATE_MSG;
    private static final MemorySegment FOOTER_MSG;

    static
    {
        // 1. Allocate off-heap structures permanently
        TELEMETRY_BLOCK = ForeignMemory.allocateNative(1024L);
        // Zero out block
        for (long i = 0; i < 1024L; i++)
        {
            ForeignMemory.putByte(TELEMETRY_BLOCK + i, (byte)0);
        }

        // 2. Pre-allocate static ASCII labels for async-signal-safe writes
        Arena globalArena = Arena.global();
        PATH_SEGMENT = globalArena.allocateFrom("crash-dump.txt\0");
        HEADER_MSG = globalArena.allocateFrom("\n========================================\n!!! CRITICAL NATIVE ENGINE FAULT !!!\n========================================\n\0");
        SIG_MSG = globalArena.allocateFrom("OS Signal Intercepted : SIG\0");
        FRAME_MSG = globalArena.allocateFrom("\nEngine Frame Count    : \0");
        LAST_PTR_MSG = globalArena.allocateFrom("\nLast Active Pointer   : 0x\0");
        STATE_MSG = globalArena.allocateFrom("\nEngine State Code     : \0");
        FOOTER_MSG = globalArena.allocateFrom("\n========================================\nCrash diagnostic written to crash-dump.txt\nProcess terminating...\n\n\0");

        // 3. Resolve system downcalls in libc
        SymbolLookup libc = LINKER.defaultLookup();
        
        try
        {
            MemorySegment signalSym = libc.find("signal").orElseThrow();
            SIGNAL = LINKER.downcallHandle(signalSym, FunctionDescriptor.of(ValueLayout.ADDRESS, ValueLayout.JAVA_INT, ValueLayout.ADDRESS));

            MemorySegment writeSym = libc.find("write").orElseThrow();
            WRITE = LINKER.downcallHandle(writeSym, FunctionDescriptor.of(ValueLayout.JAVA_LONG, ValueLayout.JAVA_INT, ValueLayout.ADDRESS, ValueLayout.JAVA_LONG));

            MemorySegment openSym = libc.find("open").orElseThrow();
            OPEN = LINKER.downcallHandle(openSym, FunctionDescriptor.of(ValueLayout.JAVA_INT, ValueLayout.ADDRESS, ValueLayout.JAVA_INT, ValueLayout.JAVA_INT));

            MemorySegment closeSym = libc.find("close").orElseThrow();
            CLOSE = LINKER.downcallHandle(closeSym, FunctionDescriptor.of(ValueLayout.JAVA_INT, ValueLayout.JAVA_INT));

            MemorySegment exitSym = libc.find("_exit").orElseThrow();
            EXIT = LINKER.downcallHandle(exitSym, FunctionDescriptor.ofVoid(ValueLayout.JAVA_INT));
        }
        catch (Throwable t)
        {
            throw new RuntimeException("CRITICAL: Failed to bind libc signal handling functions", t);
        }
    }

    private CrashDumper() {}

    /**
     * Initializes native signal listeners for SIGSEGV, SIGBUS, and SIGILL.
     */
    public static void init()
    {
        try
        {
            // Create an upcall stub for our signal handler method
            MemorySegment handlerStub = LINKER.upcallStub(
                MethodHandles.lookup().findStatic(CrashDumper.class, "handleNativeCrash", MethodType.methodType(void.class, int.class)),
                FunctionDescriptor.ofVoid(ValueLayout.JAVA_INT),
                Arena.global()
            );

            // Register handlers: SIGILL (4), SIGBUS (10), SIGSEGV (11)
            SIGNAL.invokeExact(4, handlerStub);
            SIGNAL.invokeExact(10, handlerStub);
            SIGNAL.invokeExact(11, handlerStub);

            System.out.println("[Telemetry] Segfault-Safe CrashDumper registered.");
        }
        catch (Throwable t)
        {
            System.err.println("[Telemetry WARNING] Failed to register signal handlers: " + t.getMessage());
        }
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
     * Signal Handler Callback triggered directly by the OS kernel.
     * Uses only async-signal-safe system calls (write, open, close, _exit) and pre-allocated segments.
     */
    private static void handleNativeCrash(int signal)
    {
        try
        {
            // Open a file descriptors for crash dump: O_WRONLY | O_CREAT | O_TRUNC (0x0601 on macOS, permissions 0666)
            int fd = (int) OPEN.invokeExact(PATH_SEGMENT, 0x0601, 438);

            // Print warning headers to both stdout (1), stderr (2), and the file descriptor
            writeMsg(1, HEADER_MSG);
            writeMsg(2, HEADER_MSG);
            if (fd >= 0) writeMsg(fd, HEADER_MSG);

            // Print signal number
            writeMsg(1, SIG_MSG);
            writeMsg(2, SIG_MSG);
            writeInt(1, signal);
            writeInt(2, signal);
            if (fd >= 0)
            {
                writeMsg(fd, SIG_MSG);
                writeInt(fd, signal);
            }

            // Print frame count
            writeMsg(1, FRAME_MSG);
            writeMsg(2, FRAME_MSG);
            long frame = ForeignMemory.getLong(TELEMETRY_BLOCK + OFFSET_FRAME);
            writeLong(1, frame);
            writeLong(2, frame);
            if (fd >= 0)
            {
                writeMsg(fd, FRAME_MSG);
                writeLong(fd, frame);
            }

            // Print last active pointer in hex representation
            writeMsg(1, LAST_PTR_MSG);
            writeMsg(2, LAST_PTR_MSG);
            long ptr = ForeignMemory.getLong(TELEMETRY_BLOCK + OFFSET_LAST_PTR);
            writeHex(1, ptr);
            writeHex(2, ptr);
            if (fd >= 0)
            {
                writeMsg(fd, LAST_PTR_MSG);
                writeHex(fd, ptr);
            }

            // Print state code
            writeMsg(1, STATE_MSG);
            writeMsg(2, STATE_MSG);
            int state = ForeignMemory.getInt(TELEMETRY_BLOCK + OFFSET_STATE);
            writeInt(1, state);
            writeInt(2, state);
            if (fd >= 0)
            {
                writeMsg(fd, STATE_MSG);
                writeInt(fd, state);
            }

            // Write footer complete
            writeMsg(1, FOOTER_MSG);
            writeMsg(2, FOOTER_MSG);
            if (fd >= 0)
            {
                writeMsg(fd, FOOTER_MSG);
                CLOSE.invokeExact(fd);
            }

            // Instantly terminate application to avoid double-fault loops
            EXIT.invokeExact(1);
        }
        catch (Throwable ignored)
        {
            // Extreme fallback: directly invoke exit syscall
            try
            {
                EXIT.invokeExact(1);
            }
            catch (Throwable fatal) {}
        }
    }

    private static void writeMsg(int fd, MemorySegment msg) throws Throwable
    {
        // Find null-terminator length
        long len = 0;
        while (msg.get(ValueLayout.JAVA_BYTE, len) != 0)
        {
            len++;
        }
        if (len > 0)
        {
            WRITE.invokeExact(fd, msg, len);
        }
    }

    private static void writeInt(int fd, int val) throws Throwable
    {
        writeLong(fd, (long)val);
    }

    private static void writeLong(int fd, long val) throws Throwable
    {
        if (val == 0)
        {
            writeByte(fd, (byte)'0');
            return;
        }
        if (val < 0)
        {
            writeByte(fd, (byte)'-');
            val = -val;
        }
        
        long scratch = TELEMETRY_BLOCK + 512L; // Offset 512 is used as safe formatting scratch area
        int index = 20;
        while (val > 0)
        {
            byte digit = (byte)('0' + (val % 10));
            ForeignMemory.putByte(scratch + index, digit);
            val /= 10;
            index--;
        }
        
        MemorySegment scratchSegment = MemorySegment.ofAddress(scratch + index + 1).reinterpret(20L - index);
        WRITE.invokeExact(fd, scratchSegment, (long)(20 - index));
    }

    private static void writeHex(int fd, long val) throws Throwable
    {
        if (val == 0)
        {
            writeByte(fd, (byte)'0');
            return;
        }

        long scratch = TELEMETRY_BLOCK + 512L;
        int index = 16;
        char[] hexDigits = "0123456789ABCDEF".toCharArray();
        while (val > 0)
        {
            int digit = (int)(val & 0xF);
            ForeignMemory.putByte(scratch + index, (byte)hexDigits[digit]);
            val >>>= 4;
            index--;
        }

        MemorySegment scratchSegment = MemorySegment.ofAddress(scratch + index + 1).reinterpret(16L - index);
        WRITE.invokeExact(fd, scratchSegment, (long)(16 - index));
    }

    private static void writeByte(int fd, byte b) throws Throwable
    {
        long scratch = TELEMETRY_BLOCK + 512L;
        ForeignMemory.putByte(scratch, b);
        MemorySegment scratchSegment = MemorySegment.ofAddress(scratch).reinterpret(1L);
        WRITE.invokeExact(fd, scratchSegment, 1L);
    }
}
