package com.example.gsb.charset;

import java.util.List;
import java.util.Optional;
import java.util.stream.Collectors;

/**
 * 检测结果。
 *
 * <p>当 {@code conclusive=false} 时，表示证据不足、无法唯一判定，
 * 此时 {@link #best()} 为空，{@link #candidates()} 中保留全部候选，
 * {@link #reasons()} 说明无法判定的具体原因。调用方不应在
 * 未收敛时强行选择某个候选解码，否则可能得到乱码。
 */
public record DetectionResult(
        boolean conclusive,
        Optional<Candidate> best,
        List<Candidate> candidates,
        List<String> reasons,
        List<String> rejections,
        Optional<BomInfo> bom) {

    public DetectionResult {
        candidates = List.copyOf(candidates);
        reasons = List.copyOf(reasons);
        rejections = List.copyOf(rejections);
    }

    /** 人类可读的检测结论摘要。 */
    public String summary() {
        StringBuilder sb = new StringBuilder();
        if (conclusive && best.isPresent()) {
            sb.append(String.format("判定为 %s（置信度 %.2f）",
                    best.get().family().ianaName(), best.get().confidence()));
        } else {
            sb.append("无法唯一判定编码");
            if (!candidates.isEmpty()) {
                sb.append("，候选：").append(candidates.stream()
                        .map(c -> String.format("%s(%.2f)", c.family().ianaName(), c.confidence()))
                        .collect(Collectors.joining("、")));
            }
        }
        if (!reasons.isEmpty()) {
            sb.append("。").append(String.join("；", reasons));
        }
        return sb.toString();
    }
}
