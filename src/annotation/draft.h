#ifndef ANNOTATION_DRAFT_H
#define ANNOTATION_DRAFT_H

// src/annotation/draft.h — C mirror of legacy-java/src/annotation/Draft.java.
//
// C has no language-level annotations, so each legacy annotation becomes a
// header defining a zero-cost marker macro. The macros expand to a static
// assert whose message is the annotation text: it shows up in compiler
// diagnostics and debug info, so the marker is real and greppable — not just
// prose.
//
// Draft: invoke when the file/method is being drafted/written/generated as a
// first fresh write, not ready for production.

#define DRAFT _Static_assert(1, "@Draft");

#endif