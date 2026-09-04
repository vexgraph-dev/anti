#include "system/system.h"

#include "cli/console.h"
#include "input/key.h"
#include "input/mouse.h"
#include "input/touch.h"
#include "time/nanotime.h"
#include "io/hot_file.h"
#include "annotation/overview.h"

;;OVERVIEW
/**
 * ============================================================================
 * MODULE: System (system/system.c)
 * ============================================================================
 * Unified native system information, discovery, and display subsystem
 *
 * FUNCTION REGISTRY:
 * ----------------------------------------------------------------------------
 * Core Functions:
 *   - System_initializeAll(void)
 * ============================================================================
 */


void System_initializeAll(void) {
    NanoTime_init();
    Console_init();
    Key_init();
    Mouse_init();
    Touch_init();

    // NOTE: Vfs_init() lives in darling (io/vfs.c) since the split — the
    // UI/bake layer boots the VFS itself once paths are resolvable there.
    // Boot the HotFile watcher
    HotFileSys_init();
}
