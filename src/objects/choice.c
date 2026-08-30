#include "objects/choice.h"

#include <string.h>

#include "nio/mem.h"
#include "oop/type.h"

// choice.c — Immutable deterministic choice / branch dispatcher implementation.

typedef struct ChoiceSlot {
    uint64_t       objectPtr;
    ChoiceCallback callback;
} ChoiceSlot;

typedef struct Choice {
    size_t     count;
    ChoiceSlot slots[];
} Choice;

Choice *Choice_allocate(const uint64_t *objectPtrs, const ChoiceCallback *callbacks, size_t count) {
    if (count == 0) return nullptr;
    uint32_t type = Type_make(FORM_SINGLETON, ID_CHOICE) | WRAP2_CHOICE;
    size_t bytes = sizeof(Choice) + count * sizeof(ChoiceSlot);
    Choice *choice = (Choice *)Memory_alloc(type, bytes);
    if (!choice)
        return nullptr;
    (*choice).count = count;
    for (size_t i = 0; i < count; i++) {
        (*choice).slots[i].objectPtr = objectPtrs ? objectPtrs[i] : 0;
        (*choice).slots[i].callback = callbacks ? callbacks[i] : nullptr;
    }
    return choice;
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
