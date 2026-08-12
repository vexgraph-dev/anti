package nio;

import annotation.HotCode;
import annotation.Intention;
import annotation.Unsafe;
import annotation.Volatile;
import oop.Struct;
import primitive.Byte;
import primitive.Bool;
import primitive.Double;
import primitive.Float;
import primitive.Brain;
import primitive.Fixed32;
import primitive.Fixed64;
import primitive.Int;
import primitive.IntDouble;
import primitive.IntFloat;
import primitive.Long;
import primitive.LongFloat;
import primitive.LongDouble;
import primitive.Short;
import primitive.string;
import variable.Variable;
import variable.SearchVariable;
import struct.List;
import struct.Array;
import struct.Deque;
import struct.Stack;
import struct.Queue;
import struct.Map;
import struct.Set;
import search.Trie;
import spatial.GridArray;
import spatial.CircularArray;
import thread.RingBuffer;
import thread.DrawThread;
import thread.NetworkingThread;
import thread.ConsoleThread;
import thread.ScriptingThread;
import audio.AudioBuffer;
import audio.AudioBufferLayer;
import audio.AudioSource;
import audio.Sampler;
import audio.vulkan.AudioComputeBuffer;
import buffers.Buffer;
import vulkan.CommandBuffer;
import vulkan.CommandPool;
import vulkan.Fence;
import vulkan.RenderPass;
import vulkan.Semaphore;
import vulkan.Swapchain;
import vulkan.VKBuffer;
import vulkan.VKDeviceMemory;
import vulkan.VKFramebuffer;
import vulkan.VKImage;
import vulkan.VKImageView;
import vulkan.VKTexture;
import vulkan.VKPipeline;
import vulkan.VKPipelineLayout;
import vulkan.VKShaderModule;

import java.lang.foreign.Arena;
import java.lang.foreign.FunctionDescriptor;
import java.lang.foreign.Linker;
import java.lang.foreign.MemorySegment;
import java.lang.foreign.SymbolLookup;
import java.lang.foreign.ValueLayout;
import java.lang.invoke.MethodHandle;
import java.lang.invoke.MethodHandles;
import java.lang.invoke.VarHandle;
import java.nio.ByteBuffer;
import java.nio.channels.FileChannel;
import java.nio.file.Path;

public class ForeignMemory {

    // god, the lens of all things. might be dangerous to hold, low-key
    // literally c flavored hell java version
    // good luck myself
    private static final MemorySegment GLOBAL_MEMORY = MemorySegment.ofAddress(0).reinterpret(java.lang.Long.MAX_VALUE);

    private static final VarHandle BYTE_VH = ValueLayout.JAVA_BYTE.varHandle();
    private static final VarHandle SHORT_VH = ValueLayout.JAVA_SHORT.varHandle();
    private static final VarHandle INT_VH = ValueLayout.JAVA_INT.varHandle();
    private static final VarHandle LONG_VH = ValueLayout.JAVA_LONG.varHandle();
    private static final VarHandle FLOAT_VH = ValueLayout.JAVA_FLOAT.varHandle();
    private static final VarHandle DOUBLE_VH = ValueLayout.JAVA_DOUBLE.varHandle();

    private static final MethodHandle MALLOC_HANDLE;
    private static final MethodHandle FREE_HANDLE;

    // --- Native allocation registry (double-free + leak tracking) ---
    // Lock-free off-heap open-addressing table of live malloc'd addresses.
    // Enabled unless -Danti.native-memory-tracking=false; "warn" downgrades
    // double-free detection from throwing to warning; "leaks" additionally
    // records the call site of every allocation and dumps a live-allocation
    // report (grouped by allocator) at JVM shutdown.
    static final int ALLOC_SLOT_LOG2 = 20;
    static final int ALLOC_SLOTS = 1 << ALLOC_SLOT_LOG2;
    static final int ALLOC_MASK = ALLOC_SLOTS - 1;
    static final long ALLOC_TOMBSTONE = -1L;
    static final long ALLOC_REGISTRY;
    private static final boolean NATIVE_TRACKING;
    private static final boolean NATIVE_TRACKING_STRICT;
    private static final boolean NATIVE_TRACKING_CALL_SITES;
    private static final java.util.concurrent.ConcurrentHashMap<String, long[]> ALLOC_SITE_COUNTS;
    private static final java.util.concurrent.ConcurrentHashMap<java.lang.Long, AllocInfo> ALLOC_ADDR_SITES;
    private static final java.lang.StackWalker SITE_WALKER = java.lang.StackWalker.getInstance();

    static {
        Linker linker = Linker.nativeLinker();
        SymbolLookup stdlib = linker.defaultLookup();

        try {
            MALLOC_HANDLE = linker.downcallHandle(
                    stdlib.find("malloc").orElseThrow(),
                    FunctionDescriptor.of(ValueLayout.ADDRESS, ValueLayout.JAVA_LONG)
            );

            FREE_HANDLE = linker.downcallHandle(
                    stdlib.find("free").orElseThrow(),
                    FunctionDescriptor.ofVoid(ValueLayout.ADDRESS)
            );
        } catch (Throwable t) {
            throw new ExceptionInInitializerError(t);
        }

        String tracking = java.lang.System.getProperty("anti.native-memory-tracking", "true");
        NATIVE_TRACKING = !"false".equalsIgnoreCase(tracking) && !"off".equalsIgnoreCase(tracking);
        NATIVE_TRACKING_STRICT = !"warn".equalsIgnoreCase(tracking);
        NATIVE_TRACKING_CALL_SITES = "leaks".equalsIgnoreCase(tracking);
        if (NATIVE_TRACKING_CALL_SITES) {
            ALLOC_SITE_COUNTS = new java.util.concurrent.ConcurrentHashMap<>();
            ALLOC_ADDR_SITES = new java.util.concurrent.ConcurrentHashMap<>();
            System.out.println("[ForeignMemory] call-site leak tracking armed");
        } else {
            ALLOC_SITE_COUNTS = null;
            ALLOC_ADDR_SITES = null;
        }

        if (NATIVE_TRACKING) {
            try {
                long table = ((MemorySegment) MALLOC_HANDLE.invokeExact((long) ALLOC_SLOTS * 8L)).address();
                MemorySegment.ofAddress(table).reinterpret((long) ALLOC_SLOTS * 8L).fill((byte) 0);
                ALLOC_REGISTRY = table;
            } catch (Throwable t) {
                throw new ExceptionInInitializerError("Failed to allocate native allocation registry: " + t);
            }
        } else {
            ALLOC_REGISTRY = 0L;
        }

        if (NATIVE_TRACKING_CALL_SITES) {
            Runtime.getRuntime().addShutdownHook(
                    new Thread(ForeignMemory::dumpAllocationLeaks, "anti-native-leak-report"));
        }
    }

