#include "primitive/string.h"

#include <string.h>

#include "nio/mem.h"
#include "oop/type.h"

// string.c — the string class, ported from primitive/string.java.

static uint8_t *allocate_raw(size_t len) {
    uint8_t *ptr = (uint8_t*) Memory_alloc(TYPE_STRING_ARRAY, len + 1);
    if (ptr)
        memset(ptr, 0, len + 1);
    return ptr;
}

uint8_t *string_allocate(const char *value) {
    if (!value)
        return nullptr;
    return string_allocateBytes((const uint8_t*) value, strlen(value));
}

uint8_t *string_allocateBytes(const uint8_t *bytes, size_t len) {
    if (!bytes && len > 0)
        return nullptr;
    uint8_t *ptr = allocate_raw(len);
    if (!ptr)
        return nullptr;
    if (len > 0)
        memcpy(ptr, bytes, len);
    ptr[len] = '\0';
    return ptr;
}

uint8_t *string_allocateUninitialized(size_t len) {
    return allocate_raw(len);
}

void string_free(uint8_t *ptr) {
    if (ptr)
        Memory_free(ptr);
}

const char *string_get(const uint8_t *ptr) {
    if (!ptr)
        return nullptr;
    return (const char*) ptr;
}

size_t string_length(const uint8_t *ptr) {
    if (!ptr)
        return 0;
    size_t n = Memory_length((void*) (uintptr_t)ptr);
    return n > 0 ? n - 1 : 0;
}

uint32_t string_type(const uint8_t *ptr) {
    if (!ptr)
        return 0;
    return Memory_type((void*) (uintptr_t)ptr);
}

bool string_isArray(const uint8_t *ptr) {
    uint32_t t = string_type(ptr);
    return t != 0 && (t & FORM_ARRAY) == FORM_ARRAY;
}

uint32_t string_classId(void) {
    return ID_STRING;
}

size_t string_capacity(const uint8_t *ptr) {
    if (!ptr)
        return 0;
    return Memory_length((void*) (uintptr_t)ptr);
}

uint8_t *string_copy(const uint8_t *ptr) {
    if (!ptr)
        return nullptr;
    size_t len = string_length(ptr);
    uint8_t *dup = string_allocateBytes(ptr, len);
    return dup;
}

bool string_equals(const uint8_t *ptr, const char *value) {
    if (!ptr || !value)
        return false;
    size_t len = string_length(ptr);
    if (strlen(value) != len)
        return false;
    return memcmp(ptr, value, len) == 0;
}

int String_compare(const uint8_t *a, const uint8_t *b) {
    if (!a && !b)
        return 0;
    if (!a)
        return -1;
    if (!b)
        return 1;
    const char *sa = (const char*) a;
    const char *sb = (const char*) b;
    return strcmp(sa, sb);
}