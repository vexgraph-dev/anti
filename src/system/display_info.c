#include "system/display_info.h"

#include <string.h>

#include "system/discovery.h"
#include "annotation/overview.h"

;;OVERVIEW
/**
 * ============================================================================
 * MODULE: Display_info (system/display_info.c)
 * LEVEL: L2 — Behavior (system query behavior API)
 * ============================================================================
 * global display subsystem query (Legacy: system/DisplayInfo.java)
 *
 * FUNCTION REGISTRY:
 * ----------------------------------------------------------------------------
 * Setters:
 *   - DisplayInfo_setMonitorCount(val)
 *   - DisplayInfo_setPrimaryMonitor(val)
 *   - DisplayInfo_setMonitors(monitors, count)
 *   - DisplayInfo_setMonitorResolutionWidth(val)
 *   - DisplayInfo_setMonitorResolutionHeight(val)
 *   - DisplayInfo_setPointResolutionWidth(val)
 *   - DisplayInfo_setPointResolutionHeight(val)
 *   - DisplayInfo_setNativeResolutionWidth(val)
 *   - DisplayInfo_setNativeResolutionHeight(val)
 *   - DisplayInfo_setCurrentRefreshRate(val)
 *   - DisplayInfo_setHdrSupported(val)
 *   - DisplayInfo_setDisplayDensity(val)
 *   - DisplayInfo_setHardwareDensity(val)
 *
 * Getters:
 *   - DisplayInfo_getMonitorCount(void)
 *   - DisplayInfo_getPrimaryMonitor(void)
 *   - DisplayInfo_getMonitor(index)
 *   - DisplayInfo_getMonitors(outCount)
 *   - DisplayInfo_getMonitorResolutionWidth(void)
 *   - DisplayInfo_getMonitorResolutionHeight(void)
 *   - DisplayInfo_getPointResolutionWidth(void)
 *   - DisplayInfo_getPointResolutionHeight(void)
 *   - DisplayInfo_getNativeResolutionWidth(void)
 *   - DisplayInfo_getNativeResolutionHeight(void)
 *   - DisplayInfo_getCurrentRefreshRate(void)
 *   - DisplayInfo_getHdrSupported(void)
 *   - DisplayInfo_getDisplayDensity(void)
 *   - DisplayInfo_getHardwareDensity(void)
 * ============================================================================
 */


#define MAX_SYSTEM_MONITORS 16

static int32_t         s_monitorCount = 0;
static DisplayMonitor *s_monitors[MAX_SYSTEM_MONITORS] = {0};
static DisplayMonitor *s_primaryMonitor = nullptr;

static int32_t s_monitorResolutionWidth = 0;
static int32_t s_monitorResolutionHeight = 0;
static int32_t s_pointResolutionWidth = 0;
static int32_t s_pointResolutionHeight = 0;
static int32_t s_nativeResolutionWidth = 0;
static int32_t s_nativeResolutionHeight = 0;
static int32_t s_currentRefreshRate = 0;
static bool    s_hdrSupported = false;
static float   s_displayDensity = 1.0f;

static inline void ensureDiscovered(void) {
    SystemDiscovery_bootstrap();
}

int32_t DisplayInfo_getMonitorCount(void) {
    ensureDiscovered();
    return s_monitorCount;
}

void DisplayInfo_setMonitorCount(int32_t val) {
    s_monitorCount = val;
}

DisplayMonitor *DisplayInfo_getPrimaryMonitor(void) {
    ensureDiscovered();
    return s_primaryMonitor;
}

void DisplayInfo_setPrimaryMonitor(DisplayMonitor *val) {
    s_primaryMonitor = val;
}

DisplayMonitor *DisplayInfo_getMonitor(size_t index) {
    ensureDiscovered();
    if (index < (size_t)s_monitorCount && index < MAX_SYSTEM_MONITORS)
        return s_monitors[index];
    return nullptr;
}

DisplayMonitor **DisplayInfo_getMonitors(size_t *outCount) {
    ensureDiscovered();
    if (outCount)
        *outCount = (size_t)s_monitorCount;
    return s_monitors;
}

void DisplayInfo_setMonitors(DisplayMonitor **monitors, size_t count) {
    if (count > MAX_SYSTEM_MONITORS)
        count = MAX_SYSTEM_MONITORS;
    for (size_t i = 0; i < count; i++) {
        s_monitors[i] = monitors[i];
    }
    s_monitorCount = (int32_t)count;
}

int32_t DisplayInfo_getMonitorResolutionWidth(void) {
    ensureDiscovered();
    return s_monitorResolutionWidth;
}

void DisplayInfo_setMonitorResolutionWidth(int32_t val) {
    s_monitorResolutionWidth = val;
}

int32_t DisplayInfo_getMonitorResolutionHeight(void) {
    ensureDiscovered();
    return s_monitorResolutionHeight;
}

void DisplayInfo_setMonitorResolutionHeight(int32_t val) {
    s_monitorResolutionHeight = val;
}

int32_t DisplayInfo_getPointResolutionWidth(void) {
    ensureDiscovered();
    return s_pointResolutionWidth;
}

void DisplayInfo_setPointResolutionWidth(int32_t val) {
    s_pointResolutionWidth = val;
}

int32_t DisplayInfo_getPointResolutionHeight(void) {
    ensureDiscovered();
    return s_pointResolutionHeight;
}

void DisplayInfo_setPointResolutionHeight(int32_t val) {
    s_pointResolutionHeight = val;
}

int32_t DisplayInfo_getNativeResolutionWidth(void) {
    ensureDiscovered();
    return s_nativeResolutionWidth;
}

void DisplayInfo_setNativeResolutionWidth(int32_t val) {
    s_nativeResolutionWidth = val;
}

int32_t DisplayInfo_getNativeResolutionHeight(void) {
    ensureDiscovered();
    return s_nativeResolutionHeight;
}

void DisplayInfo_setNativeResolutionHeight(int32_t val) {
    s_nativeResolutionHeight = val;
}

int32_t DisplayInfo_getCurrentRefreshRate(void) {
    ensureDiscovered();
    return s_currentRefreshRate;
}

void DisplayInfo_setCurrentRefreshRate(int32_t val) {
    s_currentRefreshRate = val;
}

bool DisplayInfo_getHdrSupported(void) {
    ensureDiscovered();
    return s_hdrSupported;
}

void DisplayInfo_setHdrSupported(bool val) {
    s_hdrSupported = val;
}

float DisplayInfo_getDisplayDensity(void) {
    ensureDiscovered();
    return s_displayDensity;
}

void DisplayInfo_setDisplayDensity(float val) {
    s_displayDensity = val;
}

static float s_hardwareDensity = 1.0f;

float DisplayInfo_getHardwareDensity(void) {
    ensureDiscovered();
    return s_hardwareDensity;
}

void DisplayInfo_setHardwareDensity(float val) {
    s_hardwareDensity = val;
}
