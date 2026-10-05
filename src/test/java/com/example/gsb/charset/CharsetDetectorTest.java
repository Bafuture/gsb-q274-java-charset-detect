package com.example.gsb.charset;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.nio.charset.StandardCharsets;
import java.util.Random;

import static org.assertj.core.api.Assertions.assertThat;

class CharsetDetectorTest {

    private final CharsetDetector detector = new CharsetDetector();

    // ---------- 四种编码识别 ----------

    @Test
    @DisplayName("识别无 BOM 的 UTF-8 中文")
    void detectsUtf8() {
        DetectionResult r = detector.detect(SampleTexts.ZH_NEWS.getBytes(StandardCharsets.UTF_8));
        assertThat(r.conclusive()).as(r.summary()).isTrue();
        assertThat(r.best().orElseThrow().family()).isEqualTo(CharsetFamily.UTF_8);
        assertThat(r.best().orElseThrow().confidence()).isGreaterThanOrEqualTo(0.78);
    }

    @Test
    @DisplayName("识别无 BOM 的 UTF-16LE / UTF-16BE 中文")
    void detectsUtf16WithoutBom() {
        DetectionResult le = detector.detect(SampleTexts.ZH_NEWS.getBytes(StandardCharsets.UTF_16LE));
        assertThat(le.conclusive()).as(le.summary()).isTrue();
        assertThat(le.best().orElseThrow().family()).isEqualTo(CharsetFamily.UTF_16LE);

        DetectionResult be = detector.detect(SampleTexts.ZH_NEWS.getBytes(StandardCharsets.UTF_16BE));
        assertThat(be.conclusive()).as(be.summary()).isTrue();
        assertThat(be.best().orElseThrow().family()).isEqualTo(CharsetFamily.UTF_16BE);
    }

    @Test
    @DisplayName("识别无 BOM 的 UTF-16 英文（零字节对齐特征）")
    void detectsUtf16English() {
        DetectionResult le = detector.detect(SampleTexts.EN.getBytes(StandardCharsets.UTF_16LE));
        assertThat(le.conclusive()).as(le.summary()).isTrue();
        assertThat(le.best().orElseThrow().family()).isEqualTo(CharsetFamily.UTF_16LE);
    }

    @Test
    @DisplayName("识别 GBK 中文")
    void detectsGbk() {
        DetectionResult r = detector.detect(SampleTexts.ZH_NEWS.getBytes(CharsetFamily.GBK.charset()));
        assertThat(r.conclusive()).as(r.summary()).isTrue();
        assertThat(r.best().orElseThrow().family()).isEqualTo(CharsetFamily.GBK);
    }

    @Test
    @DisplayName("识别 ISO-8859-1 拉丁文本")
    void detectsIso88591() {
        DetectionResult r = detector.detect(SampleTexts.LATIN1_TEXTS[0].getBytes(StandardCharsets.ISO_8859_1));
        assertThat(r.conclusive()).as(r.summary()).isTrue();
        assertThat(r.best().orElseThrow().family()).isEqualTo(CharsetFamily.ISO_8859_1);
    }

    // ---------- BOM 优先 ----------

    @Test
    @DisplayName("UTF-8 BOM 优先判定")
    void utf8BomWins() {
        byte[] content = SampleTexts.ZH_NEWS.getBytes(StandardCharsets.UTF_8);
        byte[] data = concat(new byte[] {(byte) 0xEF, (byte) 0xBB, (byte) 0xBF}, content);
        DetectionResult r = detector.detect(data);
        assertThat(r.conclusive()).as(r.summary()).isTrue();
        assertThat(r.best().orElseThrow().family()).isEqualTo(CharsetFamily.UTF_8);
        assertThat(r.bom()).isPresent();
    }

    @Test
    @DisplayName("UTF-16LE / UTF-16BE BOM 优先判定")
    void utf16BomWins() {
        byte[] le = concat(new byte[] {(byte) 0xFF, (byte) 0xFE},
                SampleTexts.ZH_NEWS.getBytes(StandardCharsets.UTF_16LE));
        DetectionResult rLe = detector.detect(le);
        assertThat(rLe.conclusive()).as(rLe.summary()).isTrue();
        assertThat(rLe.best().orElseThrow().family()).isEqualTo(CharsetFamily.UTF_16LE);

        byte[] be = concat(new byte[] {(byte) 0xFE, (byte) 0xFF},
                SampleTexts.ZH_NEWS.getBytes(StandardCharsets.UTF_16BE));
        DetectionResult rBe = detector.detect(be);
        assertThat(rBe.conclusive()).as(rBe.summary()).isTrue();
        assertThat(rBe.best().orElseThrow().family()).isEqualTo(CharsetFamily.UTF_16BE);
    }

