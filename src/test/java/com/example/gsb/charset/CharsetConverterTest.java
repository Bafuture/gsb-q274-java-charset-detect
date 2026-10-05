package com.example.gsb.charset;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.nio.charset.StandardCharsets;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class CharsetConverterTest {

    private final CharsetConverter converter = new CharsetConverter();

    @Test
    @DisplayName("四种编码往返解码正确")
    void roundTripAllCharsets() {
        assertThat(converter.decode(SampleTexts.ZH_NEWS.getBytes(StandardCharsets.UTF_8)).orElseThrow())
                .isEqualTo(SampleTexts.ZH_NEWS);
        assertThat(converter.decode(SampleTexts.ZH_NEWS.getBytes(StandardCharsets.UTF_16LE)).orElseThrow())
                .isEqualTo(SampleTexts.ZH_NEWS);
        assertThat(converter.decode(SampleTexts.ZH_NEWS.getBytes(StandardCharsets.UTF_16BE)).orElseThrow())
                .isEqualTo(SampleTexts.ZH_NEWS);
        assertThat(converter.decode(SampleTexts.ZH_NEWS.getBytes(CharsetFamily.GBK.charset())).orElseThrow())
                .isEqualTo(SampleTexts.ZH_NEWS);
        assertThat(converter.decode(SampleTexts.LATIN1_TEXTS[0].getBytes(StandardCharsets.ISO_8859_1)).orElseThrow())
                .isEqualTo(SampleTexts.LATIN1_TEXTS[0]);
    }

    @Test
    @DisplayName("带 BOM 的 UTF-8 解码后不保留 BOM 字符")
    void bomIsStrippedOnDecode() {
        byte[] data = concat(new byte[] {(byte) 0xEF, (byte) 0xBB, (byte) 0xBF},
                "abc".getBytes(StandardCharsets.UTF_8));
        DecodeResult r = converter.decodeAs(data, CharsetFamily.UTF_8);
        assertThat(r.success()).isTrue();
        assertThat(r.text()).isEqualTo("abc");
    }

    // ---------- 转换错误定位 ----------

    @Test
    @DisplayName("UTF-8 非法前导字节定位到具体偏移")
    void utf8BadLeadOffset() {
        byte[] data = {'A', (byte) 0x80, 'B'};
        DecodeResult r = converter.decodeAs(data, CharsetFamily.UTF_8);
        assertThat(r.success()).isFalse();
        assertThat(r.errorOffset()).isEqualTo(1);
        assertThat(r.message()).contains("UTF-8");
    }

    @Test
    @DisplayName("UTF-8 非法后续字节定位到具体偏移")
    void utf8BadContinuationOffset() {
        byte[] data = {'A', 'B', (byte) 0xC3, (byte) 0x28, 'C'};
        DecodeResult r = converter.decodeAs(data, CharsetFamily.UTF_8);
        assertThat(r.success()).isFalse();
        assertThat(r.errorOffset()).isEqualTo(3);
    }

    @Test
    @DisplayName("UTF-8 末尾截断定位到被截断序列起点")
    void utf8TruncatedOffset() {
        byte[] data = {'A', (byte) 0xE4, (byte) 0xB8};
        DecodeResult r = converter.decodeAs(data, CharsetFamily.UTF_8);
        assertThat(r.success()).isFalse();
        assertThat(r.errorOffset()).isEqualTo(1);
    }

    @Test
    @DisplayName("GBK 非法后续字节定位到具体偏移")
    void gbkBadTrailOffset() {
        byte[] data = {'a', 'b', 'c', (byte) 0x81, (byte) 0x20};
        DecodeResult r = converter.decodeAs(data, CharsetFamily.GBK);
        assertThat(r.success()).isFalse();
        assertThat(r.errorOffset()).isEqualTo(4);
    }

    @Test
    @DisplayName("GBK 0x80 单字节非法")
    void gbkBadLeadOffset() {
        byte[] data = {'a', (byte) 0x80};
        DecodeResult r = converter.decodeAs(data, CharsetFamily.GBK);
        assertThat(r.success()).isFalse();
        assertThat(r.errorOffset()).isEqualTo(1);
    }

    @Test
    @DisplayName("UTF-16 奇数长度与错误代理对定位")
    void utf16Errors() {
        byte[] odd = {0x00, 0x3D, 0x00};
        DecodeResult r1 = converter.decodeAs(odd, CharsetFamily.UTF_16BE);
        assertThat(r1.success()).isFalse();
        assertThat(r1.errorOffset()).isEqualTo(2);

        // BE: D8 3D（高代理）之后 41 00 不是低代理
        byte[] badSurrogate = {(byte) 0xD8, 0x3D, 0x41, 0x00};
        DecodeResult r2 = converter.decodeAs(badSurrogate, CharsetFamily.UTF_16BE);
        assertThat(r2.success()).isFalse();
        assertThat(r2.errorOffset()).isEqualTo(2);
    }

    @Test
    @DisplayName("检测未收敛时拒绝猜测解码，返回失败结果而非乱码")
    void decodeRefusedWhenInconclusive() {
        byte[] ascii = SampleTexts.EN.getBytes(StandardCharsets.US_ASCII);
        DecodeResult r = converter.decode(ascii);
        assertThat(r.success()).isFalse();
        assertThat(r.message()).contains("未收敛");
    }

    @Test
    @DisplayName("orElseThrow 抛出携带偏移的异常")
    void orElseThrowCarriesOffset() {
        byte[] data = {'A', (byte) 0x80};
        DecodeResult r = converter.decodeAs(data, CharsetFamily.UTF_8);
        assertThatThrownBy(r::orElseThrow)
                .isInstanceOf(CharsetDecodeException.class)
                .hasMessageContaining("偏移 1")
                .satisfies(e -> assertThat(((CharsetDecodeException) e).errorOffset()).isEqualTo(1));
    }

    private static byte[] concat(byte[] a, byte[] b) {
        byte[] out = new byte[a.length + b.length];
        System.arraycopy(a, 0, out, 0, a.length);
        System.arraycopy(b, 0, out, a.length, b.length);
        return out;
    }
}
