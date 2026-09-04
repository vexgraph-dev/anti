#include "buffer/shadow_buffer.h"

#include "oop/type.h"
#include "annotation/overview.h"

;;OVERVIEW
/**
 * ============================================================================
 * MODULE: Shadow_buffer (buffer/shadow_buffer.c)
 * LEVEL: L2 — Behavior (raster buffer behavior API)
 * ============================================================================
 * 1-channel shadow depth cascade buffer.
 *
 * FUNCTION REGISTRY:
 * ----------------------------------------------------------------------------
 * Constructors:
 *   - ShadowBuffer_2(width, height)
 * ============================================================================
 */


// shadow_buffer.c — ShadowBuffer implementation.

Buffer *ShadowBuffer_2(size_t width, size_t height) {
    return Buffer(ID_SHADOW_BUFFER, width, height, 1);
}
