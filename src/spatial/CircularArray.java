package spatial;

import annotation.Volatile;

import annotation.Draft;
import annotation.Intention;
import annotation.Required;
import nio.ForeignMemory;
import oop.TypeRegister;
import struct.List;

import java.lang.foreign.Arena;

/**
 * Off-heap Circular Polar Grid Spatial Partitioning Array implementation.
 * Modified to be Integer Index based (chunk/view based).
 */
@Draft
@Intention("Zero-GC off-heap circular polar spatial partitioning array mapping concentric rings and angular slices by integer indices to coordinate entity lists.")
@Volatile
public final class CircularArray {

    @Required
    public static final int CLASS_ID = TypeRegister.ID_CIRCULAR_ARRAY;

    public static final int TYPE_CIRCULAR_ARRAY = TypeRegister.FORM_ARRAY | CLASS_ID; // 0xBB000028

    private static final long HEADER_SIZE = 24L; // 8B metadata header + 16B slot layout

    private static Arena poolArena;
    private static volatile boolean active;

    static {
        poolArena = Arena.ofShared();
        active = true;
    }

    private CircularArray() {}

    private static void checkActive() {
        if (!active) throw new IllegalStateException("CircularArray subsystem is not active!");
    }

    private static void checkValid(long circularPtr) {
        if (circularPtr == 0L) throw new NullPointerException("Accessing NULL off-heap CircularArray pointer!");
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

    // create a new off-heap index-based polar spatial grid
    public static long instant(int numRings, int numSlices) {
        checkActive();
        if (numRings <= 0 || numSlices <= 0) throw new IllegalArgumentException("Rings and slices must be positive!");

        long headerBlock = ForeignMemory.allocateNative(HEADER_SIZE);
        long userPtr = headerBlock + 8L;

        ForeignMemory.setInt(headerBlock, TYPE_CIRCULAR_ARRAY);
        ForeignMemory.setInt(headerBlock + 4L, 0); // total size of entities

        ForeignMemory.setInt(userPtr, numRings);
        ForeignMemory.setInt(userPtr + 4L, numSlices);

        long cellCount = (long) numRings * numSlices;
        long bufferBytes = cellCount * 8L; // array of 64-bit List pointers
        long dataBuffer = ForeignMemory.allocateNative(bufferBytes);
        ForeignMemory.setMemory(dataBuffer, bufferBytes, (byte) 0); // clear cell pointers to 0L

        ForeignMemory.setLong(userPtr + 8L, dataBuffer);

        return userPtr;
    }

    public static int numRings(long circularPtr) {
        return ForeignMemory.getInt(circularPtr);
    }

    public static int numSlices(long circularPtr) {
        return ForeignMemory.getInt(circularPtr + 4L);
    }

    public static long dataBuffer(long circularPtr) {
        return ForeignMemory.getLong(circularPtr + 8L);
    }

    public static int size(long circularPtr) {
        return circularPtr == 0L ? 0 : ForeignMemory.getInt(circularPtr - 4L);
    }

    private static int clamp(int val, int min, int max) {
        return val < min ? min : Math.min(val, max);
    }

    // insert an entity based on its index coordinates
    public static synchronized void insert(long circularPtr, int ring, int slice, long entityId) {
        checkActive();
        checkValid(circularPtr);

        int rings = numRings(circularPtr);
        int slices = numSlices(circularPtr);

        int ir = clamp(ring, 0, rings - 1);
        int it = slice % slices;
        if (it < 0) it += slices;

        long dataBuffer = dataBuffer(circularPtr);
        long cellOffset = ((long) ir * slices + it) * 8L;
        long listPtr = ForeignMemory.getLong(dataBuffer + cellOffset);

        if (listPtr == 0L) {
            listPtr = List.instant(TypeRegister.ID_LONG, 4);
            ForeignMemory.setLong(dataBuffer + cellOffset, listPtr);
        }

        List.add(listPtr, entityId);
        ForeignMemory.setInt(circularPtr - 4L, size(circularPtr) + 1);
    }

    // remove an entity based on its index coordinates
    public static synchronized boolean remove(long circularPtr, int ring, int slice, long entityId) {
        checkActive();
        checkValid(circularPtr);

        int rings = numRings(circularPtr);
        int slices = numSlices(circularPtr);

        int ir = clamp(ring, 0, rings - 1);
        int it = slice % slices;
        if (it < 0) it += slices;

        long dataBuffer = dataBuffer(circularPtr);
        long cellOffset = ((long) ir * slices + it) * 8L;
        long listPtr = ForeignMemory.getLong(dataBuffer + cellOffset);

        if (listPtr == 0L) return false;

        int listSize = List.size(listPtr);
        for (int i = 0; i < listSize; i++) {
            if (List.get(listPtr, i) == entityId) {
                List.remove(listPtr, i);
                ForeignMemory.setInt(circularPtr - 4L, size(circularPtr) - 1);
                return true;
            }
        }

        return false;
    }

    // update index coordinates
    public static synchronized void update(long circularPtr, int oldRing, int oldSlice, int newRing, int newSlice, long entityId) {
        checkActive();
        checkValid(circularPtr);
        
        int rings = numRings(circularPtr);
        int slices = numSlices(circularPtr);
        
        int oIr = clamp(oldRing, 0, rings - 1);
        int oIt = oldSlice % slices;
        if (oIt < 0) oIt += slices;
        
        int nIr = clamp(newRing, 0, rings - 1);
        int nIt = newSlice % slices;
        if (nIt < 0) nIt += slices;

        if (oIr == nIr && oIt == nIt) {
            return; // same cell
        }

        if (remove(circularPtr, oldRing, oldSlice, entityId)) {
            insert(circularPtr, newRing, newSlice, entityId);
        }
    }

    // check if an entity exists anywhere in the polar grid
    public static synchronized boolean contains(long circularPtr, long entityId) {
        checkActive();
        checkValid(circularPtr);

        int rings = numRings(circularPtr);
        int slices = numSlices(circularPtr);
        long cellCount = (long) rings * slices;
        long dataBuffer = dataBuffer(circularPtr);

        for (int i = 0; i < cellCount; i++) {
            long listPtr = ForeignMemory.getLong(dataBuffer + i * 8L);
            if (listPtr != 0L) {
                int listSize = List.size(listPtr);
                for (int j = 0; j < listSize; j++) {
                    if (List.get(listPtr, j) == entityId) return true;
                }
            }
        }
        return false;
    }

    // fetch all entities in the polar grid and append them to resultListPtr
    public static synchronized void getAll(long circularPtr, long resultListPtr) {
        checkActive();
        checkValid(circularPtr);
        if (resultListPtr == 0L) throw new NullPointerException("Result List pointer cannot be NULL!");

        int rings = numRings(circularPtr);
        int slices = numSlices(circularPtr);
        long cellCount = (long) rings * slices;
        long dataBuffer = dataBuffer(circularPtr);

        for (int i = 0; i < cellCount; i++) {
            long listPtr = ForeignMemory.getLong(dataBuffer + i * 8L);
            if (listPtr != 0L) {
                int listSize = List.size(listPtr);
                for (int j = 0; j < listSize; j++) {
                    List.add(resultListPtr, List.get(listPtr, j));
                }
            }
        }
    }

    // clear all entities from the polar grid without deallocating cells
    public static synchronized void clear(long circularPtr) {
        checkActive();
        checkValid(circularPtr);

        int rings = numRings(circularPtr);
        int slices = numSlices(circularPtr);
        long cellCount = (long) rings * slices;
        long dataBuffer = dataBuffer(circularPtr);

        for (int i = 0; i < cellCount; i++) {
            long listPtr = ForeignMemory.getLong(dataBuffer + i * 8L);
            if (listPtr != 0L) {
                ForeignMemory.setInt(listPtr - 4L, 0); // clear the cell list
            }
        }
        ForeignMemory.setInt(circularPtr - 4L, 0); // reset total size
    }

    // query all entities inside a concentric ring index band and/or angular slice index band
    public static synchronized void query(long circularPtr, int minRing, int maxRing, int minSlice, int maxSlice, long resultListPtr) {
        checkActive();
        checkValid(circularPtr);
        if (resultListPtr == 0L) throw new NullPointerException("Result List pointer cannot be NULL!");

        int rings = numRings(circularPtr);
        int slices = numSlices(circularPtr);

        int irMin = clamp(minRing, 0, rings - 1);
        int irMax = clamp(maxRing, 0, rings - 1);

        int itMin = minSlice % slices;
        if (itMin < 0) itMin += slices;
        
        int itMax = maxSlice % slices;
        if (itMax < 0) itMax += slices;

        long dataBuffer = dataBuffer(circularPtr);

        for (int ir = irMin; ir <= irMax; ir++) {
            if (minSlice <= maxSlice && itMin <= itMax) {
                // standard slice sweep
                for (int it = itMin; it <= itMax; it++) {
                    long cellOffset = ((long) ir * slices + it) * 8L;
                    long listPtr = ForeignMemory.getLong(dataBuffer + cellOffset);
                    if (listPtr != 0L) {
                        int listSize = List.size(listPtr);
                        for (int i = 0; i < listSize; i++) {
                            List.add(resultListPtr, List.get(listPtr, i));
                        }
                    }
                }
            } else {
                // sweep wraps around the circle
                // Part 1: from itMin to last slice
                for (int it = itMin; it < slices; it++) {
                    long cellOffset = ((long) ir * slices + it) * 8L;
                    long listPtr = ForeignMemory.getLong(dataBuffer + cellOffset);
                    if (listPtr != 0L) {
                        int listSize = List.size(listPtr);
                        for (int i = 0; i < listSize; i++) {
                            List.add(resultListPtr, List.get(listPtr, i));
                        }
                    }
                }
                // Part 2: from slice 0 to itMax
                for (int it = 0; it <= itMax; it++) {
                    long cellOffset = ((long) ir * slices + it) * 8L;
                    long listPtr = ForeignMemory.getLong(dataBuffer + cellOffset);
                    if (listPtr != 0L) {
                        int listSize = List.size(listPtr);
                        for (int i = 0; i < listSize; i++) {
                            List.add(resultListPtr, List.get(listPtr, i));
                        }
                    }
                }
            }
        }
    }

    // free polar spatial grid and internal cell lists
    public static void free(long circularPtr) {
        checkActive();
        if (circularPtr == 0L) return;

        long headerBlock = circularPtr - 8L;
        int type = ForeignMemory.getInt(headerBlock);
        if (type == 0 || (type & TypeRegister.MASK_FORM) != TypeRegister.FORM_ARRAY) {
            throw new IllegalStateException("Double free or corrupt CircularArray pointer: 0x" + Long.toHexString(circularPtr).toUpperCase());
        }

        int rings = numRings(circularPtr);
        int slices = numSlices(circularPtr);
        long cellCount = (long) rings * slices;
        long dataBuffer = dataBuffer(circularPtr);

        if (dataBuffer != 0L) {
            for (int i = 0; i < cellCount; i++) {
                long listPtr = ForeignMemory.getLong(dataBuffer + (long) i * 8L);
                if (listPtr != 0L) {
                    List.free(listPtr);
                }
            }
            ForeignMemory.freeNative(dataBuffer);
        }

        ForeignMemory.setInt(headerBlock, 0);
        ForeignMemory.setInt(headerBlock + 4L, -1);
        ForeignMemory.freeNative(headerBlock);
    }

    public static int classId() {
        return CLASS_ID;
    }
}
