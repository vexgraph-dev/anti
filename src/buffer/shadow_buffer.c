#include "buffer/shadow_buffer.h"

#include "oop/type.h"

// shadow_buffer.c — ShadowBuffer implementation.

Buffer *ShadowBuffer_2(size_t width, size_t height) {
    return Buffer(ID_SHADOW_BUFFER, width, height, 1);
}
