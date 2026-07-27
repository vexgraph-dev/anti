package input;

import java.lang.foreign.ValueLayout;

public final class MouseResolve {

    private static final MouseResolve[] CACHE = new MouseResolve[16];
    static {
        for (int i = 0; i < 16; i++) {
            CACHE[i] = new MouseResolve(i);
        }
    }

    public static MouseResolve get(int button) {
        if (button < 0 || button >= 16) return CACHE[0];
        return CACHE[button];
    }

    private final int button;

    private MouseResolve(int button) {
        this.button = button;
    }

    public int getButton() { return button; }
    public String getName() { return Mouse.getString(button); }
    
    public double getX() { return Mouse.getX(); }
    public double getY() { return Mouse.getY(); }

    public boolean isDown() {
        return Mouse.STATE.get(ValueLayout.JAVA_LONG, (button * 32L)) != 0L;
    }

    public long getPressTime() {
        return Mouse.STATE.get(ValueLayout.JAVA_LONG, (button * 32L));
    }

    public long getLastReleaseTime() {
        return Mouse.STATE.get(ValueLayout.JAVA_LONG, (button * 32L) + 8L);
    }

    public long getLastHoldDurationNanos() {
        return Mouse.STATE.get(ValueLayout.JAVA_LONG, (button * 32L) + 24L);
    }

    public long getCurrentHoldDurationNanos() {
        long pressTime = getPressTime();
        if (pressTime == 0L) return 0L;
        return System.nanoTime() - pressTime;
    }

    public long getHoldDurationNanos() {
        long pressTime = getPressTime();
        if (pressTime != 0L) {
            return System.nanoTime() - pressTime;
        } else {
            return getLastHoldDurationNanos();
        }
    }

    public long getDurationSinceReleaseNanos() {
        long releaseTime = getLastReleaseTime();
        if (releaseTime == 0L) return 0L;
        return System.nanoTime() - releaseTime;
    }

    public int getKeystrokeAmount() {
        return Mouse.STATE.get(ValueLayout.JAVA_INT, (button * 32L) + 16L);
    }

    public void resetKeystrokeAmount() {
        Mouse.STATE.set(ValueLayout.JAVA_INT, (button * 32L) + 16L, 0);
    }
}
