package com.example.gsb.charset;

/**
 * 解码结果。
 *
 * @param text        解码得到的字符串（若存在 BOM，已剥离）
 * @param encoding    实际使用的编码
 * @param bomStripped 是否剥离了 BOM
 */
public record DecodedText(String text, Encoding encoding, boolean bomStripped) {
}
