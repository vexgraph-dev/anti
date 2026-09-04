#include "buffer/frame_buffer.h"

#include "oop/type.h"
#include "annotation/overview.h"

;;OVERVIEW
/**
 * ============================================================================
 * MODULE: Frame_buffer (buffer/frame_buffer.c)
 * LEVEL: L2 — Behavior (raster buffer behavior API)
 * ============================================================================
 * 4-channel composite frame buffer / render target.
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
