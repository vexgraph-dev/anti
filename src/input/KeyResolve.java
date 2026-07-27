package input;

import java.lang.foreign.ValueLayout;

/**
 * Zero-allocation analytics and reading API for the Key DOD structure.
 */
public final class KeyResolve {

    private KeyResolve() {}

    /** Returns true if the key is currently physically held down. */
    public static boolean isDown(int keyCode) {
        if (keyCode < 0 || keyCode >= 512) return false;
        return Key.STATE.get(ValueLayout.JAVA_LONG, (keyCode * 32L)) != 0L;
    }

    /** 
     * If the key is currently held down, returns the current duration (now - pressTime) in nanoseconds.
     * If the key is released, returns the total duration of the LAST complete hold.
     */
    public static long getHoldDurationNanos(int keyCode) {
        if (keyCode < 0 || keyCode >= 512) return 0L;
        long offset = keyCode * 32L;
        long pressTime = Key.STATE.get(ValueLayout.JAVA_LONG, offset);
        if (pressTime != 0L) {
            return System.nanoTime() - pressTime; // Currently held
        } else {
            return Key.STATE.get(ValueLayout.JAVA_LONG, offset + 20L); // Last hold duration
        }
    }

    /** Returns the time elapsed since the key was last released in nanoseconds. */
    public static long getDurationSinceReleaseNanos(int keyCode) {
        if (keyCode < 0 || keyCode >= 512) return 0L;
        long releaseTime = Key.STATE.get(ValueLayout.JAVA_LONG, (keyCode * 32L) + 8L);
        if (releaseTime == 0L) return 0L;
        return System.nanoTime() - releaseTime;
    }

    /** Returns the number of times the key was consecutively tapped within the multi-tap threshold. */
    public static int getKeystrokeAmount(int keyCode) {
        if (keyCode < 0 || keyCode >= 512) return 0;
        return Key.STATE.get(ValueLayout.JAVA_INT, (keyCode * 32L) + 16L);
    }

    /** Manually resets the consecutive tap counter for the given key. */
    public static void resetKeystrokeAmount(int keyCode) {
        if (keyCode >= 0 && keyCode < 512) {
            Key.STATE.set(ValueLayout.JAVA_INT, (keyCode * 32L) + 16L, 0);
        }
    }
}
