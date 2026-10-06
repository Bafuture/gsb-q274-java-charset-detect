package com.example.gsb.charset;

/**
 * 各编码的字节级扫描器：统计非法序列、字符分布等，为启发式判定提供证据。
 *
 * <p>所有扫描器支持 {@code tailTolerant} 模式：流式采样时输入可能在多字节字符
 * 中间被截断，末尾不完整的序列不计为非法。</p>
 */
final class ByteValidators {

    private ByteValidators() {
    }

    // ---------------------------------------------------------------- UTF-8

    static final class Utf8Stats {
        int errors;
        int firstError = -1; // 相对偏移
        int multibyte;
        int ascii;
    }

    /** 按 RFC 3629 严格校验 UTF-8（拒绝过长编码、代理区、超出 U+10FFFF）。 */
    static Utf8Stats scanUtf8(byte[] d, int off, int len, boolean tailTolerant) {
        Utf8Stats s = new Utf8Stats();
        int end = off + len;
        int i = off;
        while (i < end) {
            int b = d[i] & 0xFF;
            if (b < 0x80) {
                s.ascii++;
                i++;
                continue;
            }
            int need;          // 需要的延续字节数
            int lo = 0x80, hi = 0xBF; // 第一个延续字节的合法范围
            if (b >= 0xC2 && b <= 0xDF) {
                need = 1;
            } else if (b == 0xE0) {
                need = 2;
                lo = 0xA0;
            } else if ((b >= 0xE1 && b <= 0xEC) || (b >= 0xEE && b <= 0xEF)) {
                need = 2;
            } else if (b == 0xED) {
                need = 2;
                hi = 0x9F;
            } else if (b == 0xF0) {
                need = 3;
                lo = 0x90;
            } else if (b >= 0xF1 && b <= 0xF3) {
                need = 3;
            } else if (b == 0xF4) {
                need = 3;
                hi = 0x8F;
            } else { // 0x80-0xC1（游离延续字节/过 long）、0xF5-0xFF
                utf8Error(s, i - off);
                i++;
                continue;
            }
            if (i + need >= end) { // 序列被截断
                if (tailTolerant) {
                    break;
                }
                utf8Error(s, i - off);
                break;
            }
            int c1 = d[i + 1] & 0xFF;
            if (c1 < lo || c1 > hi) {
                utf8Error(s, i - off);
                i++;
                continue;
            }
            boolean ok = true;
            for (int k = 2; k <= need; k++) {
                int c = d[i + k] & 0xFF;
                if (c < 0x80 || c > 0xBF) {
                    ok = false;
                    break;
                }
            }
            if (!ok) {
                utf8Error(s, i - off);
                i++;
                continue;
            }
            s.multibyte++;
            i += need + 1;
        }
        return s;
    }

    private static void utf8Error(Utf8Stats s, int relOffset) {
        s.errors++;
        if (s.firstError < 0) {
            s.firstError = relOffset;
        }
    }

    // ----------------------------------------------------------------- GBK

    static final class GbkStats {
        int errors;
        int firstError = -1; // 相对偏移
        int pairs;           // 合法双字节字符数
        int commonPairs;     // 落在常用汉字区（lead B0-F7, trail A1-FE）的双字节字符数
        int ascii;
    }

    /**
     * 校验 GBK 字节结构：首字节 0x81-0xFE，次字节 0x40-0x7E 或 0x80-0xFE。
     * 0x80 与 0xFF 为非法单字节。
     */
    static GbkStats scanGbk(byte[] d, int off, int len, boolean tailTolerant) {
        GbkStats s = new GbkStats();
        int end = off + len;
        int i = off;
        while (i < end) {
            int b = d[i] & 0xFF;
            if (b < 0x80) {
                s.ascii++;
                i++;
                continue;
            }
            if (b >= 0x81 && b <= 0xFE) {
                if (i + 1 >= end) { // 末尾悬空首字节
                    if (tailTolerant) {
                        break;
                    }
                    gbkError(s, i - off);
                    break;
                }
                int t = d[i + 1] & 0xFF;
                if ((t >= 0x40 && t <= 0x7E) || (t >= 0x80 && t <= 0xFE)) {
                    s.pairs++;
                    if (b >= 0xB0 && b <= 0xF7 && t >= 0xA1 && t <= 0xFE) {
                        s.commonPairs++;
                    }
                    i += 2;
                } else {
                    gbkError(s, i - off);
                    i++;
                }
            } else { // 0x80、0xFF
                gbkError(s, i - off);
                i++;
            }
        }
        return s;
    }

    private static void gbkError(GbkStats s, int relOffset) {
        s.errors++;
        if (s.firstError < 0) {
            s.firstError = relOffset;
        }
    }

