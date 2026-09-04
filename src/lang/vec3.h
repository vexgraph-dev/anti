#ifndef LANG_VEC3_H
#define LANG_VEC3_H

#include <stddef.h>
#include "c23/constructor.h"

// lang/vec3.h — the Vec3 class, ported from lang/Vec3.java.
//
// Off-heap 3D vector (x, y, z) as a self-describing Memory block. All math is
// allocation-free; dest always carries the result. Operands are const views.

typedef struct Vec3 {
    float x;
    float y;
    float z;
} Vec3;

// Fixed byte width of a Vec3 payload (legacy BYTES).
#define VEC3_BYTES 12u

Vec3 *Vec3_0(void);
Vec3 *Vec3_3(float x, float y, float z);

void Vec3_free(Vec3 *v);

float Vec3_getX(const Vec3 *v);
void Vec3_setX(Vec3 *v, float x);
float Vec3_getY(const Vec3 *v);
void Vec3_setY(Vec3 *v, float y);
float Vec3_getZ(const Vec3 *v);
void Vec3_setZ(Vec3 *v, float z);
void Vec3_set(Vec3 *v, float x, float y, float z);

void Vec3_copy(const Vec3 *src, Vec3 *dest);

void Vec3_add(const Vec3 *a, const Vec3 *b, Vec3 *dest);
void Vec3_sub(const Vec3 *a, const Vec3 *b, Vec3 *dest);
void Vec3_mul(const Vec3 *a, float scalar, Vec3 *dest);
void Vec3_div(const Vec3 *a, float scalar, Vec3 *dest);

float Vec3_dot(const Vec3 *a, const Vec3 *b);

// Cross product: dest = a x b.
void Vec3_cross(const Vec3 *a, const Vec3 *b, Vec3 *dest);

float Vec3_lengthSquared(const Vec3 *v);
float Vec3_length(const Vec3 *v);

// Precise normalize (1/sqrt via libm). Zeroes dest when src is (near-)zero.
void Vec3_normalize(const Vec3 *src, Vec3 *dest);

// Fast normalize via FastMath_invSqrt. Zeroes dest when src is (near-)zero.
void Vec3_fastNormalize(const Vec3 *src, Vec3 *dest);

float Vec3_distance(const Vec3 *a, const Vec3 *b);

// Angle in radians between the two vectors (0 when either is degenerate).
float Vec3_angle(const Vec3 *a, const Vec3 *b);

// Project vector onto onto; zeroes dest when onto is (near-)zero.
void Vec3_project(const Vec3 *vector, const Vec3 *onto, Vec3 *dest);

// Mirror incident across normal (normal need not be unit length).
void Vec3_reflect(const Vec3 *incident, const Vec3 *normal, Vec3 *dest);

void Vec3_min(const Vec3 *a, const Vec3 *b, Vec3 *dest);
void Vec3_max(const Vec3 *a, const Vec3 *b, Vec3 *dest);
void Vec3_clamp(const Vec3 *src, float min_val, float max_val, Vec3 *dest);
void Vec3_abs(const Vec3 *src, Vec3 *dest);
void Vec3_lerp(const Vec3 *a, const Vec3 *b, float t, Vec3 *dest);


#define Vec3(...) CONSTRUCTOR_DISPATCH(Vec3, __VA_ARGS__)
#endif