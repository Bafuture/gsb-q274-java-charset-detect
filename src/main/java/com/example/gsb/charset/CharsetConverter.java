package com.example.gsb.charset;

import java.nio.charset.StandardCharsets;
import java.util.Objects;

/**
 * 字符集转换器：按判定结果把字节序列解码为字符串。
 *
 * <p>解码采用严格模式，任何非法序列都不会被替换字符静默吞掉；
 * 失败时 {@link DecodeResult#errorOffset()} 指向原始字节数组中第一个出错
 * 字节的偏移（含 BOM 前缀长度）。检测未收敛时直接返回失败，
 * 避免“随便选一个编码”导致乱码。
 */
public final class CharsetConverter {

    /** 先检测，检测收敛后解码；未收敛则返回失败结果。 */
    public DecodeResult decode(byte[] data) {
        DetectionResult detection = new CharsetDetector().detect(data);
        return decode(data, detection);
    }

    /** 按已有检测结果解码。 */
    public DecodeResult decode(byte[] data, DetectionResult detection) {
        Objects.requireNonNull(data, "data");
        Objects.requireNonNull(detection, "detection");
        if (detection.best().isEmpty()) {
            return DecodeResult.failure(-1, "检测未收敛，拒绝猜测编码：" + detection.summary());
        }
        return decodeAs(data, detection.best().get().family());
    }

    /** 显式指定编码解码（用于 BOM 冲突等需要人工裁决的场景）。 */
    public DecodeResult decodeAs(byte[] data, CharsetFamily family) {
        Objects.requireNonNull(data, "data");
        Objects.requireNonNull(family, "family");
        int off = 0;
        BomInfo bom = BomInfo.sniff(data, 0, data.length);
        if (bom != null && bom.family() == family) {
            off = bom.length();
        }
        return switch (family) {
            case UTF_8 -> decodeUtf8(data, off);
            case UTF_16LE -> decodeUtf16(data, off, true);
            case UTF_16BE -> decodeUtf16(data, off, false);
            case GBK -> decodeGbk(data, off);
            case ISO_8859_1 -> DecodeResult.ok(new String(data, off, data.length - off,
                    StandardCharsets.ISO_8859_1));
        };
    }

    private static DecodeResult decodeUtf8(byte[] b, int from) {
        StringBuilder sb = new StringBuilder();
        int i = from;
        while (i < b.length) {
            int x = b[i] & 0xFF;
            if (x < 0x80) {
                sb.append((char) x);
                i++;
                continue;
            }
            int need;
            int cp;
            int min;
            if (x >= 0xC2 && x <= 0xDF) {
                need = 1; cp = x & 0x1F; min = 0x80;
            } else if (x >= 0xE0 && x <= 0xEF) {
                need = 2; cp = x & 0x0F; min = 0x800;
            } else if (x >= 0xF0 && x <= 0xF4) {
                need = 3; cp = x & 0x07; min = 0x10000;
            } else {
                return DecodeResult.failure(i, String.format("非法 UTF-8 前导字节 0x%02X", x));
            }
            if (i + need >= b.length) {
                return DecodeResult.failure(i, "UTF-8 多字节序列在数据末尾被截断");
            }
            for (int k = 1; k <= need; k++) {
                int c = b[i + k] & 0xFF;
                if (c < 0x80 || c > 0xBF) {
                    return DecodeResult.failure(i + k, String.format("非法 UTF-8 后续字节 0x%02X", c));
                }
                cp = (cp << 6) | (c & 0x3F);
            }
            if (cp < min || cp > 0x10FFFF || (cp >= 0xD800 && cp <= 0xDFFF)) {
                return DecodeResult.failure(i, String.format("UTF-8 解码出非法码点 U+%04X", cp));
            }
            sb.appendCodePoint(cp);
            i += need + 1;
        }
        return DecodeResult.ok(sb.toString());
    }

    private static DecodeResult decodeUtf16(byte[] b, int from, boolean littleEndian) {
        StringBuilder sb = new StringBuilder();
        int i = from;
        while (i + 1 < b.length) {
            int u = readUnit(b, i, littleEndian);
            if (u >= 0xD800 && u <= 0xDBFF) {
                if (i + 3 >= b.length) {
                    return DecodeResult.failure(i, "UTF-16 高代理在数据末尾缺少低代理");
                }
                int u2 = readUnit(b, i + 2, littleEndian);
                if (u2 < 0xDC00 || u2 > 0xDFFF) {
                    return DecodeResult.failure(i + 2,
                            String.format("高代理后不是低代理，而是 U+%04X", u2));
                }
                int cp = 0x10000 + ((u - 0xD800) << 10) + (u2 - 0xDC00);
                sb.appendCodePoint(cp);
                i += 4;
            } else if (u >= 0xDC00 && u <= 0xDFFF) {
                return DecodeResult.failure(i, String.format("孤立的 UTF-16 低代理 U+%04X", u));
            } else {
                sb.append((char) u);
                i += 2;
            }
        }
        if (i < b.length) {
            return DecodeResult.failure(i, "UTF-16 数据长度为奇数，末尾缺失一个字节");
        }
        return DecodeResult.ok(sb.toString());
    }

    private static int readUnit(byte[] b, int i, boolean littleEndian) {
        int b0 = b[i] & 0xFF;
        int b1 = b[i + 1] & 0xFF;
        return littleEndian ? (b1 << 8) | b0 : (b0 << 8) | b1;
    }

    private static DecodeResult decodeGbk(byte[] b, int from) {
        CharsetScans.Scan scan = CharsetScans.scanGbk(b, from, b.length);
        if (!scan.valid()) {
            return DecodeResult.failure(scan.firstIllegalOffset(), "非法 GBK 字节序列");
        }
        if (scan.truncated()) {
            return DecodeResult.failure(b.length - 1, "GBK 双字节序列在数据末尾被截断");
        }
        return DecodeResult.ok(new String(b, from, b.length - from, CharsetScans.GBK));
    }
}
