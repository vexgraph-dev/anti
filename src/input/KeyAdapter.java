package input;

import event.KeyEvent;

/**
 * Adapter class for KeyEvent to allow users to selectively override methods
 * without implementing the entire interface.
 */
public abstract class KeyAdapter implements KeyEvent {
    @Override public void onKeyDown(int key) {}
    @Override public void onKeyUp(int key) {}
    @Override public void onKeyRepeat(int key) {}
}
