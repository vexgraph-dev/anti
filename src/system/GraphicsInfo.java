package system;

import annotation.HotCode;

@HotCode
public class GraphicsInfo {
    private static long gpuName;
    private static int gpuVendorId;
    private static int gpuDeviceId;
    private static long primaryGraphicsApi;
    private static boolean unifiedMemoryEnabled;
    private static boolean computeShadersEnabled;
    private static boolean meshShadersEnabled;
    private static boolean hardwareRayTracingEnabled;
    private static boolean instancedIndexedEnabled;
    private static int maxTextureSize;
    private static float maxAnisotropyLevel;
    private static int msaaSampleCounts;
    private static boolean tessellationSupported;
    private static long vramTotal;
    private static long vramAvailable;
    private static int maxComputeWorkGroupInvocations;
    private static boolean discreteGpuEnabled;
    private static float hdrMaxLuminance;
    private static float hdrMinLuminance;
    private static long surfaceColorSpace;

    public static long getGpuName() { return gpuName; }
    public static void setGpuName(long val) { gpuName = val; }
    public static int getGpuVendorId() { return gpuVendorId; }
    public static void setGpuVendorId(int val) { gpuVendorId = val; }
    public static int getGpuDeviceId() { return gpuDeviceId; }
    public static void setGpuDeviceId(int val) { gpuDeviceId = val; }
    public static long getPrimaryGraphicsApi() { return primaryGraphicsApi; }
    public static void setPrimaryGraphicsApi(long val) { primaryGraphicsApi = val; }
    public static boolean getUnifiedMemoryEnabled() { return unifiedMemoryEnabled; }
    public static void setUnifiedMemoryEnabled(boolean val) { unifiedMemoryEnabled = val; }
    public static boolean getComputeShadersEnabled() { return computeShadersEnabled; }
    public static void setComputeShadersEnabled(boolean val) { computeShadersEnabled = val; }
    public static boolean getMeshShadersEnabled() { return meshShadersEnabled; }
    public static void setMeshShadersEnabled(boolean val) { meshShadersEnabled = val; }
    public static boolean getHardwareRayTracingEnabled() { return hardwareRayTracingEnabled; }
    public static void setHardwareRayTracingEnabled(boolean val) { hardwareRayTracingEnabled = val; }
    public static boolean getInstancedIndexedEnabled() { return instancedIndexedEnabled; }
    public static void setInstancedIndexedEnabled(boolean val) { instancedIndexedEnabled = val; }
    public static int getMaxTextureSize() { return maxTextureSize; }
    public static void setMaxTextureSize(int val) { maxTextureSize = val; }
    public static float getMaxAnisotropyLevel() { return maxAnisotropyLevel; }
    public static void setMaxAnisotropyLevel(float val) { maxAnisotropyLevel = val; }
    public static int getMsaaSampleCounts() { return msaaSampleCounts; }
    public static void setMsaaSampleCounts(int val) { msaaSampleCounts = val; }
    public static boolean getTessellationSupported() { return tessellationSupported; }
    public static void setTessellationSupported(boolean val) { tessellationSupported = val; }
    public static long getVramTotal() { return vramTotal; }
    public static void setVramTotal(long val) { vramTotal = val; }
    public static long getVramAvailable() { return vramAvailable; }
    public static void setVramAvailable(long val) { vramAvailable = val; }
    public static int getMaxComputeWorkGroupInvocations() { return maxComputeWorkGroupInvocations; }
    public static void setMaxComputeWorkGroupInvocations(int val) { maxComputeWorkGroupInvocations = val; }
    public static boolean getDiscreteGpuEnabled() { return discreteGpuEnabled; }
    public static void setDiscreteGpuEnabled(boolean val) { discreteGpuEnabled = val; }
    public static float getHdrMaxLuminance() { return hdrMaxLuminance; }
    public static void setHdrMaxLuminance(float val) { hdrMaxLuminance = val; }
    public static float getHdrMinLuminance() { return hdrMinLuminance; }
    public static void setHdrMinLuminance(float val) { hdrMinLuminance = val; }
    public static long getSurfaceColorSpace() { return surfaceColorSpace; }
    public static void setSurfaceColorSpace(long val) { surfaceColorSpace = val; }
}
