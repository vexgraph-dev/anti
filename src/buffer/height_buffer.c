#include "buffer/height_buffer.h"

#include <string.h>

#include "oop/type.h"
#include "annotation/overview.h"

;;OVERVIEW
/**
 * ============================================================================
 * MODULE: Height_buffer (buffer/height_buffer.c)
 * LEVEL: L2 — Behavior (raster buffer behavior API)
 * ============================================================================
 * 1-channel terrain/displacement elevation buffer.
 *
 * FUNCTION REGISTRY:
 * ----------------------------------------------------------------------------
 * Constructors:
 *   - HeightBuffer_2(width, height)
 *
 * Setters:
 *   - HeightBuffer_setHeight(buf, x, y, h)
 *
 * Getters:
 *   - HeightBuffer_getHeight(buf, x, y)
 * ============================================================================
 */


// height_buffer.c — HeightBuffer implementation.

Buffer *HeightBuffer_2(size_t width, size_t height) {
    return Buffer(ID_HEIGHT_BUFFER, width, height, 1);
}

float HeightBuffer_getHeight(const Buffer *buf, size_t x, size_t y) {
    uint64_t raw = Buffer_getPixel(buf, x, y, 0);
    float val;
    memcpy(&val, &raw, sizeof(float));
    return val;
}

void HeightBuffer_setHeight(Buffer *buf, size_t x, size_t y, float h) {
    uint64_t raw = 0;
    memcpy(&raw, &h, sizeof(float));
    Buffer_setPixel(buf, x, y, 0, raw);
}
