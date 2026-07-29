package cli;

import annotation.Draft;

@Draft
public class CommandRegistry {

    // TODO: Off-heap structure (like an open-addressed hash map) to map command names to execution targets

    @Draft
    public static void register(long commandNameStringPointer, long functionTargetPointer) {
        // TODO: Map the command name primitive.string to the target struct/handler
    }

    @Draft
    public static void execute(long parsedCommandPointer) {
        // TODO: Resolve command pointer from registry and execute the associated behavior
    }
}
