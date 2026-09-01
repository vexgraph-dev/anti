#ifndef PRIMITIVE_STRING_H
#define PRIMITIVE_STRING_H

#include <stdbool.h>
#include <stddef.h>
#include <stdint.h>

// primitive/string.h — the string class, ported from primitive/string.java.
//
// A string is a Memory block of type TYPE_STRING_ARRAY whose length is the
// UTF-8 byte count + 1 (the trailing NUL lives inside the block). Content-based
// hashing and comparison in Map/Set walk the block header, so strings compare
// by value no matter where they live.
//
// DRAFT: legacy's small/medium/large free-list pools (ABA-tagged atomic heads)
// are deferred; every block currently comes from the Memory arena (the Java
// "oversized" path). The API is identical, so the pool can slot in behind it.

#define STRING_SMALL_SLOT_SIZE 64L
#define STRING_MEDIUM_SLOT_SIZE 256L
#define STRING_LARGE_SLOT_SIZE 1024L

// Allocate a NUL-terminated string block copied from value (Java allocate).
uint8_t *string_allocate(const char *value);

// Allocate a string block copied from len bytes (Java allocate(byte[])).
uint8_t *string_allocateBytes(const uint8_t *bytes, size_t len);

// Allocate a len-byte string block without copying (Java allocateUninitialized).
// The block is zeroed and NUL-terminated.
uint8_t *string_allocateUninitialized(size_t len);

// Recycle a string block back to the allocator (Java free).
void string_free(uint8_t *ptr);

// Read-only C view of the bytes (Java get).
const char *string_get(const uint8_t *ptr);

// Byte length excluding the NUL terminator (Java length).
size_t string_length(const uint8_t *ptr);

// Block header type id (Java type).
uint32_t string_type(const uint8_t *ptr);

// True if the block type is an array form (Java isArray).
bool string_isArray(const uint8_t *ptr);

// Class id (ID_STRING) of any string block (Java classId).
uint32_t string_classId(void);

// Allocated capacity in bytes, NUL included (Java capacity).
size_t string_capacity(const uint8_t *ptr);

// Fresh copy of the block (Java copy).
uint8_t *string_copy(const uint8_t *ptr);

// True when the string block's bytes equal a NUL-terminated C string.
bool string_equals(const uint8_t *ptr, const char *value);

// — Capital String class — anti reserved word String() — file stays string.h lowercase per prefs
// String("hello"), String(uint8_t*), String() → ""
#include "c23/constructor.h"
#define String_0(...) string_allocate("")
#define String_1(value) _Generic((value), const char*: string_allocate, char*: string_allocate, uint8_t*: string_copy, const uint8_t*: string_copy, default: string_allocate)(value)
#define String(...) CONSTRUCTOR_DISPATCH(String, ##__VA_ARGS__)

// Capital aliases for code-editor ergonomics (lowercase kept for compat)
#define String_allocate string_allocate
#define String_allocateBytes string_allocateBytes
#define String_allocateUninitialized string_allocateUninitialized
#define String_free string_free
#define String_get string_get
#define String_length string_length
#define String_type string_type
#define String_isArray string_isArray
#define String_classId string_classId
#define String_capacity string_capacity
#define String_copy string_copy
#define String_equals string_equals

// Compare — strcmp style, negatives/zero/positives
int String_compare(const uint8_t *a, const uint8_t *b);
#define String_compare String_compare

// Search — contains / indexOf / instring(index, haystack, needle)
bool String_contains(const uint8_t *haystack, const uint8_t *needle);
bool String_containsLiteral(const uint8_t *haystack, const char *needle);
size_t String_indexOf(const uint8_t *haystack, const uint8_t *needle);
size_t String_indexOfLiteral(const uint8_t *haystack, const char *needle);
bool String_instring(size_t index, const uint8_t *haystack, const uint8_t *needle);
bool String_instringLiteral(size_t index, const uint8_t *haystack, const char *needle);

#endif