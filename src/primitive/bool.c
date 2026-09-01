#include "primitive/bool.h"

#include <string.h>

#include "nio/mem.h"
#include "oop/type.h"

BitPool g_boolPool;

bool Bool_init(void) {
    return BitPool_init(&g_boolPool, 1, 1024);
}

void Bool_shutdown(void) {
    BitPool_shutdown(&g_boolPool);
}

void *Bool_alloc(void) {
    return BitPool_alloc(&g_boolPool, ID_BOOL);
}

void *Bool_allocArray(size_t count) {
    if (count == 0)
        return nullptr;
    // Array form uses same pool but stamps array type
    void *ptr = BitPool_alloc(&g_boolPool, ID_BOOL);
    if (!ptr)
        return nullptr;
    // For now, single slot alloc; array path deferred to BitPool array buckets
    (void) count;
    return ptr;
}

void Bool_free(void *ptr) {
    if (!ptr)
        return;
    BitPool_free(&g_boolPool, ptr);
}

bool Bool_get(void *ptr) {
    if (!ptr)
        return (bool) 0;
    return *(bool*) ptr;
}

void Bool_set(void *ptr, bool value) {
    if (!ptr)
        return;
    *(bool*) ptr = value;
}

bool Bool_compareAndSet(void *ptr, bool expected, bool value) {
    if (!ptr)
        return false;
    // fallback to simple CAS via __atomic_compare_exchange
    return __atomic_compare_exchange_n((bool*) ptr, &expected, value, false, __ATOMIC_SEQ_CST, __ATOMIC_SEQ_CST);
}

uint32_t Bool_type(void *ptr) {
    if (!ptr)
        return 0;
    return Memory_type(ptr);
}

size_t Bool_length(void *ptr) {
    if (!ptr)
        return 0;
    return Memory_length(ptr);
}

void *Bool_allocWithValue(bool value) {
    void *ptr = Bool_alloc();
    if (ptr) Bool_set(ptr, value);
    return ptr;
}
