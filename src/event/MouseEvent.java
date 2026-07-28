package event;

import input.MouseResolve;

public interface MouseEvent {
    void onMouseDown(MouseResolve resolve);
    void onMouseUp(MouseResolve resolve);
    void onMouseRepeat(MouseResolve resolve);
    void onMouseMove(double x, double y);
    default void onMouseDrag(int button, double x, double y) {}
    default void onMouseScroll(double dx, double dy) {}
    
    /**
     * Triggered by a trackpad pinch-to-zoom gesture (macOS NSEventTypeMagnify).
     * @param magnification The change in magnification. Positive = zoom in (fingers spread), Negative = zoom out (fingers pinched).
     */
    default void onMouseZoom(double magnification) {}
}
