package struct;

import annotation.Intention;
import annotation.Required;
import annotation.Volatile;
import lang.Class;
import nio.ForeignMemory;
import nio.MemoryRegistry;
import oop.TypeRegister;
import util.Hash;

import java.lang.foreign.Arena;

/**
 * Off-heap key-value hash map implementation.
 */
@Volatile
@Intention("Zero-GC off-heap open-addressing hash map with key class inspection, 64-bit hashing, thread-safe memory mutations, and global MemoryRegistry integration.")
public final class Map {

    @Required
    public static final int CLASS_ID = TypeRegister.ID_MAP;

    public static final int TYPE_MAP = TypeRegister.FORM_POINTER | CLASS_ID; // 0xCC000013

    private static final int DEFAULT_CAPACITY = 16;
    private static final float LOAD_FACTOR = 0.75f;
    private static final long HEADER_SIZE = 24L; // 8B metadata header + 16B slot layout
    private static final long SLOT_SIZE = 32L;   // 8B key + 8B val + 8B hash + 8B state (0=empty, 1=occupied, 2=deleted)

    private static final int STATE_EMPTY = 0;
    private static final int STATE_OCCUPIED = 1;
    private static final int STATE_DELETED = 2;

    private static Arena poolArena;
    private static volatile boolean active;

    static {
        poolArena = Arena.ofShared();
        active = true;
        MemoryRegistry.register(Map::freeAll);
    }

    private Map() {}

    private static void checkActive() {
        if (!active) throw new IllegalStateException("Map subsystem is not active!");
    }

    private static void checkValid(long mapPtr) {
        if (mapPtr == 0L) throw new NullPointerException("Accessing NULL off-heap map pointer!");
    }

    // free all subsystem resources
    public static void freeAll() {
        if (active) {
            active = false;
            if (poolArena != null && poolArena.scope().isAlive()) {
                poolArena.close();
            }
        }
    }

    // create empty off-heap map for keyClassId and valClassId
    public static long instant(int keyClassId, int valClassId) {
        return instant(keyClassId, valClassId, DEFAULT_CAPACITY);
    }

    // create empty off-heap map with initial capacity
    public static long instant(int keyClassId, int valClassId, int initialCapacity) {
        checkActive();
        int cap = (initialCapacity <= 0) ? DEFAULT_CAPACITY : highestOneBit(initialCapacity);
        if (cap < 4) cap = 4;

        long headerBlock = ForeignMemory.allocateNative(HEADER_SIZE);
        long userPtr = headerBlock + 8L;

        ForeignMemory.putInt(headerBlock, TYPE_MAP);
        ForeignMemory.putInt(headerBlock + 4L, 0); // activeCount

        ForeignMemory.putInt(userPtr, keyClassId);
        ForeignMemory.putInt(userPtr + 4L, valClassId);
        ForeignMemory.putInt(userPtr + 8L, cap);
        ForeignMemory.putInt(userPtr + 12L, 0); // padding

        long bufferBytes = (long) cap * SLOT_SIZE;
        long dataBuffer = ForeignMemory.allocateNative(bufferBytes);
        ForeignMemory.setMemory(dataBuffer, bufferBytes, (byte) 0);
        ForeignMemory.putLong(userPtr + 16L, dataBuffer);

        return userPtr;
    }

    private static boolean isReferenceClass(int classId) {
        return classId == TypeRegister.ID_STRING || classId >= TypeRegister.ID_LIST;
    }

    // compute 64-bit hash for key based on key class inspection
    private static long computeHash(int keyClassId, long key) {
        if (key == 0L) return 0L;
        if (isReferenceClass(keyClassId) && key >= 4096L) {
            int inspectedClass = Class.getClass(key);
            if (inspectedClass != 0) {
                int len = Class.getLength(key);
                if (len > 0) return Hash.fnv1a64(key, len);
            }
        }
        return Hash.murmur3Mix64(key);
    }

    // compare two keys for equality based on class inspection
    private static boolean keysEqual(int keyClassId, long k1, long k2) {
        if (k1 == k2) return true;
        if (k1 == 0L || k2 == 0L) return false;
        if (isReferenceClass(keyClassId) && k1 >= 4096L && k2 >= 4096L) {
            int c1 = Class.getClass(k1);
            int c2 = Class.getClass(k2);
            if (c1 != 0 && c1 == c2) {
                int len1 = Class.getLength(k1);
                int len2 = Class.getLength(k2);
                if (len1 != len2) return false;
                for (int i = 0; i < len1; i++) {
                    if (ForeignMemory.getByte(k1 + i) != ForeignMemory.getByte(k2 + i)) {
                        return false;
                    }
                }
                return true;
            }
        }
        return false;
    }


