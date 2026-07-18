package relational;

import nio.ForeignMemory;
import java.lang.foreign.Arena;
import java.lang.foreign.MemorySegment;
import java.nio.charset.StandardCharsets;

/**
 * Off-Heap Relational Variable Subsystem.
 * Manages symbol names (up to 32 characters packed into 4 primitive longs) and off-heap typed values
 * (Int, Long, Float, Double, Boolean, Pointer) with 0 GC overhead.
 */
public final class Variable {

    // Type Identifiers
    public static final int TYPE_UNDEFINED = 0;
    public static final int TYPE_INT       = 1;
    public static final int TYPE_LONG      = 2;
    public static final int TYPE_FLOAT     = 3;
    public static final int TYPE_DOUBLE    = 4;
    public static final int TYPE_BOOLEAN   = 5;
    public static final int TYPE_POINTER   = 6;

    // 48 bytes layout per Variable slot:
    //   0..31  (32B): Unique Name String (4 longs * 8 bytes)
    //  32..39  (8B) : Value Payload (64-bit primitive data)
    //  40..43  (4B) : Type Tag
    //  44..47  (4B) : Flags & Padding
    // 32 bytes limit for the unique name string (4 longs * 8 bytes)
    private static final long NAME_SIZE = 32L;
    private static final long SLOT_SIZE = 48L;
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

    public static int register(String name) {
        if (name == null) return -1;
        return register(name.getBytes(StandardCharsets.UTF_8));
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

            // Initialize default value and type
            ForeignMemory.putLong(targetSlot + 32L, 0L);
            ForeignMemory.putInt(targetSlot + 40L, TYPE_UNDEFINED);
            ForeignMemory.putInt(targetSlot + 44L, 0);

            int assignedId = activeCount;
            activeCount++;
            return assignedId;
        }
    }

    public static int getId(String name) {
        if (name == null) return -1;
        return getId(name.getBytes(StandardCharsets.UTF_8));
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

    public static boolean rename(String oldName, String newName) {
        if (oldName == null || newName == null) return false;
        return rename(oldName.getBytes(StandardCharsets.UTF_8), newName.getBytes(StandardCharsets.UTF_8));
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

    // =========================================================================================
    // TYPED VALUE ACCESSORS (ZERO-HEAP ALLOCATION)
    // =========================================================================================

    public static int getType(int varId) {
        if (varId < 0 || varId >= activeCount) return TYPE_UNDEFINED;
        return ForeignMemory.getInt(baseAddress + (varId * SLOT_SIZE) + 40L);
    }

    public static void setInt(int varId, int value) {
        if (varId < 0 || varId >= activeCount) return;
        long slot = baseAddress + (varId * SLOT_SIZE);
        ForeignMemory.putInt(slot + 32L, value);
        ForeignMemory.putInt(slot + 40L, TYPE_INT);
    }

    public static int getInt(int varId) {
        if (varId < 0 || varId >= activeCount) return 0;
        return ForeignMemory.getInt(baseAddress + (varId * SLOT_SIZE) + 32L);
    }

    public static void setLong(int varId, long value) {
        if (varId < 0 || varId >= activeCount) return;
        long slot = baseAddress + (varId * SLOT_SIZE);
        ForeignMemory.putLong(slot + 32L, value);
        ForeignMemory.putInt(slot + 40L, TYPE_LONG);
    }

    public static long getLong(int varId) {
        if (varId < 0 || varId >= activeCount) return 0L;
        return ForeignMemory.getLong(baseAddress + (varId * SLOT_SIZE) + 32L);
    }

    public static void setFloat(int varId, float value) {
        if (varId < 0 || varId >= activeCount) return;
        long slot = baseAddress + (varId * SLOT_SIZE);
        ForeignMemory.putFloat(slot + 32L, value);
        ForeignMemory.putInt(slot + 40L, TYPE_FLOAT);
    }

    public static float getFloat(int varId) {
        if (varId < 0 || varId >= activeCount) return 0.0f;
        return ForeignMemory.getFloat(baseAddress + (varId * SLOT_SIZE) + 32L);
    }

    public static void setDouble(int varId, double value) {
        if (varId < 0 || varId >= activeCount) return;
        long slot = baseAddress + (varId * SLOT_SIZE);
        ForeignMemory.putDouble(slot + 32L, value);
        ForeignMemory.putInt(slot + 40L, TYPE_DOUBLE);
    }

    public static double getDouble(int varId) {
        if (varId < 0 || varId >= activeCount) return 0.0;
        return ForeignMemory.getDouble(baseAddress + (varId * SLOT_SIZE) + 32L);
    }

    public static void setBoolean(int varId, boolean value) {
        if (varId < 0 || varId >= activeCount) return;
        long slot = baseAddress + (varId * SLOT_SIZE);
        ForeignMemory.putInt(slot + 32L, value ? 1 : 0);
        ForeignMemory.putInt(slot + 40L, TYPE_BOOLEAN);
    }

    public static boolean getBoolean(int varId) {
        if (varId < 0 || varId >= activeCount) return false;
        return ForeignMemory.getInt(baseAddress + (varId * SLOT_SIZE) + 32L) != 0;
    }

    public static void setPointer(int varId, long ptr) {
        if (varId < 0 || varId >= activeCount) return;
        long slot = baseAddress + (varId * SLOT_SIZE);
        ForeignMemory.putLong(slot + 32L, ptr);
        ForeignMemory.putInt(slot + 40L, TYPE_POINTER);
    }

    public static long getPointer(int varId) {
        if (varId < 0 || varId >= activeCount) return 0L;
        return ForeignMemory.getLong(baseAddress + (varId * SLOT_SIZE) + 32L);
    }

    public static String getName(int varId) {
        if (varId < 0 || varId >= activeCount) return null;
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
        return activeCount;
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