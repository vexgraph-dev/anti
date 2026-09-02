#ifndef ANTI_RICH_TEXT_H
#define ANTI_RICH_TEXT_H

#include <stdint.h>
#include <stdbool.h>
#include "../font/font.h"

typedef enum {
    WRAP_NONE,
    WRAP_WORD,
    WRAP_CHAR
} WrapMode;

typedef enum {
    ALIGN_LEFT,
    ALIGN_CENTER,
    ALIGN_RIGHT,
    ALIGN_JUSTIFY
} TextAlign;

typedef enum {
    DECOR_NONE,
    DECOR_LINE,
    DECOR_DASH,
    DECOR_SQUIGGLE
} TextDecoration;

#define STYLE_HAS_FONT        (1 << 0)
#define STYLE_HAS_SIZE        (1 << 1)
#define STYLE_HAS_COLOR       (1 << 2)
#define STYLE_HAS_BOLD        (1 << 3)
#define STYLE_HAS_DECOR       (1 << 4)
#define STYLE_HAS_LSPACING    (1 << 5)
#define STYLE_HAS_SHADOW      (1 << 6)

typedef struct TextStyle {
    uint32_t setFlags;
    
    Font *font;
    float size;
    uint32_t color;
    bool isBold;
    TextDecoration decor;
    uint32_t decorColor;
    float letterSpacing;
    float lineSpacing;
    
    bool hasShadow;
    float shadowX, shadowY;
    uint32_t shadowColor;
} TextStyle;

typedef enum { RUN_TEXT, RUN_NEWLINE } RunType;

typedef struct TextRun {
    RunType type;
    int startChar; 
    int length;
    
    TextStyle computedStyle;
    TextAlign align;
} TextRun;

typedef struct TextQuad {
    float x, y, w, h;
    float u0, v0, u1, v1;
    uint32_t color;
    int32_t textureId;
    float bold;
    TextDecoration decor;
} TextQuad;

typedef struct RichText {
    char *rawString;
    
    TextStyle *styles;
    size_t styleCapacity;
    
    TextRun *runs;
    size_t runCount;
    size_t runCapacity;
    
    TextQuad *quads;
    size_t quadCount;
    size_t quadCapacity;
    
    float layoutWidth;
    float layoutHeight;
    WrapMode wrapMode;
} RichText;

RichText *RichText_new(void);
void RichText_free(RichText *rt);

void RichText_setStyle(RichText *rt, int id, Font *font, float size, uint32_t color, bool bold, TextDecoration decor);
void RichText_setShadow(RichText *rt, int id, float offsetX, float offsetY, uint32_t color);
void RichText_setWrapMode(RichText *rt, WrapMode mode);

void RichText_setString(RichText *rt, const char *str);
void RichText_layout(RichText *rt, float maxWidth);

#endif // ANTI_RICH_TEXT_H
