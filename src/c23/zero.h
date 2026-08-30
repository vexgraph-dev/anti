#ifndef C11_ZERO_H
#define C11_ZERO_H

// c11/zero.h — the generic zero factory: zero(T t), typed by example.
//
//   int   i = zero(n);        // 0            — same type as n
//   float f = zero(x);
//   Vec3  v = zero(other);    // {0,0,0}
//   Mat4  m = zero(model);    // every element zeroed
//   Vec4  w = zero((*blk));   // heap block? deref it — zero of ITS class
//   Window *p = zero(ptr);    // nullptr — the true zero of any pointer
//   const char *s = zero(s);  // "0"
//
// The type comes from the argument through __typeof__ (gnu11, like the rest
// of c11/), so this stays correct for every type the engine will ever add —
// no enumeration, no registry. Lowers to a stack compound literal: no calls,
// no heap.

#define zero(t)                          \
    _Generic((t),                        \
        char: '0',                       \
        char *: (char *)"0",             \
        const char *: (const char *)"0", \
        default: (__typeof__(t)){0})

#endif
