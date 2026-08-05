package struct;

import annotation.Volatile;

import annotation.Intention;
import annotation.Required;
import lang.Class;
import nio.ForeignMemory;
import oop.TypeRegister;
import util.Hash;

import java.lang.foreign.Arena;

/**
 * Off-heap unique element hash set implementation.
 */
@Intention("Zero-GC off-heap open-addressing hash set with element class inspection, 64-bit hashing, thread-safe memory mutations, and global MemoryRegistry integration.")
@Volatile
public final class Set {

    @Required
    public static final int CLASS_ID = TypeRegister.ID_SET;

    public static final int TYPE_SET = TypeRegister.FORM_POINTER | CLASS_ID; // 0xCC000014

    private static final int DEFAULT_CAPACITY = 16;
    private static final float LOAD_FACTOR = 0.75f;
    private static final long HEADER_SIZE = 24L; // 8B metadata header + 16B slot layout
    private static final long SLOT_SIZE = 24L;   // 8B element + 8B hash + 8B state (0=empty, 1=occupied, 2=deleted)

    private static final int STATE_EMPTY = 0;
    private static final int STATE_OCCUPIED = 1;
    private static final int STATE_DELETED = 2;

    private static Arena poolArena;
    private static volatile boolean active;

    static {
        poolArena = Arena.ofShared();
        active = true;
    }

    private Set() {}

    private static void checkActive() {
        if (!active) throw new IllegalStateException("Set subsystem is not active!");
    }

