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
#include "objects/probable.h"
#include "objects/probable_objects.h"
#include "oop/stride.h"
#include "oop/struct.h"
#include "oop/type.h"
#include "relational/variable.h"
#include "struct/array.h"
#include "struct/deque.h"
#include "struct/list.h"
#include "struct/map.h"
#include "struct/minheap.h"
#include "struct/queue.h"
#include "struct/set.h"
#include "struct/sparseset.h"
#include "struct/stack.h"
#include "thread/ring.h"
#include "thread/spin.h"
#include "util/arrays.h"
#include "util/hash.h"
#include "util/random.h"

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

    // Variable: relational symbol registry — every name maps to a typed pointer.
    printf("== anti relational: Variable ==\n");
    Variable vars;
    Variable_init(&vars);

    void *score = Memory_alloc(TYPE_INT_SINGLETON, sizeof(int32_t));
    *(int32_t *)score = 42;

    int32_t score_id = Variable_instant(&vars, "player_score", TYPE_INT_SINGLETON, (uintptr_t) score);
    int32_t name_id = Variable_instant(&vars, "player_name", 0, (uintptr_t)0x1234);
    printf("score_id=%d name_id=%d active=%zu\n", score_id, name_id,
           Variable_getActiveCount(&vars));

    int32_t resolved = Variable_getId(&vars, "player_score");
    char name_buf[VARIABLE_NAME_SIZE + 1];
    Variable_getName(&vars, resolved, name_buf, sizeof(name_buf));
    printf("resolved=%d class=0x%08X ptr=%p name=\"%s\"\n", resolved,
           Variable_getClassId(&vars, resolved),
           (void *)Variable_getPointer(&vars, resolved), name_buf);

    uintptr_t stored = Variable_getPointer(&vars, score_id);
    printf("stored int=%d\n", *(int32_t *)stored);

    bool renamed = Variable_rename(&vars, "player_score", "score");
    int32_t re_id = Variable_getId(&vars, "score");
    int32_t gone_id = Variable_getId(&vars, "player_score");
    printf("renamed=%d new_id=%d old_id=%d\n", renamed, re_id, gone_id);
    Variable_shutdown(&vars);

    // Stride: byte width per class id.
    printf("== anti stride ==\n");
    printf("int=%zu long=%zu double=%zu variable=%zu list=%zu\n",
           Stride_get(ID_INT), Stride_get(ID_LONG), Stride_get(ID_DOUBLE),
           Stride_get(ID_VARIABLE), Stride_get(ID_LIST));

    // List: dynamic stride-based list.
    printf("== anti struct: List ==\n");
    List *list = List_allocate(ID_INT, 16);
    for (uint64_t i = 0; i < 10; i++)
        List_add(list, i * 10);
    printf("size=%zu get0=%llu get9=%llu stride=%zu\n", List_size(list),
           (unsigned long long)List_get(list, 0),
           (unsigned long long)List_get(list, 9), List_stride(list));
    List_remove(list, 0);
    printf("after remove size=%zu get0=%llu\n", List_size(list),
           (unsigned long long)List_get(list, 0));
    List_free(list);

    // Array: fixed stride-based array.
    printf("== anti struct: Array ==\n");
    Array *arr = Array_allocate(ID_LONG, 5);
    for (size_t i = 0; i < 5; i++)
        Array_set(arr, i, 100 + i);
    printf("len=%zu sum=%llu\n", Array_length(arr),
           (unsigned long long)(Array_get(arr, 0) + Array_get(arr, 1)
                                + Array_get(arr, 2) + Array_get(arr, 3)
                                + Array_get(arr, 4)));
    Array_free(arr);

    // Stack: LIFO.
    printf("== anti struct: Stack ==\n");
    Stack *stack = Stack_allocate(ID_INT, 4);
    Stack_push(stack, 1);
    Stack_push(stack, 2);
    Stack_push(stack, 3);
    printf("peek=%llu pop=%llu pop=%llu\n", (unsigned long long)Stack_peek(stack),
           (unsigned long long)Stack_pop(stack), (unsigned long long)Stack_pop(stack));
    Stack_free(stack);

    // Deque: circular double-ended.
    printf("== anti struct: Deque ==\n");
    Deque *deque = Deque_allocate(ID_INT, 4);
    Deque_addFirst(deque, 1);
    Deque_addLast(deque, 2);
    Deque_addFirst(deque, 3);
    printf("size=%zu first=%llu last=%llu get1=%llu\n", Deque_size(deque),
           (unsigned long long)Deque_peekFirst(deque),
           (unsigned long long)Deque_peekLast(deque),
           (unsigned long long)Deque_get(deque, 1));
    printf("popFirst=%llu popLast=%llu\n", (unsigned long long)Deque_removeFirst(deque),
           (unsigned long long)Deque_removeLast(deque));
    Deque_free(deque);

    // Queue: FIFO.
    printf("== anti struct: Queue ==\n");
    Queue *queue = Queue_allocate(ID_INT, 4);
    Queue_push(queue, 7);
    Queue_push(queue, 8);
    Queue_push(queue, 9);
    printf("peek=%llu pop=%llu pop=%llu\n", (unsigned long long)Queue_peek(queue),
           (unsigned long long)Queue_pop(queue), (unsigned long long)Queue_pop(queue));
    Queue_free(queue);

    // Map: open-addressing int -> int.
    printf("== anti struct: Map ==\n");
    Map *map = Map_allocate(ID_INT, ID_LONG, 8);
    for (uint64_t k = 1; k <= 20; k++)
        Map_put(map, k, k * k);
    printf("size=%zu get7=%llu contains20=%d missing100=%d\n", Map_size(map),
           (unsigned long long)Map_get(map, 7), Map_containsKey(map, 20),
           Map_containsKey(map, 100));
    printf("remove9=%llu size=%zu\n", (unsigned long long)Map_remove(map, 9),
           Map_size(map));
    Array *keys = Map_keys(map);
    printf("keys=%zu first_key=%llu\n", Array_length(keys),
           (unsigned long long)Array_get(keys, 0));
    Array_free(keys);
    Map_free(map);

    // Set: unique elements.
    printf("== anti struct: Set ==\n");
    Set *set = Set_allocate(ID_INT, 8);
    for (int32_t i = 0; i < 12; i++)
        Set_add(set, (uint64_t)(i % 6));
    printf("size=%zu contains5=%d contains9=%d\n", Set_size(set),
           Set_contains(set, 5), Set_contains(set, 9));
    Set_remove(set, 5);
    printf("after remove5 contains5=%d size=%zu\n", Set_contains(set, 5),
           Set_size(set));
    List *sorted = Set_toSortedList(set);
    printf("sorted: ");
    for (size_t i = 0; i < List_size(sorted); i++)
        printf("%llu ", (unsigned long long)List_get(sorted, i));
    printf("\n");
    List_free(sorted);
    Set_free(set);

    // MinHeap: priority queue.
    printf("== anti struct: MinHeap ==\n");
    MinHeap *heap = MinHeap_allocate(8);
    MinHeap_push(heap, 10, 5.0f);
    MinHeap_push(heap, 20, 1.0f);
    MinHeap_push(heap, 30, 3.0f);
    printf("size=%zu pop=%d pop=%d pop=%d\n", MinHeap_size(heap),
           MinHeap_popItem(heap), MinHeap_popItem(heap), MinHeap_popItem(heap));
    MinHeap_free(heap);

    // SparseSet: ECS-style entity -> component.
    printf("== anti struct: SparseSet ==\n");
    SparseSet *ss = SparseSet_allocate(8, 100, (size_t)sizeof(int32_t));
    uint8_t *comp = SparseSet_add(ss, 42);
    *(int32_t *)comp = 4242;
    printf("count=%zu contains42=%d value=%d\n", SparseSet_count(ss),
           SparseSet_contains(ss, 42), *(int32_t *)SparseSet_get(ss, 42));
    SparseSet_remove(ss, 42);
    printf("after remove contains42=%d count=%zu\n", SparseSet_contains(ss, 42),
           SparseSet_count(ss));
    SparseSet_free(ss);

    // Hash: FNV + Murmur3.
    printf("== anti util: Hash ==\n");
    printf("fnv(\"anti\")=%016llX mix32(7)=%08X\n",
           (unsigned long long)Hash_fnv1a64((const uint8_t *)"anti", 4),
           Hash_murmur3Mix32(7));

    // Random: chaotic PRNG + weighted draws.
    printf("== anti util: Random ==\n");
    Random *rng = Random_allocate(12345);
    printf("r0=%016llX f1=%f d1=%f\n", (unsigned long long)Random_nextLong(rng),
           (double)Random_nextFloat(rng), Random_nextDouble(rng));
    printf("weight(25,100) hit count=");
    int hits = 0;
    for (int i = 0; i < 1000; i++)
        hits += Random_getWeight(rng, 25, 100) ? 1 : 0;
    printf("%d\n", hits);

    Probable *p = Probable_allocate((uintptr_t)0xCAFE, 1, 2);
    printf("sample=%llu\n", (unsigned long long)Random_sample(rng, p));
    Probable_free(p);

    ProbableObjects *objpool = ProbableObjects_allocate(3);
    ProbableObjects_add(objpool, (uintptr_t)0x1111, 200);
    ProbableObjects_add(objpool, (uintptr_t)0x2222, 40);
    ProbableObjects_add(objpool, (uintptr_t)0x3333, 1);
    printf("pool total=%u draw=%llu\n", ProbableObjects_totalWeight(objpool),
           (unsigned long long)ProbableObjects_get(objpool));
    ProbableObjects_free(objpool);
    Random_free(rng);

    // Arrays: sort + search.
    printf("== anti util: Arrays ==\n");
    int32_t buf[6] = { 5, 2, 9, 1, 7, 3 };
    Arrays_sortInt(buf, 6);
    printf("sorted: %d %d %d %d %d %d\n", buf[0], buf[1], buf[2], buf[3], buf[4], buf[5]);
    printf("search7=%ld\n", (long)Arrays_binarySearchInt(buf, 6, 7));

    // Struct: runtime-defined custom struct.
    printf("== anti oop: Struct ==\n");
    uint32_t fields[3] = { ID_INT, ID_LONG, ID_FLOAT };
    uint32_t point = Struct_construct(fields, 3);
    printf("generic=0x%X stride=%zu\n", point, Struct_stride(point));
    printf("stride_via_registry=%zu\n", Stride_get(point));

    void *pt = Struct_allocateSingleton(point);
    Struct_setInt(pt, 0, 5);
    Struct_setLong(pt, 1, 123456789);
    Struct_setFloat(pt, 2, 2.5f);
    printf("x=%d y=%lld z=%f\n", Struct_getInt(pt, 0),
           (long long)Struct_getLong(pt, 1), (double)Struct_getFloat(pt, 2));
    Struct_free(pt);

    void *pts = Struct_allocateArray(point, 3);
    for (size_t i = 0; i < 3; i++)
        Struct_setIntElement(pts, i, 0, (int32_t)(i + 1));
    printf("aos: %d %d %d\n", Struct_getIntElement(pts, 0, 0),
           Struct_getIntElement(pts, 1, 0), Struct_getIntElement(pts, 2, 0));
    Struct_free(pts);

    void *mat = Struct_allocateMatrix(point, 2);
    Struct_setPointer(mat, 1, (uintptr_t)0xDEAD);
    printf("matrix ptr=%llu\n", (unsigned long long)Struct_getPointer(mat, 1));
    Struct_free(mat);

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