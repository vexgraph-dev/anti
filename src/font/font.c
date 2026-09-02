#include "../nio/mem.h"
#include "../oop/type.h"
#define STB_TRUETYPE_IMPLEMENTATION
#include "stb_truetype.h"
#include "font.h"
#include "../vulkan/texture/texture.h"
#include "../util/hash.h"
#include <stdio.h>
#include <stdlib.h>
#include <string.h>

#define ATLAS_SIZE 2048
#define SDF_PADDING 5
#define SDF_ONEDGE 128
#define SDF_PIXEL_DIST_SCALE 32.0f

#define GLYPH_CACHE_INIT_CAP 128
#define GLYPH_STATE_EMPTY 0
#define GLYPH_STATE_OCCUPIED 1
#define GLYPH_STATE_DELETED 2

typedef struct GlyphSlot {
    uint32_t codepoint;
    uint32_t sizeBits;
    GlyphMetrics metrics;
    uint64_t state;
} GlyphSlot;

struct Font {
    stbtt_fontinfo info;
    unsigned char *ttf_buffer;
    uint8_t *atlas_rgba;
    int32_t textureId;
    int current_x;
    int current_y;
    int bottom_y;
    GlyphSlot *slots;
    size_t slotCap;
    size_t slotCount;
};

static uint64_t hashGlyph(uint32_t codepoint, uint32_t sizeBits) {
    uint64_t key = ((uint64_t) codepoint << 32) | (uint64_t) sizeBits;
    return Hash_murmur3Mix64(key);
}

static GlyphSlot *slotAt(Font *font, size_t index) {
    return &(*font).slots[index];
}

static void growCache(Font *font, size_t newCap) {
    size_t oldCap = (*font).slotCap;
    GlyphSlot *oldSlots = (*font).slots;
    GlyphSlot *newSlots = (GlyphSlot*) Memory_alloc(TYPE_ARRAY, newCap * sizeof(GlyphSlot));
    if (!newSlots)
        return;
    memset(newSlots, 0, newCap * sizeof(GlyphSlot));
    size_t mask = newCap - 1;
    for (size_t i = 0; i < oldCap; i++) {
        GlyphSlot *s = &oldSlots[i];
        if ((*s).state == GLYPH_STATE_OCCUPIED) {
            uint64_t h = hashGlyph((*s).codepoint, (*s).sizeBits);
            size_t idx = (size_t) (h & mask);
            while ((*(newSlots + idx)).state == GLYPH_STATE_OCCUPIED)
                idx = (idx + 1) & mask;
            *(newSlots + idx) = *s;
        }
    }
    if (oldSlots)
        Memory_free(oldSlots);
    (*font).slots = newSlots;
    (*font).slotCap = newCap;
}

static bool findGlyph(Font *font, uint32_t codepoint, uint32_t sizeBits, GlyphMetrics *outMetrics) {
    if (!(*font).slots)
        return false;
    size_t cap = (*font).slotCap;
    if (cap == 0)
        return false;
    uint64_t h = hashGlyph(codepoint, sizeBits);
    size_t mask = cap - 1;
    size_t idx = (size_t) (h & mask);
    for (size_t i = 0; i < cap; i++) {
        GlyphSlot *s = slotAt(font, idx);
        uint64_t st = (*s).state;
        if (st == GLYPH_STATE_EMPTY)
            return false;
        if (st == GLYPH_STATE_OCCUPIED && (*s).codepoint == codepoint && (*s).sizeBits == sizeBits) {
            if (outMetrics)
                *outMetrics = (*s).metrics;
            return true;
        }
        idx = (idx + 1) & mask;
    }
    return false;
}

