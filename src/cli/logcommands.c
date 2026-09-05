#include "cli/logcommands.h"

#include <stdio.h>

#include "io/file.h"
#include "io/logparser.h"
#include "annotation/overview.h"

;;OVERVIEW
/**
 * ============================================================================
 * CLASS: Logcommands (cli/logcommands.c)
 * LEVEL: L2 — Behavior (CLI behavior API)
 * ============================================================================
 * the LogCommands class, ported from cli/LogCommands.java.
 *
 * STRUCT FIELDS (local to this file):
 * ----------------------------------------------------------------------------
 *   Cat {
 *     int64_t base_ts;           // first record timestamp (relative base)
 *     int64_t shown;             // records printed so far
 *     int64_t total;             // records scanned so far
 *     int limit;                 // max records to print (<0 = all)
 *   }
 *
 * FUNCTION REGISTRY:
 * ----------------------------------------------------------------------------
 * Core Functions:
 *   - LogCommands_stat(path)
 *   - LogCommands_cat(path, limit)
 * ============================================================================
 */


// logcommands.c — the LogCommands class, ported from cli/LogCommands.java.

void LogCommands_stat(const char *path) {
    if (!path)
        return;
    if (!LogParser_isLogFile(path)) {
        printf("not a log file: %s\n", path);
        return;
    }
    int64_t records = LogParser_count(path);
    File *f = File_open(path, FILE_MODE_READ);
    int64_t bytes = f ? File_size(f) : 0;
    if (f)
        File_close(f);
    printf("log: %s\n  records: %lld\n  bytes: %lld (%lld payload)\n",
       path, (long long)records,
       (long long)bytes,
       (long long)(bytes - LOGPARSER_HEADER_BYTES)
    );
}

typedef struct Cat {
    int64_t base_ts;
    int64_t shown;
    int64_t total;
    int limit;
} Cat;

static void cat_handler(void *userdata, int kind, int64_t ts,
                        int64_t v0, int64_t v1, int64_t v2,
                        int64_t v3, int64_t v4) {
    Cat *cat = userdata;
    (*cat).total++;
    if ((*cat).base_ts < 0)
        (*cat).base_ts = ts;
    if ((*cat).limit >= 0 && (*cat).shown >= (*cat).limit)
        return;
    (*cat).shown++;
    char line[128];
    if (LogParser_formatRecord(line, sizeof(line), kind, ts, (*cat).base_ts,
                               LogParser_kindName(kind), v0, v1, v2, v3, v4) >= 0)
        printf("%s\n", line);
}

void LogCommands_cat(const char *path, int limit) {
    if (!path)
        return;
    Cat cat = { .base_ts = -1, .shown = 0, .total = 0, .limit = limit };
    (void)LogParser_parse(path, cat_handler, &cat);
    if (limit >= 0 && cat.shown < cat.total)
        printf("(%lld more)\n", (long long)(cat.total - cat.shown));
}
