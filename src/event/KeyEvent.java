package event;

import input.KeyResolve;

/**
 * High-level OOP listener interface for hardware key events.
 * 
 * Executed safely on the Game Thread via the lock-free input queue,
 * guaranteeing zero Thread 0 blocking or GC pressure from event objects.
 */
public interface KeyEvent {
    void onKeyDown(KeyResolve resolve);
    void onKeyUp(KeyResolve resolve);
    void onKeyRepeat(KeyResolve resolve);
}
