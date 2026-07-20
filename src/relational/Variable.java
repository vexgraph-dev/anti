package relational;

import annotation.Draft;
import annotation.Required;
import nio.ForeignMemory;
import nio.MemoryRegistry;
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

    // 40 bytes layout per Variable slot:
    //   0..31  (32B): Unique Name String (4 longs * 8 bytes), always lowercased UTF-8 bytes
    //  32..39  (8B) : Target Address Pointer / Value Payload (8-byte primitive long)
    private static final long NAME_SIZE = 32L;
    private static final long SLOT_SIZE = 40L;
    private static final int DEFAULT_CAPACITY = 1024;

    private static Arena poolArena;
    private static long baseAddress;
    private static volatile int capacity;
    private static volatile int activeCount;
    private static volatile boolean active;

    static {
        poolArena = Arena.ofShared();
        long initialBytes = DEFAULT_CAPACITY * SLOT_SIZE;
        MemorySegment segment = poolArena.allocate(initialBytes, 8);

        baseAddress = segment.address();
        capacity = DEFAULT_CAPACITY;
        activeCount = 0;
        active = true;

        MemoryRegistry.register(Variable::freeAllClasses);
    }

    private Variable() {}

    private static void checkActive() {
        if (!active) throw new IllegalStateException("Variable subsystem is not active!");
    }

    private static void checkBounds(int varId) {
        checkActive();
        if (varId < 0 || varId >= activeCount) {
            throw new IndexOutOfBoundsException("Variable ID " + varId + " out of bounds for active count " + activeCount);
        }
    }

    // Free all allocated off-heap memory resources for this subsystem
    public static void freeAllClasses() {
        if (active) {
            active = false;
            if (poolArena != null && poolArena.scope().isAlive()) {
                poolArena.close();
            }
        }
    }

    // --- FACTORY INSTANTIATION METHODS ---

    @Draft
    public static int instant(String name, long targetPointer) {
        if (name == null) return -1;
        return instant(name.getBytes(StandardCharsets.UTF_8), targetPointer);
    }

    @Draft
    public static int instant(byte[] nameBytes, long targetPointer) {
        if (nameBytes == null || nameBytes.length > 32) {
            return -1;
        }

        checkActive();

        // Pack name bytes into stack primitive longs (always lowercased)
        long l0 = packLong(nameBytes, 0);
        long l1 = packLong(nameBytes, 8);
        long l2 = packLong(nameBytes, 16);
        long l3 = packLong(nameBytes, 24);

        synchronized (Variable.class) {
            // Draft O(n) search scan for duplicate name check
            for (int i = 0; i < activeCount; i++) {
                long slotAddr = baseAddress + (i * SLOT_SIZE);
                if (ForeignMemory.getLong(slotAddr) == l0 &&
                        ForeignMemory.getLong(slotAddr + 8L) == l1 &&
                        ForeignMemory.getLong(slotAddr + 16L) == l2 &&
                        ForeignMemory.getLong(slotAddr + 24L) == l3) {
                    // Symbol already registered; update its bound pointer payload
                    ForeignMemory.putLong(slotAddr + 32L, targetPointer);
                    return i;
                }
            }

            // Expand storage block dynamically if capacity boundary is reached
            if (activeCount >= capacity) {
                int newCapacity = capacity + DEFAULT_CAPACITY;
                long newBytes = newCapacity * SLOT_SIZE;
                MemorySegment newSegment = poolArena.allocate(newBytes, 8);
                long newBase = newSegment.address();

                ForeignMemory.copy(baseAddress, newBase, activeCount * SLOT_SIZE);
                baseAddress = newBase;
                capacity = newCapacity;
            }

            // Write 32-byte lowercased name segments into off-heap memory
            long targetSlot = baseAddress + (activeCount * SLOT_SIZE);
            ForeignMemory.putLong(targetSlot, l0);
            ForeignMemory.putLong(targetSlot + 8L, l1);
            ForeignMemory.putLong(targetSlot + 16L, l2);
            ForeignMemory.putLong(targetSlot + 24L, l3);

            // Bind the 8-byte target address pointer payload
            ForeignMemory.putLong(targetSlot + 32L, targetPointer);

            int assignedId = activeCount;
            activeCount++;
            return assignedId;
        }
    }

    // --- DRAFT O(N) LINEAR SEARCH LOOKUPS ---

    @Draft
    public static int getId(String name) {
        if (name == null) return -1;
        return getId(name.getBytes(StandardCharsets.UTF_8));
    }

    @Draft
    public static int getId(byte[] nameBytes) {
        if (nameBytes == null || nameBytes.length > 32) {
            return -1;
        }

        checkActive();
        long l0 = packLong(nameBytes, 0);
        long l1 = packLong(nameBytes, 8);
        long l2 = packLong(nameBytes, 16);
        long l3 = packLong(nameBytes, 24);

        // O(n) sequential scan comparing 4 packed longs off-heap
        for (int i = 0; i < activeCount; i++) {
            long slotAddr = baseAddress + (i * SLOT_SIZE);
            if (ForeignMemory.getLong(slotAddr) == l0 &&
                    ForeignMemory.getLong(slotAddr + 8L) == l1 &&
                    ForeignMemory.getLong(slotAddr + 16L) == l2 &&
                    ForeignMemory.getLong(slotAddr + 24L) == l3) {
                return i;
            }
        }
        return -1;
    }

    @Draft
    public static long getPointer(String name) {
        if (name == null) return 0L;
        return getPointer(name.getBytes(StandardCharsets.UTF_8));
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
    public static boolean rename(byte[] oldNameBytes, byte[] newNameBytes) {
        if (oldNameBytes == null || newNameBytes == null || oldNameBytes.length > 32 || newNameBytes.length > 32) {
            return false;
        }

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
            int targetIdx = -1;

            for (int i = 0; i < activeCount; i++) {
                long slotAddr = baseAddress + (i * SLOT_SIZE);
                long currentL0 = ForeignMemory.getLong(slotAddr);
                long currentL1 = ForeignMemory.getLong(slotAddr + 8L);
                long currentL2 = ForeignMemory.getLong(slotAddr + 16L);
                long currentL3 = ForeignMemory.getLong(slotAddr + 24L);

                if (currentL0 == newL0 && currentL1 == newL1 && currentL2 == newL2 && currentL3 == newL3) {
                    return false; // New name already exists
                }
                if (currentL0 == oldL0 && currentL1 == oldL1 && currentL2 == oldL2 && currentL3 == oldL3) {
                    targetIdx = i;
                }
            }

            if (targetIdx == -1) return false;

            long targetSlot = baseAddress + (targetIdx * SLOT_SIZE);
            ForeignMemory.putLong(targetSlot, newL0);
            ForeignMemory.putLong(targetSlot + 8L, newL1);
            ForeignMemory.putLong(targetSlot + 16L, newL2);
            ForeignMemory.putLong(targetSlot + 24L, newL3);
            return true;
        }
    }

    // =========================================================================================
    // 8-BYTE VALUE / POINTER PAYLOAD ACCESSORS
    // =========================================================================================

    public static void setPointer(int varId, long targetPointer) {
        checkBounds(varId);
        long slot = baseAddress + (varId * SLOT_SIZE);
        ForeignMemory.putLong(slot + 32L, targetPointer);
    }

    public static long getPointer(int varId) {
        checkBounds(varId);
        return ForeignMemory.getLong(baseAddress + (varId * SLOT_SIZE) + 32L);
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

    public static int getActiveCount() {
        checkActive();
        return activeCount;
    }

    public static int classId() {
        return CLASS_ID;
    }

    // Extract 8 bytes into a stack-allocated primitive long with lowercase normalization
    private static long packLong(byte[] bytes, int offset) {
        long result = 0L;
        int len = bytes.length;
        for (int i = 0; i < 8; i++) {
            int index = offset + i;
            long b = 0L;
            if (index < len) {
                int rawByte = bytes[index] & 0xFF;
                // Force lowercase: convert uppercase A-Z (65-90) to a-z (+32)
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