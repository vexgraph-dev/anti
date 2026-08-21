#ifndef BUFFERS_SHADOW_BUFFER_H
#define BUFFERS_SHADOW_BUFFER_H

#include "buffers/buffer.h"

// buffers/shadow_buffer.h — 1-channel shadow depth cascade buffer.
// Ported from legacy buffers/ShadowBuffer.java.

Buffer *ShadowBuffer_allocate(size_t width, size_t height);

#endif
