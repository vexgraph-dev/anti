#include "lang/vec4.h"

#include <math.h>

#include "lang/fastmath.h"
#include "nio/mem.h"
#include "oop/type.h"
#include "annotation/overview.h"

;;OVERVIEW
/**
 * ============================================================================
 * MODULE: Vec4 (lang/vec4.c)
 * ============================================================================
 * the Vec4 class, ported from lang/Vec4.java.
 *
 * FUNCTION REGISTRY:
 * ----------------------------------------------------------------------------
 * Constructors:
 *   - Vec4_0(void)
 *   - Vec4_4(x, y, z, w)
 *
 * Core Functions:
 *   - Vec4_free(v)
 *   - Vec4_copy(src, dest)
 *   - Vec4_add(a, b, dest)
 *   - Vec4_sub(a, b, dest)
 *   - Vec4_mul(a, scalar, dest)
 *   - Vec4_div(a, scalar, dest)
 *   - Vec4_dot(a, b)
 *   - Vec4_lengthSquared(v)
 *   - Vec4_length(v)
 *   - Vec4_normalize(src, dest)
 *   - Vec4_min(a, b, dest)
 *   - Vec4_max(a, b, dest)
 *   - Vec4_clamp(src, min_val, max_val, dest)
 *   - Vec4_abs(src, dest)
 *   - Vec4_lerp(a, b, t, dest)
 *
 * Setters:
 *   - Vec4_setX(v, x)
 *   - Vec4_setY(v, y)
 *   - Vec4_setZ(v, z)
 *   - Vec4_setW(v, w)
 *   - Vec4_set(v, x, y, z, w)
 *
 * Getters:
 *   - Vec4_getX(v)
 *   - Vec4_getY(v)
 *   - Vec4_getZ(v)
 *   - Vec4_getW(v)
 * ============================================================================
 */


// vec4.c — Vec4 port (Legacy: lang/Vec4.java). 16-byte vector block.

Vec4 *Vec4_0(void) {
    Vec4 *v = Memory_alloc(TYPE_VEC4_SINGLETON, sizeof(Vec4));
    if (v) {
        (*v).x = 0.0f;
        (*v).y = 0.0f;
        (*v).z = 0.0f;
        (*v).w = 0.0f;
    }
    return v;
}

Vec4 *Vec4_4(float x, float y, float z, float w) {
    Vec4 *v = Memory_alloc(TYPE_VEC4_SINGLETON, sizeof(Vec4));
    if (v) {
        (*v).x = x;
        (*v).y = y;
        (*v).z = z;
        (*v).w = w;
    }
    return v;
}

void Vec4_free(Vec4 *v) {
    Memory_free(v);
}

float Vec4_getX(const Vec4 *v) {
    return (*v).x;
}

void Vec4_setX(Vec4 *v, float x) {
    (*v).x = x;
}

float Vec4_getY(const Vec4 *v) {
    return (*v).y;
}

void Vec4_setY(Vec4 *v, float y) {
    (*v).y = y;
}

float Vec4_getZ(const Vec4 *v) {
    return (*v).z;
}

void Vec4_setZ(Vec4 *v, float z) {
    (*v).z = z;
}

float Vec4_getW(const Vec4 *v) {
    return (*v).w;
}

void Vec4_setW(Vec4 *v, float w) {
    (*v).w = w;
}

void Vec4_set(Vec4 *v, float x, float y, float z, float w) {
    (*v).x = x;
    (*v).y = y;
    (*v).z = z;
    (*v).w = w;
}

void Vec4_copy(const Vec4 *src, Vec4 *dest) {
    (*dest).x = (*src).x;
    (*dest).y = (*src).y;
    (*dest).z = (*src).z;
    (*dest).w = (*src).w;
}

void Vec4_add(const Vec4 *a, const Vec4 *b, Vec4 *dest) {
    (*dest).x = (*a).x + (*b).x;
    (*dest).y = (*a).y + (*b).y;
    (*dest).z = (*a).z + (*b).z;
    (*dest).w = (*a).w + (*b).w;
}

