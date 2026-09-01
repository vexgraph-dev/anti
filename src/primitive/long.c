#include "primitive/long.h"

#include <string.h>

#include "nio/mem.h"
#include "oop/type.h"

BitPool g_longPool;

bool Long_init(void) {
    return BitPool_init(&g_longPool, 8, 1024);
}

void Long_shutdown(void) {
    BitPool_shutdown(&g_longPool);
}

void *Long_alloc(void) {
    return BitPool_alloc(&g_longPool, ID_LONG);
}

void *Long_allocArray(size_t count) {
    if (count == 0)
        return nullptr;
    // Array form uses same pool but stamps array type
    void *ptr = BitPool_alloc(&g_longPool, ID_LONG);
    if (!ptr)
        return nullptr;
    // For now, single slot alloc; array path deferred to BitPool array buckets
    (void) count;
    return ptr;
}

void Long_free(void *ptr) {
    if (!ptr)
        return;
    BitPool_free(&g_longPool, ptr);
}

int64_t Long_get(void *ptr) {
    if (!ptr)
        return (int64_t) 0;
    return *(int64_t*) ptr;
}

void Long_set(void *ptr, int64_t value) {
    if (!ptr)
        return;
    *(int64_t*) ptr = value;
}

bool Long_compareAndSet(void *ptr, int64_t expected, int64_t value) {
    if (!ptr)
        return false;
    // fallback to simple CAS via __atomic_compare_exchange
    return __atomic_compare_exchange_n((int64_t*) ptr, &expected, value, false, __ATOMIC_SEQ_CST, __ATOMIC_SEQ_CST);
}

uint32_t Long_type(void *ptr) {
    if (!ptr)
        return 0;
    return Memory_type(ptr);
}

size_t Long_length(void *ptr) {
    if (!ptr)
        return 0;
    return Memory_length(ptr);
}

void *Long_allocWithValue(int64_t value) {
    void *ptr = Long_alloc();
    if (ptr) Long_set(ptr, value);
    return ptr;
}
