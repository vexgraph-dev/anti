#ifndef LANG_VEC4_H
#define LANG_VEC4_H

#include <stddef.h>
#include "c23/constructor.h"

// lang/vec4.h — the Vec4 class, ported from lang/Vec4.java.
//
// Off-heap 4D vector (x, y, z, w) as a self-describing Memory block. All math
// is allocation-free; dest always carries the result. Operands are const views.

typedef struct Vec4 {
    float x;
    float y;
    float z;
    float w;
} Vec4;

// Fixed byte width of a Vec4 payload (legacy BYTES).
#define VEC4_BYTES 16u

Vec4 *Vec4_0(void);
Vec4 *Vec4_4(float x, float y, float z, float w);

void Vec4_free(Vec4 *v);

float Vec4_getX(const Vec4 *v);
void Vec4_setX(Vec4 *v, float x);
float Vec4_getY(const Vec4 *v);
void Vec4_setY(Vec4 *v, float y);
float Vec4_getZ(const Vec4 *v);
void Vec4_setZ(Vec4 *v, float z);
float Vec4_getW(const Vec4 *v);
void Vec4_setW(Vec4 *v, float w);
void Vec4_set(Vec4 *v, float x, float y, float z, float w);

void Vec4_copy(const Vec4 *src, Vec4 *dest);

void Vec4_add(const Vec4 *a, const Vec4 *b, Vec4 *dest);
void Vec4_sub(const Vec4 *a, const Vec4 *b, Vec4 *dest);
void Vec4_mul(const Vec4 *a, float scalar, Vec4 *dest);
void Vec4_div(const Vec4 *a, float scalar, Vec4 *dest);

float Vec4_dot(const Vec4 *a, const Vec4 *b);
float Vec4_lengthSquared(const Vec4 *v);
float Vec4_length(const Vec4 *v);

// Normalize via FastMath_invSqrt. Zeroes dest when src is (near-)zero.
void Vec4_normalize(const Vec4 *src, Vec4 *dest);

void Vec4_min(const Vec4 *a, const Vec4 *b, Vec4 *dest);
void Vec4_max(const Vec4 *a, const Vec4 *b, Vec4 *dest);
void Vec4_clamp(const Vec4 *src, float min_val, float max_val, Vec4 *dest);
void Vec4_abs(const Vec4 *src, Vec4 *dest);
void Vec4_lerp(const Vec4 *a, const Vec4 *b, float t, Vec4 *dest);


#define Vec4(...) CONSTRUCTOR_DISPATCH(Vec4, ##__VA_ARGS__)
#endif