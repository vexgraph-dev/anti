package cli;

import annotation.Draft;
import annotation.Intention;
import nio.ForeignMemory;
import primitive.string;

import java.lang.System;
import java.lang.Character;
import java.io.IOException;

@Draft
@Intention("Zero-allocation off-heap replacement for java.util.Scanner")
public class Scanner {

    private static final byte[] READ_BUFFER = new byte[4096];

    private Scanner() {}

    @Draft
    public static long nextLine() {
        try {
            int len = 0;
            while (true) {
                int b = System.in.read();
                if (b == -1 || b == '\n') {
                    break;
                }
                if (b == '\r') {
                    continue;
                }
                if (len < READ_BUFFER.length) {
                    READ_BUFFER[len++] = (byte) b;
                } else {
                    break;
                }
            }
            if (len == 0) return 0L;

            long pointer = string.allocateUninitialized(len);
            ForeignMemory.copyFromHeap(READ_BUFFER, 0, pointer, len);
            ForeignMemory.putByte(pointer + len, (byte) 0); // null-terminator
            return pointer;
        } catch (IOException e) {
            return 0L;
        }
    }

    @Draft
    public static boolean hasNextLine() {
        try {
            return System.in.available() > 0;
        } catch (IOException e) {
            return false;
        }
    }

    @Draft
    public static long nextWord() {
        try {
            int len = 0;
            int b;
            // Skip leading whitespace
            while (true) {
                b = System.in.read();
                if (b == -1) return 0L;
                if (!Character.isWhitespace(b)) {
                    READ_BUFFER[len++] = (byte) b;
                    break;
                }
            }
            // Read word
            while (true) {
                b = System.in.read();
                if (b == -1 || Character.isWhitespace(b)) {
                    break;
                }
                if (len < READ_BUFFER.length) {
                    READ_BUFFER[len++] = (byte) b;
                } else {
                    break;
                }
            }
            if (len == 0) return 0L;

            long pointer = string.allocateUninitialized(len);
            ForeignMemory.copyFromHeap(READ_BUFFER, 0, pointer, len);
            ForeignMemory.putByte(pointer + len, (byte) 0); // null-terminator
            return pointer;
        } catch (IOException e) {
            return 0L;
        }
    }
}