    private static int registrySlot(long address) {
        return (int) ((address * 0x9E3779B97F4A7C15L) >>> (64 - ALLOC_SLOT_LOG2));
    }

    private static void trackAllocation(long address) {
        int i = registrySlot(address);
        for (int probes = 0; probes < ALLOC_SLOTS; probes++) {
            long slot = ALLOC_REGISTRY + i * 8L;
            long cur = (long) LONG_VH.getVolatile(GLOBAL_MEMORY, slot);
            if (cur == 0L || cur == ALLOC_TOMBSTONE) {
                if ((boolean) LONG_VH.compareAndSet(GLOBAL_MEMORY, slot, cur, address)) return;
            }
            i = (i + 1) & ALLOC_MASK;
        }
        throw new IllegalStateException("Native allocation registry exhausted (" + ALLOC_SLOTS + " live allocations)!");
    }

    private static boolean untrackAllocation(long address) {
        int i = registrySlot(address);
        for (int probes = 0; probes < ALLOC_SLOTS; probes++) {
            long slot = ALLOC_REGISTRY + i * 8L;
            long cur = (long) LONG_VH.getVolatile(GLOBAL_MEMORY, slot);
            if (cur == 0L) return false; // not present -> double-free or unknown pointer
            if (cur == address) {
                if ((boolean) LONG_VH.compareAndSet(GLOBAL_MEMORY, slot, address, ALLOC_TOMBSTONE)) return true;
                // lost the race to a concurrent free; keep probing
            }
            i = (i + 1) & ALLOC_MASK;
        }
        return false;
    }

    public static long allocateNative(long bytes) {
        if (bytes <= 0) return 0L;
        try {
            MemorySegment seg = (MemorySegment) MALLOC_HANDLE.invokeExact(bytes);
            long addr = seg.address();
            if (NATIVE_TRACKING) {
                trackAllocation(addr);
                if (NATIVE_TRACKING_CALL_SITES) recordAllocationSite(addr, bytes);
            }
            return addr;
        } catch (Throwable t) {
            throw new OutOfMemoryError("Native malloc failed for size: " + bytes);
        }
    }

    public static void freeNative(long address) {
        if (address == 0L)
            throw new RuntimeException("cant free a null pointer silly!");
        if (NATIVE_TRACKING) {
            if (untrackAllocation(address)) {
                if (NATIVE_TRACKING_CALL_SITES) releaseAllocationSite(address);
            } else if (NATIVE_TRACKING_STRICT) {
                throw new RuntimeException("Double-free or untracked native free at 0x" + java.lang.Long.toHexString(address));
            } else {
                System.err.println("[ForeignMemory] WARNING: double-free or untracked native free at 0x" + java.lang.Long.toHexString(address));
            }
        }
        try {
            FREE_HANDLE.invokeExact(MemorySegment.ofAddress(address));
        } catch (Throwable t) {
            throw new RuntimeException("Native free failed for address: " + address, t);
        }
    }

    // --- Call-site leak tracking ("leaks" mode) ---
    // Heap-backed, opt-in, and only active while hunting a leak. Every live
    // malloc'd address maps to its allocation site + size, and per-site live
    // counters are maintained so dumpAllocationLeaks can show "who still holds
    // memory" at JVM shutdown instead of a wall of opaque addresses.

    private static final class AllocInfo {
        final String site;
        final long bytes;

        AllocInfo(String site, long bytes) {
            this.site = site;
            this.bytes = bytes;
        }
    }

    private static String captureAllocSite() {
        return SITE_WALKER.walk(fs -> {
            java.util.Iterator<java.lang.StackWalker.StackFrame> it = fs.iterator();
            while (it.hasNext()) {
                java.lang.StackWalker.StackFrame f = it.next();
                String cn = f.getClassName();
                if (cn.startsWith("java.") || cn.startsWith("jdk.") || cn.startsWith("nio.ForeignMemory"))
                    continue;
                String line = f.getLineNumber() > 0 ? ":" + f.getLineNumber() : "";
                return cn + "." + f.getMethodName() + "("
                        + (f.getFileName() != null ? f.getFileName() : "?") + line + ")";
            }
            return "unknown";
        });
    }

    private static void recordAllocationSite(long address, long bytes) {
        String site = captureAllocSite();
        long[] c = ALLOC_SITE_COUNTS.computeIfAbsent(site, k -> new long[2]);
        c[0]++;
        c[1] += bytes;
        ALLOC_ADDR_SITES.put(java.lang.Long.valueOf(address), new AllocInfo(site, bytes));
    }

    private static void releaseAllocationSite(long address) {
        AllocInfo info = ALLOC_ADDR_SITES.remove(address);
        if (info != null) {
            long[] c = ALLOC_SITE_COUNTS.get(info.site);
            if (c != null) {
                c[0]--;
                c[1] -= info.bytes;
            }
        }
    }

    /** Prints every allocation site that still holds live native memory, biggest first. */
    public static void dumpAllocationLeaks() {
        if (!NATIVE_TRACKING_CALL_SITES) {
            System.out.println("[ForeignMemory] call-site leak report disabled; rerun with -Danti.native-memory-tracking=leaks");
            return;
        }
        java.util.ArrayList<java.util.Map.Entry<String, long[]>> live = new java.util.ArrayList<>();
        long totalCount = 0L, totalBytes = 0L;
        for (java.util.Map.Entry<String, long[]> e : ALLOC_SITE_COUNTS.entrySet()) {
            if (e.getValue()[0] > 0) {
                live.add(e);
                totalCount += e.getValue()[0];
                totalBytes += e.getValue()[1];
            }
        }
        live.sort((a, b) -> java.lang.Long.compare(b.getValue()[1], a.getValue()[1]));
        System.out.println("=== Native allocation leak report: " + totalCount + " live allocations, " + totalBytes + " bytes ===");
        for (java.util.Map.Entry<String, long[]> e : live) {
            long[] v = e.getValue();
            System.out.printf("  %6d allocs  %10d bytes  %s%n", v[0], v[1], e.getKey());
        }
        if (live.isEmpty()) System.out.println("  (none - clean teardown)");
    }

