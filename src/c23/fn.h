#ifndef C11_FN_H
#define C11_FN_H

#include <stdint.h>

// c11/fn.h — functions as data (the opcode-in-hand class).
//
//   int base = 10;
//   Function f = { .call = addTen_impl, .ctx = &base };
//   int out = *(int *)Function_run(&f, &x);      // like Java's runnable.run()
//
// A Function is pure data: one code pointer + one environment pointer — the
// same shape as Loop.tick/userdata, promoted to a reusable class. Capture is
// manual (pack what you need into ctx), which keeps it zero-allocation.
//
// There is exactly ONE runner verb. Function_run takes a single void* because
// in a everything-is-a-pointer engine, "an array of objects" IS one pointer —
// pass a stack array, an Array* block, or a Fields-struct. No varargs: `...`
// would trade away every type check this package exists to keep.
//
// Opcodes in practice: store Functions in arrays/maps and run them later —
// command buffers, listener lists, job queues. Thread_Job bodies dispatching
// through Function_run turn any worker into an executor.

typedef struct Function {
    void *(*call)(void *context, void *in);
    void *context;
} Function;

// Invoke f with its captured environment. Returns whatever the impl returned.
static void *run(Function *f, void *in) {
    return (*(*f).call)((*f).context, in);
}

// Partial application: same code, fresh environment. By value — assign it.
static Function fnbind(const Function *f, void *context) {
    Function bound;
    bound.call = (*f).call;
    bound.context = context;
    return bound;
}

#endif
