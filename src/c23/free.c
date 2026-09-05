#include "c23/free.h"
#undef free // We need the real free() for Memory_free later if we call it directly, though we actually call Memory_free here.

#include "nio/mem.h"
#include "oop/type.h"
#include "objects/probable.h"
#include "annotation/overview.h"

;;OVERVIEW
/**
 * ============================================================================
 * MODULE: Free (c23/free.c)
 * LEVEL: L2 — Behavior (relational runtime behavior)
 * ============================================================================
 * The Relational Destructor Dispatcher.
 *
 * STRUCT FIELDS: none — procedural (operates on generic Memory blocks via runtime type dispatch)
 *
 * FUNCTION REGISTRY:
 * ----------------------------------------------------------------------------
 * Core Functions:
 *   - c23_free(ptr)
 * ============================================================================
 */


// Note: Add other class headers here as they get ported and need destructors.

void c23_free(void *ptr) {
    if (!ptr) return;

    uint32_t typeId = Memory_type(ptr);

    switch (typeId) {
        case TYPE_PROBABLE:
        case TYPE_PROBABLE_ARRAY:
            // Probable currently has no internal pointers to free, 
            // but if it did, we'd call Probable_destroy(ptr) here.
            break;
            
        // Future cases for Picture, Thread, Window, etc. will go here.
        /*
        case TYPE_PICTURE_SINGLETON:
            Picture_destroy(ptr); // (Not yet ported)
            break;
        */

        default:
            break;
    }

    // Once the type-specific destructor finishes, we reclaim the raw block.
    Memory_free(ptr);
}