    /** Number of currently live native allocations (0 when tracking is disabled). */
    public static long liveAllocationCount() {
        if (!NATIVE_TRACKING) return -1L;
        long count = 0L;
        for (int i = 0; i < ALLOC_SLOTS; i++) {
            long cur = (long) LONG_VH.getVolatile(GLOBAL_MEMORY, ALLOC_REGISTRY + i * 8L);
            if (cur != 0L && cur != ALLOC_TOMBSTONE) count++;
        }
        return count;
    }

    /** Prints every live native allocation for leak analysis (tracking must be enabled). */
    public static void dumpAllocations() {
        if (!NATIVE_TRACKING) {
            System.out.println("[ForeignMemory] allocation tracking disabled (-Danti.native-memory-tracking=false)");
            return;
        }
        long count = 0L;
        for (int i = 0; i < ALLOC_SLOTS; i++) {
            long cur = (long) LONG_VH.getVolatile(GLOBAL_MEMORY, ALLOC_REGISTRY + i * 8L);
            if (cur != 0L && cur != ALLOC_TOMBSTONE) {
                System.out.println("[ForeignMemory] live allocation: 0x" + java.lang.Long.toHexString(cur));
                count++;
            }
        }
        System.out.println("[ForeignMemory] total live allocations: " + count);
    }

    // =========================================================================
    // STANDARD SAFE METHODS (No Annotations)
    // =========================================================================

    public static MemorySegment wrap(long address, long byteSize) {
        return MemorySegment.ofAddress(address).reinterpret(byteSize);
    }

    public static MemorySegment wrap(long address, long byteSize, Arena arena) {
        return MemorySegment.ofAddress(address).reinterpret(byteSize, arena, null);
    }

    public static void setMemory(long address, long byteSize, byte value) {
        if (address == 0L || byteSize <= 0) return;
        wrap(address, byteSize).fill(value);
    }

    public static void setMemory(long address, int byteSize, byte value) {
        if (address == 0L || byteSize <= 0) return;
        wrap(address, byteSize).fill(value);
    }

    public static void copy(long srcAddress, long destAddress, long bytes) {
        MemorySegment.copy(GLOBAL_MEMORY, srcAddress, GLOBAL_MEMORY, destAddress, bytes);
    }

    public static void copy(long srcAddress, long destAddress, int bytes) {
        MemorySegment.copy(GLOBAL_MEMORY, srcAddress, GLOBAL_MEMORY, destAddress, bytes);
    }

    public static void copyToHeap(long srcAddress, byte[] destArray, int destOffset, int length) {
        MemorySegment.copy(GLOBAL_MEMORY, ValueLayout.JAVA_BYTE, srcAddress, destArray, destOffset, length);
    }

    public static void copyFromHeap(byte[] srcArray, int srcOffset, long destAddress, int length) {
        MemorySegment.copy(srcArray, srcOffset, GLOBAL_MEMORY, ValueLayout.JAVA_BYTE, destAddress, length);
    }

    /** Copies length bytes from off-heap into a heap byte[]. Bridge-only helper (heap boundary is allowed at platform/API edges). */
    public static byte[] getBytes(long srcAddress, int length) {
        if (srcAddress == 0L) throw new NullPointerException("Reading from NULL off-heap pointer!");
        byte[] dest = new byte[length];
        if (length > 0) {
            MemorySegment.copy(GLOBAL_MEMORY, ValueLayout.JAVA_BYTE, srcAddress, dest, 0, length);
        }
        return dest;
    }

    public static byte getByte(long address) {
        if (address == 0L) throw new NullPointerException("Reading from NULL off-heap pointer!");
        return GLOBAL_MEMORY.get(ValueLayout.JAVA_BYTE, address);
    }

    public static void setByte(long address, byte value) {
        if (address == 0L) throw new NullPointerException("Writing to NULL off-heap pointer!");
        GLOBAL_MEMORY.set(ValueLayout.JAVA_BYTE, address, value);
    }

    public static void set(long address, byte value) {
        setByte(address, value);
    }

    public static short getShort(long address) {
        if (address == 0L) throw new NullPointerException("Reading from NULL off-heap pointer!");
        return GLOBAL_MEMORY.get(ValueLayout.JAVA_SHORT, address);
    }

    public static void setShort(long address, short value) {
        if (address == 0L) throw new NullPointerException("Writing to NULL off-heap pointer!");
        GLOBAL_MEMORY.set(ValueLayout.JAVA_SHORT, address, value);
    }

    public static void set(long address, short value) {
        setShort(address, value);
    }

    public static int getInt(long address) {
        if (address == 0L) throw new NullPointerException("Reading from NULL off-heap pointer!");
        return GLOBAL_MEMORY.get(ValueLayout.JAVA_INT, address);
    }

    public static void setInt(long address, int value) {
        if (address == 0L) throw new NullPointerException("Writing to NULL off-heap pointer!");
        GLOBAL_MEMORY.set(ValueLayout.JAVA_INT, address, value);
    }

    public static void set(long address, int value) {
        setInt(address, value);
    }

    public static long getLong(long address) {
        if (address == 0L) throw new NullPointerException("Reading from NULL off-heap pointer!");
        return GLOBAL_MEMORY.get(ValueLayout.JAVA_LONG, address);
    }

    public static void setLong(long address, long value) {
        if (address == 0L) throw new NullPointerException("Writing to NULL off-heap pointer!");
        GLOBAL_MEMORY.set(ValueLayout.JAVA_LONG, address, value);
    }

    public static void set(long address, long value) {
        setLong(address, value);
    }

