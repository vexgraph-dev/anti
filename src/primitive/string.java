package primitive;

import nio.ForeignMemory;
import nio.MemoryRegistry;

import java.lang.foreign.Arena;
import java.lang.invoke.MethodHandles;
import java.lang.invoke.VarHandle;
import java.nio.charset.StandardCharsets;

public final class string {

    public static final int TYPE_SMALL = 0;     // <= 56 UTF-8 Bytes  (64B Slot)
    public static final int TYPE_MEDIUM = 1;    // <= 248 UTF-8 Bytes (256B Slot)
    public static final int TYPE_LARGE = 2;     // <= 1016 UTF-8 Bytes (1024B Slot)
    public static final int TYPE_OVERSIZED = -1; // > 1016 UTF-8 Bytes (C malloc/free)

    private static final int DEFAULT_CAPACITY = 1024;

    private static final long SMALL_SLOT_SIZE = 64L;
    private static final long MEDIUM_SLOT_SIZE = 256L;
    private static final long LARGE_SLOT_SIZE = 1024L;

    private static final VarHandle SMALL_FREE_HEAD_VH;
    private static final VarHandle MEDIUM_FREE_HEAD_VH;
    private static final VarHandle LARGE_FREE_HEAD_VH;

    private static final VarHandle SMALL_EXPANDING_VH;
    private static final VarHandle MEDIUM_EXPANDING_VH;
    private static final VarHandle LARGE_EXPANDING_VH;

    private static volatile int smallExpanding = 0;
    private static volatile int mediumExpanding = 0;
    private static volatile int largeExpanding = 0;

    private static Arena poolArena;
    private static volatile boolean active;

    // Top 16 bits = 16-bit Generation Tag, Bottom 48 bits = Raw Memory Pointer
    private static volatile long smallFreeHead;
    private static volatile long mediumFreeHead;
    private static volatile long largeFreeHead;

    static {
        try {
            MethodHandles.Lookup lookup = MethodHandles.lookup();
            SMALL_FREE_HEAD_VH = lookup.findStaticVarHandle(string.class, "smallFreeHead", long.class);
            MEDIUM_FREE_HEAD_VH = lookup.findStaticVarHandle(string.class, "mediumFreeHead", long.class);
            LARGE_FREE_HEAD_VH = lookup.findStaticVarHandle(string.class, "largeFreeHead", long.class);

            SMALL_EXPANDING_VH = lookup.findStaticVarHandle(string.class, "smallExpanding", int.class);
            MEDIUM_EXPANDING_VH = lookup.findStaticVarHandle(string.class, "mediumExpanding", int.class);
            LARGE_EXPANDING_VH = lookup.findStaticVarHandle(string.class, "largeExpanding", int.class);
        } catch (ReflectiveOperationException e) {
            throw new ExceptionInInitializerError(e);
        }

        poolArena = Arena.ofShared();
        active = true;

        MemoryRegistry.register(string::freeAll);

        expandSmallPool();
        expandMediumPool();
        expandLargePool();
    }

    private string() {}

    private static void checkActive() {
        if (!active) throw new IllegalStateException("string subsystem is not active!");
    }

    public static void freeAll() {
        if (active) {
            active = false;
            if (poolArena != null && poolArena.scope().isAlive()) {
                poolArena.close();
            }
        }
    }

    // --- POOL EXPANSIONS ---
    private static void expandSmallPool() {
        long totalBytes = DEFAULT_CAPACITY * SMALL_SLOT_SIZE;
        long baseAddress = poolArena.allocate(totalBytes, 8).address();

        for (int i = 0; i < DEFAULT_CAPACITY; i++) {
            long currentBlock = baseAddress + (i * SMALL_SLOT_SIZE);
            long userPtr = currentBlock + 8L;

            while (true) {
                long oldTagged = smallFreeHead;
                long oldRawHead = oldTagged & 0x0000FFFFFFFFFFFFL;

                ForeignMemory.putLong(userPtr, oldRawHead);

                long nextGen = ((oldTagged >>> 48) + 1L) & 0xFFFFL;
                long newTagged = (nextGen << 48) | (userPtr & 0x0000FFFFFFFFFFFFL);

                if (SMALL_FREE_HEAD_VH.compareAndSet(oldTagged, newTagged)) break;
            }
        }
    }

