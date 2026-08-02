package com.xm2nd.tlmmimotts.ai.service.tts.mimo;

/**
 * 服务端语言桥：记录最近一次聊天时客户端上报的游戏语言
 * （TLM {@code ChatClientInfo.language()}），供 PapiReplacer 注入使用，
 * 使 LLM 生成的朗读文本语言跟随游戏语言，而不是回退到全局默认英文。
 */
public final class MimoLanguageBridge {
    private static volatile String lastChatLanguage = "";

    private MimoLanguageBridge() {
    }

    public static void set(String language) {
        lastChatLanguage = language == null ? "" : language;
    }

    public static String get() {
        return lastChatLanguage;
    }

    /** 最近一次聊天语言是否以 zh 开头（简体中文等） */
    public static boolean isChinese() {
        return lastChatLanguage.startsWith("zh");
    }
}
