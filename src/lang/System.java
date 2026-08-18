package lang;

import system.*;
import primitive.string;
import java.lang.StringBuilder;

import java.io.BufferedReader;
import java.io.InputStreamReader;

import nio.StringLookup;
public final class System {

    static {
        // Initialize Native Signal and Telemetry Crash Handler
        telemetry.CrashDumper.init();

        // base operating system and memory thingies
        String osName = java.lang.System.getProperty(StringLookup.getJavaString(143)).toLowerCase();
        long osNamePtr = string.allocate(java.lang.System.getProperty(StringLookup.getJavaString(143)));
        long osArchPtr = string.allocate(java.lang.System.getProperty(StringLookup.getJavaString(425)));
        long deviceModelPtr = string.allocate(isMobileOrHandheld() ? StringLookup.getJavaString(426) : StringLookup.getJavaString(427));

        String rawCpu = java.lang.System.getenv(StringLookup.getJavaString(428));
        int cores = Runtime.getRuntime().availableProcessors();
        int threads = cores * 2;

        long totalMemory = 0L;
        long freeMemory = 0L;

        // Non-reflective: com.sun.management.OperatingSystemMXBean directly exposes
        // getTotalPhysicalMemorySize/getFreePhysicalMemorySize methods.
        try {
            java.lang.management.OperatingSystemMXBean osBean = java.lang.management.ManagementFactory.getOperatingSystemMXBean();
            if (osBean instanceof com.sun.management.OperatingSystemMXBean sun) {
                totalMemory = sun.getTotalPhysicalMemorySize();
                freeMemory = sun.getFreePhysicalMemorySize();
            } else {
                totalMemory = Runtime.getRuntime().maxMemory();
                freeMemory = Runtime.getRuntime().freeMemory();
            }
        } catch (Throwable e) {
            totalMemory = Runtime.getRuntime().maxMemory();
            freeMemory = Runtime.getRuntime().freeMemory();
        }

        long cpuBrandPtr = string.allocate(rawCpu != null ? rawCpu : java.lang.System.getProperty(StringLookup.getJavaString(425)));

        long totalStorage = 0;
        long availableStorage = 0;
        try {
            java.io.File root = new java.io.File(StringLookup.getJavaString(40));
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
        String detectedGpuName = osName.contains(StringLookup.getJavaString(144)) ? StringLookup.getJavaString(429) : StringLookup.getJavaString(430);
        String primaryGraphicsApi;
        boolean unifiedMemoryArchitecture = osName.contains(StringLookup.getJavaString(144));
        long physicalVramTotal = 0L;

        boolean hasVulkanSdk = java.lang.System.getenv(StringLookup.getJavaString(431)) != null || java.lang.System.getenv(StringLookup.getJavaString(432)) != null;
        String vulkanApiVersion = StringLookup.getJavaString(433);
        String vulkanDriverVersion = StringLookup.getJavaString(434);
        boolean vkValidationLayers = hasVulkanSdk;

        boolean hasMetalRuntime = false;
        boolean isAppleSiliconGpu = false;
        int appleGpuFamilyTier = 0;
        String appleSiliconChipModel = StringLookup.getJavaString(434);
        String metalVersionString = StringLookup.getJavaString(433);

        boolean dynamicDx12Supported = false;
        int d3dFeatureLevel = 0;
        boolean agilitySdkPresent = false;

        if (osName.contains(StringLookup.getJavaString(144))) {
            hasMetalRuntime = true;
            primaryGraphicsApi = StringLookup.getJavaString(435);

            String arch = java.lang.System.getProperty(StringLookup.getJavaString(425)).toLowerCase();
            if (arch.contains(StringLookup.getJavaString(436)) || arch.contains(StringLookup.getJavaString(437))) {
                isAppleSiliconGpu = true;
                unifiedMemoryArchitecture = true;
                physicalVramTotal = totalMemory;
            }

            String hardwareProfile = executeNativeCommand(StringLookup.getJavaString(438), StringLookup.getJavaString(439));
            for (String line : hardwareProfile.split(StringLookup.getJavaString(102))) {
                if (line.contains(StringLookup.getJavaString(440))) {
                    appleSiliconChipModel = line.split(StringLookup.getJavaString(43), 2)[1].trim();
                    detectedGpuName = appleSiliconChipModel;
                    if (appleSiliconChipModel.contains(StringLookup.getJavaString(441))) {
                        appleGpuFamilyTier = 7;
                        metalVersionString = StringLookup.getJavaString(442);
                    } else if (appleSiliconChipModel.contains(StringLookup.getJavaString(443))) {
                        appleGpuFamilyTier = 8;
                        metalVersionString = StringLookup.getJavaString(442);
                    } else if (appleSiliconChipModel.contains(StringLookup.getJavaString(444)) || appleSiliconChipModel.contains(StringLookup.getJavaString(445)) || appleSiliconChipModel.contains(StringLookup.getJavaString(446))) {
                        appleGpuFamilyTier = 9;
                        metalVersionString = StringLookup.getJavaString(447);
                    } else if (appleSiliconChipModel.contains(StringLookup.getJavaString(448)) || appleSiliconChipModel.contains(StringLookup.getJavaString(449))) {
                        appleGpuFamilyTier = 10;
                        metalVersionString = StringLookup.getJavaString(450);
                    } else {
                        appleGpuFamilyTier = 6;
                        metalVersionString = StringLookup.getJavaString(451);
                    }
                    break;
                }
            }
            if (metalVersionString.equals(StringLookup.getJavaString(433))) metalVersionString = StringLookup.getJavaString(452);

        } else if (osName.contains(StringLookup.getJavaString(453))) {
            dynamicDx12Supported = true;
            primaryGraphicsApi = StringLookup.getJavaString(454);

            String wmicOutput = executeNativeCommand(StringLookup.getJavaString(455), StringLookup.getJavaString(456), StringLookup.getJavaString(457), StringLookup.getJavaString(458), StringLookup.getJavaString(459), StringLookup.getJavaString(460));
            for (String line : wmicOutput.split(StringLookup.getJavaString(102))) {
                line = line.trim();
                if (line.startsWith(StringLookup.getJavaString(461))) {
                    detectedGpuName = line.split(StringLookup.getJavaString(462), 2)[1].trim();
                } else if (line.startsWith(StringLookup.getJavaString(463))) {
                    try {
                        physicalVramTotal = Long.parseLong(line.split(StringLookup.getJavaString(462), 2)[1].trim());
                    } catch (Exception ignored) {}
                }
            }
            String lowerGpu = detectedGpuName.toLowerCase();
            if (lowerGpu.contains(StringLookup.getJavaString(464)) || lowerGpu.contains(StringLookup.getJavaString(465)) || lowerGpu.contains(StringLookup.getJavaString(466))) {
                unifiedMemoryArchitecture = true;
                if (physicalVramTotal == 0) physicalVramTotal = totalMemory / 4;
            }
            d3dFeatureLevel = 0xC200;
        } else {
            primaryGraphicsApi = StringLookup.getJavaString(467);
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
        GraphicsInfo.setMeshShadersEnabled(appleGpuFamilyTier >= 9 || osName.contains(StringLookup.getJavaString(453)));
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
        GraphicsInfo.setSurfaceColorSpace(string.allocate(StringLookup.getJavaString(468)));

        // Set Audio Static State
        AudioInfo.setAudioDeviceName(string.allocate(StringLookup.getJavaString(469)));
        AudioInfo.setAvailableAudioDevices(new AudioDevice[0]);
        AudioInfo.setSampleRates(new int[]{48000});
        AudioInfo.setCurrentSampleRate(48000);
        AudioInfo.setAudioChannelCount(2);
        AudioInfo.setSpatialAudioSupported(false);
        AudioInfo.setMaxAudioSources(32);
        AudioInfo.setAudioOutputLatency(10.0f);

        // Set Video Static State
        VideoInfo.setSupportedVideoCodecs(new long[]{string.allocate(StringLookup.getJavaString(470))});
        VideoInfo.setHardwareDecodingSupported(true);
        VideoInfo.setHardwareEncodingSupported(false);
        VideoInfo.setHdrVideoPlaybackSupported(false);
        VideoInfo.setVideoFrameRateMax(60);

        // Set Performance Static State
        PerformanceInfo.setGpuTimestampPeriod(1.0f);
        PerformanceInfo.setVariableRateShadingSupported(false);
        PerformanceInfo.setVrsSurfaceProperties(string.allocate(StringLookup.getJavaString(471)));
        PerformanceInfo.setDynamicResolutionScaleSupported(false);
        PerformanceInfo.setThermalMitigationLevel(0);
        PerformanceInfo.setPowerSavingModeActive(false);

        String batteryStatusStr = StringLookup.getJavaString(472);
        float batteryLevelVal = 1.0f;

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
                    output.append(line).append(StringLookup.getJavaString(102));
                }
            }
            process.waitFor();
            return output.toString();
        } catch (Exception e) {
            return StringLookup.getJavaString(0); 
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
        String os = java.lang.System.getProperty(StringLookup.getJavaString(143)).toLowerCase();
        return os.contains(StringLookup.getJavaString(473)) || os.contains(StringLookup.getJavaString(474));
    }

