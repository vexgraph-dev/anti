package system;

import annotation.HotCode;
import annotation.Volatile;

@HotCode
@Volatile
public class DisplayInfo {
    private static int monitorCount;
    private static long monitorsPointer;        // Pointer to primitive.Long array of DisplayMonitor struct pointers
    private static long primaryMonitorPointer;  // Pointer to DisplayMonitor struct
    
    // Global parameters
    private static int monitorResolutionWidth;
    private static int monitorResolutionHeight;
    private static int nativeResolutionWidth;
    private static int nativeResolutionHeight;
    private static int currentRefreshRate;
    private static boolean hdrSupported;
    private static float displayDensity;

    private DisplayInfo() {}

    public static int getMonitorCount() { return monitorCount; }
    public static void setMonitorCount(int val) { monitorCount = val; }

    public static long getMonitorsPointer() { return monitorsPointer; }
    public static void setMonitorsPointer(long val) { monitorsPointer = val; }

    public static long getPrimaryMonitorPointer() { return primaryMonitorPointer; }
    public static void setPrimaryMonitorPointer(long val) { primaryMonitorPointer = val; }

    public static int getMonitorResolutionWidth() { return monitorResolutionWidth; }
    public static void setMonitorResolutionWidth(int val) { monitorResolutionWidth = val; }

    public static int getMonitorResolutionHeight() { return monitorResolutionHeight; }
    public static void setMonitorResolutionHeight(int val) { monitorResolutionHeight = val; }

    public static int getNativeResolutionWidth() { return nativeResolutionWidth; }
    public static void setNativeResolutionWidth(int val) { nativeResolutionWidth = val; }

    public static int getNativeResolutionHeight() { return nativeResolutionHeight; }
    public static void setNativeResolutionHeight(int val) { nativeResolutionHeight = val; }

    public static int getCurrentRefreshRate() { return currentRefreshRate; }
    public static void setCurrentRefreshRate(int val) { currentRefreshRate = val; }

    public static boolean getHdrSupported() { return hdrSupported; }
    public static void setHdrSupported(boolean val) { hdrSupported = val; }

    public static float getDisplayDensity() { return displayDensity; }
    public static void setDisplayDensity(float val) { displayDensity = val; }
}
