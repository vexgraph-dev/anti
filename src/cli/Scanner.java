package cli;

import annotation.Draft;
import annotation.Intention;

@Draft
@Intention("Zero-allocation off-heap replacement for java.util.Scanner")
public class Scanner {

    @Draft
    public static long nextLine() {
        // TODO: Read raw bytes from native standard input (System.in.read)
        // TODO: Append bytes directly to a primitive.string off-heap slot
        // TODO: Return the long pointer to the string
        return 0L;
    }

    @Draft
    public static boolean hasNextLine() {
        // TODO: Check if the input stream has available bytes to consume
        return false;
    }

    @Draft
    public static long nextWord() {
        // TODO: Scan bytes until a space/newline is found, return primitive.string pointer
        return 0L;
    }
}
