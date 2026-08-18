package nio;

import annotation.HotCode;
import annotation.Intention;
import annotation.Required;
import thread.Atomic;

import java.lang.foreign.MemorySegment;
import java.nio.charset.StandardCharsets;

@Intention("Off-heap string registry indexed by flat integer IDs loaded from StringLookup.ini; zero image-heap string literals")
public final class StringLookup {

    private static final int DEFAULT_INITIAL_CAPACITY = 256;
    private static final long ENTRY_STRIDE = 16L; // [8-byte ptr, 4-byte len, 4-byte pad]
    private static final int OFF_PTR = 0;
    private static final int OFF_LEN = 8;

    private static final long BOOT_GUARD_PTR = Atomic.allocateInt(0);
    private static final long ACTIVE_PTR = Atomic.allocateBool(false);

    private static volatile long tablePtr = 0L;
    private static volatile int tableCapacity = 0;

    static {
        boot();
    }

    private StringLookup() {}

    public static void boot() {
        boot(null);
    }

    public static void boot(String filePath) {
        if (ForeignMemory.getVolatileByte(ACTIVE_PTR) != 0 && tablePtr != 0L) {
            return;
        }

        if (!ForeignMemory.compareAndSetInt(BOOT_GUARD_PTR, 0, 1)) {
            while (ForeignMemory.getVolatileByte(ACTIVE_PTR) == 0 && ForeignMemory.getVolatileInt(BOOT_GUARD_PTR) == 1) {
                Thread.onSpinWait();
            }
            return;
        }

        // Default lookup locations if filePath is null
        String path = filePath;
        if (path == null) {
            char[] defPath = new char[]{'S', 't', 'r', 'i', 'n', 'g', 'L', 'o', 'o', 'k', 'u', 'p', '.', 'i', 'n', 'i'};
            path = String.valueOf(defPath);
        }

        long handle = ForeignMemory.fileOpen(path, ForeignMemory.FILE_MODE_READ);
        if (handle == 0L) {
            // Attempt to open from current directory / project root fallback
            char[] altPath = new char[]{'.', '/', 'S', 't', 'r', 'i', 'n', 'g', 'L', 'o', 'o', 'k', 'u', 'p', '.', 'i', 'n', 'i'};
            handle = ForeignMemory.fileOpen(String.valueOf(altPath), ForeignMemory.FILE_MODE_READ);
        }

        if (handle == 0L) {
            // Initialize minimal fallback table containing empty string at ID 0
            initEmptyTable(DEFAULT_INITIAL_CAPACITY);
            ForeignMemory.setVolatileByte(ACTIVE_PTR, (byte) 1);
            ForeignMemory.setVolatileInt(BOOT_GUARD_PTR, 2);
            return;
        }

        long fileSize = ForeignMemory.fileSize(handle);
        if (fileSize <= 0L) {
            ForeignMemory.fileClose(handle);
            initEmptyTable(DEFAULT_INITIAL_CAPACITY);
            ForeignMemory.setVolatileByte(ACTIVE_PTR, (byte) 1);
            ForeignMemory.setVolatileInt(BOOT_GUARD_PTR, 2);
            return;
        }

        long rawBuf = ForeignMemory.allocateNative(fileSize + 1L);
        long readBytes = ForeignMemory.fileRead(handle, rawBuf, fileSize);
        ForeignMemory.fileClose(handle);

        if (readBytes <= 0L) {
            ForeignMemory.freeNative(rawBuf);
            initEmptyTable(DEFAULT_INITIAL_CAPACITY);
            ForeignMemory.setVolatileByte(ACTIVE_PTR, (byte) 1);
            ForeignMemory.setVolatileInt(BOOT_GUARD_PTR, 2);
            return;
        }

        ForeignMemory.setByte(rawBuf + readBytes, (byte) 0);

        // First pass: scan for maximum ID to determine table capacity
        int maxId = 0;
        long pos = 0L;
        while (pos < readBytes) {
            // Skip leading whitespace and comments
            byte b = ForeignMemory.getByte(rawBuf + pos);
            if (b == '#' || b == ';') {
                while (pos < readBytes && ForeignMemory.getByte(rawBuf + pos) != '\n') {
                    pos++;
                }
                if (pos < readBytes) pos++; // skip newline
                continue;
            }
            if (b == '\r' || b == '\n' || b == ' ' || b == '\t') {
                pos++;
                continue;
            }

            // Parse ID
            int id = 0;
            boolean hasDigits = false;
            while (pos < readBytes) {
                byte digit = ForeignMemory.getByte(rawBuf + pos);
                if (digit >= '0' && digit <= '9') {
                    id = id * 10 + (digit - '0');
                    hasDigits = true;
                    pos++;
                } else {
                    break;
                }
            }

            if (hasDigits && id > maxId) {
                maxId = id;
            }

            // Advance to next line
            while (pos < readBytes && ForeignMemory.getByte(rawBuf + pos) != '\n') {
                pos++;
            }
            if (pos < readBytes) pos++;
        }

        int capacity = Math.max(maxId + 1, DEFAULT_INITIAL_CAPACITY);
        long tableSize = (long) capacity * ENTRY_STRIDE;
        long table = ForeignMemory.allocateNative(tableSize);
        MemorySegment.ofAddress(table).reinterpret(tableSize).fill((byte) 0);

        // Allocate empty string at ID 0 as default sentinel
        long emptyStrPtr = ForeignMemory.allocateNative(1L);
        ForeignMemory.setByte(emptyStrPtr, (byte) 0);
        ForeignMemory.setLong(table + (0L * ENTRY_STRIDE) + OFF_PTR, emptyStrPtr);
        ForeignMemory.setInt(table + (0L * ENTRY_STRIDE) + OFF_LEN, 0);

        // Second pass: parse ID and string content
        pos = 0L;
        while (pos < readBytes) {
            byte b = ForeignMemory.getByte(rawBuf + pos);
            if (b == '#' || b == ';') {
                while (pos < readBytes && ForeignMemory.getByte(rawBuf + pos) != '\n') {
                    pos++;
                }
                if (pos < readBytes) pos++;
                continue;
            }
            if (b == '\r' || b == '\n' || b == ' ' || b == '\t') {
                pos++;
                continue;
            }

            // Parse ID
            int id = 0;
            boolean hasDigits = false;
            while (pos < readBytes) {
                byte digit = ForeignMemory.getByte(rawBuf + pos);
                if (digit >= '0' && digit <= '9') {
                    id = id * 10 + (digit - '0');
                    hasDigits = true;
                    pos++;
                } else {
                    break;
                }
            }

            // Expect ':' separator
            if (pos < readBytes && ForeignMemory.getByte(rawBuf + pos) == ':') {
                pos++; // Skip ':'
            }

            // Extract string payload up to newline
            long strStart = pos;
            while (pos < readBytes) {
                byte cb = ForeignMemory.getByte(rawBuf + pos);
                if (cb == '\r' || cb == '\n') {
                    break;
                }
                pos++;
            }
            long strEnd = pos;
            int strLen = (int) (strEnd - strStart);

            if (hasDigits && id >= 0 && id < capacity) {
                if (id == 0 && strLen == 0) {
                    // ID 0 already initialized as empty string
                } else {
                    long payloadPtr = ForeignMemory.allocateNative((long) strLen + 1L);
                    if (strLen > 0) {
                        ForeignMemory.copy(rawBuf + strStart, payloadPtr, strLen);
                    }
                    ForeignMemory.setByte(payloadPtr + strLen, (byte) 0); // Null-terminator

                    // Free previous entry if re-registering
                    long prevPtr = ForeignMemory.getLong(table + ((long) id * ENTRY_STRIDE) + OFF_PTR);
                    if (prevPtr != 0L && prevPtr != emptyStrPtr) {
                        ForeignMemory.freeNative(prevPtr);
                    }

                    ForeignMemory.setLong(table + ((long) id * ENTRY_STRIDE) + OFF_PTR, payloadPtr);
                    ForeignMemory.setInt(table + ((long) id * ENTRY_STRIDE) + OFF_LEN, strLen);
                }
            }

            // Skip trailing newline chars
            while (pos < readBytes && (ForeignMemory.getByte(rawBuf + pos) == '\r' || ForeignMemory.getByte(rawBuf + pos) == '\n')) {
                pos++;
            }
        }

        ForeignMemory.freeNative(rawBuf);

        tablePtr = table;
        tableCapacity = capacity;
        ForeignMemory.setVolatileByte(ACTIVE_PTR, (byte) 1);
        ForeignMemory.setVolatileInt(BOOT_GUARD_PTR, 2);
    }

