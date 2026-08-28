#include "system/system.h"

#include "cli/console.h"
#include "input/key.h"
#include "input/mouse.h"
#include "input/touch.h"
#include "time/nanotime.h"
#include "io/hot_file.h"
#include "io/vfs.h"

void System_initializeAll(void) {
    NanoTime_init();
    Console_init();
    Key_init();
    Mouse_init();
    Touch_init();
    
    // Initialize the Virtual File System (resolves anti:// and project:// paths)
    Vfs_init();
    
    // Once paths are resolvable, boot the HotFile watcher
    HotFileSys_init();
}
