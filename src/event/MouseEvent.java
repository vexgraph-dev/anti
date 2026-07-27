package event;

import input.MouseResolve;

public interface MouseEvent {
    void onMouseDown(MouseResolve resolve);
    void onMouseUp(MouseResolve resolve);
    void onMouseRepeat(MouseResolve resolve);
    void onMouseMove(double x, double y);
}
