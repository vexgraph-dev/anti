// main.c — the demo tying the C11 subsystems together.
//
// Story: 4 producer threads race to push 25 jobs each into a shared MPMC ring.
// The engine loop (a separate fixed-timestep loop) drains the ring and counts
// every job. When it has seen all 100, it stops the loop and the program ends.
//
// This exercises, in one file:
//   - Memory   : every block knows its own type + length
//   - BitPool   : lockless pool, slot gets recycled (same address back)
//   - RingBuffer  : MPMC queue with spinlock coordination
//   - Loop  : the while(running){tick} engine loop
//   - SpinLock  : used inside the ring

#include <pthread.h>
#include <stdio.h>
#include <stdlib.h>
#include <string.h>

#include "bit/bit.h"
#include "engine/loop.h"
#include "nio/mem.h"
#include "thread/ring.h"
#include "thread/spin.h"
#include "oop/type.h"

#define N_THREADS 4
#define N_PUSH 25
#define RING_CAP 16

typedef struct job {
    unsigned int from;
    unsigned int seq;
} job_t;

typedef struct producer_ctx {
    RingBuffer *ring;
    uint32_t id;
} producer_ctx_t;

typedef struct engine_ctx {
    RingBuffer *ring;
    Loop loop;
    uint32_t received;
    int ticks;
} engine_ctx_t;

static void *producer_main(void *arg) {
    producer_ctx_t *ctx = (producer_ctx_t *)arg;
    for (uint32_t i = 0; i < N_PUSH; i++) {
        job_t job = { .from = (*ctx).id, .seq = i };
        while (!RingBuffer_push((*ctx).ring, &job)) {
            // Ring full: spin. The engine loop drains it on its next tick, so
            // this eventually succeeds — it's a bounded wait, not a deadlock.
        }
    }
    return NULL;
}

// Called by the engine loop at a fixed timestep. Drains everything the
// producers pushed and stops the loop once the expected total has arrived.
static void engine_tick(void *userdata) {
    engine_ctx_t *ctx = (engine_ctx_t *)userdata;
    (*ctx).ticks++;

    job_t job;
    while (RingBuffer_pop((*ctx).ring, &job)) {
        printf("tick=%d  job from=%u seq=%u\n", (*ctx).ticks, job.from, job.seq);
        (*ctx).received++;
    }

    if ((*ctx).received >= N_THREADS * N_PUSH) {
        Loop_stop(&(*ctx).loop);
    }
}

int main(void) {
    // Memory: allocate a typed block, prove the header round-trips.
    printf("== anti memory ==\n");
    void *blk = Memory_alloc(TYPE_INT_ARRAY, 4 * sizeof(int32_t));
    printf("type=0x%08X len=%zu\n", Memory_type(blk), Memory_length(blk));
    Memory_free(blk);

    // BitPool: allocation a, free a, allocate again -> the SAME address comes back.
    printf("== anti bit pool ==\n");
    BitPool pool;
    BitPool_init(&pool, 8, 4);
    void *a = BitPool_alloc(&pool, TYPE_INT_SINGLETON);
    void *b = BitPool_alloc(&pool, TYPE_INT_SINGLETON);
    printf("a=%p b=%p\n", a, b);
    BitPool_free(&pool, a);
    void *c = BitPool_alloc(&pool, TYPE_INT_SINGLETON);
    printf("recycled a -> c=%p (same=%d)\n", c, c == a);
    BitPool_shutdown(&pool);

    // RingBuffer + Loop: 4 producers, 1 consumer loop, expect 100 jobs.
    printf("== anti ring + spin + loop ==\n");
    RingBuffer ring;
    RingBuffer_init(&ring, sizeof(job_t), RING_CAP);

    producer_ctx_t ctxs[N_THREADS];
    pthread_t threads[N_THREADS];
    for (uint32_t i = 0; i < N_THREADS; i++) {
        ctxs[i].ring = &ring;
        ctxs[i].id = i + 1;
        pthread_create(&threads[i], NULL, producer_main, &ctxs[i]);
    }

    engine_ctx_t engine = {
        .ring = &ring,
        .loop = { .tick = engine_tick, .userdata = NULL, .frame_ms = 4, .running = false },
    };
    engine.loop.userdata = &engine;

    Loop_run(&engine.loop);

    for (uint32_t i = 0; i < N_THREADS; i++) {
        pthread_join(threads[i], NULL);
    }

    printf("received=%u/%u ticks=%d\n", engine.received,
           (uint32_t)(N_THREADS * N_PUSH), engine.ticks);

    RingBuffer_shutdown(&ring);
    return 0;
}