    // ==========================================
    // THE "GOD" SPEC SPECIFICATION STRINGIFIER
    // ==========================================
    public static String getSystem() {
        StringBuilder sb = new StringBuilder();
        sb.append(StringLookup.getJavaString(475));
        sb.append(StringLookup.getJavaString(476));
        sb.append(StringLookup.getJavaString(477));

        sb.append(StringLookup.getJavaString(478));
        sb.append(StringLookup.getJavaString(479)).append(string.get(HardwareInfo.getOperatingSystem())).append(StringLookup.getJavaString(102));
        sb.append(StringLookup.getJavaString(480)).append(string.get(HardwareInfo.getSystemArchitecture())).append(StringLookup.getJavaString(102));
        sb.append(StringLookup.getJavaString(481)).append(string.get(HardwareInfo.getDeviceModel())).append(StringLookup.getJavaString(102));
        sb.append(StringLookup.getJavaString(482)).append(string.get(HardwareInfo.getCpuBrand())).append(StringLookup.getJavaString(102));
        sb.append(StringLookup.getJavaString(483)).append(HardwareInfo.getCpuCoreCount()).append(StringLookup.getJavaString(484)).append(HardwareInfo.getCpuThreadCount()).append(StringLookup.getJavaString(102));
        sb.append(StringLookup.getJavaString(485)).append(HardwareInfo.getRamTotal() / 1024 / 1024).append(StringLookup.getJavaString(486));
        sb.append(StringLookup.getJavaString(487)).append(HardwareInfo.getRamAvailable() / 1024 / 1024).append(StringLookup.getJavaString(486));
        sb.append(StringLookup.getJavaString(488)).append(HardwareInfo.getJavaHeapSize() / 1024 / 1024).append(StringLookup.getJavaString(489)).append(HardwareInfo.getJavaHeapMax() / 1024 / 1024).append(StringLookup.getJavaString(486));
        sb.append(StringLookup.getJavaString(490)).append(HardwareInfo.getStorageTotalSpace() / 1024 / 1024 / 1024).append(StringLookup.getJavaString(491)).append(HardwareInfo.getStorageAvailableSpace() / 1024 / 1024 / 1024).append(StringLookup.getJavaString(492));
        sb.append(StringLookup.getJavaString(493)).append(HardwareInfo.getBatteryLevel() * 100).append(StringLookup.getJavaString(494)).append(string.get(HardwareInfo.getBatteryStatus())).append(StringLookup.getJavaString(495));

        sb.append(StringLookup.getJavaString(496));
        sb.append(StringLookup.getJavaString(497)).append(DisplayInfo.getMonitorCount()).append(StringLookup.getJavaString(102));
        sb.append(StringLookup.getJavaString(102));

        sb.append(StringLookup.getJavaString(498));
        sb.append(StringLookup.getJavaString(499)).append(string.get(GraphicsInfo.getGpuName())).append(StringLookup.getJavaString(102));
        sb.append(StringLookup.getJavaString(500)).append(Integer.toHexString(GraphicsInfo.getGpuVendorId())).append(StringLookup.getJavaString(501)).append(Integer.toHexString(GraphicsInfo.getGpuDeviceId())).append(StringLookup.getJavaString(102));
        sb.append(StringLookup.getJavaString(502)).append(string.get(GraphicsInfo.getPrimaryGraphicsApi())).append(StringLookup.getJavaString(102));
        sb.append(StringLookup.getJavaString(503)).append(GraphicsInfo.getUnifiedMemoryEnabled() ? StringLookup.getJavaString(504) : StringLookup.getJavaString(505)).append(StringLookup.getJavaString(102));
        sb.append(StringLookup.getJavaString(506)).append(GraphicsInfo.getMaxTextureSize()).append(StringLookup.getJavaString(102));
        sb.append(StringLookup.getJavaString(507)).append(GraphicsInfo.getHardwareRayTracingEnabled() ? StringLookup.getJavaString(508) : StringLookup.getJavaString(509)).append(StringLookup.getJavaString(102));
        sb.append(StringLookup.getJavaString(510)).append(GraphicsInfo.getMeshShadersEnabled() ? StringLookup.getJavaString(508) : StringLookup.getJavaString(509)).append(StringLookup.getJavaString(102));
        sb.append(StringLookup.getJavaString(511)).append(GraphicsInfo.getComputeShadersEnabled() ? StringLookup.getJavaString(508) : StringLookup.getJavaString(509)).append(StringLookup.getJavaString(102));
        sb.append(StringLookup.getJavaString(512)).append(GraphicsInfo.getVramTotal() / 1024 / 1024).append(StringLookup.getJavaString(489)).append(GraphicsInfo.getVramAvailable() / 1024 / 1024).append(StringLookup.getJavaString(513));

        sb.append(StringLookup.getJavaString(514));
        sb.append(StringLookup.getJavaString(515)).append(VulkanInstanceInfo.getVulkanSdkInstalled() ? StringLookup.getJavaString(508) : StringLookup.getJavaString(509)).append(StringLookup.getJavaString(102));
        sb.append(StringLookup.getJavaString(516)).append(string.get(VulkanInstanceInfo.getVulkanVersion())).append(StringLookup.getJavaString(102));
        sb.append(StringLookup.getJavaString(517)).append(string.get(VulkanInstanceInfo.getApiDriversVersion())).append(StringLookup.getJavaString(102));
        sb.append(StringLookup.getJavaString(518)).append(VulkanInstanceInfo.getVulkanTotalMemoryBudget() / 1024 / 1024).append(StringLookup.getJavaString(486));
        sb.append(StringLookup.getJavaString(519)).append(VulkanInstanceInfo.getVulkanCurrentMemoryUsage() / 1024 / 1024).append(StringLookup.getJavaString(486));
        sb.append(StringLookup.getJavaString(520)).append(VulkanInstanceInfo.getValidationLayersEnabled() ? StringLookup.getJavaString(521) : StringLookup.getJavaString(522)).append(StringLookup.getJavaString(523));

        sb.append(StringLookup.getJavaString(524));
        sb.append(StringLookup.getJavaString(525)).append(MetalInstanceInfo.getMetalRuntimeAvailable() ? StringLookup.getJavaString(508) : StringLookup.getJavaString(509)).append(StringLookup.getJavaString(102));
        sb.append(StringLookup.getJavaString(526)).append(MetalInstanceInfo.getIsAppleSilicon() ? StringLookup.getJavaString(527) : StringLookup.getJavaString(528)).append(StringLookup.getJavaString(102));
        sb.append(StringLookup.getJavaString(529)).append(string.get(MetalInstanceInfo.getAppleChipModel())).append(StringLookup.getJavaString(102));
        sb.append(StringLookup.getJavaString(530)).append(MetalInstanceInfo.getAppleGpuFamily()).append(StringLookup.getJavaString(102));
        sb.append(StringLookup.getJavaString(531)).append(string.get(MetalInstanceInfo.getMetalVersion())).append(StringLookup.getJavaString(102));
        sb.append(StringLookup.getJavaString(532)).append(MetalInstanceInfo.getArgumentBuffersSupported() ? StringLookup.getJavaString(533) : StringLookup.getJavaString(534)).append(StringLookup.getJavaString(102));
        sb.append(StringLookup.getJavaString(535)).append(MetalInstanceInfo.getRayTracingTierSupported() ? StringLookup.getJavaString(536) : StringLookup.getJavaString(537)).append(StringLookup.getJavaString(523));

        sb.append(StringLookup.getJavaString(538));
        sb.append(StringLookup.getJavaString(539)).append(DirectXInstanceInfo.getDirectX12Supported() ? StringLookup.getJavaString(508) : StringLookup.getJavaString(509)).append(StringLookup.getJavaString(102));
        sb.append(StringLookup.getJavaString(540)).append(Integer.toHexString(DirectXInstanceInfo.getFeatureLevel())).append(StringLookup.getJavaString(102));
        sb.append(StringLookup.getJavaString(541)).append(DirectXInstanceInfo.getAgilitySdkPresent() ? StringLookup.getJavaString(542) : StringLookup.getJavaString(543)).append(StringLookup.getJavaString(102));
        sb.append(StringLookup.getJavaString(544)).append(DirectXInstanceInfo.getShaderModel6_xSupported() ? StringLookup.getJavaString(545) : StringLookup.getJavaString(546)).append(StringLookup.getJavaString(523));

        sb.append(StringLookup.getJavaString(547));
        sb.append(StringLookup.getJavaString(548)).append(string.get(AudioInfo.getAudioDeviceName())).append(StringLookup.getJavaString(102));
        sb.append(StringLookup.getJavaString(549)).append(AudioInfo.getAudioChannelCount()).append(StringLookup.getJavaString(484)).append(AudioInfo.getCurrentSampleRate()).append(StringLookup.getJavaString(550));
        sb.append(StringLookup.getJavaString(551)).append(AudioInfo.getAudioOutputLatency()).append(StringLookup.getJavaString(552));
        sb.append(StringLookup.getJavaString(553)).append(AudioInfo.getSpatialAudioSupported() ? StringLookup.getJavaString(508) : StringLookup.getJavaString(509)).append(StringLookup.getJavaString(523));

        sb.append(StringLookup.getJavaString(554));
        sb.append(StringLookup.getJavaString(555)).append(PerformanceInfo.getGpuTimestampPeriod()).append(StringLookup.getJavaString(556));
        sb.append(StringLookup.getJavaString(557)).append(PerformanceInfo.getVariableRateShadingSupported() ? StringLookup.getJavaString(508) : StringLookup.getJavaString(509)).append(StringLookup.getJavaString(102));
        sb.append(StringLookup.getJavaString(558)).append(PerformanceInfo.getPowerSavingModeActive() ? StringLookup.getJavaString(508) : StringLookup.getJavaString(509)).append(StringLookup.getJavaString(102));
        sb.append(StringLookup.getJavaString(559)).append(PerformanceInfo.getThermalMitigationLevel()).append(StringLookup.getJavaString(102));

        sb.append(StringLookup.getJavaString(475));
        return sb.toString();
    }
}