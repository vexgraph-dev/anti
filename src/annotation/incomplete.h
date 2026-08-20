#ifndef ANNOTATION_INCOMPLETE_H
#define ANNOTATION_INCOMPLETE_H

// src/annotation/incomplete.h — C mirror of legacy-java/src/annotation/Incomplete.java.
//
// Incomplete: invoke when the file/method is incomplete (empty, missing
// implementation, untested). Remove once it is fully implemented and tested.
//
// See draft.h for the macro convention (C has no language-level annotations).

#define INCOMPLETE _Static_assert(1, "@Incomplete");

#endif