package com.example.gsb.charset;

import com.example.gsb.charset.CharsetScans.Scan;

import java.io.IOException;
import java.io.InputStream;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.EnumMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

/**
 * 字符集检测器：在 UTF-8、UTF-16LE/BE、GBK、ISO-8859-1 之间判定字节序列的实际编码。
 *
 * <p>判定流程：
 * <ol>
 *   <li><b>BOM 优先</b>：开头存在 BOM 时直接按 BOM 判定（置信度 1.0）。
 *       若 BOM 与内容冲突（内容按 BOM 声明的编码严格校验失败，或另一种编码的
 *       启发式证据明显更强），结果标记为 {@code conclusive=false} 并给出警告，
 *       由调用方决策，而不是静默产出乱码。</li>
 *   <li><b>无 BOM 启发式</b>：对每个候选编码做严格结构扫描（非法序列直接淘汰），
 *       再基于字节分布与常见字符范围评分，给出候选列表与置信度。</li>
 *   <li><b>检测失败</b>：最高置信度低于 {@value #CONCLUSIVE_MIN}，或前两名差距
 *       小于 {@value #MIN_GAP} 时，返回不可判定结果（多个候选 + 原因），
 *       不会随便选一个返回。</li>
 * </ol>
 *
 * <p>流式检测（{@link #detectPrefix(InputStream, int)}）只读取流的前若干字节，
 * 依据：BOM 位于流的最开头；启发式统计（非法序列、字节分布）对自然语言文本
 * 是全局稳定的，几千字节即可代表整体分布。局限：样本过小时置信度下降；
 * 纯 ASCII 内容本身在四种编码下字节完全相同，任何方法都无法区分，会返回不可判定；
 * 编码在文件中途切换的混合文件无法被前缀检测发现。
 */
public final class CharsetDetector {

    /** 流式检测默认读取的前缀字节数。 */
    public static final int DEFAULT_PREFIX_BYTES = 8192;

    /** 判定收敛所需的最高置信度下限。 */
    public static final double CONCLUSIVE_MIN = 0.78;

    /** 判定收敛所需的前两名置信度最小差距。 */
    public static final double MIN_GAP = 0.12;

    /** 纯 ASCII 内容的候选置信度上限（四种编码字节相同，无法区分）。 */
    static final double ASCII_ONLY_CAP = 0.40;

    public DetectionResult detect(byte[] data) {
        return detect(data, 0, data.length, false);
    }

    /** 流式检测：最多读取流的前 {@link #DEFAULT_PREFIX_BYTES} 字节进行判定。 */
    public DetectionResult detectPrefix(InputStream in) throws IOException {
        return detectPrefix(in, DEFAULT_PREFIX_BYTES);
    }

    /**
     * 流式检测：最多读取流的前 {@code maxBytes} 字节进行判定。
     * 样本末尾被截断的多字节序列不作为非法证据。
     */
    public DetectionResult detectPrefix(InputStream in, int maxBytes) throws IOException {
        if (maxBytes <= 0) {
            throw new IllegalArgumentException("maxBytes 必须为正数");
        }
        byte[] buf = in.readNBytes(maxBytes);
        boolean prefix = buf.length == maxBytes; // 读满上限，流可能还有剩余
        return detect(buf, 0, buf.length, prefix);
    }

    private DetectionResult detect(byte[] data, int off, int len, boolean prefix) {
        if (data == null) {
            throw new NullPointerException("data");
        }
        List<String> prefixReasons = new ArrayList<>();
        if (prefix) {
            prefixReasons.add("流式模式：仅读取前 " + len + " 字节进行判定");
        }
        if (len == 0) {
            return new DetectionResult(false, Optional.empty(), List.of(),
                    withPrefix(prefixReasons, "输入为空，没有任何字节可供判定"),
                    List.of(), Optional.empty());
        }

        BomInfo bom = BomInfo.sniff(data, off, len);
        int contentOff = bom == null ? off : off + bom.length();
        int contentLen = bom == null ? len : len - bom.length();

        Map<CharsetFamily, Scan> scans = scanAll(data, contentOff, contentOff + contentLen);
        Map<CharsetFamily, Double> scores = scoreAll(scans);

        if (bom != null) {
            return withBom(bom, scans, scores, contentLen, prefixReasons);
        }
        return heuristic(scans, scores, prefixReasons, Optional.empty());
    }

