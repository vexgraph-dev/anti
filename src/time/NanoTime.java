package time;

import annotation.Draft;
import annotation.Required;
import annotation.Intention;
import nio.ForeignMemory;
import oop.TypeRegister;

import java.lang.foreign.FunctionDescriptor;
import java.lang.foreign.Linker;
import java.lang.foreign.SymbolLookup;
import java.lang.foreign.ValueLayout;
import java.lang.invoke.MethodHandle;

/**
 * Off-Heap Native High-Resolution Timer Subsystem.
 * Uses native macOS clock downcalls for zero-overhead ticks.
 */
@Draft
@Intention("Zero-GC off-heap frame tick timer integrated with native macOS clock_gettime_nsec_np and virtual clock scaling.")
public final class NanoTime {

    @Required
    public static final int CLASS_ID = TypeRegister.ID_NANOTIME;

    private static final MethodHandle CLOCK_GETTIME_NSEC_NP_HANDLE;

    static {
        Linker linker = Linker.nativeLinker();
        SymbolLookup stdlib = linker.defaultLookup();
        MethodHandle mh = null;
        try {
            // CLOCK_MONOTONIC = 6 on macOS
            // clock_gettime_nsec_np returns uint64_t nanoseconds directly
            mh = stdlib.find("clock_gettime_nsec_np").map(symbol ->
                linker.downcallHandle(symbol, FunctionDescriptor.of(ValueLayout.JAVA_LONG, ValueLayout.JAVA_INT))
            ).orElse(null);
        } catch (Throwable t) {
            // fallback
        }
        CLOCK_GETTIME_NSEC_NP_HANDLE = mh;
    }

    private NanoTime() {}

    public static int classId() {
        return CLASS_ID;
    }

    /**
     * Reads native OS monotonic time in nanoseconds.
     */
    @Draft
    public static long getNativeNanos() {
        if (CLOCK_GETTIME_NSEC_NP_HANDLE != null) {
            try {
                // CLOCK_MONOTONIC = 6
                return (long) CLOCK_GETTIME_NSEC_NP_HANDLE.invokeExact(6);
            } catch (Throwable t) {
                // fallback
            }
        }
        return System.nanoTime();
    }

    /**
     * Allocates a new NanoTime instance.
     * Memory Layout:
     *   [userPtr - 8]: Type ID (NANOTIME_SINGLETON)
     *   [userPtr - 4]: Length (1)
     *   [userPtr + 0]: 64-bit long startNanos
     *   [userPtr + 8]: 64-bit long lastNanos
     *   [userPtr + 16]: 64-bit long currentNanos
     *   [userPtr + 24]: 64-bit double deltaTime (seconds)
     *   [userPtr + 32]: 64-bit double totalTime (accumulated virtual time in seconds)
     */
    @Draft
    public static long allocate() {
        long block = ForeignMemory.allocateNative(48);
        long userPtr = block + 8L;

        ForeignMemory.setInt(block, TypeRegister.NANOTIME_SINGLETON);
        ForeignMemory.setInt(block + 4L, 1);

        long now = getNativeNanos();
        ForeignMemory.setLong(userPtr, now);       // startNanos
        ForeignMemory.setLong(userPtr + 8L, now);  // lastNanos
        ForeignMemory.setLong(userPtr + 16L, now); // currentNanos
        ForeignMemory.setDouble(userPtr + 24L, 0.0); // deltaTime
        ForeignMemory.setDouble(userPtr + 32L, 0.0); // totalTime

        return userPtr;
    }

    @Draft
    public static void free(long ptr) {
        if (ptr == 0L) return;
        ForeignMemory.freeNative(ptr - 8L);
    }

    @Draft
    public static void reset(long ptr) {
        if (ptr == 0L) throw new NullPointerException("Accessing NULL off-heap pointer!");
        long now = getNativeNanos();
        ForeignMemory.setLong(ptr, now);
        ForeignMemory.setLong(ptr + 8L, now);
        ForeignMemory.setLong(ptr + 16L, now);
        ForeignMemory.setDouble(ptr + 24L, 0.0);
        ForeignMemory.setDouble(ptr + 32L, 0.0);
    }

    @Draft
    public static void tick(long ptr) {
        if (ptr == 0L) throw new NullPointerException("Accessing NULL off-heap pointer!");
        long now = getNativeNanos();
        long current = ForeignMemory.getLong(ptr + 16L);
        
        ForeignMemory.setLong(ptr + 8L, current);
        ForeignMemory.setLong(ptr + 16L, now);

        double deltaSec = (now - current) / 1_000_000_000.0;
        ForeignMemory.setDouble(ptr + 24L, deltaSec);
        
        double total = ForeignMemory.getDouble(ptr + 32L);
        ForeignMemory.setDouble(ptr + 32L, total + deltaSec);
    }

    /**
     * Ticks the high-res timer and scales delta-time using the state of the provided virtual clock.
     */
    @Draft
    public static void tick(long ptr, long clockPtr) {
        if (ptr == 0L) throw new NullPointerException("Accessing NULL off-heap pointer!");
        
        long now = getNativeNanos();
        long current = ForeignMemory.getLong(ptr + 16L);
        
        ForeignMemory.setLong(ptr + 8L, current);
        ForeignMemory.setLong(ptr + 16L, now);

        double realDeltaSec = (now - current) / 1_000_000_000.0;
        double scale = 1.0;
        if (clockPtr != 0L) {
            if (Clock.isPaused(clockPtr)) {
                scale = 0.0;
            } else {
                scale = Clock.getTimeScale(clockPtr);
            }
        }
        
        double scaledDeltaSec = realDeltaSec * scale;
        ForeignMemory.setDouble(ptr + 24L, scaledDeltaSec);

        double total = ForeignMemory.getDouble(ptr + 32L);
        ForeignMemory.setDouble(ptr + 32L, total + scaledDeltaSec);
    }

    @Draft
    public static double getDeltaTime(long ptr) {
        if (ptr == 0L) throw new NullPointerException("Accessing NULL off-heap pointer!");
        return ForeignMemory.getDouble(ptr + 24L);
    }

    @Draft
    public static double getTotalTime(long ptr) {
        if (ptr == 0L) throw new NullPointerException("Accessing NULL off-heap pointer!");
        return ForeignMemory.getDouble(ptr + 32L);
    }

    @Draft
    public static long getDeltaNanos(long ptr) {
        if (ptr == 0L) throw new NullPointerException("Accessing NULL off-heap pointer!");
        return ForeignMemory.getLong(ptr + 16L) - ForeignMemory.getLong(ptr + 8L);
    }

    @Draft
    public static long getElapsedNanos(long ptr) {
        if (ptr == 0L) throw new NullPointerException("Accessing NULL off-heap pointer!");
        return ForeignMemory.getLong(ptr + 16L) - ForeignMemory.getLong(ptr);
    }
}
