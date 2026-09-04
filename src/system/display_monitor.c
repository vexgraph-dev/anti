#include "system/display_monitor.h"

#include <stdlib.h>
#include <string.h>

#include "nio/mem.h"
#include "oop/type.h"
#include "annotation/overview.h"

;;OVERVIEW
/**
 * ============================================================================
 * MODULE: Display_monitor (system/display_monitor.c)
 * ============================================================================
 * single display monitor representation (Legacy: system/DisplayMonitor.java)
 *
 * FUNCTION REGISTRY:
 * ----------------------------------------------------------------------------
 * Constructors:
 *   - DisplayMonitor_0(void)
 *
 * Core Functions:
 *   - DisplayMonitor_free(monitor)
 *
 * Setters:
 *   - DisplayMonitor_setId(m, id)
 *   - DisplayMonitor_setName(m, name)
 *   - DisplayMonitor_setCurrentWidth(m, val)
 *   - DisplayMonitor_setCurrentHeight(m, val)
 *   - DisplayMonitor_setPointWidth(m, val)
 *   - DisplayMonitor_setPointHeight(m, val)
 *   - DisplayMonitor_setNativeWidth(m, val)
 *   - DisplayMonitor_setNativeHeight(m, val)
 *   - DisplayMonitor_setRefreshRate(m, val)
 *   - DisplayMonitor_setHdrSupported(m, val)
 *   - DisplayMonitor_setDpi(m, val)
 *
 * Getters:
 *   - DisplayMonitor_getId(m)
 *   - DisplayMonitor_getName(m)
 *   - DisplayMonitor_getCurrentWidth(m)
 *   - DisplayMonitor_getCurrentHeight(m)
 *   - DisplayMonitor_getPointWidth(m)
 *   - DisplayMonitor_getPointHeight(m)
 *   - DisplayMonitor_getNativeWidth(m)
 *   - DisplayMonitor_getNativeHeight(m)
 *   - DisplayMonitor_getRefreshRate(m)
 *   - DisplayMonitor_getHdrSupported(m)
 *   - DisplayMonitor_getDpi(m)
 * ============================================================================
 */


struct DisplayMonitor {
    uint32_t id;
    char name[128];
    int32_t currentWidth;   // active physical pixels
    int32_t currentHeight;
    int32_t pointWidth;     // logical points
    int32_t pointHeight;
    int32_t nativeWidth;    // hardware panel native pixels
    int32_t nativeHeight;
    int32_t refreshRate;    // Hz
    bool hdrSupported;
    float dpi;              // scale factor / density
};

DisplayMonitor *DisplayMonitor_0(void) {
    DisplayMonitor *m = (DisplayMonitor*) Memory_alloc(TYPE_DISPLAY_MONITOR_SINGLETON, sizeof(DisplayMonitor));
    if (!m)
        return nullptr;
    memset(m, 0, sizeof(DisplayMonitor));
    (*m).dpi = 1.0f;
    return m;
}

void DisplayMonitor_free(DisplayMonitor *monitor) {
    if (!monitor)
        return;
    Memory_free(monitor);
}

uint32_t DisplayMonitor_getId(const DisplayMonitor *m) {
    return m ? (*m).id : 0;
}

void DisplayMonitor_setId(DisplayMonitor *m, uint32_t id) {
    if (m) (*m).id = id;
}

const char *DisplayMonitor_getName(const DisplayMonitor *m) {
    return m ? (*m).name : "";
}

void DisplayMonitor_setName(DisplayMonitor *m, const char *name) {
    if (!m) return;
    if (name) {
        strncpy((*m).name, name, sizeof((*m).name) - 1);
        (*m).name[sizeof((*m).name) - 1] = '\0';
    } else {
        (*m).name[0] = '\0';
    }
}

int32_t DisplayMonitor_getCurrentWidth(const DisplayMonitor *m) {
    return m ? (*m).currentWidth : 0;
}

void DisplayMonitor_setCurrentWidth(DisplayMonitor *m, int32_t val) {
    if (m) (*m).currentWidth = val;
}

int32_t DisplayMonitor_getCurrentHeight(const DisplayMonitor *m) {
    return m ? (*m).currentHeight : 0;
}

void DisplayMonitor_setCurrentHeight(DisplayMonitor *m, int32_t val) {
    if (m) (*m).currentHeight = val;
}

int32_t DisplayMonitor_getPointWidth(const DisplayMonitor *m) {
    return m ? (*m).pointWidth : 0;
}

void DisplayMonitor_setPointWidth(DisplayMonitor *m, int32_t val) {
    if (m) (*m).pointWidth = val;
}

int32_t DisplayMonitor_getPointHeight(const DisplayMonitor *m) {
    return m ? (*m).pointHeight : 0;
}

void DisplayMonitor_setPointHeight(DisplayMonitor *m, int32_t val) {
    if (m) (*m).pointHeight = val;
}

int32_t DisplayMonitor_getNativeWidth(const DisplayMonitor *m) {
    return m ? (*m).nativeWidth : 0;
}

void DisplayMonitor_setNativeWidth(DisplayMonitor *m, int32_t val) {
    if (m) (*m).nativeWidth = val;
}

int32_t DisplayMonitor_getNativeHeight(const DisplayMonitor *m) {
    return m ? (*m).nativeHeight : 0;
}

void DisplayMonitor_setNativeHeight(DisplayMonitor *m, int32_t val) {
    if (m) (*m).nativeHeight = val;
}

int32_t DisplayMonitor_getRefreshRate(const DisplayMonitor *m) {
    return m ? (*m).refreshRate : 0;
}

void DisplayMonitor_setRefreshRate(DisplayMonitor *m, int32_t val) {
    if (m) (*m).refreshRate = val;
}

bool DisplayMonitor_getHdrSupported(const DisplayMonitor *m) {
    return m ? (*m).hdrSupported : false;
}

void DisplayMonitor_setHdrSupported(DisplayMonitor *m, bool val) {
    if (m) (*m).hdrSupported = val;
}

float DisplayMonitor_getDpi(const DisplayMonitor *m) {
    return m ? (*m).dpi : 1.0f;
}

void DisplayMonitor_setDpi(DisplayMonitor *m, float val) {
    if (m) (*m).dpi = val;
}
