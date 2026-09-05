#include "util/hash.h"
#include "annotation/overview.h"

;;OVERVIEW
/**
 * ============================================================================
 * MODULE: Hash (util/hash.c)
 * LEVEL: L2 — Behavior (utility behavior API)
 * ============================================================================
 * the Hash utility, ported from util/Hash.java.
 *
 * STRUCT FIELDS: none — procedural (operates on byte buffers (pure hash functions))
 *
 * FUNCTION REGISTRY:
 * ----------------------------------------------------------------------------
 * Getters:
 *   - Hash_fnv1a64(data, length)
 *   - Hash_murmur3Mix64(k)
 *   - Hash_murmur3Mix32(k)
 * ============================================================================
 */


// hash.c — Hash port (Legacy: util/Hash.java). Pure functions, no state.

static const uint64_t FNV_OFFSET = 0xcbf29ce484222325ull;
static const uint64_t FNV_PRIME = 0x100000001b3ull;

uint64_t Hash_fnv1a64(const uint8_t *data, size_t length) {
    if (!data || length == 0)
        return 0;
    uint64_t hash = FNV_OFFSET;
    for (size_t i = 0; i < length; i++) {
        hash ^= (uint64_t)(*data);
        hash *= FNV_PRIME;
        data++;
    }
    return hash;
}

uint64_t Hash_murmur3Mix64(uint64_t k) {
    k ^= k >> 33;
    k *= 0xff51afd7ed558ccdull;
    k ^= k >> 33;
    k *= 0xc4ceb9fe1a85ec53ull;
    k ^= k >> 33;
    return k;
}

uint32_t Hash_murmur3Mix32(uint32_t k) {
    k ^= k >> 16;
    k *= 0x85ebca6bul;
    k ^= k >> 13;
    k *= 0xc2b2ae35ul;
    k ^= k >> 16;
    return k;
}
