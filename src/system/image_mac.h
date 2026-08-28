#ifndef SYSTEM_IMAGE_MAC_H
#define SYSTEM_IMAGE_MAC_H

#include <stddef.h>
#include <stdint.h>

// Decodes a PNG or JPG from memory into a raw RGBA8 pixel buffer using macOS CoreGraphics.
// Returns a malloc'd byte array that the caller must free().
// Outputs the width and height into outW and outH.
void *ImageMac_decode(const void *fileData, size_t fileSize, size_t *outW, size_t *outH);

#endif // SYSTEM_IMAGE_MAC_H
