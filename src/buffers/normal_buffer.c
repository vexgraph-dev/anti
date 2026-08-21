#include "buffers/normal_buffer.h"

#include <string.h>

#include "oop/type.h"

// normal_buffer.c — 3-channel normal buffer implementation.

Buffer *NormalBuffer_allocate(size_t width, size_t height) {
    return Buffer_allocate(ID_NORMAL_BUFFER, width, height, 3);
}

void NormalBuffer_setNormal(Buffer *buf, size_t x, size_t y, float nx, float ny, float nz) {
    uint64_t rx = 0, ry = 0, rz = 0;
    memcpy(&rx, &nx, sizeof(float));
    memcpy(&ry, &ny, sizeof(float));
    memcpy(&rz, &nz, sizeof(float));
    Buffer_setPixel(buf, x, y, 0, rx);
    Buffer_setPixel(buf, x, y, 1, ry);
    Buffer_setPixel(buf, x, y, 2, rz);
}

void NormalBuffer_getNormal(const Buffer *buf, size_t x, size_t y, float *nx, float *ny, float *nz) {
    uint64_t rx = Buffer_getPixel(buf, x, y, 0);
    uint64_t ry = Buffer_getPixel(buf, x, y, 1);
    uint64_t rz = Buffer_getPixel(buf, x, y, 2);
    if (nx) memcpy(nx, &rx, sizeof(float));
    if (ny) memcpy(ny, &ry, sizeof(float));
    if (nz) memcpy(nz, &rz, sizeof(float));
}
