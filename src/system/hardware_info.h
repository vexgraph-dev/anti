#ifndef SYSTEM_HARDWARE_INFO_H
#define SYSTEM_HARDWARE_INFO_H

#include <stdbool.h>
#include <stdint.h>
#include <stddef.h>

// system/hardware_info.h — host hardware information query (Legacy: system/HardwareInfo.java)

const char *HardwareInfo_getOperatingSystem(void);
void        HardwareInfo_setOperatingSystem(const char *val);

const char *HardwareInfo_getSystemArchitecture(void);
void        HardwareInfo_setSystemArchitecture(const char *val);

const char *HardwareInfo_getDeviceModel(void);
void        HardwareInfo_setDeviceModel(const char *val);

const char *HardwareInfo_getCpuBrand(void);
void        HardwareInfo_setCpuBrand(const char *val);

int32_t     HardwareInfo_getCpuCoreCount(void);
void        HardwareInfo_setCpuCoreCount(int32_t val);

int32_t     HardwareInfo_getCpuThreadCount(void);
void        HardwareInfo_setCpuThreadCount(int32_t val);

uint64_t    HardwareInfo_getRamTotal(void);
void        HardwareInfo_setRamTotal(uint64_t val);

uint64_t    HardwareInfo_getRamAvailable(void);
void        HardwareInfo_setRamAvailable(uint64_t val);

uint64_t    HardwareInfo_getStorageTotalSpace(void);
void        HardwareInfo_setStorageTotalSpace(uint64_t val);

uint64_t    HardwareInfo_getStorageAvailableSpace(void);
void        HardwareInfo_setStorageAvailableSpace(uint64_t val);

bool        HardwareInfo_hasBattery(void);
void        HardwareInfo_setHasBattery(bool val);

float       HardwareInfo_getBatteryLevel(void);
void        HardwareInfo_setBatteryLevel(float val);

const char *HardwareInfo_getBatteryStatus(void);
void        HardwareInfo_setBatteryStatus(const char *val);

#endif
