#ifndef IO_ANTIHOME_H
#define IO_ANTIHOME_H

#include <stdbool.h>

// io/antihome.h — the AntiHome class, ported from io/AntiHome.java.
//
// Per-user "anti" home layout, created once so the engine always has a stable
// place to write before any subsystem touches the disk.
//
//   ~/anti/
//     projects/    - user project workspaces
//     logs/        - engine binary logs (Log default sink)
//     placeholder/ - reserved scratch area

// Each accessor returns a pointer to an internal static buffer (valid until
// the next call to any AntiHome function).
const char *AntiHome_root(void);
const char *AntiHome_projects(void);
const char *AntiHome_logs(void);
const char *AntiHome_placeholder(void);

// Create the full layout; idempotent. Returns true when every dir exists.
bool AntiHome_ensure(void);

// Default log file: ~/anti/logs/engine.bin (truncated every run by Log).
const char *AntiHome_defaultLogPath(void);

#endif