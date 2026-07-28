package input;

import event.KeyEvent;

/**
 * Adapter class for KeyEvent to allow users to selectively override methods
 * without implementing the entire interface.
 */
public abstract class KeyAdapter implements KeyEvent {
    @Override public void onKeyDown(int keyEvent, long exactNanos) {}
    @Override public void onKeyUp(int keyEvent, long exactNanos) {}
    @Override public void onKeyRepeat(int keyEvent, long exactNanos) {}
}
