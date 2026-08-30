#ifndef LANG_VEC2_H
#define LANG_VEC2_H

#include <stddef.h>
#include "c23/constructor.h"

// lang/vec2.h — the Vec2 class, ported from lang/Vec2.java.
//
// Off-heap 2D vector (x, y) as a self-describing Memory block. All math is
// allocation-free; dest always carries the result. Operands are const views.

typedef struct Vec2 {
    float x;
    float y;
} Vec2;

// Fixed byte width of a Vec2 payload (legacy BYTES).
#define VEC2_BYTES 8u

// Allocate a zeroed Vec2 block, or one seeded with (x, y). nullptr on OOM.
Vec2 *Vec2_0(void);
Vec2 *Vec2_2(float x, float y);

void Vec2_free(Vec2 *v);

float Vec2_getX(const Vec2 *v);
void Vec2_setX(Vec2 *v, float x);
float Vec2_getY(const Vec2 *v);
void Vec2_setY(Vec2 *v, float y);
void Vec2_set(Vec2 *v, float x, float y);

void Vec2_copy(const Vec2 *src, Vec2 *dest);

void Vec2_add(const Vec2 *a, const Vec2 *b, Vec2 *dest);
void Vec2_sub(const Vec2 *a, const Vec2 *b, Vec2 *dest);
void Vec2_mul(const Vec2 *a, float scalar, Vec2 *dest);
void Vec2_div(const Vec2 *a, float scalar, Vec2 *dest);

float Vec2_dot(const Vec2 *a, const Vec2 *b);
float Vec2_lengthSquared(const Vec2 *v);
float Vec2_length(const Vec2 *v);

// Normalize src into dest; zeroes dest when src is (near-)zero.
void Vec2_normalize(const Vec2 *src, Vec2 *dest);

// Counter-clockwise 90-degree perpendicular: dest = (-y, x).
void Vec2_perpendicular(const Vec2 *src, Vec2 *dest);

float Vec2_distance(const Vec2 *a, const Vec2 *b);

// Angle in radians between the two vectors (0 when either is degenerate).
float Vec2_angle(const Vec2 *a, const Vec2 *b);

// Project vector onto onto; zeroes dest when onto is (near-)zero.
void Vec2_project(const Vec2 *vector, const Vec2 *onto, Vec2 *dest);

void Vec2_min(const Vec2 *a, const Vec2 *b, Vec2 *dest);
void Vec2_max(const Vec2 *a, const Vec2 *b, Vec2 *dest);
void Vec2_clamp(const Vec2 *src, float min_val, float max_val, Vec2 *dest);
void Vec2_abs(const Vec2 *src, Vec2 *dest);
void Vec2_lerp(const Vec2 *a, const Vec2 *b, float t, Vec2 *dest);


#define Vec2(...) CONSTRUCTOR_DISPATCH(Vec2, ##__VA_ARGS__)
#endif