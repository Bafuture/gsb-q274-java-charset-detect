package com.example.gsb.charset;

import java.nio.charset.Charset;
import java.nio.charset.StandardCharsets;

/** 测试样本工具：已知编码的文本与字节生成。 */
final class TestSamples {

    static final Charset GBK = Charset.forName("GBK");

    static final String ZH_1 = "中华人民共和国成立于一九四九年，汉字编码测试样本。";
    static final String ZH_2 = "外部数据来源编码不统一，读进来经常出现乱码，需要在解析前判断真实编码。";
    static final String ZH_3 = "春眠不觉晓，处处闻啼鸟。夜来风雨声，花落知多少。";
    static final String EN_1 = "The quick brown fox jumps over the lazy dog.";
    static final String EN_2 = "Charset detection is a classic problem in data integration.";
    static final String FR_1 = "Voilà un café très chic à côté de la façade.";
    static final String FR_2 = "Noël en décembre, ça coûte très cher.";
    static final String FR_3 = "À bientôt, naïve âme! Où est l'hôpital, s'il vous plaît?";
    static final String EMOJI = "表情符号测试 😀🎉🚀 结束";
    static final String MIXED = "订单号 ORDER-2024-1024，客户：张三，备注：fragile, à manipuler avec précaution！";

    private TestSamples() {
    }

    static byte[] utf8(String s) {
        return s.getBytes(StandardCharsets.UTF_8);
    }

    static byte[] utf16be(String s) {
        return s.getBytes(StandardCharsets.UTF_16BE);
    }

    static byte[] utf16le(String s) {
        return s.getBytes(StandardCharsets.UTF_16LE);
    }

    static byte[] gbk(String s) {
        return s.getBytes(GBK);
    }

    static byte[] latin1(String s) {
        return s.getBytes(StandardCharsets.ISO_8859_1);
    }

    static byte[] withBom(Encoding encoding, byte[] content) {
        byte[] bom = encoding.bom();
        byte[] out = new byte[bom.length + content.length];
        System.arraycopy(bom, 0, out, 0, bom.length);
        System.arraycopy(content, 0, out, bom.length, content.length);
        return out;
    }

    static byte[] concat(byte[]... parts) {
        int total = 0;
        for (byte[] p : parts) {
            total += p.length;
        }
        byte[] out = new byte[total];
        int pos = 0;
        for (byte[] p : parts) {
            System.arraycopy(p, 0, out, pos, p.length);
            pos += p.length;
        }
        return out;
    }
}
