#ifndef ANNOTATION_OVERVIEW_H
#define ANNOTATION_OVERVIEW_H

// src/annotation/overview.h — Source file architectural overview marker.
//
// Marks the structured file header that documents the class reference,
// struct fields, and function registry (core, setters, getters) directly
// within the first 50-250 lines of .c and .m files.
//
// Usage: prefix the marker with two semicolons:
// `;;OVERVIEW` (see preferences.md, rule 23).

#define OVERVIEW _Static_assert(1, "@Overview");

#endif