    private static void checkValid(long setPtr) {
        if (setPtr == 0L) throw new NullPointerException("Accessing NULL off-heap set pointer!");
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

    // create empty off-heap set for elementClassId
    public static long instant(int elementClassId) {
        return instant(elementClassId, DEFAULT_CAPACITY);
    }

    // create empty off-heap set with initial capacity
    public static long instant(int elementClassId, int initialCapacity) {
        checkActive();
        int cap = (initialCapacity <= 0) ? DEFAULT_CAPACITY : highestOneBit(initialCapacity);
        if (cap < 4) cap = 4;

        long headerBlock = ForeignMemory.allocateNative(HEADER_SIZE);
        long userPtr = headerBlock + 8L;

        ForeignMemory.setInt(headerBlock, TYPE_SET);
        ForeignMemory.setInt(headerBlock + 4L, 0); // activeCount

        ForeignMemory.setInt(userPtr, elementClassId);
        ForeignMemory.setInt(userPtr + 4L, 0); // padding
        ForeignMemory.setInt(userPtr + 8L, cap);
        ForeignMemory.setInt(userPtr + 12L, 0); // padding

        long bufferBytes = (long) cap * SLOT_SIZE;
        long dataBuffer = ForeignMemory.allocateNative(bufferBytes);
        ForeignMemory.setMemory(dataBuffer, bufferBytes, (byte) 0);
        ForeignMemory.setLong(userPtr + 16L, dataBuffer);

        return userPtr;
    }

    // returns true if this class ID stores off-heap pointers (strings, structs) rather than raw scalar values
    private static boolean isReferenceClass(int classId) {
        return classId == TypeRegister.ID_STRING || classId >= TypeRegister.ID_LIST;
    }

    // compute 64-bit hash for element based on class inspection
    private static long computeHash(int elementClassId, long element) {
        if (element == 0L) return 0L;
        if (isReferenceClass(elementClassId) && element >= 4096L) {
            int inspectedClass = Class.getClass(element);
            if (inspectedClass != 0) {
                int len = Class.getLength(element);
                if (len > 0) return Hash.fnv1a64(element, len);
            }
        }
        return Hash.murmur3Mix64(element);
    }

    // compare two elements for equality based on class inspection
    private static boolean elementsEqual(int elementClassId, long e1, long e2) {
        if (e1 == e2) return true;
        if (e1 == 0L || e2 == 0L) return false;
        if (isReferenceClass(elementClassId) && e1 >= 4096L && e2 >= 4096L) {
            int c1 = Class.getClass(e1);
            int c2 = Class.getClass(e2);
            if (c1 != 0 && c1 == c2) {
                int len1 = Class.getLength(e1);
                int len2 = Class.getLength(e2);
                if (len1 != len2) return false;
                for (int i = 0; i < len1; i++) {
                    if (ForeignMemory.getByte(e1 + i) != ForeignMemory.getByte(e2 + i)) {
                        return false;
                    }
                }
                return true;
            }
        }
        return false;
    }


    // add element to set, returns true if inserted, false if already present
    public static synchronized boolean add(long setPtr, long element) {
        checkActive();
        checkValid(setPtr);

        int count = size(setPtr);
        int cap = capacity(setPtr);
        if (count >= (int) (cap * LOAD_FACTOR)) {
            rehash(setPtr, cap * 2);
            cap = capacity(setPtr);
        }

        int elemClassId = elementClassId(setPtr);
        long hash = computeHash(elemClassId, element);
        long buffer = dataBuffer(setPtr);
        int mask = cap - 1;
        int idx = (int) (hash & mask);
        int firstDeleted = -1;

        while (true) {
            long slot = buffer + ((long) idx * SLOT_SIZE);
            long st = ForeignMemory.getLong(slot + 16L);

            if (st == STATE_EMPTY) {
                int targetIdx = (firstDeleted != -1) ? firstDeleted : idx;
                long targetSlot = buffer + ((long) targetIdx * SLOT_SIZE);
                ForeignMemory.setLong(targetSlot, element);
                ForeignMemory.setLong(targetSlot + 8L, hash);
                ForeignMemory.setLong(targetSlot + 16L, STATE_OCCUPIED);
                ForeignMemory.setInt(setPtr - 4L, count + 1);
                return true;
            } else if (st == STATE_DELETED) {
                if (firstDeleted == -1) firstDeleted = idx;
            } else if (st == STATE_OCCUPIED) {
                long slotHash = ForeignMemory.getLong(slot + 8L);
                long slotElem = ForeignMemory.getLong(slot);
                if (slotHash == hash && elementsEqual(elemClassId, slotElem, element)) {
                    return false; // already present
                }
            }
            idx = (idx + 1) & mask;
        }
    }

    // check if set contains element
    public static synchronized boolean contains(long setPtr, long element) {
        checkValid(setPtr);
        int cap = capacity(setPtr);
        if (cap == 0) return false;

        int elemClassId = elementClassId(setPtr);
        long hash = computeHash(elemClassId, element);
        long buffer = dataBuffer(setPtr);
        int mask = cap - 1;
        int idx = (int) (hash & mask);

        for (int i = 0; i < cap; i++) {
            long slot = buffer + ((long) idx * SLOT_SIZE);
            long st = ForeignMemory.getLong(slot + 16L);

            if (st == STATE_EMPTY) return false;
            if (st == STATE_OCCUPIED) {
                long slotHash = ForeignMemory.getLong(slot + 8L);
                long slotElem = ForeignMemory.getLong(slot);
                if (slotHash == hash && elementsEqual(elemClassId, slotElem, element)) {
                    return true;
                }
            }
            idx = (idx + 1) & mask;
        }
        return false;
    }

    // remove element from set, returns true if removed, false if not found
    public static synchronized boolean remove(long setPtr, long element) {
        checkValid(setPtr);
        int cap = capacity(setPtr);
        if (cap == 0) return false;

        int elemClassId = elementClassId(setPtr);
        long hash = computeHash(elemClassId, element);
        long buffer = dataBuffer(setPtr);
        int mask = cap - 1;
        int idx = (int) (hash & mask);

        for (int i = 0; i < cap; i++) {
            long slot = buffer + ((long) idx * SLOT_SIZE);
            long st = ForeignMemory.getLong(slot + 16L);

            if (st == STATE_EMPTY) return false;
            if (st == STATE_OCCUPIED) {
                long slotHash = ForeignMemory.getLong(slot + 8L);
                long slotElem = ForeignMemory.getLong(slot);
                if (slotHash == hash && elementsEqual(elemClassId, slotElem, element)) {
                    ForeignMemory.setLong(slot + 16L, STATE_DELETED);
                    int count = size(setPtr);
                    ForeignMemory.setInt(setPtr - 4L, count - 1);
                    return true;
                }
            }
            idx = (idx + 1) & mask;
        }
        return false;
    }

    private static void rehash(long setPtr, int newCap) {
        int oldCap = capacity(setPtr);
        long oldBuffer = dataBuffer(setPtr);
        long newBufferBytes = (long) newCap * SLOT_SIZE;
        long newBuffer = ForeignMemory.allocateNative(newBufferBytes);
        ForeignMemory.setMemory(newBuffer, newBufferBytes, (byte) 0);

        int mask = newCap - 1;
        for (int i = 0; i < oldCap; i++) {
            long slot = oldBuffer + ((long) i * SLOT_SIZE);
            if (ForeignMemory.getLong(slot + 16L) == STATE_OCCUPIED) {
                long elem = ForeignMemory.getLong(slot);
                long hash = ForeignMemory.getLong(slot + 8L);

                int idx = (int) (hash & mask);
                while (ForeignMemory.getLong(newBuffer + ((long) idx * SLOT_SIZE) + 16L) == STATE_OCCUPIED) {
                    idx = (idx + 1) & mask;
                }
                long targetSlot = newBuffer + ((long) idx * SLOT_SIZE);
                ForeignMemory.setLong(targetSlot, elem);
                ForeignMemory.setLong(targetSlot + 8L, hash);
                ForeignMemory.setLong(targetSlot + 16L, STATE_OCCUPIED);
            }
        }

        ForeignMemory.freeNative(oldBuffer);
        ForeignMemory.setLong(setPtr + 16L, newBuffer);
        ForeignMemory.setInt(setPtr + 8L, newCap);
    }

    private static int highestOneBit(int i) {
        return Integer.highestOneBit(i - 1) << 1;
    }

    // export all set elements into an off-heap struct.List
    public static synchronized long toList(long setPtr) {
        checkValid(setPtr);
        int elemClassId = elementClassId(setPtr);
        int count = size(setPtr);
        long listPtr = List.instant(elemClassId, count);
        if (count == 0) return listPtr;

        int cap = capacity(setPtr);
        long buffer = dataBuffer(setPtr);
        for (int i = 0; i < cap; i++) {
            long slot = buffer + ((long) i * SLOT_SIZE);
            if (ForeignMemory.getLong(slot + 16L) == STATE_OCCUPIED) {
                long elem = ForeignMemory.getLong(slot);
                List.add(listPtr, elem);
            }
        }
        return listPtr;
    }

    // export all set elements into a sorted off-heap struct.List
    public static synchronized long toSortedList(long setPtr) {
        long listPtr = toList(setPtr);
        int sz = List.size(listPtr);
        if (sz > 1) {
            long buf = List.dataBuffer(listPtr);
            int stride = List.stride(listPtr);
            if (stride == 4) {
                util.Arrays.sortInt(buf, sz);
            } else if (stride == 8) {
                util.Arrays.sortLong(buf, sz);
            }
        }
        return listPtr;
    }


    // check if set is empty
    public static boolean isEmpty(long setPtr) {
        return Collection.isEmpty(setPtr);
    }

    // get active element count
    public static int size(long setPtr) {
        return Collection.size(setPtr);
    }

    // free set data buffer and header back to native RAM
    public static synchronized void free(long setPtr) {
        checkActive();
        if (setPtr == 0L) return;

        long headerBlock = setPtr - 8L;
        int type = ForeignMemory.getInt(headerBlock);
        if (type == 0 || !TypeRegister.isPointer(type)) {
            throw new IllegalStateException("Double free or corrupt set pointer: 0x" + Long.toHexString(setPtr).toUpperCase());
        }

        long buffer = dataBuffer(setPtr);
        if (buffer != 0L) {
            ForeignMemory.freeNative(buffer);
        }

        ForeignMemory.setInt(headerBlock, 0);
        ForeignMemory.setInt(headerBlock + 4L, -1);
        ForeignMemory.freeNative(headerBlock);
    }

    public static int elementClassId(long setPtr) {
        return Collection.elementClassId(setPtr);
    }

    public static int capacity(long setPtr) {
        return Collection.capacity(setPtr);
    }

    public static long dataBuffer(long setPtr) {
        return Collection.dataBuffer(setPtr);
    }

    public static int classId() {
        return CLASS_ID;
    }

}