    // --------------------------------------------------------------- UTF-16

    static final class Utf16Stats {
        int pairs;     // 双字节单元数
        int evenNulls; // 偶数偏移为 0x00（UTF-16BE 的 ASCII 特征）
        int oddNulls;  // 奇数偏移为 0x00（UTF-16LE 的 ASCII 特征）
        int beCjk;     // 偶数字节落在 CJK 高字节区 [0x4E,0x9F]（BE 特征）
        int leCjk;     // 奇数字节落在 CJK 高字节区 [0x4E,0x9F]（LE 特征）
        // CJK 假设的可信度校验：真实 UTF-16 CJK 单元的低字节近似均匀分布，
        // 而 ASCII 文本错位配对出的“伪 CJK 单元”低字节仍是可打印 ASCII
        int beCjkLoPrintable; // BE CJK 候选单元的低字节落在 [0x20,0x7E]
        int beCjkLoHigh;      // BE CJK 候选单元的低字节 >= 0x80
        int leCjkLoPrintable; // LE CJK 候选单元的低字节落在 [0x20,0x7E]
        int leCjkLoHigh;      // LE CJK 候选单元的低字节 >= 0x80
    }

    /** 统计 UTF-16 字节分布特征（零字节位置 + CJK 高字节位置）。 */
    static Utf16Stats scanUtf16Distribution(byte[] d, int off, int len) {
        Utf16Stats s = new Utf16Stats();
        s.pairs = len / 2;
        for (int k = 0; k < s.pairs; k++) {
            int hi = d[off + 2 * k] & 0xFF;
            int lo = d[off + 2 * k + 1] & 0xFF;
            if (hi == 0) {
                s.evenNulls++;
            }
            if (lo == 0) {
                s.oddNulls++;
            }
            if (hi >= 0x4E && hi <= 0x9F) {
                s.beCjk++;
                if (lo >= 0x20 && lo <= 0x7E) {
                    s.beCjkLoPrintable++;
                }
                if (lo >= 0x80) {
                    s.beCjkLoHigh++;
                }
            }
            if (lo >= 0x4E && lo <= 0x9F) {
                s.leCjk++;
                if (hi >= 0x20 && hi <= 0x7E) {
                    s.leCjkLoPrintable++;
                }
                if (hi >= 0x80) {
                    s.leCjkLoHigh++;
                }
            }
        }
        return s;
    }

    /**
     * 校验 UTF-16 内容（用于 BOM 判定后的内容核对）：检查奇数长度与未配对代理项。
     *
     * @return 首个错误的相对偏移；-1 表示合法
     */
    static int firstUtf16Error(byte[] d, int off, int len, boolean littleEndian, boolean tailTolerant) {
        int units = len / 2;
        for (int k = 0; k < units; k++) {
            int hi = d[off + 2 * k] & 0xFF;
            int lo = d[off + 2 * k + 1] & 0xFF;
            int unit = littleEndian ? (lo << 8) | hi : (hi << 8) | lo;
            if (unit >= 0xD800 && unit <= 0xDBFF) {
                if (k + 1 < units) {
                    int hi2 = d[off + 2 * (k + 1)] & 0xFF;
                    int lo2 = d[off + 2 * (k + 1) + 1] & 0xFF;
                    int next = littleEndian ? (lo2 << 8) | hi2 : (hi2 << 8) | lo2;
                    if (next >= 0xDC00 && next <= 0xDFFF) {
                        k++;
                        continue;
                    }
                }
                return 2 * k;
            }
            if (unit >= 0xDC00 && unit <= 0xDFFF) {
                return 2 * k;
            }
        }
        if (len % 2 != 0 && !tailTolerant) {
            return len - 1; // 末尾悬空字节
        }
        return -1;
    }

    // ---------------------------------------------------------- ISO-8859-1

    static final class Latin1Stats {
        int high;    // >= 0x80 的字节数
        int c1;      // C1 控制区 [0x80,0x9F] 字节数（真实文本中罕见）
        int letters; // Latin-1 字母区 [0xC0,0xFF]（排除 ×、÷）字节数
    }

    static Latin1Stats scanLatin1(byte[] d, int off, int len) {
        Latin1Stats s = new Latin1Stats();
        for (int i = off; i < off + len; i++) {
            int b = d[i] & 0xFF;
            if (b >= 0x80) {
                s.high++;
                if (b <= 0x9F) {
                    s.c1++;
                }
                if (b >= 0xC0 && b != 0xD7 && b != 0xF7) {
                    s.letters++;
                }
            }
        }
        return s;
    }
}
