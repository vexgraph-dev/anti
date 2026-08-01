package lang;

import system.*;
import primitive.string;
import java.lang.StringBuilder;

import java.io.BufferedReader;
import java.io.InputStreamReader;

public final class System {

    static {
        // Initialize Native Signal and Telemetry Crash Handler
        telemetry.CrashDumper.init();

        // base operating system and memory thingies
        String osName = java.lang.System.getProperty("os.name").toLowerCase();
        long osNamePtr = string.allocate(java.lang.System.getProperty("os.name"));
        long osArchPtr = string.allocate(java.lang.System.getProperty("os.arch"));
        long deviceModelPtr = string.allocate(isMobileOrHandheld() ? "Handheld/Mobile" : "Desktop");

        String rawCpu = java.lang.System.getenv("PROCESSOR_IDENTIFIER");
        int cores = Runtime.getRuntime().availableProcessors();
        int threads = cores * 2;

        long totalMemory = 0L;
        long freeMemory = 0L;

        // Pure FFM OSHI Telemetry Probe
        boolean oshiAvailable = false;
        try {
            oshi.ffm.SystemInfo oshiSys = new oshi.ffm.SystemInfo();
            oshi.hardware.HardwareAbstractionLayer hal = oshiSys.getHardware();
            oshi.hardware.CentralProcessor cpu = hal.getProcessor();
            oshi.hardware.GlobalMemory mem = hal.getMemory();

            if (cpu != null && cpu.getProcessorIdentifier() != null) {
                String fetchedCpu = cpu.getProcessorIdentifier().getName();
                if (fetchedCpu != null && !fetchedCpu.isBlank()) {
                    rawCpu = fetchedCpu.trim();
                }
                if (cpu.getPhysicalProcessorCount() > 0) {
                    cores = cpu.getPhysicalProcessorCount();
                }
                if (cpu.getLogicalProcessorCount() > 0) {
                    threads = cpu.getLogicalProcessorCount();
                }
            }
            if (mem != null) {
                totalMemory = mem.getTotal();
                freeMemory = mem.getAvailable();
            }
            oshiAvailable = true;
        } catch (Throwable ignored) {}

        if (!oshiAvailable || totalMemory == 0L) {
            try {
                java.lang.management.OperatingSystemMXBean osBean = java.lang.management.ManagementFactory.getOperatingSystemMXBean();
                java.lang.reflect.Method totalMemMethod = osBean.getClass().getMethod("getTotalPhysicalMemorySize");
                java.lang.reflect.Method freeMemMethod = osBean.getClass().getMethod("getFreePhysicalMemorySize");
                totalMemory = (long) totalMemMethod.invoke(osBean);
                freeMemory = (long) freeMemMethod.invoke(osBean);
            } catch (Throwable t) {
                totalMemory = Runtime.getRuntime().maxMemory();
                freeMemory = Runtime.getRuntime().freeMemory();
            }
        }

        long cpuBrandPtr = string.allocate(rawCpu != null ? rawCpu : java.lang.System.getProperty("os.arch"));

        long totalStorage = 0;
        long availableStorage = 0;
        try {
            java.io.File root = new java.io.File("/");
            totalStorage = root.getTotalSpace();
            availableStorage = root.getFreeSpace();
        } catch (Throwable ignored) {}

        // ==========================================
        // 2. DISPLAY TOPOLOGY SYSTEM (Probing deferred to SystemDiscovery)
        // ==========================================
        DisplayInfo.setMonitorCount(0);
        DisplayInfo.setMonitorsPointer(0L);
        DisplayInfo.setPrimaryMonitorPointer(0L);
        DisplayInfo.setMonitorResolutionWidth(0);
        DisplayInfo.setMonitorResolutionHeight(0);
        DisplayInfo.setNativeResolutionWidth(0);
        DisplayInfo.setNativeResolutionHeight(0);
        DisplayInfo.setCurrentRefreshRate(60);
        DisplayInfo.setHdrSupported(false);
        DisplayInfo.setDisplayDensity(1.0f);

        // ==========================================
        // 3. DYNAMIC GRAPHICS & RUNTIME TARGET PROBING
        // ==========================================
        String detectedGpuName = "Software Rasterizer";
        String primaryGraphicsApi;
        boolean unifiedMemoryArchitecture = false;
        long physicalVramTotal = 0L;

        // Try FFM OSHI GPU hardware probing first
        try {
            oshi.ffm.SystemInfo oshiSys = new oshi.ffm.SystemInfo();
            java.util.List<oshi.hardware.GraphicsCard> gpus = oshiSys.getHardware().getGraphicsCards();
            if (gpus != null && !gpus.isEmpty()) {
                oshi.hardware.GraphicsCard mainGpu = gpus.getFirst();
                if (mainGpu.getName() != null && !mainGpu.getName().isBlank()) {
                    detectedGpuName = mainGpu.getName().trim();
                }
                if (mainGpu.getVRam() > 0) {
                    physicalVramTotal = mainGpu.getVRam();
                }
            }
        } catch (Throwable ignored) {}

        boolean hasVulkanSdk = java.lang.System.getenv("VULKAN_SDK") != null || java.lang.System.getenv("VK_LAYER_PATH") != null;
        String vulkanApiVersion = "0.0.0";
        String vulkanDriverVersion = "None";
        boolean vkValidationLayers = hasVulkanSdk;

        boolean hasMetalRuntime = false;
        boolean isAppleSiliconGpu = false;
        int appleGpuFamilyTier = 0;
        String appleSiliconChipModel = "None";
        String metalVersionString = "0.0.0";

        boolean dynamicDx12Supported = false;
        int d3dFeatureLevel = 0;
        boolean agilitySdkPresent = false;

        if (osName.contains("mac")) {
            hasMetalRuntime = true;
            primaryGraphicsApi = "Metal";

            String arch = java.lang.System.getProperty("os.arch").toLowerCase();
            if (arch.contains("aarch64") || arch.contains("arm64")) {
                isAppleSiliconGpu = true;
                unifiedMemoryArchitecture = true;
                physicalVramTotal = totalMemory;
            }

            String hardwareProfile = executeNativeCommand("system_profiler", "SPHardwareDataType");
            for (String line : hardwareProfile.split("\n")) {
                if (line.contains("Chip:")) {
                    appleSiliconChipModel = line.split(":", 2)[1].trim();
                    detectedGpuName = appleSiliconChipModel;
                    if (appleSiliconChipModel.contains("M1")) {
                        appleGpuFamilyTier = 7;
                        metalVersionString = "Metal 3.0 Native";
                    } else if (appleSiliconChipModel.contains("M2")) {
                        appleGpuFamilyTier = 8;
                        metalVersionString = "Metal 3.0 Native";
                    } else if (appleSiliconChipModel.contains("A18 Pro") || appleSiliconChipModel.contains("M3") || appleSiliconChipModel.contains("M4")) {
                        appleGpuFamilyTier = 9;
                        metalVersionString = "Metal 3.1 Native (Apple Family 9)";
                    } else if (appleSiliconChipModel.contains("A19 Pro") || appleSiliconChipModel.contains("M5")) {
                        appleGpuFamilyTier = 10;
                        metalVersionString = "Metal 3.2 Native (Apple Family 10)";
                    } else {
                        appleGpuFamilyTier = 6;
                        metalVersionString = "Metal 2.4 Legacy";
                    }
                    break;
                }
            }
            if (metalVersionString.equals("0.0.0")) metalVersionString = "Metal 3.1 Native";

        } else if (osName.contains("win")) {
            dynamicDx12Supported = true;
            primaryGraphicsApi = "DirectX 12";

            String wmicOutput = executeNativeCommand("wmic", "path", "win32_VideoController", "get", "Name,AdapterRAM", "/value");
            for (String line : wmicOutput.split("\n")) {
                line = line.trim();
                if (line.startsWith("Name=")) {
                    detectedGpuName = line.split("=", 2)[1].trim();
                } else if (line.startsWith("AdapterRAM=")) {
                    try {
                        physicalVramTotal = Long.parseLong(line.split("=", 2)[1].trim());
                    } catch (Exception ignored) {}
                }
            }
            String lowerGpu = detectedGpuName.toLowerCase();
            if (lowerGpu.contains("intel") || lowerGpu.contains("amd radeon graphics") || lowerGpu.contains("vega")) {
                unifiedMemoryArchitecture = true;
                if (physicalVramTotal == 0) physicalVramTotal = totalMemory / 4;
            }
            d3dFeatureLevel = 0xC200;
        } else {
            primaryGraphicsApi = "OpenGL Fallback";
        }

        // Set Vulkan Static State
        VulkanInstanceInfo.setVulkanSdkInstalled(hasVulkanSdk);
        VulkanInstanceInfo.setVulkanVersion(string.allocate(vulkanApiVersion));
        VulkanInstanceInfo.setApiDriversVersion(string.allocate(vulkanDriverVersion));
        VulkanInstanceInfo.setVulkanTotalMemoryBudget(physicalVramTotal);
        VulkanInstanceInfo.setVulkanCurrentMemoryUsage(0L);
        VulkanInstanceInfo.setVulkanMemoryHeaps(new MemoryHeap[0]);
        VulkanInstanceInfo.setValidationLayersEnabled(vkValidationLayers);

        // Set Metal Static State
        MetalInstanceInfo.setMetalRuntimeAvailable(hasMetalRuntime);
        MetalInstanceInfo.setIsAppleSilicon(isAppleSiliconGpu);
        MetalInstanceInfo.setAppleGpuFamily(appleGpuFamilyTier);
        MetalInstanceInfo.setAppleChipModel(string.allocate(appleSiliconChipModel));
        MetalInstanceInfo.setMetalVersion(string.allocate(metalVersionString));
        MetalInstanceInfo.setArgumentBuffersSupported(isAppleSiliconGpu);
        MetalInstanceInfo.setRayTracingTierSupported(appleGpuFamilyTier >= 9);

        // Set DirectX Static State
        DirectXInstanceInfo.setDirectX12Supported(dynamicDx12Supported);
        DirectXInstanceInfo.setFeatureLevel(d3dFeatureLevel);
        DirectXInstanceInfo.setAgilitySdkPresent(agilitySdkPresent);
        DirectXInstanceInfo.setShaderModel6_xSupported(dynamicDx12Supported);

        // Set Graphics Static State
        GraphicsInfo.setGpuName(string.allocate(detectedGpuName));
        GraphicsInfo.setGpuVendorId(0);
        GraphicsInfo.setGpuDeviceId(0);
        GraphicsInfo.setPrimaryGraphicsApi(string.allocate(primaryGraphicsApi));
        GraphicsInfo.setUnifiedMemoryEnabled(unifiedMemoryArchitecture);
        GraphicsInfo.setComputeShadersEnabled(true);
        GraphicsInfo.setMeshShadersEnabled(appleGpuFamilyTier >= 9 || osName.contains("win"));
        GraphicsInfo.setHardwareRayTracingEnabled(appleGpuFamilyTier >= 9);
        GraphicsInfo.setInstancedIndexedEnabled(true);
        GraphicsInfo.setMaxTextureSize(4096);
        GraphicsInfo.setMaxAnisotropyLevel(1.0f);
        GraphicsInfo.setMsaaSampleCounts(1);
        GraphicsInfo.setTessellationSupported(false);
        GraphicsInfo.setVramTotal(physicalVramTotal);
        GraphicsInfo.setVramAvailable(physicalVramTotal);
        GraphicsInfo.setMaxComputeWorkGroupInvocations(1024);
        GraphicsInfo.setDiscreteGpuEnabled(!unifiedMemoryArchitecture);
        GraphicsInfo.setHdrMaxLuminance(0.0f);
        GraphicsInfo.setHdrMinLuminance(0.0f);
        GraphicsInfo.setSurfaceColorSpace(string.allocate("sRGB"));

        // Set Audio Static State
        AudioInfo.setAudioDeviceName(string.allocate("Primary Audio Driver"));
        AudioInfo.setAvailableAudioDevices(new AudioDevice[0]);
        AudioInfo.setSampleRates(new int[]{48000});
        AudioInfo.setCurrentSampleRate(48000);
        AudioInfo.setAudioChannelCount(2);
        AudioInfo.setSpatialAudioSupported(false);
        AudioInfo.setMaxAudioSources(32);
        AudioInfo.setAudioOutputLatency(10.0f);

        // Set Video Static State
        VideoInfo.setSupportedVideoCodecs(new long[]{string.allocate("H264")});
        VideoInfo.setHardwareDecodingSupported(true);
        VideoInfo.setHardwareEncodingSupported(false);
        VideoInfo.setHdrVideoPlaybackSupported(false);
        VideoInfo.setVideoFrameRateMax(60);

        // Set Performance Static State
        PerformanceInfo.setGpuTimestampPeriod(1.0f);
        PerformanceInfo.setVariableRateShadingSupported(false);
        PerformanceInfo.setVrsSurfaceProperties(string.allocate("Not Supported"));
        PerformanceInfo.setDynamicResolutionScaleSupported(false);
        PerformanceInfo.setThermalMitigationLevel(0);
        PerformanceInfo.setPowerSavingModeActive(false);

        String batteryStatusStr = "AC Powered";
        float batteryLevelVal = 1.0f;
        try {
            oshi.ffm.SystemInfo oshiSys = new oshi.ffm.SystemInfo();
            java.util.List<oshi.hardware.PowerSource> powerSources = oshiSys.getHardware().getPowerSources();
            if (powerSources != null && !powerSources.isEmpty()) {
                oshi.hardware.PowerSource ps = powerSources.getFirst();
                double capacity = ps.getRemainingCapacityPercent();
                batteryLevelVal = (float) (capacity > 0 ? capacity : 1.0);
                if (ps.isPowerOnLine()) {
                    batteryStatusStr = ps.isCharging() ? "AC Powered (Charging)" : "AC Powered (Full)";
                } else {
                    batteryStatusStr = "Battery Power (Discharging)";
                }
            }
        } catch (Throwable ignored) {}

        // Set Hardware Static State
        HardwareInfo.setOperatingSystem(osNamePtr);
        HardwareInfo.setSystemArchitecture(osArchPtr);
        HardwareInfo.setDeviceModel(deviceModelPtr);
        HardwareInfo.setCpuBrand(cpuBrandPtr);
        HardwareInfo.setCpuCoreCount(cores);
        HardwareInfo.setCpuThreadCount(threads);
        HardwareInfo.setStorageTotalSpace(totalStorage);
        HardwareInfo.setStorageAvailableSpace(availableStorage);
        HardwareInfo.setRamTotal(totalMemory);
        HardwareInfo.setRamAvailable(freeMemory);
        HardwareInfo.setJavaHeapSize(Runtime.getRuntime().totalMemory());
        HardwareInfo.setJavaHeapMax(Runtime.getRuntime().maxMemory());
        HardwareInfo.setBatteryStatus(string.allocate(batteryStatusStr));
        HardwareInfo.setBatteryLevel(batteryLevelVal);
    }

