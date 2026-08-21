#include "io/filewriter.h"

#include <string.h>

#include "io/file.h"

// filewriter.c — FileWriter port (Legacy: io/FileWriter.java).

bool FileWriter_open(FileWriter *w, const char *path) {
    if (!path)
        return false;
    (*w).open = false;
    (*w).bytes_written = 0;

    const char *slash = strrchr(path, '/');
    if (slash && slash != path) {
        size_t len = (size_t)(slash - path);
        char parent[FILE_PATH_MAX];
        if (len >= FILE_PATH_MAX)
            return false;
        memcpy(parent, path, len);
        parent[len] = '\0';
        if (!File_mkdirs(parent))
            return false;
    }

    (*w).out = fopen(path, "wb");
    if (!(*w).out)
        return false;
    setvbuf((*w).out, (*w).buffer, _IOFBF, sizeof((*w).buffer));
    (*w).open = true;
    return true;
}

void FileWriter_write(FileWriter *w, const uint8_t *data, size_t len) {
    if (!(*w).open || len == 0)
        return;
    if (fwrite(data, 1, len, (*w).out) == len)
        (*w).bytes_written += len;
}

void FileWriter_flush(FileWriter *w) {
    if ((*w).open)
        fflush((*w).out);
}

void FileWriter_close(FileWriter *w) {
    if (!(*w).open)
        return;
    fflush((*w).out);
    fclose((*w).out);
    (*w).out = NULL;
    (*w).open = false;
}

uint64_t FileWriter_bytesWritten(const FileWriter *w) {
    return (*w).bytes_written;
}