#include "primitive/fixed32.h"

#include <string.h>

#include "nio/mem.h"
#include "oop/type.h"
#include "annotation/overview.h"

;;OVERVIEW
/**
 * ============================================================================
 * MODULE: Fixed32 (primitive/fixed32.c)
 * ============================================================================
 * Fixed32 primitive (Legacy: primitive/Fixed32.java).
 *
 * FUNCTION REGISTRY:
 * ----------------------------------------------------------------------------
 * Constructors:
 *   - Fixed32_init(void)
 *
 * Core Functions:
 *   - Fixed32_shutdown(void)
 *   - Fixed32_alloc(void)
 *   - Fixed32_allocArray(count)
 *   - Fixed32_free(ptr)
 *   - Fixed32_compareAndSet(ptr, expected, value)
 *   - Fixed32_type(ptr)
 *   - Fixed32_length(ptr)
 *   - Fixed32_allocWithValue(value)
 *
 * Setters:
 *   - Fixed32_set(ptr, value)
 *
 * Getters:
 *   - Fixed32_get(ptr)
 * ============================================================================
 */


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
    // Use Memory arena for arrays — BitPool is fixed 1024 slots, not for large contiguous
    size_t bytes = count * sizeof(int32_t);
    // For compound types, elem_size 4 already accounts for stride, but sizeof(int32_t) is placeholder
    // Use elem_size for true stride where c_type is int64 placeholder
    if (count > 1 && 4 != sizeof(int32_t))
        bytes = count * 4;
    return Memory_alloc(Type_make(FORM_ARRAY, ID_FIXED32), bytes);
}

void Fixed32_free(void *ptr) {
    if (!ptr)
        return;
    if (BitPool_contains(&g_fixed32Pool, ptr)) BitPool_free(&g_fixed32Pool, ptr);
    else Memory_free(ptr);
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
    if (BitPool_contains(&g_fixed32Pool, ptr)) return BitPool_type(&g_fixed32Pool, ptr);
    return Memory_type(ptr);
}

size_t Fixed32_length(void *ptr) {
    if (!ptr)
        return 0;
    if (BitPool_contains(&g_fixed32Pool, ptr)) return BitPool_length(&g_fixed32Pool, ptr);
    return Memory_length(ptr);
}

void *Fixed32_allocWithValue(int32_t value) {
    void *ptr = Fixed32_alloc();
    if (ptr) Fixed32_set(ptr, value);
    return ptr;
}
