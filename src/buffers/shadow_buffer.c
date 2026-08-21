#include "buffers/shadow_buffer.h"

#include "oop/type.h"

// shadow_buffer.c — ShadowBuffer implementation.

Buffer *ShadowBuffer_allocate(size_t width, size_t height) {
    return Buffer_allocate(ID_SHADOW_BUFFER, width, height, 1);
}
