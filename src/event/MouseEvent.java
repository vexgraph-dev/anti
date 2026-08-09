package event;


public interface MouseEvent {
    void onMouseDown(int mouseEvent, long exactNanos);
    void onMouseUp(int mouseEvent, long exactNanos);
    void onMouseRepeat(int mouseEvent, long exactNanos);
    void onMouseMove(double x, double y);

    /**
     * Relative movement while the cursor is locked. (dx, dy) is how much the
     * mouse travelled this step; the absolute position stays at the lock anchor.
     */
    default void onMouseMoveDelta(double dx, double dy) {}
    default void onMouseDrag(int mouseEvent, double x, double y) {}
    default void onMouseScroll(double dx, double dy) {}
    
    /**
     * Triggered by a trackpad pinch-to-zoom gesture (macOS NSEventTypeMagnify).
     * @param magnification The change in magnification. Positive = zoom in (fingers spread), Negative = zoom out (fingers pinched).
     */
    default void onMouseZoom(double magnification) {}
}
