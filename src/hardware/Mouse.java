package hardware;

import annotation.HotCode;

/**
 * Pure DOD Mouse input backend.
 * Zero-allocation, bitwise state tracking driven directly by OS event pumps.
 */
@HotCode
public class Mouse {
    
    // Core state array: [0: X, 1: Y, 2: DeltaX, 3: DeltaY, 4: ScrollX, 5: ScrollY]
    // Using a flat array ensures O(1) access and cache locality. 
    // This can easily be migrated to the Relational Engine's int[] bus later.
    private static final double[] STATE = new double[6];
    
    // Bitmask for buttons: bit 0=Left, bit 1=Right, bit 2=Middle, etc.
    private static int BUTTON_STATE = 0;

    /**
     * Gets the absolute X position of the mouse relative to the window.
     */
    public static double getX(long windowPtr) {
        return STATE[0];
    }

    /**
     * Gets the absolute Y position of the mouse relative to the window.
     */
    public static double getY(long windowPtr) {
        return STATE[1];
    }
    
    /**
     * Gets the X delta (movement) since the last frame.
     */
    public static double getDeltaX(long windowPtr) {
        return STATE[2];
    }
    
    /**
     * Gets the Y delta (movement) since the last frame.
     */
    public static double getDeltaY(long windowPtr) {
        return STATE[3];
    }

    /**
     * Checks if a specific mouse button is currently held down.
     * @param button 0 for Left, 1 for Right, 2 for Middle.
     */
    public static boolean isButtonDown(long windowPtr, int button) {
        return (BUTTON_STATE & (1 << button)) != 0;
    }
    
    // --- INTERNAL NATIVE PUMPS ---
    // These methods are meant to be invoked directly from the FFI window message pump.

    public static void pushPosition(long windowPtr, double x, double y) {
        STATE[2] = x - STATE[0];
        STATE[3] = y - STATE[1];
        STATE[0] = x;
        STATE[1] = y;
    }
    
    public static void pushButton(long windowPtr, int button, boolean down) {
        if (down) {
            BUTTON_STATE |= (1 << button);
        } else {
            BUTTON_STATE &= ~(1 << button);
        }
    }
    
    public static void pushScroll(long windowPtr, double scrollX, double scrollY) {
        STATE[4] = scrollX;
        STATE[5] = scrollY;
    }
}