    public static float getFloat(long address) {
        if (address == 0L) throw new NullPointerException("Reading from NULL off-heap pointer!");
        return GLOBAL_MEMORY.get(ValueLayout.JAVA_FLOAT, address);
    }

    public static void setFloat(long address, float value) {
        if (address == 0L) throw new NullPointerException("Writing to NULL off-heap pointer!");
        GLOBAL_MEMORY.set(ValueLayout.JAVA_FLOAT, address, value);
    }

    public static void set(long address, float value) {
        setFloat(address, value);
    }

    public static double getDouble(long address) {
        if (address == 0L) throw new NullPointerException("Reading from NULL off-heap pointer!");
        return GLOBAL_MEMORY.get(ValueLayout.JAVA_DOUBLE, address);
    }

    public static void setDouble(long address, double value) {
        if (address == 0L) throw new NullPointerException("Writing to NULL off-heap pointer!");
        GLOBAL_MEMORY.set(ValueLayout.JAVA_DOUBLE, address, value);
    }

    public static void set(long address, double value) {
        setDouble(address, value);
    }

    public static long getAddress(long address) {
        if (address == 0L) throw new NullPointerException("Reading address from NULL off-heap pointer!");
        return GLOBAL_MEMORY.get(ValueLayout.ADDRESS, address).address();
    }

    public static void setAddress(long address, long targetAddress) {
        if (address == 0L) throw new NullPointerException("Writing address to NULL off-heap pointer!");
        GLOBAL_MEMORY.set(ValueLayout.ADDRESS, address, MemorySegment.ofAddress(targetAddress));
    }

    public static String getString(long address) {
        if (address == 0) return null;
        return GLOBAL_MEMORY.getString(address);
    }

    public static short getShortUnaligned(long address) {
        if (address == 0L) throw new NullPointerException("Reading from NULL off-heap pointer!");
        return GLOBAL_MEMORY.get(ValueLayout.JAVA_SHORT_UNALIGNED, address);
    }

    public static void setShortUnaligned(long address, short value) {
        if (address == 0L) throw new NullPointerException("Writing to NULL off-heap pointer!");
        GLOBAL_MEMORY.set(ValueLayout.JAVA_SHORT_UNALIGNED, address, value);
    }

    public static int getIntUnaligned(long address) {
        if (address == 0L) throw new NullPointerException("Reading from NULL off-heap pointer!");
        return GLOBAL_MEMORY.get(ValueLayout.JAVA_INT_UNALIGNED, address);
    }

    public static void setIntUnaligned(long address, int value) {
        if (address == 0L) throw new NullPointerException("Writing to NULL off-heap pointer!");
        GLOBAL_MEMORY.set(ValueLayout.JAVA_INT_UNALIGNED, address, value);
    }

    public static long getLongUnaligned(long address) {
        if (address == 0L) throw new NullPointerException("Reading from NULL off-heap pointer!");
        return GLOBAL_MEMORY.get(ValueLayout.JAVA_LONG_UNALIGNED, address);
    }

    public static void setLongUnaligned(long address, long value) {
        if (address == 0L) throw new NullPointerException("Writing to NULL off-heap pointer!");
        GLOBAL_MEMORY.set(ValueLayout.JAVA_LONG_UNALIGNED, address, value);
    }

    public static float getFloatUnaligned(long address) {
        if (address == 0L) throw new NullPointerException("Reading from NULL off-heap pointer!");
        return GLOBAL_MEMORY.get(ValueLayout.JAVA_FLOAT_UNALIGNED, address);
    }

    public static void setFloatUnaligned(long address, float value) {
        if (address == 0L) throw new NullPointerException("Writing to NULL off-heap pointer!");
        GLOBAL_MEMORY.set(ValueLayout.JAVA_FLOAT_UNALIGNED, address, value);
    }

    public static double getDoubleUnaligned(long address) {
        if (address == 0L) throw new NullPointerException("Reading from NULL off-heap pointer!");
        return GLOBAL_MEMORY.get(ValueLayout.JAVA_DOUBLE_UNALIGNED, address);
    }

    public static void setDoubleUnaligned(long address, double value) {
        if (address == 0L) throw new NullPointerException("Writing to NULL off-heap pointer!");
        GLOBAL_MEMORY.set(ValueLayout.JAVA_DOUBLE_UNALIGNED, address, value);
    }

    public static boolean compareAndSetByte(long address, byte expected, byte value) {
        if (address == 0L) throw new NullPointerException("Writing to NULL off-heap pointer!");
        long alignedAddr = address & ~3L;
        int shift = (int) (address & 3L) * 8;
        int mask = 0xFF << shift;
        int expectedBits = (expected & 0xFF) << shift;
        int valueBits = (value & 0xFF) << shift;
        while (true) {
            int oldVal = getVolatileInt(alignedAddr);
            if (((oldVal >>> shift) & 0xFF) != (expected & 0xFF)) {
                return false;
            }
            int newVal = (oldVal & ~mask) | valueBits;
            if (compareAndSetInt(alignedAddr, oldVal, newVal)) {
                return true;
            }
        }
    }

    public static boolean compareAndSetShort(long address, short expected, short value) {
        if (address == 0L) throw new NullPointerException("Writing to NULL off-heap pointer!");
        long alignedAddr = address & ~3L;
        int shift = (int) (address & 3L) * 8;
        int mask = 0xFFFF << shift;
        int expectedBits = (expected & 0xFFFF) << shift;
        int valueBits = (value & 0xFFFF) << shift;
        while (true) {
            int oldVal = getVolatileInt(alignedAddr);
            if (((oldVal >>> shift) & 0xFFFF) != (expected & 0xFFFF)) {
                return false;
            }
            int newVal = (oldVal & ~mask) | valueBits;
            if (compareAndSetInt(alignedAddr, oldVal, newVal)) {
                return true;
            }
        }
    }


    public static short getAndSetShort(long address, short value)
    {
        if (address == 0L) throw new NullPointerException("Writing to NULL off-heap pointer!");
        return (short) SHORT_VH.getAndSet(GLOBAL_MEMORY, address, value);
    }

