package lang;

import primitive.string;

import java.io.BufferedReader;
import java.io.InputStreamReader;

public final class System {

    private static HardwareInfo hardware;
    private static DisplayInfo display;
    private static GraphicsInfo graphics;
    private static AudioInfo audio;
    private static VideoInfo video;
    private static PerformanceInfo performance;

    static {

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
        // 2. DISPLAY TOPOLOGY SYSTEM
        // ==========================================
        DisplayMonitor[] monitors = new DisplayMonitor[0];
        DisplayMonitor primary = null;
        Resolution res = new Resolution(1920, 1080);
        try {
            if (!java.awt.GraphicsEnvironment.isHeadless()) {
                java.awt.GraphicsDevice[] devices = java.awt.GraphicsEnvironment.getLocalGraphicsEnvironment().getScreenDevices();
                monitors = new DisplayMonitor[devices.length];
                for (int i = 0; i < devices.length; i++) {
                    java.awt.GraphicsDevice gd = devices[i];
                    java.awt.DisplayMode dm = gd.getDisplayMode();
                    Resolution monitorRes = new Resolution(dm.getWidth(), dm.getHeight());
                    monitors[i] = new DisplayMonitor(
                            string.allocate(gd.getIDstring()),
                            string.allocate("Generic Monitor " + i),
                            monitorRes, monitorRes, new int[]{dm.getRefreshRate()},
                            dm.getRefreshRate(), false, 96.0f
                    );
                }
                if (monitors.length > 0) {
                    primary = monitors[0];
                    res = primary.currentResolution();
                }
            }
        } catch (Throwable ignored) {}

        display = new DisplayInfo(
                monitors.length, monitors, primary, res, res,
                primary != null ? primary.supportedRefreshRates() : new int[]{60},
                primary != null ? primary.currentRefreshRate() : 60, false, 1.0f
        );

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

        // Vulkan Context Probing Defaults
        boolean hasVulkanSdk = java.lang.System.getenv("VULKAN_SDK") != null || java.lang.System.getenv("VK_LAYER_PATH") != null;
        if (!hasVulkanSdk) {
            String homeDir = java.lang.System.getProperty("user.home");
            hasVulkanSdk = new java.io.File("/usr/local/include/vulkan").exists() ||
                           new java.io.File("/opt/homebrew/include/vulkan").exists() ||
                           new java.io.File("/vulkan-sdk").exists() ||
                           (homeDir != null && new java.io.File(homeDir, "VulkanSDK").exists());
        }
        String vulkanApiVersion = "0.0.0";
        String vulkanDriverVersion = "None";
        boolean vkValidationLayers = hasVulkanSdk;

        // Metal Context Probing Defaults
        boolean hasMetalRuntime = false;
        boolean isAppleSiliconGpu = false;
        int appleGpuFamilyTier = 0;
        String appleSiliconChipModel = "None";
        String metalVersionString = "0.0.0";

        // DirectX Context Probing Defaults
        boolean dynamicDx12Supported = false;
        int d3dFeatureLevel = 0;
        boolean agilitySdkPresent = false;

        // Execute Profiler Commands depending on Host Kernel
        if (osName.contains("mac")) {
            hasMetalRuntime = true;
            primaryGraphicsApi = "Metal";

            // Check for explicit Apple Silicon Arm64 instruction pipeline execution
            String arch = java.lang.System.getProperty("os.arch").toLowerCase();
            if (arch.contains("aarch64") || arch.contains("arm64")) {
                isAppleSiliconGpu = true;
                unifiedMemoryArchitecture = true;
                physicalVramTotal = totalMemory;
            }

            // Parse detailed System Profiler structures
            String hardwareProfile = executeNativeCommand("system_profiler", "SPHardwareDataType");
            for (String line : hardwareProfile.split("\n")) {
                if (line.contains("Chip:")) {
                    appleSiliconChipModel = line.split(":", 2)[1].trim();
                    detectedGpuName = appleSiliconChipModel;

                    // Extrapolate hardware generation families from model identities from the apple metal documentation book
                    if (appleSiliconChipModel.contains("M1")) {
                        appleGpuFamilyTier = 7;
                        metalVersionString = "Metal 3.0 Native";
                    } else if (appleSiliconChipModel.contains("M2")) {
                        appleGpuFamilyTier = 8;
                        metalVersionString = "Metal 3.0 Native";
                    } else if (appleSiliconChipModel.contains("A18 Pro") /* macbook neo */ || appleSiliconChipModel.contains("M3") || appleSiliconChipModel.contains("M4")) {
                        appleGpuFamilyTier = 9;
                        metalVersionString = "Metal 3.1 Native (Apple Family 9)";
                    } else if (appleSiliconChipModel.contains("A19 Pro") /* some later thingies */ || appleSiliconChipModel.contains("M5")) {
                        appleGpuFamilyTier = 10;
                        metalVersionString = "Metal 3.2 Native (Apple Family 10)";
                    } else {
                        appleGpuFamilyTier = 6; // defaults to intel/old graphics chips used
                        metalVersionString = "Metal 2.4 Legacy";
                    }
                    break;
                }
            }
            if (metalVersionString.equals("0.0.0")) {
                metalVersionString = "Metal 3.1 Native";
            }

        } else if (osName.contains("win")) {
            dynamicDx12Supported = true;
            primaryGraphicsApi = "DirectX 12";

            // Interrogate Windows Management Instrumentation for Physical Video hardware
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

            // Detect Windows UMA states (Integrated graphics/APUs sharing memory pools)
            String lowerGpu = detectedGpuName.toLowerCase();
            if (lowerGpu.contains("intel") || lowerGpu.contains("amd radeon graphics") || lowerGpu.contains("vega")) {
                unifiedMemoryArchitecture = true;
                if (physicalVramTotal == 0) physicalVramTotal = totalMemory / 4; // Typical safe OS lock-allocation
            }
            d3dFeatureLevel = 0xC200; // Default mapping D3D_FEATURE_LEVEL_12_2

        } else {
            // Linux kernel fallback
            String lspci = executeNativeCommand("sh", "-c", "lspci | grep -i vga");
            if (lspci.contains(":")) {
                detectedGpuName = lspci.split(":", 2)[1].trim();
            }
            primaryGraphicsApi = "OpenGL Fallback";
        }

        // --- Global Vulkan Command Line Interrogation Tool Probe ---
        String vulkanSummary = executeNativeCommand("vulkaninfo", "--summary");
        if (vulkanSummary.contains("Vulkan Instance Version")) {
            if (osName.contains("mac")) primaryGraphicsApi = "Vulkan (MoltenVK)";
            else primaryGraphicsApi = "Vulkan Native";

            for (String line : vulkanSummary.split("\n")) {
                if (line.contains("Vulkan Instance Version") || line.contains("API Version:")) {
                    vulkanApiVersion = line.split(":", 2)[1].trim();
                    break;
                }
            }
        }

        // instantiation of the properties
        // Assemble Final Engine Structures
        VulkanInstanceInfo vulkanInstance = new VulkanInstanceInfo(
                hasVulkanSdk,
                string.allocate(vulkanApiVersion),
                string.allocate(vulkanDriverVersion),
                physicalVramTotal, // Estimated fallback allocations
                0L,
                new MemoryHeap[0],
                vkValidationLayers
        );

        MetalInstanceInfo metalInstance = new MetalInstanceInfo(
                hasMetalRuntime,
                isAppleSiliconGpu,
                appleGpuFamilyTier,
                string.allocate(appleSiliconChipModel),
                string.allocate(metalVersionString),
                isAppleSiliconGpu, // Argument buffers are standard on modern Apple Silicon
                appleGpuFamilyTier >= 9 // HW Raytracing arrived with Family 9 (M3 series)
        );

        DirectXInstanceInfo directInstance = new DirectXInstanceInfo(
                dynamicDx12Supported,
                d3dFeatureLevel,
                agilitySdkPresent,
                dynamicDx12Supported
        );

        graphics = new GraphicsInfo(
                string.allocate(detectedGpuName),
                0, 0,
                string.allocate(primaryGraphicsApi),
                unifiedMemoryArchitecture,
                true, // Compute active globally
                appleGpuFamilyTier >= 9 || osName.contains("win"), // Mesh Shading validation
                appleGpuFamilyTier >= 9, // Native hardware ray tracing tracking
                true,
                4096, 1.0f, 1, false,
                physicalVramTotal,
                physicalVramTotal,
                1024,
                !unifiedMemoryArchitecture,
                0.0f, 0.0f,
                string.allocate("sRGB"),
                vulkanInstance,
                metalInstance,
                directInstance
        );

        audio = new AudioInfo(
                string.allocate("Primary Audio Driver"),
                new AudioDevice[0],
                new int[]{48000},
                48000,
                2,
                false,
                32,
                10.0f);
        video = new VideoInfo(
                new long[]{string.allocate("H264")},
                true,
                false,
                new Resolution(3840, 2160),
                false, 60);
        performance = new PerformanceInfo(
                1.0f,
                false,
                string.allocate("Not Supported"),
                false,
                0,
                false
        );

        // Real-Time Power & Battery Telemetry Probe
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

        hardware = new HardwareInfo(
                osNamePtr,
                osArchPtr,
                deviceModelPtr,
                cpuBrandPtr,
                cores,
                threads,
                totalStorage,
                availableStorage,
                totalMemory,
                freeMemory,
                Runtime.getRuntime().totalMemory(),
                Runtime.getRuntime().maxMemory(),
                string.allocate(batteryStatusStr),
                batteryLevelVal
        );
    }

