package com.example.gsb.charset;

import java.nio.charset.Charset;
import java.util.Arrays;

/**
 * 本组件支持检测 / 转换的字符集。
 *
 * <p>UTF-16 按字节序拆分为 {@link #UTF_16BE} 与 {@link #UTF_16LE} 两个候选，
 * 无 BOM 时依据零字节与 CJK 高字节分布区分大小端。</p>
 */
public enum Encoding {

    UTF_8("UTF-8", new byte[]{(byte) 0xEF, (byte) 0xBB, (byte) 0xBF}),
    UTF_16BE("UTF-16BE", new byte[]{(byte) 0xFE, (byte) 0xFF}),
    UTF_16LE("UTF-16LE", new byte[]{(byte) 0xFF, (byte) 0xFE}),
    GBK("GBK", new byte[0]),
    ISO_8859_1("ISO-8859-1", new byte[0]);

    private final Charset charset;
    private final byte[] bom;

    Encoding(String charsetName, byte[] bom) {
        this.charset = Charset.forName(charsetName);
        this.bom = bom.clone();
    }

    /** 对应的 JDK {@link Charset}。 */
    public Charset charset() {
        return charset;
    }

    /** 该编码的 BOM 字节序列；无 BOM 约定的编码返回空数组。 */
    public byte[] bom() {
        return bom.clone();
    }

    /** 该编码是否约定了 BOM。 */
    public boolean hasBom() {
        return bom.length > 0;
    }

    boolean matchesBom(byte[] data, int off, int len) {
        if (bom.length == 0 || len < bom.length) {
            return false;
        }
        for (int i = 0; i < bom.length; i++) {
            if (data[off + i] != bom[i]) {
                return false;
            }
        }
        return true;
    }

    @Override
    public String toString() {
        return charset.name() + (bom.length > 0 ? " (BOM: " + Arrays.toString(bom) + ")" : "");
    }
}
