package util;

import annotation.Draft;
import annotation.Required;
import annotation.Intention;
import nio.ForeignMemory;
import oop.TypeRegister;

/**
 * Off-Heap Stateless Deterministic Index-Based PRNG Subsystem.
 * Computes deterministic pseudo-random values using (ptr, index) contexts
 * and fractal noise summation of octaves.
 */
@Draft
@Intention("Zero-allocation off-heap stateless PRNG mapping deterministic seed/ptr contexts and sequence indices to pseudo-random numbers.")
public final class
IndexRandom {

    @Required
    public static final int CLASS_ID = TypeRegister.ID_INDEX_RANDOM;

    // 64-bit golden ratio fractional constant (Weyl sequence step) for balanced/even dispersion
    private static final long GOLDEN_RATIO_64 = 0x9e3779b97f4a7c15L;

    private IndexRandom() {}

    public static int classId() {
        return CLASS_ID;
    }

    private static long deterministicHash(long seed, long ptr, int index) {
        long mixed = seed ^ ptr ^ ((long) index * GOLDEN_RATIO_64);
        return util.Hash.murmur3Mix64(mixed);
    }

    @Draft
    public static long nextLong(long ptr, int index) {
        if (ptr == 0L) throw new NullPointerException("Accessing NULL off-heap pointer!");
        long seed = ForeignMemory.getLong(ptr);
        return deterministicHash(seed, ptr, index);
    }

    @Draft
    public static int nextInt(long ptr, int index) {
        return (int) nextLong(ptr, index);
    }

    @Draft
    public static float nextFloat(long ptr, int index) {
        return (nextLong(ptr, index) & 0xFFFFFF) / 16777216.0f;
    }

    @Draft
    public static double nextDouble(long ptr, int index) {
        return (nextLong(ptr, index) & 0x1FFFFFFFFFFFFFL) / 9007199254740992.0;
    }

    @Draft
    public static float nextNDCFloat(long ptr, int index) {
        return ((nextLong(ptr, index) & 0xFFFFFF) / 8388608.0f) - 1.0f;
    }

    @Draft
    public static char nextChar(long ptr, int index) {
        long val = nextLong(ptr, index);
        return (char) (32 + (Math.abs(val) % 95)); // Printable ASCII: space (32) to tilde (126)
    }

    @Draft
    public static float nextFractalFloat(long ptr, int index) {
        if (ptr == 0L) throw new NullPointerException("Accessing NULL off-heap pointer!");
        long seed = ForeignMemory.getLong(ptr);
        float value = 0.0f;
        float amplitude = 1.0f;
        float totalAmplitude = 0.0f;
        int freq = 1;
        for (int i = 0; i < 4; i++) {
            long mixed = deterministicHash(seed, ptr, index * freq);
            float h = (mixed & 0xFFFFFF) / 16777216.0f;
            value += h * amplitude;
            totalAmplitude += amplitude;
            amplitude *= 0.5f;
            freq *= 2;
        }
        return value / totalAmplitude;
    }

    @Draft
    public static double nextFractalDouble(long ptr, int index) {
        if (ptr == 0L)
            throw new NullPointerException("Accessing NULL off-heap pointer!");
        long seed = ForeignMemory.getLong(ptr);
        double value = 0.0;
        double amplitude = 1.0;
        double totalAmplitude = 0.0;
        int freq = 1;
        for (int i = 0; i < 4; i++) {
            long mixed = deterministicHash(seed, ptr, index * freq);
            double h = (mixed & 0x1FFFFFFFFFFFFFL) / 9007199254740992.0;
            value += h * amplitude;
            totalAmplitude += amplitude;
            amplitude *= 0.5;
            freq *= 2;
        }
        return value / totalAmplitude;
    }

    @Draft
    public static float nextFractalNDCFloat(long ptr, int index) {
        return (nextFractalFloat(ptr, index) * 2.0f) - 1.0f;
    }
}
