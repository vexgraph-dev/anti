package variable;

import annotation.Unsafe;
import annotation.Volatile;

import annotation.Draft;
import annotation.Required;
import nio.ForeignMemory;
import oop.TypeRegister;

import java.lang.foreign.Arena;
import java.lang.foreign.MemorySegment;
import java.nio.charset.StandardCharsets;

/**
 * Off-Heap Relational Variable Subsystem.
 * Draft implementation of a 32-character string symbol registry (always stored lowercase)
 * mapping each registered symbol to an 8-byte long value / target address pointer payload.
 */
@Draft
public final class Variable {

    @Required
    public static final int CLASS_ID = TypeRegister.ID_VARIABLE;

    // 48 bytes slot layout: 32B lowercase name + 4B classId + 4B padding + 8B target pointer payload
    private static final long NAME_SIZE = 32L;
    private static final long SLOT_SIZE = 48L;
    private static final int DEFAULT_CAPACITY = 1024;

    private static final long MAP_SLOT_SIZE = 40L;

    private static Arena poolArena;
    private static long baseAddress;
    private static volatile int capacity;
    private static volatile int activeCount;
    private static volatile boolean active;

    private static volatile long mapAddress;
    private static volatile int mapCapacity;

    static {
        poolArena = Arena.ofShared();
        long initialBytes = DEFAULT_CAPACITY * SLOT_SIZE;
        MemorySegment segment = poolArena.allocate(initialBytes, 8);

        baseAddress = segment.address();
        capacity = DEFAULT_CAPACITY;
        activeCount = 0;
        active = true;

        mapCapacity = DEFAULT_CAPACITY * 2;
        mapAddress = ForeignMemory.allocateNative(mapCapacity * MAP_SLOT_SIZE);
        for (int i = 0; i < mapCapacity; i++)
            ForeignMemory.putIntVolatile(mapAddress + (i * MAP_SLOT_SIZE) + 32L, -1);
    }

    private Variable() {}

    private static void checkActive() {
        if (!active) throw new IllegalStateException("Variable subsystem is not active!");
    }

    private static void checkBounds(int varId) {
        checkActive();
        if (varId < 0 || varId >= activeCount)
            throw new IndexOutOfBoundsException("Variable ID " + varId + " out of bounds for active count " + activeCount);
    }

    // free all subsystem memory resources
    public static void freeAllClasses() {
        if (active) {
            active = false;
            long oldMap = mapAddress;
            if (oldMap != 0L) {
                mapAddress = 0L;
                ForeignMemory.freeNative(oldMap);
            }
            if (poolArena != null && poolArena.scope().isAlive())
                poolArena.close();
        }
    }

    private static int hashName(long l0, long l1, long l2, long l3) {
        long mix = l0 ^ (l1 >>> 7) ^ (l2 << 9) ^ (l3 >>> 13);
        return (int) (mix ^ (mix >>> 32));
    }

    private static void mapInsert(long mapAddr, int mapCap, long l0, long l1, long l2, long l3, int varId) {
        int hash = hashName(l0, l1, l2, l3);
        int index = Math.abs(hash) % mapCap;
        while (true) {
            long slotAddr = mapAddr + (index * MAP_SLOT_SIZE);
            int storedId = ForeignMemory.getIntVolatile(slotAddr + 32L);
            if (storedId == -1 || storedId == varId) {
                ForeignMemory.putLong(slotAddr, l0);
                ForeignMemory.putLong(slotAddr + 8L, l1);
                ForeignMemory.putLong(slotAddr + 16L, l2);
                ForeignMemory.putLong(slotAddr + 24L, l3);
                ForeignMemory.putIntVolatile(slotAddr + 32L, varId);
                break;
            }
            index = (index + 1) % mapCap;
        }
    }

