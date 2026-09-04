#ifndef RELATIONAL_VARIABLE_H
#define RELATIONAL_VARIABLE_H

#include <stdbool.h>
#include <stddef.h>
#include <stdint.h>

// relational/variable.h — the relational symbol registry (Legacy: variable/Variable.java).
//
// Maps a lowercase 32-char name to a (classId, targetPointer) payload. Every
// registered symbol is a row: the name is the key, the pointer is the value.
// This is the heart of "everything is a pointer" — an off-heap key/value table
// whose values are themselves addresses of other typed blocks.
//
// Slot layout (48 bytes):
//   [ 32B name ][ 4B classId ][ 4B pad ][ 8B pointer ]
//   `----------- packed into 4 uint64 words, lowercase ----------'
//
// Name lookup goes through an open-addressing hash map (40-byte slots:
// 4 longs of name + a var id). -1 is the empty sentinel.

#define VARIABLE_NAME_SIZE 32
#define VARIABLE_SLOT_SIZE 48
#define VARIABLE_MAP_SLOT_SIZE 40
#define VARIABLE_DEFAULT_CAPACITY 1024

typedef struct Variable {
    uint8_t *arena;        // slot arena (activeCount * SLOT_SIZE)
    size_t capacity;       // slot count
    size_t activeCount;    // registered symbols
    uint8_t *map;          // open-addressing name hash map
    size_t mapCapacity;    // map slot count
    bool active;
} Variable;

// Set up the registry (allocates the initial arena + map). Returns false on OOM.
bool Variable_init(Variable *v);

// Release all memory. Safe to call twice.
void Variable_shutdown(Variable *v);

// Register name => (classId, targetPointer), or update the payload if the name
// already exists. Returns the assigned var id, or -1 on invalid input.
int32_t Variable_instant(Variable *v, const char *name, uint32_t classId, uintptr_t targetPointer);

// Rename an existing symbol. Fails (false) if oldName is absent or newName is
// already taken.
bool Variable_rename(Variable *v, const char *oldName, const char *newName);

// Resolve a name to its var id. Returns -1 if not registered.
int32_t Variable_getId(Variable *v, const char *name);

// Payload accessors. varId must be a valid registered id.
uintptr_t Variable_getPointer(Variable *v, int32_t varId);
void Variable_setPointer(Variable *v, int32_t varId, uintptr_t targetPointer);
bool Variable_compareAndSetPointer(Variable *v, int32_t varId, uintptr_t expected, uintptr_t newPointer);
uint32_t Variable_getClassId(Variable *v, int32_t varId);

// Copy the registered name into _out (nul-terminated, at most outCap bytes).
// Returns the string length, or -1 on bad varId / short buffer.
int Variable_getName(Variable *v, int32_t varId, char *out, size_t outCap);

// Number of registered symbols.
size_t Variable_getActiveCount(Variable *v);

#endif