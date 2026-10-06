package com.example.gsb.charset;

import org.junit.jupiter.api.Test;

import static com.example.gsb.charset.TestSamples.*;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/** 要求 5：按判定结果转换，失败时定位到字节偏移。 */
class ConversionOffsetTest {

    private final CharsetTranscoder transcoder = new CharsetTranscoder();
    private final CharsetDetector detector = new CharsetDetector();

    @Test
    void roundTripsAllFourEncodings() {
        assertThat(transcoder.decode(utf8(MIXED), Encoding.UTF_8)).isEqualTo(MIXED);
        assertThat(transcoder.decode(utf16be(MIXED), Encoding.UTF_16BE)).isEqualTo(MIXED);
        assertThat(transcoder.decode(utf16le(MIXED), Encoding.UTF_16LE)).isEqualTo(MIXED);
        assertThat(transcoder.decode(gbk(ZH_1), Encoding.GBK)).isEqualTo(ZH_1);
        assertThat(transcoder.decode(latin1(FR_1), Encoding.ISO_8859_1)).isEqualTo(FR_1);
    }

    @Test
    void utf8CorruptContinuationByteReportsSequenceStart() {
        // "héllo"：é = C3 A9，把 A9 改成 'X' → 非法序列起始于偏移 1
        byte[] data = utf8("héllo");
        data[2] = 'X';
        assertThatThrownBy(() -> transcoder.decode(data, Encoding.UTF_8))
                .isInstanceOf(MalformedContentException.class)
                .satisfies(e -> {
                    MalformedContentException ex = (MalformedContentException) e;
                    assertThat(ex.getByteOffset()).isEqualTo(1);
                    assertThat(ex.getEncoding()).isEqualTo(Encoding.UTF_8);
                });
    }

    @Test
    void utf8TruncatedMultibyteAtEndReportsOffset() {
        byte[] full = utf8("ab中"); // 61 62 E4 B8 AD
        byte[] truncated = new byte[4]; // 61 62 E4 B8 → 截断的三字节序列起始于 2
        System.arraycopy(full, 0, truncated, 0, 4);
        assertThatThrownBy(() -> transcoder.decode(truncated, Encoding.UTF_8))
                .isInstanceOf(MalformedContentException.class)
                .satisfies(e -> assertThat(((MalformedContentException) e).getByteOffset()).isEqualTo(2));
    }

    @Test
    void gbkCorruptByteReportsExactOffset() {
        byte[] data = gbk("你好ABC"); // C4 E3 BA C3 41 42 43
        data[4] = (byte) 0xFF;       // 非法 GBK 字节，位于偏移 4
        assertThatThrownBy(() -> transcoder.decode(data, Encoding.GBK))
                .isInstanceOf(MalformedContentException.class)
                .satisfies(e -> assertThat(((MalformedContentException) e).getByteOffset()).isEqualTo(4));
    }

    @Test
    void gbkDanglingLeadByteAtEnd() {
        byte[] data = concat(gbk("你"), new byte[]{(byte) 0xC4}); // 悬空首字节在偏移 2
        assertThatThrownBy(() -> transcoder.decode(data, Encoding.GBK))
                .isInstanceOf(MalformedContentException.class)
                .satisfies(e -> assertThat(((MalformedContentException) e).getByteOffset()).isEqualTo(2));
    }

    @Test
    void utf16LeOddLengthReportsLastByte() {
        byte[] data = concat(utf16le("AB"), new byte[]{0x41}); // 5 字节，悬空字节在偏移 4
        assertThatThrownBy(() -> transcoder.decode(data, Encoding.UTF_16LE))
                .isInstanceOf(MalformedContentException.class)
                .satisfies(e -> assertThat(((MalformedContentException) e).getByteOffset()).isEqualTo(4));
    }

    @Test
    void utf16BeUnpairedSurrogateReportsItsOffset() {
        byte[] data = utf16be("AB"); // 00 41 00 42
        byte[] withSurrogate = concat(data, new byte[]{(byte) 0xD8, 0x00}); // 未配对高代理项在偏移 4
        assertThatThrownBy(() -> transcoder.decode(withSurrogate, Encoding.UTF_16BE))
                .isInstanceOf(MalformedContentException.class)
                .satisfies(e -> assertThat(((MalformedContentException) e).getByteOffset()).isEqualTo(4));
    }

    @Test
    void detectThenDecodeEndToEnd() {
        byte[] data = gbk(ZH_2);
        DetectionResult result = detector.detect(data);
        DecodedText decoded = transcoder.decodeAll(data, result);
        assertThat(decoded.text()).isEqualTo(ZH_2);
        assertThat(decoded.encoding()).isEqualTo(Encoding.GBK);
    }
}
