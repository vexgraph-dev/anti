package lang;

/**
 * High-performance, 32-bit polynomial math approximations and bitwise operations.
 * Replaces Lookup Tables (LUTs) to avoid L1 Cache Misses and JVM Math calls.
 * Uses the Bhaskara I approximation with an extra precision pass and Quake III invSqrt.
 */
public final class FastMath {

    public static final float PI = 3.1415927f;
    public static final float HALF_PI = 1.5707964f;
    public static final float TWO_PI = 6.2831855f;
    public static final float PI2 = 6.2831855f;
    private static final float INV_PI2 = 0.15915494f;
    public static final float EPSILON = 0.000002f;

    // Polynomial constants
    private static final float B = 1.27323954f;  // 4 / PI
    private static final float C = -0.40528473f; // -4 / (PI^2)
    private static final float P = 0.225f;       // Precision weight

    // Precalculated multipliers to convert without division
    public static final float DEG_TO_RAD = 0.0174532925f; // PI / 180.0f
    public static final float RAD_TO_DEG = 57.2957795f;   // 180.0f / PI

    private FastMath() {}

    /**
     * Branchless Absolute Value for floats using IEEE 754 bit masking.
     */
    public static float abs(float x) {
        return Float.intBitsToFloat(Float.floatToRawIntBits(x) & 0x7FFFFFFF);
    }

    /**
     * Branchless Absolute Value for integers.
     */
    public static int abs(int n) {
        return (n ^ (n >> 31)) - (n >> 31);
    }

    /**
     * Fast Rounding using the 16384 magic float trick.
     * Returns float to avoid cast-back latency in math expressions.
     */
    public static float round(float x) {
        return (float) ((int) (x + 16384.5f) - 16384);
    }

    /**
     * Fast Inverse Square Root (The Quake III 0x5f3759df magic constant).
     * Computes 1 / sqrt(x) with high precision in a single Newton-Raphson iteration.
     */
    public static float invSqrt(float x) {
        float halfx = 0.5f * x;
        int i = Float.floatToRawIntBits(x);
        i = 0x5f3759df - (i >> 1);
        x = Float.intBitsToFloat(i);
        x = x * (1.5f - (halfx * x * x));
        return x;
    }

    /**
     * Fast Sine approximation (Bhaskara I with precision pass).
     */
    public static float sin32(float x) {
        x = x - PI2 * round(x * INV_PI2);
        final float absX = abs(x);
        float y = B * x + C * x * absX;
        float absY = abs(y);
        return P * (y * absY - y) + y;
    }

    /**
     * Fast Cosine approximation.
     */
    public static float cos32(float x) {
        x = x + HALF_PI;
        x = x - PI2 * round(x * INV_PI2);
        float absX = abs(x);
        float y = B * x + C * x * absX;
        float absY = abs(y);
        return P * (y * absY - y) + y;
    }

    /**
     * Fast Tangent: Computes sine and cosine in a single pass.
     */
    public static float tan32(float x) {
        float xSin = x - PI2 * round(x * INV_PI2);
        float absXSin = abs(xSin);
        float ySin = B * xSin + C * xSin * absXSin;
        float sin = P * (ySin * abs(ySin) - ySin) + ySin;

        float xCos = x + HALF_PI;
        xCos = xCos - PI2 * round(xCos * INV_PI2);
        float absXCos = abs(xCos);
        float yCos = B * xCos + C * xCos * absXCos;
        float cos = P * (yCos * abs(yCos) - yCos) + yCos;

        return sin / cos;
    }

    public static float toRadians(float degrees) {
        return degrees * DEG_TO_RAD;
    }

    public static float toDegrees(float radians) {
        return radians * RAD_TO_DEG;
    }

    public static float pow(float base, float exponent) {
        return (float) Math.pow(base, exponent);
    }

    public static float clamp(float val, float min, float max) {
        return Math.clamp(val, min, max);
    }

    public static float cosFromSin(float sin, float angle) {
        int quadrant = sin >= 0 ? (angle >= 0 ? 1 : 4) : (angle >= 0 ? 2 : 3);
        float cosSquared = 1.0f - sin * sin;
        return (float) (Math.sqrt(cosSquared) * (quadrant == 1 || quadrant == 4 ? 1 : -1));
    }
}
