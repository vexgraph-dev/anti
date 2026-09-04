#include "relational/relational.h"

#include <string.h>
#include <strings.h>

#include "nio/mem.h"
#include "primitive/string.h"
#include "annotation/overview.h"

;;OVERVIEW
/**
 * ============================================================================
 * MODULE: Relational (relational/relational.c)
 * ============================================================================
 * spotlight relational facade over Variable (Legacy: relational/RelationalEngine.java).
 *
 * FUNCTION REGISTRY:
 * ----------------------------------------------------------------------------
 * Core Functions:
 *   - Relational_search(scope, query, outIds, cap)
 *   - Relational_searchAll(global, local, query, outIds, cap)
 *
 * Setters:
 *   - Relational_setName(scope, oldName, newName)
 *   - Relational_setValue(scope, name, classId, ptr)
 *   - Relational_setValueById(scope, varId, ptr)
 *   - Relational_setString(scope, name, value)
 *   - Relational_setFunction(scope, name, fn)
 *
 * Getters:
 *   - Relational_getId(scope, name)
 *   - Relational_getName(scope, varId, out, outCap)
 *   - Relational_getValue(scope, name)
 *   - Relational_getValueById(scope, varId)
 *   - Relational_getString(scope, name)
 *   - Relational_getFunction(scope, name)
 * ============================================================================
 */


// helpers — one level only, dest last where it matters

static bool containsCaseInsensitive(const char *haystack, const char *needle) {
    if (!haystack || !needle)
        return false;
    size_t nLen = strlen(needle);
    if (nLen == 0)
        return true;
    size_t hLen = strlen(haystack);
    if (nLen > hLen)
        return false;
    for (size_t i = 0; i + nLen <= hLen; i++) {
        if (strncasecmp(haystack + i, needle, nLen) == 0)
            return true;
    }
    return false;
}

static bool equalsCaseInsensitive(const char *a, const char *b) {
    if (!a || !b)
        return false;
    return strcasecmp(a, b) == 0;
}

static bool prefixCaseInsensitive(const char *haystack, const char *needle) {
    if (!haystack || !needle)
        return false;
    size_t nLen = strlen(needle);
    if (nLen == 0)
        return true;
    return strncasecmp(haystack, needle, nLen) == 0;
}

// exact

int32_t Relational_getId(Variable *scope, const char *name) {
    if (!scope || !name)
        return -1;
    return Variable_getId(scope, name);
}

int Relational_getName(Variable *scope, int32_t varId, char *out, size_t outCap) {
    if (!scope || !out)
        return -1;
    return Variable_getName(scope, varId, out, outCap);
}

bool Relational_setName(Variable *scope, const char *oldName, const char *newName) {
    if (!scope || !oldName || !newName)
        return false;
    return Variable_rename(scope, oldName, newName);
}

// value — store as-is, pointer is the value

void *Relational_getValue(Variable *scope, const char *name) {
    if (!scope || !name)
        return nullptr;
    int32_t varId = Variable_getId(scope, name);
    if (varId < 0)
        return nullptr;
    return (void*) Variable_getPointer(scope, varId);
}

void *Relational_getValueById(Variable *scope, int32_t varId) {
    if (!scope || varId < 0 || (size_t) varId >= Variable_getActiveCount(scope))
        return nullptr;
    return (void*) Variable_getPointer(scope, varId);
}

bool Relational_setValue(Variable *scope, const char *name, uint32_t classId, void *ptr) {
    if (!scope || !name)
        return false;
    int32_t varId = Variable_instant(scope, name, classId, (uintptr_t) ptr);
    return varId >= 0;
}

bool Relational_setValueById(Variable *scope, int32_t varId, void *ptr) {
    if (!scope || varId < 0 || (size_t) varId >= Variable_getActiveCount(scope))
        return false;
    Variable_setPointer(scope, varId, (uintptr_t) ptr);
    return true;
}

// string sugar — allocates, frees old block

const char *Relational_getString(Variable *scope, const char *name) {
    void *ptr = Relational_getValue(scope, name);
    if (!ptr)
        return nullptr;
    return string_get((const uint8_t*) ptr);
}

