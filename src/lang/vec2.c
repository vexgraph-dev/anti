#include "lang/vec2.h"

#include <math.h>

#include "lang/fastmath.h"
#include "nio/mem.h"
#include "oop/type.h"
#include "annotation/overview.h"

;;OVERVIEW
/**
 * ============================================================================
 * MODULE: Vec2 (lang/vec2.c)
 * ============================================================================
 * the Vec2 class, ported from lang/Vec2.java.
 *
 * FUNCTION REGISTRY:
 * ----------------------------------------------------------------------------
 * Constructors:
 *   - Vec2_0(void)
 *   - Vec2_2(x, y)
 *
 * Core Functions:
 *   - Vec2_free(v)
 *   - Vec2_copy(src, dest)
 *   - Vec2_add(a, b, dest)
 *   - Vec2_sub(a, b, dest)
 *   - Vec2_mul(a, scalar, dest)
 *   - Vec2_div(a, scalar, dest)
 *   - Vec2_dot(a, b)
 *   - Vec2_lengthSquared(v)
 *   - Vec2_length(v)
 *   - Vec2_normalize(src, dest)
 *   - Vec2_perpendicular(src, dest)
 *   - Vec2_distance(a, b)
 *   - Vec2_angle(a, b)
 *   - Vec2_project(vector, onto, dest)
 *   - Vec2_min(a, b, dest)
 *   - Vec2_max(a, b, dest)
 *   - Vec2_clamp(src, min_val, max_val, dest)
 *   - Vec2_abs(src, dest)
 *   - Vec2_lerp(a, b, t, dest)
 *
 * Setters:
 *   - Vec2_setX(v, x)
 *   - Vec2_setY(v, y)
 *   - Vec2_set(v, x, y)
 *
 * Getters:
 *   - Vec2_getX(v)
 *   - Vec2_getY(v)
 * ============================================================================
 */


// vec2.c — Vec2 port (Legacy: lang/Vec2.java). 8-byte vector block.

Vec2 *Vec2_0(void) {
    Vec2 *v = (Vec2*) Memory_alloc(TYPE_VEC2_SINGLETON, sizeof(Vec2));
    if (v) {
        (*v).x = 0.0f;
        (*v).y = 0.0f;
    }
    return v;
}

Vec2 *Vec2_2(float x, float y) {
    Vec2 *v = (Vec2*) Memory_alloc(TYPE_VEC2_SINGLETON, sizeof(Vec2));
    if (v) {
        (*v).x = x;
        (*v).y = y;
    }
    return v;
}

void Vec2_free(Vec2 *v) {
    Memory_free(v);
}

float Vec2_getX(const Vec2 *v) {
    return (*v).x;
}

void Vec2_setX(Vec2 *v, float x) {
    (*v).x = x;
}

float Vec2_getY(const Vec2 *v) {
    return (*v).y;
}

void Vec2_setY(Vec2 *v, float y) {
    (*v).y = y;
}

void Vec2_set(Vec2 *v, float x, float y) {
    (*v).x = x;
    (*v).y = y;
}

void Vec2_copy(const Vec2 *src, Vec2 *dest) {
    (*dest).x = (*src).x;
    (*dest).y = (*src).y;
}

void Vec2_add(const Vec2 *a, const Vec2 *b, Vec2 *dest) {
    (*dest).x = (*a).x + (*b).x;
    (*dest).y = (*a).y + (*b).y;
}

void Vec2_sub(const Vec2 *a, const Vec2 *b, Vec2 *dest) {
    (*dest).x = (*a).x - (*b).x;
    (*dest).y = (*a).y - (*b).y;
}

void Vec2_mul(const Vec2 *a, float scalar, Vec2 *dest) {
    (*dest).x = (*a).x * scalar;
    (*dest).y = (*a).y * scalar;
}

void Vec2_div(const Vec2 *a, float scalar, Vec2 *dest) {
    float inv = 1.0f / scalar;
    (*dest).x = (*a).x * inv;
    (*dest).y = (*a).y * inv;
}

float Vec2_dot(const Vec2 *a, const Vec2 *b) {
    return (*a).x * (*b).x + (*a).y * (*b).y;
}

float Vec2_lengthSquared(const Vec2 *v) {
    return (*v).x * (*v).x + (*v).y * (*v).y;
}

float Vec2_length(const Vec2 *v) {
    return sqrtf(Vec2_lengthSquared(v));
}

void Vec2_normalize(const Vec2 *src, Vec2 *dest) {
    float len_sq = Vec2_lengthSquared(src);
    if (len_sq > FastMath_EPSILON) {
        float inv_len = FastMath_invSqrt(len_sq);
        (*dest).x = (*src).x * inv_len;
        (*dest).y = (*src).y * inv_len;
    } else {
        (*dest).x = 0.0f;
        (*dest).y = 0.0f;
    }
}

void Vec2_perpendicular(const Vec2 *src, Vec2 *dest) {
    (*dest).x = -(*src).y;
    (*dest).y = (*src).x;
}

float Vec2_distance(const Vec2 *a, const Vec2 *b) {
    float dx = (*a).x - (*b).x;
    float dy = (*a).y - (*b).y;
    return sqrtf(dx * dx + dy * dy);
}

float Vec2_angle(const Vec2 *a, const Vec2 *b) {
    float d = Vec2_dot(a, b);
    float len_sq = Vec2_lengthSquared(a) * Vec2_lengthSquared(b);
    if (len_sq <= FastMath_EPSILON)
        return 0.0f;
    float cos_theta = FastMath_clamp(d * FastMath_invSqrt(len_sq), -1.0f, 1.0f);
    return acosf(cos_theta);
}

void Vec2_project(const Vec2 *vector, const Vec2 *onto, Vec2 *dest) {
    float onto_len_sq = Vec2_lengthSquared(onto);
    if (onto_len_sq > FastMath_EPSILON) {
        float scale = Vec2_dot(vector, onto) / onto_len_sq;
        Vec2_mul(onto, scale, dest);
    } else {
        (*dest).x = 0.0f;
        (*dest).y = 0.0f;
    }
}

void Vec2_min(const Vec2 *a, const Vec2 *b, Vec2 *dest) {
    (*dest).x = (*a).x < (*b).x ? (*a).x : (*b).x;
    (*dest).y = (*a).y < (*b).y ? (*a).y : (*b).y;
}

void Vec2_max(const Vec2 *a, const Vec2 *b, Vec2 *dest) {
    (*dest).x = (*a).x > (*b).x ? (*a).x : (*b).x;
    (*dest).y = (*a).y > (*b).y ? (*a).y : (*b).y;
}

void Vec2_clamp(const Vec2 *src, float min_val, float max_val, Vec2 *dest) {
    (*dest).x = FastMath_clamp((*src).x, min_val, max_val);
    (*dest).y = FastMath_clamp((*src).y, min_val, max_val);
}

void Vec2_abs(const Vec2 *src, Vec2 *dest) {
    (*dest).x = FastMath_abs((*src).x);
    (*dest).y = FastMath_abs((*src).y);
}

void Vec2_lerp(const Vec2 *a, const Vec2 *b, float t, Vec2 *dest) {
    float ax = (*a).x;
    float ay = (*a).y;
    (*dest).x = ax + t * ((*b).x - ax);
    (*dest).y = ay + t * ((*b).y - ay);
}
