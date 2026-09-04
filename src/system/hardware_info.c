#include "system/hardware_info.h"

#include <string.h>

#include "system/discovery.h"
#include "annotation/overview.h"

;;OVERVIEW
/**
 * ============================================================================
 * MODULE: Hardware_info (system/hardware_info.c)
 * ============================================================================
 * host hardware information query (Legacy: system/HardwareInfo.java)
 *
 * FUNCTION REGISTRY:
 * ----------------------------------------------------------------------------
 * Setters:
 *   - HardwareInfo_setOperatingSystem(val)
 *   - HardwareInfo_setSystemArchitecture(val)
 *   - HardwareInfo_setDeviceModel(val)
 *   - HardwareInfo_setCpuBrand(val)
 *   - HardwareInfo_setCpuCoreCount(val)
 *   - HardwareInfo_setCpuThreadCount(val)
 *   - HardwareInfo_setRamTotal(val)
 *   - HardwareInfo_setRamAvailable(val)
 *   - HardwareInfo_setStorageTotalSpace(val)
 *   - HardwareInfo_setStorageAvailableSpace(val)
 *   - HardwareInfo_setHasBattery(val)
 *   - HardwareInfo_setBatteryLevel(val)
 *   - HardwareInfo_setBatteryStatus(val)
 *
 * Getters:
 *   - HardwareInfo_getOperatingSystem(void)
 *   - HardwareInfo_getSystemArchitecture(void)
 *   - HardwareInfo_getDeviceModel(void)
 *   - HardwareInfo_getCpuBrand(void)
 *   - HardwareInfo_getCpuCoreCount(void)
 *   - HardwareInfo_getCpuThreadCount(void)
 *   - HardwareInfo_getRamTotal(void)
 *   - HardwareInfo_getRamAvailable(void)
 *   - HardwareInfo_getStorageTotalSpace(void)
 *   - HardwareInfo_getStorageAvailableSpace(void)
 *   - HardwareInfo_hasBattery(void)
 *   - HardwareInfo_getBatteryLevel(void)
 *   - HardwareInfo_getBatteryStatus(void)
 * ============================================================================
 */


static char     s_operatingSystem[64] = "macOS";
static char     s_systemArchitecture[64] = "arm64";
static char     s_deviceModel[64] = "";
static char     s_cpuBrand[128] = "";
static int32_t  s_cpuCoreCount = 0;
static int32_t  s_cpuThreadCount = 0;
static uint64_t s_ramTotal = 0;
static uint64_t s_ramAvailable = 0;
static uint64_t s_storageTotalSpace = 0;
static uint64_t s_storageAvailableSpace = 0;
static float    s_batteryLevel = -1.0f;
static bool     s_hasBattery = false;
static char     s_batteryStatus[32] = "None";

static inline void ensureDiscovered(void) {
    SystemDiscovery_bootstrap();
}

bool HardwareInfo_hasBattery(void) {
    ensureDiscovered();
    return s_hasBattery;
}

void HardwareInfo_setHasBattery(bool val) {
    s_hasBattery = val;
}

const char *HardwareInfo_getOperatingSystem(void) {
    ensureDiscovered();
    return s_operatingSystem;
}

void HardwareInfo_setOperatingSystem(const char *val) {
    if (val) {
        strncpy(s_operatingSystem, val, sizeof(s_operatingSystem) - 1);
        s_operatingSystem[sizeof(s_operatingSystem) - 1] = '\0';
    }
}

const char *HardwareInfo_getSystemArchitecture(void) {
    ensureDiscovered();
    return s_systemArchitecture;
}

void HardwareInfo_setSystemArchitecture(const char *val) {
    if (val) {
        strncpy(s_systemArchitecture, val, sizeof(s_systemArchitecture) - 1);
        s_systemArchitecture[sizeof(s_systemArchitecture) - 1] = '\0';
    }
}

const char *HardwareInfo_getDeviceModel(void) {
    ensureDiscovered();
    return s_deviceModel;
}

void HardwareInfo_setDeviceModel(const char *val) {
    if (val) {
        strncpy(s_deviceModel, val, sizeof(s_deviceModel) - 1);
        s_deviceModel[sizeof(s_deviceModel) - 1] = '\0';
    }
}

const char *HardwareInfo_getCpuBrand(void) {
    ensureDiscovered();
    return s_cpuBrand;
}

void HardwareInfo_setCpuBrand(const char *val) {
    if (val) {
        strncpy(s_cpuBrand, val, sizeof(s_cpuBrand) - 1);
        s_cpuBrand[sizeof(s_cpuBrand) - 1] = '\0';
    }
}

int32_t HardwareInfo_getCpuCoreCount(void) {
    ensureDiscovered();
    return s_cpuCoreCount;
}

void HardwareInfo_setCpuCoreCount(int32_t val) {
    s_cpuCoreCount = val;
}

int32_t HardwareInfo_getCpuThreadCount(void) {
    ensureDiscovered();
    return s_cpuThreadCount;
}

void HardwareInfo_setCpuThreadCount(int32_t val) {
    s_cpuThreadCount = val;
}

uint64_t HardwareInfo_getRamTotal(void) {
    ensureDiscovered();
    return s_ramTotal;
}

void HardwareInfo_setRamTotal(uint64_t val) {
    s_ramTotal = val;
}

uint64_t HardwareInfo_getRamAvailable(void) {
    ensureDiscovered();
    return s_ramAvailable;
}

void HardwareInfo_setRamAvailable(uint64_t val) {
    s_ramAvailable = val;
}

uint64_t HardwareInfo_getStorageTotalSpace(void) {
    ensureDiscovered();
    return s_storageTotalSpace;
}

void HardwareInfo_setStorageTotalSpace(uint64_t val) {
    s_storageTotalSpace = val;
}

uint64_t HardwareInfo_getStorageAvailableSpace(void) {
    ensureDiscovered();
    return s_storageAvailableSpace;
}

void HardwareInfo_setStorageAvailableSpace(uint64_t val) {
    s_storageAvailableSpace = val;
}

float HardwareInfo_getBatteryLevel(void) {
    ensureDiscovered();
    return s_batteryLevel;
}

void HardwareInfo_setBatteryLevel(float val) {
    s_batteryLevel = val;
}

const char *HardwareInfo_getBatteryStatus(void) {
    ensureDiscovered();
    return s_batteryStatus;
}

void HardwareInfo_setBatteryStatus(const char *val) {
    if (val) {
        strncpy(s_batteryStatus, val, sizeof(s_batteryStatus) - 1);
        s_batteryStatus[sizeof(s_batteryStatus) - 1] = '\0';
    }
}
