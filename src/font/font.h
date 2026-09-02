#ifndef ANTI_FONT_H
#define ANTI_FONT_H

#include <stdint.h>
#include <stdbool.h>

// Represents a loaded TrueType font and its dynamic SDF atlas.
typedef struct Font Font;

// A baked glyph's metrics and atlas coordinates.
typedef struct GlyphMetrics {
    float u0, v0, u1, v1;   // UV bounds in the atlas
    float width, height;    // Pixel size of the glyph quad
    float xOffset, yOffset; // Drawing offset from cursor baseline
    float advance;          // How much to advance the cursor X
} GlyphMetrics;

// Loads a TTF file from the given path.
Font *Font_load(const char *path);

// Destroys the font and its atlas.
void Font_free(Font *font);

// Gets the glyph metrics for a codepoint.
// If the glyph is not in the atlas, it generates the SDF and updates the atlas texture.
// The 'pixelHeight' is the target size of the font you want to render.
bool Font_getGlyph(Font *font, uint32_t codepoint, float pixelHeight, GlyphMetrics *outMetrics);

// Returns the bindless texture ID for this font's atlas.
int32_t Font_getTextureId(const Font *font);

// Returns the line height metrics (ascent, descent, line gap) unscaled.
void Font_getVMetrics(const Font *font, float *ascent, float *descent, float *lineGap);

// Gets the scale factor for a target pixel height.
float Font_getScaleForPixelHeight(const Font *font, float height);

float Font_getKerning(const Font *font, uint32_t cp1, uint32_t cp2, float pixelHeight);

Font *Font_loadSystem(const char *familyName);

#endif // ANTI_FONT_H