    @Test
    @DisplayName("BOM 与内容冲突：UTF-8 BOM + GBK 内容，不收敛并给出警告")
    void bomConflictWithContent() {
        byte[] gbkContent = SampleTexts.ZH_NEWS.getBytes(CharsetFamily.GBK.charset());
        byte[] data = concat(new byte[] {(byte) 0xEF, (byte) 0xBB, (byte) 0xBF}, gbkContent);
        DetectionResult r = detector.detect(data);
        assertThat(r.conclusive()).isFalse();
        assertThat(r.best()).isPresent();
        assertThat(r.best().orElseThrow().family()).isEqualTo(CharsetFamily.UTF_8);
        assertThat(r.reasons()).anyMatch(s -> s.contains("冲突"));
        assertThat(r.summary()).contains("冲突");
    }

    // ---------- 启发式候选 ----------

    @Test
    @DisplayName("候选列表按置信度降序且每个候选都有依据说明")
    void candidatesAreSortedAndExplained() {
        DetectionResult r = detector.detect(SampleTexts.ZH_POEM.getBytes(StandardCharsets.UTF_8));
        assertThat(r.candidates()).isNotEmpty();
        for (int i = 1; i < r.candidates().size(); i++) {
            assertThat(r.candidates().get(i - 1).confidence())
                    .isGreaterThanOrEqualTo(r.candidates().get(i).confidence());
        }
        assertThat(r.candidates()).allSatisfy(c -> {
            assertThat(c.confidence()).isBetween(0.0, 1.0);
            assertThat(c.reason()).isNotBlank();
        });
    }

    @Test
    @DisplayName("非法序列会被淘汰并记录原因")
    void illegalSequencesAreRejected() {
        byte[] gbk = SampleTexts.ZH_NEWS.getBytes(CharsetFamily.GBK.charset());
        DetectionResult r = detector.detect(gbk);
        assertThat(r.candidates()).noneMatch(c -> c.family() == CharsetFamily.UTF_8);
        assertThat(r.rejections()).anyMatch(s -> s.contains("UTF-8"));
    }

    // ---------- 检测失败 ----------

    @Test
    @DisplayName("纯 ASCII 内容四种编码字节相同，返回多个候选且不收敛")
    void asciiOnlyIsInconclusive() {
        DetectionResult r = detector.detect(SampleTexts.EN.getBytes(StandardCharsets.US_ASCII));
        assertThat(r.conclusive()).isFalse();
        assertThat(r.best()).isEmpty();
        assertThat(r.candidates()).extracting(Candidate::family)
                .contains(CharsetFamily.UTF_8, CharsetFamily.GBK, CharsetFamily.ISO_8859_1);
        assertThat(r.reasons()).isNotEmpty();
        assertThat(r.summary()).contains("无法唯一判定");
    }

    @Test
    @DisplayName("随机二进制数据不收敛，不会随便选一个编码")
    void randomBytesAreInconclusive() {
        byte[] random = new byte[512];
        new Random(42).nextBytes(random);
        DetectionResult r = detector.detect(random);
        assertThat(r.conclusive()).isFalse();
        assertThat(r.best()).isEmpty();
        assertThat(r.summary()).isNotBlank();
    }

    @Test
    @DisplayName("空输入不收敛并说明原因")
    void emptyInputIsInconclusive() {
        DetectionResult r = detector.detect(new byte[0]);
        assertThat(r.conclusive()).isFalse();
        assertThat(r.reasons()).anyMatch(s -> s.contains("空"));
    }

    private static byte[] concat(byte[] a, byte[] b) {
        byte[] out = new byte[a.length + b.length];
        System.arraycopy(a, 0, out, 0, a.length);
        System.arraycopy(b, 0, out, a.length, b.length);
        return out;
    }
}
