package system;

import annotation.HotCode;

@HotCode
public class MetalInstanceInfo {
    private static boolean metalRuntimeAvailable;
    private static boolean isAppleSilicon;
    private static int appleGpuFamily;
    private static long appleChipModel;
    private static long metalVersion;
    private static boolean argumentBuffersSupported;
    private static boolean rayTracingTierSupported;

    public static boolean getMetalRuntimeAvailable() { return metalRuntimeAvailable; }
    public static void setMetalRuntimeAvailable(boolean val) { metalRuntimeAvailable = val; }
    public static boolean getIsAppleSilicon() { return isAppleSilicon; }
    public static void setIsAppleSilicon(boolean val) { isAppleSilicon = val; }
    public static int getAppleGpuFamily() { return appleGpuFamily; }
    public static void setAppleGpuFamily(int val) { appleGpuFamily = val; }
    public static long getAppleChipModel() { return appleChipModel; }
    public static void setAppleChipModel(long val) { appleChipModel = val; }
    public static long getMetalVersion() { return metalVersion; }
    public static void setMetalVersion(long val) { metalVersion = val; }
    public static boolean getArgumentBuffersSupported() { return argumentBuffersSupported; }
    public static void setArgumentBuffersSupported(boolean val) { argumentBuffersSupported = val; }
    public static boolean getRayTracingTierSupported() { return rayTracingTierSupported; }
    public static void setRayTracingTierSupported(boolean val) { rayTracingTierSupported = val; }
}
