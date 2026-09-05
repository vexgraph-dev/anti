#include "lang/mat4.h"

#include "lang/fastmath.h"
#include "nio/mem.h"
#include "oop/type.h"
#include "annotation/overview.h"

;;OVERVIEW
/**
 * ============================================================================
 * CLASS: Mat4 (lang/mat4.c)
 * LEVEL: L2 — Behavior (math behavior API)
 * ============================================================================
 * the Mat4 class, ported from lang/Mat4.java.
 *
 * STRUCT FIELDS (Mirroring lang/mat4.h):
 * ----------------------------------------------------------------------------
 *   Mat4 {
 *     float m[16]; // column-major 4x4 matrix
 *   }
 *
 * FUNCTION REGISTRY:
 * ----------------------------------------------------------------------------
 * Constructors:
 *   - Mat4_0(void)
 *
 * Core Functions:
 *   - Mat4_identityAlloc(void)
 *   - Mat4_free(m)
 *   - Mat4_zero(m)
 *   - Mat4_identity(m)
 *   - Mat4_copy(src, dest)
 *   - Mat4_multiply(left, right, dest)
 *   - Mat4_transpose(src, dest)
 *   - Mat4_createTransformationMatrix(pos_x, pos_y, pos_z, rot_x_deg, rot_y_deg, rot_z_deg, scale_x, scale_y, scale_z, dest)
 *   - Mat4_createTransformationMatrix2D(pos_x, pos_y, rot_z_deg, scale_x, scale_y, dest)
 *   - Mat4_createViewMatrix(pos_x, pos_y, pos_z, pitch_deg, yaw_deg, roll_deg, dest)
 *   - Mat4_translate(src, tx, ty, tz, dest)
 *   - Mat4_scale(src, sx, sy, sz, dest)
 *   - Mat4_rotate(src, angle_radians, axis_x, axis_y, axis_z, dest)
 *   - Mat4_perspective(fov_y_radians, aspect, z_near, z_far, dest)
 *   - Mat4_perspectiveVulkan(fov_y_radians, aspect, z_near, z_far, dest)
 *   - Mat4_orthographic(left, right, bottom, top, z_near, z_far, dest)
 *   - Mat4_lookAt(eye_x, eye_y, eye_z, target_x, target_y, target_z, up_x, up_y, up_z, dest)
 *   - Mat4_transform(m, src_vec, dest_vec)
 *   - Mat4_transformVec3(m, src_vec, dest_vec)
 *
 * Setters:
 *   - Mat4_set(m, row, col, val)
 *   - Mat4_setRaw(m, index, val)
 *
 * Getters:
 *   - Mat4_get(m, row, col)
 *   - Mat4_getRaw(m, index)
 * ============================================================================
 */


// mat4.c — Mat4 port (Legacy: lang/Mat4.java). 64-byte column-major matrix.

Mat4 *Mat4_0(void) {
    Mat4 *m = Memory_alloc(TYPE_MAT4_SINGLETON, sizeof(Mat4));
    if (m)
        Mat4_identity(m);
    return m;
}

Mat4 *Mat4_identityAlloc(void) {
    return Mat4_0();
}

void Mat4_free(Mat4 *m) {
    Memory_free(m);
}

float Mat4_get(const Mat4 *m, int row, int col) {
    return (*m).m[col * 4 + row];
}

void Mat4_set(Mat4 *m, int row, int col, float val) {
    (*m).m[col * 4 + row] = val;
}

float Mat4_getRaw(const Mat4 *m, int index) {
    return (*m).m[index];
}

void Mat4_setRaw(Mat4 *m, int index, float val) {
    (*m).m[index] = val;
}

void Mat4_zero(Mat4 *m) {
    for (int i = 0; i < 16; i++)
        (*m).m[i] = 0.0f;
}

void Mat4_identity(Mat4 *m) {
    Mat4_zero(m);
    Mat4_set(m, 0, 0, 1.0f);
    Mat4_set(m, 1, 1, 1.0f);
    Mat4_set(m, 2, 2, 1.0f);
    Mat4_set(m, 3, 3, 1.0f);
}

void Mat4_copy(const Mat4 *src, Mat4 *dest) {
    for (int i = 0; i < 16; i++)
        (*dest).m[i] = (*src).m[i];
}