    private static void initEmptyTable(int capacity) {
        long tableSize = (long) capacity * ENTRY_STRIDE;
        long table = ForeignMemory.allocateNative(tableSize);
        MemorySegment.ofAddress(table).reinterpret(tableSize).fill((byte) 0);

        long emptyStrPtr = ForeignMemory.allocateNative(1L);
        ForeignMemory.setByte(emptyStrPtr, (byte) 0);
        ForeignMemory.setLong(table + (0L * ENTRY_STRIDE) + OFF_PTR, emptyStrPtr);
        ForeignMemory.setInt(table + (0L * ENTRY_STRIDE) + OFF_LEN, 0);

        tablePtr = table;
        tableCapacity = capacity;
    }

    @HotCode
    public static long getPointer(int id) {
        long tPtr = tablePtr;
        if (tPtr == 0L || id < 0 || id >= tableCapacity) {
            return tPtr != 0L ? ForeignMemory.getLong(tPtr) : 0L;
        }
        long ptr = ForeignMemory.getLong(tPtr + ((long) id * ENTRY_STRIDE) + OFF_PTR);
        return ptr != 0L ? ptr : ForeignMemory.getLong(tPtr); // Fallback to ID 0
    }

    @HotCode
    public static int getLength(int id) {
        long tPtr = tablePtr;
        if (tPtr == 0L || id < 0 || id >= tableCapacity) {
            return 0;
        }
        long ptr = ForeignMemory.getLong(tPtr + ((long) id * ENTRY_STRIDE) + OFF_PTR);
        if (ptr == 0L) {
            return 0;
        }
        return ForeignMemory.getInt(tPtr + ((long) id * ENTRY_STRIDE) + OFF_LEN);
    }

