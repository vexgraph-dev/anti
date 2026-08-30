#include "buffers/frame_buffer.h"

#include "oop/type.h"

// frame_buffer.c — FrameBuffer implementation.

Buffer *FrameBuffer_2(size_t width, size_t height) {
    return Buffer(ID_FRAME_BUFFER, width, height, 4);
}
