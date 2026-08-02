package com.xm2nd.tlmmimotts.mixin;

import com.github.tartaricacid.touhoulittlemaid.ai.manager.entity.ChatClientInfo;
import com.github.tartaricacid.touhoulittlemaid.ai.manager.entity.MaidAIChatManager;
import com.github.tartaricacid.touhoulittlemaid.ai.service.llm.LLMSite;
import com.xm2nd.tlmmimotts.ai.service.tts.mimo.MimoLanguageBridge;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * 捕获每次聊天时客户端上报的游戏语言（TLM 的 chat_language），
 * 存入 {@link MimoLanguageBridge}，供 PapiReplacer 将朗读文本语言
 * 与聊天语言对齐（TLM 默认在女仆未设置语言时回退全局英文）。
 */
@Mixin(MaidAIChatManager.class)
public abstract class MaidAIChatManagerMixin {
    @Inject(method = "tryToChat(Ljava/lang/String;Lcom/github/tartaricacid/touhoulittlemaid/ai/manager/entity/ChatClientInfo;Lcom/github/tartaricacid/touhoulittlemaid/ai/service/llm/LLMSite;)V",
            at = @At("HEAD"))
    private void tlmMimoTts$captureChatLanguage(String message, ChatClientInfo clientInfo, LLMSite site,
                                                CallbackInfo ci) {
        MimoLanguageBridge.set(clientInfo.language());
    }
}
