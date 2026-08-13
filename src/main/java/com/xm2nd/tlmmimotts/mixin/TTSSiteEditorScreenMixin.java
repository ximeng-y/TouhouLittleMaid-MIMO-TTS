package com.xm2nd.tlmmimotts.mixin;

import com.github.tartaricacid.touhoulittlemaid.client.gui.entity.maid.ai.FormField;
import com.github.tartaricacid.touhoulittlemaid.client.gui.entity.maid.ai.editor.TTSSiteEditorScreen;
import com.github.tartaricacid.touhoulittlemaid.client.gui.entity.maid.ai.layout.TTSSiteFormLayout;
import com.xm2nd.tlmmimotts.client.gui.layout.TTSMimoFormLayout;
import net.minecraft.network.chat.Style;
import net.minecraft.util.FormattedCharSequence;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * 客户端专用 Mixin：仅对 MiMo 站点编辑页，把 API Key 掩码字符替换为
 * 密集的实心圆点，并限制最大掩码长度，避免掩码串间隔过大或溢出输入框。
 * <p>
 * 对 TLM（Mojang 映射 mod）的方法注入一律 remap = false。
 */
@Mixin(value = TTSSiteEditorScreen.class, remap = false)
public abstract class TTSSiteEditorScreenMixin {
    /** 掩码字符：全角实心圆点（比 TLM 默认窄中点 · 的视觉间隔小） */
    private static final String MASK_CHAR = "●";
    /** 最大掩码长度，超过则截断并追加省略号，避免溢出输入框 */
    private static final int MAX_MASKED_LENGTH = 20;

    @Shadow(remap = false)
    @Final
    private TTSSiteFormLayout layout;

    @Inject(method = "createFieldWidget", at = @At("TAIL"), remap = false)
    private void tlm_mimo_tts$compactSecretMask(FormField field, int left, int y, int width, CallbackInfo ci) {
        if (field.box != null && field.secret && this.layout instanceof TTSMimoFormLayout) {
            field.box.setFormatter((text, pos) -> {
                String masked = MASK_CHAR.repeat(Math.min(text.length(), MAX_MASKED_LENGTH));
                if (text.length() > MAX_MASKED_LENGTH) {
                    masked += "…";
                }
                return FormattedCharSequence.forward(masked, Style.EMPTY);
            });
        }
    }
}
