#ifndef LANG_VEC2_H
#define LANG_VEC2_H

#include <stddef.h>

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

// Allocate a zeroed Vec2 block, or one seeded with (x, y). NULL on OOM.
Vec2 *Vec2_allocate(void);
Vec2 *Vec2_allocateXY(float x, float y);

void Vec2_free(Vec2 *v);

float Vec2_getX(const Vec2 *v);
void Vec2_setX(Vec2 *v, float x);
float Vec2_getY(const Vec2 *v);
void Vec2_setY(Vec2 *v, float y);
void Vec2_set(Vec2 *v, float x, float y);

void Vec2_copy(Vec2 *dest, const Vec2 *src);

void Vec2_add(Vec2 *dest, const Vec2 *a, const Vec2 *b);
void Vec2_sub(Vec2 *dest, const Vec2 *a, const Vec2 *b);
void Vec2_mul(Vec2 *dest, const Vec2 *a, float scalar);
void Vec2_div(Vec2 *dest, const Vec2 *a, float scalar);

float Vec2_dot(const Vec2 *a, const Vec2 *b);
float Vec2_lengthSquared(const Vec2 *v);
float Vec2_length(const Vec2 *v);

// Normalize src into dest; zeroes dest when src is (near-)zero.
void Vec2_normalize(Vec2 *dest, const Vec2 *src);

// Counter-clockwise 90-degree perpendicular: dest = (-y, x).
void Vec2_perpendicular(Vec2 *dest, const Vec2 *src);

float Vec2_distance(const Vec2 *a, const Vec2 *b);

// Angle in radians between the two vectors (0 when either is degenerate).
float Vec2_angle(const Vec2 *a, const Vec2 *b);

// Project vector onto onto; zeroes dest when onto is (near-)zero.
void Vec2_project(Vec2 *dest, const Vec2 *vector, const Vec2 *onto);

void Vec2_min(Vec2 *dest, const Vec2 *a, const Vec2 *b);
void Vec2_max(Vec2 *dest, const Vec2 *a, const Vec2 *b);
void Vec2_clamp(Vec2 *dest, const Vec2 *src, float min_val, float max_val);
void Vec2_abs(Vec2 *dest, const Vec2 *src);
void Vec2_lerp(Vec2 *dest, const Vec2 *a, const Vec2 *b, float t);

#endif