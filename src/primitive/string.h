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

#endif