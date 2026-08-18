package nio;

import annotation.HotCode;
import annotation.Intention;
import annotation.Required;
import thread.Atomic;

import java.io.IOException;
import java.io.InputStream;
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

        // Bundle fallback: GraalVM native-image embeds StringLookup.ini as a
        // classpath resource (see build_native.sh -H:IncludeResources). When the
        // binary is launched via Finder, CWD is "/" so no filesystem file exists;
        // load the bundled bytes so the off-heap table still populates.
        long rawBuf = 0L;
        long readBytes = 0L;
        if (handle != 0L) {
            long fileSize = ForeignMemory.fileSize(handle);
            if (fileSize <= 0L) {
                ForeignMemory.fileClose(handle);
                initEmptyTable(DEFAULT_INITIAL_CAPACITY);
                ForeignMemory.setVolatileByte(ACTIVE_PTR, (byte) 1);
                ForeignMemory.setVolatileInt(BOOT_GUARD_PTR, 2);
                return;
            }
            rawBuf = ForeignMemory.allocateNative(fileSize + 1L);
            readBytes = ForeignMemory.fileRead(handle, rawBuf, fileSize);
            ForeignMemory.fileClose(handle);
        } else {
            byte[] bundled = null;
            try (InputStream in = StringLookup.class.getResourceAsStream("/StringLookup.ini")) {
                if (in != null) {
                    bundled = in.readAllBytes();
                }
            } catch (IOException ignored) {
            }
            if (bundled != null && bundled.length > 0) {
                rawBuf = ForeignMemory.allocateNative((long) bundled.length + 1L);
                for (int k = 0; k < bundled.length; k++) {
                    ForeignMemory.setByte(rawBuf + k, bundled[k]);
                }
                readBytes = bundled.length;
            }
        }

        if (rawBuf == 0L || readBytes <= 0L) {
            if (rawBuf != 0L) {
                ForeignMemory.freeNative(rawBuf);
            }
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
                    int decLen = 0;
                    if (strLen > 0) {
                        // Decode Java-style escape sequences (\\, \n, \r, \t, \", \\uXXXX)
                        // so multi-line and control-character strings round-trip through
                        // the line-based .ini format. Existing backslash-free entries are
                        // byte-identical after decoding.
                        decLen = (int) decodeEscapedCopy(rawBuf + strStart, payloadPtr, strLen);
                    }
                    ForeignMemory.setByte(payloadPtr + decLen, (byte) 0); // Null-terminator

                    // Free previous entry if re-registering
                    long prevPtr = ForeignMemory.getLong(table + ((long) id * ENTRY_STRIDE) + OFF_PTR);
                    if (prevPtr != 0L && prevPtr != emptyStrPtr) {
                        ForeignMemory.freeNative(prevPtr);
                    }

                    ForeignMemory.setLong(table + ((long) id * ENTRY_STRIDE) + OFF_PTR, payloadPtr);
                    ForeignMemory.setInt(table + ((long) id * ENTRY_STRIDE) + OFF_LEN, decLen);
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

    /**
     * Copies {@code len} bytes from {@code src} into {@code dst}, decoding Java-style
     * backslash escape sequences (\\, \", \', \n, \r, \t, \b, \f, \\uXXXX, octal \0NN).
     * Returns the number of bytes written. Raw bytes with no backslash are copied verbatim.
     */
    private static long decodeEscapedCopy(long src, long dst, int len) {
        int out = 0;
        int i = 0;
        while (i < len) {
            byte b = ForeignMemory.getByte(src + i);
            if (b != '\\') {
                ForeignMemory.setByte(dst + out, b);
                out++;
                i++;
                continue;
            }
            if (i + 1 >= len) {
                ForeignMemory.setByte(dst + out, b);
                out++;
                i++;
                continue;
            }
            byte e = ForeignMemory.getByte(src + i + 1);
            switch (e) {
                case 'n': ForeignMemory.setByte(dst + out, (byte) '\n'); out++; i += 2; break;
                case 'r': ForeignMemory.setByte(dst + out, (byte) '\r'); out++; i += 2; break;
                case 't': ForeignMemory.setByte(dst + out, (byte) '\t'); out++; i += 2; break;
                case 'b': ForeignMemory.setByte(dst + out, (byte) '\b'); out++; i += 2; break;
                case 'f': ForeignMemory.setByte(dst + out, (byte) '\f'); out++; i += 2; break;
                case '"': ForeignMemory.setByte(dst + out, (byte) '"'); out++; i += 2; break;
                case '\'': ForeignMemory.setByte(dst + out, (byte) '\''); out++; i += 2; break;
                case '\\': ForeignMemory.setByte(dst + out, (byte) '\\'); out++; i += 2; break;
                case 'u': {
                    if (i + 6 <= len) {
                        int cp = 0;
                        boolean ok = true;
                        for (int k = 0; k < 4; k++) {
                            byte h = ForeignMemory.getByte(src + i + 2 + k);
                            int d = hexVal(h);
                            if (d < 0) { ok = false; break; }
                            cp = (cp << 4) | d;
                        }
                        if (ok) {
                            out = writeUtf8(dst, out, cp);
                            i += 6;
                            break;
                        }
                    }
                    // Not a valid \\uXXXX: fall through and keep literal backslash + 'u'
                    ForeignMemory.setByte(dst + out, b); out++;
                    ForeignMemory.setByte(dst + out, e); out++;
                    i += 2;
                    break;
                }
                default:
                    // Octal escapes \0NN and unknown escapes keep the backslash + char
                    ForeignMemory.setByte(dst + out, b); out++;
                    ForeignMemory.setByte(dst + out, e); out++;
                    i += 2;
                    break;
            }
        }
        return out;
    }

    private static int hexVal(byte b) {
        if (b >= '0' && b <= '9') return b - '0';
        if (b >= 'a' && b <= 'f') return b - 'a' + 10;
        if (b >= 'A' && b <= 'F') return b - 'A' + 10;
        return -1;
    }

    /** UTF-8 encodes code point cp into dst at out; returns new write offset. */
    private static int writeUtf8(long dst, int out, int cp) {
        if (cp <= 0x7F) {
            ForeignMemory.setByte(dst + out, (byte) cp);
            return out + 1;
        } else if (cp <= 0x7FF) {
            ForeignMemory.setByte(dst + out, (byte) (0xC0 | (cp >> 6)));
            ForeignMemory.setByte(dst + out + 1, (byte) (0x80 | (cp & 0x3F)));
            return out + 2;
        } else {
            ForeignMemory.setByte(dst + out, (byte) (0xE0 | (cp >> 12)));
            ForeignMemory.setByte(dst + out + 1, (byte) (0x80 | ((cp >> 6) & 0x3F)));
            ForeignMemory.setByte(dst + out + 2, (byte) (0x80 | (cp & 0x3F)));
            return out + 3;
        }
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
