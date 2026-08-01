package event;

public interface TouchEvent {
    void onTouchDown(int touchId, double x, double y, double pressure, long exactNanos);
    void onTouchUp(int touchId, double x, double y, double pressure, long exactNanos);
    void onTouchMove(int touchId, double x, double y, double pressure, long exactNanos);
    void onTouchCancel(int touchId, long exactNanos);
}
