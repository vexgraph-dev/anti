package telemetry;

import annotation.Draft;
import annotation.Intention;
import annotation.PlatformExclusive;
import input.Key;

import java.lang.foreign.Arena;
import java.lang.foreign.MemorySegment;
import java.lang.foreign.ValueLayout;

/**
 * Off-heap rolling log of every key the user presses across the whole system,
 * captured by a listen-only macOS CGEventTap while the engine app is running.
 *
 * Each entry packs [virtual keycode | action | monotonic elapsed-us timestamp]
 * into a 8-byte slot of a fixed-capacity off-heap ring. Nothing is written to
 * disk and nothing leaves the machine: this is local telemetry for the user's
 * own engine, on the user's own machine.
 */
@Draft
@Intention("Off-heap ring logging system-wide key events while the engine app is open")
@PlatformExclusive("Mac")
public final class KeyLog {

    private static final int CAPACITY = 1 << 13; // 8192 events, 64 KB off-heap
    private static final int MASK = CAPACITY - 1;

    private static final MemorySegment BUFFER = Arena.global().allocate(CAPACITY * 8L);
    private static final MemorySegment HEAD = Arena.global().allocate(8L);
    private static final MemorySegment COUNT = Arena.global().allocate(8L);

    private static final long START_NANOS = System.nanoTime();

    private KeyLog() {}

    /**
     * Records one system-wide key event into the rolling log.
     *
     * @param keyCode raw macOS virtual keycode (CGKeyCode)
     * @param action  1 = key down, 0 = key up, 2 = repeat
     */
    public static void record(int keyCode, int action) {
        long head = HEAD.get(ValueLayout.JAVA_LONG, 0);
        long index = (head & MASK) * 8L;

        long packed = ((long) keyCode & 0xFFFFL);
        packed |= ((long) (action & 0x3) << 16);
        packed |= (((System.nanoTime() - START_NANOS) / 1000L) & 0x3FFFFFFFFFFFL) << 32;

        BUFFER.set(ValueLayout.JAVA_LONG, index, packed);
        HEAD.set(ValueLayout.JAVA_LONG, 0, head + 1);
        COUNT.set(ValueLayout.JAVA_LONG, 0, COUNT.get(ValueLayout.JAVA_LONG, 0) + 1);
    }

    /**
     * Prints the most recent {@code sample} events as human-readable key names
     * (newest last). Call from the main thread at teardown.
     */
    public static void dumpRecent(int sample) {
        long total = COUNT.get(ValueLayout.JAVA_LONG, 0);
        if (total == 0) return;

        int shown = (int) Math.min(sample, CAPACITY);
        long head = HEAD.get(ValueLayout.JAVA_LONG, 0);
        System.out.println("[telemetry] " + total + " global key events recorded; last " + shown + ":");
        StringBuilder line = new StringBuilder();
        for (int i = shown - 1; i >= 0; i--) {
            long idx = (head - 1 - i) & MASK;
            long packed = BUFFER.get(ValueLayout.JAVA_LONG, idx * 8L);
            int keyCode = (int) (packed & 0xFFFF);
            int action = (int) ((packed >> 16) & 0x3);
            char marker = action == 1 ? '<' : (action == 0 ? '>' : '=');
            line.append(marker).append(Key.getString(keyCode)).append(' ');
        }
        System.out.println("[telemetry] " + line);
    }
}