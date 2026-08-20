#ifndef UTIL_HASH_H
#define UTIL_HASH_H

#include <stddef.h>
#include <stdint.h>

// util/hash.h — the Hash utility, ported from util/Hash.java.
//
// Fast 64-bit non-cryptographic hashing. FNV-1a feeds variable-length blocks
// (map keys that are pointer/reference blocks); the Murmur3 finalizers turn a
// scalar 64-bit key into a well-mixed 64-bit value in a couple of multiply-xor
// rounds. Everything the map/set classes need, nothing else.

// FNV-1a 64-bit over a byte block. Returns 0 on a NULL/empty block.
uint64_t Hash_fnv1a64(const uint8_t *data, size_t length);

// MurmurHash3 64-bit finalizer mix for a single 64-bit value.
uint64_t Hash_murmur3Mix64(uint64_t k);

// MurmurHash3 32-bit finalizer mix for a single 32-bit value.
uint32_t Hash_murmur3Mix32(uint32_t k);

#endif