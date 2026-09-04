#include "c23/constructor.h"
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
// nullptr on OOM.
Mat4 *Mat4_0(void);
Mat4 *Mat4_identityAlloc(void);

void Mat4_free(Mat4 *m);

// (row, col) access into the column-major layout.
float Mat4_get(const Mat4 *m, int row, int col);
void Mat4_set(Mat4 *m, int row, int col, float val);

// Raw linear access: index is col * 4 + row.
float Mat4_getRaw(const Mat4 *m, int index);
void Mat4_setRaw(Mat4 *m, int index, float val);

void Mat4_zero(Mat4 *m);
void Mat4_identity(Mat4 *m);
void Mat4_copy(const Mat4 *src, Mat4 *dest);

// dest = left * right. Safe when dest aliases left or right.
void Mat4_multiply(const Mat4 *left, const Mat4 *right, Mat4 *dest);

// Transpose in place or _out of place; safe when dest aliases src.
void Mat4_transpose(const Mat4 *src, Mat4 *dest);

// Single-pass TRS synthesis (column-major, 21 multiplies). Angles in degrees.
void Mat4_createTransformationMatrix(float pos_x, float pos_y, float pos_z,
                                     float rot_x_deg, float rot_y_deg, float rot_z_deg,
                                     float scale_x, float scale_y, float scale_z, Mat4 *dest);

// Single-pass 2D TRS synthesis. Angles in degrees.
void Mat4_createTransformationMatrix2D(float pos_x, float pos_y,
                                       float rot_z_deg, float scale_x, float scale_y, Mat4 *dest);

// Left-handed FPS camera view matrix. Angles in degrees.
void Mat4_createViewMatrix(float pos_x, float pos_y, float pos_z,
                           float pitch_deg, float yaw_deg, float roll_deg, Mat4 *dest);

void Mat4_translate(const Mat4 *src, float tx, float ty, float tz, Mat4 *dest);
void Mat4_scale(const Mat4 *src, float sx, float sy, float sz, Mat4 *dest);
void Mat4_rotate(const Mat4 *src, float angle_radians,
                 float axis_x, float axis_y, float axis_z, Mat4 *dest);

void Mat4_perspective(float fov_y_radians, float aspect, float z_near, float z_far, Mat4 *dest);

// Perspective with Vulkan clip conventions (inverted Y, [0,1] depth).
void Mat4_perspectiveVulkan(float fov_y_radians, float aspect, float z_near, float z_far, Mat4 *dest);

void Mat4_orthographic(float left, float right, float bottom, float top,
                       float z_near, float z_far, Mat4 *dest);

void Mat4_lookAt(float eye_x, float eye_y, float eye_z,
                 float target_x, float target_y, float target_z,
                 float up_x, float up_y, float up_z, Mat4 *dest);

// dest = mat * src (homogeneous 4-vector).
void Mat4_transform(const Mat4 *m, const Vec4 *src_vec, Vec4 *dest_vec);

// dest = mat * (src, 1) treating src as a 3-vector.
void Mat4_transformVec3(const Mat4 *m, const Vec3 *src_vec, Vec3 *dest_vec);

#endif
#define Mat4(...) CONSTRUCTOR_DISPATCH(Mat4, ##__VA_ARGS__)
