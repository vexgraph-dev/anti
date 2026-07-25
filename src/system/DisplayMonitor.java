package system;

import annotation.HotCode;

@HotCode
public class DisplayMonitor {
    private static long id;
    private static long name;
    private static Resolution currentResolution;
    private static Resolution nativeResolution;
    private static int[] supportedRefreshRates;
    private static int currentRefreshRate;
    private static boolean hdrSupported;
    private static float dpi;

    public static long getId() { return id; }
    public static void setId(long val) { id = val; }
    public static long getName() { return name; }
    public static void setName(long val) { name = val; }
    public static Resolution getCurrentResolution() { return currentResolution; }
    public static void setCurrentResolution(Resolution val) { currentResolution = val; }
    public static Resolution getNativeResolution() { return nativeResolution; }
    public static void setNativeResolution(Resolution val) { nativeResolution = val; }
    public static int[] getSupportedRefreshRates() { return supportedRefreshRates; }
    public static void setSupportedRefreshRates(int[] val) { supportedRefreshRates = val; }
    public static int getCurrentRefreshRate() { return currentRefreshRate; }
    public static void setCurrentRefreshRate(int val) { currentRefreshRate = val; }
    public static boolean getHdrSupported() { return hdrSupported; }
    public static void setHdrSupported(boolean val) { hdrSupported = val; }
    public static float getDpi() { return dpi; }
    public static void setDpi(float val) { dpi = val; }
}