void Mat4_multiply(const Mat4 *left, const Mat4 *right, Mat4 *dest) {
    float m00 = Mat4_get(left, 0, 0), m01 = Mat4_get(left, 0, 1), m02 = Mat4_get(left, 0, 2), m03 = Mat4_get(left, 0, 3);
    float m10 = Mat4_get(left, 1, 0), m11 = Mat4_get(left, 1, 1), m12 = Mat4_get(left, 1, 2), m13 = Mat4_get(left, 1, 3);
    float m20 = Mat4_get(left, 2, 0), m21 = Mat4_get(left, 2, 1), m22 = Mat4_get(left, 2, 2), m23 = Mat4_get(left, 2, 3);
    float m30 = Mat4_get(left, 3, 0), m31 = Mat4_get(left, 3, 1), m32 = Mat4_get(left, 3, 2), m33 = Mat4_get(left, 3, 3);

    for (int col = 0; col < 4; col++) {
        float r0 = Mat4_get(right, 0, col);
        float r1 = Mat4_get(right, 1, col);
        float r2 = Mat4_get(right, 2, col);
        float r3 = Mat4_get(right, 3, col);

        Mat4_set(dest, 0, col, m00 * r0 + m01 * r1 + m02 * r2 + m03 * r3);
        Mat4_set(dest, 1, col, m10 * r0 + m11 * r1 + m12 * r2 + m13 * r3);
        Mat4_set(dest, 2, col, m20 * r0 + m21 * r1 + m22 * r2 + m23 * r3);
        Mat4_set(dest, 3, col, m30 * r0 + m31 * r1 + m32 * r2 + m33 * r3);
    }
}

void Mat4_transpose(const Mat4 *src, Mat4 *dest) {
    if (dest == src) {
        float t;
        t = (*dest).m[1];  (*dest).m[1]  = (*dest).m[4];  (*dest).m[4]  = t;
        t = (*dest).m[2];  (*dest).m[2]  = (*dest).m[8];  (*dest).m[8]  = t;
        t = (*dest).m[3];  (*dest).m[3]  = (*dest).m[12]; (*dest).m[12] = t;
        t = (*dest).m[6];  (*dest).m[6]  = (*dest).m[9];  (*dest).m[9]  = t;
        t = (*dest).m[7];  (*dest).m[7]  = (*dest).m[13]; (*dest).m[13] = t;
        t = (*dest).m[11]; (*dest).m[11] = (*dest).m[14]; (*dest).m[14] = t;
        return;
    }
    for (int row = 0; row < 4; row++)
        for (int col = 0; col < 4; col++)
            Mat4_set(dest, col, row, Mat4_get(src, row, col));
}

void Mat4_createTransformationMatrix(float pos_x, float pos_y, float pos_z,
                                     float rot_x_deg, float rot_y_deg, float rot_z_deg,
                                     float scale_x, float scale_y, float scale_z, Mat4 *dest) {
    float rx = rot_x_deg * FastMath_DEG_TO_RAD;
    float ry = rot_y_deg * FastMath_DEG_TO_RAD;
    float rz = rot_z_deg * FastMath_DEG_TO_RAD;

    float cx = FastMath_cos32(rx);
    float sx = FastMath_sin32(rx);
    float cy = FastMath_cos32(ry);
    float sy = FastMath_sin32(ry);
    float cz = FastMath_cos32(rz);
    float sz = FastMath_sin32(rz);

    float cycz = cy * cz;
    float cysz = cy * sz;

    Mat4_set(dest, 0, 0, cycz * scale_x);
    Mat4_set(dest, 1, 0, cysz * scale_x);
    Mat4_set(dest, 2, 0, -sy * scale_x);
    Mat4_set(dest, 3, 0, 0.0f);

    Mat4_set(dest, 0, 1, (sx * sy * cz - cx * sz) * scale_y);
    Mat4_set(dest, 1, 1, (sx * sy * sz + cx * cz) * scale_y);
    Mat4_set(dest, 2, 1, (sx * cy) * scale_y);
    Mat4_set(dest, 3, 1, 0.0f);

    Mat4_set(dest, 0, 2, (cx * sy * cz + sx * sz) * scale_z);
    Mat4_set(dest, 1, 2, (cx * sy * sz - sx * cz) * scale_z);
    Mat4_set(dest, 2, 2, (cx * cy) * scale_z);
    Mat4_set(dest, 3, 2, 0.0f);

    Mat4_set(dest, 0, 3, pos_x);
    Mat4_set(dest, 1, 3, pos_y);
    Mat4_set(dest, 2, 3, pos_z);
    Mat4_set(dest, 3, 3, 1.0f);
}

