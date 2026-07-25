package system;

import annotation.HotCode;

@HotCode
public class VideoInfo {
    private static long[] supportedVideoCodecs;
    private static boolean hardwareDecodingSupported;
    private static boolean hardwareEncodingSupported;
    private static Resolution maxVideoResolution;
    private static boolean hdrVideoPlaybackSupported;
    private static int videoFrameRateMax;

    public static long[] getSupportedVideoCodecs() { return supportedVideoCodecs; }
    public static void setSupportedVideoCodecs(long[] val) { supportedVideoCodecs = val; }
    public static boolean getHardwareDecodingSupported() { return hardwareDecodingSupported; }
    public static void setHardwareDecodingSupported(boolean val) { hardwareDecodingSupported = val; }
    public static boolean getHardwareEncodingSupported() { return hardwareEncodingSupported; }
    public static void setHardwareEncodingSupported(boolean val) { hardwareEncodingSupported = val; }
    public static Resolution getMaxVideoResolution() { return maxVideoResolution; }
    public static void setMaxVideoResolution(Resolution val) { maxVideoResolution = val; }
    public static boolean getHdrVideoPlaybackSupported() { return hdrVideoPlaybackSupported; }
    public static void setHdrVideoPlaybackSupported(boolean val) { hdrVideoPlaybackSupported = val; }
    public static int getVideoFrameRateMax() { return videoFrameRateMax; }
    public static void setVideoFrameRateMax(int val) { videoFrameRateMax = val; }
}
