#include "buffers/stencil_buffer.h"

#include "oop/type.h"

// stencil_buffer.c — StencilBuffer implementation.

Buffer *StencilBuffer_allocate(size_t width, size_t height) {
    return Buffer_allocate(ID_STENCIL_BUFFER, width, height, 1);
}

uint8_t StencilBuffer_get(const Buffer *buf, size_t x, size_t y) {
    return (uint8_t)Buffer_getPixel(buf, x, y, 0);
}

void StencilBuffer_set(Buffer *buf, size_t x, size_t y, uint8_t val) {
    Buffer_setPixel(buf, x, y, 0, val);
}
