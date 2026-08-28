#include "io/vfs.h"
#include <stdlib.h>
#include <string.h>
#include <stdio.h>
#include <unistd.h>
#include <sys/stat.h>
#include <pwd.h>

static char s_userHome[1024] = {0};
static char s_antiHome[1024] = {0};
static char s_currentProject[1024] = {0};

// Internal recursive directory builder
static void createDirectoryTree(const char *dir) {
    char tmp[1024];
    snprintf(tmp, sizeof(tmp), "%s", dir);
    size_t len = strlen(tmp);
    if (len > 0 && tmp[len - 1] == '/') {
        tmp[len - 1] = '\0';
    }
    
    for (char *p = tmp + 1; *p; p++) {
        if (*p == '/') {
            *p = '\0';
            mkdir(tmp, S_IRWXU); // Ignored if it already exists
            *p = '/';
        }
    }
    mkdir(tmp, S_IRWXU);
}

void Vfs_init(void) {
    const char *home = getenv("HOME");
    
    // Windows compatibility: check USERPROFILE
    if (!home) {
        home = getenv("USERPROFILE");
    }
    
    // macOS / Linux POSIX fallback
    if (!home) {
        struct passwd *pw = getpwuid(getuid());
        if (pw) {
            home = (*pw).pw_dir;
        }
    }
    
    if (!home) home = "."; // Fallback if OS denies home dir access

    snprintf(s_userHome, sizeof(s_userHome), "%s", home);
    snprintf(s_antiHome, sizeof(s_antiHome), "%s/anti", home);
    
    // Ensure core hub directories exist (~/anti/config, ~/anti/projects)
    char pathBuf[1024];
    snprintf(pathBuf, sizeof(pathBuf), "%s/config", s_antiHome);
    createDirectoryTree(pathBuf);
    snprintf(pathBuf, sizeof(pathBuf), "%s/projects", s_antiHome);
    createDirectoryTree(pathBuf);
}

void Vfs_setProject(const char *projectName) {
    if (!projectName || !projectName[0]) {
        s_currentProject[0] = '\0';
        return;
    }
    snprintf(s_currentProject, sizeof(s_currentProject), "%s/projects/%s", s_antiHome, projectName);
    
    // Ensure standard project directories exist
    char pathBuf[1024];
    snprintf(pathBuf, sizeof(pathBuf), "%s/textures", s_currentProject);
    createDirectoryTree(pathBuf);
    snprintf(pathBuf, sizeof(pathBuf), "%s/geometry", s_currentProject);
    createDirectoryTree(pathBuf);
    snprintf(pathBuf, sizeof(pathBuf), "%s/audio", s_currentProject);
    createDirectoryTree(pathBuf);
    snprintf(pathBuf, sizeof(pathBuf), "%s/scripts", s_currentProject);
    createDirectoryTree(pathBuf);
}

bool Vfs_resolve(const char *uri, char *outPath, size_t maxLen) {
    if (!uri || !outPath || maxLen == 0) return false;

    if (strncmp(uri, "anti://", 7) == 0) {
        snprintf(outPath, maxLen, "%s/%s", s_antiHome, uri + 7);
        return true;
    } 
    else if (strncmp(uri, "project://", 10) == 0) {
        if (s_currentProject[0] == '\0') {
            // Cannot resolve project paths if no project is mounted
            return false;
        }
        snprintf(outPath, maxLen, "%s/%s", s_currentProject, uri + 10);
        return true;
    }
    else if (uri[0] == '~') {
        // Expand tilde universally to the OS home directory
        // Example: "~/Downloads/smth.png" -> "/Users/vexgraph/Downloads/smth.png"
        if (uri[1] == '/' || uri[1] == '\\') {
            snprintf(outPath, maxLen, "%s%s", s_userHome, uri + 1);
        } else if (uri[1] == '\0') {
            snprintf(outPath, maxLen, "%s", s_userHome);
        } else {
            // If it's something like "~otheruser/", we don't support it yet, 
            // so we fallback to a raw string copy.
            snprintf(outPath, maxLen, "%s", uri);
        }
        return true;
    }
    else {
        // Fallback: assume it's already an absolute or valid relative OS path
        snprintf(outPath, maxLen, "%s", uri);
        return true;
    }
}
