#ifndef NET_JSON_H
#define NET_JSON_H

#include <stdbool.h>
#include <stddef.h>
#include <stdint.h>

// net/json.h — off-heap JSON documents (Legacy: net/JSON.java).
//
// Parses into a CALLER-OWNED document: a fixed node pool plus two fixed text
// arenas (keys+strings share one, numbers none). No malloc anywhere — a
// document that does not fit simply reports failure, and the embedder sizes
// the pool for its payload class. Values are views INTO your input buffer;
// only escaped strings are copied (into the scratch arena) during parse.
//
// Writer builds into a caller byte buffer the same way.

#define JSON_MAX_DEPTH 24

typedef enum {
    JSON_NULL = 0,
    JSON_FALSE,
    JSON_TRUE,
    JSON_NUMBER,
    JSON_STRING,
    JSON_ARRAY,
    JSON_OBJECT,
} JsonType;

typedef int32_t JsonRef; // index into the doc's node pool; <0 = none

typedef struct JsonNode {
    JsonType type;
    JsonRef child;     // first element/member
    JsonRef next;      // next sibling
    uint32_t offset;   // string/key start (in src, or scratch when inScratch)
    uint32_t length;   // string/key length (decoded)
    bool inScratch;    // true when offset points into the decode arena
    double number;     // JSON_NUMBER payload
} JsonNode;

typedef struct JsonDoc {
    JsonNode *nodes;       // caller-owned pool
    uint32_t nodeCap;
    uint32_t nodeCount;

    const char *src;       // input text (must outlive all string views)
    char *scratch;         // caller-owned: decoded escape copies
    uint32_t scratchCap;
    uint32_t scratchUsed;

    JsonRef root;
    bool ok;               // parse result
} JsonDoc;

// Parse `text` (NUL-terminated) using caller storage. Returns doc->ok.
bool Json_parse(JsonDoc *doc, JsonNode *nodes, uint32_t nodeCap,
                char *scratch, uint32_t scratchCap, const char *text);

// --- Navigation ---
JsonRef Json_root(const JsonDoc *doc);
bool Json_ok(const JsonDoc *doc);

// Object member lookup by key (objects only). JSON_NONE-ref when missing.
JsonRef Json_member(const JsonDoc *doc, JsonRef obj, const char *key);
// Array/object element by ordinal; JSON_NONE-ref when out of range.
JsonRef Json_at(const JsonDoc *doc, JsonRef node, uint32_t index);
uint32_t Json_count(const JsonDoc *doc, JsonRef node); // array/object arity

JsonType Json_type(const JsonDoc *doc, JsonRef ref);
bool Json_bool(const JsonDoc *doc, JsonRef ref);
double Json_number(const JsonDoc *doc, JsonRef ref);
// String view (NUL-terminated when it came through scratch; use the length).
const char *Json_string(const JsonDoc *doc, JsonRef ref, uint32_t *lengthOut);
// Object member key of a node.
const char *Json_key(const JsonDoc *doc, JsonRef ref, uint32_t *lengthOut);

// --- Writer ---
// Appends a JSON rendering of value into out (NUL-terminated). Strings are
// escaped. Returns bytes written (excluding NUL) or -1 when out ran out.
int64_t Json_writeNumber(char *out, size_t cap, double v);
int64_t Json_writeString(char *out, size_t cap, const char *s);
int64_t Json_writeBool(char *out, size_t cap, bool v);

#endif
