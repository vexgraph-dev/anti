package system;

import annotation.HotCode;

@HotCode
public class MemoryHeap {
    private static long totalSpace;
    private static boolean isDeviceLocal;

    public static long getTotalSpace() { return totalSpace; }
    public static void setTotalSpace(long val) { totalSpace = val; }
    public static boolean getIsDeviceLocal() { return isDeviceLocal; }
    public static void setIsDeviceLocal(boolean val) { isDeviceLocal = val; }
}
