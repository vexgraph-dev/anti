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
 * Off-heap Uniform 2D Spatial Grid Partitioning Array implementation.
 */
@Draft
@Intention("Zero-GC off-heap uniform 2D spatial grid array partitioning space into cell lists for O(1) insertion, removal, and range queries.")
@Volatile
public final class GridArray {

    @Required
    public static final int CLASS_ID = TypeRegister.ID_GRID_ARRAY;

    public static final int TYPE_GRID_ARRAY = TypeRegister.FORM_ARRAY | CLASS_ID; // 0xBB000024

    private static final long HEADER_SIZE = 40L; // 8B metadata header + 32B slot layout

    private static Arena poolArena;
    private static volatile boolean active;

    static {
        poolArena = Arena.ofShared();
        active = true;
    }

    private GridArray() {}

    private static void checkActive() {
        if (!active) throw new IllegalStateException("GridArray subsystem is not active!");
    }

    private static void checkValid(long gridPtr) {
        if (gridPtr == 0L) throw new NullPointerException("Accessing NULL off-heap GridArray pointer!");
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

    // create a new off-heap 2D spatial grid
    public static long instant(float cellWidth, int resX, int resY, float minBoundsX, float minBoundsY) {
        checkActive();
        if (cellWidth <= 0.0f) throw new IllegalArgumentException("cellWidth must be positive!");
        if (resX <= 0 || resY <= 0) throw new IllegalArgumentException("Resolution must be positive!");

        long headerBlock = ForeignMemory.allocateNative(HEADER_SIZE);
        long userPtr = headerBlock + 8L;

        ForeignMemory.putInt(headerBlock, TYPE_GRID_ARRAY);
        ForeignMemory.putInt(headerBlock + 4L, 0); // total size of entities

        ForeignMemory.putInt(userPtr, resX);
        ForeignMemory.putInt(userPtr + 4L, resY);
        ForeignMemory.putFloat(userPtr + 8L, cellWidth);
        ForeignMemory.putFloat(userPtr + 12L, minBoundsX);
        ForeignMemory.putFloat(userPtr + 16L, minBoundsY);
        ForeignMemory.putInt(userPtr + 20L, 0); // padding

        long cellCount = (long) resX * resY;
        long bufferBytes = cellCount * 8L; // array of 64-bit List pointers
        long dataBuffer = ForeignMemory.allocateNative(bufferBytes);
        ForeignMemory.setMemory(dataBuffer, bufferBytes, (byte) 0); // clear all cell pointers to 0L

        ForeignMemory.putLong(userPtr + 24L, dataBuffer);

        return userPtr;
    }

    public static int resX(long gridPtr) {
        return ForeignMemory.getInt(gridPtr);
    }

    public static int resY(long gridPtr) {
        return ForeignMemory.getInt(gridPtr + 4L);
    }

    public static float cellWidth(long gridPtr) {
        return ForeignMemory.getFloat(gridPtr + 8L);
    }

    public static float minBoundsX(long gridPtr) {
        return ForeignMemory.getFloat(gridPtr + 12L);
    }

    public static float minBoundsY(long gridPtr) {
        return ForeignMemory.getFloat(gridPtr + 16L);
    }

    public static long dataBuffer(long gridPtr) {
        return ForeignMemory.getLong(gridPtr + 24L);
    }

    public static int size(long gridPtr) {
        return gridPtr == 0L ? 0 : ForeignMemory.getInt(gridPtr - 4L);
    }

    private static int clamp(int val, int min, int max) {
        return val < min ? min : (val > max ? max : val);
    }

    // insert an entity into the grid based on (x, y) coordinates
    public static synchronized void insert(long gridPtr, float x, float y, long entityId) {
        checkActive();
        checkValid(gridPtr);

        int rx = resX(gridPtr);
        int ry = resY(gridPtr);
        float w = cellWidth(gridPtr);
        float minX = minBoundsX(gridPtr);
        float minY = minBoundsY(gridPtr);

        int cx = clamp((int) ((x - minX) / w), 0, rx - 1);
        int cy = clamp((int) ((y - minY) / w), 0, ry - 1);

        long dataBuffer = dataBuffer(gridPtr);
        long cellOffset = ((long) cy * rx + cx) * 8L;
        long listPtr = ForeignMemory.getLong(dataBuffer + cellOffset);

        if (listPtr == 0L) {
            // Lazy initialization of the cell's off-heap List of Long pointers
            listPtr = List.instant(TypeRegister.ID_LONG, 4);
            ForeignMemory.putLong(dataBuffer + cellOffset, listPtr);
        }

        List.add(listPtr, entityId);
        ForeignMemory.putInt(gridPtr - 4L, size(gridPtr) + 1);
    }

    // remove an entity from a grid cell based on (x, y) coordinates
    public static synchronized boolean remove(long gridPtr, float x, float y, long entityId) {
        checkActive();
        checkValid(gridPtr);

        int rx = resX(gridPtr);
        int ry = resY(gridPtr);
        float w = cellWidth(gridPtr);
        float minX = minBoundsX(gridPtr);
        float minY = minBoundsY(gridPtr);

        int cx = clamp((int) ((x - minX) / w), 0, rx - 1);
        int cy = clamp((int) ((y - minY) / w), 0, ry - 1);

        long dataBuffer = dataBuffer(gridPtr);
        long cellOffset = ((long) cy * rx + cx) * 8L;
        long listPtr = ForeignMemory.getLong(dataBuffer + cellOffset);

        if (listPtr == 0L) return false;

        int listSize = List.size(listPtr);
        for (int i = 0; i < listSize; i++) {
            if (List.get(listPtr, i) == entityId) {
                List.remove(listPtr, i);
                ForeignMemory.putInt(gridPtr - 4L, size(gridPtr) - 1);
                return true;
            }
        }

        return false;
    }

    // update an entity's coordinates, shifting cells if necessary
    public static synchronized void update(long gridPtr, float oldX, float oldY, float newX, float newY, long entityId) {
        checkActive();
        checkValid(gridPtr);

        int rx = resX(gridPtr);
        int ry = resY(gridPtr);
        float w = cellWidth(gridPtr);
        float minX = minBoundsX(gridPtr);
        float minY = minBoundsY(gridPtr);

        int oldCx = clamp((int) ((oldX - minX) / w), 0, rx - 1);
        int oldCy = clamp((int) ((oldY - minY) / w), 0, ry - 1);
        int newCx = clamp((int) ((newX - minX) / w), 0, rx - 1);
        int newCy = clamp((int) ((newY - minY) / w), 0, ry - 1);

        if (oldCx == newCx && oldCy == newCy) {
            return; // same cell, no change needed
        }

        // Move across cells
        if (remove(gridPtr, oldX, oldY, entityId)) {
            insert(gridPtr, newX, newY, entityId);
        }
    }

    // check if an entity exists anywhere in the grid
    public static synchronized boolean contains(long gridPtr, long entityId) {
        checkActive();
        checkValid(gridPtr);

        int rx = resX(gridPtr);
        int ry = resY(gridPtr);
        long cellCount = (long) rx * ry;
        long dataBuffer = dataBuffer(gridPtr);

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

    // fetch all entities in the grid and append them to resultListPtr
    public static synchronized void getAll(long gridPtr, long resultListPtr) {
        checkActive();
        checkValid(gridPtr);
        if (resultListPtr == 0L) throw new NullPointerException("Result List pointer cannot be NULL!");

        int rx = resX(gridPtr);
        int ry = resY(gridPtr);
        long cellCount = (long) rx * ry;
        long dataBuffer = dataBuffer(gridPtr);

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

    // clear all entities from the grid without deallocating cells
    public static synchronized void clear(long gridPtr) {
        checkActive();
        checkValid(gridPtr);

        int rx = resX(gridPtr);
        int ry = resY(gridPtr);
        long cellCount = (long) rx * ry;
        long dataBuffer = dataBuffer(gridPtr);

        for (int i = 0; i < cellCount; i++) {
            long listPtr = ForeignMemory.getLong(dataBuffer + i * 8L);
            if (listPtr != 0L) {
                ForeignMemory.putInt(listPtr - 4L, 0); // clear the cell list
            }
        }
        ForeignMemory.putInt(gridPtr - 4L, 0); // reset total size
    }

    // query all entities overlapping the bounding box [minX, minY, maxX, maxY]
    // appends matching entity IDs directly to the resultListPtr
    public static synchronized void query(long gridPtr, float minX, float minY, float maxX, float maxY, long resultListPtr) {
        checkActive();
        checkValid(gridPtr);
        if (resultListPtr == 0L) throw new NullPointerException("Result List pointer cannot be NULL!");

        int rx = resX(gridPtr);
        int ry = resY(gridPtr);
        float w = cellWidth(gridPtr);
        float minBux = minBoundsX(gridPtr);
        float minBuy = minBoundsY(gridPtr);

        int cxMin = clamp((int) ((minX - minBux) / w), 0, rx - 1);
        int cxMax = clamp((int) ((maxX - minBux) / w), 0, rx - 1);
        int cyMin = clamp((int) ((minY - minBuy) / w), 0, ry - 1);
        int cyMax = clamp((int) ((maxY - minBuy) / w), 0, ry - 1);

        long dataBuffer = dataBuffer(gridPtr);

        for (int cy = cyMin; cy <= cyMax; cy++) {
            for (int cx = cxMin; cx <= cxMax; cx++) {
                long cellOffset = ((long) cy * rx + cx) * 8L;
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

    // free grid and all internal cell lists
    public static void free(long gridPtr) {
        checkActive();
        if (gridPtr == 0L) return;

        long headerBlock = gridPtr - 8L;
        int type = ForeignMemory.getInt(headerBlock);
        if (type == 0 || (type & TypeRegister.MASK_FORM) != TypeRegister.FORM_ARRAY) {
            throw new IllegalStateException("Double free or corrupt GridArray pointer: 0x" + Long.toHexString(gridPtr).toUpperCase());
        }

        int rx = resX(gridPtr);
        int ry = resY(gridPtr);
        long cellCount = (long) rx * ry;
        long dataBuffer = dataBuffer(gridPtr);

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
