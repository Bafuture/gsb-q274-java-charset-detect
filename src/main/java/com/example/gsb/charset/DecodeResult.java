package com.example.gsb.charset;

/**
 * 解码（字节 -> 字符串）结果。
 *
 * <p>{@code success=false} 时 {@link #errorOffset()} 给出失败位置在
 * 原始字节数组中的偏移（相对数组起始，含 BOM 前缀长度），
 * {@link #message()} 说明失败原因。
 */
public record DecodeResult(boolean success, String text, int errorOffset, String message) {

    public static DecodeResult ok(String text) {
        return new DecodeResult(true, text, -1, "OK");
    }

    public static DecodeResult failure(int errorOffset, String message) {
        return new DecodeResult(false, null, errorOffset, message);
    }

    /** 成功时返回文本；失败时抛出携带字节偏移的 {@link CharsetDecodeException}。 */
    public String orElseThrow() {
        if (!success) {
            throw new CharsetDecodeException(errorOffset, message);
        }
        return text;
    }
}
