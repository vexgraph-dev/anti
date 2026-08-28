#ifndef IO_BAKE_H
#define IO_BAKE_H

#include <stdint.h>
#include <stdbool.h>

// io/bake.h — Offline asset compilation pipeline
// 
// Converts raw source assets (glTF, OBJ, PNG) into the zero-copy 
// binary ".anti" format mapped directly into RAM via mmap.c.

#define ANTI_ASSET_MAGIC 0x49544E41 // "ANTI"

typedef struct AntiAssetHeader {
    uint32_t magic;
    uint32_t version;
    uint32_t type;
    uint32_t payloadBytes;
} AntiAssetHeader;

// Bakes a hardcoded test mesh (triangle) into an .anti file on disk.
bool Scene_bake(const char *outputPath);

#endif
