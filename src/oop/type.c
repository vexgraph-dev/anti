#include "oop/type.h"
#include "annotation/overview.h"

;;OVERVIEW
/**
 * ============================================================================
 * MODULE: Type (oop/type.c)
 * ============================================================================
 * the TypeRegister, ported from oop/TypeRegister.java.
 *
 * FUNCTION REGISTRY:
 * ----------------------------------------------------------------------------
 * Core Functions:
 *   - Type_make(form, classId)
 *   - Type_class(typeId)
 *   - Type_form(typeId)
 *
 * Getters:
 *   - Type_isStruct(form)
 *   - Type_isSingleton(typeId)
 *   - Type_isArray(typeId)
 *   - Type_isPointer(typeId)
 *   - Type_isStructSingleton(typeId)
 *   - Type_isStructArray(typeId)
 *   - Type_isStructSOA(typeId)
 *   - Type_isStructAOS(typeId)
 *   - Type_isStructCoexistent(typeId)
 *   - Type_isStructPointer(typeId)
 *   - Type_isPrimitive(typeId)
 *   - Type_isGlobal(typeId)
 *   - Type_isLocale(typeId)
 *   - Type_isTransient(typeId)
 *   - Type_isProactive(typeId)
 *   - Type_isReactive(typeId)
 *   - Type_isProbable(typeId)
 *   - Type_isProbableObjects(typeId)
 *   - Type_isFuture(typeId)
 *   - Type_isChoice(typeId)
 *   - Type_getParentClass(classId)
 *   - Type_isA(classId, ancestorId)
 * ============================================================================
 */


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
