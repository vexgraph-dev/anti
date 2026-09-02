#include "label.h"
#include "../vulkan/vk.h"
#include "../nio/mem.h"
#include "../oop/type.h"
#include <math.h>
#include <string.h>

static void Label_renderFn(Panel *panel, void *renderer, void *cmdBuffer, float x, float y, float w, float h) {
    Label *lbl = (Label*) panel;
    (void) renderer;
    uint32_t bgColor = Panel_getBackgroundColor(panel);
    if ((bgColor >> 24) > 0) {
        float br = ((bgColor >> 16) & 0xFF) / 255.0f;
        float bg = ((bgColor >> 8) & 0xFF) / 255.0f;
        float bb = (bgColor & 0xFF) / 255.0f;
        float ba = ((bgColor >> 24) & 0xFF) / 255.0f;
        Vk_fillRect(cmdBuffer, w, h, x, y, w, h, br, bg, bb, ba);
    }
    if (!(*lbl).text || !(*lbl).font || (*lbl).fontSize <= 0)
        return;
    float cr = (((*lbl).textColor >> 16) & 0xFF) / 255.0f;
    float cg = (((*lbl).textColor >> 8) & 0xFF) / 255.0f;
    float cb = ((*lbl).textColor & 0xFF) / 255.0f;
    float ca = (((*lbl).textColor >> 24) & 0xFF) / 255.0f;
    float ascent = 0;
    float descent = 0;
    float lineGap = 0;
    Font_getVMetrics((*lbl).font, &ascent, &descent, &lineGap);
    float scale = Font_getScaleForPixelHeight((*lbl).font, (*lbl).fontSize);
    float lineHeight = (ascent - descent + lineGap) * scale;
    if (lineHeight <= 0.0f)
        lineHeight = (*lbl).fontSize * 1.2f;

    GlyphMetrics spaceGm = {0};
    float spaceAdvance = (*lbl).fontSize * 0.3f;
    if (Font_getGlyph((*lbl).font, (uint32_t) ' ', (*lbl).fontSize, &spaceGm) && spaceGm.advance > 0.0f)
        spaceAdvance = spaceGm.advance;
    float tabWidth = 4.0f * spaceAdvance;

    float cx = x;
    float cy = y + (ascent * scale);
    int32_t texId = Font_getTextureId((*lbl).font);
    uint32_t prevChar = 0;
    int len = (int) strlen((*lbl).text);
    for (int i = 0; i < len; ) {
        uint32_t codepoint = 0;
        unsigned char c0 = (unsigned char) (*lbl).text[i];
        int charLen = 1;
        if (c0 < 0x80) {
            codepoint = c0;
        } else if ((c0 & 0xE0) == 0xC0) {
            if (i + 1 < len)
                codepoint = ((c0 & 0x1F) << 6) | ((*lbl).text[i + 1] & 0x3F);
            charLen = 2;
        } else if ((c0 & 0xF0) == 0xE0) {
            if (i + 2 < len)
                codepoint = ((c0 & 0x0F) << 12) | (((*lbl).text[i + 1] & 0x3F) << 6) | ((*lbl).text[i + 2] & 0x3F);
            charLen = 3;
        } else if ((c0 & 0xF8) == 0xF0) {
            if (i + 3 < len)
                codepoint = ((c0 & 0x07) << 18) | (((*lbl).text[i + 1] & 0x3F) << 12) | (((*lbl).text[i + 2] & 0x3F) << 6) | ((*lbl).text[i + 3] & 0x3F);
            charLen = 4;
        }
        if (codepoint == '\r') {
            prevChar = 0;
            i += charLen;
            continue;
        }
        if (codepoint == '\n') {
            cx = x;
            cy += lineHeight;
            prevChar = 0;
            i += charLen;
            continue;
        }
        if (codepoint == '\t') {
            if (tabWidth > 0.0f) {
                float relX = cx - x;
                cx = x + (floorf(relX / tabWidth) + 1.0f) * tabWidth;
            } else {
                cx += 4.0f * spaceAdvance;
            }
            prevChar = 0;
            i += charLen;
            continue;
        }
        GlyphMetrics gm = {0};
        if (Font_getGlyph((*lbl).font, codepoint, (*lbl).fontSize, &gm)) {
            if (prevChar != 0)
                cx += Font_getKerning((*lbl).font, prevChar, codepoint, (*lbl).fontSize);
            prevChar = codepoint;
            if (gm.width > 0.0f && gm.height > 0.0f) {
                float qx = cx + gm.xOffset;
                float qy = cy + gm.yOffset;
                Vk_drawSDFText(cmdBuffer, w, h, qx, qy, gm.width, gm.height, cr, cg, cb, ca, texId, 0.0f, (*lbl).smoothness, gm.u0, gm.v0, gm.u1, gm.v1);
            }
            cx += gm.advance;
        }
        i += charLen;
    }
}

