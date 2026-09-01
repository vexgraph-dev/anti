#include "primitive/brain.h"

#include <string.h>

#include "nio/mem.h"
#include "oop/type.h"

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
    // Array form uses same pool but stamps array type
    void *ptr = BitPool_alloc(&g_brainPool, ID_BRAIN);
    if (!ptr)
        return nullptr;
    // For now, single slot alloc; array path deferred to BitPool array buckets
    (void) count;
    return ptr;
}

void Brain_free(void *ptr) {
    if (!ptr)
        return;
    BitPool_free(&g_brainPool, ptr);
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
    return Memory_type(ptr);
}

size_t Brain_length(void *ptr) {
    if (!ptr)
        return 0;
    return Memory_length(ptr);
}

void *Brain_allocWithValue(uint16_t value) {
    void *ptr = Brain_alloc();
    if (ptr) Brain_set(ptr, value);
    return ptr;
}
