package com.example.gsb.charset;

import java.nio.charset.Charset;
import java.util.Optional;

/** 组件支持的字符集族。UTF-16 按字节序拆分为两个独立候选。 */
public enum CharsetFamily {
    UTF_8("UTF-8"),
    UTF_16LE("UTF-16LE"),
    UTF_16BE("UTF-16BE"),
    GBK("GBK"),
    ISO_8859_1("ISO-8859-1");

    private final String ianaName;

    CharsetFamily(String ianaName) {
        this.ianaName = ianaName;
    }

    public String ianaName() {
        return ianaName;
    }

    public Charset charset() {
        return Charset.forName(ianaName);
    }

    /** 按常见名称/别名解析字符集族，未知名称返回 empty。 */
    public static Optional<CharsetFamily> fromIana(String name) {
        if (name == null) {
            return Optional.empty();
        }
        String n = name.trim().toUpperCase().replace('_', '-');
        return switch (n) {
            case "UTF-8", "UTF8" -> Optional.of(UTF_8);
            case "UTF-16LE", "UTF-16-LE" -> Optional.of(UTF_16LE);
            case "UTF-16BE", "UTF-16-BE" -> Optional.of(UTF_16BE);
            case "GBK", "GB2312", "GB-2312", "CP936", "WINDOWS-936" -> Optional.of(GBK);
            case "ISO-8859-1", "ISO8859-1", "LATIN1", "LATIN-1" -> Optional.of(ISO_8859_1);
            default -> Optional.empty();
        };
    }
}