    private static void expandMediumPool() {
        long totalBytes = DEFAULT_CAPACITY * MEDIUM_SLOT_SIZE;
        long baseAddress = poolArena.allocate(totalBytes, 8).address();

        for (int i = 0; i < DEFAULT_CAPACITY; i++) {
            long currentBlock = baseAddress + (i * MEDIUM_SLOT_SIZE);
            long userPtr = currentBlock + 8L;

            while (true) {
                long oldTagged = mediumFreeHead;
                long oldRawHead = oldTagged & 0x0000FFFFFFFFFFFFL;

                ForeignMemory.putLong(userPtr, oldRawHead);

                long nextGen = ((oldTagged >>> 48) + 1L) & 0xFFFFL;
                long newTagged = (nextGen << 48) | (userPtr & 0x0000FFFFFFFFFFFFL);

                if (MEDIUM_FREE_HEAD_VH.compareAndSet(oldTagged, newTagged)) break;
            }
        }
    }

    private static void expandLargePool() {
        long totalBytes = DEFAULT_CAPACITY * LARGE_SLOT_SIZE;
        long baseAddress = poolArena.allocate(totalBytes, 8).address();

        for (int i = 0; i < DEFAULT_CAPACITY; i++) {
            long currentBlock = baseAddress + (i * LARGE_SLOT_SIZE);
            long userPtr = currentBlock + 8L;

            while (true) {
                long oldTagged = largeFreeHead;
                long oldRawHead = oldTagged & 0x0000FFFFFFFFFFFFL;

                ForeignMemory.putLong(userPtr, oldRawHead);

                long nextGen = ((oldTagged >>> 48) + 1L) & 0xFFFFL;
                long newTagged = (nextGen << 48) | (userPtr & 0x0000FFFFFFFFFFFFL);

                if (LARGE_FREE_HEAD_VH.compareAndSet(oldTagged, newTagged)) break;
            }
        }
    }

    // --- ALLOCATION LAYER ---
    public static long allocate(String value) {
        if (value == null) return 0L;
        byte[] bytes = value.getBytes(StandardCharsets.UTF_8);
        return allocate(bytes);
    }

    public static long allocate(byte[] bytes) {
        checkActive();
        if (bytes == null) return 0L;
        int len = bytes.length;

        long pointer = allocateUninitialized(len);
        ForeignMemory.copyFromHeap(bytes, 0, pointer, len);
        ForeignMemory.putByte(pointer + len, (byte) 0); // null-terminator
        return pointer;
    }

    private static long allocateUninitialized(int len) {
        long pointer;
        if (len <= 56) {
            pointer = popPool(SMALL_FREE_HEAD_VH, SMALL_EXPANDING_VH, TYPE_SMALL, len);
        } else if (len <= 248) {
            pointer = popPool(MEDIUM_FREE_HEAD_VH, MEDIUM_EXPANDING_VH, TYPE_MEDIUM, len);
        } else if (len <= 1016) {
            pointer = popPool(LARGE_FREE_HEAD_VH, LARGE_EXPANDING_VH, TYPE_LARGE, len);
        } else {
            // Oversized: C malloc downcall (0% GC)
            long totalBytes = 8L + len + 1L;
            long alignedBytes = (totalBytes + 7L) & ~7L;
            long base = ForeignMemory.allocateNative(alignedBytes);
            ForeignMemory.putInt(base, len);
            ForeignMemory.putInt(base + 4L, TYPE_OVERSIZED);
            pointer = base + 8L;
        }
        return pointer;
    }

    private static long popPool(VarHandle headVh, VarHandle expandingVh, int type, int len) {
        while (true) {
            long oldTagged = (long) headVh.getVolatile();
            long rawHead = oldTagged & 0x0000FFFFFFFFFFFFL;

            if (rawHead == 0L) {
                if (expandingVh.compareAndSet(0, 1)) {
                    if (type == TYPE_SMALL) expandSmallPool();
                    else if (type == TYPE_MEDIUM) expandMediumPool();
                    else if (type == TYPE_LARGE) expandLargePool();
                    expandingVh.setVolatile(0);
                } else {
                    Thread.onSpinWait();
                }
                continue;
            }

            long nextRawHead = ForeignMemory.getLong(rawHead);
            long nextGen = ((oldTagged >>> 48) + 1L) & 0xFFFFL;
            long newTagged = (nextGen << 48) | (nextRawHead & 0x0000FFFFFFFFFFFFL);

            if (headVh.compareAndSet(oldTagged, newTagged)) {
                long base = rawHead - 8L;
                ForeignMemory.putInt(base, len);
                ForeignMemory.putInt(base + 4L, type);
                return rawHead;
            }
        }
    }

