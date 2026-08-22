#ifndef SYSTEM_DISPLAY_INFO_H
#define SYSTEM_DISPLAY_INFO_H

#include <stdbool.h>
#include <stdint.h>
#include <stddef.h>

#include "system/display_monitor.h"

// system/display_info.h — global display subsystem query (Legacy: system/DisplayInfo.java)
//
// Tracks primary monitor properties and the full list of active monitors.
// All getters perform lazy discovery on first invocation.

int32_t         DisplayInfo_getMonitorCount(void);
void            DisplayInfo_setMonitorCount(int32_t val);

DisplayMonitor *DisplayInfo_getPrimaryMonitor(void);
void            DisplayInfo_setPrimaryMonitor(DisplayMonitor *val);

DisplayMonitor *DisplayInfo_getMonitor(size_t index);
DisplayMonitor **DisplayInfo_getMonitors(size_t *outCount);
void            DisplayInfo_setMonitors(DisplayMonitor **monitors, size_t count);

// Primary Monitor Active Physical Pixel Resolution
int32_t         DisplayInfo_getMonitorResolutionWidth(void);
void            DisplayInfo_setMonitorResolutionWidth(int32_t val);

int32_t         DisplayInfo_getMonitorResolutionHeight(void);
void            DisplayInfo_setMonitorResolutionHeight(int32_t val);

// Primary Monitor Logical Point Resolution (AppKit / UI space)
int32_t         DisplayInfo_getPointResolutionWidth(void);
void            DisplayInfo_setPointResolutionWidth(int32_t val);

int32_t         DisplayInfo_getPointResolutionHeight(void);
void            DisplayInfo_setPointResolutionHeight(int32_t val);

// Primary Monitor Native Hardware Panel Resolution
int32_t         DisplayInfo_getNativeResolutionWidth(void);
void            DisplayInfo_setNativeResolutionWidth(int32_t val);

int32_t         DisplayInfo_getNativeResolutionHeight(void);
void            DisplayInfo_setNativeResolutionHeight(int32_t val);

int32_t         DisplayInfo_getCurrentRefreshRate(void);
void            DisplayInfo_setCurrentRefreshRate(int32_t val);

bool            DisplayInfo_getHdrSupported(void);
void            DisplayInfo_setHdrSupported(bool val);

float           DisplayInfo_getDisplayDensity(void);
void            DisplayInfo_setDisplayDensity(float val);

// Native Hardware Panel Density (Native Panel Width / Point Width)
float           DisplayInfo_getHardwareDensity(void);
void            DisplayInfo_setHardwareDensity(float val);

#endif
