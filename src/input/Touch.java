package input;

import java.lang.foreign.Arena;
import java.lang.foreign.MemorySegment;
import java.lang.foreign.ValueLayout;
import event.TouchEvent;
import thread.RingBuffer;
import oop.TypeRegister;

public final class Touch {

    public static final int DOWN = 0;
    public static final int UP = 1;
    public static final int MOVE = 2;
    public static final int CANCEL = 3;

    private static final int MAX_TOUCHES = 10;

    // 10 touches * 32 bytes = 320 bytes off-heap for STATE
    // offset 0 (8B): pressTimeNanos
    // offset 8 (8B): lastHoldDuration
    // offset 16 (8B): lastReleaseTime
    // offset 24 (4B): currentTaps
    // offset 28 (4B): action
    static final MemorySegment STATE = Arena.global().allocate(MAX_TOUCHES * 32);

    // 10 touches * 32 bytes = 320 bytes off-heap for POS
    // offset 0 (8B): x (double)
    // offset 8 (8B): y (double)
    // offset 16 (8B): pressure (double)
    // offset 24 (8B): padding/unused
    static final MemorySegment POS = Arena.global().allocate(MAX_TOUCHES * 32);

    // Lock-free queue for touch events
    private static final long QUEUE_PTR = RingBuffer.instant(TypeRegister.ID_LONG, 1024);

    private static final TouchEvent[] listeners = new TouchEvent[64];
    private static int listenerCount = 0;

    private Touch() {}

    public static void addTouchListener(TouchEvent listener) {
        if (listener != null && listenerCount < listeners.length) {
            listeners[listenerCount++] = listener;
        }
    }

    public static void pushTouchEvent(int touchId, int action, double x, double y, double pressure, long thresholdNanos) {
        if (touchId < 0 || touchId >= MAX_TOUCHES) return;

        long stateOffset = touchId * 32L;
        long posOffset = touchId * 32L;
        long now = System.nanoTime();

        // Update Position & Pressure
        POS.set(ValueLayout.JAVA_DOUBLE, posOffset, x);
        POS.set(ValueLayout.JAVA_DOUBLE, posOffset + 8L, y);
        POS.set(ValueLayout.JAVA_DOUBLE, posOffset + 16L, pressure);

        // Update State
        if (action == DOWN) {
            long lastRelease = STATE.get(ValueLayout.JAVA_LONG, stateOffset + 16L);
            int currentTaps = STATE.get(ValueLayout.JAVA_INT, stateOffset + 24L);

            if ((now - lastRelease) < thresholdNanos) {
                STATE.set(ValueLayout.JAVA_INT, stateOffset + 24L, currentTaps + 1);
            } else {
                STATE.set(ValueLayout.JAVA_INT, stateOffset + 24L, 1);
            }
            STATE.set(ValueLayout.JAVA_LONG, stateOffset, now); // Set pressTimeNanos
        } else if (action == UP) {
            long pressTime = STATE.get(ValueLayout.JAVA_LONG, stateOffset);
            if (pressTime != 0L) {
                STATE.set(ValueLayout.JAVA_LONG, stateOffset + 8L, now - pressTime); // Store lastHoldDuration
            }
            STATE.set(ValueLayout.JAVA_LONG, stateOffset + 16L, now); // Store lastReleaseTime
            STATE.set(ValueLayout.JAVA_LONG, stateOffset, 0L); // Clear pressTimeNanos
        } else if (action == CANCEL) {
            STATE.set(ValueLayout.JAVA_LONG, stateOffset, 0L); // Clear pressTimeNanos
        }
        STATE.set(ValueLayout.JAVA_INT, stateOffset + 28L, action);

        // Push event to lock-free RingBuffer (Epoch-Packed: 46 bits time, 12 bits touchId, 6 bits action)
        long timeDeltaMicros = (now - Key.ENGINE_START_NANOS) / 1000L;
        timeDeltaMicros &= 0x3FFFFFFFFFFFL; // 46 bits

        long packed = (timeDeltaMicros << 18) | (((long) touchId & 0xFFF) << 6) | (action & 0x3F);
        RingBuffer.offer(QUEUE_PTR, packed);
    }

    public static void update() {
        long packed;
        while ((packed = RingBuffer.poll(QUEUE_PTR)) != 0L) {
            long timeDeltaMicros = (packed >> 18) & 0x3FFFFFFFFFFFL;
            long exactNanos = Key.ENGINE_START_NANOS + (timeDeltaMicros * 1000L);
            int touchId = (int) ((packed >> 6) & 0xFFF);
            int action = (int) (packed & 0x3F);

            long posOffset = touchId * 32L;
            double x = POS.get(ValueLayout.JAVA_DOUBLE, posOffset);
            double y = POS.get(ValueLayout.JAVA_DOUBLE, posOffset + 8L);
            double pressure = POS.get(ValueLayout.JAVA_DOUBLE, posOffset + 16L);

            for (int i = 0; i < listenerCount; i++) {
                if (action == DOWN) {
                    listeners[i].onTouchDown(touchId, x, y, pressure, exactNanos);
                } else if (action == UP) {
                    listeners[i].onTouchUp(touchId, x, y, pressure, exactNanos);
                } else if (action == MOVE) {
                    listeners[i].onTouchMove(touchId, x, y, pressure, exactNanos);
                } else if (action == CANCEL) {
                    listeners[i].onTouchCancel(touchId, exactNanos);
                }
            }
        }
    }

    // Getters for off-heap states
    public static boolean isDown(int touchId) {
        if (touchId < 0 || touchId >= MAX_TOUCHES) return false;
        return STATE.get(ValueLayout.JAVA_LONG, touchId * 32L) != 0L;
    }

    public static long getPressTime(int touchId) {
        if (touchId < 0 || touchId >= MAX_TOUCHES) return 0L;
        return STATE.get(ValueLayout.JAVA_LONG, touchId * 32L);
    }

    public static long getLastHoldDuration(int touchId) {
        if (touchId < 0 || touchId >= MAX_TOUCHES) return 0L;
        return STATE.get(ValueLayout.JAVA_LONG, touchId * 32L + 8L);
    }

    public static int getTaps(int touchId) {
        if (touchId < 0 || touchId >= MAX_TOUCHES) return 0;
        return STATE.get(ValueLayout.JAVA_INT, touchId * 32L + 24L);
    }

    public static int getLastAction(int touchId) {
        if (touchId < 0 || touchId >= MAX_TOUCHES) return CANCEL;
        return STATE.get(ValueLayout.JAVA_INT, touchId * 32L + 28L);
    }

    public static double getX(int touchId) {
        if (touchId < 0 || touchId >= MAX_TOUCHES) return 0.0;
        return POS.get(ValueLayout.JAVA_DOUBLE, touchId * 32L);
    }

    public static double getY(int touchId) {
        if (touchId < 0 || touchId >= MAX_TOUCHES) return 0.0;
        return POS.get(ValueLayout.JAVA_DOUBLE, touchId * 32L + 8L);
    }

    public static double getPressure(int touchId) {
        if (touchId < 0 || touchId >= MAX_TOUCHES) return 0.0;
        return POS.get(ValueLayout.JAVA_DOUBLE, touchId * 32L + 16L);
    }
}
