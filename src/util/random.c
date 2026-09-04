#include "util/random.h"

#include <stdlib.h>

#include <mach/mach_time.h>

#include "nio/mem.h"
#include "oop/type.h"
#include "util/hash.h"
#include "annotation/overview.h"

;;OVERVIEW
/**
 * ============================================================================
 * MODULE: Random (util/random.c)
 * LEVEL: L2 — Behavior (utility behavior API)
 * ============================================================================
 * the Random class, ported from util/Random.java.
 *
 * FUNCTION REGISTRY:
 * ----------------------------------------------------------------------------
 * Constructors:
 *   - Random_1(seed)
 *
 * Core Functions:
 *   - Random_free(r)
 *   - Random_system(void)
 *   - Random_nextLong(r)
 *   - Random_nextInt(r)
 *   - Random_nextFloat(r)
 *   - Random_nextDouble(r)
 *   - Random_nextNDCFloat(r)
 *   - Random_nextChar(r)
 *   - Random_sample(r, probable)
 *   - Random_probablePool(r, pool)
 *
 * Getters:
 *   - Random_getWeight(r, weight, total)
 * ============================================================================
 */


// random.c — Random port (Legacy: util/Random.java).

static const uint64_t GOLDEN_RATIO_64 = 0x9e3779b97f4a7c15ull;

static Random *system_rng = nullptr;

static uint64_t system_seed(void) {
    uint64_t ticks = (uint64_t)mach_absolute_time();
    return ticks ^ (uint64_t)(uintptr_t)&system_rng;
}

Random *Random_1(uint64_t seed) {
    Random *r = (Random*) Memory_alloc(TYPE_RANDOM, sizeof(Random));
    if (!r) return nullptr;
    (*r).seed = seed;
    (*r).counter = 0;
    (*r).pad = 0;
    return r;
}

void Random_free(Random *r) {
    Memory_free(r);
}

Random *Random_system(void) {
    if (!system_rng)
        system_rng = Random_1(system_seed());
    return system_rng;
}

uint64_t Random_nextLong(Random *r) {
    if (!r) return 0;
    uint64_t mixed = (*r).seed
        ^ (uint64_t)(uintptr_t)r
        ^ ((uint64_t)(*r).counter * GOLDEN_RATIO_64);
    uint64_t result = Hash_murmur3Mix64(mixed);
    (*r).seed = result;
    (*r).counter++;
    return result;
}

int32_t Random_nextInt(Random *r) {
    return (int32_t)Random_nextLong(r);
}

float Random_nextFloat(Random *r) {
    return (float)((Random_nextLong(r) & 0xFFFFFF) / 16777216.0);
}

double Random_nextDouble(Random *r) {
    return (double)((Random_nextLong(r) & 0x1FFFFFFFFFFFFFull) / 9007199254740992.0);
}

float Random_nextNDCFloat(Random *r) {
    return (float)(((Random_nextLong(r) & 0xFFFFFF) / 8388608.0) - 1.0);
}

char Random_nextChar(Random *r) {
    int64_t v = (int64_t)Random_nextLong(r);
    return (char)(32 + (llabs(v) % 95));
}

bool Random_getWeight(Random *r, uint32_t weight, uint32_t total) {
    if (!r) return false;
    if (total == 0) return false;
    if (weight >= total) return true;
    if (weight == 0) return false;
    uint64_t val = Random_nextLong(r) & 0x7FFFFFFFFFFFFFFFull;
    return (val % total) < weight;
}

uintptr_t Random_sample(Random *r, const Probable *probable) {
    if (!probable) return 0;
    if (Random_getWeight(r, (*probable).weight, (*probable).total))
        return (*probable).object;
    return 0;
}

uintptr_t Random_probablePool(Random *r, const ProbableObjects *pool) {
    if (!pool) return 0;
    size_t count = ProbableObjects_size((ProbableObjects*) pool);
    if (count == 0) return 0;

    uint32_t total_weight = ProbableObjects_totalWeight((ProbableObjects*) pool);
    uint64_t val = Random_nextLong(r) & 0x7FFFFFFFFFFFFFFFull;

    if (total_weight == 0) {
        size_t idx = (size_t)(val % count);
        return ProbableObjects_objectAt((ProbableObjects*) pool, idx);
    }

    uint32_t target = (uint32_t)(val % total_weight);
    size_t low = 0;
    size_t high = count - 1;
    while (low < high) {
        size_t mid = (low + high) / 2;
        uint32_t cumulative = ProbableObjects_cumulativeAt((ProbableObjects*) pool, mid);
        if (cumulative < target)
            low = mid + 1;
        else
            high = mid;
    }
    return ProbableObjects_objectAt((ProbableObjects*) pool, low);
}
