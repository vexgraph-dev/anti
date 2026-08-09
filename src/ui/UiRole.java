package ui;

import annotation.Draft;
import annotation.Intention;

/**
 * UI role recorder. Event-driven (not immediate mode / not ImGui): UI widgets
 * submit event handles when they change; the CORE drains them and records only
 * dirty regions into the merged command buffer, keeping the UI pass minimal.
 */
@Draft
@Intention("UI role recorder. Only changed widgets re-record into the merged frame.")
public final class UiRole {

    private UiRole() {}

    public static void record(long taskPtr) {
        // TODO: record changed widget ops into the merged frame. Structure only.
    }
}