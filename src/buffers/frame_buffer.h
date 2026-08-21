#ifndef BUFFERS_FRAME_BUFFER_H
#define BUFFERS_FRAME_BUFFER_H

#include "buffers/buffer.h"

// buffers/frame_buffer.h — 4-channel composite frame buffer / render target.
// Ported from legacy buffers/FrameBuffer.java.

Buffer *FrameBuffer_allocate(size_t width, size_t height);

#endif
