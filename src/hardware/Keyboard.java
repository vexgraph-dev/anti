package hardware;

import annotation.HotCode;

/**
 * Pure DOD Keyboard input backend.
 * Uses packed bitsets (long arrays) for 0-allocation O(1) key lookups.
 */
@HotCode
public class Keyboard {

    // Bitset for 256 virtual key codes. 
    // 256 bits = exactly 4 longs.
    private static final long[] KEY_STATE = new long[4];

    /**
     * Fast O(1) lookup to check if a key is held down.
     * @param windowPtr The window context
     * @param keyCode The OS-specific or Engine-specific virtual key code (0-255)
     */
    public static boolean isKeyDown(long windowPtr, int keyCode) {
        if (keyCode < 0 || keyCode > 255) return false;
        
        int bucket = keyCode >> 6; // divide by 64
        int bit = keyCode & 63;    // modulo 64
        
        return (KEY_STATE[bucket] & (1L << bit)) != 0;
    }

    // --- INTERNAL NATIVE PUMPS ---
    // These methods are meant to be invoked directly from the FFI window message pump.

    public static void pushKey(long windowPtr, int keyCode, boolean down) {
        if (keyCode < 0 || keyCode > 255) return;
        
        int bucket = keyCode >> 6;
        int bit = keyCode & 63;
        
        if (down) {
            KEY_STATE[bucket] |= (1L << bit);
        } else {
            KEY_STATE[bucket] &= ~(1L << bit);
        }
    }
    
    /**
     * Clears all keyboard state instantly (e.g. on window focus loss).
     */
    public static void clear(long windowPtr) {
        KEY_STATE[0] = 0L;
        KEY_STATE[1] = 0L;
        KEY_STATE[2] = 0L;
        KEY_STATE[3] = 0L;
    }
}
