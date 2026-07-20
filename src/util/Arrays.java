package util;

import annotation.Draft;
import annotation.Required;
import nio.ForeignMemory;
import oop.TypeRegister;

/**
 * Draft utility for off-heap memory array operations (sorting, searching, filling, copying).
 */
@Draft
public final class Arrays {

    @Required
    public static final int CLASS_ID = TypeRegister.ID_ARRAYS;

    private Arrays() {}

    public static int classId() {
        return CLASS_ID;
    }

    // off-heap int array quicksort
    @Draft
    public static void sortInt(long pointer, int length) {
        if (pointer == 0L || length <= 1) return;
        quickSortInt(pointer, 0, length - 1);
    }

    private static void quickSortInt(long pointer, int low, int high) {
        if (low < high) {
            int pi = partitionInt(pointer, low, high);
            quickSortInt(pointer, low, pi - 1);
            quickSortInt(pointer, pi + 1, high);
        }
    }

    private static int partitionInt(long pointer, int low, int high) {
        int pivot = ForeignMemory.getInt(pointer + (high * 4L));
        int i = low - 1;
        for (int j = low; j < high; j++) {
            if (ForeignMemory.getInt(pointer + (j * 4L)) <= pivot) {
                i++;
                swapInt(pointer, i, j);
            }
        }
        swapInt(pointer, i + 1, high);
        return i + 1;
    }

    private static void swapInt(long pointer, int i, int j) {
        if (i == j) return;
        long p1 = pointer + (i * 4L);
        long p2 = pointer + (j * 4L);
        int temp = ForeignMemory.getInt(p1);
        ForeignMemory.putInt(p1, ForeignMemory.getInt(p2));
        ForeignMemory.putInt(p2, temp);
    }

    // off-heap long array quicksort
    @Draft
    public static void sortLong(long pointer, int length) {
        if (pointer == 0L || length <= 1) return;
        quickSortLong(pointer, 0, length - 1);
    }

    private static void quickSortLong(long pointer, int low, int high) {
        if (low < high) {
            int pi = partitionLong(pointer, low, high);
            quickSortLong(pointer, low, pi - 1);
            quickSortLong(pointer, pi + 1, high);
        }
    }

    private static int partitionLong(long pointer, int low, int high) {
        long pivot = ForeignMemory.getLong(pointer + (high * 8L));
        int i = low - 1;
        for (int j = low; j < high; j++) {
            if (ForeignMemory.getLong(pointer + (j * 8L)) <= pivot) {
                i++;
                swapLong(pointer, i, j);
            }
        }
        swapLong(pointer, i + 1, high);
        return i + 1;
    }

    private static void swapLong(long pointer, int i, int j) {
        if (i == j) return;
        long p1 = pointer + (i * 8L);
        long p2 = pointer + (j * 8L);
        long temp = ForeignMemory.getLong(p1);
        ForeignMemory.putLong(p1, ForeignMemory.getLong(p2));
        ForeignMemory.putLong(p2, temp);
    }

    // off-heap int array binary search
    @Draft
    public static int binarySearchInt(long pointer, int length, int key) {
        if (pointer == 0L) return -1;
        int low = 0;
        int high = length - 1;
        while (low <= high) {
            int mid = (low + high) >>> 1;
            int midVal = ForeignMemory.getInt(pointer + (mid * 4L));
            if (midVal < key) low = mid + 1;
            else if (midVal > key) high = mid - 1;
            else return mid;
        }
        return -(low + 1);
    }

    // off-heap long array binary search
    @Draft
    public static int binarySearchLong(long pointer, int length, long key) {
        if (pointer == 0L) return -1;
        int low = 0;
        int high = length - 1;
        while (low <= high) {
            int mid = (low + high) >>> 1;
            long midVal = ForeignMemory.getLong(pointer + (mid * 8L));
            if (midVal < key) low = mid + 1;
            else if (midVal > key) high = mid - 1;
            else return mid;
        }
        return -(low + 1);
    }

    // off-heap memory block fill
    @Draft
    public static void fill(long pointer, int length, byte value) {
        if (pointer == 0L || length <= 0) return;
        for (int i = 0; i < length; i++) {
            ForeignMemory.putByte(pointer + i, value);
        }
    }

    // off-heap memory block copy
    @Draft
    public static void copy(long srcPointer, long destPointer, long bytes) {
        if (srcPointer == 0L || destPointer == 0L || bytes <= 0) return;
        ForeignMemory.copy(srcPointer, destPointer, bytes);
    }
}
