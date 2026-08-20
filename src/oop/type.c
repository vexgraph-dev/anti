#include "oop/type.h"

// type.c — TypeRegister port (Legacy: oop/TypeRegister.java).
//
// The class table lives in type.h as macros; this file carries only the two
// parent-chain helpers that need real code.

uint32_t Type_getParentClass(uint32_t class_id) {
    // Buffer family: 0x50..0x63 in legacy all descend from ID_BUFFER.
    if (class_id >= 0x0050u && class_id <= 0x0063u)
        return ID_BUFFER;
    if (class_id == ID_PANEL)
        return ID_CONTAINER;
    if (class_id == ID_PICTURE)
        return ID_CONTAINER;
    if (class_id == ID_SCENE)
        return ID_PANEL;
    if (class_id == ID_SCENE2D)
        return ID_SCENE;
    if (class_id == ID_SCENE3D)
        return ID_SCENE;
    return class_id;
}

int Type_isA(uint32_t class_id, uint32_t ancestor_id) {
    uint32_t current = class_id;
    while (current != ancestor_id) {
        uint32_t parent = Type_getParentClass(current);
        if (parent == current)
            return 0;
        current = parent;
    }
    return 1;
}