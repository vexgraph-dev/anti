package util;

import annotation.Draft;
import annotation.Required;
import annotation.Intention;
import nio.ForeignMemory;
import oop.TypeRegister;

/**
 * Off-Heap Pseudo-Random Number Generator (PRNG) Subsystem.
 * Provides state-updating PRNG operations.
 */
@Draft
@Intention("Zero-allocation off-heap PRNG using mixed chaotic hash finalizers (Murmur3) on mutable sequence counter state.")
public final class Random {

    @Required
    public static final int CLASS_ID = TypeRegister.ID_RANDOM;

    // 64-bit golden ratio fractional constant (Weyl sequence step) for balanced/even dispersion
    private static final long GOLDEN_RATIO_64 = 0x9e3779b97f4a7c15L;

    private Random() {}

    public static int classId() {
        return CLASS_ID;
    }

    /**
     * Allocates a new off-heap Random state block.
     * Block layout:
     *   [userPtr - 8]: 32-bit packed Type ID
     *   [userPtr - 4]: 32-bit length (1)
     *   [userPtr]    : 64-bit seed / state
     *   [userPtr + 8]: 32-bit current integer counter
     *   [userPtr + 12]: 32-bit padding
     *
     * @param seed the initial 64-bit seed
     * @return the raw memory address pointer of the user payload state
     */
    @Draft
    public static long allocate(long seed) {
        long block = ForeignMemory.allocateNative(24);
        long userPtr = block + 8L;

        // Write class metadata headers
        ForeignMemory.putInt(block, TypeRegister.RANDOM_SINGLETON);
        ForeignMemory.putInt(block + 4L, 1);

        // Write initial state values
        ForeignMemory.putLong(userPtr, seed);
        ForeignMemory.putInt(userPtr + 8L, 0);
        ForeignMemory.putInt(userPtr + 12L, 0);

        return userPtr;
    }

    /**
     * Frees the allocated native memory block for the given Random state pointer.
     *
     * @param ptr the raw memory address pointer returned by allocate
     */
    @Draft
    public static void free(long ptr) {
        if (ptr == 0L) return;
        ForeignMemory.freeNative(ptr - 8L);
    }

    // ==========================================
    // STATE-BASED MUTATING GENERATION PIPELINE
    // ==========================================

    @Draft
    public static long nextLong(long ptr) {
        if (ptr == 0L) throw new NullPointerException("Accessing NULL off-heap pointer!");
        
        long seed = ForeignMemory.getLong(ptr);
        int currentInteger = ForeignMemory.getInt(ptr + 8L);

        // Advance seed/state using chaotic Murmur3 mix and sequence index
        long mixed = seed ^ ptr ^ ((long) currentInteger * GOLDEN_RATIO_64);
        long result = util.Hash.murmur3Mix64(mixed);

        // Commit updated state back to off-heap memory
        ForeignMemory.putLong(ptr, result);
        ForeignMemory.putInt(ptr + 8L, currentInteger + 1);

        return result;
    }

    @Draft
    public static int nextInt(long ptr) {
        return (int) nextLong(ptr);
    }

    @Draft
    public static float nextFloat(long ptr) {
        return (nextLong(ptr) & 0xFFFFFF) / 16777216.0f;
    }

    @Draft
    public static double nextDouble(long ptr) {
        return (nextLong(ptr) & 0x1FFFFFFFFFFFFFL) / 9007199254740992.0;
    }

    @Draft
    public static float nextNDCFloat(long ptr) {
        return ((nextLong(ptr) & 0xFFFFFF) / 8388608.0f) - 1.0f;
    }

    @Draft
    public static char nextChar(long ptr) {
        long val = nextLong(ptr);
        return (char) (32 + (Math.abs(val) % 95)); // Printable ASCII: space (32) to tilde (126)
    }
}
