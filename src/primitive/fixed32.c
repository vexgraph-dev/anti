#include "primitive/fixed32.h"

#include <string.h>

#include "nio/mem.h"
#include "oop/type.h"

BitPool g_fixed32Pool;

bool Fixed32_init(void) {
    return BitPool_init(&g_fixed32Pool, 4, 1024);
}

void Fixed32_shutdown(void) {
    BitPool_shutdown(&g_fixed32Pool);
}

void *Fixed32_alloc(void) {
    return BitPool_alloc(&g_fixed32Pool, ID_FIXED32);
}

void *Fixed32_allocArray(size_t count) {
    if (count == 0)
        return nullptr;
    // Array form uses same pool but stamps array type
    void *ptr = BitPool_alloc(&g_fixed32Pool, ID_FIXED32);
    if (!ptr)
        return nullptr;
    // For now, single slot alloc; array path deferred to BitPool array buckets
    (void) count;
    return ptr;
}

void Fixed32_free(void *ptr) {
    if (!ptr)
        return;
    BitPool_free(&g_fixed32Pool, ptr);
}

int32_t Fixed32_get(void *ptr) {
    if (!ptr)
        return (int32_t) 0;
    return *(int32_t*) ptr;
}

void Fixed32_set(void *ptr, int32_t value) {
    if (!ptr)
        return;
    *(int32_t*) ptr = value;
}

bool Fixed32_compareAndSet(void *ptr, int32_t expected, int32_t value) {
    if (!ptr)
        return false;
    // fallback to simple CAS via __atomic_compare_exchange
    return __atomic_compare_exchange_n((int32_t*) ptr, &expected, value, false, __ATOMIC_SEQ_CST, __ATOMIC_SEQ_CST);
}

uint32_t Fixed32_type(void *ptr) {
    if (!ptr)
        return 0;
    return Memory_type(ptr);
}

size_t Fixed32_length(void *ptr) {
    if (!ptr)
        return 0;
    return Memory_length(ptr);
}