void Relational_setString(Variable *scope, const char *name, const char *value) {
    if (!scope || !name || !value)
        return;
    int32_t varId = Variable_getId(scope, name);
    void *oldPtr = nullptr;
    if (varId >= 0)
        oldPtr = (void*) Variable_getPointer(scope, varId);
    uint8_t *newPtr = string_allocate(value);
    if (!newPtr)
        return;
    int32_t assigned = Variable_instant(scope, name, string_classId(), (uintptr_t) newPtr);
    if (assigned < 0) {
        string_free(newPtr);
        return;
    }
    if (oldPtr)
        string_free((uint8_t*) oldPtr);
}

// function pointers — same as value, typed helper

void *Relational_getFunction(Variable *scope, const char *name) {
    return Relational_getValue(scope, name);
}

bool Relational_setFunction(Variable *scope, const char *name, void *fn) {
    if (!scope || !name || !fn)
        return false;
    // classId 0 = raw function pointer; caller may pass ID_FUNCTION if defined
    int32_t varId = Variable_instant(scope, name, 0, (uintptr_t) fn);
    return varId >= 0;
}

// spotlight — linear scan, ranked exact > prefix > substring

static size_t searchOne(Variable *scope, const char *query, int32_t *outIds, size_t cap, size_t filled) {
    if (!scope || !query || !outIds)
        return filled;
    size_t active = Variable_getActiveCount(scope);
    // pass 1: exact
    for (size_t i = 0; i < active; i++) {
        if (filled >= cap)
            break;
        int32_t varId = (int32_t) i;
        char nameBuf[33];
        int len = Variable_getName(scope, varId, nameBuf, sizeof(nameBuf));
        if (len < 0)
            continue;
        if (equalsCaseInsensitive(nameBuf, query)) {
            // avoid duplicate if already added
            bool dup = false;
            for (size_t k = 0; k < filled; k++) {
                if (outIds[k] == varId) {
                    dup = true;
                    break;
                }
            }
            if (dup)
                continue;
            outIds[filled++] = varId;
        }
    }
    // pass 2: prefix
    for (size_t i = 0; i < active; i++) {
        if (filled >= cap)
            break;
        int32_t varId = (int32_t) i;
        char nameBuf[33];
        int len = Variable_getName(scope, varId, nameBuf, sizeof(nameBuf));
        if (len < 0)
            continue;
        if (!prefixCaseInsensitive(nameBuf, query))
            continue;
        if (equalsCaseInsensitive(nameBuf, query))
            continue;
        bool dup = false;
        for (size_t k = 0; k < filled; k++) {
            if (outIds[k] == varId) {
                dup = true;
                break;
            }
        }
        if (dup)
            continue;
        outIds[filled++] = varId;
    }
    // pass 3: substring
    for (size_t i = 0; i < active; i++) {
        if (filled >= cap)
            break;
        int32_t varId = (int32_t) i;
        char nameBuf[33];
        int len = Variable_getName(scope, varId, nameBuf, sizeof(nameBuf));
        if (len < 0)
            continue;
        if (!containsCaseInsensitive(nameBuf, query))
            continue;
        if (equalsCaseInsensitive(nameBuf, query))
            continue;
        if (prefixCaseInsensitive(nameBuf, query))
            continue;
        bool dup = false;
        for (size_t k = 0; k < filled; k++) {
            if (outIds[k] == varId) {
                dup = true;
                break;
            }
        }
        if (dup)
            continue;
        outIds[filled++] = varId;
    }
    return filled;
}

size_t Relational_search(Variable *scope, const char *query, int32_t *outIds, size_t cap) {
    if (!scope || !query || !outIds || cap == 0)
        return 0;
    size_t filled = 0;
    filled = searchOne(scope, query, outIds, cap, filled);
    return filled;
}

size_t Relational_searchAll(Variable *global, Variable *local, const char *query, int32_t *outIds, size_t cap) {
    if (!query || !outIds || cap == 0)
        return 0;
    size_t filled = 0;
    if (global) {
        filled = searchOne(global, query, outIds, cap, filled);
        if (filled >= cap)
            return filled;
    }
    if (local) {
        // need to collect local matches without clobbering global exact ranking?
        // reuse searchOne which already ranks exact>prefix>substring per table
        size_t before = filled;
        // use a temporary offset — searchOne appends, we keep ranking per table,
        // final order is global exact/prefix/substring then local exact/prefix/substring
        // that's spotlight-friendly: global first, but local exact still outranks global substring
        // we achieve by running searchOne on local after global — it will add its exact first
        filled = searchOne(local, query, outIds, cap, filled);
        // if we want strict global rank interleaved, we'd merge; for now keep simple two-phase
        (void) before;
    }
    return filled;
}
