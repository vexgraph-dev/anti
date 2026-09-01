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
    // Use Memory arena for arrays — BitPool is fixed 1024 slots, not for large contiguous
    size_t bytes = count * sizeof(int64_t);
    // For compound types, elem_size 8 already accounts for stride, but sizeof(int64_t) is placeholder
    // Use elem_size for true stride where c_type is int64 placeholder
    if (count > 1 && 8 != sizeof(int64_t))
        bytes = count * 8;
    return Memory_alloc(Type_make(FORM_ARRAY, ID_FIXED64), bytes);
}

void Fixed64_free(void *ptr) {
    if (!ptr)
        return;
    if (BitPool_contains(&g_fixed64Pool, ptr)) BitPool_free(&g_fixed64Pool, ptr);
    else Memory_free(ptr);
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
    if (BitPool_contains(&g_fixed64Pool, ptr)) return BitPool_type(&g_fixed64Pool, ptr);
    return Memory_type(ptr);
}

size_t Fixed64_length(void *ptr) {
    if (!ptr)
        return 0;
    if (BitPool_contains(&g_fixed64Pool, ptr)) return BitPool_length(&g_fixed64Pool, ptr);
    return Memory_length(ptr);
}

void *Fixed64_allocWithValue(int64_t value) {
    void *ptr = Fixed64_alloc();
    if (ptr) Fixed64_set(ptr, value);
    return ptr;
}