void Mat4_createTransformationMatrix2D(float pos_x, float pos_y,
                                       float rot_z_deg, float scale_x, float scale_y, Mat4 *dest) {
    float rz = rot_z_deg * FastMath_DEG_TO_RAD;
    float cz = FastMath_cos32(rz);
    float sz = FastMath_sin32(rz);

    Mat4_zero(dest);
    Mat4_set(dest, 0, 0, cz * scale_x);
    Mat4_set(dest, 1, 0, sz * scale_x);
    Mat4_set(dest, 0, 1, -sz * scale_y);
    Mat4_set(dest, 1, 1, cz * scale_y);
    Mat4_set(dest, 2, 2, 1.0f);
    Mat4_set(dest, 3, 3, 1.0f);
    Mat4_set(dest, 0, 3, pos_x);
    Mat4_set(dest, 1, 3, pos_y);
}

void Mat4_createViewMatrix(float pos_x, float pos_y, float pos_z,
                           float pitch_deg, float yaw_deg, float roll_deg, Mat4 *dest) {
    (void)roll_deg;

    float pitch_rad = pitch_deg * FastMath_DEG_TO_RAD;
    float yaw_rad = yaw_deg * FastMath_DEG_TO_RAD;

    float cp = FastMath_cos32(pitch_rad);
    float sp = FastMath_sin32(pitch_rad);
    float cy = FastMath_cos32(yaw_rad);
    float sy = FastMath_sin32(yaw_rad);

    float m00 = cy,  m01 = sy * sp, m02 = sy * cp;
    float m10 = 0.0f, m11 = cp,     m12 = -sp;
    float m20 = -sy, m21 = cy * sp, m22 = cy * cp;

    Mat4_set(dest, 0, 0, m00); Mat4_set(dest, 0, 1, m01); Mat4_set(dest, 0, 2, m02); Mat4_set(dest, 0, 3, 0.0f);
    Mat4_set(dest, 1, 0, m10); Mat4_set(dest, 1, 1, m11); Mat4_set(dest, 1, 2, m12); Mat4_set(dest, 1, 3, 0.0f);
    Mat4_set(dest, 2, 0, m20); Mat4_set(dest, 2, 1, m21); Mat4_set(dest, 2, 2, m22); Mat4_set(dest, 2, 3, 0.0f);

    Mat4_set(dest, 3, 0, -(m00 * pos_x + m10 * pos_y + m20 * pos_z));
    Mat4_set(dest, 3, 1, -(m01 * pos_x + m11 * pos_y + m21 * pos_z));
    Mat4_set(dest, 3, 2, -(m02 * pos_x + m12 * pos_y + m22 * pos_z));
    Mat4_set(dest, 3, 3, 1.0f);
}

void Mat4_translate(const Mat4 *src, float tx, float ty, float tz, Mat4 *dest) {
    Mat4_copy(src, dest);
    float m00 = Mat4_get(src, 0, 0), m01 = Mat4_get(src, 0, 1), m02 = Mat4_get(src, 0, 2), m03 = Mat4_get(src, 0, 3);
    float m10 = Mat4_get(src, 1, 0), m11 = Mat4_get(src, 1, 1), m12 = Mat4_get(src, 1, 2), m13 = Mat4_get(src, 1, 3);
    float m20 = Mat4_get(src, 2, 0), m21 = Mat4_get(src, 2, 1), m22 = Mat4_get(src, 2, 2), m23 = Mat4_get(src, 2, 3);
    float m30 = Mat4_get(src, 3, 0), m31 = Mat4_get(src, 3, 1), m32 = Mat4_get(src, 3, 2), m33 = Mat4_get(src, 3, 3);

    Mat4_set(dest, 0, 3, m00 * tx + m01 * ty + m02 * tz + m03);
    Mat4_set(dest, 1, 3, m10 * tx + m11 * ty + m12 * tz + m13);
    Mat4_set(dest, 2, 3, m20 * tx + m21 * ty + m22 * tz + m23);
    Mat4_set(dest, 3, 3, m30 * tx + m31 * ty + m32 * tz + m33);
}

