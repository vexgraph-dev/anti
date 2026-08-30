#ifndef SYSTEM_DISPLAY_MONITOR_H
#define SYSTEM_DISPLAY_MONITOR_H

#include <stdbool.h>
#include <stdint.h>
#include "c23/constructor.h"
#include <stddef.h>

// system/display_monitor.h — single display monitor representation (Legacy: system/DisplayMonitor.java)
//
// Represents a physical or virtual display with both its logical point resolution (AppKit/UI coordinates)
// and its physical pixel framebuffer resolution (backing swapchain / panel hardware pixels).

typedef struct DisplayMonitor DisplayMonitor;

DisplayMonitor *DisplayMonitor_0(void);
void            DisplayMonitor_free(DisplayMonitor *monitor);

uint32_t        DisplayMonitor_getId(const DisplayMonitor *m);
void            DisplayMonitor_setId(DisplayMonitor *m, uint32_t id);

const char     *DisplayMonitor_getName(const DisplayMonitor *m);
void            DisplayMonitor_setName(DisplayMonitor *m, const char *name);

// Active Physical Pixel Resolution (actual framebuffer pixels)
int32_t         DisplayMonitor_getCurrentWidth(const DisplayMonitor *m);
void            DisplayMonitor_setCurrentWidth(DisplayMonitor *m, int32_t val);

int32_t         DisplayMonitor_getCurrentHeight(const DisplayMonitor *m);
void            DisplayMonitor_setCurrentHeight(DisplayMonitor *m, int32_t val);

// Logical Point Resolution (AppKit / UI layout coordinates)
int32_t         DisplayMonitor_getPointWidth(const DisplayMonitor *m);
void            DisplayMonitor_setPointWidth(DisplayMonitor *m, int32_t val);

int32_t         DisplayMonitor_getPointHeight(const DisplayMonitor *m);
void            DisplayMonitor_setPointHeight(DisplayMonitor *m, int32_t val);

// Native Physical Panel Resolution (hardware panel pixel grid)
int32_t         DisplayMonitor_getNativeWidth(const DisplayMonitor *m);
void            DisplayMonitor_setNativeWidth(DisplayMonitor *m, int32_t val);

int32_t         DisplayMonitor_getNativeHeight(const DisplayMonitor *m);
void            DisplayMonitor_setNativeHeight(DisplayMonitor *m, int32_t val);

int32_t         DisplayMonitor_getRefreshRate(const DisplayMonitor *m);
void            DisplayMonitor_setRefreshRate(DisplayMonitor *m, int32_t val);

bool            DisplayMonitor_getHdrSupported(const DisplayMonitor *m);
void            DisplayMonitor_setHdrSupported(DisplayMonitor *m, bool val);

float           DisplayMonitor_getDpi(const DisplayMonitor *m);
void            DisplayMonitor_setDpi(DisplayMonitor *m, float val);


#define DisplayMonitor(...) CONSTRUCTOR_DISPATCH(DisplayMonitor, ##__VA_ARGS__)
#endif
