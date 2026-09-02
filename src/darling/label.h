#ifndef DARLING_LABEL_H
#define DARLING_LABEL_H

#include "panel.h"
#include "../font/font.h"
#include <stdint.h>
#include "c23/constructor.h"

// A lightweight View component for simple, single-styled text.
typedef struct Label {
    Panel base;
    char *text;
    Font *font;
    float fontSize;
    uint32_t textColor;
    float smoothness;
} Label;

Label *Label_0(void);
Label *Label_1(const char *text);
Label *Label_2(Panel *parent, const char *text);
Label *Label_1_parent(Panel *parent);

#define Label(...) CONSTRUCTOR_DISPATCH(Label, ##__VA_ARGS__)

void Label_setText(Label *label, const char *text);
void Label_setFont(Label *label, Font *font);
void Label_setFontSize(Label *label, float size);
void Label_setTextColor(Label *label, uint32_t color);
void Label_setSmoothness(Label *label, float smoothness);
void Label_setLocation(Label *label, float x, float y);
void Label_setSize(Label *label, float w, float h);
void Label_setBackgroundColor(Label *label, uint32_t color);

#endif // DARLING_LABEL_H