    private static void resizeMap() {
        int newCapacity = mapCapacity * 2;
        long newAddress = ForeignMemory.allocateNative(newCapacity * MAP_SLOT_SIZE);
        for (int i = 0; i < newCapacity; i++)
            ForeignMemory.putIntVolatile(newAddress + (i * MAP_SLOT_SIZE) + 32L, -1);

        for (int i = 0; i < activeCount; i++) {
            long slotAddr = baseAddress + (i * SLOT_SIZE);
            long l0 = ForeignMemory.getLong(slotAddr);
            long l1 = ForeignMemory.getLong(slotAddr + 8L);
            long l2 = ForeignMemory.getLong(slotAddr + 16L);
            long l3 = ForeignMemory.getLong(slotAddr + 24L);
            mapInsert(newAddress, newCapacity, l0, l1, l2, l3, i);
        }

        long oldAddress = mapAddress;
        mapAddress = newAddress;
        mapCapacity = newCapacity;

        if (oldAddress != 0L)
            ForeignMemory.freeNative(oldAddress);
    }

    private static void rebuildMap() {
        for (int i = 0; i < mapCapacity; i++)
            ForeignMemory.putIntVolatile(mapAddress + (i * MAP_SLOT_SIZE) + 32L, -1);

        for (int i = 0; i < activeCount; i++) {
            long slotAddr = baseAddress + (i * SLOT_SIZE);
            long l0 = ForeignMemory.getLong(slotAddr);
            long l1 = ForeignMemory.getLong(slotAddr + 8L);
            long l2 = ForeignMemory.getLong(slotAddr + 16L);
            long l3 = ForeignMemory.getLong(slotAddr + 24L);
            mapInsert(mapAddress, mapCapacity, l0, l1, l2, l3, i);
        }
    }

    // factory functions
    @Draft
    public static int instant(String name, int classId, long targetPointer) {
        if (name == null) return -1;
        return instant(name.getBytes(StandardCharsets.UTF_8), classId, targetPointer);
    }

    @Draft
    public static int instant(char[] nameChars, int classId, long targetPointer) {
        if (nameChars == null) return -1;
        return instant(new String(nameChars).getBytes(StandardCharsets.UTF_8), classId, targetPointer);
    }

    @Draft
    public static int instant(byte[] nameBytes, int classId, long targetPointer) {
        if (nameBytes == null || nameBytes.length > 32)
            return -1;

        checkActive();

        long l0 = packLong(nameBytes, 0);
        long l1 = packLong(nameBytes, 8);
        long l2 = packLong(nameBytes, 16);
        long l3 = packLong(nameBytes, 24);

        synchronized (Variable.class) {
            int existingId = getId(nameBytes);
            if (existingId != -1) {
                long slotAddr = baseAddress + (existingId * SLOT_SIZE);
                ForeignMemory.putInt(slotAddr + 32L, classId);
                ForeignMemory.putLong(slotAddr + 40L, targetPointer);
                return existingId;
            }

            if (activeCount >= capacity) {
                int newCapacity = capacity + DEFAULT_CAPACITY;
                long newBytes = newCapacity * SLOT_SIZE;
                MemorySegment newSegment = poolArena.allocate(newBytes, 8);
                long newBase = newSegment.address();

                ForeignMemory.copy(baseAddress, newBase, activeCount * SLOT_SIZE);
                baseAddress = newBase;
                capacity = newCapacity;
            }

            long targetSlot = baseAddress + (activeCount * SLOT_SIZE);
            ForeignMemory.putLong(targetSlot, l0);
            ForeignMemory.putLong(targetSlot + 8L, l1);
            ForeignMemory.putLong(targetSlot + 16L, l2);
            ForeignMemory.putLong(targetSlot + 24L, l3);

            ForeignMemory.putInt(targetSlot + 32L, classId);
            ForeignMemory.putLong(targetSlot + 40L, targetPointer);

            int assignedId = activeCount;
            activeCount++;

            if (activeCount >= mapCapacity * 0.6)
                resizeMap();
            else
                mapInsert(mapAddress, mapCapacity, l0, l1, l2, l3, assignedId);

            SearchVariable.insert(nameBytes, assignedId);
            return assignedId;
        }
    }

