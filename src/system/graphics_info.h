#ifndef SYSTEM_GRAPHICS_INFO_H
#define SYSTEM_GRAPHICS_INFO_H

#include <stdbool.h>
#include <stdint.h>
#include <stddef.h>

// system/graphics_info.h — GPU and graphics capabilities query (Legacy: system/GraphicsInfo.java)

const char *GraphicsInfo_getGpuName(void);
void        GraphicsInfo_setGpuName(const char *val);

int32_t     GraphicsInfo_getGpuVendorId(void);
void        GraphicsInfo_setGpuVendorId(int32_t val);

int32_t     GraphicsInfo_getGpuDeviceId(void);
void        GraphicsInfo_setGpuDeviceId(int32_t val);

const char *GraphicsInfo_getPrimaryGraphicsApi(void);
void        GraphicsInfo_setPrimaryGraphicsApi(const char *val);

bool        GraphicsInfo_getUnifiedMemoryEnabled(void);
void        GraphicsInfo_setUnifiedMemoryEnabled(bool val);

bool        GraphicsInfo_getComputeShadersEnabled(void);
void        GraphicsInfo_setComputeShadersEnabled(bool val);

bool        GraphicsInfo_getMeshShadersEnabled(void);
void        GraphicsInfo_setMeshShadersEnabled(bool val);

bool        GraphicsInfo_getHardwareRayTracingEnabled(void);
void        GraphicsInfo_setHardwareRayTracingEnabled(bool val);

int32_t     GraphicsInfo_getMaxTextureSize(void);
void        GraphicsInfo_setMaxTextureSize(int32_t val);

uint64_t    GraphicsInfo_getVramTotal(void);
void        GraphicsInfo_setVramTotal(uint64_t val);

uint64_t    GraphicsInfo_getVramAvailable(void);
void        GraphicsInfo_setVramAvailable(uint64_t val);

#endif
