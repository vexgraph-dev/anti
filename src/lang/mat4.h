#ifndef LANG_MAT4_H
#define LANG_MAT4_H

#include <stddef.h>

#include "lang/vec3.h"
#include "lang/vec4.h"

// lang/mat4.h — the Mat4 class, ported from lang/Mat4.java.
//
// Off-heap 4x4 matrix, column-major, 16 contiguous floats (64 bytes) as a
// self-describing Memory block. Column-major matches the Vulkan/OpenGL layout:
//
//     m[col * 4 + row]
//     col 0: 0..3, col 1: 4..7, col 2: 8..11, col 3: 12..15

typedef struct Mat4 {
    float m[16];
} Mat4;

// Fixed byte width of a Mat4 payload (legacy BYTES).
#define MAT4_BYTES 64u

// Allocate a Mat4 block, initialized to identity (or zeroed via Mat4_zero).
// NULL on OOM.
Mat4 *Mat4_allocate(void);
Mat4 *Mat4_allocateIdentity(void);

void Mat4_free(Mat4 *m);

// (row, col) access into the column-major layout.
float Mat4_get(const Mat4 *m, int row, int col);
void Mat4_set(Mat4 *m, int row, int col, float val);

// Raw linear access: index is col * 4 + row.
float Mat4_getRaw(const Mat4 *m, int index);
void Mat4_setRaw(Mat4 *m, int index, float val);

void Mat4_zero(Mat4 *m);
void Mat4_identity(Mat4 *m);
void Mat4_copy(Mat4 *dest, const Mat4 *src);

// dest = left * right. Safe when dest aliases left or right.
void Mat4_multiply(Mat4 *dest, const Mat4 *left, const Mat4 *right);

// Transpose in place or out of place; safe when dest aliases src.
void Mat4_transpose(Mat4 *dest, const Mat4 *src);

// Single-pass TRS synthesis (column-major, 21 multiplies). Angles in degrees.
void Mat4_createTransformationMatrix(Mat4 *dest, float pos_x, float pos_y, float pos_z,
                                     float rot_x_deg, float rot_y_deg, float rot_z_deg,
                                     float scale_x, float scale_y, float scale_z);

// Single-pass 2D TRS synthesis. Angles in degrees.
void Mat4_createTransformationMatrix2D(Mat4 *dest, float pos_x, float pos_y,
                                       float rot_z_deg, float scale_x, float scale_y);

// Left-handed FPS camera view matrix. Angles in degrees.
void Mat4_createViewMatrix(Mat4 *dest, float pos_x, float pos_y, float pos_z,
                           float pitch_deg, float yaw_deg, float roll_deg);

void Mat4_translate(Mat4 *dest, const Mat4 *src, float tx, float ty, float tz);
void Mat4_scale(Mat4 *dest, const Mat4 *src, float sx, float sy, float sz);
void Mat4_rotate(Mat4 *dest, const Mat4 *src, float angle_radians,
                 float axis_x, float axis_y, float axis_z);

void Mat4_perspective(Mat4 *dest, float fov_y_radians, float aspect, float z_near, float z_far);

// Perspective with Vulkan clip conventions (inverted Y, [0,1] depth).
void Mat4_perspectiveVulkan(Mat4 *dest, float fov_y_radians, float aspect, float z_near, float z_far);

void Mat4_orthographic(Mat4 *dest, float left, float right, float bottom, float top,
                       float z_near, float z_far);

void Mat4_lookAt(Mat4 *dest, float eye_x, float eye_y, float eye_z,
                 float target_x, float target_y, float target_z,
                 float up_x, float up_y, float up_z);

// dest = mat * src (homogeneous 4-vector).
void Mat4_transform(Vec4 *dest_vec, const Mat4 *m, const Vec4 *src_vec);

// dest = mat * (src, 1) treating src as a 3-vector.
void Mat4_transformVec3(Vec3 *dest_vec, const Mat4 *m, const Vec3 *src_vec);

#endif