static void insertGlyph(Font *font, uint32_t codepoint, uint32_t sizeBits, GlyphMetrics *metrics) {
    size_t cap = (*font).slotCap;
    if (cap == 0) {
        growCache(font, GLYPH_CACHE_INIT_CAP);
        cap = (*font).slotCap;
        if (cap == 0)
            return;
    }
    size_t load = cap - cap / 4;
    if ((*font).slotCount >= load) {
        growCache(font, cap * 2);
        cap = (*font).slotCap;
    }
    uint64_t h = hashGlyph(codepoint, sizeBits);
    size_t mask = cap - 1;
    size_t idx = (size_t) (h & mask);
    size_t firstDeleted = (size_t) -1;
    while (1) {
        GlyphSlot *s = slotAt(font, idx);
        uint64_t st = (*s).state;
        if (st == GLYPH_STATE_EMPTY) {
            size_t target = firstDeleted != (size_t) -1 ? firstDeleted : idx;
            GlyphSlot *t = slotAt(font, target);
            (*t).codepoint = codepoint;
            (*t).sizeBits = sizeBits;
            (*t).metrics = *metrics;
            (*t).state = GLYPH_STATE_OCCUPIED;
            (*font).slotCount++;
            return;
        }
        if (st == GLYPH_STATE_DELETED) {
            if (firstDeleted == (size_t) -1)
                firstDeleted = idx;
        } else if (st == GLYPH_STATE_OCCUPIED) {
            if ((*s).codepoint == codepoint && (*s).sizeBits == sizeBits) {
                (*s).metrics = *metrics;
                return;
            }
        }
        idx = (idx + 1) & mask;
    }
}

Font *Font_load(const char *path) {
    FILE *f = fopen(path, "rb");
    if (!f) {
        printf("Failed to open font: %s\n", path);
        return NULL;
    }
    fseek(f, 0, SEEK_END);
    size_t size = (size_t) ftell(f);
    fseek(f, 0, SEEK_SET);
    unsigned char *ttf_buffer = (unsigned char*) Memory_alloc(TYPE_ARRAY, size);
    if (!ttf_buffer) {
        fclose(f);
        return NULL;
    }
    fread(ttf_buffer, 1, size, f);
    fclose(f);
    Font *font = (Font*) Memory_alloc(TYPE_PANEL_SINGLETON, sizeof(Font));
    if (font)
        memset(font, 0, sizeof(Font));
    if (!font) {
        Memory_free(ttf_buffer);
        return NULL;
    }
    (*font).ttf_buffer = ttf_buffer;
    if (!stbtt_InitFont(&(*font).info, (*font).ttf_buffer, 0)) {
        Memory_free(ttf_buffer);
        (*font).ttf_buffer = NULL;
        Memory_free(font);
        return NULL;
    }
    (*font).atlas_rgba = (uint8_t*) Memory_alloc(TYPE_ARRAY, ATLAS_SIZE * ATLAS_SIZE * 4);
    if ((*font).atlas_rgba)
        memset((*font).atlas_rgba, 0, ATLAS_SIZE * ATLAS_SIZE * 4);
    (*font).textureId = Texture_loadRaw((*font).atlas_rgba, ATLAS_SIZE, ATLAS_SIZE);
    (*font).current_x = 0;
    (*font).current_y = 0;
    (*font).bottom_y = 0;
    (*font).slots = (GlyphSlot*) Memory_alloc(TYPE_ARRAY, GLYPH_CACHE_INIT_CAP * sizeof(GlyphSlot));
    if ((*font).slots) {
        memset((*font).slots, 0, GLYPH_CACHE_INIT_CAP * sizeof(GlyphSlot));
        (*font).slotCap = GLYPH_CACHE_INIT_CAP;
        (*font).slotCount = 0;
    }
    return font;
}

void Font_free(Font *font) {
    if (!font)
        return;
    if ((*font).ttf_buffer)
        Memory_free((*font).ttf_buffer);
    if ((*font).atlas_rgba)
        Memory_free((*font).atlas_rgba);
    if ((*font).slots)
        Memory_free((*font).slots);
    Memory_free(font);
}

float Font_getScaleForPixelHeight(const Font *font, float height) {
    return stbtt_ScaleForPixelHeight(&(*font).info, height);
}

void Font_getVMetrics(const Font *font, float *ascent, float *descent, float *lineGap) {
    int a = 0;
    int d = 0;
    int l = 0;
    stbtt_GetFontVMetrics(&(*font).info, &a, &d, &l);
    if (ascent)
        *ascent = (float) a;
    if (descent)
        *descent = (float) d;
    if (lineGap)
        *lineGap = (float) l;
}

