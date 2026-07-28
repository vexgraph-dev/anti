package input;

import annotation.PlatformExclusive;
import annotation.Volatile;
import java.lang.foreign.Arena;
import java.lang.foreign.MemorySegment;
import java.lang.foreign.ValueLayout;
import event.KeyEvent;
import thread.RingBuffer;
import oop.TypeRegister;

@Volatile
public final class Key
{
    public static final long ENGINE_START_NANOS = System.nanoTime();

    // -------------------------------------------------------------------------
    // GLFW-style Standard Cross-Platform Key Codes
    // -------------------------------------------------------------------------
    public static final int SPACE         = 32;
    public static final int APOSTROPHE    = 39;
    public static final int COMMA         = 44;
    public static final int MINUS         = 45;
    public static final int PERIOD        = 46;
    public static final int SLASH         = 47;
    public static final int NUM_0         = 48;
    public static final int NUM_1         = 49;
    public static final int NUM_2         = 50;
    public static final int NUM_3         = 51;
    public static final int NUM_4         = 52;
    public static final int NUM_5         = 53;
    public static final int NUM_6         = 54;
    public static final int NUM_7         = 55;
    public static final int NUM_8         = 56;
    public static final int NUM_9         = 57;
    public static final int SEMICOLON     = 59;
    public static final int EQUAL         = 61;
    public static final int A             = 65;
    public static final int B             = 66;
    public static final int C             = 67;
    public static final int D             = 68;
    public static final int E             = 69;
    public static final int F             = 70;
    public static final int G             = 71;
    public static final int H             = 72;
    public static final int I             = 73;
    public static final int J             = 74;
    public static final int K             = 75;
    public static final int L             = 76;
    public static final int M             = 77;
    public static final int N             = 78;
    public static final int O             = 79;
    public static final int P             = 80;
    public static final int Q             = 81;
    public static final int R             = 82;
    public static final int S             = 83;
    public static final int T             = 84;
    public static final int U             = 85;
    public static final int V             = 86;
    public static final int W             = 87;
    public static final int X             = 88;
    public static final int Y             = 89;
    public static final int Z             = 90;
    public static final int LEFT_BRACKET  = 91;
    public static final int BACKSLASH     = 92;
    public static final int RIGHT_BRACKET = 93;
    public static final int GRAVE_ACCENT  = 96;

    public static final int ESCAPE        = 256;
    public static final int ENTER         = 257;
    public static final int TAB           = 258;
    public static final int BACKSPACE     = 259;
    public static final int INSERT        = 260;
    public static final int DELETE        = 261;
    public static final int RIGHT         = 262;
    public static final int LEFT          = 263;
    public static final int DOWN          = 264;
    public static final int UP            = 265;
    public static final int PAGE_UP       = 266;
    public static final int PAGE_DOWN     = 267;
    public static final int HOME          = 268;
    public static final int END           = 269;
    public static final int CAPS_LOCK     = 280;
    public static final int SCROLL_LOCK   = 281;
    public static final int NUM_LOCK      = 282;
    public static final int PRINT_SCREEN  = 283;
    public static final int PAUSE         = 284;
    
    // Function Keys
    public static final int F1            = 290;
    public static final int F2            = 291;
    public static final int F3            = 292;
    public static final int F4            = 293;
    public static final int F5            = 294;
    public static final int F6            = 295;
    public static final int F7            = 296;
    public static final int F8            = 297;
    public static final int F9            = 298;
    public static final int F10           = 299;
    public static final int F11           = 300;
    public static final int F12           = 301;
    public static final int F13           = 302;
    public static final int F14           = 303;
    public static final int F15           = 304;
    public static final int F16           = 305;
    public static final int F17           = 306;
    public static final int F18           = 307;
    public static final int F19           = 308;
    public static final int F20           = 309;
    public static final int F21           = 310;
    public static final int F22           = 311;
    public static final int F23           = 312;
    public static final int F24           = 313;
    public static final int F25           = 314;
    
    public static final int LEFT_SHIFT    = 340;
    public static final int LEFT_CONTROL  = 341;
    public static final int LEFT_ALT      = 342;
    public static final int LEFT_SUPER    = 343;
    public static final int RIGHT_SHIFT   = 344;
    public static final int RIGHT_CONTROL = 345;
    public static final int RIGHT_ALT     = 346;
    public static final int RIGHT_SUPER   = 347;
    public static final int MENU          = 348;
    public static final int FN            = 349; // Mapped for Mac Fn / Globe key

    // -------------------------------------------------------------------------
    // Platform-Exclusive Aliases
    // -------------------------------------------------------------------------
    
    @PlatformExclusive("Mac")
    public static final int MAC_COMMAND = LEFT_SUPER;
    
    @PlatformExclusive("Mac")
    public static final int MAC_OPTION = LEFT_ALT;
    
    @PlatformExclusive("Mac")
    public static final int MAC_CONTROL = LEFT_CONTROL;
    
