package spatial;

import annotation.Draft;
import annotation.Intention;
import annotation.Required;
import nio.ForeignMemory;
import nio.MemoryRegistry;
import oop.TypeRegister;
import struct.List;

import java.lang.foreign.Arena;

/**
 * Off-heap Circular Polar Grid Spatial Partitioning Array implementation.
 */
@Draft
@Intention("Zero-GC off-heap circular polar spatial partitioning array mapping concentric rings and angular slices to coordinate entity lists.")
public final class CircularArray {

    @Required
    public static final int CLASS_ID = TypeRegister.ID_CIRCULAR_ARRAY;

    public static final int TYPE_CIRCULAR_ARRAY = TypeRegister.FORM_ARRAY | CLASS_ID; // 0xBB000028

    private static final long HEADER_SIZE = 40L; // 8B metadata header + 32B slot layout
    private static final double PI2 = Math.PI * 2.0;

    private static Arena poolArena;
    private static volatile boolean active;

    static {
        poolArena = Arena.ofShared();
        active = true;
        MemoryRegistry.register(CircularArray::freeAll);
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

    // create a new off-heap polar spatial grid
    public static long instant(float centerX, float centerY, float ringWidth, int numRings, int numSlices) {
        checkActive();
        if (ringWidth <= 0.0f) throw new IllegalArgumentException("ringWidth must be positive!");
        if (numRings <= 0 || numSlices <= 0) throw new IllegalArgumentException("Rings and slices must be positive!");

        long headerBlock = ForeignMemory.allocateNative(HEADER_SIZE);
        long userPtr = headerBlock + 8L;

        ForeignMemory.putInt(headerBlock, TYPE_CIRCULAR_ARRAY);
        ForeignMemory.putInt(headerBlock + 4L, 0); // total size of entities

        ForeignMemory.putFloat(userPtr, centerX);
        ForeignMemory.putFloat(userPtr + 4L, centerY);
        ForeignMemory.putFloat(userPtr + 8L, ringWidth);
        ForeignMemory.putInt(userPtr + 12L, numRings);
        ForeignMemory.putInt(userPtr + 16L, numSlices);
        ForeignMemory.putInt(userPtr + 20L, 0); // padding

        long cellCount = (long) numRings * numSlices;
        long bufferBytes = cellCount * 8L; // array of 64-bit List pointers
        long dataBuffer = ForeignMemory.allocateNative(bufferBytes);
        ForeignMemory.setMemory(dataBuffer, bufferBytes, (byte) 0); // clear cell pointers to 0L

        ForeignMemory.putLong(userPtr + 24L, dataBuffer);

        return userPtr;
    }

    public static float centerX(long circularPtr) {
        return ForeignMemory.getFloat(circularPtr);
    }

    public static float centerY(long circularPtr) {
        return ForeignMemory.getFloat(circularPtr + 4L);
    }

    public static float ringWidth(long circularPtr) {
        return ForeignMemory.getFloat(circularPtr + 8L);
    }

    public static int numRings(long circularPtr) {
        return ForeignMemory.getInt(circularPtr + 12L);
    }

    public static int numSlices(long circularPtr) {
        return ForeignMemory.getInt(circularPtr + 16L);
    }

    public static long dataBuffer(long circularPtr) {
        return ForeignMemory.getLong(circularPtr + 24L);
    }

    public static int size(long circularPtr) {
        return circularPtr == 0L ? 0 : ForeignMemory.getInt(circularPtr - 4L);
    }

    private static int clamp(int val, int min, int max) {
        return val < min ? min : (val > max ? max : val);
    }

    // insert an entity based on its coordinate position (x, y)
    public static synchronized void insert(long circularPtr, float x, float y, long entityId) {
        checkActive();
        checkValid(circularPtr);

        float cx = centerX(circularPtr);
        float cy = centerY(circularPtr);
        float rw = ringWidth(circularPtr);
        int rings = numRings(circularPtr);
        int slices = numSlices(circularPtr);

        float dx = x - cx;
        float dy = y - cy;
        double r = Math.sqrt(dx * dx + dy * dy);
        double theta = Math.atan2(dy, dx);

        // Normalize angle to [0, 2PI]
        if (theta < 0.0) {
            theta += PI2;
        }

        int ir = clamp((int) (r / rw), 0, rings - 1);
        int it = clamp((int) (theta / (PI2 / slices)), 0, slices - 1);

        long dataBuffer = dataBuffer(circularPtr);
        long cellOffset = ((long) ir * slices + it) * 8L;
        long listPtr = ForeignMemory.getLong(dataBuffer + cellOffset);

        if (listPtr == 0L) {
            listPtr = List.instant(TypeRegister.ID_INT64, 4);
            ForeignMemory.putLong(dataBuffer + cellOffset, listPtr);
        }

        List.add(listPtr, entityId);
        ForeignMemory.putInt(circularPtr - 4L, size(circularPtr) + 1);
    }

    // remove an entity based on its coordinates
    public static synchronized boolean remove(long circularPtr, float x, float y, long entityId) {
        checkActive();
        checkValid(circularPtr);

        float cx = centerX(circularPtr);
        float cy = centerY(circularPtr);
        float rw = ringWidth(circularPtr);
        int rings = numRings(circularPtr);
        int slices = numSlices(circularPtr);

        float dx = x - cx;
        float dy = y - cy;
        double r = Math.sqrt(dx * dx + dy * dy);
        double theta = Math.atan2(dy, dx);

        if (theta < 0.0) {
            theta += PI2;
        }

        int ir = clamp((int) (r / rw), 0, rings - 1);
        int it = clamp((int) (theta / (PI2 / slices)), 0, slices - 1);

        long dataBuffer = dataBuffer(circularPtr);
        long cellOffset = ((long) ir * slices + it) * 8L;
        long listPtr = ForeignMemory.getLong(dataBuffer + cellOffset);

        if (listPtr == 0L) return false;

        int listSize = List.size(listPtr);
        for (int i = 0; i < listSize; i++) {
            if (List.get(listPtr, i) == entityId) {
                List.remove(listPtr, i);
                ForeignMemory.putInt(circularPtr - 4L, size(circularPtr) - 1);
                return true;
            }
        }

        return false;
    }

    // update position coordinates
    public static synchronized void update(long circularPtr, float oldX, float oldY, float newX, float newY, long entityId) {
        checkActive();
        checkValid(circularPtr);

        float cx = centerX(circularPtr);
        float cy = centerY(circularPtr);
        float rw = ringWidth(circularPtr);
        int rings = numRings(circularPtr);
        int slices = numSlices(circularPtr);

        // check old cell
        float odx = oldX - cx;
        float ody = oldY - cy;
        double oldR = Math.sqrt(odx * odx + ody * ody);
        double oldTheta = Math.atan2(ody, odx);
        if (oldTheta < 0.0) oldTheta += PI2;

        int oldIr = clamp((int) (oldR / rw), 0, rings - 1);
        int oldIt = clamp((int) (oldTheta / (PI2 / slices)), 0, slices - 1);

        // check new cell
        float ndx = newX - cx;
        float ndy = newY - cy;
        double newR = Math.sqrt(ndx * ndx + ndy * ndy);
        double newTheta = Math.atan2(ndy, ndx);
        if (newTheta < 0.0) newTheta += PI2;

        int newIr = clamp((int) (newR / rw), 0, rings - 1);
        int newIt = clamp((int) (newTheta / (PI2 / slices)), 0, slices - 1);

        if (oldIr == newIr && oldIt == newIt) {
            return; // same cell
        }

        if (remove(circularPtr, oldX, oldY, entityId)) {
            insert(circularPtr, newX, newY, entityId);
        }
    }

    // query all entities inside a concentric ring band [minRadius, maxRadius] and/or angular slices [minAngle, maxAngle]
    // angles should be in radians in range [0, 2PI].
    public static synchronized void query(long circularPtr, float minRadius, float maxRadius, double minAngle, double maxAngle, long resultListPtr) {
        checkActive();
        checkValid(circularPtr);
        if (resultListPtr == 0L) throw new NullPointerException("Result List pointer cannot be NULL!");

        float rw = ringWidth(circularPtr);
        int rings = numRings(circularPtr);
        int slices = numSlices(circularPtr);

        int irMin = clamp((int) (minRadius / rw), 0, rings - 1);
        int irMax = clamp((int) (maxRadius / rw), 0, rings - 1);

        double sliceWidth = PI2 / slices;
        
        // Normalize search angles to [0, 2PI]
        double minAn = minAngle;
        while (minAn < 0.0) minAn += PI2;
        while (minAn >= PI2) minAn -= PI2;

        double maxAn = maxAngle;
        while (maxAn < 0.0) maxAn += PI2;
        while (maxAn >= PI2) maxAn -= PI2;

        int itMin = clamp((int) (minAn / sliceWidth), 0, slices - 1);
        int itMax = clamp((int) (maxAn / sliceWidth), 0, slices - 1);

        long dataBuffer = dataBuffer(circularPtr);

        for (int ir = irMin; ir <= irMax; ir++) {
            if (minAn <= maxAn) {
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
                // sweep wraps around 2PI boundary
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

        ForeignMemory.putInt(headerBlock, 0);
        ForeignMemory.putInt(headerBlock + 4L, -1);
        ForeignMemory.freeNative(headerBlock);
    }

    public static int classId() {
        return CLASS_ID;
    }
}