bool Font_getGlyph(Font *font, uint32_t codepoint, float pixelHeight, GlyphMetrics *outMetrics) {
    if (!font)
        return false;
    uint32_t sizeBits = 0;
    memcpy(&sizeBits, &pixelHeight, sizeof(float));
    GlyphMetrics cached = {0};
    if (findGlyph(font, codepoint, sizeBits, &cached)) {
        if (outMetrics)
            *outMetrics = cached;
        return true;
    }
    float scale = stbtt_ScaleForPixelHeight(&(*font).info, pixelHeight);
    int w = 0;
    int h = 0;
    int xoff = 0;
    int yoff = 0;
    unsigned char *sdf = stbtt_GetCodepointSDF(&(*font).info, scale, (int) codepoint, SDF_PADDING, SDF_ONEDGE, SDF_PIXEL_DIST_SCALE, &w, &h, &xoff, &yoff);
    if (!sdf)
        return false;
    if ((*font).current_x + w > ATLAS_SIZE) {
        (*font).current_x = 0;
        (*font).current_y = (*font).bottom_y;
    }
    if ((*font).current_y + h > ATLAS_SIZE) {
        free(sdf);
        return false;
    }
    int destX = (*font).current_x;
    int destY = (*font).current_y;
    for (int y = 0; y < h; y++) {
        for (int x = 0; x < w; x++) {
            int srcIdx = y * w + x;
            int dstIdx = ((destY + y) * ATLAS_SIZE + (destX + x)) * 4;
            unsigned char dist = sdf[srcIdx];
            (*font).atlas_rgba[dstIdx + 0] = dist;
            (*font).atlas_rgba[dstIdx + 1] = dist;
            (*font).atlas_rgba[dstIdx + 2] = dist;
            (*font).atlas_rgba[dstIdx + 3] = dist;
        }
    }
    uint8_t *tmp = (uint8_t*) Memory_alloc(TYPE_ARRAY, (size_t) (w * h * 4));
    int tmpIsManaged = 1;
    if (!tmp) {
        tmp = (uint8_t*) malloc((size_t) (w * h * 4));
        tmpIsManaged = 0;
    }
    if (tmp) {
        for (int i = 0; i < w * h; i++) {
            unsigned char dist = sdf[i];
            tmp[i * 4 + 0] = dist;
            tmp[i * 4 + 1] = dist;
            tmp[i * 4 + 2] = dist;
            tmp[i * 4 + 3] = dist;
        }
        Texture_updateSubRaw((*font).textureId, tmp, (uint32_t) destX, (uint32_t) destY, (uint32_t) w, (uint32_t) h);
        if (tmpIsManaged)
            Memory_free(tmp);
        else
            free(tmp);
    }
    free(sdf);
    (*font).current_x += w + 1;
    if ((*font).current_y + h > (*font).bottom_y)
        (*font).bottom_y = (*font).current_y + h + 1;
    int advanceWidth = 0;
    int leftSideBearing = 0;
    stbtt_GetCodepointHMetrics(&(*font).info, (int) codepoint, &advanceWidth, &leftSideBearing);
    GlyphMetrics gm = {0};
    gm.u0 = (float) destX / (float) ATLAS_SIZE;
    gm.v0 = (float) destY / (float) ATLAS_SIZE;
    gm.u1 = (float) (destX + w) / (float) ATLAS_SIZE;
    gm.v1 = (float) (destY + h) / (float) ATLAS_SIZE;
    gm.width = (float) w;
    gm.height = (float) h;
    gm.xOffset = (float) xoff;
    gm.yOffset = (float) yoff;
    gm.advance = (float) advanceWidth * scale;
    insertGlyph(font, codepoint, sizeBits, &gm);
    if (outMetrics)
        *outMetrics = gm;
    return true;
}

int32_t Font_getTextureId(const Font *font) {
    if (!font)
        return -1;
    return (*font).textureId;
}

float Font_getKerning(const Font *font, uint32_t cp1, uint32_t cp2, float pixelHeight) {
    if (!font)
        return 0.0f;
    float scale = stbtt_ScaleForPixelHeight(&(*font).info, pixelHeight);
    return (float) stbtt_GetCodepointKernAdvance(&(*font).info, (int) cp1, (int) cp2) * scale;
}

extern char* System_getFontPath(const char* familyName);

Font *Font_loadSystem(const char *familyName) {
    char *path = System_getFontPath(familyName);
    if (!path) {
        printf("Font_loadSystem: Could not find font '%s'\n", familyName);
        return NULL;
    }
    Font *f = Font_load(path);
    free(path);
    return f;
}