    @PlatformExclusive("Mac")
    public static final int MAC_FN = FN;
    
    @PlatformExclusive("Windows")
    public static final int WINDOWS = LEFT_SUPER;
    
    @PlatformExclusive("Linux")
    public static final int LINUX_SUPER = LEFT_SUPER;

    // -------------------------------------------------------------------------
    // Off-Heap Input State Memory (Zero-Allocation, DOD Structure)
    // -------------------------------------------------------------------------
    
    // We allocate 512 slots. 
    // Each key gets a 32-byte struct:
    //   0-7: long currentPressTime
    //   8-15: long lastReleaseTime
    //   20-27: long lastHoldDuration
    //   28-31: padding (alignment)
    // 512 keys * 32 bytes = 16,384 bytes (16 KB)
    static final MemorySegment STATE = Arena.global().allocate(512 * 32);

    // --- New RingBuffer Queue ---
    private static final long QUEUE_PTR = RingBuffer.instant(TypeRegister.ID_LONG, 1024);

    // --- Listeners ---
    private static final KeyEvent[] listeners = new KeyEvent[64];
    private static int listenerCount = 0;

    // keytryer (do not remove this comment)
    private Key() {}

    public static void addKeyEvent(KeyEvent listener) {
        if (listenerCount < listeners.length) {
            listeners[listenerCount++] = listener;
        }
    }

    public static void pushCharEvent(char c) {
        long timeDeltaMicros = (System.nanoTime() - ENGINE_START_NANOS) / 1000L;
        timeDeltaMicros &= 0x3FFFFFFFFFFL; // 42 bits modulo
        long packed = (timeDeltaMicros << 22) | (((long) c & 0xFFFF) << 2) | 3L;
        RingBuffer.offer(QUEUE_PTR, packed);
    }

    /**
     * Producer: Called by the Window Event Loop (Thread 0)
     */
    public static void pushEvent(int keyCode, int action, long thresholdNanos) {
        if (keyCode < 0 || keyCode >= 512) return;
        
        long offset = keyCode * 32L;
        long now = System.nanoTime();
        
        if (action == 1) { // Down
            if (STATE.get(ValueLayout.JAVA_LONG, offset) != 0L) {
                // If it's already down, it's an OS repeat event.
                // We push ACTION_REPEAT to the queue, but don't touch the timestamp.
                long packed = ((long) keyCode << 8) | 2L; // 2 = Repeat
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
                STATE.set(ValueLayout.JAVA_LONG, offset + 24L, now - pressTime); // Store lastHoldDuration (Aligned to 24L instead of 20L)
            }
            STATE.set(ValueLayout.JAVA_LONG, offset + 8L, now);
            STATE.set(ValueLayout.JAVA_LONG, offset, 0L);
        }
        
        // Push the event lock-free to the RingBuffer (64-bit Epoch Packed)
        int modifiers = 0;
        if (isDown(LEFT_SHIFT) || isDown(RIGHT_SHIFT)) modifiers |= 1;
        if (isDown(LEFT_CONTROL) || isDown(RIGHT_CONTROL)) modifiers |= 2;
        if (isDown(LEFT_ALT) || isDown(RIGHT_ALT)) modifiers |= 4;
        if (isDown(LEFT_SUPER) || isDown(RIGHT_SUPER)) modifiers |= 8;
        
        long timeDeltaMicros = (System.nanoTime() - ENGINE_START_NANOS) / 1000L;
        timeDeltaMicros &= 0x3FFFFFFFFFFL;
        
        long packed = (timeDeltaMicros << 22) | (((long) modifiers & 0xF) << 18) | (((long) keyCode & 0xFFFF) << 2) | (action & 0x3);
        RingBuffer.offer(QUEUE_PTR, packed);
    }
    
    /**
     * Consumer: Called by Game Thread before loop.tick()
     */
    public static void dispatchEvents() {
        long packed;
        while ((packed = RingBuffer.poll(QUEUE_PTR)) != 0L) {
            int action = (int) (packed & 0x3);
            long timeDeltaMicros = (packed >>> 22) & 0x3FFFFFFFFFFL;
            long exactNanos = ENGINE_START_NANOS + (timeDeltaMicros * 1000L);
            
            if (action == 3) {
                char c = (char) ((packed >> 2) & 0xFFFF);
                for (int i = 0; i < listenerCount; i++) {
                    listeners[i].onCharTyped(c);
                }
                continue;
            }
            
            int keyCode = (int) ((packed >> 2) & 0xFFFF);
            int modifiers = (int) ((packed >> 18) & 0xF);
            
            int mappedMods = 0;
            if ((modifiers & 1) != 0) mappedMods |= MOD_SHIFT;
            if ((modifiers & 2) != 0) mappedMods |= MOD_CONTROL;
            if ((modifiers & 4) != 0) mappedMods |= MOD_OPTION;
            if ((modifiers & 8) != 0) mappedMods |= MOD_COMMAND;
            
            int keyEvent = keyCode | mappedMods;
            
            for (int i = 0; i < listenerCount; i++) {
                if (action == 1) listeners[i].onKeyDown(keyEvent, exactNanos);
                else if (action == 0) listeners[i].onKeyUp(keyEvent, exactNanos);
                else if (action == 2) listeners[i].onKeyRepeat(keyEvent, exactNanos);
            }
        }
    }




