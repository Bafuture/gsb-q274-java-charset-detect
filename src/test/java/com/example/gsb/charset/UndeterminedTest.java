package com.example.gsb.charset;

import org.junit.jupiter.api.Test;

import static com.example.gsb.charset.TestSamples.*;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/** 要求 4：检测失败时返回多个候选与原因，绝不随便选一个返回乱码。 */
class UndeterminedTest {

    private final CharsetDetector detector = new CharsetDetector();

    @Test
    void emptyInputIsUndetermined() {
        DetectionResult r = detector.detect(new byte[0]);
        assertThat(r.isDetermined()).isFalse();
        assertThat(r.explain()).contains("无法确定");
        assertThatThrownBy(r::encoding).isInstanceOf(UndeterminedEncodingException.class);
    }

    @Test
    void pureAsciiIsUndeterminedWithMultipleCandidates() {
        DetectionResult r = detector.detect(utf8(EN_1 + " 1234567890"));
        assertThat(r.isDetermined()).isFalse();
        // 纯 ASCII 与 UTF-8 / GBK / ISO-8859-1 均兼容 → 多个候选并列
        assertThat(r.candidates())
                .filteredOn(c -> c.confidence() > 0)
                .extracting(EncodingCandidate::encoding)
                .contains(Encoding.UTF_8, Encoding.GBK, Encoding.ISO_8859_1);
        assertThat(r.best().orElseThrow().confidence()).isLessThan(0.6);
    }

    @Test
    void ambiguousShortSampleReturnsBothCandidates() {
        // Latin-1 的 "àé" 恰好也是合法 GBK → 必须报歧义而不是二选一
        DetectionResult r = detector.detect(latin1("àé"));
        assertThat(r.isDetermined()).isFalse();
        assertThat(r.candidates())
                .extracting(EncodingCandidate::encoding)
                .contains(Encoding.GBK, Encoding.ISO_8859_1);
        assertThat(r.explain()).contains("无法确定");
    }

    @Test
    void decodeOnUndeterminedThrowsInsteadOfReturningMojibake() {
        byte[] ascii = utf8(EN_1);
        assertThatThrownBy(() -> detector.decode(ascii))
                .isInstanceOf(UndeterminedEncodingException.class)
                .satisfies(e -> {
                    DetectionResult r = ((UndeterminedEncodingException) e).getResult();
                    assertThat(r.candidates()).hasSizeGreaterThanOrEqualTo(2);
                });
        assertThatThrownBy(() -> new CharsetTranscoder().decodeAll(ascii, detector.detect(ascii)))
                .isInstanceOf(UndeterminedEncodingException.class);
    }

    @Test
    void undeterminedResultRefusesEncodingAccess() {
        DetectionResult r = detector.detect(utf8(EN_1));
        assertThatThrownBy(r::encoding)
                .isInstanceOf(UndeterminedEncodingException.class)
                .hasMessageContaining("候选");
    }
}
