#include "primitive/string.h"

#include <string.h>

#include "nio/mem.h"
#include "oop/type.h"
#include "annotation/overview.h"

;;OVERVIEW
/**
 * ============================================================================
 * MODULE: String (primitive/string.c)
 * LEVEL: L2 — Behavior (primitive behavior API)
 * ============================================================================
 * the string class, ported from primitive/string.java.
 *
 * STRUCT FIELDS: none — procedural (operates on BitPool/Memory blocks of string payloads)
 *
 * FUNCTION REGISTRY:
 * ----------------------------------------------------------------------------
 * Core Functions:
 *   - string_allocate(value)
 *   - string_allocateBytes(bytes, len)
 *   - string_allocateUninitialized(len)
 *   - string_free(ptr)
 *   - string_length(ptr)
 *   - string_type(ptr)
 *   - string_classId(void)
 *   - string_capacity(ptr)
 *   - string_copy(ptr)
 *   - string_equals(ptr, value)
 *   - String_compare(a, b)
 *   - String_contains(haystack, needle)
 *   - String_containsLiteral(haystack, needle)
 *   - String_indexOf(haystack, needle)
 *   - String_indexOfLiteral(haystack, needle)
 *   - String_instring(index, haystack, needle)
 *   - String_instringLiteral(index, haystack, needle)
 *   - String_substring(str, start, len)
 *   - String_substringLiteral(str, start, len)
 *   - String_subFirstChar(str)
 *   - String_subLastChar(str)
 *   - String_subFirst(str, count)
 *   - String_subLast(str, count)
 *   - String_append(a, b)
 *   - String_appendLiteral(a, b)
 *   - String_appendLiterals(a, b)
 *   - String_appendInto(a, b, dest)
 *   - String_appendFirst(a, b)
 *   - String_appendFirstLiteral(a, b)
 *
 * Getters:
 *   - string_get(ptr)
 *   - string_isArray(ptr)
 * ============================================================================
 */


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

bool String_contains(const uint8_t *haystack, const uint8_t *needle) {
    if (!haystack || !needle)
        return false;
    const char *h = (const char*) haystack;
    const char *n = (const char*) needle;
    return strstr(h, n) != nullptr;
}

bool String_containsLiteral(const uint8_t *haystack, const char *needle) {
    if (!haystack || !needle)
        return false;
    const char *h = (const char*) haystack;
    return strstr(h, needle) != nullptr;
}

size_t String_indexOf(const uint8_t *haystack, const uint8_t *needle) {
    if (!haystack || !needle)
        return (size_t) -1;
    const char *h = (const char*) haystack;
    const char *n = (const char*) needle;
    const char *p = strstr(h, n);
    if (!p)
        return (size_t) -1;
    return (size_t)(p - h);
}

size_t String_indexOfLiteral(const uint8_t *haystack, const char *needle) {
    if (!haystack || !needle)
        return (size_t) -1;
    const char *h = (const char*) haystack;
    const char *p = strstr(h, needle);
    if (!p)
        return (size_t) -1;
    return (size_t)(p - h);
}

bool String_instring(size_t index, const uint8_t *haystack, const uint8_t *needle) {
    if (!haystack || !needle)
        return false;
    size_t pos = String_indexOf(haystack, needle);
    if (pos == (size_t) -1)
        return false;
    return pos == index;
}

bool String_instringLiteral(size_t index, const uint8_t *haystack, const char *needle) {
    if (!haystack || !needle)
        return false;
    size_t pos = String_indexOfLiteral(haystack, needle);
    if (pos == (size_t) -1)
        return false;
    return pos == index;
}

uint8_t *String_substring(const uint8_t *str, size_t start, size_t len) {
    if (!str)
        return nullptr;
    size_t sLen = string_length(str);
    if (start >= sLen)
        return String("");
    if (start + len > sLen)
        len = sLen - start;
    return string_allocateBytes(str + start, len);
}

