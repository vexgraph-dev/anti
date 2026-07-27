package input;

import event.MouseEvent;

public abstract class MouseAdapter implements MouseEvent {
    @Override public void onMouseDown(MouseResolve resolve) {}
    @Override public void onMouseUp(MouseResolve resolve) {}
    @Override public void onMouseRepeat(MouseResolve resolve) {}
    @Override public void onMouseMove(double x, double y) {}
}
