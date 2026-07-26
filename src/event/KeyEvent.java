package event;

/**
 * High-level OOP listener interface for hardware key events.
 * 
 * Executed safely on the Game Thread via the lock-free input queue,
 * guaranteeing zero Thread 0 blocking or GC pressure from event objects.
 */
public interface KeyEvent {
    void onKeyDown(int key);
    void onKeyUp(int key);
    void onKeyRepeat(int key);
}
