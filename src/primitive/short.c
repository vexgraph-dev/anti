#include "primitive/short.h"

#include <string.h>

#include "nio/mem.h"
#include "oop/type.h"

BitPool g_shortPool;

bool Short_init(void) {
    return BitPool_init(&g_shortPool, 2, 1024);
}

void Short_shutdown(void) {
    BitPool_shutdown(&g_shortPool);
}

void *Short_alloc(void) {
    return BitPool_alloc(&g_shortPool, ID_SHORT);
}

void *Short_allocArray(size_t count) {
    if (count == 0)
        return nullptr;
    // Array form uses same pool but stamps array type
    void *ptr = BitPool_alloc(&g_shortPool, ID_SHORT);
    if (!ptr)
        return nullptr;
    // For now, single slot alloc; array path deferred to BitPool array buckets
    (void) count;
    return ptr;
}

void Short_free(void *ptr) {
    if (!ptr)
        return;
    BitPool_free(&g_shortPool, ptr);
}

int16_t Short_get(void *ptr) {
    if (!ptr)
        return (int16_t) 0;
    return *(int16_t*) ptr;
}

void Short_set(void *ptr, int16_t value) {
    if (!ptr)
        return;
    *(int16_t*) ptr = value;
}

bool Short_compareAndSet(void *ptr, int16_t expected, int16_t value) {
    if (!ptr)
        return false;
    // fallback to simple CAS via __atomic_compare_exchange
    return __atomic_compare_exchange_n((int16_t*) ptr, &expected, value, false, __ATOMIC_SEQ_CST, __ATOMIC_SEQ_CST);
}

uint32_t Short_type(void *ptr) {
    if (!ptr)
        return 0;
    return Memory_type(ptr);
}

size_t Short_length(void *ptr) {
    if (!ptr)
        return 0;
    return Memory_length(ptr);
}
