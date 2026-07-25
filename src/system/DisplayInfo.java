package system;

import annotation.HotCode;

@HotCode
public class DisplayInfo {
    private static int monitorCount;
    private static DisplayMonitor[] monitors;
    private static DisplayMonitor primaryMonitor;
    private static Resolution monitorResolution;
    private static Resolution nativeResolution;
    private static int[] refreshRates;
    private static int currentRefreshRate;
    private static boolean hdrSupported;
    private static float displayDensity;

    public static int getMonitorCount() { return monitorCount; }
    public static void setMonitorCount(int val) { monitorCount = val; }
    public static DisplayMonitor[] getMonitors() { return monitors; }
    public static void setMonitors(DisplayMonitor[] val) { monitors = val; }
    public static DisplayMonitor getPrimaryMonitor() { return primaryMonitor; }
    public static void setPrimaryMonitor(DisplayMonitor val) { primaryMonitor = val; }
    public static Resolution getMonitorResolution() { return monitorResolution; }
    public static void setMonitorResolution(Resolution val) { monitorResolution = val; }
    public static Resolution getNativeResolution() { return nativeResolution; }
    public static void setNativeResolution(Resolution val) { nativeResolution = val; }
    public static int[] getRefreshRates() { return refreshRates; }
    public static void setRefreshRates(int[] val) { refreshRates = val; }
    public static int getCurrentRefreshRate() { return currentRefreshRate; }
    public static void setCurrentRefreshRate(int val) { currentRefreshRate = val; }
    public static boolean getHdrSupported() { return hdrSupported; }
    public static void setHdrSupported(boolean val) { hdrSupported = val; }
    public static float getDisplayDensity() { return displayDensity; }
    public static void setDisplayDensity(float val) { displayDensity = val; }
}