    public static byte getAndSetByte(long address, byte value)
    {
        if (address == 0L) throw new NullPointerException("Writing to NULL off-heap pointer!");
        return (byte) BYTE_VH.getAndSet(GLOBAL_MEMORY, address, value);
    }

    public static boolean compareAndSetInt(long address, int expected, int value) {
        if (address == 0L) throw new NullPointerException("Writing to NULL off-heap pointer!");
        return (boolean) INT_VH.compareAndSet(GLOBAL_MEMORY, address, expected, value);
    }

    public static int getAndSetInt(long address, int value) {
        if (address == 0L) throw new NullPointerException("Writing to NULL off-heap pointer!");
        return (int) INT_VH.getAndSet(GLOBAL_MEMORY, address, value);
    }

    public static boolean compareAndSetLong(long address, long expected, long value) {
        if (address == 0L) throw new NullPointerException("Writing to NULL off-heap pointer!");
        return (boolean) LONG_VH.compareAndSet(GLOBAL_MEMORY, address, expected, value);
    }

    public static long getAndSetLong(long address, long value) {
        if (address == 0L) throw new NullPointerException("Writing to NULL off-heap pointer!");
        return (long) LONG_VH.getAndSet(GLOBAL_MEMORY, address, value);
    }

    @Volatile
    public static long getAndAddLong(long address, long delta) {
        if (address == 0L) throw new NullPointerException("Writing to NULL off-heap pointer!");
        return (long) LONG_VH.getAndAdd(GLOBAL_MEMORY, address, delta);
    }

    public static long getAndBitwiseOrLong(long address, long mask) {
        if (address == 0L) throw new NullPointerException("Writing to NULL off-heap pointer!");
        return (long) LONG_VH.getAndBitwiseOr(GLOBAL_MEMORY, address, mask);
    }

    public static long getAndBitwiseAndLong(long address, long mask) {
        if (address == 0L) throw new NullPointerException("Writing to NULL off-heap pointer!");
        return (long) LONG_VH.getAndBitwiseAnd(GLOBAL_MEMORY, address, mask);
    }

    public static boolean compareAndSetFloat(long address, float expected, float value) {
        if (address == 0L) throw new NullPointerException("Writing to NULL off-heap pointer!");
        return (boolean) FLOAT_VH.compareAndSet(GLOBAL_MEMORY, address, expected, value);
    }

    public static float getAndSetFloat(long address, float value) {
        if (address == 0L) throw new NullPointerException("Writing to NULL off-heap pointer!");
        return (float) FLOAT_VH.getAndSet(GLOBAL_MEMORY, address, value);
    }

    public static boolean compareAndSetDouble(long address, double expected, double value) {
        if (address == 0L) throw new NullPointerException("Writing to NULL off-heap pointer!");
        return (boolean) DOUBLE_VH.compareAndSet(GLOBAL_MEMORY, address, expected, value);
    }

    public static double getAndSetDouble(long address, double value) {
        if (address == 0L) throw new NullPointerException("Writing to NULL off-heap pointer!");
        return (double) DOUBLE_VH.getAndSet(GLOBAL_MEMORY, address, value);
    }

    // =========================================================================
    // UNSAFE METHODS (@Unsafe only)
    // =========================================================================

    @Unsafe
    public static byte getUnsafeByte(long address) {
        return GLOBAL_MEMORY.get(ValueLayout.JAVA_BYTE, address);
    }

    @Unsafe
    public static void setUnsafeByte(long address, byte value) {
        GLOBAL_MEMORY.set(ValueLayout.JAVA_BYTE, address, value);
    }

    @Unsafe
    public static void setUnsafe(long address, byte value) {
        setUnsafeByte(address, value);
    }

    @Unsafe
    public static short getUnsafeShort(long address) {
        return GLOBAL_MEMORY.get(ValueLayout.JAVA_SHORT, address);
    }

    @Unsafe
    public static void setUnsafeShort(long address, short value) {
        GLOBAL_MEMORY.set(ValueLayout.JAVA_SHORT, address, value);
    }

    @Unsafe
    public static void setUnsafe(long address, short value) {
        setUnsafeShort(address, value);
    }

    @Unsafe
    public static int getUnsafeInt(long address) {
        return GLOBAL_MEMORY.get(ValueLayout.JAVA_INT, address);
    }

    @Unsafe
    public static void setUnsafeInt(long address, int value) {
        GLOBAL_MEMORY.set(ValueLayout.JAVA_INT, address, value);
    }

    @Unsafe
    public static void setUnsafe(long address, int value) {
        setUnsafeInt(address, value);
    }

    @Unsafe
    public static long getUnsafeLong(long address) {
        return GLOBAL_MEMORY.get(ValueLayout.JAVA_LONG, address);
    }

    @Unsafe
    public static void setUnsafeLong(long address, long value) {
        GLOBAL_MEMORY.set(ValueLayout.JAVA_LONG, address, value);
    }

    @Unsafe
    public static void setUnsafe(long address, long value) {
        setUnsafeLong(address, value);
    }

    @Unsafe
    public static float getUnsafeFloat(long address) {
        return GLOBAL_MEMORY.get(ValueLayout.JAVA_FLOAT, address);
    }

    @Unsafe
    public static void setUnsafeFloat(long address, float value) {
        GLOBAL_MEMORY.set(ValueLayout.JAVA_FLOAT, address, value);
    }

    @Unsafe
    public static void setUnsafe(long address, float value) {
        setUnsafeFloat(address, value);
    }

    @Unsafe
    public static double getUnsafeDouble(long address) {
        return GLOBAL_MEMORY.get(ValueLayout.JAVA_DOUBLE, address);
    }

    @Unsafe
    public static void setUnsafeDouble(long address, double value) {
        GLOBAL_MEMORY.set(ValueLayout.JAVA_DOUBLE, address, value);
    }

    @Unsafe
    public static void setUnsafe(long address, double value) {
        setUnsafeDouble(address, value);
    }

    // =========================================================================
    // VOLATILE METHODS (@Volatile only)
    // =========================================================================

    @Volatile
    public static byte getVolatileByte(long address) {
        if (address == 0L) throw new NullPointerException("Reading from NULL off-heap pointer!");
        return (byte) BYTE_VH.getVolatile(GLOBAL_MEMORY, address);
    }

