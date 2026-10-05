package com.example.gsb.charset;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.EnumMap;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * 用已知编码生成的多样本验证检测准确率。
 * 语料共 39 组：UTF-8 10 组、GBK 4 组、ISO-8859-1 5 组、
 * UTF-16LE 10 组、UTF-16BE 10 组，覆盖中/日/韩/俄/阿拉伯/拉丁/emoji 等内容。
 */
class DetectionAccuracyTest {

    private final CharsetDetector detector = new CharsetDetector();

    @Test
    @DisplayName("已知编码样本检测准确率不低于 95%")
    void accuracyAtLeast95Percent() {
        List<Sample> samples = buildCorpus();
        Map<CharsetFamily, int[]> stats = new EnumMap<>(CharsetFamily.class);
        for (CharsetFamily f : CharsetFamily.values()) {
            stats.put(f, new int[2]);
        }
        int correct = 0;
        List<String> failures = new ArrayList<>();
        StringBuilder report = new StringBuilder("准确率验证报告：\n");

        for (Sample sample : samples) {
            DetectionResult r = detector.detect(sample.data());
            boolean ok = r.conclusive()
                    && r.best().isPresent()
                    && r.best().get().family() == sample.expected();
            stats.get(sample.expected())[1]++;
            if (ok) {
                correct++;
                stats.get(sample.expected())[0]++;
            } else {
                failures.add(sample.expected() + " 判定失败：" + r.summary());
            }
        }

        for (CharsetFamily f : CharsetFamily.values()) {
            int[] s = stats.get(f);
            if (s[1] > 0) {
                report.append(String.format("  %-10s %2d/%2d = %5.1f%%%n",
                        f.ianaName(), s[0], s[1], 100.0 * s[0] / s[1]));
            }
        }
        double accuracy = 100.0 * correct / samples.size();
        report.append(String.format("  总计       %2d/%2d = %5.1f%%%n",
                correct, samples.size(), accuracy));
        System.out.println(report);

        assertThat(accuracy)
                .as("检测失败明细：\n" + String.join("\n", failures))
                .isGreaterThanOrEqualTo(95.0);
    }

    private List<Sample> buildCorpus() {
        List<Sample> samples = new ArrayList<>();
        for (String text : SampleTexts.UNICODE_TEXTS) {
            samples.add(new Sample(CharsetFamily.UTF_8, text.getBytes(StandardCharsets.UTF_8)));
            samples.add(new Sample(CharsetFamily.UTF_16LE, text.getBytes(StandardCharsets.UTF_16LE)));
            samples.add(new Sample(CharsetFamily.UTF_16BE, text.getBytes(StandardCharsets.UTF_16BE)));
        }
        for (String gbkText : new String[] {SampleTexts.ZH_NEWS, SampleTexts.ZH_POEM,
                SampleTexts.ZH_MIXED, SampleTexts.ZH_JAVA}) {
            samples.add(new Sample(CharsetFamily.GBK, gbkText.getBytes(CharsetFamily.GBK.charset())));
        }
        for (String latin : SampleTexts.LATIN1_TEXTS) {
            samples.add(new Sample(CharsetFamily.ISO_8859_1, latin.getBytes(StandardCharsets.ISO_8859_1)));
        }
        return samples;
    }

    private record Sample(CharsetFamily expected, byte[] data) {
    }
}
