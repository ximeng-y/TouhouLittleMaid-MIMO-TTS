package com.xm2nd.tlmmimotts.util;

/**
 * 由 {@link TTSCallbackMixin} 注入到 TLM {@code TTSCallback} 的访问器：
 * 供 TTS 客户端读取聊天气泡文本（chatText），用于朗读文本被 LLM 翻译成
 * 其他语言时的中文兜底。
 * <p>
 * 若 Mixin 未应用（版本不匹配等），{@code instanceof} 判断为 false，自然降级。
 */
public interface ChatTextAccessor {
    /** 返回聊天气泡中显示的文本（LLM 回复的第一段） */
    String getChatText();
}