    @Volatile
    public static void setVolatileByte(long address, byte value) {
        if (address == 0L) throw new NullPointerException("Writing to NULL off-heap pointer!");
        BYTE_VH.setVolatile(GLOBAL_MEMORY, address, value);
    }

    @Volatile
    public static void setVolatile(long address, byte value) {
        setVolatileByte(address, value);
    }

    @Volatile
    public static short getVolatileShort(long address) {
        if (address == 0L) throw new NullPointerException("Reading from NULL off-heap pointer!");
        return (short) SHORT_VH.getVolatile(GLOBAL_MEMORY, address);
    }

    @Volatile
    public static void setVolatileShort(long address, short value) {
        if (address == 0L) throw new NullPointerException("Writing to NULL off-heap pointer!");
        SHORT_VH.setVolatile(GLOBAL_MEMORY, address, value);
    }

    @Volatile
    public static void setVolatile(long address, short value) {
        setVolatileShort(address, value);
    }

    @Volatile
    public static int getVolatileInt(long address) {
        if (address == 0L) throw new NullPointerException("Reading from NULL off-heap pointer!");
        return (int) INT_VH.getVolatile(GLOBAL_MEMORY, address);
    }

    @Volatile
    public static void setVolatileInt(long address, int value) {
        if (address == 0L) throw new NullPointerException("Writing to NULL off-heap pointer!");
        INT_VH.setVolatile(GLOBAL_MEMORY, address, value);
    }

    @Volatile
    public static void setVolatile(long address, int value) {
        setVolatileInt(address, value);
    }

    @Volatile
    public static long getVolatileLong(long address) {
        if (address == 0L) throw new NullPointerException("Reading from NULL off-heap pointer!");
        return (long) LONG_VH.getVolatile(GLOBAL_MEMORY, address);
    }

    @Volatile
    public static void setVolatileLong(long address, long value) {
        if (address == 0L) throw new NullPointerException("Writing to NULL off-heap pointer!");
        LONG_VH.setVolatile(GLOBAL_MEMORY, address, value);
    }

    @Volatile
    public static void setVolatile(long address, long value) {
        setVolatileLong(address, value);
    }

    @Volatile
    public static float getVolatileFloat(long address) {
        if (address == 0L) throw new NullPointerException("Reading from NULL off-heap pointer!");
        return (float) FLOAT_VH.getVolatile(GLOBAL_MEMORY, address);
    }

    @Volatile
    public static void setVolatileFloat(long address, float value) {
        if (address == 0L) throw new NullPointerException("Writing to NULL off-heap pointer!");
        FLOAT_VH.setVolatile(GLOBAL_MEMORY, address, value);
    }

    @Volatile
    public static void setVolatile(long address, float value) {
        setVolatileFloat(address, value);
    }

    @Volatile
    public static double getVolatileDouble(long address) {
        if (address == 0L) throw new NullPointerException("Reading from NULL off-heap pointer!");
        return (double) DOUBLE_VH.getVolatile(GLOBAL_MEMORY, address);
    }

    @Volatile
    public static void setVolatileDouble(long address, double value) {
        if (address == 0L) throw new NullPointerException("Writing to NULL off-heap pointer!");
        DOUBLE_VH.setVolatile(GLOBAL_MEMORY, address, value);
    }

    @Volatile
    public static void setVolatile(long address, double value) {
        setVolatileDouble(address, value);
    }

    // =========================================================================
    // UNSAFE & VOLATILE METHODS (@Unsafe and @Volatile)
    // =========================================================================

    @Unsafe
    @Volatile
    public static byte getUnsafeVolatileByte(long address) {
        return (byte) BYTE_VH.getVolatile(GLOBAL_MEMORY, address);
    }

    @Unsafe
    @Volatile
    public static void setUnsafeVolatileByte(long address, byte value) {
        BYTE_VH.setVolatile(GLOBAL_MEMORY, address, value);
    }

    @Unsafe
    @Volatile
    public static void setUnsafeVolatile(long address, byte value) {
        setUnsafeVolatileByte(address, value);
    }

    @Unsafe
    @Volatile
    public static short getUnsafeVolatileShort(long address) {
        return (short) SHORT_VH.getVolatile(GLOBAL_MEMORY, address);
    }

    @Unsafe
    @Volatile
    public static void setUnsafeVolatileShort(long address, short value) {
        SHORT_VH.setVolatile(GLOBAL_MEMORY, address, value);
    }

    @Unsafe
    @Volatile
    public static void setUnsafeVolatile(long address, short value) {
        setUnsafeVolatileShort(address, value);
    }

    @Unsafe
    @Volatile
    public static int getUnsafeVolatileInt(long address) {
        return (int) INT_VH.getVolatile(GLOBAL_MEMORY, address);
    }

    @Unsafe
    @Volatile
    public static void setUnsafeVolatileInt(long address, int value) {
        INT_VH.setVolatile(GLOBAL_MEMORY, address, value);
    }

    @Unsafe
    @Volatile
    public static void setUnsafeVolatile(long address, int value) {
        setUnsafeVolatileInt(address, value);
    }

    @Unsafe
    @Volatile
    public static long getUnsafeVolatileLong(long address) {
        return (long) LONG_VH.getVolatile(GLOBAL_MEMORY, address);
    }

    @Unsafe
    @Volatile
    public static void setUnsafeVolatileLong(long address, long value) {
        LONG_VH.setVolatile(GLOBAL_MEMORY, address, value);
    }

    @Unsafe
    @Volatile
    public static void setUnsafeVolatile(long address, long value) {
        setUnsafeVolatileLong(address, value);
    }

    @Unsafe
    @Volatile
    public static float getUnsafeVolatileFloat(long address) {
        return (float) FLOAT_VH.getVolatile(GLOBAL_MEMORY, address);
    }

    @Unsafe
    @Volatile
    public static void setUnsafeVolatileFloat(long address, float value) {
        FLOAT_VH.setVolatile(GLOBAL_MEMORY, address, value);
    }

    @Unsafe
    @Volatile
    public static void setUnsafeVolatile(long address, float value) {
        setUnsafeVolatileFloat(address, value);
    }