    // draft search lookups
    @Draft
    public static int getId(String name) {
        if (name == null) return -1;
        return getId(name.getBytes(StandardCharsets.UTF_8));
    }

    @Draft
    public static int getId(char[] nameChars) {
        if (nameChars == null) return -1;
        return getId(new String(nameChars).getBytes(StandardCharsets.UTF_8));
    }

    @Draft
    public static int getId(byte[] nameBytes) {
        if (nameBytes == null || nameBytes.length > 32)
            return -1;

        checkActive();
        long l0 = packLong(nameBytes, 0);
        long l1 = packLong(nameBytes, 8);
        long l2 = packLong(nameBytes, 16);
        long l3 = packLong(nameBytes, 24);

        long currentMapAddr = mapAddress;
        int currentCapacity = mapCapacity;
        if (currentMapAddr == 0L)
            return -1;

        int hash = hashName(l0, l1, l2, l3);
        int index = Math.abs(hash) % currentCapacity;

        int limit = currentCapacity;
        for (int i = 0; i < limit; i++) {
            long slotAddr = currentMapAddr + (index * MAP_SLOT_SIZE);
            int storedId = ForeignMemory.getIntVolatile(slotAddr + 32L);
            if (storedId == -1)
                return -1;

            long curL0 = ForeignMemory.getLong(slotAddr);
            long curL1 = ForeignMemory.getLong(slotAddr + 8L);
            long curL2 = ForeignMemory.getLong(slotAddr + 16L);
            long curL3 = ForeignMemory.getLong(slotAddr + 24L);

            if (curL0 == l0 && curL1 == l1 && curL2 == l2 && curL3 == l3)
                return storedId;
            index = (index + 1) % currentCapacity;
        }
        return -1;
    }

    @Draft
    public static long getPointer(String name) {
        if (name == null) return 0L;
        return getPointer(name.getBytes(StandardCharsets.UTF_8));
    }

    @Draft
    public static long getPointer(char[] nameChars) {
        if (nameChars == null) return 0L;
        return getPointer(new String(nameChars).getBytes(StandardCharsets.UTF_8));
    }

    @Draft
    public static long getPointer(byte[] nameBytes) {
        int id = getId(nameBytes);
        if (id == -1) return 0L;
        return getPointer(id);
    }

    @Draft
    public static boolean rename(String oldName, String newName) {
        if (oldName == null || newName == null) return false;
        return rename(oldName.getBytes(StandardCharsets.UTF_8), newName.getBytes(StandardCharsets.UTF_8));
    }

    @Draft
    public static boolean rename(char[] oldNameChars, char[] newNameChars) {
        if (oldNameChars == null || newNameChars == null) return false;
        return rename(new String(oldNameChars).getBytes(StandardCharsets.UTF_8), new String(newNameChars).getBytes(StandardCharsets.UTF_8));
    }

    @Draft
    public static boolean rename(byte[] oldNameBytes, byte[] newNameBytes) {
        if (oldNameBytes == null || newNameBytes == null || oldNameBytes.length > 32 || newNameBytes.length > 32)
            return false;

        checkActive();
        long oldL0 = packLong(oldNameBytes, 0);
        long oldL1 = packLong(oldNameBytes, 8);
        long oldL2 = packLong(oldNameBytes, 16);
        long oldL3 = packLong(oldNameBytes, 24);

        long newL0 = packLong(newNameBytes, 0);
        long newL1 = packLong(newNameBytes, 8);
        long newL2 = packLong(newNameBytes, 16);
        long newL3 = packLong(newNameBytes, 24);

        synchronized (Variable.class) {
            if (getId(newNameBytes) != -1)
                return false;

            int targetIdx = getId(oldNameBytes);
            if (targetIdx == -1)
                return false;

            long targetSlot = baseAddress + (targetIdx * SLOT_SIZE);
            ForeignMemory.putLong(targetSlot, newL0);
            ForeignMemory.putLong(targetSlot + 8L, newL1);
            ForeignMemory.putLong(targetSlot + 16L, newL2);
            ForeignMemory.putLong(targetSlot + 24L, newL3);

            rebuildMap();
            return true;
        }
    }

