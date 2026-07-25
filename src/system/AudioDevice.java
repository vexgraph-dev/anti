package system;

import annotation.HotCode;

@HotCode
public class AudioDevice {
    private static long name;
    private static int[] supportedSampleRates;

    public static long getName() { return name; }
    public static void setName(long val) { name = val; }
    public static int[] getSupportedSampleRates() { return supportedSampleRates; }
    public static void setSupportedSampleRates(int[] val) { supportedSampleRates = val; }
}
