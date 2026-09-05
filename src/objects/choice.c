#include "objects/choice.h"

#include <string.h>

#include "nio/mem.h"
#include "oop/type.h"
#include "annotation/overview.h"

;;OVERVIEW
/**
 * ============================================================================
 * CLASS: Choice (objects/choice.c)
 * LEVEL: L2 — Behavior (object behavior API)
 * ============================================================================
 * Immutable deterministic choice / branch dispatcher.
 *
 * STRUCT FIELDS (local to this file):
 * ----------------------------------------------------------------------------
 *   ChoiceSlot {
 *     uint64_t objectPtr;        // option object pointer payload
 *     ChoiceCallback callback;   // per-option branch callback
 *   }
 *   Choice {
 *     size_t count;              // number of live options
 *     ChoiceSlot slots[];        // flexible option array
 *   }
 *
 * FUNCTION REGISTRY:
 * ----------------------------------------------------------------------------
 * Constructors:
 *   - Choice_3(objectPtrs, callbacks, count)
 *   - Choice_2(init, count)
 *
 * Core Functions:
 *   - Choice_free(choice)
 *   - Choice_length(choice)
 *   - Choice_trigger(choice, index, userdata)
 *
 * Getters:
 *   - Choice_getObject(choice, index)
 * ============================================================================
 */


// choice.c — Immutable deterministic choice / branch dispatcher implementation.

typedef struct ChoiceSlot {
    uint64_t       objectPtr;
    ChoiceCallback callback;
} ChoiceSlot;

typedef struct Choice {
    size_t     count;
    ChoiceSlot slots[];
} Choice;

Choice *Choice_3(const uint64_t *objectPtrs, const ChoiceCallback *callbacks, size_t count) {
    if (count == 0) return nullptr;
    uint32_t type = Type_make(FORM_SINGLETON, ID_CHOICE) | WRAP2_CHOICE;
    size_t bytes = sizeof(Choice) + count * sizeof(ChoiceSlot);
    Choice *choice = (Choice*) Memory_alloc(type, bytes);
    if (!choice)
        return nullptr;
    (*choice).count = count;
    for (size_t i = 0; i < count; i++) {
        (*choice).slots[i].objectPtr = objectPtrs ? objectPtrs[i] : 0;
        (*choice).slots[i].callback = callbacks ? callbacks[i] : nullptr;
    }
    return choice;
}


Choice *Choice_2(const Choice *init, size_t count) {
    if (count == 0) return nullptr;
    Choice *p = (Choice*) Memory_alloc(TYPE_CHOICE_ARRAY, sizeof(Choice) * count);
    if (!p) return nullptr;
    if (init) {
        for (size_t i = 0; i < count; i++) p[i] = *init;
    } else {
        memset(p, 0, sizeof(Choice) * count);
    }
    return p;
}
void Choice_free(Choice *choice) {
    if (!choice) return;
    Memory_free(choice);
}

size_t Choice_length(const Choice *choice) {
    return choice ? (*choice).count : 0;
}

uint64_t Choice_getObject(const Choice *choice, size_t index) {
    if (!choice || index >= (*choice).count)
        return 0;
    return (*choice).slots[index].objectPtr;
}

void Choice_trigger(const Choice *choice, size_t index, void *userdata) {
    if (!choice || index >= (*choice).count)
        return;
    ChoiceCallback cb = (*choice).slots[index].callback;
    if (cb) {
        cb((*choice).slots[index].objectPtr, userdata);
    }
}
