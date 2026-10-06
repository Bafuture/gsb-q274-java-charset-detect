package com.example.gsb.charset;

/**
 * 检测失败（无法确定编码）时抛出。
 *
 * <p>组件约定：宁可报错也绝不随便选一个编码返回乱码。
 * 调用方可通过 {@link #getResult()} 拿到全部候选与判定依据，人工选择后再用
 * {@link CharsetTranscoder#decode(byte[], Encoding)} 显式解码。</p>
 */
public class UndeterminedEncodingException extends RuntimeException {

    private final transient DetectionResult result;

    public UndeterminedEncodingException(DetectionResult result) {
        super("无法确定编码，候选如下：" + System.lineSeparator() + result.explain());
        this.result = result;
    }

    /** 携带的检测结果（含全部候选编码、置信度与原因）。 */
    public DetectionResult getResult() {
        return result;
    }
}
