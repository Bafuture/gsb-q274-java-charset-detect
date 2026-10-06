package com.example.gsb.charset;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import static com.example.gsb.charset.TestSamples.*;
import static org.assertj.core.api.Assertions.assertThat;

/** 要求 7：超大文件只读前若干字节即可判定。 */
class StreamingDetectionTest {

    private final CharsetDetector detector = new CharsetDetector();

    @TempDir
    Path dir;

    @Test
    void hugeUtf8FileDetectedFromFirstBytes() throws IOException {
        byte[] chunk = utf8(ZH_1 + ZH_2 + EN_1);
        byte[] big = repeatToSize(chunk, 3 * 1024 * 1024); // 约 3MB
        Path file = Files.write(dir.resolve("big-utf8.txt"), big);

        DetectionResult r = detector.detect(file, 4096);
        assertThat(r.bytesExamined()).isEqualTo(4096);
        assertThat(r.isTruncatedSample()).isTrue();
        assertThat(r.encoding()).isEqualTo(Encoding.UTF_8);
    }

    @Test
    void hugeGbkFileDetectedFromFirstBytes() throws IOException {
        byte[] chunk = gbk(ZH_2 + ZH_3);
        byte[] big = repeatToSize(chunk, 2 * 1024 * 1024);
        Path file = Files.write(dir.resolve("big-gbk.txt"), big);

        DetectionResult r = detector.detect(file, 8192);
        assertThat(r.bytesExamined()).isEqualTo(8192);
        assertThat(r.encoding()).isEqualTo(Encoding.GBK);
    }

    @Test
    void hugeUtf16LeFileDetectedFromFirstBytes() throws IOException {
        byte[] chunk = utf16le(ZH_1 + EN_1);
        byte[] big = repeatToSize(chunk, 2 * 1024 * 1024);
        Path file = Files.write(dir.resolve("big-utf16.txt"), big);

        DetectionResult r = detector.detect(file, 4095); // 奇数截断也应容忍
        assertThat(r.bytesExamined()).isEqualTo(4095);
        assertThat(r.encoding()).isEqualTo(Encoding.UTF_16LE);
    }

    @Test
    void bomFileDetectedFromTinySample() throws IOException {
        byte[] big = withBom(Encoding.UTF_8, repeatToSize(utf8(ZH_1), 1024 * 1024));
        Path file = Files.write(dir.resolve("big-bom.txt"), big);

        DetectionResult r = detector.detect(file, 64);
        assertThat(r.isBomDetected()).isTrue();
        assertThat(r.encoding()).isEqualTo(Encoding.UTF_8);
    }

    @Test
    void truncatedMultibyteAtSampleBoundaryIsTolerated() throws IOException {
        // 3000 字节 = 1000 个“中”；采样 2999 字节会把最后一个字符拦腰截断
        byte[] data = utf8("中".repeat(1000));
        DetectionResult r = detector.detect(new ByteArrayInputStream(data), 2999);
        assertThat(r.bytesExamined()).isEqualTo(2999);
        assertThat(r.isTruncatedSample()).isTrue();
        assertThat(r.encoding()).isEqualTo(Encoding.UTF_8);
    }

    @Test
    void streamIsNotReadBeyondLimit() throws IOException {
        byte[] data = gbk(ZH_1 + ZH_2);
        InputStream in = new ByteArrayInputStream(data);
        DetectionResult r = detector.detect(in, 32);
        assertThat(r.bytesExamined()).isEqualTo(32);
        // 流中应剩余未读数据（内部最多多读 1 字节用于判断截断）
        assertThat(in.available()).isGreaterThanOrEqualTo(data.length - 33);
    }

    @Test
    void smallFileIsReadFullyAndNotMarkedTruncated() throws IOException {
        byte[] data = gbk(ZH_1);
        Path file = Files.write(dir.resolve("small.txt"), data);
        DetectionResult r = detector.detect(file, 65536);
        assertThat(r.bytesExamined()).isEqualTo(data.length);
        assertThat(r.isTruncatedSample()).isFalse();
        assertThat(r.encoding()).isEqualTo(Encoding.GBK);
    }

    private static byte[] repeatToSize(byte[] chunk, int size) {
        byte[] out = new byte[size];
        for (int pos = 0; pos < size; pos += chunk.length) {
            System.arraycopy(chunk, 0, out, pos, Math.min(chunk.length, size - pos));
        }
        return out;
    }
}
