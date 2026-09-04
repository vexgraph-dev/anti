#include "primitive/brain.h"

#include <string.h>

#include "nio/mem.h"
#include "oop/type.h"
#include "annotation/overview.h"

;;OVERVIEW
/**
 * ============================================================================
 * MODULE: Brain (primitive/brain.c)
 * ============================================================================
 * Brain primitive (Legacy: primitive/Brain.java).
 *
 * FUNCTION REGISTRY:
 * ----------------------------------------------------------------------------
 * Constructors:
 *   - Brain_init(void)
 *
 * Core Functions:
 *   - Brain_shutdown(void)
 *   - Brain_alloc(void)
 *   - Brain_allocArray(count)
 *   - Brain_free(ptr)
 *   - Brain_compareAndSet(ptr, expected, value)
 *   - Brain_type(ptr)
 *   - Brain_length(ptr)
 *   - Brain_floatToBFloat16(value)
 *   - Brain_bFloat16ToFloat(bits)
 *   - Brain_allocWithValue(value)
 *
 * Setters:
 *   - Brain_set(ptr, value)
 *   - Brain_setFloat(ptr, value)
 *
 * Getters:
 *   - Brain_get(ptr)
 *   - Brain_getFloat(ptr)
 * ============================================================================
 */


BitPool g_brainPool;

bool Brain_init(void) {
    return BitPool_init(&g_brainPool, 2, 1024);
}

void Brain_shutdown(void) {
    BitPool_shutdown(&g_brainPool);
}

void *Brain_alloc(void) {
    return BitPool_alloc(&g_brainPool, ID_BRAIN);
}

void *Brain_allocArray(size_t count) {
    if (count == 0)
        return nullptr;
    // Use Memory arena for arrays — BitPool is fixed 1024 slots, not for large contiguous
    size_t bytes = count * sizeof(uint16_t);
    // For compound types, elem_size 2 already accounts for stride, but sizeof(uint16_t) is placeholder
    // Use elem_size for true stride where c_type is int64 placeholder
    if (count > 1 && 2 != sizeof(uint16_t))
        bytes = count * 2;
    return Memory_alloc(Type_make(FORM_ARRAY, ID_BRAIN), bytes);
}

void Brain_free(void *ptr) {
    if (!ptr)
        return;
    if (BitPool_contains(&g_brainPool, ptr)) BitPool_free(&g_brainPool, ptr);
    else Memory_free(ptr);
}

uint16_t Brain_get(void *ptr) {
    if (!ptr)
        return (uint16_t) 0;
    return *(uint16_t*) ptr;
}

void Brain_set(void *ptr, uint16_t value) {
    if (!ptr)
        return;
    *(uint16_t*) ptr = value;
}

uint16_t Brain_floatToBFloat16(float value) {
    uint32_t bits;
    memcpy(&bits, &value, sizeof(bits));
    uint32_t lsb = (bits >> 16) & 1u;
    uint32_t roundingBias = 0x7FFFu + lsb;
    bits += roundingBias;
    return (uint16_t)(bits >> 16);
}

float Brain_bFloat16ToFloat(uint16_t bits) {
    uint32_t expanded = ((uint32_t) bits) << 16;
    float out;
    memcpy(&out, &expanded, sizeof(out));
    return out;
}

float Brain_getFloat(void *ptr) {
    return Brain_bFloat16ToFloat(Brain_get(ptr));
}

void Brain_setFloat(void *ptr, float value) {
    Brain_set(ptr, Brain_floatToBFloat16(value));
}

bool Brain_compareAndSet(void *ptr, uint16_t expected, uint16_t value) {
    if (!ptr)
        return false;
    // fallback to simple CAS via __atomic_compare_exchange
    return __atomic_compare_exchange_n((uint16_t*) ptr, &expected, value, false, __ATOMIC_SEQ_CST, __ATOMIC_SEQ_CST);
}

uint32_t Brain_type(void *ptr) {
    if (!ptr)
        return 0;
    if (BitPool_contains(&g_brainPool, ptr)) return BitPool_type(&g_brainPool, ptr);
    return Memory_type(ptr);
}

size_t Brain_length(void *ptr) {
    if (!ptr)
        return 0;
    if (BitPool_contains(&g_brainPool, ptr)) return BitPool_length(&g_brainPool, ptr);
    return Memory_length(ptr);
}

void *Brain_allocWithValue(uint16_t value) {
    void *ptr = Brain_alloc();
    if (ptr) Brain_set(ptr, value);
    return ptr;
}
