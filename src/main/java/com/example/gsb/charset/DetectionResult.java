package com.example.gsb.charset;

import java.util.List;
import java.util.Optional;

/**
 * 一次编码检测的结果。
 *
 * <p>结果中始终保留<strong>全部</strong>候选编码（含被排除的，置信度为 0）及其判定依据，
 * 便于排查。当 {@link #isDetermined()} 为 {@code false} 时表示检测失败：
 * 此时 {@link #encoding()} 与 {@link CharsetDetector#decode} 会抛出
 * {@link UndeterminedEncodingException}，组件不会随便挑一个编码返回乱码。</p>
 */
public final class DetectionResult {

    private final List<EncodingCandidate> candidates;
    private final boolean determined;
    private final boolean bomDetected;
    private final boolean bomContentConflict;
    private final int bytesExamined;
    private final boolean truncatedSample;

    DetectionResult(List<EncodingCandidate> candidates, boolean determined,
                    boolean bomDetected, boolean bomContentConflict,
                    int bytesExamined, boolean truncatedSample) {
        this.candidates = List.copyOf(candidates);
        this.determined = determined;
        this.bomDetected = bomDetected;
        this.bomContentConflict = bomContentConflict;
        this.bytesExamined = bytesExamined;
        this.truncatedSample = truncatedSample;
    }

    /** 全部候选编码，按置信度从高到低排序（BOM 命中时 BOM 候选固定居首）。 */
    public List<EncodingCandidate> candidates() {
        return candidates;
    }

    /** 置信度最高的候选；无任何候选时（如空输入）返回空。 */
    public Optional<EncodingCandidate> best() {
        return candidates.isEmpty() ? Optional.empty() : Optional.of(candidates.get(0));
    }

    /**
     * 检测是否得出确定结论。
     * 为 {@code false} 时应查看 {@link #candidates()} 与 {@link #explain()} 人工介入，
     * 而不是直接取 {@link #best()} 解码。
     */
    public boolean isDetermined() {
        return determined;
    }

    /** 是否检测到了 BOM。 */
    public boolean isBomDetected() {
        return bomDetected;
    }

    /**
     * BOM 与内容是否冲突：BOM 声明的编码无法合法解码后续内容时为 {@code true}。
     * 冲突时仍按 BOM 优先判定（见 {@link CharsetDetector} 类文档），
     * 但 {@link CharsetTranscoder#decode} 会在第一个非法字节处抛出
     * {@link MalformedContentException} 并给出字节偏移。
     */
    public boolean isBomContentConflict() {
        return bomContentConflict;
    }

    /** 本次检测实际检查的字节数（流式检测时等于采样上限或文件实际长度）。 */
    public int bytesExamined() {
        return bytesExamined;
    }

    /** 输入是否为被截断的采样（流式检测大文件时为 {@code true}）。 */
    public boolean isTruncatedSample() {
        return truncatedSample;
    }

    /**
     * 返回确定结论对应的编码。
     *
     * @throws UndeterminedEncodingException 检测失败（无法确定）时抛出，异常中携带本结果
     */
    public Encoding encoding() {
        if (!determined) {
            throw new UndeterminedEncodingException(this);
        }
        return best().orElseThrow(() -> new UndeterminedEncodingException(this)).encoding();
    }

    /** 人类可读的完整判定报告（结论 + 每个候选的得分依据）。 */
    public String explain() {
        StringBuilder sb = new StringBuilder();
        if (determined) {
            sb.append("检测结论：").append(best().orElseThrow().encoding().charset().name())
                    .append(String.format("（置信度 %.2f）", best().orElseThrow().confidence()));
        } else {
            sb.append("检测失败：无法确定编码，返回全部候选，拒绝盲目解码");
        }
        if (bomDetected) {
            sb.append("；检测到 BOM");
            if (bomContentConflict) {
                sb.append("，且 BOM 与内容冲突（仍按 BOM 优先，解码时会在非法字节处报错）");
            }
        }
        if (truncatedSample) {
            sb.append("；基于前 ").append(bytesExamined).append(" 字节采样");
        }
        sb.append(System.lineSeparator());
        for (EncodingCandidate c : candidates) {
            sb.append("  - ").append(c).append(System.lineSeparator());
        }
        return sb.toString();
    }

    @Override
    public String toString() {
        return explain();
    }
}
