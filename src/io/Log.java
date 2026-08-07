package io;

import annotation.Draft;
import annotation.Intention;
import annotation.Volatile;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicLong;
import java.util.concurrent.locks.LockSupport;
import nio.ForeignMemory;

/**
 * Zero-GC off-heap binary logger.
 *
 * Hot path ({@link #append}): CAS-claims a 64-byte slot in a fixed off-heap ring,
 * writes the record, release-publishes via a volatile valid flag, and unparks the
 * writer. No allocations, no locks, no syscalls on the producing thread.
 *
 * The writer daemon thread drains the ring and writes raw 52-byte records to a
 * file through {@link FileWriter}; all formatting happens on read via
 * {@link LogParser}.
 *
 * On by default: the sink is ~/anti/logs/engine.bin (see {@link AntiHome}).
 * Override with -Danti.log=&lt;path&gt;, or disable entirely with -Danti.log=off.
 */
@Draft
@Intention("Off-heap MPSC event ring: CAD-claim, volatile release-publish, dedicated daemon writer. Drop-before-claim so every published record is drained.")
@Volatile
public final class Log {

    public static final int SLOT_SIZE = 64;
    public static final int SLOT_COUNT = 1 << 14;
    public static final int SLOT_MASK = SLOT_COUNT - 1;
    public static final int RECORD_BYTES = 52;

    private static final long HEAD_OFF = 0L;
    private static final long TAIL_OFF = 8L;
    private static final long SLOT_BASE = 16L;

    private static final int VALID = 1;
    private static final int FLUSH_EVERY = 4096;
    private static final int IDLE_FLUSH_TICKS = 1000;
    private static final long IDLE_PARK_NANOS = 100_000L;

    private static final byte[] HEADER = {
        0x41, 0x4E, 0x54, 0x49, 0x4C, 0x4F, 0x47,
        0x01,
        (byte) (RECORD_BYTES >>> 24), (byte) (RECORD_BYTES >>> 16),
        (byte) (RECORD_BYTES >>> 8), (byte) RECORD_BYTES
    };

    private static final boolean ENABLED;
    private static final String PATH;

    private static long buffer;
    private static boolean fileReady;
    private static FileWriter file;
    private static Thread writer;
    private static volatile boolean running;
    private static boolean closed;
    private static volatile boolean active;

    private static final AtomicLong appended = new AtomicLong();
    private static final AtomicLong dropped = new AtomicLong();
    private static long writtenRecords;

    private static final ConcurrentHashMap<Integer, String> NAMES = new ConcurrentHashMap<>();

    private static final byte[] COPY = new byte[RECORD_BYTES];

    static {
        String prop = System.getProperty("anti.log");
        boolean enabled = prop == null || !"off".equalsIgnoreCase(prop);
        if (prop == null || prop.isEmpty()) {
            prop = AntiHome.defaultLogPath();
        }
        ENABLED = enabled;
        PATH = enabled ? prop : null;
        active = enabled;
        if (enabled) {
            buffer = ForeignMemory.allocateNative((long) SLOT_BASE + (long) SLOT_COUNT * SLOT_SIZE);
            ForeignMemory.setMemory(buffer, (long) SLOT_BASE + (long) SLOT_COUNT * SLOT_SIZE, (byte) 0);
            file = new FileWriter();
            fileReady = file.open(PATH);
            if (fileReady) {
                file.write(HEADER, 0, HEADER.length);
            } else {
                System.err.println("[anti.log] cannot open log file: " + PATH);
            }
            running = true;
            writer = new Thread(Log::writerLoop, "anti-log-writer");
            writer.setDaemon(true);
            writer.start();
            Runtime.getRuntime().addShutdownHook(new Thread(Log::flushAndClose, "anti-log-flush"));
        }
    }

    private Log() {
    }

    public static boolean isEnabled() {
        return ENABLED;
    }

    /** Runtime gate: when false, append() is a no-op but the logger stays open. */
    public static boolean isActive() {
        return active;
    }

    public static void setActive(boolean on) {
        if (ENABLED) {
            active = on;
        }
    }

    public static String path() {
        return PATH;
    }

    public static long appended() {
        return appended.get();
    }

    public static long dropped() {
        return dropped.get();
    }

    public static long written() {
        return writtenRecords;
    }

    /** Registers a display name for a kind. Safe to call in any process that will read logs. */
    public static void setName(int kind, String name) {
        if (kind >= 0 && name != null) {
            NAMES.put(kind, name);
        }
    }

    public static String name(int kind) {
        String n = NAMES.get(kind);
        return n != null ? n : ("kind" + kind);
    }

    public static void append(int kind) {
        append(kind, 0L, 0L, 0L, 0L, 0L);
    }

    public static void append(int kind, long v0) {
        append(kind, v0, 0L, 0L, 0L, 0L);
    }

    public static void append(int kind, long v0, long v1) {
        append(kind, v0, v1, 0L, 0L, 0L);
    }

    public static void append(int kind, long v0, long v1, long v2) {
        append(kind, v0, v1, v2, 0L, 0L);
    }

    public static void append(int kind, long v0, long v1, long v2, long v3) {
        append(kind, v0, v1, v2, v3, 0L);
    }

