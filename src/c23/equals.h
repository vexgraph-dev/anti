#ifndef C23_EQUALS_H
#define C23_EQUALS_H

#include <stdbool.h>

// c23/equals.h — Relational Equality.
//
// Java `.equals()` compares content; C `==` compares addresses. These close
// the gap the relational way: identity first, then Memory headers (type +
// length), then the Class schema field-by-field so padding bytes never vote
// (Lesson 9: `memcmp` lies on padding). Unregistered types fall back to a
// payload `memcmp`; foreign (non-allocator) pointers can only prove identity.

bool isEqual(const void *a, const void *b);

// Registry form: resolve two symbol names in `v` and deep-compare their
// payloads. False when either name is unregistered or classIds differ.
struct Variable;
bool isEquallyNamed(struct Variable *v, const char *nameA, const char *nameB);

#endif
