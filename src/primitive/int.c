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
    // Array form uses same pool but stamps array type
    void *ptr = BitPool_alloc(&g_intPool, ID_INT);
    if (!ptr)
        return nullptr;
    // For now, single slot alloc; array path deferred to BitPool array buckets
    (void) count;
    return ptr;
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
