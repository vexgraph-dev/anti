#ifndef BUFFERS_NORMAL_BUFFER_H
#define BUFFERS_NORMAL_BUFFER_H

#include "buffers/buffer.h"

// buffers/normal_buffer.h — 3-channel (XYZ) surface/view normals buffer.
// Ported from legacy buffers/NormalBuffer.java.

Buffer *NormalBuffer_allocate(size_t width, size_t height);
void NormalBuffer_setNormal(Buffer *buf, size_t x, size_t y, float nx, float ny, float nz);
void NormalBuffer_getNormal(const Buffer *buf, size_t x, size_t y, float *nx, float *ny, float *nz);

#endif
