#ifndef SYSTEM_SYSTEM_H
#define SYSTEM_SYSTEM_H

// system/system.h — Unified native system information, discovery, and display subsystem
// (Legacy: darling/system/* in anti Java engine)

#include "system/discovery.h"
#include "system/display_info.h"
#include "system/display_monitor.h"
#include "system/hardware_info.h"
#include "system/graphics_info.h"

// Master initializer: boots all core engine subsystems (time, input, cli, io).
// Called exactly once at process start.
void System_initializeAll(void);

#endif
