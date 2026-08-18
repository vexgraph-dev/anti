package system;

import annotation.Draft;
import annotation.Required;
import primitive.string;
import primitive.Long;

import java.lang.foreign.Arena;
import java.lang.foreign.FunctionDescriptor;
import java.lang.foreign.Linker;
import java.lang.foreign.MemorySegment;
import java.lang.foreign.SymbolLookup;
import java.lang.foreign.ValueLayout;
import java.lang.invoke.MethodHandle;

import nio.StringLookup;
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
            libc.find(StringLookup.getJavaString(566)).orElseThrow(),
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
        if (System.getProperty(StringLookup.getJavaString(143)).toLowerCase().contains(StringLookup.getJavaString(144))) {
            try {
                cg = SymbolLookup.libraryLookup(StringLookup.getJavaString(567), Arena.global());
                
                GET_ACTIVE_DISPLAY_LIST = linker.downcallHandle(
                        cg.find(StringLookup.getJavaString(568)).orElseThrow(),
                        FunctionDescriptor.of(ValueLayout.JAVA_INT,
                                ValueLayout.JAVA_INT, ValueLayout.ADDRESS, ValueLayout.ADDRESS)
                );
                
                MAIN_DISPLAY_ID = linker.downcallHandle(
                        cg.find(StringLookup.getJavaString(569)).orElseThrow(),
                        FunctionDescriptor.of(ValueLayout.JAVA_INT)
                );
                
                DISPLAY_PIXELS_WIDE = linker.downcallHandle(
                        cg.find(StringLookup.getJavaString(570)).orElseThrow(),
                        FunctionDescriptor.of(ValueLayout.JAVA_LONG, ValueLayout.JAVA_INT)
                );
                
                DISPLAY_PIXELS_HIGH = linker.downcallHandle(
                        cg.find(StringLookup.getJavaString(571)).orElseThrow(),
                        FunctionDescriptor.of(ValueLayout.JAVA_LONG, ValueLayout.JAVA_INT)
                );
                
                DISPLAY_COPY_DISPLAY_MODE = linker.downcallHandle(
                        cg.find(StringLookup.getJavaString(572)).orElseThrow(),
                        FunctionDescriptor.of(ValueLayout.ADDRESS, ValueLayout.JAVA_INT)
                );
                
                DISPLAY_MODE_GET_REFRESH_RATE = linker.downcallHandle(
                        cg.find(StringLookup.getJavaString(573)).orElseThrow(),
                        FunctionDescriptor.of(ValueLayout.JAVA_DOUBLE, ValueLayout.ADDRESS)
                );
                
                DISPLAY_MODE_RELEASE = linker.downcallHandle(
                        cg.find(StringLookup.getJavaString(574)).orElseThrow(),
                        FunctionDescriptor.ofVoid(ValueLayout.ADDRESS)
                );
            } catch (Throwable t) {
                System.err.println(StringLookup.getJavaString(575) + t.getMessage());
            }
        }
    }

    public static void bootstrap() {
        try (Arena arena = Arena.ofConfined()) {
            // Get RAM (hw.memsize)
            MemorySegment memsizeName = arena.allocateFrom(StringLookup.getJavaString(576));
            MemorySegment memsizeVal = arena.allocate(ValueLayout.JAVA_LONG);
            MemorySegment memsizeLen = arena.allocateFrom(ValueLayout.JAVA_LONG, 8L);
            
            if ((int) SYSCTL_BY_NAME.invokeExact(memsizeName, memsizeVal, memsizeLen, MemorySegment.NULL, 0L) == 0) {
                HardwareInfo.setRamTotal(memsizeVal.get(ValueLayout.JAVA_LONG, 0));
            }

            // Get CPU Cores (hw.logicalcpu)
            MemorySegment cpuName = arena.allocateFrom(StringLookup.getJavaString(577));
            MemorySegment cpuVal = arena.allocate(ValueLayout.JAVA_INT);
            MemorySegment cpuLen = arena.allocateFrom(ValueLayout.JAVA_LONG, 4L);
            
            if ((int) SYSCTL_BY_NAME.invokeExact(cpuName, cpuVal, cpuLen, MemorySegment.NULL, 0L) == 0) {
                HardwareInfo.setCpuCoreCount(cpuVal.get(ValueLayout.JAVA_INT, 0));
                HardwareInfo.setCpuThreadCount(cpuVal.get(ValueLayout.JAVA_INT, 0));
            }

            // Get Device Model (hw.model)
            MemorySegment modelName = arena.allocateFrom(StringLookup.getJavaString(578));
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
                    long monitorsPtr = Long.allocateArray(count);
                    long primaryPtr = 0L;
                    
                    for (int i = 0; i < count; i++) {
                        int displayId = displaysArray.getAtIndex(ValueLayout.JAVA_INT, i);
                        long monitorPtr = DisplayMonitor.allocate();
                        DisplayMonitor.setId(monitorPtr, displayId);
                        
                        // Query resolution
                        long wide = (long) DISPLAY_PIXELS_WIDE.invokeExact(displayId);
                        long high = (long) DISPLAY_PIXELS_HIGH.invokeExact(displayId);
                        DisplayMonitor.setCurrentWidth(monitorPtr, (int) wide);
                        DisplayMonitor.setCurrentHeight(monitorPtr, (int) high);
                        DisplayMonitor.setNativeWidth(monitorPtr, (int) wide);
                        DisplayMonitor.setNativeHeight(monitorPtr, (int) high);
                        
                        // Query refresh rate
                        MemorySegment mode = (MemorySegment) DISPLAY_COPY_DISPLAY_MODE.invokeExact(displayId);
                        if (mode.address() != 0L) {
                            double refreshHz = (double) DISPLAY_MODE_GET_REFRESH_RATE.invokeExact(mode);
                            DisplayMonitor.setRefreshRate(monitorPtr, (int) refreshHz);
                            DISPLAY_MODE_RELEASE.invokeExact(mode);
                        } else {
                            DisplayMonitor.setRefreshRate(monitorPtr, 60); // fallback
                        }
                        
                        // Display Name
                        String nameStr = StringLookup.getJavaString(579) + displayId;
                        DisplayMonitor.setName(monitorPtr, string.allocate(nameStr));
                        
                        // Set in array
                        Long.set(monitorsPtr, i, monitorPtr);
                        
                        if (displayId == mainDisplayId) {
                            primaryPtr = monitorPtr;
                        }
                    }
                    
                    DisplayInfo.setMonitorsPointer(monitorsPtr);
                    if (primaryPtr == 0L && count > 0) {
                        primaryPtr = Long.get(monitorsPtr, 0);
                    }
                    
                    if (primaryPtr != 0L) {
                        DisplayInfo.setPrimaryMonitorPointer(primaryPtr);
                        DisplayInfo.setMonitorResolutionWidth(DisplayMonitor.getCurrentWidth(primaryPtr));
                        DisplayInfo.setMonitorResolutionHeight(DisplayMonitor.getCurrentHeight(primaryPtr));
                        DisplayInfo.setNativeResolutionWidth(DisplayMonitor.getNativeWidth(primaryPtr));
                        DisplayInfo.setNativeResolutionHeight(DisplayMonitor.getNativeHeight(primaryPtr));
                        DisplayInfo.setCurrentRefreshRate(DisplayMonitor.getRefreshRate(primaryPtr));
                    }
                }
            }
        } catch (Throwable t) {
            t.printStackTrace();
        }
    }
}
