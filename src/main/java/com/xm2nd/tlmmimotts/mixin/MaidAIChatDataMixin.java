package com.xm2nd.tlmmimotts.mixin;

import com.github.tartaricacid.touhoulittlemaid.ai.manager.entity.MaidAIChatData;
import com.xm2nd.tlmmimotts.ai.service.tts.mimo.MimoLanguageBridge;
import org.apache.commons.lang3.StringUtils;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/**
 * 修正 TLM 朗读文本语言回退逻辑（聊天中文、语音英文的根源）：
 * <p>
 * TLM 的 {@code tts_language}（决定 LLM 生成朗读文本的语言）取女仆显式设置的
 * TTS 语言，未设置时回退全局配置 {@code TTSLanguage = "en_us"}，导致 LLM 被要求
 * 把朗读文本翻译成英文。
 * <p>
 * 修复：女仆未显式设置 TTS 语言时，{@code getTTSLanguage()} 返回最近一次聊天的
 * 游戏语言（与 chat_language 一致），使提示词模板判定走"相同语言"分支，
 * 朗读文本语言与对话语言一致；女仆显式设置了语言时仍尊重用户选择。
 * <p>
 * 注入点选择普通方法 {@code getTTSLanguage()}（不依赖编译器生成的 lambda 方法名），
 * 一次注入同时影响 PapiReplacer 的占位符与 SAME/DIFFERENT 模板判定、
 * 以及 {@code TTSConfig.language}（TTS 客户端兜底依据）。
 */
@Mixin(MaidAIChatData.class)
public abstract class MaidAIChatDataMixin {
    /** 女仆显式设置的 TTS 语言（继承自 MaidAIChatSerializable），空串表示未设置 */
    @Shadow
    public String ttsLanguage;

    @Inject(method = "getTTSLanguage", at = @At("RETURN"), cancellable = true)
    private void tlmMimoTts$fallbackTtsLanguageToChatLanguage(CallbackInfoReturnable<String> cir) {
        // 女仆显式设置了 TTS 语言 → 尊重用户选择，返回原值
        if (StringUtils.isNotBlank(this.ttsLanguage)) {
            return;
        }
        // 未显式设置 → 跟随最近一次聊天的游戏语言，避免回退全局英文 TTSLanguage
        String chatLanguage = MimoLanguageBridge.get();
        if (StringUtils.isNotBlank(chatLanguage)) {
            cir.setReturnValue(chatLanguage);
        }
    }
}
