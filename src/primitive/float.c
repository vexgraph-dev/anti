#include "primitive/float.h"

#include <string.h>

#include "nio/mem.h"
#include "oop/type.h"

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
    // Array form uses same pool but stamps array type
    void *ptr = BitPool_alloc(&g_floatPool, ID_FLOAT);
    if (!ptr)
        return nullptr;
    // For now, single slot alloc; array path deferred to BitPool array buckets
    (void) count;
    return ptr;
}

void Float_free(void *ptr) {
    if (!ptr)
        return;
    BitPool_free(&g_floatPool, ptr);
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
    return Memory_type(ptr);
}

size_t Float_length(void *ptr) {
    if (!ptr)
        return 0;
    return Memory_length(ptr);
}

void *Float_allocWithValue(float value) {
    void *ptr = Float_alloc();
    if (ptr) Float_set(ptr, value);
    return ptr;
}
