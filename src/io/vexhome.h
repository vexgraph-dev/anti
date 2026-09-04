#ifndef IO_VEXHOME_H
#define IO_VEXHOME_H

#include <stdbool.h>

// io/vexhome.h — the VexHome class (was AntiHome, renamed on the
// vexspoke/darling split; Legacy: io/AntiHome.java).
//
// Per-user "anti" home layout, created once so the engine always has a stable
// place to write before any subsystem touches the disk.
//
//   ~/anti/
//     projects/    - user project workspaces
//     logs/        - engine binary logs (Log default sink)
//     fonts/       - baked font store (FontBake .antifont files)
//     placeholder/ - reserved scratch area

// Each accessor returns a pointer to an internal static buffer (valid until
// the next call to any AntiHome function).
const char *VexHome_root(void);
const char *VexHome_projects(void);
const char *VexHome_logs(void);
const char *VexHome_fonts(void);
const char *VexHome_placeholder(void);

// Create the full layout; idempotent. Returns true when every dir exists.
bool VexHome_ensure(void);

// Default log file: ~/anti/logs/engine.bin (truncated every run by Log).
const char *VexHome_defaultLogPath(void);

#endif