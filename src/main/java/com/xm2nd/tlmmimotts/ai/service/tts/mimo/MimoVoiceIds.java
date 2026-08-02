package com.xm2nd.tlmmimotts.ai.service.tts.mimo;

/**
 * MiMo 音色 ID 约定：
 * <ul>
 *   <li>{@code preset:<voiceId>}：内置 MiMo 预置音色，显示为音色名称</li>
 *   <li>{@code clone:<filename>}：服务端固定目录中的参考音频，显示为完整文件名</li>
 * </ul>
 * 用户在女仆 AI 聊天页只需选择音色；服务端根据前缀自动选择
 * {@code mimo-v2.5-tts} 或 {@code mimo-v2.5-tts-voiceclone}。
 */
public final class MimoVoiceIds {
    public static final String PRESET_PREFIX = "preset:";
    public static final String CLONE_PREFIX = "clone:";

    private MimoVoiceIds() {
    }

    public static boolean isPreset(String voiceId) {
        return voiceId != null && voiceId.startsWith(PRESET_PREFIX);
    }

    public static boolean isClone(String voiceId) {
        return voiceId != null && voiceId.startsWith(CLONE_PREFIX);
    }

    public static String presetId(String voiceId) {
        return voiceId.substring(PRESET_PREFIX.length());
    }

    public static String cloneFileName(String voiceId) {
        return voiceId.substring(CLONE_PREFIX.length());
    }
}
