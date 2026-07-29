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
            
            // TODO: Bind CoreGraphics / Metal FFM downcalls for GraphicsInfo and DisplayInfo
            // TODO: Bind CoreAudio for AudioInfo
        } catch (Throwable t) {
            t.printStackTrace();
        }
    }
}
