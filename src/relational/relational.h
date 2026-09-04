#ifndef RELATIONAL_RELATIONAL_H
#define RELATIONAL_RELATIONAL_H

#include <stdbool.h>
#include <stddef.h>
#include <stdint.h>

#include "relational/variable.h"

// relational/relational.h — spotlight relational facade over Variable (Legacy: relational/RelationalEngine.java).
//
// Two Variable tables are the scopes: global and local. Every symbol is a row
// name => (classId, targetPointer). The pointer is the value — a string block,
// a typed struct, a Map, or a function address. Search is the spotlight: query
// "health" returns health, health_ui, health_progress_bar, hp_text, etc.

// Exact lookup: name => varId, or -1 if absent.
int32_t Relational_getId(Variable *scope, const char *name);

// Name accessors for search/rename. varId must be valid.
int Relational_getName(Variable *scope, int32_t varId, char *out, size_t outCap);
bool Relational_setName(Variable *scope, const char *oldName, const char *newName);

// Value accessors — store as-is, no copy. classId describes the pointer type.
void *Relational_getValue(Variable *scope, const char *name);
void *Relational_getValueById(Variable *scope, int32_t varId);
bool Relational_setValue(Variable *scope, const char *name, uint32_t classId, void *ptr);
bool Relational_setValueById(Variable *scope, int32_t varId, void *ptr);

// String sugar — value is a string block (TYPE_STRING_ARRAY). Old block is freed on set.
const char *Relational_getString(Variable *scope, const char *name);
void Relational_setString(Variable *scope, const char *name, const char *value);

// Function pointers — same as value, typed as void (*)(void*).
void *Relational_getFunction(Variable *scope, const char *name);
bool Relational_setFunction(Variable *scope, const char *name, void *fn);

// Spotlight search: query substring (case-insensitive) over all names in scope.
// Fills outIds[cap] with matching varIds ranked exact > prefix > substring.
// Returns the number of matches (may exceed cap; only cap are written).
size_t Relational_search(Variable *scope, const char *query, int32_t *outIds, size_t cap);
size_t Relational_searchAll(Variable *global, Variable *local, const char *query, int32_t *outIds, size_t cap);

#endif
