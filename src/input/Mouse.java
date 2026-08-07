package input;

import java.lang.foreign.Arena;
import java.lang.foreign.MemorySegment;
import java.lang.foreign.ValueLayout;
import event.MouseEvent;
import thread.RingBuffer;
import oop.TypeRegister;

public final class Mouse {

    public static final int LEFT = 0;
    public static final int RIGHT = 1;
    public static final int MIDDLE = 2;
    public static final int BUTTON_4 = 3;
    public static final int BUTTON_5 = 4;
    public static final int BUTTON_6 = 5;
    public static final int BUTTON_7 = 6;
    public static final int BUTTON_8 = 7;

    // 16 buttons * 32 bytes = 512 bytes off-heap
    static final MemorySegment STATE = Arena.global().allocate(16 * 32);
    static final MemorySegment POS = Arena.global().allocate(16);
    
    // Lock-free queue for mouse button events
    private static final long QUEUE_PTR = RingBuffer.instant(TypeRegister.ID_LONG, 1024);

    private static final MouseEvent[] listeners = new MouseEvent[64];
    private static int listenerCount = 0;

    private Mouse() {}

    public static void addMouseEvent(MouseEvent listener) {
        if (listenerCount < listeners.length) {
            listeners[listenerCount++] = listener;
        }
    }

    public static void pushButtonEvent(int button, int action, long thresholdNanos) {
        if (button < 0 || button >= 16) return;
        
        long offset = button * 32L;
        long now = System.nanoTime();
        
        if (action == 1) { // Down
            if (STATE.get(ValueLayout.JAVA_LONG, offset) != 0L) {
                int modifiers = 0;
                if (Key.isDown(Key.LEFT_SHIFT) || Key.isDown(Key.RIGHT_SHIFT)) modifiers |= 1;
                if (Key.isDown(Key.LEFT_CONTROL) || Key.isDown(Key.RIGHT_CONTROL)) modifiers |= 2;
                if (Key.isDown(Key.LEFT_ALT) || Key.isDown(Key.RIGHT_ALT)) modifiers |= 4;
                if (Key.isDown(Key.LEFT_SUPER) || Key.isDown(Key.RIGHT_SUPER)) modifiers |= 8;
                
                long timeDeltaMicros = (System.nanoTime() - Key.ENGINE_START_NANOS) / 1000L;
                timeDeltaMicros &= 0x3FFFFFFFFFFFL;
                long packed = (timeDeltaMicros << 18) | (((long) modifiers & 0xF) << 14) | (((long) button & 0xFFF) << 2) | 2L;
                RingBuffer.offer(QUEUE_PTR, packed);
                return;
            }
            
            long lastRelease = STATE.get(ValueLayout.JAVA_LONG, offset + 8L);
            int currentTaps = STATE.get(ValueLayout.JAVA_INT, offset + 16L);
            
            if ((now - lastRelease) < thresholdNanos) {
                STATE.set(ValueLayout.JAVA_INT, offset + 16L, currentTaps + 1);
            } else {
                STATE.set(ValueLayout.JAVA_INT, offset + 16L, 1);
            }
            STATE.set(ValueLayout.JAVA_LONG, offset, now);
        } else if (action == 0) { // Up
            long pressTime = STATE.get(ValueLayout.JAVA_LONG, offset);
            if (pressTime != 0L) {
                STATE.set(ValueLayout.JAVA_LONG, offset + 24L, now - pressTime); // Store lastHoldDuration
            }
            STATE.set(ValueLayout.JAVA_LONG, offset + 8L, now);
            STATE.set(ValueLayout.JAVA_LONG, offset, 0L);
        }
        
        // Push the event lock-free to the RingBuffer (64-bit Epoch Packed - 2.2 Years)
        int modifiers = 0;
        if (Key.isDown(Key.LEFT_SHIFT) || Key.isDown(Key.RIGHT_SHIFT)) modifiers |= 1;
        if (Key.isDown(Key.LEFT_CONTROL) || Key.isDown(Key.RIGHT_CONTROL)) modifiers |= 2;
        if (Key.isDown(Key.LEFT_ALT) || Key.isDown(Key.RIGHT_ALT)) modifiers |= 4;
        if (Key.isDown(Key.LEFT_SUPER) || Key.isDown(Key.RIGHT_SUPER)) modifiers |= 8;
        
        long timeDeltaMicros = (System.nanoTime() - Key.ENGINE_START_NANOS) / 1000L;
        timeDeltaMicros &= 0x3FFFFFFFFFFFL; // 46 bits modulo
        
        // 46 bits time | 4 bits modifiers | 12 bits button | 2 bits action
        long packed = (timeDeltaMicros << 18) | (((long) modifiers & 0xF) << 14) | (((long) button & 0xFFF) << 2) | (action & 0x3);
        RingBuffer.offer(QUEUE_PTR, packed);
    }

    public static void pushMoveEvent(double x, double y) {
        // Pack x and y into the 64-bit token (using 16 bits each for coords)
        long packed = (255L << 8) | 5L;
        packed |= (((long) (short) x & 0xFFFF) << 16);
        packed |= (((long) (short) y & 0xFFFF) << 32);
        RingBuffer.offer(QUEUE_PTR, packed);
    }

    public static void pushDragEvent(int button, double x, double y) {
        long packed = ((long) button << 8) | 7L;
        packed |= (((long) (short) x & 0xFFFF) << 16);
        packed |= (((long) (short) y & 0xFFFF) << 32);
        RingBuffer.offer(QUEUE_PTR, packed);
    }
    
