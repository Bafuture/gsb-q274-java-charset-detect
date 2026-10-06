package com.example.gsb.charset;

import org.junit.jupiter.api.Test;

import static com.example.gsb.charset.TestSamples.*;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/** 要求 2：BOM 识别、BOM 优先、BOM 与内容冲突的处理。 */
class BomPriorityTest {

    private final CharsetDetector detector = new CharsetDetector();

    @Test
    void detectsUtf8Bom() {
        DetectionResult r = detector.detect(withBom(Encoding.UTF_8, utf8(ZH_1)));
        assertThat(r.isBomDetected()).isTrue();
        assertThat(r.isBomContentConflict()).isFalse();
        assertThat(r.encoding()).isEqualTo(Encoding.UTF_8);
        assertThat(r.best().orElseThrow().confidence()).isGreaterThan(0.95);
    }

    @Test
    void detectsUtf16BeAndLeBom() {
        DetectionResult be = detector.detect(withBom(Encoding.UTF_16BE, utf16be(ZH_1 + EN_1)));
        assertThat(be.encoding()).isEqualTo(Encoding.UTF_16BE);
        assertThat(be.isBomDetected()).isTrue();

        DetectionResult le = detector.detect(withBom(Encoding.UTF_16LE, utf16le(ZH_1 + EN_1)));
        assertThat(le.encoding()).isEqualTo(Encoding.UTF_16LE);
        assertThat(le.isBomDetected()).isTrue();
    }

    @Test
    void bomWinsOverHeuristics() {
        // 纯 ASCII 内容：无 BOM 时启发式无法确定，有 UTF-8 BOM 时必须按 BOM 判定
        byte[] data = withBom(Encoding.UTF_8, utf8(EN_1));
        DetectionResult r = detector.detect(data);
        assertThat(r.isDetermined()).isTrue();
        assertThat(r.encoding()).isEqualTo(Encoding.UTF_8);
    }

    @Test
    void bomContentConflictStillPrefersBomButFlagsIt() {
        // UTF-8 BOM + 实际是 GBK 编码的中文内容
        byte[] data = withBom(Encoding.UTF_8, gbk(ZH_1));
        DetectionResult r = detector.detect(data);
        assertThat(r.isBomDetected()).isTrue();
        assertThat(r.isBomContentConflict()).isTrue();
        // 策略：仍按 BOM 优先判定
        assertThat(r.encoding()).isEqualTo(Encoding.UTF_8);
        // 但备选候选中保留 GBK 供人工核对
        assertThat(r.candidates()).anyMatch(c -> c.encoding() == Encoding.GBK);
        // 解码时在第一个非法字节处报错（BOM 占 3 字节，内容从偏移 3 开始即非法）
        assertThatThrownBy(() -> new CharsetTranscoder().decode(data, Encoding.UTF_8))
                .isInstanceOf(MalformedContentException.class)
                .satisfies(e -> assertThat(((MalformedContentException) e).getByteOffset()).isEqualTo(3));
    }

    @Test
    void utf16BomWithCorruptContentIsConflict() {
        // UTF-16BE BOM + 奇数长度内容（末尾悬空字节）
        byte[] data = concat(withBom(Encoding.UTF_16BE, utf16be("AB")), new byte[]{0x00});
        DetectionResult r = detector.detect(data);
        assertThat(r.isBomDetected()).isTrue();
        assertThat(r.isBomContentConflict()).isTrue();
        assertThat(r.encoding()).isEqualTo(Encoding.UTF_16BE);
    }

    @Test
    void decodeStripsBom() {
        DecodedText decoded = detector.decode(withBom(Encoding.UTF_8, utf8("你好，世界")));
        assertThat(decoded.text()).isEqualTo("你好，世界");
        assertThat(decoded.bomStripped()).isTrue();

        String le = new CharsetTranscoder().decode(withBom(Encoding.UTF_16LE, utf16le("Hello 你好")),
                Encoding.UTF_16LE);
        assertThat(le).isEqualTo("Hello 你好");
    }
}
