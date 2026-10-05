package com.example.gsb.charset;

/** 各语言样本文本，供多个测试类共享。 */
final class SampleTexts {

    private SampleTexts() {
    }

    static final String ZH_NEWS =
            "中华人民共和国成立于一九四九年。中国人民从此站起来了，古老的民族开始了新的征程。";
    static final String ZH_POEM =
            "春眠不觉晓，处处闻啼鸟。夜来风雨声，花落知多少。";
    static final String ZH_MIXED =
            "2026年10月5日，天气晴朗，温度25°C，适合出游。";
    static final String ZH_JAVA =
            "Java 是一种广泛使用的编程语言，目前的长期支持版本包括 17 和 21。";
    static final String JA =
            "これは日本語のテキストです。私は東京に住んでいます。桜が咲きました。";
    static final String RU =
            "Привет, мир! Это проверка кодировки текста. Добрый день.";
    static final String FR =
            "Café crème brûlée : très délicieux, à déguster avec plaisir.";
    static final String DE =
            "Grüße aus München: ä ö ü, Ä Ö Ü und ß.";
    static final String EMOJI =
            "测试表情符号 🎉🚀🌟 混合内容 ✅";
    static final String CURRENCY =
            "价格分别是 ¥100、€90 和 £80，请注意汇率变化。";
    static final String KO =
            "한국어 텍스트입니다. 안녕하세요, 반갑습니다.";
    static final String AR =
            "مرحبا بالعالم، هذا اختبار لترميز النص.";
    static final String EN =
            "Hello world, this is a plain English sentence for charset detection.";

    /** ISO-8859-1 可表示的拉丁语系文本（重音字符后跟随空格/标点）。 */
    static final String[] LATIN1_TEXTS = {
            "Le café est très bon, vraiment délicieux.",
            "A bientôt, mon ami, et à demain soir.",
            "Grüße aus München, mit ä ö ü und ß.",
            "El señor está aquí, niño pequeño.",
            "Noël approche, joyeux réveillon à tous."
    };

    static final String[] UNICODE_TEXTS = {
            ZH_NEWS, ZH_POEM, JA, RU, FR, DE, EMOJI, CURRENCY, KO, AR
    };
}
