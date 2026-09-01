#include "primitive/int_float.h"

#include <string.h>

#include "nio/mem.h"
#include "oop/type.h"

BitPool g_int_floatPool;

bool IntFloat_init(void) {
    return BitPool_init(&g_int_floatPool, 8, 1024);
}

void IntFloat_shutdown(void) {
    BitPool_shutdown(&g_int_floatPool);
}

void *IntFloat_alloc(void) {
    return BitPool_alloc(&g_int_floatPool, ID_INT_FLOAT);
}

void *IntFloat_allocArray(size_t count) {
    if (count == 0)
        return nullptr;
    // Array form uses same pool but stamps array type
    void *ptr = BitPool_alloc(&g_int_floatPool, ID_INT_FLOAT);
    if (!ptr)
        return nullptr;
    // For now, single slot alloc; array path deferred to BitPool array buckets
    (void) count;
    return ptr;
}

void IntFloat_free(void *ptr) {
    if (!ptr)
        return;
    BitPool_free(&g_int_floatPool, ptr);
}

int64_t IntFloat_get(void *ptr) {
    if (!ptr)
        return (int64_t) 0;
    return *(int64_t*) ptr;
}

void IntFloat_set(void *ptr, int64_t value) {
    if (!ptr)
        return;
    *(int64_t*) ptr = value;
}

bool IntFloat_compareAndSet(void *ptr, int64_t expected, int64_t value) {
    if (!ptr)
        return false;
    // fallback to simple CAS via __atomic_compare_exchange
    return __atomic_compare_exchange_n((int64_t*) ptr, &expected, value, false, __ATOMIC_SEQ_CST, __ATOMIC_SEQ_CST);
}

uint32_t IntFloat_type(void *ptr) {
    if (!ptr)
        return 0;
    return Memory_type(ptr);
}

size_t IntFloat_length(void *ptr) {
    if (!ptr)
        return 0;
    return Memory_length(ptr);
}

void *IntFloat_allocWithValues(int32_t v1, float v2) {
    void *ptr = IntFloat_alloc();
    if (!ptr) return nullptr;
    *(int32_t*) ptr = v1;
    *(float*) ((uint8_t*) ptr + sizeof(int32_t)) = v2;
    return ptr;
}
