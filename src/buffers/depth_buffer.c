#include "buffers/depth_buffer.h"

#include <string.h>

#include "oop/type.h"

// depth_buffer.c — 1-channel depth buffer implementation.

Buffer *DepthBuffer_allocate(size_t width, size_t height) {
    return Buffer_allocate(ID_DEPTH_BUFFER, width, height, 1);
}

float DepthBuffer_get(const Buffer *buf, size_t x, size_t y) {
    uint64_t raw = Buffer_getPixel(buf, x, y, 0);
    float val;
    memcpy(&val, &raw, sizeof(float));
    return val;
}

void DepthBuffer_set(Buffer *buf, size_t x, size_t y, float depth) {
    uint64_t raw = 0;
    memcpy(&raw, &depth, sizeof(float));
    Buffer_setPixel(buf, x, y, 0, raw);
}

void DepthBuffer_clear(Buffer *buf, float depth) {
    uint64_t raw = 0;
    memcpy(&raw, &depth, sizeof(float));
    Buffer_clear(buf, raw);
}
