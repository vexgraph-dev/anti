#include "oop/type.h"

// type.c — TypeRegister port (Legacy: oop/TypeRegister.java).
//
// The class table lives in type.h as macros; this file carries only the two
// parent-chain helpers that need real code.

uint32_t Type_getParentClass(uint32_t classId) {
    // Buffer family: 0x50..0x63 in legacy all descend from ID_BUFFER.
    if (classId >= 0x0050u && classId <= 0x0063u)
        return ID_BUFFER;
    if (classId == ID_PANEL)
        return ID_CONTAINER;
    if (classId == ID_PICTURE)
        return ID_CONTAINER;
    if (classId == ID_SCENE)
        return ID_PANEL;
    if (classId == ID_SCENE2D)
        return ID_SCENE;
    if (classId == ID_SCENE3D)
        return ID_SCENE;
    return classId;
}

int Type_isA(uint32_t classId, uint32_t ancestorId) {
    uint32_t current = classId;
    while (current != ancestorId) {
        uint32_t parent = Type_getParentClass(current);
        if (parent == current)
            return 0;
        current = parent;
    }
    return 1;
}