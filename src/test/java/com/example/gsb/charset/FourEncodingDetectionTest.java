package com.example.gsb.charset;

import org.junit.jupiter.api.Test;

import static com.example.gsb.charset.TestSamples.*;
import static org.assertj.core.api.Assertions.assertThat;

/** 要求 1：四种编码的无 BOM 识别。 */
class FourEncodingDetectionTest {

    private final CharsetDetector detector = new CharsetDetector();

    @Test
    void detectsUtf8Chinese() {
        DetectionResult r = detector.detect(utf8(ZH_1 + ZH_2));
        assertThat(r.isDetermined()).isTrue();
        assertThat(r.encoding()).isEqualTo(Encoding.UTF_8);
        assertThat(r.best().orElseThrow().confidence()).isGreaterThan(0.9);
    }

    @Test
    void detectsUtf8WithEmojiAndAccents() {
        DetectionResult r = detector.detect(utf8(EMOJI + FR_1));
        assertThat(r.encoding()).isEqualTo(Encoding.UTF_8);
    }

    @Test
    void detectsUtf16BeWithoutBom() {
        DetectionResult r = detector.detect(utf16be(EN_1 + " " + EN_2));
        assertThat(r.isDetermined()).isTrue();
        assertThat(r.encoding()).isEqualTo(Encoding.UTF_16BE);
    }

    @Test
    void detectsUtf16LeWithoutBom() {
        DetectionResult r = detector.detect(utf16le(EN_1 + " " + EN_2));
        assertThat(r.isDetermined()).isTrue();
        assertThat(r.encoding()).isEqualTo(Encoding.UTF_16LE);
    }

    @Test
    void detectsUtf16BeChineseWithoutBom() {
        // 纯中文 UTF-16 没有零字节，靠 CJK 高字节分布识别
        DetectionResult r = detector.detect(utf16be(ZH_1 + ZH_3));
        assertThat(r.encoding()).isEqualTo(Encoding.UTF_16BE);
    }

    @Test
    void detectsUtf16LeChineseWithoutBom() {
        DetectionResult r = detector.detect(utf16le(ZH_1 + ZH_3));
        assertThat(r.encoding()).isEqualTo(Encoding.UTF_16LE);
    }

    @Test
    void detectsGbkChinese() {
        DetectionResult r = detector.detect(gbk(ZH_2 + ZH_3));
        assertThat(r.isDetermined()).isTrue();
        assertThat(r.encoding()).isEqualTo(Encoding.GBK);
        assertThat(r.best().orElseThrow().confidence()).isGreaterThan(0.8);
    }

    @Test
    void detectsGbkMixedWithAscii() {
        DetectionResult r = detector.detect(gbk("订单 ORDER-1024 已发货，注意防潮。"));
        assertThat(r.encoding()).isEqualTo(Encoding.GBK);
    }

    @Test
    void detectsIso88591French() {
        DetectionResult r = detector.detect(latin1(FR_1 + " " + FR_2));
        assertThat(r.isDetermined()).isTrue();
        assertThat(r.encoding()).isEqualTo(Encoding.ISO_8859_1);
    }

    @Test
    void distinguishesGbkFromUtf8ForSameChineseText() {
        String text = ZH_1 + ZH_2 + ZH_3;
        assertThat(detector.detect(utf8(text)).encoding()).isEqualTo(Encoding.UTF_8);
        assertThat(detector.detect(gbk(text)).encoding()).isEqualTo(Encoding.GBK);
    }
}
