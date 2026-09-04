#ifndef UTIL_RANDOM_H
#define UTIL_RANDOM_H

#include <stdbool.h>
#include <stdint.h>
#include "c23/constructor.h"

#include "objects/probable.h"
#include "objects/probable_objects.h"

// util/random.h — the Random class, ported from util/Random.java.
//
// Chaotic PRNG: state is a mutable seed that advances via a Murmur3 finalizer
// mixed with a Weyl sequence step. One Random block per stream; the system RNG
// is a lazily-initialized shared stream for parameterless draws.

typedef struct Random {
    uint64_t seed;      // current mixed state
    uint32_t counter;   // sequence index
    uint32_t pad;
} Random;

// New PRNG stream seeded from the given value.
Random *Random_1(uint64_t seed);

void Random_free(Random *r);

// Shared system stream, seeded from the monotonic clock on first use.
Random *Random_system(void);

uint64_t Random_nextLong(Random *r);
int32_t Random_nextInt(Random *r);
float Random_nextFloat(Random *r);
double Random_nextDouble(Random *r);
float Random_nextNDCFloat(Random *r);
char Random_nextChar(Random *r);

// True with probability weight/total (legacy getWeight).
bool Random_getWeight(Random *r, uint32_t weight, uint32_t total);

// Roll a Probable: object on a hit, 0 on a miss.
uintptr_t Random_sample(Random *r, const Probable *probable);

// Draw one object from a weighted ProbableObjects pool.
uintptr_t Random_probablePool(Random *r, const ProbableObjects *pool);


#define Random(...) CONSTRUCTOR_DISPATCH(Random, __VA_ARGS__)
#endif