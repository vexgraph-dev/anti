package primitive;

import annotation.Draft;
import annotation.Intention;
import annotation.Required;
import annotation.Unsafe;
import nio.ForeignMemory;
import oop.TypeRegister;
import thread.ThreadRegistry;

/**
 * Off-heap argument list for the bindings / scripting language pack.
 *
 * <p>A raw {@code long} pointer to a flat, self-describing off-heap buffer used as the
 * uniform calling convention for every bound method in the method directory:
 *
 * <pre>
 *   [ptr - 8] typeId      (ARGUMENTS_SINGLETON)
 *   [ptr - 4] capacity    (max number of long argument slots)
 *   [ptr + 0] count       (current number of packed arguments)
 *   [ptr + 4] reserved
 *   [ptr + 8] arg0        (8 bytes each, 8-aligned)
 *   [ptr + 16] arg1
 *   ...
 * </pre>
 *
 * Every value crossing the boundary is normalized to a raw {@code long}: ints and floats
 * are bitcast (zero-extended), doubles use their raw bits, bools are 0/1, pointers are
 * just the address. This is what makes the whole method table uniform —
 * {@code long fn(long argsPtr, long argCount)} — with zero heap allocation per call.
 */
@Draft
@Intention("Off-heap uniform-ABI argument transport for the bindings method directory: a flat long stream behind a raw pointer, pure static view (no allocation per call) plus a pooled per-thread reusable buffer.")
public final class Arguments {

    @Required
    public static final int CLASS_ID = TypeRegister.ID_ARGUMENTS;

    public static final int TYPE_SINGLETON = TypeRegister.ARGUMENTS_SINGLETON;
    public static final int TYPE_ARRAY     = TypeRegister.ARGUMENTS_ARRAY;
    public static final int TYPE_POINTER   = TypeRegister.ARGUMENTS_POINTER;

    public static final int STRIDE = 8;         // every argument slot is one long

    private static final long COUNT_OFFSET = 0L;
    private static final long DATA_OFFSET  = 8L; // args start after the count slot

    public static final int DEFAULT_CAPACITY = 64;

    private static final int THREAD_SLOTS = 256;
    private static final long SLOT_BYTES = DATA_OFFSET + (long) DEFAULT_CAPACITY * STRIDE;

    private static final long CACHE_ARENA_BASE;

    private static volatile boolean active;

    static {
        CACHE_ARENA_BASE = ForeignMemory.allocateNative(8L + (long) THREAD_SLOTS * SLOT_BYTES);
        ForeignMemory.setMemory(CACHE_ARENA_BASE, 8L + (long) THREAD_SLOTS * SLOT_BYTES, (byte) 0);
        active = true;
    }

    private Arguments() {}

    public static int classId() {
        return CLASS_ID;
    }

    public static void freeAll() {
        if (active) {
            active = false;
            if (CACHE_ARENA_BASE != 0L) {
                ForeignMemory.freeNative(CACHE_ARENA_BASE);
            }
        }
    }

    // =========================================================================
    // ALLOCATION (self-describing header, like every engine primitive)
    // =========================================================================

    /** Allocates a fresh off-heap argument buffer with the given capacity. */
    public static long allocate(int capacity) {
        if (capacity <= 0) throw new IllegalArgumentException("Argument capacity must be positive!");
        long bytes = 8L + DATA_OFFSET + (long) capacity * STRIDE;
        long block = ForeignMemory.allocateNative(bytes);
        long userPtr = block + 8L;

        ForeignMemory.setInt(block, TYPE_SINGLETON);
        ForeignMemory.setInt(block + 4L, capacity);
        ForeignMemory.setInt(userPtr + COUNT_OFFSET, 0);
        ForeignMemory.setInt(userPtr + COUNT_OFFSET + 4L, 0);

        return userPtr;
    }

    /** Frees an argument buffer allocated by {@link #allocate(int)}. */
    public static void free(long userPtr) {
        if (userPtr == 0L) return;
        if (userPtr == CACHE_ARENA_BASE) throw new IllegalStateException("Cannot free the pooled thread args buffer!");
        int type = ForeignMemory.getInt(userPtr - 8L);
        if (type == 0 || (!TypeRegister.isSingleton(type) && !TypeRegister.isArray(type) && !TypeRegister.isPointer(type))) {
            throw new IllegalStateException("Double free or corrupt arguments pointer: 0x" + java.lang.Long.toHexString(userPtr).toUpperCase());
        }
        ForeignMemory.setInt(userPtr - 8L, 0);
        ForeignMemory.setInt(userPtr - 4L, -1);
        ForeignMemory.freeNative(userPtr - 8L);
    }

    // =========================================================================
    // POOLED PER-THREAD BUFFER (reused, zero per-call allocation)
    // =========================================================================

    /** Returns the caller thread's reusable argument buffer, reset to zero args. */
    public static long threadArgs() {
        int idx = ThreadRegistry.getThreadIndex();
        long slot = CACHE_ARENA_BASE + 8L + (long) idx * SLOT_BYTES;
        ForeignMemory.setInt(slot - 8L, TYPE_SINGLETON);
        ForeignMemory.setInt(slot - 4L, DEFAULT_CAPACITY);
        ForeignMemory.setInt(slot + COUNT_OFFSET, 0);
        ForeignMemory.setInt(slot + COUNT_OFFSET + 4L, 0);
        return slot;
    }