    public static void pushScrollEvent(double dx, double dy) {
        short sdx = (short) Math.clamp(dx * 100.0, Short.MIN_VALUE, Short.MAX_VALUE);
        short sdy = (short) Math.clamp(dy * 100.0, Short.MIN_VALUE, Short.MAX_VALUE);
        long packed = (254L << 8) | 6L; 
        packed |= ((long) (sdx & 0xFFFF) << 16);
        packed |= ((long) (sdy & 0xFFFF) << 32);
        RingBuffer.offer(QUEUE_PTR, packed);
    }

    public static void pushZoomEvent(double magnification) {
        int floatBits = Float.floatToIntBits((float) magnification);
        long packed = (255L << 8) | 8L; 
        packed |= ((long) (floatBits & 0xFFFFFFFFL) << 16);
        RingBuffer.offer(QUEUE_PTR, packed);
    }

    public static double getX() { return POS.get(ValueLayout.JAVA_DOUBLE, 0); }
    public static double getY() { return POS.get(ValueLayout.JAVA_DOUBLE, 8); }

    public static void dispatchEvents() {
        long packed;
        while ((packed = RingBuffer.poll(QUEUE_PTR)) != 0L) {
            int action = (int) (packed & 0xFF);
            int button = (int) ((packed >> 8) & 0xFF);

            // Button Events
            if ((packed & 0x3) <= 2 && (action != 5 && action != 6 && action != 7 && action != 8)) {
                action = (int) (packed & 0x3);
                button = (int) ((packed >> 2) & 0xFFF);
                int modifiers = (int) ((packed >> 14) & 0xF);
                long timeDeltaMicros = (packed >>> 18) & 0x3FFFFFFFFFFFL;
                long exactNanos = Key.ENGINE_START_NANOS + (timeDeltaMicros * 1000L);

                int mappedMods = 0;
                if ((modifiers & 1) != 0) mappedMods |= Key.MOD_SHIFT;
                if ((modifiers & 2) != 0) mappedMods |= Key.MOD_CONTROL;
                if ((modifiers & 4) != 0) mappedMods |= Key.MOD_OPTION;
                if ((modifiers & 8) != 0) mappedMods |= Key.MOD_COMMAND;

                int mouseEvent = button | mappedMods;

                for (int i = 0; i < listenerCount; i++) {
                    if (action == 1) listeners[i].onMouseDown(mouseEvent, exactNanos);
                    else if (action == 0) listeners[i].onMouseUp(mouseEvent, exactNanos);
                    else if (action == 2) listeners[i].onMouseRepeat(mouseEvent, exactNanos);
                }
                continue;
            }

            // Mouse Move
            if (button == 255 && action == 5) {
                // Unpack directly from the 64-bit token
                double x = (short) ((packed >> 16) & 0xFFFF);
                double y = (short) ((packed >> 32) & 0xFFFF);
                for (int i = 0; i < listenerCount; i++) {
                    listeners[i].onMouseMove(x, y);
                }
                continue;
            }

            // Mouse Scroll
            if (button == 254 && action == 6) {
                double dx = (short) ((packed >> 16) & 0xFFFF) / 100.0;
                double dy = (short) ((packed >> 32) & 0xFFFF) / 100.0;
                for (int i = 0; i < listenerCount; i++) {
                    listeners[i].onMouseScroll(dx, dy);
                }
                continue;
            }

            // Mouse Drag
            if (action == 7) {
                // Unpack directly from the 64-bit token
                double x = (short) ((packed >> 16) & 0xFFFF);
                double y = (short) ((packed >> 32) & 0xFFFF);
                for (int i = 0; i < listenerCount; i++) {
                    listeners[i].onMouseDrag(button, x, y);
                }
                continue;
            }

            // Mouse Zoom
            if (action == 8) {
                int floatBits = (int) ((packed >> 16) & 0xFFFFFFFFL);
                double magnification = Float.intBitsToFloat(floatBits);
                for (int i = 0; i < listenerCount; i++) {
                    listeners[i].onMouseZoom(magnification);
                }
                continue;
            }
        }
    }
    
    
    public static int getButton(int mouseEvent) { return mouseEvent & 0xFFFF; }
    public static boolean hasShift(int mouseEvent) { return (mouseEvent & Key.MOD_SHIFT) != 0; }
    public static boolean hasControl(int mouseEvent) { return (mouseEvent & Key.MOD_CONTROL) != 0; }
    public static boolean hasOption(int mouseEvent) { return (mouseEvent & Key.MOD_OPTION) != 0; }
    public static boolean hasCommand(int mouseEvent) { return (mouseEvent & Key.MOD_COMMAND) != 0; }

    public static boolean isDown(int button) { return STATE.get(ValueLayout.JAVA_LONG, (button * 32L)) != 0L; }
    public static long getPressTime(int button) { return STATE.get(ValueLayout.JAVA_LONG, (button * 32L)); }
    public static long getLastReleaseTime(int button) { return STATE.get(ValueLayout.JAVA_LONG, (button * 32L) + 8L); }
    public static long getLastHoldDurationNanos(int button) { return STATE.get(ValueLayout.JAVA_LONG, (button * 32L) + 24L); }
    public static long getCurrentHoldDurationNanos(int button) {
        long p = getPressTime(button);
        return p == 0L ? 0L : System.nanoTime() - p;
    }
    public static int getKeystrokeAmount(int button) { return STATE.get(ValueLayout.JAVA_INT, (button * 32L) + 16L); }

    public static String getString(int button) {
        return switch(button) {
            case 0 -> "Left";
            case 1 -> "Right";
            case 2 -> "Middle";
            default -> "Button " + (button + 1);
        };
    }
}
