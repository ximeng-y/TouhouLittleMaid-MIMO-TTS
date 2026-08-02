package com.xm2nd.tlmmimotts.client.gui.layout;

import com.github.tartaricacid.touhoulittlemaid.ai.service.tts.TTSSite;
import com.github.tartaricacid.touhoulittlemaid.client.gui.entity.maid.ai.FormField;
import com.github.tartaricacid.touhoulittlemaid.client.gui.entity.maid.ai.Translations;
import com.github.tartaricacid.touhoulittlemaid.client.gui.entity.maid.ai.editor.TTSSiteEditorScreen;
import com.github.tartaricacid.touhoulittlemaid.client.gui.entity.maid.ai.layout.FieldDescriptor;
import com.github.tartaricacid.touhoulittlemaid.client.gui.entity.maid.ai.layout.TTSSiteFormLayout;
import com.github.tartaricacid.touhoulittlemaid.client.gui.widget.button.FlatColorButton;
import com.xm2nd.tlmmimotts.ai.service.tts.mimo.TTSMimoSite;
import com.xm2nd.tlmmimotts.network.RefreshMimoClonesPacket;
import net.minecraft.ChatFormatting;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.components.StringWidget;
import net.minecraft.network.chat.Component;
import net.neoforged.neoforge.network.PacketDistributor;
import org.apache.commons.lang3.StringUtils;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.function.Consumer;
import java.util.function.Function;

/**
 * MiMo TTS 站点编辑表单：仅提供 URL 与 API Key 两个字段，外加「刷新克隆音色」按钮。
 * <p>
 * 不开放通用音色行编辑，避免手工构造或保存任意文件路径；音色列表由服务端管理
 * （预置音色固定、克隆音色来自固定目录扫描结果），编辑页以只读列表展示，
 * 刷新克隆音色后重新进入编辑页即可看到最新音色。
 */
public class TTSMimoFormLayout extends TTSSiteFormLayout {
    private static final int REFRESH_BUTTON_HEIGHT = 20;
    private static final int REFRESH_BUTTON_MARGIN = 6;
    private static final int VOICE_ROW_HEIGHT = 10;
    private static final int MAX_VOICE_ROWS = 10;

    public TTSMimoFormLayout(TTSSite sourceSite) {
        super(sourceSite);
    }

    @Override
    public List<FieldDescriptor> getFieldDescriptors() {
        TTSMimoSite site = (TTSMimoSite) this.sourceSite;
        return List.of(
                new FieldDescriptor(FormField.URL, site.url(), true, false),
                new FieldDescriptor(FormField.SECRET_KEY, site.secretKey(), true, true)
        );
    }

    @Override
    public boolean supportsModelRows() {
        return false;
    }

    @Override
    public int extraInit(int x, int y, int width, TTSSiteEditorScreen screen) {
        FlatColorButton refreshButton = new FlatColorButton(x, y, 130, REFRESH_BUTTON_HEIGHT,
                Component.translatable("tlm_mimo_tts.gui.refresh_clone_voices"),
                button -> PacketDistributor.sendToServer(new RefreshMimoClonesPacket(this.sourceSite.id())));
        refreshButton.setTooltips("tlm_mimo_tts.gui.clone_dir_tooltip");
        screen.addRenderableWidget(refreshButton);

        // 只读音色列表：预置 + 克隆统一展示（克隆显示完整文件名），不开放行编辑
        int listHeight = this.renderReadOnlyVoiceList(x, y + REFRESH_BUTTON_HEIGHT + REFRESH_BUTTON_MARGIN, width, screen);
        return REFRESH_BUTTON_HEIGHT + REFRESH_BUTTON_MARGIN + listHeight;
    }

    /** 渲染只读音色列表（每行：显示名 + 灰色音色 ID），返回占用高度 */
    private int renderReadOnlyVoiceList(int x, int y, int width, TTSSiteEditorScreen screen) {
        TTSMimoSite site = (TTSMimoSite) this.sourceSite;
        List<Map.Entry<String, String>> voices = new ArrayList<>(site.models().entrySet());
        if (voices.isEmpty()) {
            return 0;
        }
        Font font = Minecraft.getInstance().font;
        int shown = Math.min(voices.size(), MAX_VOICE_ROWS);
        int used = 0;
        for (int i = 0; i < shown; i++) {
            Map.Entry<String, String> entry = voices.get(i);
            Component line = Component.literal(entry.getValue())
                    .append("  ")
                    .append(Component.literal(entry.getKey()).withStyle(ChatFormatting.GRAY));
            screen.addRenderableWidget(new StringWidget(x, y + used, width, VOICE_ROW_HEIGHT, line, font));
            used += VOICE_ROW_HEIGHT;
        }
        if (voices.size() > shown) {
            screen.addRenderableWidget(new StringWidget(x, y + used, width, VOICE_ROW_HEIGHT,
                    Component.literal("… 等 " + voices.size() + " 个音色").withStyle(ChatFormatting.GRAY), font));
            used += VOICE_ROW_HEIGHT;
        }
        return used;
    }

    @Override
    public @Nullable TTSSite buildSite(Function<String, String> fieldValues, Map<String, String> models,
                                       Consumer<Component> showStatus) {
        TTSMimoSite site = (TTSMimoSite) this.sourceSite;
        String url = fieldValues.apply(FormField.URL);
        if (StringUtils.isBlank(url)) {
            showStatus.accept(Translations.URL_IS_EMPTY);
            return null;
        }
        String secretKey = fieldValues.apply(FormField.SECRET_KEY);
        if (StringUtils.isBlank(secretKey)) {
            showStatus.accept(Translations.SECRET_KEY_IS_EMPTY);
            return null;
        }
        if (site.models().isEmpty()) {
            showStatus.accept(Translations.VOICE_IS_EMPTY);
            return null;
        }
        // 保留原音色列表（预置音色 + 服务端管理的克隆音色元数据），不做行级编辑
        return new TTSMimoSite(site.id(), site.icon(), url, site.enabled(), secretKey, site.headers(), site.models());
    }
}
