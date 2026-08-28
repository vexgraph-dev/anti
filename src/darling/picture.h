#ifndef DARLING_PICTURE_H
#define DARLING_PICTURE_H

#include <stdbool.h>
#include <stdint.h>

#include "c11/constructor.h"
#include "darling/panel.h"
#include "annotation/intention.h"

;;INTENTION("Retained-mode off-heap picture node: Panel subclass holding an Image asset with -1 auto-size from the image aspect.")

#define PICTURE_AUTO -1.0f

typedef struct Picture {
    Panel base;
    void *image; // Pointer to an Image asset (generic until Image.h is ported)
    float imageSizeW;
    float imageSizeH;
    float cropX1;
    float cropY1;
    float cropX2;
    float cropY2;
    bool hasImageSize;
    bool hasCrop;
} Picture;

Picture *Picture_0(void);
Picture *Picture_1(void *image);

#define Picture(...) CONSTRUCTOR_DISPATCH(Picture, ##__VA_ARGS__)

void *Picture_getImage(const Picture *p);
void Picture_setImage(Picture *p, void *image);

void Picture_setImageSize(Picture *p, float w, float h);
void Picture_setCrop(Picture *p, float x1, float y1, float x2, float y2);

// Layout facade — inherit from Panel
static inline void Picture_setLocation(Picture *p, float x, float y)
    { if (p) Panel_setLocation(&(*p).base, x, y); }
static inline void Picture_setSize(Picture *p, float w, float h)
    { if (p) Panel_setSize(&(*p).base, w, h); }
static inline void Picture_setParentAnchor(Picture *p, int anchor)
    { if (p) Panel_setParentAnchor(&(*p).base, anchor); }
static inline void Picture_setSelfAnchor(Picture *p, int anchor)
    { if (p) Panel_setSelfAnchor(&(*p).base, anchor); }
static inline void Picture_setBackgroundColor(Picture *p, uint32_t color)
    { if (p) Panel_setBackgroundColor(&(*p).base, color); }

#endif
