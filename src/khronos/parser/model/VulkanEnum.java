package khronos.parser.model;

public record VulkanEnum(
    String name,
    String value,
    String bitpos,
    String comment,
    boolean isAlias,
    String aliasOf,
    String extendsEnum,
    String extNumber,
    String offset,
    String dir
) {
    public long getNumericValue() {
        if (value != null) {
            try {
                if (value.startsWith("0x") || value.startsWith("0X")) {
                    return Long.parseUnsignedLong(value.substring(2), 16);
                }
                return Long.parseLong(value);
            } catch (NumberFormatException e) {
                return 0L;
            }
        }
        if (bitpos != null) {
            try {
                int pos = Integer.parseInt(bitpos);
                return 1L << pos;
            } catch (NumberFormatException e) {
                return 0L;
            }
        }
        if (offset != null) {
            try {
                long ext = extNumber != null ? Long.parseLong(extNumber) : 0L;
                long off = Long.parseLong(offset);
                long val = 1000000000L + (ext - 1L) * 1000L + off;
                if ("-".equals(dir)) {
                    val = -val;
                }
                return val;
            } catch (NumberFormatException e) {
                return 0L;
            }
        }
        return 0L;
    }
}
