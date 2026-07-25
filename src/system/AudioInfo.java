package system;

import annotation.HotCode;

@HotCode
public class AudioInfo {
    private static long audioDeviceName;
    private static AudioDevice[] availableAudioDevices;
    private static int[] sampleRates;
    private static int currentSampleRate;
    private static int audioChannelCount;
    private static boolean spatialAudioSupported;
    private static int maxAudioSources;
    private static float audioOutputLatency;

    public static long getAudioDeviceName() { return audioDeviceName; }
    public static void setAudioDeviceName(long val) { audioDeviceName = val; }
    public static AudioDevice[] getAvailableAudioDevices() { return availableAudioDevices; }
    public static void setAvailableAudioDevices(AudioDevice[] val) { availableAudioDevices = val; }
    public static int[] getSampleRates() { return sampleRates; }
    public static void setSampleRates(int[] val) { sampleRates = val; }
    public static int getCurrentSampleRate() { return currentSampleRate; }
    public static void setCurrentSampleRate(int val) { currentSampleRate = val; }
    public static int getAudioChannelCount() { return audioChannelCount; }
    public static void setAudioChannelCount(int val) { audioChannelCount = val; }
    public static boolean getSpatialAudioSupported() { return spatialAudioSupported; }
    public static void setSpatialAudioSupported(boolean val) { spatialAudioSupported = val; }
    public static int getMaxAudioSources() { return maxAudioSources; }
    public static void setMaxAudioSources(int val) { maxAudioSources = val; }
    public static float getAudioOutputLatency() { return audioOutputLatency; }
    public static void setAudioOutputLatency(float val) { audioOutputLatency = val; }
}
