#include "lang/vec2.h"

#include <math.h>

#include "lang/fastmath.h"
#include "nio/mem.h"
#include "oop/type.h"

// vec2.c — Vec2 port (Legacy: lang/Vec2.java). 8-byte vector block.

Vec2 *Vec2_allocate(void) {
    Vec2 *v = (Vec2 *)Memory_alloc(TYPE_VEC2_SINGLETON, sizeof(Vec2));
    if (v) {
        (*v).x = 0.0f;
        (*v).y = 0.0f;
    }
    return v;
}

Vec2 *Vec2_allocateXY(float x, float y) {
    Vec2 *v = (Vec2 *)Memory_alloc(TYPE_VEC2_SINGLETON, sizeof(Vec2));
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

void Vec2_copy(Vec2 *dest, const Vec2 *src) {
    (*dest).x = (*src).x;
    (*dest).y = (*src).y;
}

void Vec2_add(Vec2 *dest, const Vec2 *a, const Vec2 *b) {
    (*dest).x = (*a).x + (*b).x;
    (*dest).y = (*a).y + (*b).y;
}

void Vec2_sub(Vec2 *dest, const Vec2 *a, const Vec2 *b) {
    (*dest).x = (*a).x - (*b).x;
    (*dest).y = (*a).y - (*b).y;
}

void Vec2_mul(Vec2 *dest, const Vec2 *a, float scalar) {
    (*dest).x = (*a).x * scalar;
    (*dest).y = (*a).y * scalar;
}

void Vec2_div(Vec2 *dest, const Vec2 *a, float scalar) {
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

void Vec2_normalize(Vec2 *dest, const Vec2 *src) {
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

void Vec2_perpendicular(Vec2 *dest, const Vec2 *src) {
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

void Vec2_project(Vec2 *dest, const Vec2 *vector, const Vec2 *onto) {
    float onto_len_sq = Vec2_lengthSquared(onto);
    if (onto_len_sq > FastMath_EPSILON) {
        float scale = Vec2_dot(vector, onto) / onto_len_sq;
        Vec2_mul(dest, onto, scale);
    } else {
        (*dest).x = 0.0f;
        (*dest).y = 0.0f;
    }
}

void Vec2_min(Vec2 *dest, const Vec2 *a, const Vec2 *b) {
    (*dest).x = (*a).x < (*b).x ? (*a).x : (*b).x;
    (*dest).y = (*a).y < (*b).y ? (*a).y : (*b).y;
}

void Vec2_max(Vec2 *dest, const Vec2 *a, const Vec2 *b) {
    (*dest).x = (*a).x > (*b).x ? (*a).x : (*b).x;
    (*dest).y = (*a).y > (*b).y ? (*a).y : (*b).y;
}

void Vec2_clamp(Vec2 *dest, const Vec2 *src, float min_val, float max_val) {
    (*dest).x = FastMath_clamp((*src).x, min_val, max_val);
    (*dest).y = FastMath_clamp((*src).y, min_val, max_val);
}

void Vec2_abs(Vec2 *dest, const Vec2 *src) {
    (*dest).x = FastMath_abs((*src).x);
    (*dest).y = FastMath_abs((*src).y);
}

void Vec2_lerp(Vec2 *dest, const Vec2 *a, const Vec2 *b, float t) {
    float ax = (*a).x;
    float ay = (*a).y;
    (*dest).x = ax + t * ((*b).x - ax);
    (*dest).y = ay + t * ((*b).y - ay);
}