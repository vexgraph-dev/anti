#include "lang/vec3.h"

#include <math.h>

#include "lang/fastmath.h"
#include "nio/mem.h"
#include "oop/type.h"

// vec3.c — Vec3 port (Legacy: lang/Vec3.java). 12-byte vector block.

Vec3 *Vec3_0(void) {
    Vec3 *v = (Vec3 *)Memory_alloc(TYPE_VEC3_SINGLETON, sizeof(Vec3));
    if (v) {
        (*v).x = 0.0f;
        (*v).y = 0.0f;
        (*v).z = 0.0f;
    }
    return v;
}

Vec3 *Vec3_3(float x, float y, float z) {
    Vec3 *v = (Vec3 *)Memory_alloc(TYPE_VEC3_SINGLETON, sizeof(Vec3));
    if (v) {
        (*v).x = x;
        (*v).y = y;
        (*v).z = z;
    }
    return v;
}

void Vec3_free(Vec3 *v) {
    Memory_free(v);
}

float Vec3_getX(const Vec3 *v) {
    return (*v).x;
}

void Vec3_setX(Vec3 *v, float x) {
    (*v).x = x;
}

float Vec3_getY(const Vec3 *v) {
    return (*v).y;
}

void Vec3_setY(Vec3 *v, float y) {
    (*v).y = y;
}

float Vec3_getZ(const Vec3 *v) {
    return (*v).z;
}

void Vec3_setZ(Vec3 *v, float z) {
    (*v).z = z;
}

void Vec3_set(Vec3 *v, float x, float y, float z) {
    (*v).x = x;
    (*v).y = y;
    (*v).z = z;
}

void Vec3_copy(const Vec3 *src, Vec3 *dest) {
    (*dest).x = (*src).x;
    (*dest).y = (*src).y;
    (*dest).z = (*src).z;
}

void Vec3_add(const Vec3 *a, const Vec3 *b, Vec3 *dest) {
    (*dest).x = (*a).x + (*b).x;
    (*dest).y = (*a).y + (*b).y;
    (*dest).z = (*a).z + (*b).z;
}

void Vec3_sub(const Vec3 *a, const Vec3 *b, Vec3 *dest) {
    (*dest).x = (*a).x - (*b).x;
    (*dest).y = (*a).y - (*b).y;
    (*dest).z = (*a).z - (*b).z;
}

void Vec3_mul(const Vec3 *a, float scalar, Vec3 *dest) {
    (*dest).x = (*a).x * scalar;
    (*dest).y = (*a).y * scalar;
    (*dest).z = (*a).z * scalar;
}

void Vec3_div(const Vec3 *a, float scalar, Vec3 *dest) {
    float inv = 1.0f / scalar;
    (*dest).x = (*a).x * inv;
    (*dest).y = (*a).y * inv;
    (*dest).z = (*a).z * inv;
}

float Vec3_dot(const Vec3 *a, const Vec3 *b) {
    return (*a).x * (*b).x + (*a).y * (*b).y + (*a).z * (*b).z;
}

void Vec3_cross(const Vec3 *a, const Vec3 *b, Vec3 *dest) {
    float ax = (*a).x, ay = (*a).y, az = (*a).z;
    float bx = (*b).x, by = (*b).y, bz = (*b).z;
    (*dest).x = ay * bz - az * by;
    (*dest).y = az * bx - ax * bz;
    (*dest).z = ax * by - ay * bx;
}

float Vec3_lengthSquared(const Vec3 *v) {
    return (*v).x * (*v).x + (*v).y * (*v).y + (*v).z * (*v).z;
}

float Vec3_length(const Vec3 *v) {
    return sqrtf(Vec3_lengthSquared(v));
}

void Vec3_normalize(const Vec3 *src, Vec3 *dest) {
    float len_sq = Vec3_lengthSquared(src);
    if (len_sq > FastMath_EPSILON) {
        if (FastMath_abs(len_sq - 1.0f) < FastMath_EPSILON) {
            Vec3_copy(src, dest);
        } else {
            float inv_len = (float)(1.0 / sqrt((double)len_sq));
            (*dest).x = (*src).x * inv_len;
            (*dest).y = (*src).y * inv_len;
            (*dest).z = (*src).z * inv_len;
        }
    } else {
        (*dest).x = 0.0f;
        (*dest).y = 0.0f;
        (*dest).z = 0.0f;
    }
}

