package system;

import annotation.HotCode;

@HotCode
public class PerformanceInfo {
    private static float gpuTimestampPeriod;
    private static boolean variableRateShadingSupported;
    private static long vrsSurfaceProperties;
    private static boolean dynamicResolutionScaleSupported;
    private static int thermalMitigationLevel;
    private static boolean powerSavingModeActive;

    public static float getGpuTimestampPeriod() { return gpuTimestampPeriod; }
    public static void setGpuTimestampPeriod(float val) { gpuTimestampPeriod = val; }
    public static boolean getVariableRateShadingSupported() { return variableRateShadingSupported; }
    public static void setVariableRateShadingSupported(boolean val) { variableRateShadingSupported = val; }
    public static long getVrsSurfaceProperties() { return vrsSurfaceProperties; }
    public static void setVrsSurfaceProperties(long val) { vrsSurfaceProperties = val; }
    public static boolean getDynamicResolutionScaleSupported() { return dynamicResolutionScaleSupported; }
    public static void setDynamicResolutionScaleSupported(boolean val) { dynamicResolutionScaleSupported = val; }
    public static int getThermalMitigationLevel() { return thermalMitigationLevel; }
    public static void setThermalMitigationLevel(int val) { thermalMitigationLevel = val; }
    public static boolean getPowerSavingModeActive() { return powerSavingModeActive; }
    public static void setPowerSavingModeActive(boolean val) { powerSavingModeActive = val; }
}
