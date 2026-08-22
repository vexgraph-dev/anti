#include "objects/local.h"

#include <string.h>

#include "nio/mem.h"
#include "oop/type.h"

// local.c — Thread-local variable slot array object wrapper implementation.

typedef struct Local {
    uint64_t slots[LOCAL_THREAD_MAX];
} Local;

Local *Local_allocate(void) {
    uint32_t type = Type_make(FORM_SINGLETON, ID_LOCAL) | MOD_LOCALE;
    Local *local = (Local *)Memory_alloc(type, sizeof(Local));
    if (!local)
        return NULL;
    memset((*local).slots, 0, sizeof((*local).slots));
    return local;
}

void Local_free(Local *local) {
    if (!local) return;
    Memory_free(local);
}

uint64_t Local_get(const Local *local, uint32_t threadId) {
    if (!local || threadId >= LOCAL_THREAD_MAX)
        return 0;
    return (*local).slots[threadId];
}

void Local_set(Local *local, uint32_t threadId, uint64_t value) {
    if (!local || threadId >= LOCAL_THREAD_MAX)
        return;
    (*local).slots[threadId] = value;
}
