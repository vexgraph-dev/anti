package input;

import event.KeyEvent;

/**
 * Adapter class for KeyEvent to allow users to selectively override methods
 * without implementing the entire interface.
 */
public abstract class KeyAdapter implements KeyEvent {
    @Override public void onKeyDown(KeyResolve resolve) {}
    @Override public void onKeyUp(KeyResolve resolve) {}
    @Override public void onKeyRepeat(KeyResolve resolve) {}
}