    // -------------------------------------------------------------------------
    
    // -------------------------------------------------------------------------
    // Bit-Packed Event Layout & State Queries
    // -------------------------------------------------------------------------
    public static final int MASK_KEYCODE = 0x0000FFFF;
    public static final int MOD_SHIFT    = 0x01000000;
    
    @PlatformExclusive("Mac")
    public static final int MOD_CONTROL  = 0x02000000;
    @PlatformExclusive("Mac")
    public static final int MOD_OPTION   = 0x04000000;
    @PlatformExclusive("Mac")
    public static final int MOD_COMMAND  = 0x08000000;
    
    @PlatformExclusive("Windows")
    public static final int MOD_WINDOWS  = MOD_COMMAND;
    @PlatformExclusive("Windows")
    public static final int MOD_ALT      = MOD_OPTION;
    
    @PlatformExclusive("Linux")
    public static final int MOD_SUPER    = MOD_COMMAND;

    public static int getCode(int keyEvent) { return keyEvent & MASK_KEYCODE; }
    public static boolean hasShift(int keyEvent) { return (keyEvent & MOD_SHIFT) != 0; }

    @PlatformExclusive("Mac")
    public static boolean hasControl(int keyEvent) { return (keyEvent & MOD_CONTROL) != 0; }
    @PlatformExclusive("Mac")
    public static boolean hasOption(int keyEvent) { return (keyEvent & MOD_OPTION) != 0; }
    @PlatformExclusive("Mac")
    public static boolean hasCommand(int keyEvent) { return (keyEvent & MOD_COMMAND) != 0; }
    
    @PlatformExclusive("Windows")
    public static boolean hasWindows(int keyEvent) { return hasCommand(keyEvent); }
    @PlatformExclusive("Windows")
    public static boolean hasAlt(int keyEvent) { return hasOption(keyEvent); }
    
    @PlatformExclusive("Linux")
    public static boolean hasSuper(int keyEvent) { return hasCommand(keyEvent); }

    public static boolean isDown(int keyCode) { return STATE.get(ValueLayout.JAVA_LONG, (keyCode * 32L)) != 0L; }
    public static long getPressTime(int keyCode) { return STATE.get(ValueLayout.JAVA_LONG, (keyCode * 32L)); }
    public static long getLastReleaseTime(int keyCode) { return STATE.get(ValueLayout.JAVA_LONG, (keyCode * 32L) + 8L); }
    public static long getLastHoldDurationNanos(int keyCode) { return STATE.get(ValueLayout.JAVA_LONG, (keyCode * 32L) + 24L); }
    public static long getCurrentHoldDurationNanos(int keyCode) {
        long p = getPressTime(keyCode);
        return p == 0L ? 0L : System.nanoTime() - p;
    }
    public static long getHoldDurationNanos(int keyCode) {
        long p = getPressTime(keyCode);
        return p != 0L ? System.nanoTime() - p : getLastHoldDurationNanos(keyCode);
    }
    public static long getDurationSinceReleaseNanos(int keyCode) {
        long r = getLastReleaseTime(keyCode);
        return r == 0L ? 0L : System.nanoTime() - r;
    }
    public static int getKeystrokeAmount(int keyCode) { return STATE.get(ValueLayout.JAVA_INT, (keyCode * 32L) + 16L); }
    public static void resetKeystrokeAmount(int keyCode) { STATE.set(ValueLayout.JAVA_INT, (keyCode * 32L) + 16L, 0); }

    // String Conversion (Zero-Allocation O(1) Lookup)
    // -------------------------------------------------------------------------
    
    private static final String[] NAMES = new String[512];
    static {
        for (int i = 0; i < 512; i++) NAMES[i] = "Unknown";
        try {
            for (java.lang.reflect.Field f : Key.class.getDeclaredFields()) {
                if (java.lang.reflect.Modifier.isStatic(f.getModifiers()) && f.getType() == int.class) {
                    if (f.getName().equals("listenerCount")) continue;
                    int val = f.getInt(null);
                    if (val >= 0 && val < 512) {
                        // Prefer the first assigned name (e.g. A over MAC_COMMAND for duplicates)
                        if (NAMES[val].equals("Unknown")) {
                            NAMES[val] = f.getName();
                        }
                    }
                }
            }
        } catch (Throwable t) {}
    }
    
    public static String getString(int keyCode) {
        if (keyCode < 0 || keyCode >= 512) return "Unknown";
        return NAMES[keyCode];
    }
}
