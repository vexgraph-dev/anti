package event;


/**
 * High-level OOP listener interface for hardware key events.
 * 
 * Executed safely on the Game Thread via the lock-free input queue,
 * guaranteeing zero Thread 0 blocking or GC pressure from event objects.
 */
public interface KeyEvent {
    void onKeyDown(int keyEvent, long exactNanos);
    void onKeyUp(int keyEvent, long exactNanos);
    void onKeyRepeat(int keyEvent, long exactNanos);
    default void onCharTyped(char character) {}
}
