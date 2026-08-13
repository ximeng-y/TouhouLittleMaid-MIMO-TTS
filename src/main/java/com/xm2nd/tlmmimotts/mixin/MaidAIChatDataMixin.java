package com.xm2nd.tlmmimotts.mixin;

import com.github.tartaricacid.touhoulittlemaid.ai.manager.entity.MaidAIChatData;
import com.xm2nd.tlmmimotts.ai.service.tts.mimo.MimoLanguageBridge;
import net.minecraftforge.common.ForgeConfigSpec;
import org.apache.commons.lang3.StringUtils;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Redirect;

/**
 * 修正 TLM 朗读文本语言回退逻辑（聊天中文、语音英文的根源）：
 * <p>
 * {@link MaidAIChatData#getTTSLanguage()} 在女仆未显式设置 TTS 语言时回退全局配置
 * {@code AIConfig.TTSLanguage = "en_us"}，使 LLM 被要求把朗读文本翻译成英文。
 * <p>
 * 修复：重定向"全局回退"分支的 {@code AIConfig.TTS_LANGUAGE.get()} 调用，
 * 改为返回最近一次聊天的游戏语言（与 chat_language 一致）；女仆显式设置了
 * 语言时走原分支，尊重用户选择。
 * <p>
 * 注入点为普通方法 {@code getTTSLanguage()} 内的唯一全局回退调用
 * （字节码：{@code ForgeConfigSpec$ConfigValue.get()Ljava/lang/Object;}），
 * 不依赖编译器生成的 lambda 方法名，也不 @Shadow 任何字段。
 * <p>
 * 对 TLM（Mojang 映射 mod）的方法注入一律 remap = false。
 */
@Mixin(value = MaidAIChatData.class, remap = false)
public abstract class MaidAIChatDataMixin {
    @Redirect(method = "getTTSLanguage", remap = false,
            at = @At(value = "INVOKE",
                    target = "Lnet/minecraftforge/common/ForgeConfigSpec$ConfigValue;get()Ljava/lang/Object;"))
    private Object tlmMimoTts$useChatLanguageAsGlobalTtsLanguage(ForgeConfigSpec.ConfigValue<?> configValue) {
        // 女仆未显式设置 TTS 语言（走全局回退分支）时，跟随最近一次聊天的游戏语言
        String chatLanguage = MimoLanguageBridge.get();
        if (StringUtils.isNotBlank(chatLanguage)) {
            return chatLanguage;
        }
        return configValue.get();
    }
}