    public static boolean has(int id) {
        long tPtr = tablePtr;
        if (tPtr == 0L || id < 0 || id >= tableCapacity) {
            return false;
        }
        return ForeignMemory.getLong(tPtr + ((long) id * ENTRY_STRIDE) + OFF_PTR) != 0L;
    }

    public static int capacity() {
        return tableCapacity;
    }

    public static boolean isActive() {
        return ForeignMemory.getVolatileByte(ACTIVE_PTR) != 0;
    }

    /**
     * Cold startup helper: reconstructs a java.lang.String from off-heap UTF-8 bytes.
     * Use exclusively during cold initialization for FFM downcalls and reflection APIs.
     */
    public static String getJavaString(int id) {
        long tPtr = tablePtr;
        if (tPtr == 0L || id < 0 || id >= tableCapacity) {
            return String.valueOf(new char[0]);
        }
        long ptr = ForeignMemory.getLong(tPtr + ((long) id * ENTRY_STRIDE) + OFF_PTR);
        if (ptr == 0L) {
            return String.valueOf(new char[0]);
        }
        int len = ForeignMemory.getInt(tPtr + ((long) id * ENTRY_STRIDE) + OFF_LEN);
        if (len <= 0) {
            return String.valueOf(new char[0]);
        }
        byte[] dest = ForeignMemory.getBytes(ptr, len);
        return new String(dest, StandardCharsets.UTF_8);
    }

    public static void shutdown() {
        long tPtr = tablePtr;
        if (tPtr == 0L) return;

        int cap = tableCapacity;
        for (int i = 0; i < cap; i++) {
            long ptr = ForeignMemory.getLong(tPtr + ((long) i * ENTRY_STRIDE) + OFF_PTR);
            if (ptr != 0L) {
                ForeignMemory.freeNative(ptr);
            }
        }
        ForeignMemory.freeNative(tPtr);
        tablePtr = 0L;
        tableCapacity = 0;
        ForeignMemory.setVolatileByte(ACTIVE_PTR, (byte) 0);
        ForeignMemory.setVolatileInt(BOOT_GUARD_PTR, 0);
    }
}
