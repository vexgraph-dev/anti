package cli;

import annotation.Draft;

@Draft
public class Command {

    @Draft
    public static long allocate(long nameStringPointer, long[] argumentStringPointers) {
        // TODO: Allocate an off-heap struct representing a command and its parameters
        return 0L;
    }

    @Draft
    public static long getName(long commandPointer) {
        // TODO: Return primitive.string pointer for the command name
        return 0L;
    }

    @Draft
    public static long getArgument(long commandPointer, int index) {
        // TODO: Return primitive.string pointer for a specific argument
        return 0L;
    }

    @Draft
    public static void free(long commandPointer) {
        // TODO: Recursively free the command struct and its string pointers
    }
}
