package khronos.parser;

import annotation.Draft;
import annotation.HotCode;
import annotation.Intention;
import nio.ForeignMemory;
import thread.Atomic;

import java.io.InputStream;
import java.lang.foreign.MemorySegment;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;

/**
 * Off-heap String Registry for Vulkan identifiers, function names, and extension strings.
 * Provides O(1) table indexing without java.lang.String heap allocations.
 */
@Draft
@Intention("Off-heap registry for Vulkan function, extension, and enum string lookups (zero GC overhead).")
public final class VulkanRegistry {

    private static final int DEFAULT_INITIAL_CAPACITY = 8192;
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

    private VulkanRegistry() {}

    @HotCode
    public static void boot() {
        if (ForeignMemory.getVolatileByte(ACTIVE_PTR) != 0 && tablePtr != 0L) {
            return;
        }

        if (!ForeignMemory.compareAndSetInt(BOOT_GUARD_PTR, 0, 1)) {
            while (ForeignMemory.getVolatileByte(ACTIVE_PTR) == 0 && ForeignMemory.getVolatileInt(BOOT_GUARD_PTR) == 1) {
                Thread.onSpinWait();
            }
            return;
        }

        try {
            loadIni();
            ForeignMemory.setVolatileByte(ACTIVE_PTR, (byte) 1);
            ForeignMemory.setVolatileInt(BOOT_GUARD_PTR, 2);
        } catch (Throwable t) {
            ForeignMemory.setVolatileInt(BOOT_GUARD_PTR, 3);
            System.err.println(t);
        }
    }

    private static void loadIni() {
        byte[] bytes = null;
        try {
            Path directPath = Path.of("src/khronos/parser/VulkanRegistry.ini");
            if (Files.exists(directPath)) {
                bytes = Files.readAllBytes(directPath);
            } else {
                Path altPath = Path.of("VulkanRegistry.ini");
                if (Files.exists(altPath)) {
                    bytes = Files.readAllBytes(altPath);
                } else {
                    InputStream in = VulkanRegistry.class.getResourceAsStream("/VulkanRegistry.ini");
                    if (in != null) {
                        bytes = in.readAllBytes();
                    }
                }
            }
        } catch (Throwable ignored) {}

        if (bytes == null || bytes.length == 0) {
            allocateTable(DEFAULT_INITIAL_CAPACITY);
            return;
        }

        parseIniBytes(bytes);
    }

    private static void allocateTable(int capacity) {
        long bytes = (long) capacity * ENTRY_STRIDE;
        long table = ForeignMemory.allocateNative(bytes);
        MemorySegment.ofAddress(table).reinterpret(bytes).fill((byte) 0);

        long emptyStrPtr = ForeignMemory.allocateNative(1L);
        ForeignMemory.setByte(emptyStrPtr, (byte) 0);
        ForeignMemory.setLong(table + (0L * ENTRY_STRIDE) + OFF_PTR, emptyStrPtr);
        ForeignMemory.setInt(table + (0L * ENTRY_STRIDE) + OFF_LEN, 0);

        tablePtr = table;
        tableCapacity = capacity;
    }

    private static void parseIniBytes(byte[] bytes) {
        // First pass: find maxId
        int pos = 0;
        int len = bytes.length;
        int maxId = 0;

        while (pos < len) {
            while (pos < len && (bytes[pos] == ' ' || bytes[pos] == '\t' || bytes[pos] == '\r' || bytes[pos] == '\n')) {
                pos++;
            }
            if (pos >= len) break;

            if (bytes[pos] == '#' || bytes[pos] == ';') {
                while (pos < len && bytes[pos] != '\n') {
                    pos++;
                }
                continue;
            }

            int id = 0;
            boolean hasDigits = false;
            while (pos < len && bytes[pos] >= '0' && bytes[pos] <= '9') {
                id = id * 10 + (bytes[pos] - '0');
                hasDigits = true;
                pos++;
            }

            if (hasDigits && id > maxId) {
                maxId = id;
            }

            while (pos < len && bytes[pos] != '\n') {
                pos++;
            }
        }

        int capacity = Math.max(maxId + 1, DEFAULT_INITIAL_CAPACITY);
        allocateTable(capacity);

        // Second pass: parse entries
        pos = 0;
        while (pos < len) {
            while (pos < len && (bytes[pos] == ' ' || bytes[pos] == '\t' || bytes[pos] == '\r' || bytes[pos] == '\n')) {
                pos++;
            }
            if (pos >= len) break;

            if (bytes[pos] == '#' || bytes[pos] == ';') {
                while (pos < len && bytes[pos] != '\n') {
                    pos++;
                }
                continue;
            }

            int id = 0;
            boolean hasDigits = false;
            while (pos < len && bytes[pos] >= '0' && bytes[pos] <= '9') {
                id = id * 10 + (bytes[pos] - '0');
                hasDigits = true;
                pos++;
            }

            while (pos < len && (bytes[pos] == ' ' || bytes[pos] == '\t')) {
                pos++;
            }

            if (pos < len && (bytes[pos] == '=' || bytes[pos] == ':')) {
                pos++;
            }

            int valStart = pos;
            while (pos < len && bytes[pos] != '\r' && bytes[pos] != '\n') {
                pos++;
            }

            int valLen = pos - valStart;
            if (valLen >= 2 && bytes[valStart] == '"' && bytes[valStart + valLen - 1] == '"') {
                valStart++;
                valLen -= 2;
            }

            if (hasDigits && id >= 0 && id < capacity) {
                if (id == 0 && valLen == 0) {
                    // ID 0 already initialized
                } else {
                    long payloadPtr = ForeignMemory.allocateNative((long) valLen + 1L);
                    for (int i = 0; i < valLen; i++) {
                        ForeignMemory.setByte(payloadPtr + i, bytes[valStart + i]);
                    }
                    ForeignMemory.setByte(payloadPtr + valLen, (byte) 0); // Null-terminator

                    ForeignMemory.setLong(tablePtr + ((long) id * ENTRY_STRIDE) + OFF_PTR, payloadPtr);
                    ForeignMemory.setInt(tablePtr + ((long) id * ENTRY_STRIDE) + OFF_LEN, valLen);
                }
            }
        }
    }

    @HotCode
    public static long getPointer(int id) {
        if (id <= 0 || id >= tableCapacity || tablePtr == 0L) return 0L;
        return ForeignMemory.getLong(tablePtr + ((long) id * ENTRY_STRIDE) + OFF_PTR);
    }

    @HotCode
    public static int getLength(int id) {
        if (id <= 0 || id >= tableCapacity || tablePtr == 0L) return 0;
        return ForeignMemory.getInt(tablePtr + ((long) id * ENTRY_STRIDE) + OFF_LEN);
    }

    public static String getJavaString(int id) {
        long ptr = getPointer(id);
        int len = getLength(id);
        if (ptr == 0L || len <= 0) return "";
        byte[] buf = new byte[len];
        for (int i = 0; i < len; i++) {
            buf[i] = ForeignMemory.getByte(ptr + i);
        }
        return new String(buf, StandardCharsets.UTF_8);
    }
}
