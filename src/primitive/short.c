#include "primitive/short.h"

#include <string.h>

#include "nio/mem.h"
#include "oop/type.h"
#include "annotation/overview.h"

;;OVERVIEW
/**
 * ============================================================================
 * MODULE: Short (primitive/short.c)
 * ============================================================================
 * Short primitive (Legacy: primitive/Short.java).
 *
 * FUNCTION REGISTRY:
 * ----------------------------------------------------------------------------
 * Constructors:
 *   - Short_init(void)
 *
 * Core Functions:
 *   - Short_shutdown(void)
 *   - Short_alloc(void)
 *   - Short_allocArray(count)
 *   - Short_free(ptr)
 *   - Short_compareAndSet(ptr, expected, value)
 *   - Short_type(ptr)
 *   - Short_length(ptr)
 *   - Short_allocWithValue(value)
 *
 * Setters:
 *   - Short_set(ptr, value)
 *
 * Getters:
 *   - Short_get(ptr)
 * ============================================================================
 */


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
    // Use Memory arena for arrays — BitPool is fixed 1024 slots, not for large contiguous
    size_t bytes = count * sizeof(int16_t);
    // For compound types, elem_size 2 already accounts for stride, but sizeof(int16_t) is placeholder
    // Use elem_size for true stride where c_type is int64 placeholder
    if (count > 1 && 2 != sizeof(int16_t))
        bytes = count * 2;
    return Memory_alloc(Type_make(FORM_ARRAY, ID_SHORT), bytes);
}

void Short_free(void *ptr) {
    if (!ptr)
        return;
    if (BitPool_contains(&g_shortPool, ptr)) BitPool_free(&g_shortPool, ptr);
    else Memory_free(ptr);
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
    if (BitPool_contains(&g_shortPool, ptr)) return BitPool_type(&g_shortPool, ptr);
    return Memory_type(ptr);
}

size_t Short_length(void *ptr) {
    if (!ptr)
        return 0;
    if (BitPool_contains(&g_shortPool, ptr)) return BitPool_length(&g_shortPool, ptr);
    return Memory_length(ptr);
}

void *Short_allocWithValue(int16_t value) {
    void *ptr = Short_alloc();
    if (ptr) Short_set(ptr, value);
    return ptr;
}
