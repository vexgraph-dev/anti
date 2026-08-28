#include "io/mmap.h"
#include <fcntl.h>
#include <sys/mman.h>
#include <sys/stat.h>
#include <unistd.h>

MemoryMap MemoryMap_open(const char *path) {
    MemoryMap map = { .data = NULL, .size = 0, .valid = false };
    if (!path) return map;

    int fd = open(path, O_RDONLY);
    if (fd < 0) return map;

    struct stat sb;
    if (fstat(fd, &sb) == 0 && sb.st_size > 0) {
        void *ptr = mmap(NULL, (size_t)sb.st_size, PROT_READ, MAP_PRIVATE, fd, 0);
        if (ptr != MAP_FAILED) {
            map.data = ptr;
            map.size = (size_t)sb.st_size;
            map.valid = true;
        }
    }
    
    // POSIX mmap retains its own internal reference to the inode, 
    // so we can safely close the file descriptor immediately to avoid leaking.
    close(fd);
    return map;
}

void MemoryMap_close(MemoryMap *map) {
    if (map && (*map).valid && (*map).data && (*map).size > 0) {
        munmap((*map).data, (*map).size);
        (*map).data = NULL;
        (*map).size = 0;
        (*map).valid = false;
    }
}
