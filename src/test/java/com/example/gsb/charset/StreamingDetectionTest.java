package com.example.gsb.charset;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;

import static org.assertj.core.api.Assertions.assertThat;

class StreamingDetectionTest {

    private final CharsetDetector detector = new CharsetDetector();

    @Test
    @DisplayName("超大 GBK 文件只读前 4096 字节即可判定")
    void hugeGbkFileDetectedFromPrefix() throws IOException {
        byte[] huge = SampleTexts.ZH_NEWS.repeat(20000).getBytes(CharsetFamily.GBK.charset());
        assertThat(huge.length).isGreaterThan(1024 * 1024);
        try (InputStream in = new ByteArrayInputStream(huge)) {
            DetectionResult r = detector.detectPrefix(in, 4096);
            assertThat(r.conclusive()).as(r.summary()).isTrue();
            assertThat(r.best().orElseThrow().family()).isEqualTo(CharsetFamily.GBK);
            assertThat(r.reasons()).anyMatch(s -> s.contains("流式模式"));
        }
    }

    @Test
    @DisplayName("超大 UTF-8 文件使用默认前缀判定")
    void hugeUtf8FileDetectedFromDefaultPrefix() throws IOException {
        byte[] huge = SampleTexts.ZH_NEWS.repeat(20000).getBytes(StandardCharsets.UTF_8);
        try (InputStream in = new ByteArrayInputStream(huge)) {
            DetectionResult r = detector.detectPrefix(in);
            assertThat(r.conclusive()).as(r.summary()).isTrue();
            assertThat(r.best().orElseThrow().family()).isEqualTo(CharsetFamily.UTF_8);
        }
    }

    @Test
    @DisplayName("前缀恰好截断多字节序列时不误判为非法")
    void prefixCuttingMultibyteSequenceIsTolerated() throws IOException {
        // UTF-8 中文字符 3 字节，取奇数长度前缀，末尾必截断一个序列
        byte[] data = SampleTexts.ZH_NEWS.getBytes(StandardCharsets.UTF_8);
        byte[] prefix = java.util.Arrays.copyOf(data, 101);
        try (InputStream in = new ByteArrayInputStream(prefix)) {
            DetectionResult r = detector.detectPrefix(in, 101);
            assertThat(r.best().orElseThrow().family()).isEqualTo(CharsetFamily.UTF_8);
            assertThat(r.reasons()).anyMatch(s -> s.contains("截断"));
        }
    }

    @Test
    @DisplayName("带 BOM 的流：BOM 位于开头，前缀检测同样命中")
    void bomStreamDetectedFromPrefix() throws IOException {
        byte[] content = SampleTexts.ZH_NEWS.getBytes(StandardCharsets.UTF_16LE);
        byte[] data = new byte[content.length + 2];
        data[0] = (byte) 0xFF;
        data[1] = (byte) 0xFE;
        System.arraycopy(content, 0, data, 2, content.length);
        try (InputStream in = new ByteArrayInputStream(data)) {
            DetectionResult r = detector.detectPrefix(in, 512);
            assertThat(r.conclusive()).isTrue();
            assertThat(r.best().orElseThrow().family()).isEqualTo(CharsetFamily.UTF_16LE);
        }
    }
}
