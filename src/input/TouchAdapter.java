package input;

import event.TouchEvent;

public abstract class TouchAdapter implements TouchEvent {
    @Override public void onTouchDown(int touchId, double x, double y, double pressure, long exactNanos) {}
    @Override public void onTouchUp(int touchId, double x, double y, double pressure, long exactNanos) {}
    @Override public void onTouchMove(int touchId, double x, double y, double pressure, long exactNanos) {}
    @Override public void onTouchCancel(int touchId, long exactNanos) {}
}
