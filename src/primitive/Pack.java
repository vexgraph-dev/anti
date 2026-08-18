package primitive;

import annotation.Required;
import oop.TypeRegister;

import nio.StringLookup;
/**
 * Zero-overhead primitive bit-packing and unpacking utilities.
 */
public final class Pack {

    @Required
    public static final int CLASS_ID = TypeRegister.ID_PACK;

    private Pack() {}

    public static int classId() {
        return CLASS_ID;
    }

    // 2 bytes to short
    public static short pack(byte b1, byte b2) {
        return (short) (((b1 & 0xFF) << 8) | (b2 & 0xFF));
    }

    public static byte unpackLeftByte(short value) {
        return (byte) ((value >>> 8) & 0xFF);
    }

    public static byte unpackRightByte(short value) {
        return (byte) (value & 0xFF);
    }

    // 2 shorts or 4 bytes to int
    public static int pack(short leftShort, short rightShort) {
        return ((leftShort & 0xFFFF) << 16) | (rightShort & 0xFFFF);
    }

    public static int pack(byte b1, byte b2, byte b3, byte b4) {
        return ((b1 & 0xFF) << 24) |
               ((b2 & 0xFF) << 16) |
               ((b3 & 0xFF) << 8)  |
               (b4 & 0xFF);
    }

    public static short unpackLeftShort(int value) {
        return (short) ((value >>> 16) & 0xFFFF);
    }

    public static short unpackRightShort(int value) {
        return (short) (value & 0xFFFF);
    }

    public static byte unpackByte(int value, int byteIndex) {
        if (byteIndex < 0 || byteIndex > 3) {
            throw new IndexOutOfBoundsException(StringLookup.getJavaString(295) + byteIndex + StringLookup.getJavaString(296));
        }
        int shift = (3 - byteIndex) * 8;
        return (byte) ((value >>> shift) & 0xFF);
    }

    // 2 ints, 4 shorts, or 8 bytes to long
    public static long pack(int leftInt, int rightInt) {
        return (((long) leftInt & 0xFFFFFFFFL) << 32) | ((long) rightInt & 0xFFFFFFFFL);
    }

    public static long pack(short s1, short s2, short s3, short s4) {
        return (((long) s1 & 0xFFFFL) << 48) |
               (((long) s2 & 0xFFFFL) << 32) |
               (((long) s3 & 0xFFFFL) << 16) |
               ((long) s4 & 0xFFFFL);
    }

    public static long pack(byte b1, byte b2, byte b3, byte b4, byte b5, byte b6, byte b7, byte b8) {
        return (((long) b1 & 0xFFL) << 56) |
               (((long) b2 & 0xFFL) << 48) |
               (((long) b3 & 0xFFL) << 40) |
               (((long) b4 & 0xFFL) << 32) |
               (((long) b5 & 0xFFL) << 24) |
               (((long) b6 & 0xFFL) << 16) |
               (((long) b7 & 0xFFL) << 8)  |
               ((long) b8 & 0xFFL);
    }

    public static int unpackLeftInt(long value) {
        return (int) (value >>> 32);
    }

    public static int unpackRightInt(long value) {
        return (int) value;
    }

    public static short unpackShort(long value, int shortIndex) {
        if (shortIndex < 0 || shortIndex > 3) {
            throw new IndexOutOfBoundsException(StringLookup.getJavaString(297) + shortIndex + StringLookup.getJavaString(296));
        }
        int shift = (3 - shortIndex) * 16;
        return (short) ((value >>> shift) & 0xFFFF);
    }

    public static byte unpackByte(long value, int byteIndex) {
        if (byteIndex < 0 || byteIndex > 7) {
            throw new IndexOutOfBoundsException(StringLookup.getJavaString(295) + byteIndex + StringLookup.getJavaString(298));
        }
        int shift = (7 - byteIndex) * 8;
        return (byte) ((value >>> shift) & 0xFF);
    }
}
