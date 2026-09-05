#include "oop/type.h"
#include "annotation/overview.h"

;;OVERVIEW
/**
 * ============================================================================
 * CLASS: Type (oop/type.c)
 * LEVEL: L1 — File Metadata (type-id metadata registry)
 * ============================================================================
 * the TypeRegister, ported from oop/TypeRegister.java.
 *
 * STRUCT FIELDS (Mirroring oop/type.h):
 * ----------------------------------------------------------------------------
 *   TypeHeader {
 *     uint32_t typeId; // block-header type id
 *     uint32_t length; // payload length
 *   }
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
 *   - Type_arch(classId)
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
    // Darling UI tree: everything panels-up descends from Panel,
    // Panel descends from Container (the root).
    if (classId == ID_PANEL)
        return ID_CONTAINER;
    if (classId == ID_PICTURE)
        return ID_PANEL;
    if (classId == ID_LABEL)
        return ID_PANEL;
    if (classId == ID_RICH_LABEL)
        return ID_PANEL;
    if (classId == ID_SCENE)
        return ID_PANEL;
    if (classId == ID_SCENE2D)
        return ID_SCENE;
    if (classId == ID_SCENE3D)
        return ID_SCENE;
    if (classId == ID_CANVAS)
        return ID_CANVAS;
    if (classId == ID_CONTAINER)
        return ID_CONTAINER;
    return classId;
}

uint32_t Type_arch(uint32_t classId) {
    switch (classId) {
        case ID_CONTAINER:
        case ID_PANEL:
        case ID_PICTURE:
        case ID_LABEL:
        case ID_SCENE:
        case ID_SCENE2D:
        case ID_SCENE3D:
        case ID_RICH_LABEL:
        case ID_CANVAS:
            return ARCH_DARLING;
        case ID_THREAD:
        case ID_THREAD_NETWORKING:
        case ID_THREAD_EVENT:
        case ID_THREAD_DRAW:
        case ID_THREAD_SCRIPTING:
        case ID_THREAD_UI:
            return ARCH_HOTCWAP;
        default:
            return ARCH_VEXSPOKE;
    }
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
