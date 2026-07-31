package system;

import annotation.Draft;
import annotation.Required;
import primitive.string;

import java.lang.foreign.Arena;
import java.lang.foreign.FunctionDescriptor;
import java.lang.foreign.Linker;
import java.lang.foreign.MemorySegment;
import java.lang.foreign.SymbolLookup;
import java.lang.foreign.ValueLayout;
import java.lang.invoke.MethodHandle;

/**
 * Pure FFM zero-allocation system query subsystem (bypassing OSHI).
 */
@Draft
@Required
public class SystemDiscovery {

    private static final Linker linker = Linker.nativeLinker();
    private static final SymbolLookup libc = linker.defaultLookup();
    
    // int sysctlbyname(const char *name, void *oldp, size_t *oldlenp, void *newp, size_t newlen);
    private static final MethodHandle SYSCTL_BY_NAME = linker.downcallHandle(
            libc.find("sysctlbyname").orElseThrow(),
            FunctionDescriptor.of(ValueLayout.JAVA_INT, 
                ValueLayout.ADDRESS, ValueLayout.ADDRESS, ValueLayout.ADDRESS, ValueLayout.ADDRESS, ValueLayout.JAVA_LONG)
    );

    // CoreGraphics library lookup and method handles
    private static SymbolLookup cg;
    private static MethodHandle GET_ACTIVE_DISPLAY_LIST;
    private static MethodHandle MAIN_DISPLAY_ID;
    private static MethodHandle DISPLAY_PIXELS_WIDE;
    private static MethodHandle DISPLAY_PIXELS_HIGH;
    private static MethodHandle DISPLAY_COPY_DISPLAY_MODE;
    private static MethodHandle DISPLAY_MODE_GET_REFRESH_RATE;
    private static MethodHandle DISPLAY_MODE_RELEASE;

    static {
        if (System.getProperty("os.name").toLowerCase().contains("mac")) {
            try {
                cg = SymbolLookup.libraryLookup("/System/Library/Frameworks/CoreGraphics.framework/CoreGraphics", Arena.global());
                
                GET_ACTIVE_DISPLAY_LIST = linker.downcallHandle(
                        cg.find("CGGetActiveDisplayList").orElseThrow(),
                        FunctionDescriptor.of(ValueLayout.JAVA_INT,
                                ValueLayout.JAVA_INT, ValueLayout.ADDRESS, ValueLayout.ADDRESS)
                );
                
                MAIN_DISPLAY_ID = linker.downcallHandle(
                        cg.find("CGMainDisplayID").orElseThrow(),
                        FunctionDescriptor.of(ValueLayout.JAVA_INT)
                );
                
                DISPLAY_PIXELS_WIDE = linker.downcallHandle(
                        cg.find("CGDisplayPixelsWide").orElseThrow(),
                        FunctionDescriptor.of(ValueLayout.JAVA_LONG, ValueLayout.JAVA_INT)
                );
                
                DISPLAY_PIXELS_HIGH = linker.downcallHandle(
                        cg.find("CGDisplayPixelsHigh").orElseThrow(),
                        FunctionDescriptor.of(ValueLayout.JAVA_LONG, ValueLayout.JAVA_INT)
                );
                
                DISPLAY_COPY_DISPLAY_MODE = linker.downcallHandle(
                        cg.find("CGDisplayCopyDisplayMode").orElseThrow(),
                        FunctionDescriptor.of(ValueLayout.ADDRESS, ValueLayout.JAVA_INT)
                );
                
                DISPLAY_MODE_GET_REFRESH_RATE = linker.downcallHandle(
                        cg.find("CGDisplayModeGetRefreshRate").orElseThrow(),
                        FunctionDescriptor.of(ValueLayout.JAVA_DOUBLE, ValueLayout.ADDRESS)
                );
                
                DISPLAY_MODE_RELEASE = linker.downcallHandle(
                        cg.find("CGDisplayModeRelease").orElseThrow(),
                        FunctionDescriptor.ofVoid(ValueLayout.ADDRESS)
                );
            } catch (Throwable t) {
                System.err.println("Warning: Failed to bind macOS CoreGraphics FFM downcalls: " + t.getMessage());
            }
        }
    }

