#include "primitive/long_double.h"

#include <string.h>

#include "nio/mem.h"
#include "oop/type.h"

BitPool g_long_doublePool;

bool LongDouble_init(void) {
    return BitPool_init(&g_long_doublePool, 16, 1024);
}

void LongDouble_shutdown(void) {
    BitPool_shutdown(&g_long_doublePool);
}

void *LongDouble_alloc(void) {
    return BitPool_alloc(&g_long_doublePool, ID_LONG_DOUBLE);
}

void *LongDouble_allocArray(size_t count) {
    if (count == 0)
        return nullptr;
    // Use Memory arena for arrays — BitPool is fixed 1024 slots, not for large contiguous
    size_t bytes = count * sizeof(int64_t);
    // For compound types, elem_size 16 already accounts for stride, but sizeof(int64_t) is placeholder
    // Use elem_size for true stride where c_type is int64 placeholder
    if (count > 1 && 16 != sizeof(int64_t))
        bytes = count * 16;
    return Memory_alloc(Type_make(FORM_ARRAY, ID_LONG_DOUBLE), bytes);
}

void LongDouble_free(void *ptr) {
    if (!ptr)
        return;
    BitPool_free(&g_long_doublePool, ptr);
}

int64_t LongDouble_get(void *ptr) {
    if (!ptr)
        return (int64_t) 0;
    return *(int64_t*) ptr;
}

void LongDouble_set(void *ptr, int64_t value) {
    if (!ptr)
        return;
    *(int64_t*) ptr = value;
}

bool LongDouble_compareAndSet(void *ptr, int64_t expected, int64_t value) {
    if (!ptr)
        return false;
    // fallback to simple CAS via __atomic_compare_exchange
    return __atomic_compare_exchange_n((int64_t*) ptr, &expected, value, false, __ATOMIC_SEQ_CST, __ATOMIC_SEQ_CST);
}

uint32_t LongDouble_type(void *ptr) {
    if (!ptr)
        return 0;
    return Memory_type(ptr);
}

size_t LongDouble_length(void *ptr) {
    if (!ptr)
        return 0;
    return Memory_length(ptr);
}

void *LongDouble_allocWithValues(int64_t v1, double v2) {
    void *ptr = LongDouble_alloc();
    if (!ptr) return nullptr;
    *(int64_t*) ptr = v1;
    *(double*) ((uint8_t*) ptr + sizeof(int64_t)) = v2;
    return ptr;
}