void Mat4_scale(const Mat4 *src, float sx, float sy, float sz, Mat4 *dest) {
    Mat4_copy(src, dest);
    for (int row = 0; row < 4; row++) {
        Mat4_set(dest, row, 0, Mat4_get(src, row, 0) * sx);
        Mat4_set(dest, row, 1, Mat4_get(src, row, 1) * sy);
        Mat4_set(dest, row, 2, Mat4_get(src, row, 2) * sz);
    }
}

void Mat4_rotate(const Mat4 *src, float angle_radians,
                 float axis_x, float axis_y, float axis_z, Mat4 *dest) {
    float len_sq = axis_x * axis_x + axis_y * axis_y + axis_z * axis_z;
    if (len_sq <= FastMath_EPSILON)
        return;
    float inv_len = FastMath_invSqrt(len_sq);
    axis_x *= inv_len;
    axis_y *= inv_len;
    axis_z *= inv_len;

    float c = FastMath_cos32(angle_radians);
    float s = FastMath_sin32(angle_radians);
    float nc = 1.0f - c;

    float xy = axis_x * axis_y, yz = axis_y * axis_z, zx = axis_z * axis_x;
    float xs = axis_x * s,      ys = axis_y * s,      zs = axis_z * s;

    float r00 = axis_x * axis_x * nc + c;
    float r01 = xy * nc - zs;
    float r02 = zx * nc + ys;

    float r10 = xy * nc + zs;
    float r11 = axis_y * axis_y * nc + c;
    float r12 = yz * nc - xs;

    float r20 = zx * nc - ys;
    float r21 = yz * nc + xs;
    float r22 = axis_z * axis_z * nc + c;

    for (int row = 0; row < 4; row++) {
        float m0 = Mat4_get(src, row, 0);
        float m1 = Mat4_get(src, row, 1);
        float m2 = Mat4_get(src, row, 2);

        Mat4_set(dest, row, 0, m0 * r00 + m1 * r10 + m2 * r20);
        Mat4_set(dest, row, 1, m0 * r01 + m1 * r11 + m2 * r21);
        Mat4_set(dest, row, 2, m0 * r02 + m1 * r12 + m2 * r22);
    }
}

void Mat4_perspective(float fov_y_radians, float aspect, float z_near, float z_far, Mat4 *dest) {
    Mat4_zero(dest);
    float tan_half_fov = FastMath_tan32(fov_y_radians / 2.0f);

    Mat4_set(dest, 0, 0, 1.0f / (aspect * tan_half_fov));
    Mat4_set(dest, 1, 1, 1.0f / tan_half_fov);
    Mat4_set(dest, 2, 2, -(z_far + z_near) / (z_far - z_near));
    Mat4_set(dest, 2, 3, -(2.0f * z_far * z_near) / (z_far - z_near));
    Mat4_set(dest, 3, 2, -1.0f);
}

void Mat4_perspectiveVulkan(float fov_y_radians, float aspect, float z_near, float z_far, Mat4 *dest) {
    Mat4_perspective(fov_y_radians, aspect, z_near, z_far, dest);
    Mat4_set(dest, 1, 1, -Mat4_get(dest, 1, 1));
    Mat4_set(dest, 2, 2, z_far / (z_near - z_far));
    Mat4_set(dest, 2, 3, (z_near * z_far) / (z_near - z_far));
}

void Mat4_orthographic(float left, float right, float bottom, float top,
                       float z_near, float z_far, Mat4 *dest) {
    Mat4_zero(dest);
    Mat4_set(dest, 0, 0, 2.0f / (right - left));
    Mat4_set(dest, 1, 1, 2.0f / (top - bottom));
    Mat4_set(dest, 2, 2, -2.0f / (z_far - z_near));
    Mat4_set(dest, 0, 3, -(right + left) / (right - left));
    Mat4_set(dest, 1, 3, -(top + bottom) / (top - bottom));
    Mat4_set(dest, 2, 3, -(z_far + z_near) / (z_far - z_near));
    Mat4_set(dest, 3, 3, 1.0f);
}

