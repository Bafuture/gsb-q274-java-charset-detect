package com.example.gsb.charset;

import java.nio.charset.Charset;

/**
 * 针对四种编码的字节级严格扫描器：校验结构合法性并统计字节分布，
 * 不做评分。被检测器（启发式判定）与转换器（错误定位）共用。
 *
 * <p>“样本截断”与“非法序列”被区分开：流式检测只读前缀时，末尾可能
 * 恰好切断一个多字节序列，这不作为非法证据，仅标记 {@code truncated}。
 */
final class CharsetScans {

    static final Charset GBK = Charset.forName("GBK");

    private CharsetScans() {
    }

    /**
     * @param valid             结构是否全部合法
     * @param firstIllegalOffset 第一个非法字节的偏移（相对数组起始），合法时为 -1
     * @param units             解析出的字符单元数
     * @param asciiUnits        其中 ASCII 单元数
     * @param sensibleUnits     落在常见字符范围的单元数
     * @param puaUnits          解码到 Unicode 私用区的单元数（仅 GBK 统计）
     * @param nonSensibleUnits  落在罕见/控制范围的单元数
     * @param c1Units           C1 控制字符（0x80-0x9F）数（仅 ISO-8859-1 统计）
     * @param zerosAtEven       偶数偏移为零字节的单元数（仅 UTF-16 统计）
     * @param zerosAtOdd        奇数偏移为零字节的单元数（仅 UTF-16 统计）
     * @param truncated         末尾是否存在被截断的多字节序列
     * @param cjkPunctUnits     CJK 标点/全角单元数（仅 UTF-16 统计，用于排除偶然合法解析）
     * @param nulUnits          0x00 字节数（仅 ISO-8859-1 统计）
     * @param spaceUnits        0x20 空格数（仅 ISO-8859-1 统计）
     * @param highBytes         高位（>=0x80）字节数（仅 ISO-8859-1 统计）
     * @param adjacentHighPairs 相邻高位字节对数（仅 ISO-8859-1 统计）
     */
    record Scan(boolean valid, int firstIllegalOffset, int units, int asciiUnits,
                int sensibleUnits, int puaUnits, int nonSensibleUnits,
                int c1Units, int zerosAtEven, int zerosAtOdd, boolean truncated,
                int cjkPunctUnits, int nulUnits, int spaceUnits,
                int highBytes, int adjacentHighPairs) {

        static Scan invalid(int offset) {
            return new Scan(false, offset, 0, 0, 0, 0, 0, 0, 0, 0, false, 0, 0, 0, 0, 0);
        }

        static Scan of(int units, int ascii, int sensible, int pua, int nonSensible,
                       int c1, int zEven, int zOdd, boolean truncated,
                       int cjkPunct, int nul, int space, int high, int adjHigh) {
            return new Scan(true, -1, units, ascii, sensible, pua, nonSensible,
                    c1, zEven, zOdd, truncated, cjkPunct, nul, space, high, adjHigh);
        }
    }

    /** 常见字符范围：ASCII 可打印、拉丁系、希腊、西里尔、常见标点货币、假名、CJK、全角、emoji 等。 */
    static boolean isSensibleCodePoint(int cp) {
        if (cp == 0x09 || cp == 0x0A || cp == 0x0D) {
            return true;
        }
        if (cp >= 0x20 && cp <= 0x7E) {
            return true;
        }
        if (cp >= 0xA0 && cp <= 0x24F) {
            return true; // Latin-1 补充 + 拉丁扩展
        }
        if (cp >= 0x370 && cp <= 0x4FF) {
            return true; // 希腊、西里尔
        }
        if (cp >= 0x590 && cp <= 0x6FF) {
            return true; // 希伯来、阿拉伯
        }
        if (cp >= 0x900 && cp <= 0x97F) {
            return true; // 天城文
        }
        if (cp >= 0xE00 && cp <= 0xE7F) {
            return true; // 泰文
        }
        if (cp >= 0x2000 && cp <= 0x20BF) {
            return true; // 常用标点、货币符号
        }
        if (cp >= 0x3000 && cp <= 0x30FF) {
            return true; // CJK 标点、假名
        }
        if (cp >= 0x4E00 && cp <= 0x9FFF) {
            return true; // CJK 统一表意文字
        }
        if (cp >= 0xAC00 && cp <= 0xD7AF) {
            return true; // 韩文音节
        }
        if (cp >= 0xFF00 && cp <= 0xFFEF) {
            return true; // 全角/半角
        }
        return cp >= 0x1F300 && cp <= 0x1FAFF; // 常见 emoji
    }

    static boolean isSensibleAscii(int b) {
        return b == 0x09 || b == 0x0A || b == 0x0D || b >= 0x20;
    }

    /** 严格 UTF-8 扫描：拒绝过短编码、孤立后续字节、代理区与超界码点。 */
    static Scan scanUtf8(byte[] b, int from, int to) {
        int units = 0, ascii = 0, sensible = 0, nonSensible = 0;
        boolean truncated = false;
        int i = from;
        while (i < to) {
            int x = b[i] & 0xFF;
            if (x < 0x80) {
                units++;
                ascii++;
                if (isSensibleAscii(x)) {
                    sensible++;
                } else {
                    nonSensible++;
                }
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
                return Scan.invalid(i); // C0/C1 过短编码、孤立后续字节、F5-FF
            }
            if (i + need >= to) {
                truncated = true; // 样本末尾截断，不视为非法
                break;
            }
            for (int k = 1; k <= need; k++) {
                int c = b[i + k] & 0xFF;
                if (c < 0x80 || c > 0xBF) {
                    return Scan.invalid(i + k);
                }
                cp = (cp << 6) | (c & 0x3F);
            }
            if (cp < min || cp > 0x10FFFF || (cp >= 0xD800 && cp <= 0xDFFF)) {
                return Scan.invalid(i);
            }
            units++;
            if (isSensibleCodePoint(cp)) {
                sensible++;
            } else {
                nonSensible++;
            }
            i += need + 1;
        }
        return Scan.of(units, ascii, sensible, 0, nonSensible, 0, 0, 0, truncated, 0, 0, 0, 0, 0);
    }

