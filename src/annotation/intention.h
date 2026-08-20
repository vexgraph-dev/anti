#ifndef ANNOTATION_INTENTION_H
#define ANNOTATION_INTENTION_H

// src/annotation/intention.h — C mirror of legacy-java/src/annotation/Intention.java.
//
// Intention(text): state the reason a file/function exists, annotated on top
// of a header/class for a reason. The text is a string literal.
//
// See draft.h for the macro convention (C has no language-level annotations).
// Usage: `;;INTENTION("...")` (see preferences.txt, rule 8).

#define INTENTION(text) _Static_assert(1, "@Intention(" text ")");

#endif