#include "lang/fastmath.h"

#include <math.h>
#include <string.h>

// fastmath.c — FastMath port (Legacy: lang/FastMath.java). Pure functions.

static const float INV_PI2 = 0.15915494f;   // 1 / (2*PI)
static const float B = 1.27323954f;          // 4 / PI
static const float C = -0.40528473f;         // -4 / (PI^2)
static const float P = 0.225f;               // precision weight

static uint32_t raw_bits(float x) {
    uint32_t bits;
    memcpy(&bits, &x, sizeof(bits));
    return bits;
}

static float from_bits(uint32_t bits) {
    float x;
    memcpy(&x, &bits, sizeof(x));
    return x;
}

float FastMath_abs(float x) {
    return from_bits(raw_bits(x) & 0x7FFFFFFFu);
}

int32_t FastMath_absInt(int32_t n) {
    const uint32_t u = (uint32_t)n;
    const uint32_t mask = (uint32_t)(n >> 31);
    return (int32_t)((u ^ mask) - mask);
}

float FastMath_round(float x) {
    return (float)((int32_t)(x + 16384.5f) - 16384);
}

float FastMath_invSqrt(float x) {
    float half_x = 0.5f * x;
    uint32_t i = raw_bits(x);
    i = 0x5f3759dfu - (i >> 1);
    x = from_bits(i);
    x = x * (1.5f - (half_x * x * x));
    return x;
}

float FastMath_sin32(float x) {
    x = x - FastMath_TWO_PI * FastMath_round(x * INV_PI2);
    const float abs_x = FastMath_abs(x);
    float y = B * x + C * x * abs_x;
    const float abs_y = FastMath_abs(y);
    return P * (y * abs_y - y) + y;
}

float FastMath_cos32(float x) {
    x = x + FastMath_HALF_PI;
    x = x - FastMath_TWO_PI * FastMath_round(x * INV_PI2);
    float abs_x = FastMath_abs(x);
    float y = B * x + C * x * abs_x;
    float abs_y = FastMath_abs(y);
    return P * (y * abs_y - y) + y;
}

float FastMath_tan32(float x) {
    float x_sin = x - FastMath_TWO_PI * FastMath_round(x * INV_PI2);
    float abs_x_sin = FastMath_abs(x_sin);
    float y_sin = B * x_sin + C * x_sin * abs_x_sin;
    float sin = P * (y_sin * FastMath_abs(y_sin) - y_sin) + y_sin;

    float x_cos = x + FastMath_HALF_PI;
    x_cos = x_cos - FastMath_TWO_PI * FastMath_round(x_cos * INV_PI2);
    float abs_x_cos = FastMath_abs(x_cos);
    float y_cos = B * x_cos + C * x_cos * abs_x_cos;
    float cos = P * (y_cos * FastMath_abs(y_cos) - y_cos) + y_cos;

    return sin / cos;
}

float FastMath_toRadians(float degrees) {
    return degrees * FastMath_DEG_TO_RAD;
}

float FastMath_toDegrees(float radians) {
    return radians * FastMath_RAD_TO_DEG;
}

float FastMath_pow(float base, float exponent) {
    return powf(base, exponent);
}

float FastMath_clamp(float val, float min, float max) {
    if (val < min)
        return min;
    if (val > max)
        return max;
    return val;
}

float FastMath_cosFromSin(float sin, float angle) {
    int quadrant = sin >= 0 ? (angle >= 0 ? 1 : 4) : (angle >= 0 ? 2 : 3);
    float cos_sq = 1.0f - sin * sin;
    float root = sqrtf(cos_sq);
    return (quadrant == 1 || quadrant == 4) ? root : -root;
}