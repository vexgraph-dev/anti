#include "buffer/frame_buffer.h"

#include "oop/type.h"
#include "annotation/overview.h"

;;OVERVIEW
/**
 * ============================================================================
 * CLASS: Frame_buffer (buffer/frame_buffer.c)
 * LEVEL: L2 — Behavior (raster buffer behavior API)
 * ============================================================================
 * 4-channel composite frame buffer / render target.
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
 *   - FrameBuffer_2(width, height)
 * ============================================================================
 */


// frame_buffer.c — FrameBuffer implementation.

Buffer *FrameBuffer_2(size_t width, size_t height) {
    return Buffer(ID_FRAME_BUFFER, width, height, 4);
}
