package util;

import annotation.Draft;
import annotation.Required;
import annotation.Intention;
import nio.ForeignMemory;
import oop.TypeRegister;
import objects.Probable;
import objects.ProbableObjects;

import nio.StringLookup;
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

    private static final long SYSTEM_RNG = allocate(System.nanoTime());

    public static long getSystemRng() {
        return SYSTEM_RNG;
    }

    private Random() {}

    public static int classId() {
        return CLASS_ID;
    }

    /**
     * Allocates a new off-heap Random state block.
     * Block layout:
     *   [userPtr - 8]: 32-bit packed Type ID
     *   [userPtr - 4]: 32-bit length (1)
     *   [userPtr + 0]: 64-bit seed / state
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
        ForeignMemory.setInt(block, TypeRegister.RANDOM_SINGLETON);
        ForeignMemory.setInt(block + 4L, 1);

        // Write initial state values
        ForeignMemory.setLong(userPtr, seed);
        ForeignMemory.setInt(userPtr + 8L, 0);
        ForeignMemory.setInt(userPtr + 12L, 0);

        return userPtr;
    }

    /**
     * Frees the allocated native memory block for the given Random state pointer.
     *
     * @param ptr the raw memory address pointer returned by allocate
     */
    @Draft
    public static void free(long ptr) {
        ForeignMemory.freeNative(ptr - 8L);
    }

    // ==========================================
    // STATE-BASED MUTATING GENERATION PIPELINE
    // ==========================================

    @Draft
    public static long nextLong(long ptr) {
        if (ptr == 0L) throw new NullPointerException(StringLookup.getJavaString(27));
        
        long seed = ForeignMemory.getLong(ptr);
        int currentInteger = ForeignMemory.getInt(ptr + 8L);

        // Advance seed/state using chaotic Murmur3 mix and sequence index
        long mixed = seed ^ ptr ^ ((long) currentInteger * GOLDEN_RATIO_64);
        long result = util.Hash.murmur3Mix64(mixed);

        // Commit updated state back to off-heap memory
        ForeignMemory.setLong(ptr, result);
        ForeignMemory.setInt(ptr + 8L, currentInteger + 1);

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

    @Draft
    public static boolean getWeight(long ptr, int weight, int total) {
        if (ptr == 0L) throw new NullPointerException(StringLookup.getJavaString(27));
        if (total <= 0) return false;
        if (weight >= total) return true;
        if (weight <= 0) return false;
        long val = nextLong(ptr) & 0x7FFFFFFFFFFFFFFFL;
        return (val % total) < weight;
    }

    @Draft
    public static long sample(long ptr, long probablePtr) {
        if (probablePtr == 0L) throw new NullPointerException(StringLookup.getJavaString(27));
        int weight = Probable.getWeight(probablePtr);
        int total = Probable.getTotal(probablePtr);
        if (getWeight(ptr, weight, total)) {
            return Probable.getObject(probablePtr);
        }
        return 0L;
    }

    @Draft
    public static long probablePool(long ptr, long probableObjectsPtr) {
        if (probableObjectsPtr == 0L) throw new NullPointerException(StringLookup.getJavaString(27));
        int count = ProbableObjects.size(probableObjectsPtr);
        if (count == 0) return 0L;

        int totalWeight = ProbableObjects.getTotalWeight(probableObjectsPtr);
        if (totalWeight <= 0) {
            long val = nextLong(ptr) & 0x7FFFFFFFFFFFFFFFL;
            int idx = (int) (val % count);
            long slotBase = probableObjectsPtr + 8L + (idx * 16L);
            return ForeignMemory.getLong(slotBase);
        }

        long val = nextLong(ptr) & 0x7FFFFFFFFFFFFFFFL;
        int r = (int) (val % totalWeight);

        // Binary search cumulative weights
        int low = 0;
        int high = count - 1;
        while (low < high) {
            int mid = (low + high) >>> 1;
            long slotBase = probableObjectsPtr + 8L + (mid * 16L);
            int cumWeight = ForeignMemory.getInt(slotBase + 8L);
            if (cumWeight < r) {
                low = mid + 1;
            } else {
                high = mid;
            }
        }

        long slotBase = probableObjectsPtr + 8L + (low * 16L);
        return ForeignMemory.getLong(slotBase);
    }

    // ==========================================
    // PARAMETERLESS SYSTEM_RNG HELPER OVERLOADS
    // ==========================================

    public static long nextLong() {
        return nextLong(SYSTEM_RNG);
    }

    public static int nextInt() {
        return nextInt(SYSTEM_RNG);
    }

    public static float nextFloat() {
        return nextFloat(SYSTEM_RNG);
    }

    public static double nextDouble() {
        return nextDouble(SYSTEM_RNG);
    }

    public static float nextNDCFloat() {
        return nextNDCFloat(SYSTEM_RNG);
    }

    public static char nextChar() {
        return nextChar(SYSTEM_RNG);
    }

    public static boolean getWeight(int weight, int total) {
        return getWeight(SYSTEM_RNG, weight, total);
    }

    public static long sample(long probablePtr) {
        return sample(SYSTEM_RNG, probablePtr);
    }

    public static long probablePool(long probableObjectsPtr) {
        return probablePool(SYSTEM_RNG, probableObjectsPtr);
    }
}