    public static void append(int kind, long v0, long v1, long v2, long v3, long v4) {
        if (!ENABLED || !active || buffer == 0L) {
            return;
        }
        long index = claimSlot();
        if (index < 0L) {
            dropped.incrementAndGet();
            return;
        }
        int slotIdx = (int) (index & SLOT_MASK);
        long base = SLOT_BASE + (long) slotIdx * SLOT_SIZE;
        ForeignMemory.setInt(buffer + base, kind);
        ForeignMemory.setLong(buffer + base + 8L, System.nanoTime());
        ForeignMemory.setLong(buffer + base + 16L, v0);
        ForeignMemory.setLong(buffer + base + 24L, v1);
        ForeignMemory.setLong(buffer + base + 32L, v2);
        ForeignMemory.setLong(buffer + base + 40L, v3);
        ForeignMemory.setLong(buffer + base + 48L, v4);
        ForeignMemory.setVolatileInt(buffer + base + 4L, VALID);
        appended.incrementAndGet();
        LockSupport.unpark(writer);
    }

    /**
     * CAS fetch-add on HEAD. Returns the claimed index, or -1 if the ring is
     * full. The full-check runs against a fresh head/tail snapshot before each
     * CAS, so a successfully claimed index K satisfies K - tail < SLOT_COUNT;
     * since tail is monotonic, slot (K & MASK) (previously used by K-SLOT_COUNT)
     * is already drained and free. Dropping here, before HEAD advances, is what
     * keeps the writer able to drain every published record.
     */
    private static long claimSlot() {
        long head;
        long tail;
        do {
            head = ForeignMemory.getVolatileLong(buffer + HEAD_OFF);
            tail = ForeignMemory.getVolatileLong(buffer + TAIL_OFF);
            if (head - tail >= SLOT_COUNT) {
                return -1L;
            }
        } while (!ForeignMemory.compareAndSetLong(buffer + HEAD_OFF, head, head + 1L));
        return head;
    }

    private static void writerLoop() {
        long idle = 0L;
        while (running) {
            long wrote = drainAndWrite();
            if (wrote > 0L) {
                idle = 0L;
                if (writtenRecords - flushPoint >= FLUSH_EVERY) {
                    flushPoint = writtenRecords;
                    file.flush();
                }
                continue;
            }
            idle++;
            if (idle >= IDLE_FLUSH_TICKS) {
                idle = 0L;
                flushPoint = writtenRecords;
                file.flush();
            }
            LockSupport.parkNanos(IDLE_PARK_NANOS);
        }
        drainAndWrite();
        file.flush();
    }

    private static long flushPoint;

    private static long drainAndWrite() {
        long head = ForeignMemory.getVolatileLong(buffer + HEAD_OFF);
        long tail = ForeignMemory.getVolatileLong(buffer + TAIL_OFF);
        long wrote = 0L;
        while (tail < head) {
            int slotIdx = (int) (tail & SLOT_MASK);
            long base = SLOT_BASE + (long) slotIdx * SLOT_SIZE;
            if (ForeignMemory.getVolatileInt(buffer + base + 4L) != VALID) {
                break;
            }
            writeSlot(buffer + base);
            ForeignMemory.setVolatileInt(buffer + base + 4L, 0);
            tail++;
            ForeignMemory.setVolatileLong(buffer + TAIL_OFF, tail);
            wrote++;
        }
        if (wrote > 0L) {
            writtenRecords += wrote;
        }
        return wrote;
    }

    private static void writeSlot(long address) {
        int kind = ForeignMemory.getInt(address);
        long ts = ForeignMemory.getLong(address + 8L);
        long v0 = ForeignMemory.getLong(address + 16L);
        long v1 = ForeignMemory.getLong(address + 24L);
        long v2 = ForeignMemory.getLong(address + 32L);
        long v3 = ForeignMemory.getLong(address + 40L);
        long v4 = ForeignMemory.getLong(address + 48L);
        putInt(COPY, 0, kind);
        putLong(COPY, 4, ts);
        putLong(COPY, 12, v0);
        putLong(COPY, 20, v1);
        putLong(COPY, 28, v2);
        putLong(COPY, 36, v3);
        putLong(COPY, 44, v4);
        file.write(COPY, 0, RECORD_BYTES);
    }

    private static void putInt(byte[] b, int off, int v) {
        b[off] = (byte) (v >>> 24);
        b[off + 1] = (byte) (v >>> 16);
        b[off + 2] = (byte) (v >>> 8);
        b[off + 3] = (byte) v;
    }

    private static void putLong(byte[] b, int off, long v) {
        b[off] = (byte) (v >>> 56);
        b[off + 1] = (byte) (v >>> 48);
        b[off + 2] = (byte) (v >>> 40);
        b[off + 3] = (byte) (v >>> 32);
        b[off + 4] = (byte) (v >>> 24);
        b[off + 5] = (byte) (v >>> 16);
        b[off + 6] = (byte) (v >>> 8);
        b[off + 7] = (byte) v;
    }

    /** Stops the writer, drains remaining records, closes the file, frees the ring. Idempotent. */
    public static void flushAndClose() {
        if (!ENABLED) {
            return;
        }
        synchronized (Log.class) {
            if (closed) {
                return;
            }
            closed = true;
        }
        running = false;
        LockSupport.unpark(writer);
        try {
            writer.join(1000L);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }
        if (buffer != 0L) {
            drainAndWrite();
        }
        if (file != null) {
            file.flush();
            file.close();
        }
        if (buffer != 0L) {
            ForeignMemory.freeNative(buffer);
            buffer = 0L;
        }
    }
}
