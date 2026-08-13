package com.xm2nd.tlmmimotts.mixin;

import com.github.tartaricacid.touhoulittlemaid.ai.manager.entity.TTSCallback;
import com.xm2nd.tlmmimotts.util.ChatTextAccessor;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;

/**
 * 为 TLM {@link TTSCallback} 注入 {@link ChatTextAccessor} 实现，
 * 暴露聊天气泡文本供 TTS 客户端在朗读文本被翻译成其他语言时兜底。
 * <p>
 * 对 TLM（Mojang 映射 mod）的方法注入一律 remap = false。
 */
@Mixin(value = TTSCallback.class, remap = false)
public abstract class TTSCallbackMixin implements ChatTextAccessor {
    @Shadow(remap = false)
    private String chatText;

    @Override
    public String getChatText() {
        return this.chatText;
    }
}
