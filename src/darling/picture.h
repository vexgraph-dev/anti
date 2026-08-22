#ifndef DARLING_PICTURE_H
#define DARLING_PICTURE_H

#include <stdbool.h>
#include <stdint.h>

#include "c11/constructor.h"
#include "darling/container.h"

// darling/picture.h — an image-rendering node (Legacy: darling/Picture.java).
//
// Container + image payload + draw-size overrides. AUTO (-1) sizing resolves
// at draw time from the bound image's aspect: set one dimension and the other
// derives; both AUTO = the image's raw pixel size.

#define PICTURE_AUTO (-1.0f)

typedef struct Picture {
    Container base;
    void *image;      // image/Image handle — seam lands with the image package
    float drawW;
    float drawH;
} Picture;

// Constructors:
//   Picture()          — no image, AUTO size
//   Picture(image)     — bound image, AUTO size (raw pixel dims at draw)
Picture *Picture_0(void);
Picture *Picture_1(void *image);

#define Picture(...) CONSTRUCTOR_DISPATCH(Picture, ##__VA_ARGS__)

void *Picture_getImage(const Picture *p);
void Picture_setImage(Picture *p, void *image);
float Picture_getDrawWidth(const Picture *p);
float Picture_getDrawHeight(const Picture *p);
void Picture_setDrawSize(Picture *p, float w, float h);

#endif
