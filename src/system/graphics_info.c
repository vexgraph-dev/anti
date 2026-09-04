#include "system/graphics_info.h"

#include <string.h>

#include "system/discovery.h"
#include "annotation/overview.h"

;;OVERVIEW
/**
 * ============================================================================
 * MODULE: Graphics_info (system/graphics_info.c)
 * LEVEL: L2 — Behavior (system query behavior API)
 * ============================================================================
 * GPU and graphics capabilities query (Legacy: system/GraphicsInfo.java)
 *
 * FUNCTION REGISTRY:
 * ----------------------------------------------------------------------------
 * Setters:
 *   - GraphicsInfo_setGpuName(val)
 *   - GraphicsInfo_setGpuVendorId(val)
 *   - GraphicsInfo_setGpuDeviceId(val)
 *   - GraphicsInfo_setPrimaryGraphicsApi(val)
 *   - GraphicsInfo_setUnifiedMemoryEnabled(val)
 *   - GraphicsInfo_setComputeShadersEnabled(val)
 *   - GraphicsInfo_setMeshShadersEnabled(val)
 *   - GraphicsInfo_setHardwareRayTracingEnabled(val)
 *   - GraphicsInfo_setMaxTextureSize(val)
 *   - GraphicsInfo_setVramTotal(val)
 *   - GraphicsInfo_setVramAvailable(val)
 *
 * Getters:
 *   - GraphicsInfo_getGpuName(void)
 *   - GraphicsInfo_getGpuVendorId(void)
 *   - GraphicsInfo_getGpuDeviceId(void)
 *   - GraphicsInfo_getPrimaryGraphicsApi(void)
 *   - GraphicsInfo_getUnifiedMemoryEnabled(void)
 *   - GraphicsInfo_getComputeShadersEnabled(void)
 *   - GraphicsInfo_getMeshShadersEnabled(void)
 *   - GraphicsInfo_getHardwareRayTracingEnabled(void)
 *   - GraphicsInfo_getMaxTextureSize(void)
 *   - GraphicsInfo_getVramTotal(void)
 *   - GraphicsInfo_getVramAvailable(void)
 * ============================================================================
 */


static char     s_gpuName[128] = "Apple M-Series GPU";
static int32_t  s_gpuVendorId = 0x106B; // Apple vendor ID
static int32_t  s_gpuDeviceId = 0;
static char     s_primaryGraphicsApi[32] = "Metal/Vulkan";
static bool     s_unifiedMemoryEnabled = true;
static bool     s_computeShadersEnabled = true;
static bool     s_meshShadersEnabled = true;
static bool     s_hardwareRayTracingEnabled = true;
static int32_t  s_maxTextureSize = 16384;
static uint64_t s_vramTotal = 0;
static uint64_t s_vramAvailable = 0;

static inline void ensureDiscovered(void) {
    SystemDiscovery_bootstrap();
}

const char *GraphicsInfo_getGpuName(void) {
    ensureDiscovered();
    return s_gpuName;
}

void GraphicsInfo_setGpuName(const char *val) {
    if (val) {
        strncpy(s_gpuName, val, sizeof(s_gpuName) - 1);
        s_gpuName[sizeof(s_gpuName) - 1] = '\0';
    }
}

int32_t GraphicsInfo_getGpuVendorId(void) {
    ensureDiscovered();
    return s_gpuVendorId;
}

void GraphicsInfo_setGpuVendorId(int32_t val) {
    s_gpuVendorId = val;
}

int32_t GraphicsInfo_getGpuDeviceId(void) {
    ensureDiscovered();
    return s_gpuDeviceId;
}

void GraphicsInfo_setGpuDeviceId(int32_t val) {
    s_gpuDeviceId = val;
}

const char *GraphicsInfo_getPrimaryGraphicsApi(void) {
    ensureDiscovered();
    return s_primaryGraphicsApi;
}

void GraphicsInfo_setPrimaryGraphicsApi(const char *val) {
    if (val) {
        strncpy(s_primaryGraphicsApi, val, sizeof(s_primaryGraphicsApi) - 1);
        s_primaryGraphicsApi[sizeof(s_primaryGraphicsApi) - 1] = '\0';
    }
}

bool GraphicsInfo_getUnifiedMemoryEnabled(void) {
    ensureDiscovered();
    return s_unifiedMemoryEnabled;
}

void GraphicsInfo_setUnifiedMemoryEnabled(bool val) {
    s_unifiedMemoryEnabled = val;
}

bool GraphicsInfo_getComputeShadersEnabled(void) {
    ensureDiscovered();
    return s_computeShadersEnabled;
}

void GraphicsInfo_setComputeShadersEnabled(bool val) {
    s_computeShadersEnabled = val;
}

bool GraphicsInfo_getMeshShadersEnabled(void) {
    ensureDiscovered();
    return s_meshShadersEnabled;
}

void GraphicsInfo_setMeshShadersEnabled(bool val) {
    s_meshShadersEnabled = val;
}

bool GraphicsInfo_getHardwareRayTracingEnabled(void) {
    ensureDiscovered();
    return s_hardwareRayTracingEnabled;
}

void GraphicsInfo_setHardwareRayTracingEnabled(bool val) {
    s_hardwareRayTracingEnabled = val;
}

int32_t GraphicsInfo_getMaxTextureSize(void) {
    ensureDiscovered();
    return s_maxTextureSize;
}

void GraphicsInfo_setMaxTextureSize(int32_t val) {
    s_maxTextureSize = val;
}

uint64_t GraphicsInfo_getVramTotal(void) {
    ensureDiscovered();
    return s_vramTotal;
}

void GraphicsInfo_setVramTotal(uint64_t val) {
    s_vramTotal = val;
}

uint64_t GraphicsInfo_getVramAvailable(void) {
    ensureDiscovered();
    return s_vramAvailable;
}

void GraphicsInfo_setVramAvailable(uint64_t val) {
    s_vramAvailable = val;
}
