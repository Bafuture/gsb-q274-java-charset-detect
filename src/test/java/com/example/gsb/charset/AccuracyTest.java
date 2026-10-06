package com.example.gsb.charset;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Random;
import org.junit.jupiter.api.Test;

import static com.example.gsb.charset.TestSamples.*;
import static org.assertj.core.api.Assertions.assertThat;

/**
 * 要求 6：对已知编码生成的多样本批量验证准确率。
 *
 * <p>每种编码 24 个样本（共 120 个），样本由中英文、法文（Latin-1 重音字符）、
 * emoji 片段随机组合而成；UTF-8 与 UTF-16 中包含带/不带 BOM 两类样本。</p>
 *
 * <p>纯 ASCII 样本不纳入准确率统计：它在 UTF-8/GBK/ISO-8859-1 下字节完全一致，
 * 任何检测算法都无法区分（组件此时应返回“检测失败”，由 {@link UndeterminedTest} 验证）。</p>
 */
class AccuracyTest {

    private static final int PER_ENCODING = 24;

    private record Sample(byte[] data, Encoding expected, String description) {
    }

    @Test
    void accuracyOnGeneratedSamples() {
        List<Sample> samples = buildSamples();
        CharsetDetector detector = new CharsetDetector();

        Map<Encoding, Integer> total = new LinkedHashMap<>();
        Map<Encoding, Integer> correct = new LinkedHashMap<>();
        List<String> failures = new ArrayList<>();

        for (Sample s : samples) {
            total.merge(s.expected(), 1, Integer::sum);
            DetectionResult r = detector.detect(s.data());
            boolean ok = r.isDetermined() && r.encoding() == s.expected();
            if (ok) {
                correct.merge(s.expected(), 1, Integer::sum);
            } else {
                failures.add(String.format("期望 %s，实际 %s [%s]%n%s",
                        s.expected(), r.isDetermined() ? r.encoding().charset().name() : "UNDETERMINED",
                        s.description(), r.explain()));
            }
        }

        System.out.println("================ 编码检测准确率报告 ================");
        int allTotal = 0;
        int allCorrect = 0;
        for (Encoding e : total.keySet()) {
            int t = total.get(e);
            int c = correct.getOrDefault(e, 0);
            allTotal += t;
            allCorrect += c;
            System.out.printf("%-10s %2d/%2d  准确率 %.1f%%%n",
                    e.charset().name(), c, t, 100.0 * c / t);
        }
        System.out.printf("%-10s %2d/%2d  准确率 %.1f%%%n",
                "总计", allCorrect, allTotal, 100.0 * allCorrect / allTotal);
        failures.forEach(f -> System.out.println("失败样本:\n" + f));
        System.out.println("====================================================");

        failures.forEach(f -> assertThat(f).isEmpty()); // 逐个暴露失败细节
        assertThat((double) allCorrect / allTotal)
                .as("整体准确率")
                .isGreaterThanOrEqualTo(0.98);
        for (Encoding e : total.keySet()) {
            assertThat((double) correct.getOrDefault(e, 0) / total.get(e))
                    .as("%s 准确率", e.charset().name())
                    .isGreaterThanOrEqualTo(0.95);
        }
    }

    private List<Sample> buildSamples() {
        List<Sample> list = new ArrayList<>();
        Random rnd = new Random(20241006L);

        String[] unicodePool = {ZH_1, ZH_2, ZH_3, FR_1, FR_2, FR_3, EN_1, EN_2, EMOJI};
        String[] gbkPool = {ZH_1, ZH_2, ZH_3, EN_1, EN_2};
        String[] latinPool = {FR_1, FR_2, FR_3, EN_1, EN_2};

        for (int i = 0; i < PER_ENCODING; i++) {
            String text = compose(rnd, unicodePool, 6); // 前 6 个为非纯 ASCII 片段
            byte[] data = utf8(text);
            if (i % 4 == 0) {
                data = withBom(Encoding.UTF_8, data);
            }
            list.add(new Sample(data, Encoding.UTF_8, "#" + i + " " + brief(text)));
        }
        for (int i = 0; i < PER_ENCODING; i++) {
            String text = compose(rnd, unicodePool, 6);
            byte[] data = i % 2 == 0 ? withBom(Encoding.UTF_16BE, utf16be(text)) : utf16be(text);
            list.add(new Sample(data, Encoding.UTF_16BE, "#" + i + " " + brief(text)));
        }
        for (int i = 0; i < PER_ENCODING; i++) {
            String text = compose(rnd, unicodePool, 6);
            byte[] data = i % 2 == 0 ? withBom(Encoding.UTF_16LE, utf16le(text)) : utf16le(text);
            list.add(new Sample(data, Encoding.UTF_16LE, "#" + i + " " + brief(text)));
        }
        for (int i = 0; i < PER_ENCODING; i++) {
            String text = compose(rnd, gbkPool, 3); // 前 3 个为中文片段，保证含汉字
            list.add(new Sample(gbk(text), Encoding.GBK, "#" + i + " " + brief(text)));
        }
        for (int i = 0; i < PER_ENCODING; i++) {
            String text = compose(rnd, latinPool, 3); // 前 3 个为法文片段，保证含重音字符
            list.add(new Sample(latin1(text), Encoding.ISO_8859_1, "#" + i + " " + brief(text)));
        }
        return list;
    }

    /** 随机拼接 1-3 个片段，第一个必从含目标特征的片段中选取。 */
    private String compose(Random rnd, String[] pool, int requiredCount) {
        int n = 1 + rnd.nextInt(3);
        StringBuilder sb = new StringBuilder();
        for (int i = 0; i < n; i++) {
            String fragment;
            if (i == 0) {
                fragment = pool[rnd.nextInt(requiredCount)];
            } else {
                fragment = pool[rnd.nextInt(pool.length)];
            }
            if (sb.length() > 0) {
                sb.append(rnd.nextBoolean() ? "，" : " ");
            }
            sb.append(fragment);
        }
        return sb.toString();
    }

    private String brief(String text) {
        String oneLine = text.replace('\n', ' ');
        return oneLine.length() <= 40 ? oneLine : oneLine.substring(0, 40) + "...";
    }
}
