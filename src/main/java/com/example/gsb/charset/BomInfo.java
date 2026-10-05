package com.example.gsb.charset;

/** 字节序标记（BOM）识别结果。 */
public record BomInfo(CharsetFamily family, byte[] bytes) {

    public int length() {
        return bytes.length;
    }

    public String hexString() {
        StringBuilder sb = new StringBuilder();
        for (byte b : bytes) {
            sb.append(String.format("%02X ", b));
        }
        return sb.toString().trim();
    }

    /**
     * 嗅探字节序列开头的 BOM。仅识别 UTF-8 / UTF-16LE / UTF-16BE 三种，
     * GBK 与 ISO-8859-1 没有标准 BOM。
     */
    public static BomInfo sniff(byte[] data, int off, int len) {
        if (len >= 3
                && (data[off] & 0xFF) == 0xEF
                && (data[off + 1] & 0xFF) == 0xBB
                && (data[off + 2] & 0xFF) == 0xBF) {
            return new BomInfo(CharsetFamily.UTF_8, new byte[] {(byte) 0xEF, (byte) 0xBB, (byte) 0xBF});
        }
        if (len >= 2 && (data[off] & 0xFF) == 0xFF && (data[off + 1] & 0xFF) == 0xFE) {
            return new BomInfo(CharsetFamily.UTF_16LE, new byte[] {(byte) 0xFF, (byte) 0xFE});
        }
        if (len >= 2 && (data[off] & 0xFF) == 0xFE && (data[off + 1] & 0xFF) == 0xFF) {
            return new BomInfo(CharsetFamily.UTF_16BE, new byte[] {(byte) 0xFE, (byte) 0xFF});
        }
        return null;
    }
}
