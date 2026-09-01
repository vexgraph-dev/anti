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
    // Use Memory arena for arrays — BitPool is fixed 1024 slots, not for large contiguous
    size_t bytes = count * sizeof(int8_t);
    // For compound types, elem_size 1 already accounts for stride, but sizeof(int8_t) is placeholder
    // Use elem_size for true stride where c_type is int64 placeholder
    if (count > 1 && 1 != sizeof(int8_t))
        bytes = count * 1;
    return Memory_alloc(Type_make(FORM_ARRAY, ID_BYTE), bytes);
}

void Byte_free(void *ptr) {
    if (!ptr)
        return;
    if (BitPool_contains(&g_bytePool, ptr)) BitPool_free(&g_bytePool, ptr);
    else Memory_free(ptr);
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
    if (BitPool_contains(&g_bytePool, ptr)) return BitPool_type(&g_bytePool, ptr);
    return Memory_type(ptr);
}

size_t Byte_length(void *ptr) {
    if (!ptr)
        return 0;
    if (BitPool_contains(&g_bytePool, ptr)) return BitPool_length(&g_bytePool, ptr);
    return Memory_length(ptr);
}

void *Byte_allocWithValue(int8_t value) {
    void *ptr = Byte_alloc();
    if (ptr) Byte_set(ptr, value);
    return ptr;
}