    // =========================================================================
    // HEADER METADATA
    // =========================================================================

    public static int type(long userPtr) {
        return ForeignMemory.getUnsafeInt(userPtr - 8L);
    }

    /** Max number of packed argument slots (from the self-describing header). */
    public static int capacity(long userPtr) {
        return ForeignMemory.getUnsafeInt(userPtr - 4L);
    }

    /** Number of currently packed arguments. */
    public static int count(long userPtr) {
        return ForeignMemory.getInt(userPtr + COUNT_OFFSET);
    }

    public static void setCount(long userPtr, int n) {
        if (n < 0 || n > capacity(userPtr)) {
            throw new IndexOutOfBoundsException("Argument count " + n + " out of capacity " + capacity(userPtr));
        }
        ForeignMemory.setInt(userPtr + COUNT_OFFSET, n);
    }

    public static void reset(long userPtr) {
        ForeignMemory.setInt(userPtr + COUNT_OFFSET, 0);
    }

    private static long argAddress(long userPtr, int index) {
        return userPtr + DATA_OFFSET + (long) index * STRIDE;
    }

    private static void checkBounds(long userPtr, int index) {
        if (userPtr == 0L) throw new NullPointerException("Accessing NULL off-heap arguments pointer!");
        int cap = capacity(userPtr);
        if (index < 0 || index >= cap) {
            throw new IndexOutOfBoundsException("Argument index " + index + " out of capacity " + cap);
        }
    }

    // =========================================================================
    // 1. SAFE ACCESSORS (bounds checked)
    // =========================================================================

    public static long get(long userPtr, int index) {
        checkBounds(userPtr, index);
        return ForeignMemory.getLong(argAddress(userPtr, index));
    }

    public static int getInt(long userPtr, int index) {
        return (int) get(userPtr, index);
    }

    public static float getFloat(long userPtr, int index) {
        return java.lang.Float.intBitsToFloat((int) get(userPtr, index));
    }

    public static double getDouble(long userPtr, int index) {
        return java.lang.Double.longBitsToDouble(get(userPtr, index));
    }

    public static boolean getBool(long userPtr, int index) {
        return get(userPtr, index) != 0L;
    }

    public static long getPointer(long userPtr, int index) {
        return get(userPtr, index);
    }

    public static void set(long userPtr, int index, long value) {
        checkBounds(userPtr, index);
        ForeignMemory.setLong(argAddress(userPtr, index), value);
    }

    public static void setInt(long userPtr, int index, int value) {
        set(userPtr, index, java.lang.Integer.toUnsignedLong(value));
    }

    public static void setFloat(long userPtr, int index, float value) {
        set(userPtr, index, java.lang.Integer.toUnsignedLong(java.lang.Float.floatToRawIntBits(value)));
    }

    public static void setDouble(long userPtr, int index, double value) {
        set(userPtr, index, java.lang.Double.doubleToRawLongBits(value));
    }

    public static void setBool(long userPtr, int index, boolean value) {
        set(userPtr, index, value ? 1L : 0L);
    }

    public static void setPointer(long userPtr, int index, long target) {
        set(userPtr, index, target);
    }

    // =========================================================================
    // 2. UNSAFE ACCESSORS (no checks, maximum speed)
    // =========================================================================

    @Unsafe
    public static long getUnsafe(long userPtr, int index) {
        return ForeignMemory.getUnsafeLong(argAddress(userPtr, index));
    }

    @Unsafe
    public static int getUnsafeInt(long userPtr, int index) {
        return (int) getUnsafe(userPtr, index);
    }

    @Unsafe
    public static float getUnsafeFloat(long userPtr, int index) {
        return java.lang.Float.intBitsToFloat((int) getUnsafe(userPtr, index));
    }

    @Unsafe
    public static double getUnsafeDouble(long userPtr, int index) {
        return java.lang.Double.longBitsToDouble(getUnsafe(userPtr, index));
    }

    @Unsafe
    public static boolean getUnsafeBool(long userPtr, int index) {
        return getUnsafe(userPtr, index) != 0L;
    }

    @Unsafe
    public static void setUnsafe(long userPtr, int index, long value) {
        ForeignMemory.setUnsafeLong(argAddress(userPtr, index), value);
    }

    @Unsafe
    public static void setUnsafeInt(long userPtr, int index, int value) {
        setUnsafe(userPtr, index, java.lang.Integer.toUnsignedLong(value));
    }

    @Unsafe
    public static void setUnsafeFloat(long userPtr, int index, float value) {
        setUnsafe(userPtr, index, java.lang.Integer.toUnsignedLong(java.lang.Float.floatToRawIntBits(value)));
    }

    @Unsafe
    public static void setUnsafeDouble(long userPtr, int index, double value) {
        setUnsafe(userPtr, index, java.lang.Double.doubleToRawLongBits(value));
    }

    @Unsafe
    public static void setUnsafeBool(long userPtr, int index, boolean value) {
        setUnsafe(userPtr, index, value ? 1L : 0L);
    }
}