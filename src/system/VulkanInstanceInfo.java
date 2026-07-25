package system;

import annotation.HotCode;

@HotCode
public class VulkanInstanceInfo {
    private static boolean vulkanSdkInstalled;
    private static long vulkanVersion;
    private static long apiDriversVersion;
    private static long vulkanTotalMemoryBudget;
    private static long vulkanCurrentMemoryUsage;
    private static MemoryHeap[] vulkanMemoryHeaps;
    private static boolean validationLayersEnabled;

    public static boolean getVulkanSdkInstalled() { return vulkanSdkInstalled; }
    public static void setVulkanSdkInstalled(boolean val) { vulkanSdkInstalled = val; }
    public static long getVulkanVersion() { return vulkanVersion; }
    public static void setVulkanVersion(long val) { vulkanVersion = val; }
    public static long getApiDriversVersion() { return apiDriversVersion; }
    public static void setApiDriversVersion(long val) { apiDriversVersion = val; }
    public static long getVulkanTotalMemoryBudget() { return vulkanTotalMemoryBudget; }
    public static void setVulkanTotalMemoryBudget(long val) { vulkanTotalMemoryBudget = val; }
    public static long getVulkanCurrentMemoryUsage() { return vulkanCurrentMemoryUsage; }
    public static void setVulkanCurrentMemoryUsage(long val) { vulkanCurrentMemoryUsage = val; }
    public static MemoryHeap[] getVulkanMemoryHeaps() { return vulkanMemoryHeaps; }
    public static void setVulkanMemoryHeaps(MemoryHeap[] val) { vulkanMemoryHeaps = val; }
    public static boolean getValidationLayersEnabled() { return validationLayersEnabled; }
    public static void setValidationLayersEnabled(boolean val) { validationLayersEnabled = val; }
}
