package cli;

import annotation.Draft;
import io.Log;
import io.LogParser;

import java.io.File;

/**
 * Read-side of {@link Log} for the CLI. Plain Java entry points a console
 * loop calls; no off-heap command allocation on this path.
 */
@Draft
public final class LogCommands {

    private LogCommands() {
    }

    public static String stat(String path) {
        if (!LogParser.isLogFile(path)) {
            return "not a log file: " + path;
        }
        long n = LogParser.count(path);
        File f = new File(path);
        return "log: " + path
            + "\n  records: " + n
            + "\n  bytes: " + f.length()
            + " (" + (f.length() - LogParser.HEADER_BYTES) + " payload)";
    }

    /** Prints formatted records to stdout, newest-last. limit < 0 prints everything. */
    public static void cat(String path, int limit) {
        final long[] base = { -1L };
        final long[] shown = { 0L };
        final long[] total = { 0L };
        LogParser.parse(path, (kind, ts, v0, v1, v2, v3, v4) -> {
            total[0]++;
            if (base[0] < 0L) {
                base[0] = ts;
            }
            if (limit >= 0 && shown[0] >= limit) {
                return;
            }
            shown[0]++;
            System.out.println(LogParser.formatRecord(
                kind, ts, base[0], Log.name(kind), v0, v1, v2, v3, v4));
        });
        if (limit >= 0 && shown[0] < total[0]) {
            System.out.println("(" + (total[0] - shown[0]) + " more)");
        }
    }
}
