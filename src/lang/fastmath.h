#ifndef LANG_FASTMATH_H
#define LANG_FASTMATH_H

#include <stdint.h>

// lang/fastmath.h — the FastMath class, ported from lang/FastMath.java.
//
// High-performance 32-bit polynomial approximations and bitwise operations,
// replacing libm calls and lookup tables. Uses the Bhaskara I sine/cosine
// approximation with an extra precision pass and the Quake III invSqrt. All
// functions are pure and allocation-free.

#define FastMath_PI         3.1415927f
#define FastMath_HALF_PI    1.5707964f
#define FastMath_TWO_PI     6.2831855f
#define FastMath_PI2        6.2831855f
#define FastMath_EPSILON    0.000002f
#define FastMath_DEG_TO_RAD 0.0174532925f
#define FastMath_RAD_TO_DEG 57.2957795f

// Branchless absolute value (IEEE 754 sign-bit mask). Returns |x|.
float FastMath_abs(float x);

// Branchless integer absolute value. INT32_MIN maps to INT32_MIN, matching
// the Java overflow semantics, without triggering signed overflow UB.
int32_t FastMath_absInt(int32_t n);

// Fast rounding using the 16384 magic-float trick. Returns float to avoid
// cast-back latency in math expressions.
float FastMath_round(float x);

// Quake III 0x5f3759df inverse square root, one Newton-Raphson pass.
float FastMath_invSqrt(float x);

// Bhaskara I fast sine (radians, wraps any input).
float FastMath_sin32(float x);

// Bhaskara I fast cosine (radians, wraps any input).
float FastMath_cos32(float x);

// Fast tangent from a shared sine/cosine pass.
float FastMath_tan32(float x);

float FastMath_toRadians(float degrees);
float FastMath_toDegrees(float radians);

// Math.pow cast to float (double-precision exponentiation).
float FastMath_pow(float base, float exponent);

// Clamp val into [min, max].
float FastMath_clamp(float val, float min, float max);

// Recover cos from sin and the angle's quadrant (+/- sign).
float FastMath_cosFromSin(float sin, float angle);

#endif