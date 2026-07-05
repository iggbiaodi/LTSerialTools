package indi.lt.serialtool.utils;

/**
 * @author Nonoas
 * @date 2025/9/23
 * @since
 */
public class StringUtil {
    private static final char[] HEX_CHARS = "0123456789ABCDEF".toCharArray();

    /**
     * HEX 转 byte
     */
    public static byte[] hexStringToBytes(String hex) {
        if (hex == null) {
            throw new IllegalArgumentException("HEX 内容不能为空");
        }
        hex = hex.replaceAll("\\s+", "");
        if (hex.isEmpty()) {
            return new byte[0];
        }
        if (hex.length() % 2 != 0) {
            hex = "0" + hex;
        }

        byte[] result = new byte[hex.length() / 2];
        for (int i = 0; i < result.length; i++) {
            int hi = Character.digit(hex.charAt(i * 2), 16);
            int lo = Character.digit(hex.charAt(i * 2 + 1), 16);
            if (hi < 0 || lo < 0) {
                throw new IllegalArgumentException("非法 HEX 字符: " + hex.substring(i * 2, i * 2 + 2));
            }
            result[i] = (byte) ((hi << 4) | lo);
        }
        return result;
    }

    /**
     * byte[] 转 HEX（大写，空格分隔）
     */
    public static String bytesToHexString(byte[] bytes) {
        if (bytes == null) {
            return "";
        }
        return bytesToHexString(bytes, bytes.length);
    }

    /**
     * byte[] 前 length 个字节转 HEX（大写，空格分隔）
     */
    public static String bytesToHexString(byte[] bytes, int length) {
        if (bytes == null || length <= 0) {
            return "";
        }
        int safeLength = Math.min(length, bytes.length);
        StringBuilder sb = new StringBuilder(safeLength * 3 - 1);
        for (int i = 0; i < safeLength; i++) {
            int v = bytes[i] & 0xFF;
            sb.append(HEX_CHARS[v >>> 4]).append(HEX_CHARS[v & 0x0F]);
            if (i < safeLength - 1) {
                sb.append(' ');
            }
        }
        return sb.toString();
    }
}
