#include "io/antihome.h"

#include <stdio.h>
#include <stdlib.h>
#include <string.h>

#include "io/file.h"

// antihome.c — AntiHome port (Legacy: io/AntiHome.java). ~/anti layout.

#define ROOT_NAME      "anti"
#define PROJECTS_NAME  "projects"
#define LOGS_NAME      "logs"
#define PLACEHOLDER    "placeholder"
#define LOG_FILE       "engine.bin"

static char root_buf[FILE_PATH_MAX];
static char projects_buf[FILE_PATH_MAX];
static char logs_buf[FILE_PATH_MAX];
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

const char *AntiHome_root(void) {
    build_path(root_buf, "");
    return root_buf;
}

const char *AntiHome_projects(void) {
    build_path(projects_buf, PROJECTS_NAME);
    return projects_buf;
}

const char *AntiHome_logs(void) {
    build_path(logs_buf, LOGS_NAME);
    return logs_buf;
}

const char *AntiHome_placeholder(void) {
    build_path(placeholder_buf, PLACEHOLDER);
    return placeholder_buf;
}

bool AntiHome_ensure(void) {
    if (ensured)
        return true;
    if (!File_mkdirs(AntiHome_root()))
        return false;
    if (!File_mkdirs(AntiHome_projects()))
        return false;
    if (!File_mkdirs(AntiHome_logs()))
        return false;
    if (!File_mkdirs(AntiHome_placeholder()))
        return false;
    ensured = true;
    return true;
}

const char *AntiHome_defaultLogPath(void) {
    AntiHome_ensure();
    snprintf(logs_buf, FILE_PATH_MAX, "%s/%s", AntiHome_logs(), LOG_FILE);
    return logs_buf;
}