#include "../nio/mem.h"
#include "../oop/type.h"
#define STB_TRUETYPE_IMPLEMENTATION
#include "stb_truetype.h"
#include "font.h"
#include "../vulkan/texture/texture.h"
#include <stdio.h>
#include <stdlib.h>
#include <string.h>

#define ATLAS_SIZE 2048
#define SDF_PADDING 5
#define SDF_ONEDGE 128
#define SDF_PIXEL_DIST_SCALE 32.0f

struct Font {
    stbtt_fontinfo info;
    unsigned char *ttf_buffer;
    uint8_t *atlas_rgba;
    int32_t textureId;
    
    // Very simple shelf packer state
    int current_x;
    int current_y;
    int bottom_y;
    
    // Simple glyph cache (very basic for drafting)
    // Real implementation would use a hash map for sizes/codepoints.
    // For drafting, we'll just cache exactly one size per glyph or assume it's cached.
};

Font *Font_load(const char *path) {
    FILE *f = fopen(path, "rb");
    if (!f) {
        printf("Failed to open font: %s\n", path);
        return NULL;
    }
    fseek(f, 0, SEEK_END);
    size_t size = ftell(f);
    fseek(f, 0, SEEK_SET);
    
    unsigned char *ttf_buffer = Memory_alloc(TYPE_ARRAY, size);
    if (!ttf_buffer) { fclose(f); return NULL; }
    fread(ttf_buffer, 1, size, f);
    fclose(f);
    
    Font *font = Memory_alloc(TYPE_PANEL_SINGLETON, sizeof(Font));
    if (font) memset(font, 0, sizeof(Font));
    font->ttf_buffer = ttf_buffer;
    
    if (!stbtt_InitFont(&font->info, font->ttf_buffer, 0)) {
        free(ttf_buffer);
        Memory_free(font);
        return NULL;
    }
    
    font->atlas_rgba = Memory_alloc(TYPE_ARRAY, ATLAS_SIZE * ATLAS_SIZE * 4);
    if (font->atlas_rgba) memset(font->atlas_rgba, 0, ATLAS_SIZE * ATLAS_SIZE * 4);
    // Initialize full texture
    font->textureId = Texture_loadRaw(font->atlas_rgba, ATLAS_SIZE, ATLAS_SIZE);
    
    font->current_x = 0;
    font->current_y = 0;
    font->bottom_y = 0;
    
    return font;
}

void Font_free(Font *font) {
    if (!font) return;
    if (font->ttf_buffer) Memory_free(font->ttf_buffer);
    if (font->atlas_rgba) Memory_free(font->atlas_rgba);
    Memory_free(font);
}

float Font_getScaleForPixelHeight(const Font *font, float height) {
    return stbtt_ScaleForPixelHeight(&font->info, height);
}

void Font_getVMetrics(const Font *font, float *ascent, float *descent, float *lineGap) {
    int a, d, l;
    stbtt_GetFontVMetrics(&font->info, &a, &d, &l);
    if (ascent) *ascent = a;
    if (descent) *descent = d;
    if (lineGap) *lineGap = l;
}

bool Font_getGlyph(Font *font, uint32_t codepoint, float pixelHeight, GlyphMetrics *outMetrics) {
    // For drafting, we assume we generate it every time or you can cache it.
    // In a real engine, you look up (codepoint, pixelHeight) in a hash map first.
    // Here we will just generate and pack it to show the flow.
    
    float scale = stbtt_ScaleForPixelHeight(&font->info, pixelHeight);
    
    int w, h, xoff, yoff;
    unsigned char *sdf = stbtt_GetCodepointSDF(&font->info, scale, codepoint, 
                                               SDF_PADDING, SDF_ONEDGE, SDF_PIXEL_DIST_SCALE, 
                                               &w, &h, &xoff, &yoff);
    if (!sdf) {
        return false;
    }
    
    // Pack into shelf
    if (font->current_x + w > ATLAS_SIZE) {
        font->current_x = 0;
        font->current_y = font->bottom_y;
    }
    if (font->current_y + h > ATLAS_SIZE) {
        free(sdf);
        return false; // Atlas full
    }
    
    int destX = font->current_x;
    int destY = font->current_y;
    
    // Copy SDF into RGBA atlas (we put distance in RGB and Alpha)
    for (int y = 0; y < h; y++) {
        for (int x = 0; x < w; x++) {
            int srcIdx = y * w + x;
            int dstIdx = ((destY + y) * ATLAS_SIZE + (destX + x)) * 4;
            unsigned char dist = sdf[srcIdx];
            font->atlas_rgba[dstIdx + 0] = dist;
            font->atlas_rgba[dstIdx + 1] = dist;
            font->atlas_rgba[dstIdx + 2] = dist;
            font->atlas_rgba[dstIdx + 3] = dist;
        }
    }
    
    // Upload sub-region
    // We update the texture directly. In a real engine you might batch this per-frame.
    Texture_updateSubRaw(font->textureId, font->atlas_rgba, destX, destY, w, h); // Or specific rect
    
    free(sdf);
    
    // Advance packer
    font->current_x += w + 1; // 1px padding
    if (font->current_y + h > font->bottom_y) {
        font->bottom_y = font->current_y + h + 1;
    }
    
    int advanceWidth, leftSideBearing;
    stbtt_GetCodepointHMetrics(&font->info, codepoint, &advanceWidth, &leftSideBearing);
    
    if (outMetrics) {
        outMetrics->u0 = (float)destX / ATLAS_SIZE;
        outMetrics->v0 = (float)destY / ATLAS_SIZE;
        outMetrics->u1 = (float)(destX + w) / ATLAS_SIZE;
        outMetrics->v1 = (float)(destY + h) / ATLAS_SIZE;
        outMetrics->width = w;
        outMetrics->height = h;
        outMetrics->xOffset = xoff;
        outMetrics->yOffset = yoff;
        outMetrics->advance = advanceWidth * scale;
    }
    
    return true;
}

int32_t Font_getTextureId(const Font *font) {
    return font ? font->textureId : -1;
}

float Font_getKerning(const Font *font, uint32_t cp1, uint32_t cp2, float pixelHeight) {
    if (!font) return 0.0f;
    float scale = stbtt_ScaleForPixelHeight(&font->info, pixelHeight);
    return stbtt_GetCodepointKernAdvance(&font->info, cp1, cp2) * scale;
}

extern char* System_getFontPath(const char* familyName);

Font *Font_loadSystem(const char *familyName) {
    char *path = System_getFontPath(familyName);
    if (!path) {
        printf("Font_loadSystem: Could not find font '%s'\n", familyName);
        return NULL;
    }
    Font *f = Font_load(path);
    free(path); // strdup'd in font_cocoa.m
    return f;
}
