package system;

import annotation.HotCode;

@HotCode
public class DisplayMonitor {
    private long id;
    private long name;
    private Resolution currentResolution;
    private Resolution nativeResolution;
    private int[] supportedRefreshRates;
    private int currentRefreshRate;
    private boolean hdrSupported;
    private float dpi;

    public long getId() { return id; }
    public void setId(long val) { id = val; }
    public long getName() { return name; }
    public void setName(long val) { name = val; }
    public Resolution getCurrentResolution() { return currentResolution; }
    public void setCurrentResolution(Resolution val) { currentResolution = val; }
    public Resolution getNativeResolution() { return nativeResolution; }
    public void setNativeResolution(Resolution val) { nativeResolution = val; }
    public int[] getSupportedRefreshRates() { return supportedRefreshRates; }
    public void setSupportedRefreshRates(int[] val) { supportedRefreshRates = val; }
    public int getCurrentRefreshRate() { return currentRefreshRate; }
    public void setCurrentRefreshRate(int val) { currentRefreshRate = val; }
    public boolean getHdrSupported() { return hdrSupported; }
    public void setHdrSupported(boolean val) { hdrSupported = val; }
    public float getDpi() { return dpi; }
    public void setDpi(float val) { dpi = val; }
}
