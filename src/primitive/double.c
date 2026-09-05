#include "primitive/double.h"

#include <string.h>

#include "nio/mem.h"
#include "oop/type.h"
#include "annotation/overview.h"

;;OVERVIEW
/**
 * ============================================================================
 * MODULE: Double (primitive/double.c)
 * LEVEL: L2 — Behavior (primitive behavior API)
 * ============================================================================
 * Double primitive (Legacy: primitive/Double.java).
 *
 * STRUCT FIELDS: none — procedural (operates on BitPool/Memory blocks of double payloads)
 *
 * FUNCTION REGISTRY:
 * ----------------------------------------------------------------------------
 * Constructors:
 *   - Double_init(void)
 *
 * Core Functions:
 *   - Double_shutdown(void)
 *   - Double_alloc(void)
 *   - Double_allocArray(count)
 *   - Double_free(ptr)
 *   - Double_compareAndSet(ptr, expected, value)
 *   - Double_type(ptr)
 *   - Double_length(ptr)
 *   - Double_allocWithValue(value)
 *
 * Setters:
 *   - Double_set(ptr, value)
 *
 * Getters:
 *   - Double_get(ptr)
 * ============================================================================
 */


BitPool g_doublePool;

bool Double_init(void) {
    return BitPool_init(&g_doublePool, 8, 1024);
}

void Double_shutdown(void) {
    BitPool_shutdown(&g_doublePool);
}

void *Double_alloc(void) {
    return BitPool_alloc(&g_doublePool, ID_DOUBLE);
}

void *Double_allocArray(size_t count) {
    if (count == 0)
        return nullptr;
    // Use Memory arena for arrays — BitPool is fixed 1024 slots, not for large contiguous
    size_t bytes = count * sizeof(double);
    // For compound types, elem_size 8 already accounts for stride, but sizeof(double) is placeholder
    // Use elem_size for true stride where c_type is int64 placeholder
    if (count > 1 && 8 != sizeof(double))
        bytes = count * 8;
    return Memory_alloc(Type_make(FORM_ARRAY, ID_DOUBLE), bytes);
}

void Double_free(void *ptr) {
    if (!ptr)
        return;
    if (BitPool_contains(&g_doublePool, ptr)) BitPool_free(&g_doublePool, ptr);
    else Memory_free(ptr);
}

double Double_get(void *ptr) {
    if (!ptr)
        return (double) 0;
    return *(double*) ptr;
}

void Double_set(void *ptr, double value) {
    if (!ptr)
        return;
    *(double*) ptr = value;
}

bool Double_compareAndSet(void *ptr, double expected, double value) {
    if (!ptr)
        return false;
    int64_t expBits;
    int64_t valBits;
    memcpy(&expBits, &expected, sizeof(expBits));
    memcpy(&valBits, &value, sizeof(valBits));
    return __atomic_compare_exchange_n((int64_t*) ptr, &expBits, valBits, false, __ATOMIC_SEQ_CST, __ATOMIC_SEQ_CST);
}

uint32_t Double_type(void *ptr) {
    if (!ptr)
        return 0;
    if (BitPool_contains(&g_doublePool, ptr)) return BitPool_type(&g_doublePool, ptr);
    return Memory_type(ptr);
}

size_t Double_length(void *ptr) {
    if (!ptr)
        return 0;
    if (BitPool_contains(&g_doublePool, ptr)) return BitPool_length(&g_doublePool, ptr);
    return Memory_length(ptr);
}

void *Double_allocWithValue(double value) {
    void *ptr = Double_alloc();
    if (ptr) Double_set(ptr, value);
    return ptr;
}