    private static Map<CharsetFamily, Scan> scanAll(byte[] data, int from, int to) {
        Map<CharsetFamily, Scan> scans = new EnumMap<>(CharsetFamily.class);
        scans.put(CharsetFamily.UTF_8, CharsetScans.scanUtf8(data, from, to));
        scans.put(CharsetFamily.UTF_16LE, CharsetScans.scanUtf16(data, from, to, true));
        scans.put(CharsetFamily.UTF_16BE, CharsetScans.scanUtf16(data, from, to, false));
        scans.put(CharsetFamily.GBK, CharsetScans.scanGbk(data, from, to));
        scans.put(CharsetFamily.ISO_8859_1, CharsetScans.scanIso88591(data, from, to));
        return scans;
    }

    private static Map<CharsetFamily, Double> scoreAll(Map<CharsetFamily, Scan> scans) {
        Map<CharsetFamily, Double> scores = new EnumMap<>(CharsetFamily.class);
        for (Map.Entry<CharsetFamily, Scan> e : scans.entrySet()) {
            Scan s = e.getValue();
            if (!s.valid()) {
                continue;
            }
            double score = switch (e.getKey()) {
                case UTF_8 -> scoreUtf8(s);
                case UTF_16LE -> scoreUtf16(s, true);
                case UTF_16BE -> scoreUtf16(s, false);
                case GBK -> scoreGbk(s);
                case ISO_8859_1 -> scoreIso88591(s);
            };
            scores.put(e.getKey(), score);
        }
        return scores;
    }

    // ---------- 启发式评分（仅对结构合法的候选打分） ----------

    private static double scoreUtf8(Scan s) {
        int multi = s.units() - s.asciiUnits();
        if (multi == 0) {
            return ASCII_ONLY_CAP; // 纯 ASCII：四种编码字节相同，无法区分
        }
        double base = multi >= 3 ? 0.95 : multi == 2 ? 0.80 : 0.70;
        double r = s.sensibleUnits() / (double) s.units();
        double ns = s.nonSensibleUnits() / (double) s.units();
        return clamp(base * (0.6 + 0.4 * r) - 0.3 * ns);
    }

    private static double scoreGbk(Scan s) {
        int doubleByte = s.units() - s.asciiUnits();
        if (doubleByte == 0) {
            return ASCII_ONLY_CAP;
        }
        double r = s.sensibleUnits() / (double) s.units();
        double pua = s.puaUnits() / (double) s.units();
        double ns = s.nonSensibleUnits() / (double) s.units();
        double score = 0.35 + 0.45 * r - 0.30 * pua - 0.20 * ns;
        if (doubleByte == 1) {
            score = Math.min(score, 0.70); // 仅一个双字节字符，证据太弱
        }
        return clamp(score);
    }

    private static double scoreUtf16(Scan s, boolean littleEndian) {
        int units = s.units();
        double r = s.sensibleUnits() / (double) units;
        double zerosAligned = littleEndian ? s.zerosAtOdd() : s.zerosAtEven();
        double zr = zerosAligned / (double) units;
        double asciiRatio = s.asciiUnits() / (double) units;
        double ns = s.nonSensibleUnits() / (double) units;
        double score = 0.30 + 0.55 * r + 0.20 * zr - 0.40 * ns;
        if (zr < 0.15 && asciiRatio > 0.80) {
            // 纯 ASCII 字节流偶数长度时可能“碰巧”全部解析成合法码元，缺乏零字节对齐证据
            score = Math.min(score, 0.20);
        }
        // 无 BOM 的 UTF-16 需要佐证：零字节对齐、ASCII 单元或 CJK 标点至少出现其一。
        // 否则拉丁/GBK 字节流“碰巧”解析成一串 CJK 码元时无法与真实中文 UTF-16 区分。
        boolean corroborated = zr >= 0.05 || s.asciiUnits() > 0 || s.cjkPunctUnits() > 0;
        if (!corroborated) {
            score = Math.min(score, 0.55);
        }
        if (units < 4) {
            score = Math.min(score, 0.70); // 样本太短
        }
        return clamp(score);
    }

