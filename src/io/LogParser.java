package io;

import annotation.Draft;

import java.io.BufferedInputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;

import nio.StringLookup;
/**
 * Reads and formats binary logs produced by {@link Log}.
 *
 * File layout: 12-byte header ("ANTILOG" + version + recordSize) followed by
 * big-endian 52-byte records: kind(4) + tsNanos(8) + v0..v4(40).
 */
@Draft
public final class LogParser {

    public static final int RECORD_BYTES = 52;
    public static final int HEADER_BYTES = 12;

    private static final byte[] MAGIC = { 0x41, 0x4E, 0x54, 0x49, 0x4C, 0x4F, 0x47 };

    public interface RecordHandler {
        void onRecord(int kind, long ts, long v0, long v1, long v2, long v3, long v4);
    }

    private LogParser() {
    }

    private static boolean isLogHeader(byte[] h) {
        if (h == null || h.length < HEADER_BYTES) {
            return false;
        }
        for (int i = 0; i < 7; i++) {
            if (h[i] != MAGIC[i]) {
                return false;
            }
        }
        return h[7] == 1 && readInt(h, 8) == RECORD_BYTES;
    }

    public static boolean isLogFile(String path) {
        try (BufferedInputStream in = new BufferedInputStream(
                 new FileInputStream(path), 1 << 16)) {
            byte[] h = new byte[HEADER_BYTES];
            if (in.read(h) != HEADER_BYTES) {
                return false;
            }
            return isLogHeader(h);
        } catch (IOException e) {
            return false;
        }
    }

    /** Number of records in the file, or -1 if it is not a valid log file. */
    public static long count(String path) {
        File f = new File(path);
        if (!f.isFile() || f.length() < HEADER_BYTES) {
            return -1L;
        }
        return (f.length() - HEADER_BYTES) / RECORD_BYTES;
    }

    /** Streams every record to handler. Returns record count, or -1 on failure. */
    public static long parse(String path, RecordHandler handler) {
        try (BufferedInputStream in = new BufferedInputStream(
                 new FileInputStream(path), 1 << 16)) {
            byte[] h = new byte[HEADER_BYTES];
            if (in.read(h) != HEADER_BYTES || !isLogHeader(h)) {
                return -1L;
            }
            byte[] buf = new byte[RECORD_BYTES];
            long n = 0L;
            int r;
            while ((r = in.read(buf)) == RECORD_BYTES) {
                handler.onRecord(
                    readInt(buf, 0),
                    readLong(buf, 4),
                    readLong(buf, 12),
                    readLong(buf, 20),
                    readLong(buf, 28),
                    readLong(buf, 36),
                    readLong(buf, 44));
                n++;
            }
            return n;
        } catch (IOException e) {
            return -1L;
        }
    }

    /** Formats one record relative to baseTsNanos, e.g. "[ 12.345 ms]  EVENT  v0 v1 v2 v3 v4". */
    public static String formatRecord(int kind, long ts, long baseTs, String name,
                                      long v0, long v1, long v2, long v3, long v4) {
        double ms = baseTs <= 0L ? 0.0 : (ts - baseTs) / 1_000_000.0;
        long rounded = Math.round(ms * 1000.0);
        String msStr = (rounded / 1000) + StringLookup.getJavaString(311) + String.valueOf(1000 + Math.abs(rounded % 1000)).substring(1);
        return msStr + StringLookup.getJavaString(312) + name + StringLookup.getJavaString(313) + v0 + StringLookup.getJavaString(313) + v1 + StringLookup.getJavaString(313) + v2 + StringLookup.getJavaString(313) + v3 + StringLookup.getJavaString(313) + v4;
    }

    private static int readInt(byte[] b, int off) {
        return ((b[off] & 0xFF) << 24) | ((b[off + 1] & 0xFF) << 16)
            | ((b[off + 2] & 0xFF) << 8) | (b[off + 3] & 0xFF);
    }

    private static long readLong(byte[] b, int off) {
        long v = 0L;
        for (int i = 0; i < 8; i++) {
            v = (v << 8) | (b[off + i] & 0xFFL);
        }
        return v;
    }
}
