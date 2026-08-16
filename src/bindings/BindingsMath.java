package bindings;

import annotation.Draft;
import annotation.Intention;
import lang.FastMath;
import lang.Vec2;
import lang.Vec4;
import primitive.Arguments;
import primitive.Int;

/**
 * Math language-pack adapters. Every method here is a uniform bound method:
 *
 * <pre>
 *   long fn(long argsPtr, long argCount)
 * </pre>
 *
 * Arguments are read through {@link Arguments} (off-heap, zero allocation).
 * Return values are normalized to a raw {@code long}: floats return their raw
 * bit pattern, bools return 0/1, pointers return the address.
 */
@Draft
@Intention("Uniform-ABI language-pack adapters over existing math primitives: long fn(long argsPtr, long argCount).")
public final class BindingsMath {

    private BindingsMath() {}

    // --- arithmetic (two raw longs in, raw long out) ---

    public static long add(long argsPtr, long argCount) {
        return Arguments.get(argsPtr, 0) + Arguments.get(argsPtr, 1);
    }

    public static long sub(long argsPtr, long argCount) {
        return Arguments.get(argsPtr, 0) - Arguments.get(argsPtr, 1);
    }

    public static long mul(long argsPtr, long argCount) {
        return Arguments.get(argsPtr, 0) * Arguments.get(argsPtr, 1);
    }

    public static long div(long argsPtr, long argCount) {
        long b = Arguments.get(argsPtr, 1);
        if (b == 0L) return 0L;
        return Arguments.get(argsPtr, 0) / b;
    }

    public static long max(long argsPtr, long argCount) {
        return java.lang.Math.max(Arguments.get(argsPtr, 0), Arguments.get(argsPtr, 1));
    }

    public static long min(long argsPtr, long argCount) {
        return java.lang.Math.min(Arguments.get(argsPtr, 0), Arguments.get(argsPtr, 1));
    }

    // --- float maths (float in as raw bits, raw bits out) ---

    /** abs of a float. */
    public static long abs(long argsPtr, long argCount) {
        float v = Arguments.getFloat(argsPtr, 0);
        return Integer.toUnsignedLong(Float.floatToRawIntBits(FastMath.abs(v)));
    }

    /** length of a Vec2 (ptr in, float out as raw bits). */
    public static long vec2Length(long argsPtr, long argCount) {
        float len = Vec2.length(Arguments.getPointer(argsPtr, 0));
        return Integer.toUnsignedLong(Float.floatToRawIntBits(len));
    }

    /** length of a Vec4 (ptr in, float out as raw bits). */
    public static long vec4Length(long argsPtr, long argCount) {
        float len = Vec4.length(Arguments.getPointer(argsPtr, 0));
        return Integer.toUnsignedLong(Float.floatToRawIntBits(len));
    }

    // --- int access (ptr in, int out) ---

    /** Returns primitive.Int value at the given pointer as a raw long. */
    public static long intGet(long argsPtr, long argCount) {
        long p = Arguments.getPointer(argsPtr, 0);
        return p == 0L ? 0L : Integer.toUnsignedLong(Int.get(p));
    }

    /** Stores a value into a primitive.Int; returns the pointer. */
    public static long intSet(long argsPtr, long argCount) {
        long p = Arguments.getPointer(argsPtr, 0);
        if (p == 0L) throw new NullPointerException("int.set on NULL primitive.Int pointer!");
        Int.set(p, (int) Arguments.get(argsPtr, 1));
        return p;
    }
}