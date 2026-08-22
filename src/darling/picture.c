#include "darling/picture.h"

#include "annotation/incomplete.h"
#include "nio/mem.h"
#include "oop/type.h"

// darling/picture.c — picture node (Legacy: darling/Picture.java).

;;INCOMPLETE // AUTO-size resolution needs image/Image intrinsic dimensions;
;;INCOMPLETE // until that package lands, resolve falls back to stored box size.

Picture *Picture_0(void) {
    Picture *p = (Picture *)Memory_alloc(TYPE_PICTURE_SINGLETON, sizeof(Picture));
    if (!p)
        return NULL;

    Container *b = Container_0();
    if (!b) {
        Memory_free(p);
        return NULL;
    }
    (*p).base = (*b);
    Memory_free(b);

    (*p).image = NULL;
    (*p).drawW = PICTURE_AUTO;
    (*p).drawH = PICTURE_AUTO;
    return p;
}

Picture *Picture_1(void *image) {
    Picture *p = Picture_0();
    if (p)
        (*p).image = image;
    return p;
}

void *Picture_getImage(const Picture *p) {
    return p ? (*p).image : NULL;
}

void Picture_setImage(Picture *p, void *image) {
    if (!p)
        return;
    (*p).image = image;
    Container_markDirty(&(*p).base);
}

float Picture_getDrawWidth(const Picture *p) {
    return p ? (*p).drawW : PICTURE_AUTO;
}

float Picture_getDrawHeight(const Picture *p) {
    return p ? (*p).drawH : PICTURE_AUTO;
}

void Picture_setDrawSize(Picture *p, float w, float h) {
    if (!p)
        return;
    (*p).drawW = w;
    (*p).drawH = h;
    Container_markDirty(&(*p).base);
}
