#include "io/bake.h"
#include <stdio.h>
#include <string.h>

// Dummy vertex structure for our baked file
typedef struct {
    float x, y, z;    // Position
    float r, g, b, a; // Color
} BakedVertex;

bool Scene_bake(const char *outputPath) {
    if (!outputPath) return false;

    FILE *file = fopen(outputPath, "wb");
    if (!file) return false;

    // 1. Write the zero-copy header
    AntiAssetHeader header;
    header.magic = ANTI_ASSET_MAGIC;
    header.version = 1;
    header.type = 1; // 1 = MESH
    header.payloadBytes = sizeof(BakedVertex) * 3;
    fwrite(&header, sizeof(AntiAssetHeader), 1, file);

    // 2. Write the payload (interleaved vertex data)
    BakedVertex vertices[3] = {
        {  0.0f, -0.5f, 0.0f,   1.0f, 0.0f, 0.0f, 1.0f }, // Top Red
        {  0.5f,  0.5f, 0.0f,   0.0f, 1.0f, 0.0f, 1.0f }, // Bottom Right Green
        { -0.5f,  0.5f, 0.0f,   0.0f, 0.0f, 1.0f, 1.0f }  // Bottom Left Blue
    };
    
    fwrite(vertices, sizeof(BakedVertex), 3, file);

    fclose(file);
    return true;
}
