package com.xm2nd.tlmmimotts.mixin;

import com.github.tartaricacid.touhoulittlemaid.ai.manager.setting.papi.PapiReplacer;
import com.github.tartaricacid.touhoulittlemaid.entity.passive.EntityMaid;
import com.xm2nd.tlmmimotts.ai.service.tts.mimo.MimoLanguageBridge;
import org.apache.commons.lang3.StringUtils;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Redirect;

/**
 * 修正 TLM 朗读文本语言占位符（{@code ${tts_language}}）的回退逻辑：
 * 女仆未显式设置 TTS 语言时，TLM 会回退到全局配置
 * {@code TTSLanguage = "en_us"}，导致 LLM 被要求把朗读文本翻译成英文
 * （聊天中文、语音英文）。
 * <p>
 * 修复：未显式设置时，朗读文本语言跟随最近一次聊天的游戏语言
 * （与 chat_language 一致），保证语音语言与对话语言相同。
 * 女仆显式设置了语言时仍尊重用户选择。
 */
@Mixin(PapiReplacer.class)
public abstract class PapiReplacerMixin {
    /**
     * valueMap 的填充在 {@code Util.make(..., map -> ...)} 的 lambda 中，
     * 编译后位于 {@code lambda$replaceSetting$0}（TLM 1.5.3 字节码已验证）。
     */
    @Redirect(method = "lambda$replaceSetting$0",
            at = @At(value = "INVOKE",
                    target = "Lcom/github/tartaricacid/touhoulittlemaid/ai/manager/setting/papi/PapiReplacer;getTtsLanguage(Lcom/github/tartaricacid/touhoulittlemaid/entity/passive/EntityMaid;)Ljava/lang/String;"))
    private static String tlmMimoTts$ttsLanguageFollowChatLanguage(EntityMaid maid) {
        // 女仆显式设置了 TTS 语言 → 尊重原逻辑
        if (StringUtils.isNotBlank(maid.getAiChatManager().ttsLanguage)) {
            return PapiReplacer.getTtsLanguage(maid);
        }
        // 未显式设置 → 跟随最近一次聊天的游戏语言，避免回退全局英文
        String chatLanguage = MimoLanguageBridge.get();
        if (StringUtils.isNotBlank(chatLanguage)) {
            return PapiReplacer.getChatLanguage(chatLanguage);
        }
        return PapiReplacer.getTtsLanguage(maid);
    }
}
