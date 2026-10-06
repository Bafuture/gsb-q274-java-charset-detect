package com.example.gsb.charset;

import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;

/**
 * 字符集检测入口。
 *
 * <h3>BOM 策略（优先级最高）</h3>
 * <ul>
 *   <li>{@code EF BB BF} → UTF-8；{@code FE FF} → UTF-16BE；{@code FF FE} → UTF-16LE。</li>
 *   <li>BOM 与内容<strong>不冲突</strong>（内容可按 BOM 编码合法解码）时，置信度 0.99，
 *       即使内容是纯 ASCII（启发式无法区分）也以 BOM 为准。</li>
 *   <li>BOM 与内容<strong>冲突</strong>（BOM 声明的编码下存在非法序列）时：
 *       仍按 BOM 优先判定（BOM 是写入方留下的显式声明，通常比字节统计更可信），
 *       标记 {@link DetectionResult#isBomContentConflict()}，置信度降为 0.60，
 *       备选启发式候选一律压至 0.50 以下并保留在结果中供人工核对；
 *       此时 {@link CharsetTranscoder#decode} 会在第一个非法字节处抛出
 *       {@link MalformedContentException}（带字节偏移），而不是静默返回乱码。</li>
 * </ul>
 *
 * <h3>无 BOM 时的判定规则</h3>
 * 由 {@link Heuristics} 给出全部候选的置信度；当最高置信度 ≥ 0.60 且与第二名差距 ≥ 0.15
 * 时才判定为确定，否则视为检测失败：返回多个候选与原因，拒绝盲目解码。
 *
 * <h3>流式检测</h3>
 * 对大文件 / 输入流只读前 N 字节（默认 65536）采样判定；采样在多字节字符中间截断时，
 * 末尾不完整序列不计为非法。
 *
 * <p><strong>局限</strong>：不支持 UTF-32（{@code FF FE 00 00} 会被当作 UTF-16LE）；
 * 纯 ASCII 无法区分（UTF-8/GBK/ISO-8859-1 皆兼容）；短样本可能天然歧义（如 Latin-1 的
 * "àé" 恰好是合法 GBK 对）；采样结果取决于文件开头内容，若开头是大量纯 ASCII 而后文才出现
 * 特征字节，应增大采样上限。</p>
 */
public final class CharsetDetector {

    /** 默认流式采样上限：64 KiB。 */
    public static final int DEFAULT_MAX_BYTES = 65536;

    private static final double DETERMINED_MIN_CONFIDENCE = 0.60;
    private static final double DETERMINED_MIN_MARGIN = 0.15;
    private static final double BOM_OK_CONFIDENCE = 0.99;
    private static final double BOM_CONFLICT_CONFIDENCE = 0.60;
    private static final double BOM_CONFLICT_ALTERNATIVE_CAP = 0.50;

    public CharsetDetector() {
    }

    // ---------------------------------------------------------- 字节数组

    public DetectionResult detect(byte[] data) {
        if (data == null) {
            throw new IllegalArgumentException("data 不能为 null");
        }
        return detect(data, 0, data.length, false);
    }

    public DetectionResult detect(byte[] data, int off, int len) {
        checkRange(data, off, len);
        return detect(data, off, len, false);
    }

    // ----------------------------------------------------------- 流式入口

    /** 对文件做流式检测，读取前 {@value #DEFAULT_MAX_BYTES} 字节。 */
    public DetectionResult detect(Path file) throws IOException {
        return detect(file, DEFAULT_MAX_BYTES);
    }

    /** 对文件做流式检测，只读取前 {@code maxBytes} 字节。 */
    public DetectionResult detect(Path file, int maxBytes) throws IOException {
        if (maxBytes <= 0) {
            throw new IllegalArgumentException("maxBytes 必须为正数");
        }
        try (InputStream in = Files.newInputStream(file)) {
            return detect(in, maxBytes);
        }
    }

    /** 对输入流做流式检测，读取前 {@value #DEFAULT_MAX_BYTES} 字节；本方法不关闭流。 */
    public DetectionResult detect(InputStream in) throws IOException {
        return detect(in, DEFAULT_MAX_BYTES);
    }

    /**
     * 对输入流做流式检测，只读取前 {@code maxBytes} 字节。本方法不关闭流。
     * 内部多读 1 字节以判断采样是否被截断；被截断时末尾的不完整多字节字符不计为非法。
     */
    public DetectionResult detect(InputStream in, int maxBytes) throws IOException {
        if (in == null || maxBytes <= 0) {
            throw new IllegalArgumentException("in 不能为 null，maxBytes 必须为正数");
        }
        byte[] buf = new byte[maxBytes + 1];
        int total = 0;
        while (total < buf.length) {
            int n = in.read(buf, total, buf.length - total);
            if (n < 0) {
                break;
            }
            total += n;
        }
        boolean truncated = total > maxBytes;
        int len = Math.min(total, maxBytes);
        return detect(buf, 0, len, truncated);
    }

    // ------------------------------------------------------------- 解码门面