void Mat4_lookAt(float eye_x, float eye_y, float eye_z,
                 float target_x, float target_y, float target_z,
                 float up_x, float up_y, float up_z, Mat4 *dest) {
    float fx = eye_x - target_x;
    float fy = eye_y - target_y;
    float fz = eye_z - target_z;
    float f_len_sq = fx * fx + fy * fy + fz * fz;
    if (f_len_sq > FastMath_EPSILON) {
        float inv_f = FastMath_invSqrt(f_len_sq);
        fx *= inv_f;
        fy *= inv_f;
        fz *= inv_f;
    }

    float rx = up_y * fz - up_z * fy;
    float ry = up_z * fx - up_x * fz;
    float rz = up_x * fy - up_y * fx;
    float r_len_sq = rx * rx + ry * ry + rz * rz;
    if (r_len_sq > FastMath_EPSILON) {
        float inv_r = FastMath_invSqrt(r_len_sq);
        rx *= inv_r;
        ry *= inv_r;
        rz *= inv_r;
    }

    float ux = fy * rz - fz * ry;
    float uy = fz * rx - fx * rz;
    float uz = fx * ry - fy * rx;

    Mat4_zero(dest);
    Mat4_set(dest, 0, 0, rx); Mat4_set(dest, 0, 1, ry); Mat4_set(dest, 0, 2, rz); Mat4_set(dest, 0, 3, -(rx * eye_x + ry * eye_y + rz * eye_z));
    Mat4_set(dest, 1, 0, ux); Mat4_set(dest, 1, 1, uy); Mat4_set(dest, 1, 2, uz); Mat4_set(dest, 1, 3, -(ux * eye_x + uy * eye_y + uz * eye_z));
    Mat4_set(dest, 2, 0, fx); Mat4_set(dest, 2, 1, fy); Mat4_set(dest, 2, 2, fz); Mat4_set(dest, 2, 3, -(fx * eye_x + fy * eye_y + fz * eye_z));
    Mat4_set(dest, 3, 3, 1.0f);
}

void Mat4_transform(const Mat4 *m, const Vec4 *src_vec, Vec4 *dest_vec) {
    float x = (*src_vec).x;
    float y = (*src_vec).y;
    float z = (*src_vec).z;
    float w = (*src_vec).w;

    float rx = Mat4_get(m, 0, 0) * x + Mat4_get(m, 0, 1) * y + Mat4_get(m, 0, 2) * z + Mat4_get(m, 0, 3) * w;
    float ry = Mat4_get(m, 1, 0) * x + Mat4_get(m, 1, 1) * y + Mat4_get(m, 1, 2) * z + Mat4_get(m, 1, 3) * w;
    float rz = Mat4_get(m, 2, 0) * x + Mat4_get(m, 2, 1) * y + Mat4_get(m, 2, 2) * z + Mat4_get(m, 2, 3) * w;
    float rw = Mat4_get(m, 3, 0) * x + Mat4_get(m, 3, 1) * y + Mat4_get(m, 3, 2) * z + Mat4_get(m, 3, 3) * w;

    (*dest_vec).x = rx;
    (*dest_vec).y = ry;
    (*dest_vec).z = rz;
    (*dest_vec).w = rw;
}

void Mat4_transformVec3(const Mat4 *m, const Vec3 *src_vec, Vec3 *dest_vec) {
    float x = (*src_vec).x;
    float y = (*src_vec).y;
    float z = (*src_vec).z;

    float rx = Mat4_get(m, 0, 0) * x + Mat4_get(m, 0, 1) * y + Mat4_get(m, 0, 2) * z + Mat4_get(m, 0, 3);
    float ry = Mat4_get(m, 1, 0) * x + Mat4_get(m, 1, 1) * y + Mat4_get(m, 1, 2) * z + Mat4_get(m, 1, 3);
    float rz = Mat4_get(m, 2, 0) * x + Mat4_get(m, 2, 1) * y + Mat4_get(m, 2, 2) * z + Mat4_get(m, 2, 3);

    (*dest_vec).x = rx;
    (*dest_vec).y = ry;
    (*dest_vec).z = rz;
}
