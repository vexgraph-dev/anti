package system;

import annotation.Draft;
import annotation.Required;
import annotation.Intention;
import nio.ForeignMemory;
import oop.TypeRegister;
import primitive.string;

import nio.StringLookup;
@Draft
@Intention("Off-heap zero-allocation display monitor struct representation")
public final class DisplayMonitor {

    @Required
    public static final int CLASS_ID = TypeRegister.ID_DISPLAY_MONITOR;

    private DisplayMonitor() {}

    public static long allocate() {
        long block = ForeignMemory.allocateNative(8L + 48L);
        long userPtr = block + 8L;

        int type = TypeRegister.FORM_SINGLETON | CLASS_ID;
        ForeignMemory.setInt(block, type);
        ForeignMemory.setInt(block + 4L, 1);

        // Zero memory
        ForeignMemory.setMemory(userPtr, 48L, (byte) 0);
        return userPtr;
    }

    private static void checkType(long monitorPtr) {
        if (monitorPtr == 0L) throw new NullPointerException(StringLookup.getJavaString(580));
        int type = ForeignMemory.getInt(monitorPtr - 8L);
        int expected = TypeRegister.FORM_SINGLETON | CLASS_ID;
        if (type != expected) {
            throw new IllegalArgumentException(StringLookup.getJavaString(581) + Integer.toHexString(type).toUpperCase());
        }
    }

    public static long getId(long monitorPtr) {
        checkType(monitorPtr);
        return ForeignMemory.getLong(monitorPtr);
    }

    public static void setId(long monitorPtr, long id) {
        checkType(monitorPtr);
        ForeignMemory.setLong(monitorPtr, id);
    }

    public static long getName(long monitorPtr) {
        checkType(monitorPtr);
        return ForeignMemory.getLong(monitorPtr + 8L);
    }

    public static void setName(long monitorPtr, long nameStrPtr) {
        checkType(monitorPtr);
        ForeignMemory.setLong(monitorPtr + 8L, nameStrPtr);
    }

    public static int getCurrentWidth(long monitorPtr) {
        checkType(monitorPtr);
        return ForeignMemory.getInt(monitorPtr + 16L);
    }

    public static void setCurrentWidth(long monitorPtr, int val) {
        checkType(monitorPtr);
        ForeignMemory.setInt(monitorPtr + 16L, val);
    }

    public static int getCurrentHeight(long monitorPtr) {
        checkType(monitorPtr);
        return ForeignMemory.getInt(monitorPtr + 20L);
    }

    public static void setCurrentHeight(long monitorPtr, int val) {
        checkType(monitorPtr);
        ForeignMemory.setInt(monitorPtr + 20L, val);
    }

    public static int getNativeWidth(long monitorPtr) {
        checkType(monitorPtr);
        return ForeignMemory.getInt(monitorPtr + 24L);
    }

    public static void setNativeWidth(long monitorPtr, int val) {
        checkType(monitorPtr);
        ForeignMemory.setInt(monitorPtr + 24L, val);
    }

    public static int getNativeHeight(long monitorPtr) {
        checkType(monitorPtr);
        return ForeignMemory.getInt(monitorPtr + 28L);
    }

    public static void setNativeHeight(long monitorPtr, int val) {
        checkType(monitorPtr);
        ForeignMemory.setInt(monitorPtr + 28L, val);
    }

    public static int getRefreshRate(long monitorPtr) {
        checkType(monitorPtr);
        return ForeignMemory.getInt(monitorPtr + 32L);
    }

    public static void setRefreshRate(long monitorPtr, int val) {
        checkType(monitorPtr);
        ForeignMemory.setInt(monitorPtr + 32L, val);
    }

    public static boolean getHdrSupported(long monitorPtr) {
        checkType(monitorPtr);
        return ForeignMemory.getInt(monitorPtr + 36L) == 1;
    }

    public static void setHdrSupported(long monitorPtr, boolean val) {
        checkType(monitorPtr);
        ForeignMemory.setInt(monitorPtr + 36L, val ? 1 : 0);
    }

    public static float getDpi(long monitorPtr) {
        checkType(monitorPtr);
        return ForeignMemory.getFloat(monitorPtr + 40L);
    }

    public static void setDpi(long monitorPtr, float val) {
        checkType(monitorPtr);
        ForeignMemory.setFloat(monitorPtr + 40L, val);
    }

    public static void free(long monitorPtr) {
        if (monitorPtr == 0L) return;
        checkType(monitorPtr);

        long namePtr = getName(monitorPtr);
        if (namePtr != 0L) {
            string.free(namePtr);
        }

        long block = monitorPtr - 8L;
        ForeignMemory.setInt(block, 0);
        ForeignMemory.setInt(block + 4L, -1);
        ForeignMemory.freeNative(block);
    }
}
