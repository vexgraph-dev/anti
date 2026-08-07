package io;

import annotation.Draft;
import annotation.Intention;

/**
 * Numeric event kinds for {@link Log}. Payload slots are documented per kind;
 * coordinates are stored as fixed-point (value * 1000) so LogCat output stays readable.
 */
@Draft
@Intention("Stable numeric event taxonomy for the binary log; names registered for the future in-house GUI.")
public interface LogKind {

    int RENDER_PRODUCE = 1;   // v0=slot, v1=drawCount
    int RENDER_PRESENT = 2;   // v0=slot, v1=presentCount
    int RENDER_DROPPED = 3;   // v0=slot, v1=reason (0=skipped, 1=out-of-date)

    int KEY_DOWN = 10;        // v0=keyCode, v1=exactNanos
    int KEY_REPEAT = 11;      // v0=keyCode, v1=exactNanos
    int KEY_UP = 12;          // v0=keyCode, v1=exactNanos

    int MOUSE_DOWN = 20;      // v0=button, v1=x*1000, v2=y*1000
    int MOUSE_UP = 21;        // v0=button, v1=x*1000, v2=y*1000
    int MOUSE_MOVE = 22;      // v0=0, v1=x*1000, v2=y*1000

    int TOUCH_DOWN = 30;      // v0=touchId, v1=x*1000, v2=y*1000, v3=pressure*1000
    int TOUCH_UP = 31;        // v0=touchId, v1=x*1000, v2=y*1000
    int TOUCH_MOVE = 32;      // v0=touchId, v1=x*1000, v2=y*1000, v3=pressure*1000
    int TOUCH_CANCEL = 33;    // v0=touchId

    static void registerNames() {
        Log.setName(RENDER_PRODUCE, "produce");
        Log.setName(RENDER_PRESENT, "present");
        Log.setName(RENDER_DROPPED, "drop");
        Log.setName(KEY_DOWN, "keyDown");
        Log.setName(KEY_REPEAT, "keyRepeat");
        Log.setName(KEY_UP, "keyUp");
        Log.setName(MOUSE_DOWN, "mouseDown");
        Log.setName(MOUSE_UP, "mouseUp");
        Log.setName(MOUSE_MOVE, "mouseMove");
        Log.setName(TOUCH_DOWN, "touchDown");
        Log.setName(TOUCH_UP, "touchUp");
        Log.setName(TOUCH_MOVE, "touchMove");
        Log.setName(TOUCH_CANCEL, "touchCancel");
    }
}
