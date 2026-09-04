#include "buffer/depth_buffer.h"

#include <string.h>

#include "oop/type.h"
#include "annotation/overview.h"

;;OVERVIEW
/**
 * ============================================================================
 * MODULE: Depth_buffer (buffer/depth_buffer.c)
 * LEVEL: L2 — Behavior (raster buffer behavior API)
 * ============================================================================
 * 1-channel floating-point depth buffer.
 *
 * FUNCTION REGISTRY:
 * ----------------------------------------------------------------------------
 * Constructors:
 *   - DepthBuffer_2(width, height)
 *
 * Core Functions:
 *   - DepthBuffer_clear(buf, depth)
 *
 * Setters:
 *   - DepthBuffer_set(buf, x, y, depth)
 *
 * Getters:
 *   - DepthBuffer_get(buf, x, y)
 * ============================================================================
 */


// depth_buffer.c — 1-channel depth buffer implementation.

Buffer *DepthBuffer_2(size_t width, size_t height) {
    return Buffer(ID_DEPTH_BUFFER, width, height, 1);
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