    // put key-value entry into map
    public static synchronized void put(long mapPtr, long key, long value) {
        checkActive();
        checkValid(mapPtr);

        int count = size(mapPtr);
        int cap = capacity(mapPtr);
        if (count >= (int) (cap * LOAD_FACTOR)) {
            rehash(mapPtr, cap * 2);
            cap = capacity(mapPtr);
        }

        int keyClassId = keyClassId(mapPtr);
        long hash = computeHash(keyClassId, key);
        long buffer = dataBuffer(mapPtr);
        int mask = cap - 1;
        int idx = (int) (hash & mask);
        int firstDeleted = -1;

        while (true) {
            long slot = buffer + ((long) idx * SLOT_SIZE);
            long st = ForeignMemory.getLong(slot + 24L);

            if (st == STATE_EMPTY) {
                int targetIdx = (firstDeleted != -1) ? firstDeleted : idx;
                long targetSlot = buffer + ((long) targetIdx * SLOT_SIZE);
                ForeignMemory.putLong(targetSlot, key);
                ForeignMemory.putLong(targetSlot + 8L, value);
                ForeignMemory.putLong(targetSlot + 16L, hash);
                ForeignMemory.putLong(targetSlot + 24L, STATE_OCCUPIED);
                ForeignMemory.putInt(mapPtr - 4L, count + 1);
                return;
            } else if (st == STATE_DELETED) {
                if (firstDeleted == -1) firstDeleted = idx;
            } else if (st == STATE_OCCUPIED) {
                long slotHash = ForeignMemory.getLong(slot + 16L);
                long slotKey = ForeignMemory.getLong(slot);
                if (slotHash == hash && keysEqual(keyClassId, slotKey, key)) {
                    ForeignMemory.putLong(slot + 8L, value); // update value
                    return;
                }
            }
            idx = (idx + 1) & mask;
        }
    }

    // get value for key, returns 0L if not found
    public static synchronized long get(long mapPtr, long key) {
        checkValid(mapPtr);
        int cap = capacity(mapPtr);
        if (cap == 0) return 0L;

        int keyClassId = keyClassId(mapPtr);
        long hash = computeHash(keyClassId, key);
        long buffer = dataBuffer(mapPtr);
        int mask = cap - 1;
        int idx = (int) (hash & mask);

        for (int i = 0; i < cap; i++) {
            long slot = buffer + ((long) idx * SLOT_SIZE);
            long st = ForeignMemory.getLong(slot + 24L);

            if (st == STATE_EMPTY) return 0L;
            if (st == STATE_OCCUPIED) {
                long slotHash = ForeignMemory.getLong(slot + 16L);
                long slotKey = ForeignMemory.getLong(slot);
                if (slotHash == hash && keysEqual(keyClassId, slotKey, key)) {
                    return ForeignMemory.getLong(slot + 8L);
                }
            }
            idx = (idx + 1) & mask;
        }
        return 0L;
    }

    // check if map contains key
    public static synchronized boolean containsKey(long mapPtr, long key) {
        checkValid(mapPtr);
        int cap = capacity(mapPtr);
        if (cap == 0) return false;

        int keyClassId = keyClassId(mapPtr);
        long hash = computeHash(keyClassId, key);
        long buffer = dataBuffer(mapPtr);
        int mask = cap - 1;
        int idx = (int) (hash & mask);

        for (int i = 0; i < cap; i++) {
            long slot = buffer + ((long) idx * SLOT_SIZE);
            long st = ForeignMemory.getLong(slot + 24L);

            if (st == STATE_EMPTY) return false;
            if (st == STATE_OCCUPIED) {
                long slotHash = ForeignMemory.getLong(slot + 16L);
                long slotKey = ForeignMemory.getLong(slot);
                if (slotHash == hash && keysEqual(keyClassId, slotKey, key)) {
                    return true;
                }
            }
            idx = (idx + 1) & mask;
        }
        return false;
    }