    public static void bootstrap() {
        try (Arena arena = Arena.ofConfined()) {
            // Get RAM (hw.memsize)
            MemorySegment memsizeName = arena.allocateFrom("hw.memsize");
            MemorySegment memsizeVal = arena.allocate(ValueLayout.JAVA_LONG);
            MemorySegment memsizeLen = arena.allocateFrom(ValueLayout.JAVA_LONG, 8L);
            
            if ((int) SYSCTL_BY_NAME.invokeExact(memsizeName, memsizeVal, memsizeLen, MemorySegment.NULL, 0L) == 0) {
                HardwareInfo.setRamTotal(memsizeVal.get(ValueLayout.JAVA_LONG, 0));
            }

            // Get CPU Cores (hw.logicalcpu)
            MemorySegment cpuName = arena.allocateFrom("hw.logicalcpu");
            MemorySegment cpuVal = arena.allocate(ValueLayout.JAVA_INT);
            MemorySegment cpuLen = arena.allocateFrom(ValueLayout.JAVA_LONG, 4L);
            
            if ((int) SYSCTL_BY_NAME.invokeExact(cpuName, cpuVal, cpuLen, MemorySegment.NULL, 0L) == 0) {
                HardwareInfo.setCpuCoreCount(cpuVal.get(ValueLayout.JAVA_INT, 0));
                HardwareInfo.setCpuThreadCount(cpuVal.get(ValueLayout.JAVA_INT, 0));
            }

            // Get Device Model (hw.model)
            MemorySegment modelName = arena.allocateFrom("hw.model");
            MemorySegment modelLen = arena.allocateFrom(ValueLayout.JAVA_LONG, 256L);
            MemorySegment modelVal = arena.allocate(256);
            
            if ((int) SYSCTL_BY_NAME.invokeExact(modelName, modelVal, modelLen, MemorySegment.NULL, 0L) == 0) {
                long modelStringPtr = string.allocate(modelVal.getString(0));
                HardwareInfo.setDeviceModel(modelStringPtr);
            }
            
            // Query CoreGraphics Displays on macOS
            if (cg != null) {
                MemorySegment displayCount = arena.allocate(ValueLayout.JAVA_INT);
                MemorySegment displaysArray = arena.allocate(ValueLayout.JAVA_INT, 16L); // support up to 16 displays
                
                if ((int) GET_ACTIVE_DISPLAY_LIST.invokeExact(16, displaysArray, displayCount) == 0) {
                    int count = displayCount.get(ValueLayout.JAVA_INT, 0);
                    DisplayInfo.setMonitorCount(count);
                    
                    int mainDisplayId = (int) MAIN_DISPLAY_ID.invokeExact();
                    DisplayMonitor[] monitors = new DisplayMonitor[count];
                    DisplayMonitor primary = null;
                    
                    for (int i = 0; i < count; i++) {
                        int displayId = displaysArray.getAtIndex(ValueLayout.JAVA_INT, i);
                        DisplayMonitor monitor = new DisplayMonitor();
                        monitor.setId(displayId);
                        
                        // Query resolution
                        long wide = (long) DISPLAY_PIXELS_WIDE.invokeExact(displayId);
                        long high = (long) DISPLAY_PIXELS_HIGH.invokeExact(displayId);
                        Resolution currentRes = new Resolution((int) wide, (int) high);
                        monitor.setCurrentResolution(currentRes);
                        monitor.setNativeResolution(currentRes); // default to current
                        
                        // Query refresh rate
                        MemorySegment mode = (MemorySegment) DISPLAY_COPY_DISPLAY_MODE.invokeExact(displayId);
                        if (mode.address() != 0L) {
                            double refreshHz = (double) DISPLAY_MODE_GET_REFRESH_RATE.invokeExact(mode);
                            monitor.setCurrentRefreshRate((int) refreshHz);
                            DISPLAY_MODE_RELEASE.invokeExact(mode);
                        } else {
                            monitor.setCurrentRefreshRate(60); // fallback
                        }
                        
                        // Display Name
                        String nameStr = "Display-" + displayId;
                        monitor.setName(string.allocate(nameStr));
                        
                        monitors[i] = monitor;
                        if (displayId == mainDisplayId) {
                            primary = monitor;
                        }
                    }
                    
                    DisplayInfo.setMonitors(monitors);
                    if (primary != null) {
                        DisplayInfo.setPrimaryMonitor(primary);
                        DisplayInfo.setMonitorResolution(primary.getCurrentResolution());
                        DisplayInfo.setNativeResolution(primary.getNativeResolution());
                        DisplayInfo.setCurrentRefreshRate(primary.getCurrentRefreshRate());
                    } else if (count > 0) {
                        DisplayInfo.setPrimaryMonitor(monitors[0]);
                        DisplayInfo.setMonitorResolution(monitors[0].getCurrentResolution());
                        DisplayInfo.setNativeResolution(monitors[0].getNativeResolution());
                        DisplayInfo.setCurrentRefreshRate(monitors[0].getCurrentRefreshRate());
                    }
                }
            }
        } catch (Throwable t) {
            t.printStackTrace();
        }
    }
}