    private static double scoreIso88591(Scan s) {
        double hi = s.highBytes() / (double) s.units();
        if (hi == 0) {
            return ASCII_ONLY_CAP;
        }
        double c1 = s.c1Units() / (double) s.units();
        double ns = s.nonSensibleUnits() / (double) s.units();
        // 真实拉丁文本的高位字节（重音字母）孤立出现；相邻高位字节是多字节编码的特征
        double adj = s.adjacentHighPairs() / (double) Math.max(1, s.highBytes());
        // 高位字节占比过高更像 GBK/UTF-8 等多字节编码
        double hiPenalty = Math.max(0.0, hi - 0.35) * 1.2;
        double score = 0.85 - 0.60 * c1 - 0.15 * ns + 0.10 * Math.min(hi, 0.30)
                - hiPenalty - 0.35 * adj;
        if (s.nulUnits() > 0) {
            score -= 0.20; // 文本编码几乎不会包含 NUL，是 UTF-16 的强信号
        }
        if (s.spaceUnits() / (double) s.units() < 0.03) {
            score -= 0.25; // 真实拉丁文本几乎必有空格；无空格更像 CJK 多字节编码
        }
        return clamp(score);
    }

    private static double clamp(double v) {
        return Math.max(0.0, Math.min(1.0, v));
    }

    // ---------- BOM 优先 ----------

    private DetectionResult withBom(BomInfo bom, Map<CharsetFamily, Scan> scans,
                                    Map<CharsetFamily, Double> scores, int contentLen,
                                    List<String> prefixReasons) {
        CharsetFamily family = bom.family();
        List<String> reasons = new ArrayList<>(prefixReasons);
        reasons.add("检测到 BOM " + bom.hexString() + "，按规范优先判定为 " + family.ianaName());

        List<Candidate> candidates = new ArrayList<>();
        Candidate best = new Candidate(family, 1.0, "BOM " + bom.hexString() + " 直接指定编码");
        candidates.add(best);
        appendHeuristicCandidates(candidates, scans, scores, family);

        boolean conflict = false;
        if (contentLen > 0) {
            Scan own = scans.get(family);
            if (!own.valid()) {
                conflict = true;
                reasons.add("警告：BOM 与内容冲突——内容按 " + family.ianaName()
                        + " 严格校验在字节偏移 " + own.firstIllegalOffset() + " 处非法");
            } else {
                double ownScore = scores.getOrDefault(family, 0.0);
                for (Map.Entry<CharsetFamily, Double> e : scores.entrySet()) {
                    if (e.getKey() != family
                            && e.getValue() >= CONCLUSIVE_MIN
                            && ownScore < e.getValue() - MIN_GAP) {
                        conflict = true;
                        reasons.add("警告：BOM 与内容冲突——内容特征更符合 "
                                + e.getKey().ianaName() + "（置信度 "
                                + String.format("%.2f", e.getValue()) + "）");
                    }
                }
            }
        }
        if (conflict) {
            reasons.add("BOM 优先但存在冲突，结果不收敛；请人工确认后调用 decodeAs 指定编码解码");
            return new DetectionResult(false, Optional.of(best), candidates, reasons,
                    rejectionsOf(scans), Optional.of(bom));
        }
        return new DetectionResult(true, Optional.of(best), candidates, reasons,
                rejectionsOf(scans), Optional.of(bom));
    }

    // ---------- 无 BOM 启发式 ----------

