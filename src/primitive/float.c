#include "primitive/float.h"

#include <string.h>

#include "nio/mem.h"
#include "oop/type.h"
#include "annotation/overview.h"

;;OVERVIEW
/**
 * ============================================================================
 * MODULE: Float (primitive/float.c)
 * LEVEL: L2 — Behavior (primitive behavior API)
 * ============================================================================
 * Float primitive (Legacy: primitive/Float.java).
 *
 * FUNCTION REGISTRY:
 * ----------------------------------------------------------------------------
 * Constructors:
 *   - Float_init(void)
 *
 * Core Functions:
 *   - Float_shutdown(void)
 *   - Float_alloc(void)
 *   - Float_allocArray(count)
 *   - Float_free(ptr)
 *   - Float_compareAndSet(ptr, expected, value)
 *   - Float_type(ptr)
 *   - Float_length(ptr)
 *   - Float_allocWithValue(value)
 *
 * Setters:
 *   - Float_set(ptr, value)
 *
 * Getters:
 *   - Float_get(ptr)
 * ============================================================================
 */


BitPool g_floatPool;

bool Float_init(void) {
    return BitPool_init(&g_floatPool, 4, 1024);
}

void Float_shutdown(void) {
    BitPool_shutdown(&g_floatPool);
}

void *Float_alloc(void) {
    return BitPool_alloc(&g_floatPool, ID_FLOAT);
}

void *Float_allocArray(size_t count) {
    if (count == 0)
        return nullptr;
    // Use Memory arena for arrays — BitPool is fixed 1024 slots, not for large contiguous
    size_t bytes = count * sizeof(float);
    // For compound types, elem_size 4 already accounts for stride, but sizeof(float) is placeholder
    // Use elem_size for true stride where c_type is int64 placeholder
    if (count > 1 && 4 != sizeof(float))
        bytes = count * 4;
    return Memory_alloc(Type_make(FORM_ARRAY, ID_FLOAT), bytes);
}

void Float_free(void *ptr) {
    if (!ptr)
        return;
    if (BitPool_contains(&g_floatPool, ptr)) BitPool_free(&g_floatPool, ptr);
    else Memory_free(ptr);
}

float Float_get(void *ptr) {
    if (!ptr)
        return (float) 0;
    return *(float*) ptr;
}

void Float_set(void *ptr, float value) {
    if (!ptr)
        return;
    *(float*) ptr = value;
}

bool Float_compareAndSet(void *ptr, float expected, float value) {
    if (!ptr)
        return false;
    int32_t expBits;
    int32_t valBits;
    memcpy(&expBits, &expected, sizeof(expBits));
    memcpy(&valBits, &value, sizeof(valBits));
    return __atomic_compare_exchange_n((int32_t*) ptr, &expBits, valBits, false, __ATOMIC_SEQ_CST, __ATOMIC_SEQ_CST);
}

uint32_t Float_type(void *ptr) {
    if (!ptr)
        return 0;
    if (BitPool_contains(&g_floatPool, ptr)) return BitPool_type(&g_floatPool, ptr);
    return Memory_type(ptr);
}

size_t Float_length(void *ptr) {
    if (!ptr)
        return 0;
    if (BitPool_contains(&g_floatPool, ptr)) return BitPool_length(&g_floatPool, ptr);
    return Memory_length(ptr);
}

void *Float_allocWithValue(float value) {
    void *ptr = Float_alloc();
    if (ptr) Float_set(ptr, value);
    return ptr;
}
