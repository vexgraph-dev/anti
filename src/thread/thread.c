#include "thread/thread.h"

#include <pthread.h>
#include <stdatomic.h>
#include <time.h>

#include "nio/mem.h"
#include "oop/type.h"
#include "struct/array.h"
#include "struct/map.h"

// thread/thread.c — the worker engine (Legacy: thread/*Thread.java).

typedef struct Thread {
    _Atomic int state;        // 0 = STOPPED, 1 = RUNNING
    uint32_t typeId;          // block header class (ID_THREAD_*)
    pthread_t platform;
    bool platformStarted;
    RingBuffer queue;         // void* task slots
    Thread_Job job;
    bool tickWhenIdle;
    bool core;
} Thread;

#define THREAD_TASK_SIZE sizeof(void *)
#define THREAD_QUEUE_CAPACITY 2048

// Central registry: handle address -> 1. Legacy used Map.put(workerPtr, 1L).
static Map *s_workers = nullptr;

static void *platform_main(void *arg);

static Map *workers(void) {
    if (!s_workers)
        s_workers = Map(ID_LONG, ID_LONG, 16);
    return s_workers;
}

Thread *Thread_new(uint32_t typeId, Thread_Job job, size_t queueCapacity,
                   bool tickWhenIdle, bool core) {
    if (!job || queueCapacity == 0)
        return nullptr;
    Thread *t = (Thread *)Memory_alloc(typeId, sizeof(Thread));
    if (!t)
        return nullptr;
    atomic_init(&(*t).state, 0);
    (*t).typeId = typeId;
    (*t).platformStarted = false;
    (*t).job = job;
    (*t).tickWhenIdle = tickWhenIdle;
    (*t).core = core;
    if (!RingBuffer_init(&(*t).queue, THREAD_TASK_SIZE, queueCapacity)) {
        Memory_free(t);
        return nullptr;
    }
    Map_put(workers(), (uint64_t)(uintptr_t)t, 1);
    return t;
}

bool Thread_run(Thread *self) {
    if (!self)
        return false;
    int expected = 0;
    if (!atomic_compare_exchange_strong(&(*self).state, &expected, 1))
        return true; // already running
    if ((*self).platformStarted) {
        // Re-running a joined handle: the old pthread_t is spent; start fresh.
        (*self).platformStarted = false;
    }
    if (pthread_create(&(*self).platform, nullptr, platform_main, self) != 0) {
        atomic_store(&(*self).state, 0);
        return false;
    }
    (*self).platformStarted = true;
    return true;
}

bool Thread_submit(Thread *self, void *task) {
    if (!self || !task)
        return false;
    if (atomic_load(&(*self).state) != 1)
        return false;
    return RingBuffer_push(&(*self).queue, &task);
}

static void joinPlatform(Thread *self) {
    if ((*self).platformStarted) {
        pthread_join((*self).platform, nullptr);
        (*self).platformStarted = false;
    }
}

void Thread_stop(Thread *self) {
    if (!self || (*self).core)
        return;
    atomic_store(&(*self).state, 0);
    joinPlatform(self);
}

// force=true is the system-shutdown path: cores die too (legacy freeAllSystem).
static void freeInternal(Thread *self, bool force) {
    if (!self)
        return;
    if ((*self).core && !force)
        return;
    atomic_store(&(*self).state, 0);
    joinPlatform(self);
    RingBuffer_shutdown(&(*self).queue);
    Map_remove(workers(), (uint64_t)(uintptr_t)self);
    Memory_free(self);
}

void Thread_free(Thread *self) {
    freeInternal(self, false);
}

bool Thread_isRunning(Thread *self) {
    return self && atomic_load(&(*self).state) == 1;
}

RingBuffer *Thread_queue(Thread *self) {
    return self ? &(*self).queue : nullptr;
}

uint32_t Thread_purpose(Thread *self) {
    return self ? (*self).typeId : 0;
}

static void *platform_main(void *arg) {
    Thread *self = (Thread *)arg;
    while (atomic_load(&(*self).state) == 1) {
        void *task = nullptr;
        if (RingBuffer_pop(&(*self).queue, &task)) {
            (*(*self).job)(self, task);
        } else if ((*self).tickWhenIdle) {
            (*(*self).job)(self, nullptr);
        } else {
            struct timespec idle = { 0, 1000 * 1000 }; // legacy Thread.sleep(1)
            nanosleep(&idle, nullptr);
        }
    }
    return nullptr;
}

static void walkPool(void (*visit)(Thread *self, bool system), bool system) {
    Map *pool = workers();
    if (!pool)
        return;
    Array *keys = Map_keys(pool);
    if (!keys)
        return;
    size_t count = Array_length(keys);
    for (size_t i = 0; i < count; i++) {
        Thread *t = (Thread *)(uintptr_t)Array_get(keys, i);
        visit(t, system);
    }
    Array_free(keys);
}

static void visitRun(Thread *self, bool system) {
    (void)system;
    Thread_run(self);
}

static void visitStop(Thread *self, bool system) {
    (void)system;
    Thread_stop(self);
}

static void visitFree(Thread *self, bool system) {
    freeInternal(self, system);
}

void Thread_runAll(void) {
    walkPool(visitRun, false);
}

void Thread_stopAll(void) {
    walkPool(visitStop, false);
}

void Thread_freeAll(void) {
    walkPool(visitFree, false);
}

void Thread_freeAllSystem(void) {
    walkPool(visitFree, true);
}

size_t Thread_count(void) {
    return s_workers ? Map_size(s_workers) : 0;
}
