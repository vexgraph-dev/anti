#include "primitive/long.h"

#include <string.h>

#include "nio/mem.h"
#include "oop/type.h"
#include "annotation/overview.h"

;;OVERVIEW
/**
 * ============================================================================
 * MODULE: Long (primitive/long.c)
 * ============================================================================
 * Long primitive (Legacy: primitive/Long.java).
 *
 * FUNCTION REGISTRY:
 * ----------------------------------------------------------------------------
 * Constructors:
 *   - Long_init(void)
 *
 * Core Functions:
 *   - Long_shutdown(void)
 *   - Long_alloc(void)
 *   - Long_allocArray(count)
 *   - Long_free(ptr)
 *   - Long_compareAndSet(ptr, expected, value)
 *   - Long_type(ptr)
 *   - Long_length(ptr)
 *   - Long_allocWithValue(value)
 *
 * Setters:
 *   - Long_set(ptr, value)
 *
 * Getters:
 *   - Long_get(ptr)
 * ============================================================================
 */


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
    // Use Memory arena for arrays — BitPool is fixed 1024 slots, not for large contiguous
    size_t bytes = count * sizeof(int64_t);
    // For compound types, elem_size 8 already accounts for stride, but sizeof(int64_t) is placeholder
    // Use elem_size for true stride where c_type is int64 placeholder
    if (count > 1 && 8 != sizeof(int64_t))
        bytes = count * 8;
    return Memory_alloc(Type_make(FORM_ARRAY, ID_LONG), bytes);
}

void Long_free(void *ptr) {
    if (!ptr)
        return;
    if (BitPool_contains(&g_longPool, ptr)) BitPool_free(&g_longPool, ptr);
    else Memory_free(ptr);
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
    if (BitPool_contains(&g_longPool, ptr)) return BitPool_type(&g_longPool, ptr);
    return Memory_type(ptr);
}

size_t Long_length(void *ptr) {
    if (!ptr)
        return 0;
    if (BitPool_contains(&g_longPool, ptr)) return BitPool_length(&g_longPool, ptr);
    return Memory_length(ptr);
}

void *Long_allocWithValue(int64_t value) {
    void *ptr = Long_alloc();
    if (ptr) Long_set(ptr, value);
    return ptr;
}
