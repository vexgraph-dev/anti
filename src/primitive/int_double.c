#include "primitive/int_double.h"

#include <string.h>

#include "nio/mem.h"
#include "oop/type.h"
#include "annotation/overview.h"

;;OVERVIEW
/**
 * ============================================================================
 * MODULE: Int_double (primitive/int_double.c)
 * ============================================================================
 * IntDouble primitive (Legacy: primitive/IntDouble.java).
 *
 * FUNCTION REGISTRY:
 * ----------------------------------------------------------------------------
 * Constructors:
 *   - IntDouble_init(void)
 *
 * Core Functions:
 *   - IntDouble_shutdown(void)
 *   - IntDouble_alloc(void)
 *   - IntDouble_allocArray(count)
 *   - IntDouble_free(ptr)
 *   - IntDouble_compareAndSet(ptr, expected, value)
 *   - IntDouble_type(ptr)
 *   - IntDouble_length(ptr)
 *   - IntDouble_allocWithValues(v1, v2)
 *
 * Setters:
 *   - IntDouble_set(ptr, value)
 *
 * Getters:
 *   - IntDouble_get(ptr)
 * ============================================================================
 */


BitPool g_int_doublePool;

bool IntDouble_init(void) {
    return BitPool_init(&g_int_doublePool, 16, 1024);
}

void IntDouble_shutdown(void) {
    BitPool_shutdown(&g_int_doublePool);
}

void *IntDouble_alloc(void) {
    return BitPool_alloc(&g_int_doublePool, ID_INT_DOUBLE);
}

void *IntDouble_allocArray(size_t count) {
    if (count == 0)
        return nullptr;
    // Use Memory arena for arrays — BitPool is fixed 1024 slots, not for large contiguous
    size_t bytes = count * sizeof(int64_t);
    // For compound types, elem_size 16 already accounts for stride, but sizeof(int64_t) is placeholder
    // Use elem_size for true stride where c_type is int64 placeholder
    if (count > 1 && 16 != sizeof(int64_t))
        bytes = count * 16;
    return Memory_alloc(Type_make(FORM_ARRAY, ID_INT_DOUBLE), bytes);
}

void IntDouble_free(void *ptr) {
    if (!ptr)
        return;
    if (BitPool_contains(&g_int_doublePool, ptr)) BitPool_free(&g_int_doublePool, ptr);
    else Memory_free(ptr);
}

int64_t IntDouble_get(void *ptr) {
    if (!ptr)
        return (int64_t) 0;
    return *(int64_t*) ptr;
}

void IntDouble_set(void *ptr, int64_t value) {
    if (!ptr)
        return;
    *(int64_t*) ptr = value;
}

bool IntDouble_compareAndSet(void *ptr, int64_t expected, int64_t value) {
    if (!ptr)
        return false;
    // fallback to simple CAS via __atomic_compare_exchange
    return __atomic_compare_exchange_n((int64_t*) ptr, &expected, value, false, __ATOMIC_SEQ_CST, __ATOMIC_SEQ_CST);
}

uint32_t IntDouble_type(void *ptr) {
    if (!ptr)
        return 0;
    if (BitPool_contains(&g_int_doublePool, ptr)) return BitPool_type(&g_int_doublePool, ptr);
    return Memory_type(ptr);
}

size_t IntDouble_length(void *ptr) {
    if (!ptr)
        return 0;
    if (BitPool_contains(&g_int_doublePool, ptr)) return BitPool_length(&g_int_doublePool, ptr);
    return Memory_length(ptr);
}

void *IntDouble_allocWithValues(int32_t v1, double v2) {
    void *ptr = IntDouble_alloc();
    if (!ptr) return nullptr;
    *(int32_t*) ptr = v1;
    *(double*) ((uint8_t*) ptr + sizeof(int32_t)) = v2;
    return ptr;
}
