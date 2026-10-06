package com.example.gsb.charset;

import java.util.ArrayList;
import java.util.List;

/**
 * 无 BOM 时的启发式判定：综合非法序列、字节分布与常见字符范围，
 * 为每种候选编码计算置信度并给出判定依据。
 *
 * <h3>评分模型</h3>
 * <ul>
 *   <li>UTF-8：严格校验，非法即 0 分；纯 ASCII 0.30；含多字节字符 0.90 起，
 *       多字节字符越多越接近 0.99。</li>
 *   <li>UTF-16：依据零字节位置（ASCII 文本）与 CJK 高字节 [0x4E,0x9F] 位置
 *       判断大小端，证据占比 &gt; 0.6 时 0.80-0.95。</li>
 *   <li>GBK：结构非法即 0 分；纯 ASCII 0.30；否则 0.55 起，按常用汉字区
 *       （首字节 B0-F7、次字节 A1-FE）占比与双字节数量加分，封顶 0.97；
 *       无常用汉字区字符时封顶 0.50（短序列偶然合法的可信度有限）。</li>
 *   <li>ISO-8859-1：任意字节均合法，纯 ASCII 0.30；含高位字节时 0.50 起，
 *       C1 控制区（0x80-0x9F，真实文本罕见）扣分，Latin-1 字母区加分。</li>
 * </ul>
 *
 * <h3>交叉校准</h3>
 * <ul>
 *   <li>内容同时是合法 UTF-8（≥2 个多字节字符）时，GBK 候选压至 0.45：
 *       真实 GBK 文本长序列恰好是合法 UTF-8 的概率极低。</li>
 *   <li>UTF-16 分布证据占比 &gt; 0.6 时，GBK 候选压至 0.45：
 *       真实 GBK 文本的奇数字节不会稳定落在 CJK 高字节区。</li>
 * </ul>
 */
final class Heuristics {

    /** 纯 ASCII 内容的置信度：与 UTF-8/GBK/ISO-8859-1 均兼容，无法区分。 */
    static final double ASCII_ONLY_CONFIDENCE = 0.30;

    private Heuristics() {
    }