    private static String executeNativeCommand(String... command) {
        StringBuilder output = new StringBuilder();
        try {
            Process process = new ProcessBuilder(command).redirectErrorStream(true).start();
            try (BufferedReader reader = new BufferedReader(new InputStreamReader(process.getInputStream()))) {
                String line;
                while ((line = reader.readLine()) != null) {
                    output.append(line).append("\n");
                }
            }
            process.waitFor();
            return output.toString();
        } catch (Exception e) {
            return ""; 
        }
    }

    private System() {}

    // ==========================================
    // CONVENIENCE DEEP GETTERS
    // ==========================================
    public static float getGpuTimestamp() {
        return PerformanceInfo.getGpuTimestampPeriod();
    }

    public static String getGpuName() {
        return string.get(GraphicsInfo.getGpuName());
    }

    public static long getRamTotal() {
        return HardwareInfo.getRamTotal();
    }

    public static int getCpuCoreCount() {
        return HardwareInfo.getCpuCoreCount();
    }

    public static int getMonitorCount() {
        return DisplayInfo.getMonitorCount();
    }

    private static boolean isMobileOrHandheld() {
        String os = java.lang.System.getProperty("os.name").toLowerCase();
        return os.contains("android") || os.contains("ios");
    }

    // ==========================================
    // THE "GOD" SPEC SPECIFICATION STRINGIFIER
    // ==========================================
    public static String getSystem() {
        StringBuilder sb = new StringBuilder();
        sb.append("==================================================\n");
        sb.append("         CORE ENGINE HARDWARE PROFILE             \n");
        sb.append("==================================================\n\n");

        sb.append("[HARDWARE SPECIFICATIONS]\n");
        sb.append("  - OS: ").append(string.get(HardwareInfo.getOperatingSystem())).append("\n");
        sb.append("  - Arch: ").append(string.get(HardwareInfo.getSystemArchitecture())).append("\n");
        sb.append("  - Model Class: ").append(string.get(HardwareInfo.getDeviceModel())).append("\n");
        sb.append("  - CPU: ").append(string.get(HardwareInfo.getCpuBrand())).append("\n");
        sb.append("  - Cores / Threads: ").append(HardwareInfo.getCpuCoreCount()).append(" / ").append(HardwareInfo.getCpuThreadCount()).append("\n");
        sb.append("  - RAM Total: ").append(HardwareInfo.getRamTotal() / 1024 / 1024).append(" MB\n");
        sb.append("  - RAM Available: ").append(HardwareInfo.getRamAvailable() / 1024 / 1024).append(" MB\n");
        sb.append("  - Heap Allocated / Max: ").append(HardwareInfo.getJavaHeapSize() / 1024 / 1024).append(" MB / ").append(HardwareInfo.getJavaHeapMax() / 1024 / 1024).append(" MB\n");
        sb.append("  - Storage Total / Available: ").append(HardwareInfo.getStorageTotalSpace() / 1024 / 1024 / 1024).append(" GB / ").append(HardwareInfo.getStorageAvailableSpace() / 1024 / 1024 / 1024).append(" GB\n");
        sb.append("  - Battery Level: ").append(HardwareInfo.getBatteryLevel() * 100).append("% (").append(string.get(HardwareInfo.getBatteryStatus())).append(")\n\n");

        sb.append("[DISPLAY TOPOLOGY]\n");
        sb.append("  - Detected Monitors: ").append(DisplayInfo.getMonitorCount()).append("\n");
        sb.append("\n");

        sb.append("[GRAPHICS DEVICE CAPABILITIES]\n");
        sb.append("  - GPU: ").append(string.get(GraphicsInfo.getGpuName())).append("\n");
        sb.append("  - Vendor / Device ID: 0x").append(Integer.toHexString(GraphicsInfo.getGpuVendorId())).append(" / 0x").append(Integer.toHexString(GraphicsInfo.getGpuDeviceId())).append("\n");
        sb.append("  - Active System API: ").append(string.get(GraphicsInfo.getPrimaryGraphicsApi())).append("\n");
        sb.append("  - Unified Memory Architecture (UMA): ").append(GraphicsInfo.getUnifiedMemoryEnabled() ? "Yes (Shared Pool)" : "No (Discrete VRAM Heap)").append("\n");
        sb.append("  - Max Texture Size: ").append(GraphicsInfo.getMaxTextureSize()).append("\n");
        sb.append("  - Hardware Ray Tracing: ").append(GraphicsInfo.getHardwareRayTracingEnabled() ? "Yes" : "No").append("\n");
        sb.append("  - Mesh Shaders: ").append(GraphicsInfo.getMeshShadersEnabled() ? "Yes" : "No").append("\n");
        sb.append("  - Compute Shaders: ").append(GraphicsInfo.getComputeShadersEnabled() ? "Yes" : "No").append("\n");
        sb.append("  - Physical VRAM Total / Available: ").append(GraphicsInfo.getVramTotal() / 1024 / 1024).append(" MB / ").append(GraphicsInfo.getVramAvailable() / 1024 / 1024).append(" MB\n\n");

        sb.append("  [Vulkan Sub-System Instance]\n");
        sb.append("    * Vulkan SDK Installed: ").append(VulkanInstanceInfo.getVulkanSdkInstalled() ? "Yes" : "No").append("\n");
        sb.append("    * Runtime API Version: ").append(string.get(VulkanInstanceInfo.getVulkanVersion())).append("\n");
        sb.append("    * Device Driver Build: ").append(string.get(VulkanInstanceInfo.getApiDriversVersion())).append("\n");
        sb.append("    * OS Memory Budget Allocation: ").append(VulkanInstanceInfo.getVulkanTotalMemoryBudget() / 1024 / 1024).append(" MB\n");
        sb.append("    * Sub-allocator Active Usage: ").append(VulkanInstanceInfo.getVulkanCurrentMemoryUsage() / 1024 / 1024).append(" MB\n");
        sb.append("    * Validation Status Layers: ").append(VulkanInstanceInfo.getValidationLayersEnabled() ? "Active Debug Profile" : "Disabled (Release)").append("\n\n");

        sb.append("  [Apple Metal Sub-System Instance]\n");
        sb.append("    * Metal Framework Present: ").append(MetalInstanceInfo.getMetalRuntimeAvailable() ? "Yes" : "No").append("\n");
        sb.append("    * Architecture Class: ").append(MetalInstanceInfo.getIsAppleSilicon() ? "Apple Silicon SoC" : "Legacy Intel Mac Hardware").append("\n");
        sb.append("    * Processor Chip Silicon Model: ").append(string.get(MetalInstanceInfo.getAppleChipModel())).append("\n");
        sb.append("    * Hardware Feature Set: Apple GPU Family ").append(MetalInstanceInfo.getAppleGpuFamily()).append("\n");
        sb.append("    * Metal Dynamic API Version: ").append(string.get(MetalInstanceInfo.getMetalVersion())).append("\n");
        sb.append("    * Argument Binding Layouts: ").append(MetalInstanceInfo.getArgumentBuffersSupported() ? "Tier 2/Supported" : "Unsupported").append("\n");
        sb.append("    * Ray Tracing Execution Tier: ").append(MetalInstanceInfo.getRayTracingTierSupported() ? "Hardware Accelerated Cores" : "None/Compute Polling").append("\n\n");

        sb.append("  [DirectX Sub-System Instance]\n");
        sb.append("    * DX12 API Supported: ").append(DirectXInstanceInfo.getDirectX12Supported() ? "Yes" : "No").append("\n");
        sb.append("    * Direct3D Core Feature Level: 0x").append(Integer.toHexString(DirectXInstanceInfo.getFeatureLevel())).append("\n");
        sb.append("    * Runtime Engine Agility SDK: ").append(DirectXInstanceInfo.getAgilitySdkPresent() ? "Linked / Loaded" : "Missing / Stale").append("\n");
        sb.append("    * High Tier Shader Program compilation: ").append(DirectXInstanceInfo.getShaderModel6_xSupported() ? "Shader Model 6.x Early Native" : "Legacy Direct3D Layer").append("\n\n");

        sb.append("[AUDIO LATENCY PIPELINE]\n");
        sb.append("  - Device Target: ").append(string.get(AudioInfo.getAudioDeviceName())).append("\n");
        sb.append("  - Channels / Native Rate: ").append(AudioInfo.getAudioChannelCount()).append(" / ").append(AudioInfo.getCurrentSampleRate()).append("Hz\n");
        sb.append("  - Output Hardware Latency: ").append(AudioInfo.getAudioOutputLatency()).append(" ms\n");
        sb.append("  - Spatial Audio: ").append(AudioInfo.getSpatialAudioSupported() ? "Yes" : "No").append("\n\n");

        sb.append("[REAL-TIME EXECUTION METRICS]\n");
        sb.append("  - GPU Timestamp Period: ").append(PerformanceInfo.getGpuTimestampPeriod()).append(" ns/tick\n");
        sb.append("  - Variable Rate Shading: ").append(PerformanceInfo.getVariableRateShadingSupported() ? "Yes" : "No").append("\n");
        sb.append("  - Power Saving Mode Active: ").append(PerformanceInfo.getPowerSavingModeActive() ? "Yes" : "No").append("\n");
        sb.append("  - OS Thermal Throttle State: Level ").append(PerformanceInfo.getThermalMitigationLevel()).append("\n");

        sb.append("==================================================\n");
        return sb.toString();
    }
}