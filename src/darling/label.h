#ifndef DARLING_LABEL_H
#define DARLING_LABEL_H

#include "panel.h"
#include "../font/font.h"
#include <stdint.h>

// A lightweight View component for simple, single-styled text.
typedef struct Label {
    Panel base;
    char *text;
    Font *font;
    float fontSize;
    uint32_t textColor;
} Label;

Label *Label_0(void);
Label *Label_1(Panel *parent);

void Label_setText(Label *label, const char *text);
void Label_setFont(Label *label, Font *font);
void Label_setFontSize(Label *label, float size);
void Label_setTextColor(Label *label, uint32_t color);

#endif // DARLING_LABEL_H
