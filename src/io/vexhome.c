#include "io/vexhome.h"

#include <stdio.h>
#include <stdlib.h>
#include <string.h>

#include "io/file.h"

// vexhome.c — VexHome port (was AntiHome; Legacy: io/AntiHome.java). ~/anti layout.

#define ROOT_NAME      "anti"
#define PROJECTS_NAME  "projects"
#define LOGS_NAME      "logs"
#define FONTS_NAME     "fonts"
#define PLACEHOLDER    "placeholder"
#define LOG_FILE       "engine.bin"

static char root_buf[FILE_PATH_MAX];
static char projects_buf[FILE_PATH_MAX];
static char logs_buf[FILE_PATH_MAX];
static char fonts_buf[FILE_PATH_MAX];
static char placeholder_buf[FILE_PATH_MAX];
static bool ensured;

static void build_path(char *out, const char *sub) {
    const char *home = getenv("HOME");
    if (!home || *home == '\0')
        home = ".";
    if (*sub == '\0')
        snprintf(out, FILE_PATH_MAX, "%s/%s", home, ROOT_NAME);
    else
        snprintf(out, FILE_PATH_MAX, "%s/%s/%s", home, ROOT_NAME, sub);
}

const char *VexHome_root(void) {
    build_path(root_buf, "");
    return root_buf;
}

const char *VexHome_projects(void) {
    build_path(projects_buf, PROJECTS_NAME);
    return projects_buf;
}

const char *VexHome_logs(void) {
    build_path(logs_buf, LOGS_NAME);
    return logs_buf;
}

const char *VexHome_fonts(void) {
    build_path(fonts_buf, FONTS_NAME);
    return fonts_buf;
}

const char *VexHome_placeholder(void) {
    build_path(placeholder_buf, PLACEHOLDER);
    return placeholder_buf;
}

bool VexHome_ensure(void) {
    if (ensured)
        return true;
    if (!File_mkdirs(VexHome_root()))
        return false;
    if (!File_mkdirs(VexHome_projects()))
        return false;
    if (!File_mkdirs(VexHome_logs()))
        return false;
    if (!File_mkdirs(VexHome_fonts()))
        return false;
    if (!File_mkdirs(VexHome_placeholder()))
        return false;
    ensured = true;
    return true;
}

const char *VexHome_defaultLogPath(void) {
    VexHome_ensure();
    snprintf(logs_buf, FILE_PATH_MAX, "%s/%s", VexHome_logs(), LOG_FILE);
    return logs_buf;
}