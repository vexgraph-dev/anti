package system;

import annotation.HotCode;

@HotCode
public class HardwareInfo {
    private static long operatingSystem;
    private static long systemArchitecture;
    private static long deviceModel;
    private static long cpuBrand;
    private static int cpuCoreCount;
    private static int cpuThreadCount;
    private static long storageTotalSpace;
    private static long storageAvailableSpace;
    private static long ramTotal;
    private static long ramAvailable;
    private static long javaHeapSize;
    private static long javaHeapMax;
    private static long batteryStatus;
    private static float batteryLevel;

    public static long getOperatingSystem() { return operatingSystem; }
    public static void setOperatingSystem(long val) { operatingSystem = val; }
    public static long getSystemArchitecture() { return systemArchitecture; }
    public static void setSystemArchitecture(long val) { systemArchitecture = val; }
    public static long getDeviceModel() { return deviceModel; }
    public static void setDeviceModel(long val) { deviceModel = val; }
    public static long getCpuBrand() { return cpuBrand; }
    public static void setCpuBrand(long val) { cpuBrand = val; }
    public static int getCpuCoreCount() { return cpuCoreCount; }
    public static void setCpuCoreCount(int val) { cpuCoreCount = val; }
    public static int getCpuThreadCount() { return cpuThreadCount; }
    public static void setCpuThreadCount(int val) { cpuThreadCount = val; }
    public static long getStorageTotalSpace() { return storageTotalSpace; }
    public static void setStorageTotalSpace(long val) { storageTotalSpace = val; }
    public static long getStorageAvailableSpace() { return storageAvailableSpace; }
    public static void setStorageAvailableSpace(long val) { storageAvailableSpace = val; }
    public static long getRamTotal() { return ramTotal; }
    public static void setRamTotal(long val) { ramTotal = val; }
    public static long getRamAvailable() { return ramAvailable; }
    public static void setRamAvailable(long val) { ramAvailable = val; }
    public static long getJavaHeapSize() { return javaHeapSize; }
    public static void setJavaHeapSize(long val) { javaHeapSize = val; }
    public static long getJavaHeapMax() { return javaHeapMax; }
    public static void setJavaHeapMax(long val) { javaHeapMax = val; }
    public static long getBatteryStatus() { return batteryStatus; }
    public static void setBatteryStatus(long val) { batteryStatus = val; }
    public static float getBatteryLevel() { return batteryLevel; }
    public static void setBatteryLevel(float val) { batteryLevel = val; }
}
