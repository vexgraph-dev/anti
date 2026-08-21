#include "cli/commandparser.h"

#include <ctype.h>
#include <stdlib.h>
#include <string.h>

#include "primitive/string.h"

// commandparser.c — the CommandParser class, ported from cli/CommandParser.java.

Command *CommandParser_parse(const char *line) {
    if (!line)
        return NULL;

    size_t n = strlen(line);
    size_t start = 0;
    while (start < n && isspace((unsigned char)line[start]))
        start++;
    size_t end = n;
    while (end > start && isspace((unsigned char)line[end - 1]))
        end--;
    if (start == end)
        return NULL;

    // Split on whitespace runs: count tokens first so arguments land contiguously.
    size_t tokens = 0;
    size_t i = start;
    while (i < end) {
        while (i < end && !isspace((unsigned char)line[i]))
            i++;
        tokens++;
        while (i < end && isspace((unsigned char)line[i]))
            i++;
    }
    size_t argc = tokens - 1;

    uint8_t *name = NULL;
    uint8_t **args = argc > 0 ? (uint8_t **)calloc(argc, sizeof(uint8_t *)) : NULL;
    if (argc > 0 && !args)
        return NULL;

    size_t tok = 0;
    i = start;
    while (i < end) {
        size_t t0 = i;
        while (i < end && !isspace((unsigned char)line[i]))
            i++;
        if (tok == 0)
            name = string_allocateBytes((const uint8_t *)line + t0, i - t0);
        else
            args[tok - 1] = string_allocateBytes((const uint8_t *)line + t0, i - t0);
        tok++;
        while (i < end && isspace((unsigned char)line[i]))
            i++;
    }

    if (!name) {
        for (size_t k = 0; k < argc; k++)
            string_free(args[k]);
        free(args);
        return NULL;
    }

    Command *cmd = Command_allocate(name, args, argc);
    if (!cmd) {
        string_free(name);
        for (size_t k = 0; k < argc; k++)
            string_free(args[k]);
        free(args);
        return NULL;
    }
    free(args);
    return cmd;
}