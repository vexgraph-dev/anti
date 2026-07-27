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
                long packed = ((long) button << 8) | 2L; // 2 = Repeat
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
        
        // Push the event lock-free to the RingBuffer
        long packed = ((long) button << 8) | (action & 0xFF);
        RingBuffer.offer(QUEUE_PTR, packed);
    }
    
    public static void pushMoveEvent(double x, double y) {
        POS.set(ValueLayout.JAVA_DOUBLE, 0, x);
        POS.set(ValueLayout.JAVA_DOUBLE, 8, y);
        
        // Use button 255 to signify a move event in the RingBuffer
        long packed = (255L << 8) | 5L; 
        RingBuffer.offer(QUEUE_PTR, packed);
    }
    
    public static double getX() { return POS.get(ValueLayout.JAVA_DOUBLE, 0); }
    public static double getY() { return POS.get(ValueLayout.JAVA_DOUBLE, 8); }
    
    public static void dispatchEvents() {
        long packed;
        while ((packed = RingBuffer.poll(QUEUE_PTR)) != 0L) {
            int button = (int) ((packed >> 8) & 0xFF);
            int action = (int) (packed & 0xFF);
            
            if (button == 255 && action == 5) {
                double x = POS.get(ValueLayout.JAVA_DOUBLE, 0);
                double y = POS.get(ValueLayout.JAVA_DOUBLE, 8);
                for (int i = 0; i < listenerCount; i++) {
                    listeners[i].onMouseMove(x, y);
                }
                continue;
            }
            
            MouseResolve resolve = MouseResolve.get(button);
            for (int i = 0; i < listenerCount; i++) {
                if (action == 1) listeners[i].onMouseDown(resolve);
                else if (action == 0) listeners[i].onMouseUp(resolve);
                else if (action == 2) listeners[i].onMouseRepeat(resolve);
            }
        }
    }
    
    public static String getString(int button) {
        switch(button) {
            case 0: return "Left";
            case 1: return "Right";
            case 2: return "Middle";
            default: return "Button " + (button + 1);
        }
    }
}
