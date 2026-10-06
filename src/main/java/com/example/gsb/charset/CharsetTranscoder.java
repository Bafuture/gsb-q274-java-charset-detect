package com.example.gsb.charset;

import java.nio.ByteBuffer;
import java.nio.CharBuffer;
import java.nio.charset.CharacterCodingException;
import java.nio.charset.CharsetDecoder;
import java.nio.charset.CodingErrorAction;
import java.nio.file.Files;
import java.nio.file.Path;
import java.io.IOException;

/**
 * 按判定结果把字节序列解码为字符串。
 *
 * <p>所有解码都以 {@link CodingErrorAction#REPORT} 严格模式进行：
 * 遇到非法 / 不可映射序列时抛出 {@link MalformedContentException}，
 * 并给出出错序列起始位置的字节偏移（相对传入数组），绝不静默替换为乱码字符。</p>
 *
 * <p>若数据带有与指定编码匹配的 BOM，解码前会自动剥离。</p>
 */
public final class CharsetTranscoder {

    public CharsetTranscoder() {
    }

    /** 解码整个数组（自动剥离匹配的 BOM）。 */
    public String decode(byte[] data, Encoding encoding) {
        if (data == null || encoding == null) {
            throw new IllegalArgumentException("data 与 encoding 不能为 null");
        }
        return decode(data, 0, data.length, encoding);
    }

    /**
     * 解码 {@code data[off, off+len)}（自动剥离匹配的 BOM）。
     *
     * @throws MalformedContentException 解码失败，异常携带出错位置的字节偏移
     */
    public String decode(byte[] data, int off, int len, Encoding encoding) {
        if (data == null || encoding == null) {
            throw new IllegalArgumentException("data 与 encoding 不能为 null");
        }
        if (off < 0 || len < 0 || off + len > data.length) {
            throw new IndexOutOfBoundsException("非法范围: off=" + off + ", len=" + len);
        }
        int start = off;
        if (encoding.matchesBom(data, off, len)) {
            start += encoding.bom().length;
        }
        int remaining = off + len - start;

        CharsetDecoder decoder = encoding.charset().newDecoder()
                .onMalformedInput(CodingErrorAction.REPORT)
                .onUnmappableCharacter(CodingErrorAction.REPORT);
        CharBuffer out = CharBuffer.allocate(
                (int) Math.max(16, Math.ceil(remaining * (double) decoder.maxCharsPerByte())));
        ByteBuffer in = ByteBuffer.wrap(data, start, remaining);
        while (true) {
            var result = decoder.decode(in, out, true);
            if (result.isUnderflow()) {
                break;
            }
            if (result.isError()) {
                // ByteBuffer.wrap(array, start, n) 的初始 position 即为 start，
                // 报错时 position 停在非法序列起始字节，即绝对数组偏移
                throw new MalformedContentException(encoding, in.position(), result.length());
            }
            if (result.isOverflow()) {
                CharBuffer bigger = CharBuffer.allocate(out.capacity() * 2 + 16);
                out.flip();
                bigger.put(out);
                out = bigger;
            }
        }
        out.flip();
        return out.toString();
    }

    /** 读取整个文件并按指定编码解码。 */
    public String decode(Path file, Encoding encoding) throws IOException {
        return decode(Files.readAllBytes(file), encoding);
    }

    /**
     * 按检测结果解码：检测失败时抛 {@link UndeterminedEncodingException}，
     * 绝不随便选一个候选返回乱码。
     */
    public DecodedText decodeAll(byte[] data, DetectionResult result) {
        Encoding encoding = result.encoding();
        String text = decode(data, encoding);
        return new DecodedText(text, encoding, result.isBomDetected());
    }
}
