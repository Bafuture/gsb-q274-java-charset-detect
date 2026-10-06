package com.example.gsb.charset;

/**
 * 一个候选编码及其置信度与判定依据。
 *
 * @param encoding   候选编码
 * @param confidence 置信度，取值 [0.0, 1.0]；0 表示该编码已被非法序列排除
 * @param reason     人类可读的判定依据（为何得分如此 / 为何被排除）
 */
public record EncodingCandidate(Encoding encoding, double confidence, String reason)
        implements Comparable<EncodingCandidate> {

    /** 按置信度从高到低排序。 */
    @Override
    public int compareTo(EncodingCandidate other) {
        return Double.compare(other.confidence, this.confidence);
    }

    @Override
    public String toString() {
        return String.format("%-10s 置信度=%.2f  %s", encoding.charset().name(), confidence, reason);
    }
}
