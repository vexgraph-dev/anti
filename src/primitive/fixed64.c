#include "primitive/fixed64.h"

#include <string.h>

#include "nio/mem.h"
#include "oop/type.h"

BitPool g_fixed64Pool;

bool Fixed64_init(void) {
    return BitPool_init(&g_fixed64Pool, 8, 1024);
}

void Fixed64_shutdown(void) {
    BitPool_shutdown(&g_fixed64Pool);
}

void *Fixed64_alloc(void) {
    return BitPool_alloc(&g_fixed64Pool, ID_FIXED64);
}

void *Fixed64_allocArray(size_t count) {
    if (count == 0)
        return nullptr;
    // Array form uses same pool but stamps array type
    void *ptr = BitPool_alloc(&g_fixed64Pool, ID_FIXED64);
    if (!ptr)
        return nullptr;
    // For now, single slot alloc; array path deferred to BitPool array buckets
    (void) count;
    return ptr;
}

void Fixed64_free(void *ptr) {
    if (!ptr)
        return;
    BitPool_free(&g_fixed64Pool, ptr);
}

int64_t Fixed64_get(void *ptr) {
    if (!ptr)
        return (int64_t) 0;
    return *(int64_t*) ptr;
}

void Fixed64_set(void *ptr, int64_t value) {
    if (!ptr)
        return;
    *(int64_t*) ptr = value;
}

bool Fixed64_compareAndSet(void *ptr, int64_t expected, int64_t value) {
    if (!ptr)
        return false;
    // fallback to simple CAS via __atomic_compare_exchange
    return __atomic_compare_exchange_n((int64_t*) ptr, &expected, value, false, __ATOMIC_SEQ_CST, __ATOMIC_SEQ_CST);
}

uint32_t Fixed64_type(void *ptr) {
    if (!ptr)
        return 0;
    return Memory_type(ptr);
}

size_t Fixed64_length(void *ptr) {
    if (!ptr)
        return 0;
    return Memory_length(ptr);
}