    /**
     * 对 {@code data[off, off+len)} 计算全部候选编码的置信度。
     *
     * @param tailTolerant 输入是否为可能被截断的采样（流式检测）
     * @return 按置信度降序排列的候选列表（含被排除的 0 分候选）
     */
    static List<EncodingCandidate> score(byte[] data, int off, int len, boolean tailTolerant) {
        List<EncodingCandidate> out = new ArrayList<>();
        if (len == 0) {
            for (Encoding e : Encoding.values()) {
                out.add(new EncodingCandidate(e, 0.0, "输入为空，无法分析"));
            }
            return out;
        }

        // ---- UTF-8：非法序列一票否决 ----
        ByteValidators.Utf8Stats u8 = ByteValidators.scanUtf8(data, off, len, tailTolerant);
        double u8Score;
        String u8Reason;
        if (u8.errors > 0) {
            u8Score = 0.0;
            u8Reason = String.format("发现 %d 处非法 UTF-8 序列（首处位于字节偏移 %d），排除",
                    u8.errors, off + u8.firstError);
        } else if (u8.multibyte == 0) {
            u8Score = ASCII_ONLY_CONFIDENCE;
            u8Reason = "纯 ASCII，UTF-8/GBK/ISO-8859-1 均兼容，无法据此区分";
        } else {
            u8Score = 0.90 + 0.09 * Math.min(1.0, u8.multibyte / 20.0);
            u8Reason = String.format("严格合法 UTF-8，含 %d 个多字节字符", u8.multibyte);
        }
        out.add(new EncodingCandidate(Encoding.UTF_8, u8Score, u8Reason));

        // ---- UTF-16：零字节 / CJK 高字节分布 ----
        // 注意：ASCII 字母（0x4E-0x7A）恰好落在 CJK 高字节区，纯 ASCII 文本错位配对后
        // 会呈现大量“伪 CJK 单元”。因此 CJK 证据需通过可信度校验：真实 UTF-16 CJK
        // 单元的低字节近似均匀分布（可打印 ASCII 占比约 37%、高位字节约 50%），
        // 而伪 CJK 单元的低字节几乎全是可打印 ASCII 或全为 0x00。
        ByteValidators.Utf16Stats u16 = ByteValidators.scanUtf16Distribution(data, off, len);
        boolean beCjkPlausible = u16.beCjk >= 4
                && u16.beCjkLoPrintable / (double) u16.beCjk < 0.6
                && u16.beCjkLoHigh / (double) u16.beCjk > 0.1;
        boolean leCjkPlausible = u16.leCjk >= 4
                && u16.leCjkLoPrintable / (double) u16.leCjk < 0.6
                && u16.leCjkLoHigh / (double) u16.leCjk > 0.1;
        double beEvidence = u16.pairs == 0 ? 0
                : (u16.evenNulls + (beCjkPlausible ? u16.beCjk : 0)) / (double) u16.pairs;
        double leEvidence = u16.pairs == 0 ? 0
                : (u16.oddNulls + (leCjkPlausible ? u16.leCjk : 0)) / (double) u16.pairs;
        out.add(utf16Candidate(Encoding.UTF_16BE, beEvidence, len,
                "偶数字节为零/CJK 高字节的比例 %.0f%%", beEvidence));
        out.add(utf16Candidate(Encoding.UTF_16LE, leEvidence, len,
                "奇数字节为零/CJK 高字节的比例 %.0f%%", leEvidence));

        // ---- GBK：结构校验 + 常用汉字区占比 ----
        ByteValidators.GbkStats gbk = ByteValidators.scanGbk(data, off, len, tailTolerant);
        double gbkScore;
        String gbkReason;
        if (gbk.errors > 0) {
            gbkScore = 0.0;
            gbkReason = String.format("发现 %d 处非法 GBK 序列（首处位于字节偏移 %d），排除",
                    gbk.errors, off + gbk.firstError);
        } else if (gbk.pairs == 0) {
            gbkScore = ASCII_ONLY_CONFIDENCE;
            gbkReason = "纯 ASCII，UTF-8/GBK/ISO-8859-1 均兼容，无法据此区分";
        } else {
            double commonRatio = gbk.commonPairs / (double) gbk.pairs;
            gbkScore = 0.55
                    + 0.35 * commonRatio * Math.min(1.0, gbk.pairs / 3.0)
                    + 0.10 * Math.min(1.0, gbk.pairs / 20.0);
            gbkReason = String.format("%d 个合法 GBK 双字节字符，其中 %.0f%% 落在常用汉字区",
                    gbk.pairs, commonRatio * 100);
            if (gbk.commonPairs == 0) {
                gbkScore = Math.min(gbkScore, 0.50);
                gbkReason += "；但无一落在常用汉字区，疑似偶然合法，降权";
            }
            gbkScore = Math.min(gbkScore, 0.97);
        }

        // ---- ISO-8859-1：高位字节分布 ----
        ByteValidators.Latin1Stats latin = ByteValidators.scanLatin1(data, off, len);
        double latinScore;
        String latinReason;
        if (latin.high == 0) {
            latinScore = ASCII_ONLY_CONFIDENCE;
            latinReason = "纯 ASCII，UTF-8/GBK/ISO-8859-1 均兼容，无法据此区分";
        } else {
            latinScore = 0.50
                    - 0.30 * (latin.c1 / (double) latin.high)
                    + 0.05 * Math.min(1.0, latin.high / 10.0)
                    + 0.15 * (latin.letters / (double) latin.high);
            latinReason = String.format("含 %d 个高位字节（C1 控制区 %d 个，Latin-1 字母区 %d 个）",
                    latin.high, latin.c1, latin.letters);
        }

        // ---- 交叉校准 ----
        if (u8.errors == 0 && u8.multibyte >= 2) {
            if (gbkScore > 0.45) {
                gbkScore = 0.45;
                gbkReason += "；但内容同时是合法 UTF-8（真实 GBK 长序列恰好合法 UTF-8 的概率极低），降权";
            }
            if (latinScore > 0.45) {
                latinScore = 0.45;
                latinReason += "；但内容同时是合法 UTF-8（真实 Latin-1 长序列恰好合法 UTF-8 的概率极低），降权";
            }
        }
        if (Math.max(beEvidence, leEvidence) > 0.6 && gbkScore > 0.45) {
            gbkScore = 0.45;
            gbkReason += "；但字节分布高度符合 UTF-16 特征，降权";
        }

        out.add(new EncodingCandidate(Encoding.GBK, gbkScore, gbkReason));
        out.add(new EncodingCandidate(Encoding.ISO_8859_1, latinScore, latinReason));

        out.sort(EncodingCandidate::compareTo);
        return out;
    }

    private static EncodingCandidate utf16Candidate(Encoding enc, double evidence, int len,
                                                    String fmt, double fmtArg) {
        double score;
        String reason;
        if (len < 4) {
            score = 0.05;
            reason = "样本不足 4 字节，无法判断 UTF-16 分布特征";
        } else if (evidence > 0.6) {
            score = 0.80 + 0.15 * Math.min(1.0, evidence);
            reason = String.format(fmt + "，符合该字节序特征", fmtArg * 100);
        } else if (evidence > 0.3) {
            score = 0.55;
            reason = String.format(fmt + "，有一定特征但不充分", fmtArg * 100);
        } else if (evidence > 0.1) {
            score = 0.30;
            reason = String.format(fmt + "，特征微弱", fmtArg * 100);
        } else {
            score = 0.05;
            reason = String.format(fmt + "，基本不符合该字节序特征", fmtArg * 100);
        }
        return new EncodingCandidate(enc, score, reason);
    }
}