    // ==========================================
    // NATIVE PIPELINE SUBPROCESS RUNNER
    // ==========================================
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
            return ""; // Soft safety landing if command is absent on the execution node
        }
    }

    private System() {}

    // ==========================================
    // CORE STATIC GETTERS
    // ==========================================
    public static HardwareInfo getHardware() { return hardware; }
    public static DisplayInfo getDisplay() { return display; }
    public static GraphicsInfo getGraphics() { return graphics; }
    public static AudioInfo getAudio() { return audio; }
    public static VideoInfo getVideo() { return video; }
    public static PerformanceInfo getPerformance() { return performance; }

    // ==========================================
    // CONVENIENCE DEEP GETTERS
    // ==========================================
    public static float getGpuTimestamp() {
        return performance != null ? performance.gpuTimestampPeriod() : 0.0f;
    }

    public static String getGpuName() {
        return graphics != null ? string.get(graphics.gpuName()) : "Unknown GPU";
    }

    public static long getRamTotal() {
        return hardware != null ? hardware.ramTotal() : 0L;
    }

    public static int getCpuCoreCount() {
        return hardware != null ? hardware.cpuCoreCount() : 1;
    }

    public static int getMonitorCount() {
        return display != null ? display.monitorCount() : 0;
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

        if (hardware != null) {
            sb.append("[HARDWARE SPECIFICATIONS]\n");
            sb.append("  - OS: ").append(string.get(hardware.operatingSystem())).append("\n");
            sb.append("  - Arch: ").append(string.get(hardware.systemArchitecture())).append("\n");
            sb.append("  - Model Class: ").append(string.get(hardware.deviceModel())).append("\n");
            sb.append("  - CPU: ").append(string.get(hardware.cpuBrand())).append("\n");
            sb.append("  - Cores / Threads: ").append(hardware.cpuCoreCount()).append(" / ").append(hardware.cpuThreadCount()).append("\n");
            sb.append("  - RAM Total: ").append(hardware.ramTotal() / 1024 / 1024).append(" MB\n");
            sb.append("  - RAM Available: ").append(hardware.ramAvailable() / 1024 / 1024).append(" MB\n");
            sb.append("  - Heap Allocated / Max: ").append(hardware.javaHeapSize() / 1024 / 1024).append(" MB / ").append(hardware.javaHeapMax() / 1024 / 1024).append(" MB\n");
            sb.append("  - Storage Total / Available: ").append(hardware.storageTotalSpace() / 1024 / 1024 / 1024).append(" GB / ").append(hardware.storageAvailableSpace() / 1024 / 1024 / 1024).append(" GB\n");
            sb.append("  - Battery Level: ").append(hardware.batteryLevel() * 100).append("% (").append(string.get(hardware.batteryStatus())).append(")\n\n");
        }

        if (display != null) {
            sb.append("[DISPLAY TOPOLOGY]\n");
            sb.append("  - Detected Monitors: ").append(display.monitorCount()).append("\n");
            if (display.monitors() != null) {
                for (int i = 0; i < display.monitors().length; i++) {
                    DisplayMonitor m = display.monitors()[i];
                    sb.append("    * Monitor [").append(i).append("]: ").append(string.get(m.name()))
                            .append(" (ID: ").append(string.get(m.id())).append(")\n");
                    sb.append("      Res (Current/Native): ").append(m.currentResolution().width()).append("x").append(m.currentResolution().height())
                            .append(" / ").append(m.nativeResolution().width()).append("x").append(m.nativeResolution().height()).append("\n");
                    sb.append("      Refresh Rate: ").append(m.currentRefreshRate()).append("Hz\n");
                    sb.append("      HDR / DPI: ").append(m.hdrSupported() ? "Yes" : "No").append(" / ").append(m.dpi()).append("\n");
                }
            }
            sb.append("\n");
        }

        if (graphics != null) {
            sb.append("[GRAPHICS DEVICE CAPABILITIES]\n");
            sb.append("  - GPU: ").append(string.get(graphics.gpuName())).append("\n");
            sb.append("  - Vendor / Device ID: 0x").append(Integer.toHexString(graphics.gpuVendorId())).append(" / 0x").append(Integer.toHexString(graphics.gpuDeviceId())).append("\n");
            sb.append("  - Active System API: ").append(string.get(graphics.primaryGraphicsApi())).append("\n");
            sb.append("  - Unified Memory Architecture (UMA): ").append(graphics.unifiedMemoryEnabled() ? "Yes (Shared Pool)" : "No (Discrete VRAM Heap)").append("\n");
            sb.append("  - Max Texture Size: ").append(graphics.maxTextureSize()).append("\n");
            sb.append("  - Hardware Ray Tracing: ").append(graphics.hardwareRayTracingEnabled() ? "Yes" : "No").append("\n");
            sb.append("  - Mesh Shaders: ").append(graphics.meshShadersEnabled() ? "Yes" : "No").append("\n");
            sb.append("  - Compute Shaders: ").append(graphics.computeShadersEnabled() ? "Yes" : "No").append("\n");
            sb.append("  - Physical VRAM Total / Available: ").append(graphics.vramTotal() / 1024 / 1024).append(" MB / ").append(graphics.vramAvailable() / 1024 / 1024).append(" MB\n\n");

            // --- Vulkan Instance Printing Diagnostic ---
            if (graphics.vulkanInstance() != null) {
                VulkanInstanceInfo vk = graphics.vulkanInstance();
                sb.append("  [Vulkan Sub-System Instance]\n");
                sb.append("    * Vulkan SDK Installed: ").append(vk.vulkanSdkInstalled() ? "Yes" : "No").append("\n");
                sb.append("    * Runtime API Version: ").append(string.get(vk.vulkanVersion())).append("\n");
                sb.append("    * Device Driver Build: ").append(string.get(vk.apiDriversVersion())).append("\n");
                sb.append("    * OS Memory Budget Allocation: ").append(vk.vulkanTotalMemoryBudget() / 1024 / 1024).append(" MB\n");
                sb.append("    * Sub-allocator Active Usage: ").append(vk.vulkanCurrentMemoryUsage() / 1024 / 1024).append(" MB\n");
                sb.append("    * Validation Status Layers: ").append(vk.validationLayersEnabled() ? "Active Debug Profile" : "Disabled (Release)").append("\n\n");
            }

            // --- Metal Instance Printing Diagnostic ---
            if (graphics.metalInstance() != null) {
                MetalInstanceInfo mt = graphics.metalInstance();
                sb.append("  [Apple Metal Sub-System Instance]\n");
                sb.append("    * Metal Framework Present: ").append(mt.metalRuntimeAvailable() ? "Yes" : "No").append("\n");
                sb.append("    * Architecture Class: ").append(mt.isAppleSilicon() ? "Apple Silicon SoC" : "Legacy Intel Mac Hardware").append("\n");
                sb.append("    * Processor Chip Silicon Model: ").append(string.get(mt.appleChipModel())).append("\n");
                sb.append("    * Hardware Feature Set: Apple GPU Family ").append(mt.appleGpuFamily()).append("\n");
                sb.append("    * Metal Dynamic API Version: ").append(string.get(mt.metalVersion())).append("\n");
                sb.append("    * Argument Binding Layouts: ").append(mt.argumentBuffersSupported() ? "Tier 2/Supported" : "Unsupported").append("\n");
                sb.append("    * Ray Tracing Execution Tier: ").append(mt.rayTracingTierSupported() ? "Hardware Accelerated Cores" : "None/Compute Polling").append("\n\n");
            }

            // --- DirectX Instance Printing Diagnostic ---
            if (graphics.directInstance() != null) {
                DirectXInstanceInfo dx = graphics.directInstance();
                sb.append("  [DirectX Sub-System Instance]\n");
                sb.append("    * DX12 API Supported: ").append(dx.directX12Supported() ? "Yes" : "No").append("\n");
                sb.append("    * Direct3D Core Feature Level: 0x").append(Integer.toHexString(dx.featureLevel())).append("\n");
                sb.append("    * Runtime Engine Agility SDK: ").append(dx.agilitySdkPresent() ? "Linked / Loaded" : "Missing / Stale").append("\n");
                sb.append("    * High Tier Shader Program compilation: ").append(dx.shaderModel6_xSupported() ? "Shader Model 6.x Early Native" : "Legacy Direct3D Layer").append("\n\n");
            }
        }

        if (audio != null) {
            sb.append("[AUDIO LATENCY PIPELINE]\n");
            sb.append("  - Device Target: ").append(string.get(audio.audioDeviceName())).append("\n");
            sb.append("  - Channels / Native Rate: ").append(audio.audioChannelCount()).append(" / ").append(audio.currentSampleRate()).append("Hz\n");
            sb.append("  - Output Hardware Latency: ").append(audio.audioOutputLatency()).append(" ms\n");
            sb.append("  - Spatial Audio: ").append(audio.spatialAudioSupported() ? "Yes" : "No").append("\n\n");
        }

        if (performance != null) {
            sb.append("[REAL-TIME EXECUTION METRICS]\n");
            sb.append("  - GPU Timestamp Period: ").append(performance.gpuTimestampPeriod()).append(" ns/tick\n");
            sb.append("  - Variable Rate Shading: ").append(performance.variableRateShadingSupported() ? "Yes" : "No").append("\n");
            sb.append("  - Power Saving Mode Active: ").append(performance.powerSavingModeActive() ? "Yes" : "No").append("\n");
            sb.append("  - OS Thermal Throttle State: Level ").append(performance.thermalMitigationLevel()).append("\n");
        }

        sb.append("==================================================\n");
        return sb.toString();
    }

    // ==========================================
    // STRUCTURAL STATIC RECORDS
    // ==========================================

    public static record Resolution(int width, int height) {}

    public static record HardwareInfo(
            long operatingSystem,
            long systemArchitecture,
            long deviceModel,
            long cpuBrand,
            int cpuCoreCount,
            int cpuThreadCount,
            long storageTotalSpace,
            long storageAvailableSpace,
            long ramTotal,
            long ramAvailable,
            long javaHeapSize,
            long javaHeapMax,
            long batteryStatus,
            float batteryLevel
    ) {}

    public static record DisplayMonitor(
            long id,
            long name,
            Resolution currentResolution,
            Resolution nativeResolution,
            int[] supportedRefreshRates,
            int currentRefreshRate,
            boolean hdrSupported,
            float dpi
    ) {}

    public static record DisplayInfo(
            int monitorCount,
            DisplayMonitor[] monitors,
            DisplayMonitor primaryMonitor,
            Resolution monitorResolution,
            Resolution nativeResolution,
            int[] refreshRates,
            int currentRefreshRate,
            boolean hdrSupported,
            float displayDensity
    ) {}

    public static record MemoryHeap(
            long totalSpace,
            boolean isDeviceLocal
    ) {}

    // Modular Sub-System Context Interfaces
    public static record VulkanInstanceInfo(
            boolean vulkanSdkInstalled,
            long vulkanVersion,
            long apiDriversVersion,
            long vulkanTotalMemoryBudget,
            long vulkanCurrentMemoryUsage,
            MemoryHeap[] vulkanMemoryHeaps,
            boolean validationLayersEnabled
    ) {}

    public static record MetalInstanceInfo(
            boolean metalRuntimeAvailable,
            boolean isAppleSilicon,
            int appleGpuFamily,
            long appleChipModel,
            long metalVersion,
            boolean argumentBuffersSupported,
            boolean rayTracingTierSupported
    ) {}

    public static record DirectXInstanceInfo(
            boolean directX12Supported,
            int featureLevel,
            boolean agilitySdkPresent,
            boolean shaderModel6_xSupported
    ) {}

    public static record GraphicsInfo(
            long gpuName,
            int gpuVendorId,
            int gpuDeviceId,
            long primaryGraphicsApi,
            boolean unifiedMemoryEnabled,
            boolean computeShadersEnabled,
            boolean meshShadersEnabled,
            boolean hardwareRayTracingEnabled,
            boolean instancedIndexedEnabled,
            int maxTextureSize,
            float maxAnisotropyLevel,
            int msaaSampleCounts,
            boolean tessellationSupported,
            long vramTotal,
            long vramAvailable,
            int maxComputeWorkGroupInvocations,
            boolean discreteGpuEnabled,
            float hdrMaxLuminance,
            float hdrMinLuminance,
            long surfaceColorSpace,
            VulkanInstanceInfo vulkanInstance,
            MetalInstanceInfo metalInstance,
            DirectXInstanceInfo directInstance
    ) {}

    public static record AudioDevice(
            long name,
            int[] supportedSampleRates
    ) {}

    public static record AudioInfo(
            long audioDeviceName,
            AudioDevice[] availableAudioDevices,
            int[] sampleRates,
            int currentSampleRate,
            int audioChannelCount,
            boolean spatialAudioSupported,
            int maxAudioSources,
            float audioOutputLatency
    ) {}

    public static record VideoInfo(
            long[] supportedVideoCodecs,
            boolean hardwareDecodingSupported,
            boolean hardwareEncodingSupported,
            Resolution maxVideoResolution,
            boolean hdrVideoPlaybackSupported,
            int videoFrameRateMax
    ) {}

    public static record PerformanceInfo(
            float gpuTimestampPeriod,
            boolean variableRateShadingSupported,
            long vrsSurfaceProperties,
            boolean dynamicResolutionScaleSupported,
            int thermalMitigationLevel,
            boolean powerSavingModeActive
    ) {}
}