void Vec4_sub(const Vec4 *a, const Vec4 *b, Vec4 *dest) {
    (*dest).x = (*a).x - (*b).x;
    (*dest).y = (*a).y - (*b).y;
    (*dest).z = (*a).z - (*b).z;
    (*dest).w = (*a).w - (*b).w;
}

void Vec4_mul(const Vec4 *a, float scalar, Vec4 *dest) {
    (*dest).x = (*a).x * scalar;
    (*dest).y = (*a).y * scalar;
    (*dest).z = (*a).z * scalar;
    (*dest).w = (*a).w * scalar;
}

void Vec4_div(const Vec4 *a, float scalar, Vec4 *dest) {
    float inv = 1.0f / scalar;
    (*dest).x = (*a).x * inv;
    (*dest).y = (*a).y * inv;
    (*dest).z = (*a).z * inv;
    (*dest).w = (*a).w * inv;
}

float Vec4_dot(const Vec4 *a, const Vec4 *b) {
    return (*a).x * (*b).x + (*a).y * (*b).y + (*a).z * (*b).z + (*a).w * (*b).w;
}

float Vec4_lengthSquared(const Vec4 *v) {
    return (*v).x * (*v).x + (*v).y * (*v).y + (*v).z * (*v).z + (*v).w * (*v).w;
}

float Vec4_length(const Vec4 *v) {
    return sqrtf(Vec4_lengthSquared(v));
}

void Vec4_normalize(const Vec4 *src, Vec4 *dest) {
    float len_sq = Vec4_lengthSquared(src);
    if (len_sq > FastMath_EPSILON) {
        float inv_len = FastMath_invSqrt(len_sq);
        (*dest).x = (*src).x * inv_len;
        (*dest).y = (*src).y * inv_len;
        (*dest).z = (*src).z * inv_len;
        (*dest).w = (*src).w * inv_len;
    } else {
        (*dest).x = 0.0f;
        (*dest).y = 0.0f;
        (*dest).z = 0.0f;
        (*dest).w = 0.0f;
    }
}

void Vec4_min(const Vec4 *a, const Vec4 *b, Vec4 *dest) {
    (*dest).x = (*a).x < (*b).x ? (*a).x : (*b).x;
    (*dest).y = (*a).y < (*b).y ? (*a).y : (*b).y;
    (*dest).z = (*a).z < (*b).z ? (*a).z : (*b).z;
    (*dest).w = (*a).w < (*b).w ? (*a).w : (*b).w;
}

void Vec4_max(const Vec4 *a, const Vec4 *b, Vec4 *dest) {
    (*dest).x = (*a).x > (*b).x ? (*a).x : (*b).x;
    (*dest).y = (*a).y > (*b).y ? (*a).y : (*b).y;
    (*dest).z = (*a).z > (*b).z ? (*a).z : (*b).z;
    (*dest).w = (*a).w > (*b).w ? (*a).w : (*b).w;
}

void Vec4_clamp(const Vec4 *src, float min_val, float max_val, Vec4 *dest) {
    (*dest).x = FastMath_clamp((*src).x, min_val, max_val);
    (*dest).y = FastMath_clamp((*src).y, min_val, max_val);
    (*dest).z = FastMath_clamp((*src).z, min_val, max_val);
    (*dest).w = FastMath_clamp((*src).w, min_val, max_val);
}

void Vec4_abs(const Vec4 *src, Vec4 *dest) {
    (*dest).x = FastMath_abs((*src).x);
    (*dest).y = FastMath_abs((*src).y);
    (*dest).z = FastMath_abs((*src).z);
    (*dest).w = FastMath_abs((*src).w);
}

void Vec4_lerp(const Vec4 *a, const Vec4 *b, float t, Vec4 *dest) {
    float ax = (*a).x, ay = (*a).y, az = (*a).z, aw = (*a).w;
    (*dest).x = ax + t * ((*b).x - ax);
    (*dest).y = ay + t * ((*b).y - ay);
    (*dest).z = az + t * ((*b).z - az);
    (*dest).w = aw + t * ((*b).w - aw);
}
