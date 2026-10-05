package com.example.gsb.charset;

/**
 * 一个候选编码及其置信度。
 *
 * @param confidence 取值 [0,1]，由启发式规则（非法序列、字节分布、常见字符范围）计算；
 *                   由 BOM 直接判定时为 1.0。
 * @param reason     该候选得分的人类可读依据。
 */
public record Candidate(CharsetFamily family, double confidence, String reason) {
}
