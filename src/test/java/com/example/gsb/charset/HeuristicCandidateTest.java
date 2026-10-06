package com.example.gsb.charset;

import org.junit.jupiter.api.Test;

import static com.example.gsb.charset.TestSamples.*;
import static org.assertj.core.api.Assertions.assertThat;

/** 要求 3：无 BOM 时的启发式候选、置信度与判定依据。 */
class HeuristicCandidateTest {

    private final CharsetDetector detector = new CharsetDetector();

    @Test
    void everyCandidateCarriesConfidenceAndReason() {
        DetectionResult r = detector.detect(gbk(ZH_1));
        assertThat(r.candidates()).hasSize(Encoding.values().length);
        assertThat(r.candidates()).allSatisfy(c -> {
            assertThat(c.confidence()).isBetween(0.0, 1.0);
            assertThat(c.reason()).isNotBlank();
        });
        // 候选按置信度降序排列
        for (int i = 1; i < r.candidates().size(); i++) {
            assertThat(r.candidates().get(i).confidence())
                    .isLessThanOrEqualTo(r.candidates().get(i - 1).confidence());
        }
    }

    @Test
    void gbkSampleExcludesUtf8WithIllegalSequenceReason() {
        DetectionResult r = detector.detect(gbk(ZH_1 + ZH_2));
        EncodingCandidate utf8 = candidate(r, Encoding.UTF_8);
        assertThat(utf8.confidence()).isEqualTo(0.0);
        assertThat(utf8.reason()).contains("非法 UTF-8 序列");

        EncodingCandidate gbk = candidate(r, Encoding.GBK);
        assertThat(gbk.confidence()).isGreaterThan(0.8);
        assertThat(gbk.reason()).contains("双字节");
    }

    @Test
    void utf8SampleBeatsGbkViaCrossValidation() {
        // UTF-8 中文按 GBK 规则也常能“碰巧合法”，靠交叉校准把 GBK 压下去
        DetectionResult r = detector.detect(utf8(ZH_1 + ZH_2 + ZH_3));
        assertThat(candidate(r, Encoding.UTF_8).confidence()).isGreaterThan(0.9);
        assertThat(candidate(r, Encoding.GBK).confidence()).isLessThanOrEqualTo(0.45);
        assertThat(r.encoding()).isEqualTo(Encoding.UTF_8);
    }

    @Test
    void utf16LeBeatsGbkViaByteDistribution() {
        DetectionResult r = detector.detect(utf16le(ZH_1 + ZH_3));
        assertThat(candidate(r, Encoding.UTF_16LE).confidence()).isGreaterThan(0.8);
        assertThat(candidate(r, Encoding.GBK).confidence()).isLessThanOrEqualTo(0.45);
        assertThat(r.encoding()).isEqualTo(Encoding.UTF_16LE);
    }

    @Test
    void gbkPairOutsideCommonHanziRangeIsCapped() {
        // 0x81 0x40 是合法 GBK 双字节结构但落在扩展区、不在常用汉字区 → GBK 降权
        byte[] data = new byte[]{(byte) 0x81, 0x40, (byte) 0x81, 0x40, (byte) 0x81, 0x40};
        DetectionResult r = detector.detect(data);
        assertThat(r.isDetermined()).isFalse();
        assertThat(candidate(r, Encoding.GBK).confidence()).isLessThanOrEqualTo(0.50);
    }

    @Test
    void explainContainsConclusionAndAllReasons() {
        DetectionResult r = detector.detect(gbk(ZH_1));
        String explain = r.explain();
        assertThat(explain).contains("GBK").contains("置信度");
        assertThat(explain.lines()).hasSizeGreaterThanOrEqualTo(Encoding.values().length);
    }

    private static EncodingCandidate candidate(DetectionResult r, Encoding e) {
        return r.candidates().stream()
                .filter(c -> c.encoding() == e)
                .findFirst()
                .orElseThrow();
    }
}
