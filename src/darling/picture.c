#include "darling/picture.h"

#include "nio/mem.h"
#include "oop/type.h"

// darling/picture.c — UI panel displaying an image (Legacy: darling/Picture.java)

Picture *Picture_0(void) {
    Picture *p = (Picture *)Memory_alloc(TYPE_PICTURE_SINGLETON, sizeof(Picture));
    if (!p) return NULL;

    // Allocate the base Panel structure correctly, mimicking Scene allocation.
    // Panel_0() gives us a fully initialized UI panel.
    Panel *basePanel = Panel_0();
    if (!basePanel) {
        Memory_free(p);
        return NULL;
    }
    
    // Copy initialized state into our base struct, then free the heap-allocated one
    (*p).base = (*basePanel);
    Memory_free(basePanel);

    // Initialize Picture-specific fields (default matching legacy initDefaults)
    (*p).image = NULL;
    (*p).imageSizeW = 0.0f;
    (*p).imageSizeH = 0.0f;
    (*p).cropX1 = 0.0f;
    (*p).cropY1 = 0.0f;
    (*p).cropX2 = 0.0f;
    (*p).cropY2 = 0.0f;
    (*p).hasImageSize = false;
    (*p).hasCrop = false;

    return p;
}

Picture *Picture_1(void *image) {
    Picture *p = Picture_0();
    if (p) {
        (*p).image = image;
    }
    return p;
}

void *Picture_getImage(const Picture *p) {
    return p ? (*p).image : NULL;
}

void Picture_setImage(Picture *p, void *image) {
    if (p) {
        (*p).image = image;
        Container_markDirty(&(*p).base.base); // Panel's base is Container
    }
}

void Picture_setImageSize(Picture *p, float w, float h) {
    if (p) {
        (*p).imageSizeW = w;
        (*p).imageSizeH = h;
        (*p).hasImageSize = true;
        Container_markDirty(&(*p).base.base);
    }
}

void Picture_setCrop(Picture *p, float x1, float y1, float x2, float y2) {
    if (p) {
        (*p).cropX1 = x1;
        (*p).cropY1 = y1;
        (*p).cropX2 = x2;
        (*p).cropY2 = y2;
        (*p).hasCrop = true;
        Container_markDirty(&(*p).base.base);
    }
}
