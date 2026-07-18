package relational;

import nio.ForeignMemory;
import java.lang.foreign.Arena;
import java.lang.foreign.MemorySegment;

public final class Variable {

    // 32 bytes limit for the unique name string (4 longs * 8 bytes)
    private static final long SLOT_SIZE = 32L;
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
    }

    private Variable() {}

    private static void checkActive() {
        if (!active) throw new IllegalStateException("Variable subsystem not active!");
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

    // Register a unique name via raw bytes and return its assigned index, or -1 if invalid/duplicate
    public static int register(byte[] nameBytes) {
        if (nameBytes == null || nameBytes.length > 32) {
            return -1;
        }

        checkActive();

        // Pack name bytes into stack primitives
        long l0 = packLong(nameBytes, 0);
        long l1 = packLong(nameBytes, 8);
        long l2 = packLong(nameBytes, 16);
        long l3 = packLong(nameBytes, 24);

        synchronized (Variable.class) {
            // Scan for duplications
            for (int i = 0; i < activeCount; i++) {
                long slotAddr = baseAddress + (i * SLOT_SIZE);
                if (ForeignMemory.getLong(slotAddr) == l0 &&
                        ForeignMemory.getLong(slotAddr + 8L) == l1 &&
                        ForeignMemory.getLong(slotAddr + 16L) == l2 &&
                        ForeignMemory.getLong(slotAddr + 24L) == l3) {
                    return -1;
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

            // Write name segments back-to-back into off-heap memory
            long targetSlot = baseAddress + (activeCount * SLOT_SIZE);
            ForeignMemory.putLong(targetSlot, l0);
            ForeignMemory.putLong(targetSlot + 8L, l1);
            ForeignMemory.putLong(targetSlot + 16L, l2);
            ForeignMemory.putLong(targetSlot + 24L, l3);

            int assignedId = activeCount;
            activeCount++;
            return assignedId;
        }
    }

    // Look up a name via raw bytes and return its index, or -1 if not found
    public static int getId(byte[] nameBytes) {
        if (nameBytes == null || nameBytes.length > 32) {
            return -1;
        }

        checkActive();
        long l0 = packLong(nameBytes, 0);
        long l1 = packLong(nameBytes, 8);
        long l2 = packLong(nameBytes, 16);
        long l3 = packLong(nameBytes, 24);

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

    // Rename an existing entry using raw byte arrays while preserving its index
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

    // Extract 8 bytes into a stack-allocated primitive long with lowercase normalization
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