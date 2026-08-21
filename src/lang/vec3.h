#ifndef LANG_VEC3_H
#define LANG_VEC3_H

#include <stddef.h>

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

Vec3 *Vec3_allocate(void);
Vec3 *Vec3_allocateXYZ(float x, float y, float z);

void Vec3_free(Vec3 *v);

float Vec3_getX(const Vec3 *v);
void Vec3_setX(Vec3 *v, float x);
float Vec3_getY(const Vec3 *v);
void Vec3_setY(Vec3 *v, float y);
float Vec3_getZ(const Vec3 *v);
void Vec3_setZ(Vec3 *v, float z);
void Vec3_set(Vec3 *v, float x, float y, float z);

void Vec3_copy(Vec3 *dest, const Vec3 *src);

void Vec3_add(Vec3 *dest, const Vec3 *a, const Vec3 *b);
void Vec3_sub(Vec3 *dest, const Vec3 *a, const Vec3 *b);
void Vec3_mul(Vec3 *dest, const Vec3 *a, float scalar);
void Vec3_div(Vec3 *dest, const Vec3 *a, float scalar);

float Vec3_dot(const Vec3 *a, const Vec3 *b);

// Cross product: dest = a x b.
void Vec3_cross(Vec3 *dest, const Vec3 *a, const Vec3 *b);

float Vec3_lengthSquared(const Vec3 *v);
float Vec3_length(const Vec3 *v);

// Precise normalize (1/sqrt via libm). Zeroes dest when src is (near-)zero.
void Vec3_normalize(Vec3 *dest, const Vec3 *src);

// Fast normalize via FastMath_invSqrt. Zeroes dest when src is (near-)zero.
void Vec3_fastNormalize(Vec3 *dest, const Vec3 *src);

float Vec3_distance(const Vec3 *a, const Vec3 *b);

// Angle in radians between the two vectors (0 when either is degenerate).
float Vec3_angle(const Vec3 *a, const Vec3 *b);

// Project vector onto onto; zeroes dest when onto is (near-)zero.
void Vec3_project(Vec3 *dest, const Vec3 *vector, const Vec3 *onto);

// Mirror incident across normal (normal need not be unit length).
void Vec3_reflect(Vec3 *dest, const Vec3 *incident, const Vec3 *normal);

void Vec3_min(Vec3 *dest, const Vec3 *a, const Vec3 *b);
void Vec3_max(Vec3 *dest, const Vec3 *a, const Vec3 *b);
void Vec3_clamp(Vec3 *dest, const Vec3 *src, float min_val, float max_val);
void Vec3_abs(Vec3 *dest, const Vec3 *src);
void Vec3_lerp(Vec3 *dest, const Vec3 *a, const Vec3 *b, float t);

#endif