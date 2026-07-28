package input;

import event.MouseEvent;

public abstract class MouseAdapter implements MouseEvent {
    @Override public void onMouseDown(int mouseEvent, long exactNanos) {}
    @Override public void onMouseUp(int mouseEvent, long exactNanos) {}
    @Override public void onMouseRepeat(int mouseEvent, long exactNanos) {}
    @Override public void onMouseMove(double x, double y) {}
    @Override public void onMouseDrag(int mouseEvent, double x, double y) {}
}