void Vec3_fastNormalize(const Vec3 *src, Vec3 *dest) {
    float len_sq = Vec3_lengthSquared(src);
    if (len_sq > FastMath_EPSILON) {
        float inv_len = FastMath_invSqrt(len_sq);
        (*dest).x = (*src).x * inv_len;
        (*dest).y = (*src).y * inv_len;
        (*dest).z = (*src).z * inv_len;
    } else {
        (*dest).x = 0.0f;
        (*dest).y = 0.0f;
        (*dest).z = 0.0f;
    }
}

float Vec3_distance(const Vec3 *a, const Vec3 *b) {
    float dx = (*a).x - (*b).x;
    float dy = (*a).y - (*b).y;
    float dz = (*a).z - (*b).z;
    return sqrtf(dx * dx + dy * dy + dz * dz);
}

float Vec3_angle(const Vec3 *a, const Vec3 *b) {
    float d = Vec3_dot(a, b);
    float len_sq = Vec3_lengthSquared(a) * Vec3_lengthSquared(b);
    if (len_sq <= FastMath_EPSILON)
        return 0.0f;
    float cos_theta = FastMath_clamp((float)(d / sqrt((double)len_sq)), -1.0f, 1.0f);
    return acosf(cos_theta);
}

void Vec3_project(const Vec3 *vector, const Vec3 *onto, Vec3 *dest) {
    float onto_len_sq = Vec3_lengthSquared(onto);
    if (onto_len_sq > FastMath_EPSILON) {
        float scale = Vec3_dot(vector, onto) / onto_len_sq;
        Vec3_mul(onto, scale, dest);
    } else {
        (*dest).x = 0.0f;
        (*dest).y = 0.0f;
        (*dest).z = 0.0f;
    }
}

void Vec3_reflect(const Vec3 *incident, const Vec3 *normal, Vec3 *dest) {
    float d = 2.0f * Vec3_dot(incident, normal);
    (*dest).x = (*incident).x - d * (*normal).x;
    (*dest).y = (*incident).y - d * (*normal).y;
    (*dest).z = (*incident).z - d * (*normal).z;
}

void Vec3_min(const Vec3 *a, const Vec3 *b, Vec3 *dest) {
    (*dest).x = (*a).x < (*b).x ? (*a).x : (*b).x;
    (*dest).y = (*a).y < (*b).y ? (*a).y : (*b).y;
    (*dest).z = (*a).z < (*b).z ? (*a).z : (*b).z;
}

void Vec3_max(const Vec3 *a, const Vec3 *b, Vec3 *dest) {
    (*dest).x = (*a).x > (*b).x ? (*a).x : (*b).x;
    (*dest).y = (*a).y > (*b).y ? (*a).y : (*b).y;
    (*dest).z = (*a).z > (*b).z ? (*a).z : (*b).z;
}

void Vec3_clamp(const Vec3 *src, float min_val, float max_val, Vec3 *dest) {
    (*dest).x = FastMath_clamp((*src).x, min_val, max_val);
    (*dest).y = FastMath_clamp((*src).y, min_val, max_val);
    (*dest).z = FastMath_clamp((*src).z, min_val, max_val);
}

void Vec3_abs(const Vec3 *src, Vec3 *dest) {
    (*dest).x = FastMath_abs((*src).x);
    (*dest).y = FastMath_abs((*src).y);
    (*dest).z = FastMath_abs((*src).z);
}

void Vec3_lerp(const Vec3 *a, const Vec3 *b, float t, Vec3 *dest) {
    float ax = (*a).x, ay = (*a).y, az = (*a).z;
    (*dest).x = ax + t * ((*b).x - ax);
    (*dest).y = ay + t * ((*b).y - ay);
    (*dest).z = az + t * ((*b).z - az);
}