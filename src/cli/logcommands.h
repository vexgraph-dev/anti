#ifndef CLI_LOGCOMMANDS_H
#define CLI_LOGCOMMANDS_H

// cli/logcommands.h — the LogCommands class, ported from cli/LogCommands.java.
//
// Read-side of Log for the CLI: human summaries and formatted record dumps,
// implemented directly on LogParser. No command allocation on this path.

// Print a summary of the log at path:
//   not a log file: <path>        — or —
//   log: <path>
//     records: <n>
//     bytes: <total> (<payload> payload)
void LogCommands_stat(const char *path);

// Print formatted records to stdout, oldest-first (newest last).
// limit < 0 prints everything; otherwise at most limit records, followed by
// "(<n> more)" when records were elided.
void LogCommands_cat(const char *path, int limit);

#endif