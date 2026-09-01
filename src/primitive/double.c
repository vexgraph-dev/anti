#include "primitive/double.h"

#include <string.h>

#include "nio/mem.h"
#include "oop/type.h"

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
    // Array form uses same pool but stamps array type
    void *ptr = BitPool_alloc(&g_doublePool, ID_DOUBLE);
    if (!ptr)
        return nullptr;
    // For now, single slot alloc; array path deferred to BitPool array buckets
    (void) count;
    return ptr;
}

void Double_free(void *ptr) {
    if (!ptr)
        return;
    BitPool_free(&g_doublePool, ptr);
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
    return Memory_type(ptr);
}

size_t Double_length(void *ptr) {
    if (!ptr)
        return 0;
    return Memory_length(ptr);
}
