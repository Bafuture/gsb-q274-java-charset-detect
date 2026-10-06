package com.example.gsb.charset;

/**
 * 按指定编码解码失败时抛出，携带出错位置的字节偏移。
 *
 * <p>偏移语义：指向非法（或不可映射）序列的<strong>起始字节</strong>，
 * 与 JDK {@link java.nio.charset.CharsetDecoder} 报错时缓冲区位置一致。
 * 例如 UTF-8 三字节序列在第 2 个延续字节处损坏时，偏移指向该序列的第 1 个字节。</p>
 */
public class MalformedContentException extends RuntimeException {

    private final Encoding encoding;
    private final int byteOffset;
    private final int errorLength;

    public MalformedContentException(Encoding encoding, int byteOffset, int errorLength) {
        super(String.format("无法按 %s 解码：字节偏移 %d 处存在非法/不可映射序列（长度 %d 字节）",
                encoding.charset().name(), byteOffset, errorLength));
        this.encoding = encoding;
        this.byteOffset = byteOffset;
        this.errorLength = errorLength;
    }

    /** 解码时使用的编码。 */
    public Encoding getEncoding() {
        return encoding;
    }

    /** 出错序列的起始字节偏移（相对被解码数组的起始位置）。 */
    public int getByteOffset() {
        return byteOffset;
    }

    /** 出错序列占用的字节数。 */
    public int getErrorLength() {
        return errorLength;
    }
}
