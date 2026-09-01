#include "primitive/int.h"

#include <string.h>

#include "nio/mem.h"
#include "oop/type.h"

BitPool g_intPool;

bool Int_init(void) {
    return BitPool_init(&g_intPool, 4, 1024);
}

void Int_shutdown(void) {
    BitPool_shutdown(&g_intPool);
}

void *Int_alloc(void) {
    return BitPool_alloc(&g_intPool, ID_INT);
}

void *Int_allocArray(size_t count) {
    if (count == 0)
        return nullptr;
    // Use Memory arena for arrays — BitPool is fixed 1024 slots, not for large contiguous
    size_t bytes = count * sizeof(int32_t);
    // For compound types, elem_size 4 already accounts for stride, but sizeof(int32_t) is placeholder
    // Use elem_size for true stride where c_type is int64 placeholder
    if (count > 1 && 4 != sizeof(int32_t))
        bytes = count * 4;
    return Memory_alloc(Type_make(FORM_ARRAY, ID_INT), bytes);
}

void Int_free(void *ptr) {
    if (!ptr)
        return;
    BitPool_free(&g_intPool, ptr);
}

int32_t Int_get(void *ptr) {
    if (!ptr)
        return (int32_t) 0;
    return *(int32_t*) ptr;
}

void Int_set(void *ptr, int32_t value) {
    if (!ptr)
        return;
    *(int32_t*) ptr = value;
}

bool Int_compareAndSet(void *ptr, int32_t expected, int32_t value) {
    if (!ptr)
        return false;
    // fallback to simple CAS via __atomic_compare_exchange
    return __atomic_compare_exchange_n((int32_t*) ptr, &expected, value, false, __ATOMIC_SEQ_CST, __ATOMIC_SEQ_CST);
}

uint32_t Int_type(void *ptr) {
    if (!ptr)
        return 0;
    return Memory_type(ptr);
}

size_t Int_length(void *ptr) {
    if (!ptr)
        return 0;
    return Memory_length(ptr);
}

void *Int_allocWithValue(int32_t value) {
    void *ptr = Int_alloc();
    if (ptr) Int_set(ptr, value);
    return ptr;
}
