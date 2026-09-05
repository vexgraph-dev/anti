#include "buffer/stencil_buffer.h"

#include "oop/type.h"
#include "annotation/overview.h"

;;OVERVIEW
/**
 * ============================================================================
 * CLASS: Stencil_buffer (buffer/stencil_buffer.c)
 * LEVEL: L2 — Behavior (raster buffer behavior API)
 * ============================================================================
 * 1-channel 8-bit stencil masking buffer.
 *
 * STRUCT FIELDS (Mirroring buffer/buffer.h):
 * ----------------------------------------------------------------------------
 *   Buffer {
 *     uint32_t width; // raster width in pixels
 *     uint32_t height; // raster height in pixels
 *     uint32_t channels; // channel count
 *     uint32_t typeId; // block-header type id
 *     uint32_t length; // width * height * channels
 *     uint32_t pad; // alignment padding
 *     uint64_t data[]; // Contiguous 64-bit element array
 *   }
 *
 * FUNCTION REGISTRY:
 * ----------------------------------------------------------------------------
 * Constructors:
 *   - StencilBuffer_2(width, height)
 *
 * Setters:
 *   - StencilBuffer_set(buf, x, y, val)
 *
 * Getters:
 *   - StencilBuffer_get(buf, x, y)
 * ============================================================================
 */


// stencil_buffer.c — StencilBuffer implementation.

Buffer *StencilBuffer_2(size_t width, size_t height) {
    return Buffer(ID_STENCIL_BUFFER, width, height, 1);
}

uint8_t StencilBuffer_get(const Buffer *buf, size_t x, size_t y) {
    return (uint8_t)Buffer_getPixel(buf, x, y, 0);
}

void StencilBuffer_set(Buffer *buf, size_t x, size_t y, uint8_t val) {
    Buffer_setPixel(buf, x, y, 0, val);
}
