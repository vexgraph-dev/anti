#include "primitive/bool.h"

#include <string.h>

#include "nio/mem.h"
#include "oop/type.h"
#include "annotation/overview.h"

;;OVERVIEW
/**
 * ============================================================================
 * MODULE: Bool (primitive/bool.c)
 * LEVEL: L2 — Behavior (primitive behavior API)
 * ============================================================================
 * Bool primitive (Legacy: primitive/Bool.java).
 *
 * STRUCT FIELDS: none — procedural (operates on BitPool/Memory blocks of bool payloads)
 *
 * FUNCTION REGISTRY:
 * ----------------------------------------------------------------------------
 * Constructors:
 *   - Bool_init(void)
 *
 * Core Functions:
 *   - Bool_shutdown(void)
 *   - Bool_alloc(void)
 *   - Bool_allocArray(count)
 *   - Bool_free(ptr)
 *   - Bool_compareAndSet(ptr, expected, value)
 *   - Bool_type(ptr)
 *   - Bool_length(ptr)
 *   - Bool_allocWithValue(value)
 *
 * Setters:
 *   - Bool_set(ptr, value)
 *
 * Getters:
 *   - Bool_get(ptr)
 * ============================================================================
 */


BitPool g_boolPool;

bool Bool_init(void) {
    return BitPool_init(&g_boolPool, 1, 1024);
}

void Bool_shutdown(void) {
    BitPool_shutdown(&g_boolPool);
}

void *Bool_alloc(void) {
    return BitPool_alloc(&g_boolPool, ID_BOOL);
}

void *Bool_allocArray(size_t count) {
    if (count == 0)
        return nullptr;
    // Use Memory arena for arrays — BitPool is fixed 1024 slots, not for large contiguous
    size_t bytes = count * sizeof(bool);
    // For compound types, elem_size 1 already accounts for stride, but sizeof(bool) is placeholder
    // Use elem_size for true stride where c_type is int64 placeholder
    if (count > 1 && 1 != sizeof(bool))
        bytes = count * 1;
    return Memory_alloc(Type_make(FORM_ARRAY, ID_BOOL), bytes);
}

void Bool_free(void *ptr) {
    if (!ptr)
        return;
    if (BitPool_contains(&g_boolPool, ptr)) BitPool_free(&g_boolPool, ptr);
    else Memory_free(ptr);
}

bool Bool_get(void *ptr) {
    if (!ptr)
        return (bool) 0;
    return *(bool*) ptr;
}

void Bool_set(void *ptr, bool value) {
    if (!ptr)
        return;
    *(bool*) ptr = value;
}

bool Bool_compareAndSet(void *ptr, bool expected, bool value) {
    if (!ptr)
        return false;
    // fallback to simple CAS via __atomic_compare_exchange
    return __atomic_compare_exchange_n((bool*) ptr, &expected, value, false, __ATOMIC_SEQ_CST, __ATOMIC_SEQ_CST);
}

uint32_t Bool_type(void *ptr) {
    if (!ptr)
        return 0;
    if (BitPool_contains(&g_boolPool, ptr)) return BitPool_type(&g_boolPool, ptr);
    return Memory_type(ptr);
}

size_t Bool_length(void *ptr) {
    if (!ptr)
        return 0;
    if (BitPool_contains(&g_boolPool, ptr)) return BitPool_length(&g_boolPool, ptr);
    return Memory_length(ptr);
}

void *Bool_allocWithValue(bool value) {
    void *ptr = Bool_alloc();
    if (ptr) Bool_set(ptr, value);
    return ptr;
}
