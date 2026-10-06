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

# 实现说明（分支 B）

组件位于 `com.example.gsb.charset`，纯 JDK 实现、零第三方依赖。

## 模块结构

| 类 | 职责 |
|---|---|
| `CharsetDetector` | 检测入口：BOM 优先判定、启发式判定、流式采样（文件/流只读前 N 字节） |
| `Heuristics` | 无 BOM 时的评分模型与交叉校准（包私有） |
| `ByteValidators` | UTF-8/GBK/UTF-16/Latin-1 字节级扫描器（包私有） |
| `CharsetTranscoder` | 严格模式解码，失败时抛出带字节偏移的异常 |
| `DetectionResult` / `EncodingCandidate` | 检测结果：全部候选 + 置信度 + 判定依据 |
| `UndeterminedEncodingException` | 检测失败时抛出，携带全部候选，拒绝盲目解码 |
| `MalformedContentException` | 解码失败时抛出，携带出错字节偏移 |

## BOM 策略（要求 2）

- 识别 `EF BB BF`（UTF-8）、`FE FF`（UTF-16BE）、`FF FE`（UTF-16LE），BOM 优先级最高。
- BOM 与内容一致：置信度 0.99，即使内容是纯 ASCII（启发式无法区分）也以 BOM 为准。
- **BOM 与内容冲突**（BOM 声明的编码下内容存在非法序列）：仍按 BOM 优先判定
  （BOM 是写入方的显式声明，通常比字节统计更可信），但
  - `DetectionResult.isBomContentConflict()` 置为 `true`，BOM 候选置信度降为 0.60；
  - 启发式备选候选保留在结果中并压至 0.50 以下，供人工核对；
  - 解码时不会静默返回乱码：`CharsetTranscoder.decode` 会在第一个非法字节处抛出
    `MalformedContentException`（带字节偏移）。

## 无 BOM 启发式（要求 3）

- **UTF-8**：按 RFC 3629 严格校验，非法序列一票否决；含多字节字符时 0.90 起。
- **UTF-16BE/LE**：零字节位置（ASCII 文本）+ CJK 高字节区 `[0x4E,0x9F]` 位置判断大小端。
  因 ASCII 字母恰好落在 CJK 高字节区，CJK 证据需通过可信度校验：真实 UTF-16 CJK 单元的
  低字节近似均匀分布，而 ASCII 错位配对出的伪 CJK 单元低字节几乎全是可打印 ASCII。
- **GBK**：结构校验（首字节 0x81-0xFE，次字节 0x40-0x7E/0x80-0xFE）+ 常用汉字区
  （B0-F7 / A1-FE）占比加分；无常用汉字区字符时封顶 0.50。
- **ISO-8859-1**：任意字节合法，按高位字节分布评分，C1 控制区（0x80-0x9F）扣分。
- **交叉校准**：内容同时是合法 UTF-8（≥2 个多字节字符）时 GBK/Latin-1 压至 0.45；
  UTF-16 分布证据占比 > 0.6 时 GBK 压至 0.45。
- **判定规则**：最高置信度 ≥ 0.60 且领先第二名 ≥ 0.15 才判定为确定，否则检测失败。

## 检测失败（要求 4）

无法确定时（空输入、纯 ASCII、天然歧义短样本如 Latin-1 "àé" 恰好是合法 GBK 对），
`DetectionResult.isDetermined()` 为 `false`，`encoding()` 与 `decode()` 抛出
`UndeterminedEncodingException`，异常携带全部候选编码、置信度与原因，
绝不随便选一个返回乱码。

## 转换与错误定位（要求 5）

`CharsetTranscoder` 以 `REPORT` 严格模式解码；出错时
`MalformedContentException.getByteOffset()` 给出**非法序列起始字节**的偏移
（例如 UTF-8 三字节序列的第二个延续字节损坏时，偏移指向该序列第一个字节）。
解码自动剥离匹配的 BOM。

## 准确率（要求 6）

`AccuracyTest` 用固定随机种子生成 120 个样本（UTF-8 / UTF-16BE / UTF-16LE / GBK /
ISO-8859-1 各 24 个，含带/不带 BOM、中文、英文、法文重音字符、emoji 及混合内容），
当前结果 **120/120 = 100%**（报告在 `mvn verify` 时打印）。
纯 ASCII 样本不纳入统计：它在 UTF-8/GBK/ISO-8859-1 下字节完全一致，任何算法都无法区分。

## 流式检测（要求 7）

`detect(Path, maxBytes)` / `detect(InputStream, maxBytes)` 只读前 N 字节（默认 64 KiB），
内部多读 1 字节判断采样是否截断；截断时末尾不完整的多字节字符不计为非法。

**判定依据**：BOM 在最前若干字节即可命中；启发式所需证据（非法序列、双字节占比、
UTF-16 分布特征）在几千字节样本上已统计显著。

**局限**：
- 不支持 UTF-32（`FF FE 00 00` 会被当作 UTF-16LE）；
- 纯 ASCII 无法区分 UTF-8/GBK/ISO-8859-1（返回检测失败而非乱猜）；
- 极短样本可能天然歧义（如两个字节的 Latin-1 恰好是合法 GBK 对）；
- 采样结论取决于文件开头内容：若开头是大段纯 ASCII、特征字节出现在后文，
  应调大 `maxBytes`；
- UTF-16 启发式主要针对中英文内容，纯日文假名/韩文音节文本可能返回检测失败。

## 测试（要求 8）

43 个用例覆盖：四种编码识别（`FourEncodingDetectionTest`）、BOM 优先与冲突
（`BomPriorityTest`）、启发式候选与置信度（`HeuristicCandidateTest`）、检测失败
（`UndeterminedTest`）、转换错误字节偏移（`ConversionOffsetTest`）、准确率
（`AccuracyTest`）、流式检测（`StreamingDetectionTest`）。

```bash
./mvnw -q verify
```