uint8_t *String_substringLiteral(const char *str, size_t start, size_t len) {
    if (!str)
        return nullptr;
    size_t sLen = strlen(str);
    if (start >= sLen)
        return String("");
    if (start + len > sLen)
        len = sLen - start;
    return string_allocateBytes((const uint8_t*) str + start, len);
}

uint8_t *String_subFirstChar(const uint8_t *str) {
    return String_substring(str, 0, 1);
}

uint8_t *String_subLastChar(const uint8_t *str) {
    if (!str)
        return nullptr;
    size_t sLen = string_length(str);
    if (sLen == 0)
        return String("");
    return String_substring(str, sLen - 1, 1);
}

uint8_t *String_subFirst(const uint8_t *str, size_t count) {
    return String_substring(str, 0, count);
}

uint8_t *String_subLast(const uint8_t *str, size_t count) {
    if (!str)
        return nullptr;
    size_t sLen = string_length(str);
    if (count >= sLen)
        return string_copy(str);
    return String_substring(str, sLen - count, count);
}

uint8_t *String_append(const uint8_t *a, const uint8_t *b) {
    if (!a && !b)
        return String("");
    if (!a)
        return string_copy(b);
    if (!b)
        return string_copy(a);
    size_t aLen = string_length(a);
    size_t bLen = string_length(b);
    uint8_t *out = string_allocateUninitialized(aLen + bLen);
    if (!out)
        return nullptr;
    memcpy(out, a, aLen);
    memcpy(out + aLen, b, bLen);
    out[aLen + bLen] = '\0';
    return out;
}

uint8_t *String_appendLiteral(const uint8_t *a, const char *b) {
    if (!a && !b)
        return String("");
    if (!a)
        return string_allocate(b);
    if (!b)
        return string_copy(a);
    size_t aLen = string_length(a);
    size_t bLen = strlen(b);
    uint8_t *out = string_allocateUninitialized(aLen + bLen);
    if (!out)
        return nullptr;
    memcpy(out, a, aLen);
    memcpy(out + aLen, b, bLen);
    out[aLen + bLen] = '\0';
    return out;
}

uint8_t *String_appendLiterals(const char *a, const char *b) {
    if (!a && !b)
        return String("");
    if (!a)
        return string_allocate(b);
    if (!b)
        return string_allocate(a);
    size_t aLen = strlen(a);
    size_t bLen = strlen(b);
    uint8_t *out = string_allocateUninitialized(aLen + bLen);
    if (!out)
        return nullptr;
    memcpy(out, a, aLen);
    memcpy(out + aLen, b, bLen);
    out[aLen + bLen] = '\0';
    return out;
}

void String_appendInto(const uint8_t *a, const uint8_t *b, uint8_t *dest) {
    if (!a || !b || !dest)
        return;
    size_t aLen = string_length(a);
    size_t bLen = string_length(b);
    size_t cap = string_capacity(dest);
    size_t need = aLen + bLen + 1;
    if (cap < need)
        return;
    memmove(dest, a, aLen);
    memcpy(dest + aLen, b, bLen);
    dest[aLen + bLen] = '\0';
}

void String_appendFirst(uint8_t *a, const uint8_t *b) {
    if (!a || !b)
        return;
    size_t aLen = string_length(a);
    size_t bLen = string_length(b);
    size_t cap = string_capacity(a);
    size_t need = aLen + bLen + 1;
    if (cap >= need) {
        memcpy(a + aLen, b, bLen + 1);
        return;
    }
    // realloc via new block — caller must reassign: a = String_append(a,b); free old not done here
    // For in-place grow, we require capacity; if not enough, do nothing (caller should use String_append)
}

void String_appendFirstLiteral(uint8_t *a, const char *b) {
    if (!a || !b)
        return;
    size_t aLen = string_length(a);
    size_t bLen = strlen(b);
    size_t cap = string_capacity(a);
    size_t need = aLen + bLen + 1;
    if (cap >= need) {
        memcpy(a + aLen, b, bLen + 1);
    }
}
