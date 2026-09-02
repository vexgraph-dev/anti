#ifndef DARLING_RICH_LABEL_H
#define DARLING_RICH_LABEL_H

#include "panel.h"
#include "../text/rich_text.h"

typedef struct RichLabel {
    Panel base;
    RichText *textModel;
    WrapMode wrapMode; // Inherited by the layout engine during validation
} RichLabel;

RichLabel *RichLabel_0(void);
RichLabel *RichLabel_1(Panel *parent);

void RichLabel_setTextModel(RichLabel *label, RichText *model);
void RichLabel_setWrapMode(RichLabel *label, WrapMode mode);

#endif // DARLING_RICH_LABEL_H
