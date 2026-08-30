#ifndef THREAD_THREAD_H
#define THREAD_THREAD_H

#include <stdbool.h>
#include <stddef.h>
#include <stdint.h>

#include "atomic/ring.h"

// thread/thread.h — the worker-thread engine (Legacy: the six *Thread.java
// managers that each re-implemented the same 200 lines).
//
// A thread handle is a self-describing block: [typeId][len][state|queue|job…].
// The engine owns the lifecycle — spawn, join, submit, stop, free, registry,
// core protection — while each purpose class (networking/event/draw/scripting/
// ui) supplies only its per-pass job:
//
//   Thread_Job: called once per popped task (task != nullptr) or once per idle
//   pass when tickWhenIdle is set (task == nullptr).
//
// Loop semantics mirror legacy: drain queue → run job; sleep 1ms when idle
// unless tickWhenIdle; exit when state leaves RUNNING.

typedef struct Thread Thread;

// Per-pass body. Runs on the worker platform thread.
typedef void (*Thread_Job)(Thread *self, void *task);

// Create a stopped handle. typeId stamps the block header (one of the
// ID_THREAD_* classes); queueCapacity holds void* tasks. Core handles reject
// stop/free like legacy ROLE_CORE. nullptr on OOM/bad args.
Thread *Thread_new(uint32_t typeId, Thread_Job job, size_t queueCapacity,
                   bool tickWhenIdle, bool core);

// Spawn the platform thread. True on success or if already running.
bool Thread_run(Thread *self);

// Enqueue a task handle. False when stopped or the ring is full.
bool Thread_submit(Thread *self, void *task);

// Ask the loop to exit and join. No-op when already stopped. Refused on cores.
void Thread_stop(Thread *self);

// Stop, tear down the ring, unregister, release the block. Cores are immune
// until shutdown calls Thread_freeAllSystem().
void Thread_free(Thread *self);

bool Thread_isRunning(Thread *self);
RingBuffer *Thread_queue(Thread *self);
uint32_t Thread_purpose(Thread *self);

// Pool-wide walkers over the central registry (legacy runAll/stopAll/freeAll).
void Thread_runAll(void);
void Thread_stopAll(void);
void Thread_freeAll(void);

// Shutdown-only: frees even core handles. Nothing survives this call.
void Thread_freeAllSystem(void);

size_t Thread_count(void);

#endif
