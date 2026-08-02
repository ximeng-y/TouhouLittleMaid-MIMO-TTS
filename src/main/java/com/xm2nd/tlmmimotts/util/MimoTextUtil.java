package com.xm2nd.tlmmimotts.util;

/**
 * 文本语言辅助工具。
 * <p>
 * 用于识别 LLM 返回的朗读文本是否被翻译成了英文（无汉字），
 * 从而在中文语音意图下兜底改用中文聊天文本朗读。
 */
public final class MimoTextUtil {
    private MimoTextUtil() {
    }

    /** 文本中是否包含汉字（CJK 统一表意文字） */
    public static boolean containsHan(String text) {
        if (text == null || text.isEmpty()) {
            return false;
        }
        for (int i = 0; i < text.length(); ) {
            int codePoint = text.codePointAt(i);
            if (Character.UnicodeScript.of(codePoint) == Character.UnicodeScript.HAN) {
                return true;
            }
            i += Character.charCount(codePoint);
        }
        return false;
    }
}
