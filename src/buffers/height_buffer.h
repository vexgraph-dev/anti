#ifndef BUFFERS_HEIGHT_BUFFER_H
#define BUFFERS_HEIGHT_BUFFER_H

#include "buffers/buffer.h"

// buffers/height_buffer.h — 1-channel terrain/displacement elevation buffer.
// Ported from legacy buffers/HeightBuffer.java.

Buffer *HeightBuffer_allocate(size_t width, size_t height);
float HeightBuffer_getHeight(const Buffer *buf, size_t x, size_t y);
void HeightBuffer_setHeight(Buffer *buf, size_t x, size_t y, float h);

#endif