    @Unsafe
    @Volatile
    public static double getUnsafeVolatileDouble(long address) {
        return (double) DOUBLE_VH.getVolatile(GLOBAL_MEMORY, address);
    }

    @Unsafe
    @Volatile
    public static void setUnsafeVolatileDouble(long address, double value) {
        DOUBLE_VH.setVolatile(GLOBAL_MEMORY, address, value);
    }

    @Unsafe
    @Volatile
    public static void setUnsafeVolatile(long address, double value) {
        setUnsafeVolatileDouble(address, value);
    }

    // =========================================================================
    // FILE I/O BRIDGE (FFM floor, cross-platform)
    // =========================================================================
    // Replicates the java.nio file surface WITHOUT ever crossing the heap:
    // reads land directly in caller-owned native memory via
    // MemorySegment.asByteBuffer() over the destination address; writes depart
    // straight from native memory. Open files are tracked in a fixed 64-slot
    // registry (bitmask free-list) so the handle is a long token, not an object.
    // The only JDK object that survives is the FileChannel itself — the OS
    // handle — which is the unavoidable boundary, same as a raw fd.

    public static final int FILE_MODE_READ = 0x01;
    public static final int FILE_MODE_WRITE = 0x02;
    public static final int FILE_MODE_APPEND = 0x04;
    public static final int FILE_MODE_CREATE = 0x08;
    public static final int FILE_MODE_TRUNCATE = 0x10;

    private static final int FILE_SLOTS = 64;
    private static final FileChannel[] FILE_CHANNELS = new FileChannel[FILE_SLOTS];
    private static final int[] FILE_MODES = new int[FILE_SLOTS];
    private static volatile long fileSlotMask;
    private static final VarHandle FILE_SLOT_MASK_VH;

    static {
        try {
            FILE_SLOT_MASK_VH = MethodHandles.lookup()
                    .findStaticVarHandle(ForeignMemory.class, "fileSlotMask", long.class);
        } catch (ReflectiveOperationException e) {
            throw new ExceptionInInitializerError(e);
        }
    }

    /** Returns a long file handle token (>0), or 0 on failure. Creates parent dirs if CREATE is set. */
    public static long fileOpen(String path, int mode) {
        if (path == null) return 0L;
        int slot = allocFileSlot();
        if (slot < 0) throw new IllegalStateException("File handle registry exhausted (64 open files max)");
        try {
            Path p = Path.of(path);
            if ((mode & FILE_MODE_CREATE) != 0) {
                Path parent = p.getParent();
                if (parent != null && !java.nio.file.Files.isDirectory(parent)) {
                    java.nio.file.Files.createDirectories(parent);
                }
            }
            boolean write = (mode & (FILE_MODE_WRITE | FILE_MODE_APPEND)) != 0;
            boolean append = (mode & FILE_MODE_APPEND) != 0;
            java.util.ArrayList<java.nio.file.OpenOption> opts = new java.util.ArrayList<>();
            if ((mode & FILE_MODE_READ) != 0 || !write) opts.add(java.nio.file.StandardOpenOption.READ);
            if (write) {
                opts.add(java.nio.file.StandardOpenOption.WRITE);
                if ((mode & FILE_MODE_CREATE) != 0) opts.add(java.nio.file.StandardOpenOption.CREATE);
                if ((mode & FILE_MODE_TRUNCATE) != 0) opts.add(java.nio.file.StandardOpenOption.TRUNCATE_EXISTING);
                if (append) opts.add(java.nio.file.StandardOpenOption.APPEND);
            }
            FileChannel ch = FileChannel.open(p, opts.toArray(new java.nio.file.OpenOption[0]));
            FILE_CHANNELS[slot] = ch;
            FILE_MODES[slot] = mode;
            return slot + 1L;
        } catch (Throwable t) {
            freeFileSlot(slot);
            return 0L;
        }
    }

    /** Reads up to len bytes into caller-owned native memory at dest. Returns bytes read, -1 on error. */
    public static long fileRead(long handle, long dest, long len) {
        FileChannel ch = fileChannel(handle);
        if (ch == null || dest == 0L || len <= 0L) return 0L;
        try {
            ByteBuffer buf = MemorySegment.ofAddress(dest).reinterpret((int) len).asByteBuffer();
            return ch.read(buf);
        } catch (Throwable t) {
            return -1L;
        }
    }

    /** Writes len bytes from caller-owned native memory at src. Returns bytes written, -1 on error. */
    public static long fileWrite(long handle, long src, long len) {
        FileChannel ch = fileChannel(handle);
        if (ch == null || src == 0L || len <= 0L) return 0L;
        try {
            ByteBuffer buf = MemorySegment.ofAddress(src).reinterpret((int) len).asByteBuffer();
            return ch.write(buf);
        } catch (Throwable t) {
            return -1L;
        }
    }

    /** Positions the file for the next read/write. Returns true on success. */
    public static boolean fileSeek(long handle, long position) {
        FileChannel ch = fileChannel(handle);
        if (ch == null || position < 0L) return false;
        try {
            ch.position(position);
            return true;
        } catch (Throwable t) {
            return false;
        }
    }

    /** Returns the current byte size of the file, or -1 on error. */
    public static long fileSize(long handle) {
        FileChannel ch = fileChannel(handle);
        if (ch == null) return -1L;
        try {
            return ch.size();
        } catch (Throwable t) {
            return -1L;
        }
    }

    public static boolean fileFlush(long handle) {
        FileChannel ch = fileChannel(handle);
        if (ch == null) return false;
        try {
            ch.force(true);
            return true;
        } catch (Throwable t) {
            return false;
        }
    }

    public static boolean fileClose(long handle) {
        if (handle <= 0L) return false;
        int slot = (int) (handle - 1L);
        if (slot >= FILE_SLOTS) return false;
        FileChannel ch = FILE_CHANNELS[slot];
        if (ch == null) return false;
        try {
            ch.close();
        } catch (Throwable t) {
            // fall through, still release the slot
        }
        FILE_CHANNELS[slot] = null;
        FILE_MODES[slot] = 0;
        freeFileSlot(slot);
        return true;
    }