    // payload accessors
    public static void setPointer(int varId, long targetPointer) {
        checkBounds(varId);
        long slot = baseAddress + (varId * SLOT_SIZE);
        ForeignMemory.putLong(slot + 40L, targetPointer);
    }

    public static long getPointer(int varId) {
        checkBounds(varId);
        return ForeignMemory.getLong(baseAddress + (varId * SLOT_SIZE) + 40L);
    }

    public static long getPointerAddress(int varId) {
        checkBounds(varId);
        return baseAddress + (varId * SLOT_SIZE) + 40L;
    }

    // --- AUTOGENERATED UNSAFE & VOLATILE VARIANTS ---

    @Unsafe
    public static void unsafeSetPointer(int varId, long targetPointer) {
        long slot = baseAddress + (varId * SLOT_SIZE);
        ForeignMemory.putLong(slot + 40L, targetPointer);
    }

    @Unsafe
    public static long unsafeGetPointer(int varId) {
        return ForeignMemory.getLong(baseAddress + (varId * SLOT_SIZE) + 40L);
    }

    @Volatile
    public static void setPointerVolatile(int varId, long targetPointer) {
        checkBounds(varId);
        long slot = baseAddress + (varId * SLOT_SIZE);
        ForeignMemory.putLongVolatile(slot + 40L, targetPointer);
    }

    @Volatile
    public static long getPointerVolatile(int varId) {
        checkBounds(varId);
        return ForeignMemory.getLongVolatile(baseAddress + (varId * SLOT_SIZE) + 40L);
    }

    @Unsafe
    @Volatile
    public static void unsafeVolatileSetPointer(int varId, long targetPointer) {
        long slot = baseAddress + (varId * SLOT_SIZE);
        ForeignMemory.putLongVolatile(slot + 40L, targetPointer);
    }

    @Unsafe
    @Volatile
    public static long unsafeVolatileGetPointer(int varId) {
        return ForeignMemory.getLongVolatile(baseAddress + (varId * SLOT_SIZE) + 40L);
    }

    public static boolean compareAndSetPointer(int varId, long expectedPointer, long newPointer) {
        checkBounds(varId);
        long addr = baseAddress + (varId * SLOT_SIZE) + 40L;
        return ForeignMemory.compareAndSetLong(addr, expectedPointer, newPointer);
    }

    public static int getClassId(int varId) {
        checkBounds(varId);
        return ForeignMemory.getInt(baseAddress + (varId * SLOT_SIZE) + 32L);
    }

    public static String getName(int varId) {
        checkBounds(varId);
        long slot = baseAddress + (varId * SLOT_SIZE);
        byte[] bytes = new byte[32];
        for (int i = 0; i < 4; i++) {
            long l = ForeignMemory.getLong(slot + (i * 8L));
            for (int j = 0; j < 8; j++) {
                byte b = (byte) ((l >> (j * 8)) & 0xFF);
                if (b == 0) {
                    return new String(bytes, 0, i * 8 + j, StandardCharsets.UTF_8);
                }
                bytes[i * 8 + j] = b;
            }
        }
        return new String(bytes, StandardCharsets.UTF_8).trim();
    }

    public static char[] getChars(int varId) {
        String name = getName(varId);
        return name != null ? name.toCharArray() : null;
    }

    public static int getActiveCount() {
        checkActive();
        return activeCount;
    }

    public static int classId() {
        return CLASS_ID;
    }

    // pack 8 bytes into primitive long with lowercase normalization
    private static long packLong(byte[] bytes, int offset) {
        long result = 0L;
        int len = bytes.length;
        for (int i = 0; i < 8; i++) {
            int index = offset + i;
            long b = 0L;
            if (index < len) {
                int rawByte = bytes[index] & 0xFF;
                if (rawByte >= 65 && rawByte <= 90) {
                    rawByte += 32;
                }
                b = rawByte;
            }
            result |= (b << (i * 8));
        }
        return result;
    }
}