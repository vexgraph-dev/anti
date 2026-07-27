package input;

import java.lang.foreign.ValueLayout;

/**
 * Zero-allocation analytics and reading API for the Key DOD structure.
 */
public final class KeyResolve {

    private static final KeyResolve[] CACHE = new KeyResolve[512];
    static {
        for (int i = 0; i < 512; i++) {
            CACHE[i] = new KeyResolve(i);
        }
    }

    public static KeyResolve get(int keyCode) {
        if (keyCode < 0 || keyCode >= 512) return CACHE[0];
        return CACHE[keyCode];
    }

    private final int keyCode;

    private KeyResolve(int keyCode) {
        this.keyCode = keyCode;
    }

    public int getKey() {
        return keyCode;
    }

    public String getName() {
        return Key.getString(keyCode);
    }

    /** Returns true if the key is currently physically held down. */
    public boolean isDown() {
        return Key.STATE.get(ValueLayout.JAVA_LONG, (keyCode * 32L)) != 0L;
    }

    /** Returns the absolute System.nanoTime() when the key was last pressed, or 0 if not currently pressed. */
    public long getPressTime() {
        return Key.STATE.get(ValueLayout.JAVA_LONG, (keyCode * 32L));
    }

    /** Returns the absolute System.nanoTime() when the key was last released. */
    public long getLastReleaseTime() {
        return Key.STATE.get(ValueLayout.JAVA_LONG, (keyCode * 32L) + 8L);
    }

    /** Returns the exact duration in nanoseconds of the PREVIOUS completed hold (from press to release). */
    public long getLastHoldDurationNanos() {
        return Key.STATE.get(ValueLayout.JAVA_LONG, (keyCode * 32L) + 24L);
    }

    /** Returns the ongoing duration in nanoseconds of the CURRENT hold, or 0 if not pressed. */
    public long getCurrentHoldDurationNanos() {
        long pressTime = getPressTime();
        if (pressTime == 0L) return 0L;
        return System.nanoTime() - pressTime;
    }

    /** 
     * Flexible getter: If currently held down, returns the current duration.
     * If currently released, returns the duration of the LAST complete hold.
     */
    public long getHoldDurationNanos() {
        long pressTime = getPressTime();
        if (pressTime != 0L) {
            return System.nanoTime() - pressTime;
        } else {
            return getLastHoldDurationNanos();
        }
    }

    /** Returns the time elapsed since the key was last released in nanoseconds. */
    public long getDurationSinceReleaseNanos() {
        long releaseTime = getLastReleaseTime();
        if (releaseTime == 0L) return 0L;
        return System.nanoTime() - releaseTime;
    }

    /** Returns the number of times the key was consecutively tapped within the multi-tap threshold. */
    public int getKeystrokeAmount() {
        return Key.STATE.get(ValueLayout.JAVA_INT, (keyCode * 32L) + 16L);
    }

    /** Manually resets the consecutive tap counter for the given key. */
    public void resetKeystrokeAmount() {
        Key.STATE.set(ValueLayout.JAVA_INT, (keyCode * 32L) + 16L, 0);
    }
}
