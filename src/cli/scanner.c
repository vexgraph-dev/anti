#include "cli/scanner.h"

#include <ctype.h>
#include <stdio.h>

#include "primitive/string.h"
#include "annotation/overview.h"

;;OVERVIEW
/**
 * ============================================================================
 * MODULE: Scanner (cli/scanner.c)
 * LEVEL: L2 — Behavior (CLI behavior API)
 * ============================================================================
 * the Scanner class, ported from cli/Scanner.java.
 *
 * STRUCT FIELDS: none — procedural (operates on stdin stream (returns string blocks))
 *
 * FUNCTION REGISTRY:
 * ----------------------------------------------------------------------------
 * Core Functions:
 *   - Scanner_nextLine(void)
 *   - Scanner_nextWord(void)
 *
 * Getters:
 *   - Scanner_hasNextLine(void)
 * ============================================================================
 */


// scanner.c — the Scanner class, ported from cli/Scanner.java.

static uint8_t read_buffer[SCANNER_BUFFER_SIZE];

bool Scanner_hasNextLine(void) {
    return !feof(stdin);
}

uint8_t *Scanner_nextLine(void) {
    size_t len = 0;
    while (true) {
        int b = getchar();
        if (b == EOF || b == '\n')
            break;
        if (b == '\r')
            continue;
        if (len < SCANNER_BUFFER_SIZE)
            read_buffer[len++] = (uint8_t)b;
        else
            break;
    }
    if (len == 0)
        return nullptr;
    return string_allocateBytes(read_buffer, len);
}

uint8_t *Scanner_nextWord(void) {
    size_t len = 0;
    int b;
    while (true) {
        b = getchar();
        if (b == EOF)
            return nullptr;
        if (!isspace(b)) {
            read_buffer[len++] = (uint8_t)b;
            break;
        }
    }
    while (true) {
        b = getchar();
        if (b == EOF || isspace(b))
            break;
        if (len < SCANNER_BUFFER_SIZE)
            read_buffer[len++] = (uint8_t)b;
        else
            break;
    }
    if (len == 0)
        return nullptr;
    return string_allocateBytes(read_buffer, len);
}
