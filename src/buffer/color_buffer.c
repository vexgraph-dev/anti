#include "buffer/color_buffer.h"

#include "oop/type.h"
#include "annotation/overview.h"

;;OVERVIEW
/**
 * ============================================================================
 * MODULE: Color_buffer (buffer/color_buffer.c)
 * LEVEL: L2 — Behavior (raster buffer behavior API)
 * ============================================================================
 * 4-channel RGBA color buffer.
 *
 * FUNCTION REGISTRY:
 * ----------------------------------------------------------------------------
 * Constructors:
 *   - ColorBuffer_2(width, height)
 *
 * Core Functions:
 *   - ColorBuffer_clearRGBA(buf, r, g, b, a)
 *
 * Setters:
 *   - ColorBuffer_setRGBA(buf, x, y, r, g, b, a)
 *
 * Getters:
 *   - ColorBuffer_getRGBA(buf, x, y, r, g, b, a)
 * ============================================================================
 */


// color_buffer.c — 4-channel RGBA color buffer implementation.

Buffer *ColorBuffer_2(size_t width, size_t height) {
    return Buffer(ID_COLOR_BUFFER, width, height, 4);
}

void ColorBuffer_setRGBA(Buffer *buf, size_t x, size_t y, uint8_t r, uint8_t g, uint8_t b, uint8_t a) {
    Buffer_setPixel(buf, x, y, 0, r);
    Buffer_setPixel(buf, x, y, 1, g);
    Buffer_setPixel(buf, x, y, 2, b);
    Buffer_setPixel(buf, x, y, 3, a);
}

void ColorBuffer_getRGBA(const Buffer *buf, size_t x, size_t y, uint8_t *r, uint8_t *g, uint8_t *b, uint8_t *a) {
    if (r) *r = (uint8_t)Buffer_getPixel(buf, x, y, 0);
    if (g) *g = (uint8_t)Buffer_getPixel(buf, x, y, 1);
    if (b) *b = (uint8_t)Buffer_getPixel(buf, x, y, 2);
    if (a) *a = (uint8_t)Buffer_getPixel(buf, x, y, 3);
}

void ColorBuffer_clearRGBA(Buffer *buf, uint8_t r, uint8_t g, uint8_t b, uint8_t a) {
    if (!buf) return;
    size_t w = Buffer_width(buf);
    size_t h = Buffer_height(buf);
    for (size_t y = 0; y < h; y++) {
        for (size_t x = 0; x < w; x++) {
            ColorBuffer_setRGBA(buf, x, y, r, g, b, a);
        }
    }
}
