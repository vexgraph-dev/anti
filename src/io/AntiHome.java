package io;

import annotation.Draft;
import annotation.Intention;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;

/**
 * Per-user "anti" home layout, created once so the engine always has a stable
 * place to write before any subsystem touches the disk. Lives in {@code io} so
 * an installer can invoke {@link #ensure()} as part of setup.
 *
 *   ~/anti/
 *     projects/    - user project workspaces
 *     logs/        - engine binary logs (io.Log default sink)
 *     placeholder/ - reserved scratch area
 */
@Draft
@Intention("Bootstrap the ~/anti directory tree once so Log, projects, and future bakery tooling have a canonical home before they run. Installer-facing via ensure().")
public final class AntiHome {

    private static final String ROOT_NAME = "anti";
    private static final String PROJECTS_NAME = "projects";
    private static final String LOGS_NAME = "logs";
    private static final String PLACEHOLDER_NAME = "placeholder";

    private static volatile boolean ensured;

    private AntiHome() {
    }

    public static String root() {
        return dir("");
    }

    public static String projects() {
        return dir(PROJECTS_NAME);
    }

    public static String logs() {
        return dir(LOGS_NAME);
    }

    public static String placeholder() {
        return dir(PLACEHOLDER_NAME);
    }

    private static String dir(String sub) {
        return Path.of(System.getProperty("user.home"), ROOT_NAME, sub).toString();
    }

    /** Creates the full layout; idempotent. Returns true when every dir exists. */
    public static boolean ensure() {
        if (ensured) {
            return true;
        }
        try {
            Files.createDirectories(Path.of(root()));
            Files.createDirectories(Path.of(projects()));
            Files.createDirectories(Path.of(logs()));
            Files.createDirectories(Path.of(placeholder()));
            ensured = true;
            return true;
        } catch (IOException e) {
            return false;
        }
    }

    /** Default log file: ~/anti/logs/engine.bin (truncated every run by FileWriter). */
    public static String defaultLogPath() {
        ensure();
        return Path.of(logs(), "engine.bin").toString();
    }
}