    // ------------------------------------------------------------------
    // MAPPED FILES
    // ------------------------------------------------------------------

    private static final int MAP_SLOTS = 64;
    private static final MemorySegment[] MAP_SEGMENTS = new MemorySegment[MAP_SLOTS];
    private static final Arena[] MAP_ARENAS = new Arena[MAP_SLOTS];
    private static volatile long mapSlotMask;
    private static final VarHandle MAP_SLOT_MASK_VH;

    static {
        try {
            MAP_SLOT_MASK_VH = MethodHandles.lookup()
                    .findStaticVarHandle(ForeignMemory.class, "mapSlotMask", long.class);
        } catch (ReflectiveOperationException e) {
            throw new ExceptionInInitializerError(e);
        }
    }

    /** Maps [offset, offset+size) of the file into native address space. Returns the base address, or 0 on failure. */
    public static long mapFile(String path, long offset, long size, boolean readOnly) {
        if (path == null || size <= 0L) return 0L;
        int slot = allocMapSlot();
        if (slot < 0) throw new IllegalStateException("Mapped-file registry exhausted (64 maps max)");
        Arena arena = null;
        try {
            arena = Arena.ofConfined();
            FileChannel ch = FileChannel.open(Path.of(path),
                    readOnly
                            ? new java.nio.file.OpenOption[]{java.nio.file.StandardOpenOption.READ}
                            : new java.nio.file.OpenOption[]{java.nio.file.StandardOpenOption.READ,
                                    java.nio.file.StandardOpenOption.WRITE});
            MemorySegment seg;
            try (ch) {
                seg = ch.map(readOnly ? FileChannel.MapMode.READ_ONLY : FileChannel.MapMode.READ_WRITE,
                        offset, size, arena);
            }
            MAP_SEGMENTS[slot] = seg;
            MAP_ARENAS[slot] = arena;
            return seg.address();
        } catch (Throwable t) {
            if (arena != null) {
                try { arena.close(); } catch (Throwable ignored) { }
            }
            freeMapSlot(slot);
            return 0L;
        }
    }

    public static boolean unmapFile(long address) {
        if (address == 0L) return false;
        for (int i = 0; i < MAP_SLOTS; i++) {
            MemorySegment seg = MAP_SEGMENTS[i];
            if (seg != null && seg.address() == address) {
                try {
                    MAP_ARENAS[i].close();
                } catch (Throwable t) {
                    // best effort
                }
                MAP_SEGMENTS[i] = null;
                MAP_ARENAS[i] = null;
                freeMapSlot(i);
                return true;
            }
        }
        return false;
    }

    // ------------------------------------------------------------------
    // helpers
    // ------------------------------------------------------------------

    private static FileChannel fileChannel(long handle) {
        if (handle <= 0L) return null;
        int slot = (int) (handle - 1L);
        if (slot >= FILE_SLOTS) return null;
        return FILE_CHANNELS[slot];
    }

    private static int allocFileSlot() {
        while (true) {
            long mask = fileSlotMask;
            long free = ~mask & ~0L;
            if (free == 0L) return -1;
            int bit = java.lang.Long.numberOfTrailingZeros(free);
            if (FILE_SLOT_MASK_VH.compareAndSet(mask, mask | (1L << bit))) return bit;
        }
    }

    private static void freeFileSlot(int slot) {
        FILE_SLOT_MASK_VH.getAndBitwiseAnd(~(1L << slot));
    }

    private static int allocMapSlot() {
        while (true) {
            long mask = mapSlotMask;
            long free = ~mask & ~0L;
            if (free == 0L) return -1;
            int bit = java.lang.Long.numberOfTrailingZeros(free);
            if (MAP_SLOT_MASK_VH.compareAndSet(mask, mask | (1L << bit))) return bit;
        }
    }

    private static void freeMapSlot(int slot) {
        MAP_SLOT_MASK_VH.getAndBitwiseAnd(~(1L << slot));
    }

    // =========================================================================
    // GARBAGE COLLECTION TRIGGERS
    // =========================================================================

    // nuclear, dangerous
    @HotCode
    @Intention("the garbage collection to end the actual application")
    public static void freeAllClasses() {
        // Stop/join worker threads first: their teardown drains RingBuffer/Map/Array
        // which are freed below.
        DrawThread.freeAllSystem();
        NetworkingThread.freeAllSystem();
        ConsoleThread.freeAllSystem();
        ScriptingThread.freeAllSystem();

        // Close subsystem pool arenas.
        AudioBuffer.freeAll();
        AudioBufferLayer.freeAll();
        AudioSource.freeAll();
        Sampler.freeAll();
        AudioComputeBuffer.freeAll();
        Buffer.freeAll();
        CommandBuffer.freeAll();
        CommandPool.freeAll();
        Fence.freeAll();
        RenderPass.freeAll();
        Semaphore.freeAll();
        Swapchain.freeAll();
        VKBuffer.freeAll();
        VKDeviceMemory.freeAll();
        VKFramebuffer.freeAll();
        VKImage.freeAll();
        VKImageView.freeAll();
        VKTexture.freeAll();
        VKPipeline.freeAll();
        VKPipelineLayout.freeAll();
        VKShaderModule.freeAll();

        darling.Container.freeAll();
        darling.Panel.freeAll();
        darling.Picture.freeAll();
        image.Image.freeAll();

        Byte.freeAll();
        Short.freeAll();
        Int.freeAll();
        Long.freeAll();
        Float.freeAll();
        Double.freeAll();
        Bool.freeAll();
        IntFloat.freeAll();
        IntDouble.freeAll();
        LongFloat.freeAll();
        LongDouble.freeAll();
        Brain.freeAll();
        Fixed32.freeAll();
        Fixed64.freeAll();
        string.freeAll();
        Struct.freeAll();

        Variable.freeAllClasses();
        SearchVariable.freeAll();
        List.freeAll();
        Array.freeAll();
        Deque.freeAll();
        Stack.freeAll();
        Queue.freeAll();
        Map.freeAll();
        Set.freeAll();
        Trie.freeAll();
        GridArray.freeAll();
        CircularArray.freeAll();
        RingBuffer.freeAll();
    }
}