    /** 严格 UTF-16 扫描：校验代理对配对，统计零字节对齐与 CJK 标点（排除偶然合法解析）。 */
    static Scan scanUtf16(byte[] b, int from, int to, boolean littleEndian) {
        if (to - from < 2) {
            return Scan.invalid(from);
        }
        int units = 0, ascii = 0, sensible = 0, nonSensible = 0, zEven = 0, zOdd = 0, cjkPunct = 0;
        boolean truncated = false;
        int i = from;
        while (i + 1 < to) {
            int b0 = b[i] & 0xFF;
            int b1 = b[i + 1] & 0xFF;
            if (b0 == 0) {
                zEven++;
            }
            if (b1 == 0) {
                zOdd++;
            }
            int u = littleEndian ? (b1 << 8) | b0 : (b0 << 8) | b1;
            int cp;
            int step;
            if (u >= 0xD800 && u <= 0xDBFF) {
                if (i + 3 >= to) {
                    truncated = true;
                    break;
                }
                int c0 = b[i + 2] & 0xFF;
                int c1 = b[i + 3] & 0xFF;
                int u2 = littleEndian ? (c1 << 8) | c0 : (c0 << 8) | c1;
                if (u2 < 0xDC00 || u2 > 0xDFFF) {
                    return Scan.invalid(i + 2);
                }
                cp = 0x10000 + ((u - 0xD800) << 10) + (u2 - 0xDC00);
                step = 4;
            } else if (u >= 0xDC00 && u <= 0xDFFF) {
                return Scan.invalid(i); // 孤立低代理
            } else {
                cp = u;
                step = 2;
            }
            units++;
            if (cp < 0x80) {
                ascii++;
            }
            if (isSensibleCodePoint(cp)) {
                sensible++;
            } else {
                nonSensible++;
            }
            if ((cp >= 0x3000 && cp <= 0x303F) || (cp >= 0xFF00 && cp <= 0xFFEF)) {
                cjkPunct++;
            }
            i += step;
        }
        if (i == to - 1) {
            truncated = true; // 奇数字节，末尾缺失一半码元
        }
        return Scan.of(units, ascii, sensible, 0, nonSensible, 0, zEven, zOdd, truncated,
                cjkPunct, 0, 0, 0, 0);
    }

    /**
     * GBK 扫描：结构校验（前导 0x81-0xFE、后续 0x40-0xFE 且非 0x7F、
     * 拒绝单字节 0x80/0xFF），合法双字节对交给 JDK 解码做语义统计。
     */
    static Scan scanGbk(byte[] b, int from, int to) {
        int units = 0, ascii = 0, sensible = 0, pua = 0, nonSensible = 0;
        boolean truncated = false;
        int i = from;
        while (i < to) {
            int x = b[i] & 0xFF;
            if (x < 0x80) {
                units++;
                ascii++;
                if (isSensibleAscii(x)) {
                    sensible++;
                } else {
                    nonSensible++;
                }
                i++;
                continue;
            }
            if (x == 0x80 || x == 0xFF) {
                return Scan.invalid(i);
            }
            if (i + 1 >= to) {
                truncated = true;
                break;
            }
            int y = b[i + 1] & 0xFF;
            if (y < 0x40 || y == 0x7F || y == 0xFF) {
                return Scan.invalid(i + 1);
            }
            int cp = new String(b, i, 2, GBK).codePointAt(0);
            units++;
            if (Character.getType(cp) == Character.PRIVATE_USE) {
                pua++;
            } else if (isSensibleCodePoint(cp)) {
                sensible++;
            } else {
                nonSensible++;
            }
            i += 2;
        }
        return Scan.of(units, ascii, sensible, pua, nonSensible, 0, 0, 0, truncated, 0, 0, 0, 0, 0);
    }

    /**
     * ISO-8859-1 任何字节都合法，只统计分布：C1 控制、NUL、空格、
     * 高位字节占比与相邻高位字节对（区分真实拉丁文本与多字节编码的反向证据）。
     */
    static Scan scanIso88591(byte[] b, int from, int to) {
        int units = 0, ascii = 0, sensible = 0, c1 = 0, nonSensible = 0;
        int nul = 0, space = 0, high = 0, adjHigh = 0;
        boolean prevHigh = false;
        for (int i = from; i < to; i++) {
            int x = b[i] & 0xFF;
            units++;
            if (x == 0x00) {
                nul++;
            }
            if (x == 0x20) {
                space++;
            }
            if (x < 0x80) {
                ascii++;
                if (isSensibleAscii(x)) {
                    sensible++;
                } else {
                    nonSensible++;
                }
                prevHigh = false;
            } else {
                high++;
                if (prevHigh) {
                    adjHigh++;
                }
                prevHigh = true;
                if (x < 0xA0) {
                    c1++;
                } else {
                    sensible++;
                }
            }
        }
        return Scan.of(units, ascii, sensible, 0, nonSensible, c1, 0, 0, false,
                0, nul, space, high, adjHigh);
    }
}