    // remove key entry from map and return old value
    public static synchronized long remove(long mapPtr, long key) {
        checkValid(mapPtr);
        int cap = capacity(mapPtr);
        if (cap == 0) return 0L;

        int keyClassId = keyClassId(mapPtr);
        long hash = computeHash(keyClassId, key);
        long buffer = dataBuffer(mapPtr);
        int mask = cap - 1;
        int idx = (int) (hash & mask);

        for (int i = 0; i < cap; i++) {
            long slot = buffer + ((long) idx * SLOT_SIZE);
            long st = ForeignMemory.getLong(slot + 24L);

            if (st == STATE_EMPTY) return 0L;
            if (st == STATE_OCCUPIED) {
                long slotHash = ForeignMemory.getLong(slot + 16L);
                long slotKey = ForeignMemory.getLong(slot);
                if (slotHash == hash && keysEqual(keyClassId, slotKey, key)) {
                    long oldVal = ForeignMemory.getLong(slot + 8L);
                    ForeignMemory.putLong(slot + 24L, STATE_DELETED);
                    int count = size(mapPtr);
                    ForeignMemory.putInt(mapPtr - 4L, count - 1);
                    return oldVal;
                }
            }
            idx = (idx + 1) & mask;
        }
        return 0L;
    }

    private static void rehash(long mapPtr, int newCap) {
        int oldCap = capacity(mapPtr);
        long oldBuffer = dataBuffer(mapPtr);
        long newBufferBytes = (long) newCap * SLOT_SIZE;
        long newBuffer = ForeignMemory.allocateNative(newBufferBytes);
        ForeignMemory.setMemory(newBuffer, newBufferBytes, (byte) 0);

        int mask = newCap - 1;
        for (int i = 0; i < oldCap; i++) {
            long slot = oldBuffer + ((long) i * SLOT_SIZE);
            if (ForeignMemory.getLong(slot + 24L) == STATE_OCCUPIED) {
                long key = ForeignMemory.getLong(slot);
                long val = ForeignMemory.getLong(slot + 8L);
                long hash = ForeignMemory.getLong(slot + 16L);

                int idx = (int) (hash & mask);
                while (ForeignMemory.getLong(newBuffer + ((long) idx * SLOT_SIZE) + 24L) == STATE_OCCUPIED) {
                    idx = (idx + 1) & mask;
                }
                long targetSlot = newBuffer + ((long) idx * SLOT_SIZE);
                ForeignMemory.putLong(targetSlot, key);
                ForeignMemory.putLong(targetSlot + 8L, val);
                ForeignMemory.putLong(targetSlot + 16L, hash);
                ForeignMemory.putLong(targetSlot + 24L, STATE_OCCUPIED);
            }
        }

        ForeignMemory.freeNative(oldBuffer);
        ForeignMemory.putLong(mapPtr + 16L, newBuffer);
        ForeignMemory.putInt(mapPtr + 8L, newCap);
    }

    private static int highestOneBit(int i) {
        return Integer.highestOneBit(i - 1) << 1;
    }

    // check if map is empty
    public static boolean isEmpty(long mapPtr) {
        return Collection.isEmpty(mapPtr);
    }

    // get element count
    public static int size(long mapPtr) {
        return Collection.size(mapPtr);
    }

    // free map data buffer and header back to native RAM
    public static synchronized void free(long mapPtr) {
        checkActive();
        if (mapPtr == 0L) return;

        long headerBlock = mapPtr - 8L;
        int type = ForeignMemory.getInt(headerBlock);
        if (type == 0 || !TypeRegister.isPointer(type)) {
            throw new IllegalStateException("Double free or corrupt map pointer: 0x" + Long.toHexString(mapPtr).toUpperCase());
        }

        long buffer = dataBuffer(mapPtr);
        if (buffer != 0L) {
            ForeignMemory.freeNative(buffer);
        }

        ForeignMemory.putInt(headerBlock, 0);
        ForeignMemory.putInt(headerBlock + 4L, -1);
        ForeignMemory.freeNative(headerBlock);
    }

    public static int keyClassId(long mapPtr) {
        return Collection.keyClassId(mapPtr);
    }

    public static int valClassId(long mapPtr) {
        return Collection.valClassId(mapPtr);
    }

    public static int capacity(long mapPtr) {
        return Collection.capacity(mapPtr);
    }

    public static long dataBuffer(long mapPtr) {
        return Collection.dataBuffer(mapPtr);
    }

    public static int classId() {
        return CLASS_ID;
    }
}