    /** 检测并一次性解码整个字节数组；检测失败时抛 {@link UndeterminedEncodingException}。 */
    public DecodedText decode(byte[] data) {
        DetectionResult result = detect(data);
        Encoding encoding = result.encoding();
        String text = new CharsetTranscoder().decode(data, encoding);
        return new DecodedText(text, encoding, result.isBomDetected());
    }

    // ------------------------------------------------------------ 核心逻辑

    private DetectionResult detect(byte[] data, int off, int len, boolean truncated) {
        Encoding bomEnc = detectBom(data, off, len);
        if (bomEnc != null) {
            return detectByBom(data, off, len, truncated, bomEnc);
        }
        return detectByHeuristics(data, off, len, truncated);
    }

    private static Encoding detectBom(byte[] data, int off, int len) {
        if (Encoding.UTF_8.matchesBom(data, off, len)) {
            return Encoding.UTF_8;
        }
        if (Encoding.UTF_16BE.matchesBom(data, off, len)) {
            return Encoding.UTF_16BE;
        }
        if (Encoding.UTF_16LE.matchesBom(data, off, len)) {
            return Encoding.UTF_16LE;
        }
        return null;
    }

    private DetectionResult detectByBom(byte[] data, int off, int len, boolean truncated, Encoding bomEnc) {
        int bomLen = bomEnc.bom().length;
        int contentOff = off + bomLen;
        int contentLen = len - bomLen;
        int firstError = validateContent(data, contentOff, contentLen, truncated, bomEnc);

        List<EncodingCandidate> result = new ArrayList<>();
        boolean conflict = firstError >= 0;
        if (!conflict) {
            result.add(new EncodingCandidate(bomEnc, BOM_OK_CONFIDENCE,
                    "检测到 BOM " + bomHex(bomEnc) + "，且内容按该编码校验合法"));
        } else {
            result.add(new EncodingCandidate(bomEnc, BOM_CONFLICT_CONFIDENCE,
                    String.format("检测到 BOM %s，但内容在字节偏移 %d 处存在非法序列——"
                                    + "BOM 与内容冲突，按策略仍以 BOM 优先；解码会在此处报错而非返回乱码",
                            bomHex(bomEnc), firstError)));
        }

        // 保留启发式备选（在去掉 BOM 的真实内容上分析），冲突时压分
        List<EncodingCandidate> alternatives =
                Heuristics.score(data, contentOff, Math.max(contentLen, 0), truncated);
        for (EncodingCandidate c : alternatives) {
            if (c.encoding() == bomEnc) {
                continue;
            }
            double score = c.confidence();
            String reason = c.reason();
            if (conflict && score > BOM_CONFLICT_ALTERNATIVE_CAP) {
                score = BOM_CONFLICT_ALTERNATIVE_CAP;
                reason += "；存在 BOM 声明，备选编码降权";
            }
            result.add(new EncodingCandidate(c.encoding(), score, reason));
        }

        return new DetectionResult(result, true, true, conflict, len, truncated);
    }

    private DetectionResult detectByHeuristics(byte[] data, int off, int len, boolean truncated) {
        List<EncodingCandidate> candidates = Heuristics.score(data, off, len, truncated);
        boolean determined = false;
        if (!candidates.isEmpty()) {
            EncodingCandidate best = candidates.get(0);
            EncodingCandidate second = candidates.size() > 1 ? candidates.get(1) : null;
            double margin = second == null ? Double.MAX_VALUE : best.confidence() - second.confidence();
            determined = best.confidence() >= DETERMINED_MIN_CONFIDENCE
                    && margin >= DETERMINED_MIN_MARGIN;
        }
        return new DetectionResult(candidates, determined, false, false, len, truncated);
    }

    /** 返回内容相对数组的首个非法字节偏移（绝对值）；合法返回 -1。 */
    private static int validateContent(byte[] data, int off, int len,
                                       boolean truncated, Encoding encoding) {
        int rel;
        switch (encoding) {
            case UTF_8 -> rel = ByteValidators.scanUtf8(data, off, len, truncated).firstError;
            case UTF_16BE -> rel = ByteValidators.firstUtf16Error(data, off, len, false, truncated);
            case UTF_16LE -> rel = ByteValidators.firstUtf16Error(data, off, len, true, truncated);
            default -> rel = -1;
        }
        return rel < 0 ? -1 : off + rel;
    }

    private static String bomHex(Encoding enc) {
        byte[] bom = enc.bom();
        StringBuilder sb = new StringBuilder();
        for (byte b : bom) {
            if (sb.length() > 0) {
                sb.append(' ');
            }
            sb.append(String.format("%02X", b & 0xFF));
        }
        return sb.toString();
    }

    private static void checkRange(byte[] data, int off, int len) {
        if (data == null) {
            throw new IllegalArgumentException("data 不能为 null");
        }
        if (off < 0 || len < 0 || off + len > data.length) {
            throw new IndexOutOfBoundsException("非法范围: off=" + off + ", len=" + len);
        }
    }
}
