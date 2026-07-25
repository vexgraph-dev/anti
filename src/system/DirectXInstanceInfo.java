package system;

import annotation.HotCode;

@HotCode
public class DirectXInstanceInfo {
    private static boolean directX12Supported;
    private static int featureLevel;
    private static boolean agilitySdkPresent;
    private static boolean shaderModel6_xSupported;

    public static boolean getDirectX12Supported() { return directX12Supported; }
    public static void setDirectX12Supported(boolean val) { directX12Supported = val; }
    public static int getFeatureLevel() { return featureLevel; }
    public static void setFeatureLevel(int val) { featureLevel = val; }
    public static boolean getAgilitySdkPresent() { return agilitySdkPresent; }
    public static void setAgilitySdkPresent(boolean val) { agilitySdkPresent = val; }
    public static boolean getShaderModel6_xSupported() { return shaderModel6_xSupported; }
    public static void setShaderModel6_xSupported(boolean val) { shaderModel6_xSupported = val; }
}
