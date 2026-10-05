# 字符集检测与转换组件

Pair-wise GSB 标注任务仓库（第 17 批 / 274）。

| 项目 | 内容 |
|------|------|
| 任务类型 | Feature 迭代 |
| 任务难度 | 困难 |
| 语言/框架 | Java, Maven, JUnit 5 |
| 环境可复现等级 | 无外部依赖 |
| 构建方式 | Maven（含 mvnw wrapper，无需本机安装 Maven） |

> 本仓库是**初始环境快照**：只有工程骨架，不含任何实现代码。
> 分支说明：`main` 为初始环境；`A`、`B` 为两次独立执行各自的工作分支，均从 `main` 的同一个提交拉出。

## 运行方式

```bash
./mvnw -q verify
```

## 任务提示词

以下为本题完整的 User Prompt 原文，两次执行必须使用完全相同的文本。

我们的外部数据来源编码不统一，读进来经常出现乱码，需要在解析前判断真实编码。请从零实现一个字符集检测与转换组件。仓库目前只有一个空的 Maven 工程（pom.xml 只声明 JUnit 5 与 AssertJ）。要求：1) 支持在 UTF-8、UTF-16、GBK 与 ISO-8859-1 之间检测字节序列的实际编码；2) 支持带 BOM 的识别并优先按 BOM 判定，需说明 BOM 与内容冲突时的处理；3) 支持无 BOM 时的启发式判定：基于非法序列、字节分布与常见字符范围给出候选编码与置信度；4) 支持检测失败：无法确定时要返回多个候选并说明原因，不得随便选一个返回乱码；5) 支持转换：按判定结果解码为字符串，转换失败的位置要能定位到字节偏移；6) 正确性：对已知编码生成的样本必须判定正确，需用多组样本验证并给出准确率；7) 支持流式检测：对超大文件只读前若干字节即可判定，需说明判定依据与局限；8) 测试覆盖四种编码识别、BOM 优先、启发式候选、检测失败、转换错误定位与准确率验证；`mvn -q verify` 一条命令跑通。

## 提交要求

1. 在本仓库中完成提示词要求的全部内容。
2. `./mvnw -q verify` 必须通过。
3. 完成后在所属分支（A 或 B）上提交，产物快照的父提交必须是初始环境快照。

---

# 组件文档：字符集检测与转换

实现位于 `src/main/java/com/example/gsb/charset/`，零第三方依赖（仅 JDK）。

## 快速使用

```java
CharsetDetector detector = new CharsetDetector();
DetectionResult result = detector.detect(bytes);          // 全量检测
DetectionResult r2 = detector.detectPrefix(inputStream);  // 流式：只读前 8192 字节

if (result.conclusive()) {
    String text = new CharsetConverter().decode(bytes, result).orElseThrow();
} else {
    // result.candidates() 多个候选 + result.reasons() 原因，不要强行解码
}
```

## 判定流程与依据

1. **BOM 优先**：识别 UTF-8（`EF BB BF`）、UTF-16LE（`FF FE`）、UTF-16BE（`FE FF`）
   三种 BOM，命中即以置信度 1.0 判定。GBK 与 ISO-8859-1 无标准 BOM。
2. **无 BOM 启发式**：对四种编码分别做严格结构扫描（非法序列直接淘汰并记录
   偏移），再对幸存候选按字节分布与常见字符范围评分：
   - UTF-8：多字节序列严格合法（拒绝过短编码/孤立后续字节/代理区），多字节
     字符越多、落常见字符范围比例越高，置信度越高；
   - UTF-16：码元合法 + 代理对配对，零字节对齐率（ASCII 文本特征）；无 BOM 时
     要求佐证（零字节对齐 / ASCII 单元 / CJK 标点至少其一），防止拉丁或 GBK
     字节流“碰巧”解析成一串 CJK 码元被误判；
   - GBK：双字节结构合法（前导 0x81-0xFE、后续 0x40-0xFE 非 0x7F、拒绝 0x80/0xFF），
     按解码结果的常见范围与私用区映射比例评分；
   - ISO-8859-1：任何字节均合法，靠反向证据压分——C1 控制字符、NUL、空格缺失、
     高位字节占比过高、相邻高位字节对（真实拉丁文本的重音字母孤立出现）。
3. **收敛条件**：最高置信度 ≥ 0.78 且与第二名差距 ≥ 0.12，否则返回
   `conclusive=false`、全部候选与原因，绝不随便选一个返回乱码。

## BOM 与内容冲突的处理

BOM 优先判定后，仍会用内容做交叉校验：若内容按 BOM 声明的编码严格校验失败，
或另一种编码的启发式证据明显更强，则结果标记为**不收敛**（`conclusive=false`），
`best()` 保留 BOM 判定结果并在 `reasons()` 中给出冲突警告，由调用方人工裁决
（可用 `CharsetConverter.decodeAs` 显式指定编码），组件不会静默产出乱码。

## 检测失败

以下情况返回多个候选而非单一结论：纯 ASCII 内容（四种编码字节完全相同，原理上
不可区分）、随机/二进制数据、过短样本、多候选得分接近。`DecodeResult` 在检测
未收敛时直接失败，`CharsetConverter.decode` 拒绝猜测。

## 转换与错误定位

`CharsetConverter` 严格解码（UTF-8/UTF-16 为手写解码器，GBK 结构预检 + JDK 解码），
失败时 `DecodeResult.errorOffset()` 给出原始字节数组中第一个出错字节的偏移
（含 BOM 前缀），`orElseThrow()` 抛出携带偏移的 `CharsetDecodeException`。

## 流式检测的依据与局限

`detectPrefix(InputStream, maxBytes)` 只读流的前若干字节（默认 8192）：

- **依据**：BOM 位于流最开头；非法序列比例与字节分布对自然语言文本是全局稳定
  的统计特征，几千字节即可代表整体；样本末尾被截断的多字节序列不计为非法。
- **局限**：样本过小时置信度下降，可能返回不收敛；纯 ASCII 前缀无法区分四种
  编码；文件中途切换编码的混合文件无法被前缀检测发现；前缀恰好落在非典型
  片段（如大段数字）上时结论可能偏差。

## 准确率验证

`DetectionAccuracyTest` 用已知编码生成 39 组样本（UTF-8/UTF-16LE/UTF-16BE 各 10 组，
GBK 4 组，ISO-8859-1 5 组，覆盖中/日/韩/俄/阿拉伯/拉丁/emoji 等内容），
当前结果 **39/39 = 100%**（阈值断言 ≥ 95%），运行 `mvn verify` 时打印分编码报告。

## 已知限制

- 极短文本（如单个重音词 "café"）在 UTF-8/GBK/ISO-8859-1 间原理性歧义，
  组件会返回不收敛而非猜测；
- GB2312 按 GBK 超集处理；UTF-16 无 BOM 且无标点/ASCII 的纯 CJK 短文本可能不收敛。
