package cli;

import annotation.Draft;
import primitive.string;

@Draft
public class CommandParser {

    @Draft
    public static long parse(long rawStringPointer) {
        if (rawStringPointer == 0L) return 0L;
        String raw = string.get(rawStringPointer);
        if (raw == null) return 0L;
        raw = raw.trim();
        if (raw.isEmpty()) return 0L;

        String[] parts = raw.split("\\s+");
        if (parts.length == 0 || parts[0].isEmpty()) return 0L;

        long namePtr = string.allocate(parts[0]);
        long[] argPtrs = new long[parts.length - 1];
        for (int i = 1; i < parts.length; i++) {
            argPtrs[i - 1] = string.allocate(parts[i]);
        }

        return Command.allocate(namePtr, argPtrs);
    }
}