    // --- RECYCLING LAYER ---
    public static void free(long pointer) {
        checkActive();
        if (pointer == 0L) return;

        int type = type(pointer);
        long base = pointer - 8L;

        ForeignMemory.putInt(base, 0);
        ForeignMemory.putInt(base + 4L, -1);

        if (type == TYPE_OVERSIZED) {
            // Oversized: Free back to OS immediately via C free()
            ForeignMemory.freeNative(base);
            return;
        }

        VarHandle headVh = (type == TYPE_SMALL) ? SMALL_FREE_HEAD_VH :
                           (type == TYPE_MEDIUM) ? MEDIUM_FREE_HEAD_VH : LARGE_FREE_HEAD_VH;

        while (true) {
            long oldTagged = (long) headVh.getVolatile();
            long oldRawHead = oldTagged & 0x0000FFFFFFFFFFFFL;

            ForeignMemory.putLong(pointer, oldRawHead);

            long nextGen = ((oldTagged >>> 48) + 1L) & 0xFFFFL;
            long newTagged = (nextGen << 48) | (pointer & 0x0000FFFFFFFFFFFFL);

            if (headVh.compareAndSet(oldTagged, newTagged)) return;
        }
    }

    // --- ACCESSORS ---
    public static String get(long pointer) {
        checkActive();
        if (pointer == 0L) return null;
        return ForeignMemory.getString(pointer);
    }

    public static int length(long pointer) { return ForeignMemory.getInt(pointer - 8L); }
    public static int type(long pointer) { return ForeignMemory.getInt(pointer - 4L); }

    public static int capacity(long pointer) {
        int t = type(pointer);
        if (t == TYPE_SMALL) return 56;
        if (t == TYPE_MEDIUM) return 248;
        if (t == TYPE_LARGE) return 1016;
        return length(pointer);
    }

    // --- COPY & APPEND LAYER ---
    public static long copy(long srcPtr) {
        checkActive();
        if (srcPtr == 0L) return 0L;
        int len = length(srcPtr);
        long newPtr = allocateUninitialized(len);
        ForeignMemory.copy(srcPtr, newPtr, len);
        ForeignMemory.putByte(newPtr + len, (byte) 0);
        return newPtr;
    }

    public static long append(long destPtr, String value) {
        if (value == null || value.isEmpty()) return destPtr;
        return append(destPtr, value.getBytes(StandardCharsets.UTF_8));
    }

    public static long append(long destPtr, byte[] bytes) {
        if (bytes == null || bytes.length == 0) return destPtr;
        return append(destPtr, bytes, 0, bytes.length);
    }

    public static long append(long destPtr, byte[] bytes, int offset, int length) {
        checkActive();
        if (destPtr == 0L) return allocate(bytes);
        if (bytes == null || length <= 0) return destPtr;

        int oldLen = length(destPtr);
        int newLen = oldLen + length;
        int maxCap = capacity(destPtr);

        if (type(destPtr) != TYPE_OVERSIZED && newLen <= maxCap) {
            // In-place append! 0 allocation & 0 pointer relocation
            ForeignMemory.copyFromHeap(bytes, offset, destPtr + oldLen, length);
            ForeignMemory.putByte(destPtr + newLen, (byte) 0);
            ForeignMemory.putInt(destPtr - 8L, newLen);
            return destPtr;
        } else {
            // Reallocate to larger slot/pool and recycle old slot
            long newPtr = allocateUninitialized(newLen);
            ForeignMemory.copy(destPtr, newPtr, oldLen);
            ForeignMemory.copyFromHeap(bytes, offset, newPtr + oldLen, length);
            ForeignMemory.putByte(newPtr + newLen, (byte) 0);
            free(destPtr);
            return newPtr;
        }
    }

    public static long append(long destPtr, long srcPtr) {
        checkActive();
        if (destPtr == 0L) return copy(srcPtr);
        if (srcPtr == 0L) return destPtr;

        int oldLen = length(destPtr);
        int srcLen = length(srcPtr);
        int newLen = oldLen + srcLen;
        int maxCap = capacity(destPtr);

        if (type(destPtr) != TYPE_OVERSIZED && newLen <= maxCap) {
            // In-place append! 0 allocation & 0 pointer relocation
            ForeignMemory.copy(srcPtr, destPtr + oldLen, srcLen);
            ForeignMemory.putByte(destPtr + newLen, (byte) 0);
            ForeignMemory.putInt(destPtr - 8L, newLen);
            return destPtr;
        } else {
            // Reallocate to larger slot/pool and recycle old slot
            long newPtr = allocateUninitialized(newLen);
            ForeignMemory.copy(destPtr, newPtr, oldLen);
            ForeignMemory.copy(srcPtr, newPtr + oldLen, srcLen);
            ForeignMemory.putByte(newPtr + newLen, (byte) 0);
            free(destPtr);
            return newPtr;
        }
    }

    public static long append(long destPtr, long... srcPointers) {
        checkActive();
        if (srcPointers == null || srcPointers.length == 0) return destPtr;
        long currentPtr = destPtr;
        for (long srcPtr : srcPointers) {
            currentPtr = append(currentPtr, srcPtr);
        }
        return currentPtr;
    }
}