    private DetectionResult heuristic(Map<CharsetFamily, Scan> scans,
                                      Map<CharsetFamily, Double> scores,
                                      List<String> prefixReasons,
                                      Optional<BomInfo> bom) {
        List<String> reasons = new ArrayList<>(prefixReasons);
        List<Candidate> candidates = new ArrayList<>();
        appendHeuristicCandidates(candidates, scans, scores, null);
        candidates.sort(Comparator.comparingDouble(Candidate::confidence).reversed()
                .thenComparing(c -> c.family().ordinal()));
        List<String> rejections = rejectionsOf(scans);

        for (Map.Entry<CharsetFamily, Scan> e : scans.entrySet()) {
            if (e.getValue().valid() && e.getValue().truncated()) {
                reasons.add(e.getKey().ianaName() + "：样本末尾存在被截断的多字节序列（未计为非法）");
            }
        }
        if (candidates.isEmpty()) {
            reasons.add("所有候选编码均存在非法字节序列，无法判定");
            return new DetectionResult(false, Optional.empty(), candidates, reasons, rejections, bom);
        }
        Candidate top = candidates.get(0);
        double gap = candidates.size() > 1
                ? top.confidence() - candidates.get(1).confidence()
                : top.confidence();
        if (top.confidence() >= CONCLUSIVE_MIN && gap >= MIN_GAP) {
            return new DetectionResult(true, Optional.of(top), candidates, reasons, rejections, bom);
        }
        if (top.confidence() < CONCLUSIVE_MIN) {
            reasons.add(String.format("最高置信度仅 %.2f（收敛阈值 %.2f），证据不足",
                    top.confidence(), CONCLUSIVE_MIN));
        } else {
            reasons.add(String.format("前两名候选 %s(%.2f) 与 %s(%.2f) 差距 %.2f 小于 %.2f，无法区分",
                    top.family().ianaName(), top.confidence(),
                    candidates.get(1).family().ianaName(), candidates.get(1).confidence(),
                    gap, MIN_GAP));
        }
        return new DetectionResult(false, Optional.empty(), candidates, reasons, rejections, bom);
    }

    private static void appendHeuristicCandidates(List<Candidate> candidates,
                                                  Map<CharsetFamily, Scan> scans,
                                                  Map<CharsetFamily, Double> scores,
                                                  CharsetFamily exclude) {
        for (Map.Entry<CharsetFamily, Scan> e : scans.entrySet()) {
            CharsetFamily family = e.getKey();
            if (family == exclude || !e.getValue().valid()) {
                continue;
            }
            candidates.add(new Candidate(family, scores.get(family), reasonOf(family, e.getValue())));
        }
    }

    private static List<String> rejectionsOf(Map<CharsetFamily, Scan> scans) {
        List<String> rejections = new ArrayList<>();
        for (Map.Entry<CharsetFamily, Scan> e : scans.entrySet()) {
            if (!e.getValue().valid()) {
                rejections.add(e.getKey().ianaName() + "：字节偏移 "
                        + e.getValue().firstIllegalOffset() + " 处出现非法序列，已淘汰");
            }
        }
        return rejections;
    }

    private static String reasonOf(CharsetFamily family, Scan s) {
        int units = Math.max(1, s.units());
        int sensiblePct = (int) Math.round(100.0 * s.sensibleUnits() / units);
        return switch (family) {
            case UTF_8 -> String.format("UTF-8 序列全部合法（%d 个多字节字符，%d%% 落在常见字符范围）",
                    s.units() - s.asciiUnits(), sensiblePct);
            case UTF_16LE, UTF_16BE -> {
                int zeros = family == CharsetFamily.UTF_16LE ? s.zerosAtOdd() : s.zerosAtEven();
                yield String.format("%s 码元全部合法（零字节对齐率 %d%%，%d%% 落在常见字符范围）",
                        family.ianaName(), (int) Math.round(100.0 * zeros / units), sensiblePct);
            }
            case GBK -> String.format("GBK 双字节序列全部合法（%d 个双字节字符，%d 个映射到私用区，%d%% 落在常见字符范围）",
                    s.units() - s.asciiUnits(), s.puaUnits(), sensiblePct);
            case ISO_8859_1 -> String.format("ISO-8859-1 任何字节均合法（%d 个高位字节，%d 个 C1 控制字符）",
                    s.units() - s.asciiUnits(), s.c1Units());
        };
    }

    private static List<String> withPrefix(List<String> prefix, String reason) {
        List<String> all = new ArrayList<>(prefix);
        all.add(reason);
        return all;
    }
}
