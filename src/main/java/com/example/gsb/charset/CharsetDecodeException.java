package com.example.gsb.charset;

/** 解码失败异常，携带出错位置的字节偏移。 */
public class CharsetDecodeException extends RuntimeException {

    private final int errorOffset;

    public CharsetDecodeException(int errorOffset, String message) {
        super(message + "（字节偏移 " + errorOffset + "）");
        this.errorOffset = errorOffset;
    }

    public int errorOffset() {
        return errorOffset;
    }
}