Label *Label_0(void) {
    Label *lbl = (Label*) Memory_alloc(TYPE_PANEL_SINGLETON, sizeof(Label));
    if (!lbl)
        return NULL;
    Panel *p = Panel_0();
    if (!p) {
        Memory_free(lbl);
        return NULL;
    }
    (*lbl).base = *p;
    Memory_free(p);
    (*lbl).text = NULL;
    (*lbl).font = NULL;
    (*lbl).fontSize = 12.0f;
    (*lbl).textColor = 0xFFFFFFFF;
    (*lbl).smoothness = 0.5f;
    Panel_setRenderHandler(&(*lbl).base, Label_renderFn);
    return lbl;
}

Label *Label_1(const char *text) {
    Label *lbl = Label_0();
    if (lbl)
        Label_setText(lbl, text);
    return lbl;
}

Label *Label_1_parent(Panel *parent) {
    Label *lbl = Label_0();
    if (lbl && parent)
        Panel_addContainer(parent, &(*lbl).base);
    return lbl;
}

Label *Label_2(Panel *parent, const char *text) {
    Label *lbl = Label_1(text);
    if (lbl && parent)
        Panel_addContainer(parent, &(*lbl).base);
    return lbl;
}

void Label_setText(Label *label, const char *text) {
    if (!label)
        return;
    if ((*label).text)
        Memory_free((*label).text);
    if (text) {
        (*label).text = (char*) Memory_alloc(TYPE_ARRAY, strlen(text) + 1);
        if ((*label).text)
            strcpy((*label).text, text);
    } else {
        (*label).text = NULL;
    }
    Container_markDirty(&(*label).base.base);
}

void Label_setFont(Label *label, Font *font) {
    if (!label)
        return;
    (*label).font = font;
    Container_markDirty(&(*label).base.base);
}

void Label_setFontSize(Label *label, float size) {
    if (!label)
        return;
    (*label).fontSize = size;
    Container_markDirty(&(*label).base.base);
}

void Label_setTextColor(Label *label, uint32_t color) {
    if (!label)
        return;
    (*label).textColor = color;
    Container_markDirty(&(*label).base.base);
}

void Label_setSmoothness(Label *label, float smoothness) {
    if (!label)
        return;
    if (smoothness < 0.0f)
        smoothness = 0.0f;
    if (smoothness > 1.0f)
        smoothness = 1.0f;
    (*label).smoothness = smoothness;
    Container_markDirty(&(*label).base.base);
}

void Label_setLocation(Label *label, float x, float y) {
    if (!label)
        return;
    Panel_setLocation(&(*label).base, x, y);
}

void Label_setSize(Label *label, float w, float h) {
    if (!label)
        return;
    Panel_setSize(&(*label).base, w, h);
}

void Label_setBackgroundColor(Label *label, uint32_t color) {
    if (!label)
        return;
    Panel_setBackgroundColor(&(*label).base, color);
}
