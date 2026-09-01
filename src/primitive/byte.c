#include "primitive/byte.h"

#include <string.h>

#include "nio/mem.h"
#include "oop/type.h"

BitPool g_bytePool;

bool Byte_init(void) {
    return BitPool_init(&g_bytePool, 1, 1024);
}

void Byte_shutdown(void) {
    BitPool_shutdown(&g_bytePool);
}

void *Byte_alloc(void) {
    return BitPool_alloc(&g_bytePool, ID_BYTE);
}

void *Byte_allocArray(size_t count) {
    if (count == 0)
        return nullptr;
    // Array form uses same pool but stamps array type
    void *ptr = BitPool_alloc(&g_bytePool, ID_BYTE);
    if (!ptr)
        return nullptr;
    // For now, single slot alloc; array path deferred to BitPool array buckets
    (void) count;
    return ptr;
}

void Byte_free(void *ptr) {
    if (!ptr)
        return;
    BitPool_free(&g_bytePool, ptr);
}

int8_t Byte_get(void *ptr) {
    if (!ptr)
        return (int8_t) 0;
    return *(int8_t*) ptr;
}

void Byte_set(void *ptr, int8_t value) {
    if (!ptr)
        return;
    *(int8_t*) ptr = value;
}

bool Byte_compareAndSet(void *ptr, int8_t expected, int8_t value) {
    if (!ptr)
        return false;
    // fallback to simple CAS via __atomic_compare_exchange
    return __atomic_compare_exchange_n((int8_t*) ptr, &expected, value, false, __ATOMIC_SEQ_CST, __ATOMIC_SEQ_CST);
}

uint32_t Byte_type(void *ptr) {
    if (!ptr)
        return 0;
    return Memory_type(ptr);
}

size_t Byte_length(void *ptr) {
    if (!ptr)
        return 0;
    return Memory_length(ptr);
}

void *Byte_allocWithValue(int8_t value) {
    void *ptr = Byte_alloc();
    if (ptr) Byte_set(ptr, value);
    return ptr;
}
