package com.xm2nd.tlmmimotts.client.gui.layout;

import com.github.tartaricacid.touhoulittlemaid.ai.service.tts.TTSSite;
import com.github.tartaricacid.touhoulittlemaid.client.gui.entity.maid.ai.FormField;
import com.github.tartaricacid.touhoulittlemaid.client.gui.entity.maid.ai.Translations;
import com.github.tartaricacid.touhoulittlemaid.client.gui.entity.maid.ai.editor.TTSSiteEditorScreen;
import com.github.tartaricacid.touhoulittlemaid.client.gui.entity.maid.ai.layout.FieldDescriptor;
import com.github.tartaricacid.touhoulittlemaid.client.gui.entity.maid.ai.layout.TTSSiteFormLayout;
import com.github.tartaricacid.touhoulittlemaid.client.gui.widget.button.FlatColorButton;
import com.xm2nd.tlmmimotts.ai.service.tts.mimo.TTSMimoSite;
import com.xm2nd.tlmmimotts.client.gui.MimoCloneDescriptions;
import com.xm2nd.tlmmimotts.network.RefreshMimoClonesPacket;
import com.xm2nd.tlmmimotts.network.RequestMimoCloneDescriptionsPacket;
import com.xm2nd.tlmmimotts.network.SaveMimoCloneDescriptionPacket;
import com.xm2nd.tlmmimotts.server.MimoCloneSampleRepository;
import net.minecraft.ChatFormatting;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.client.gui.components.StringWidget;
import net.minecraft.network.chat.Component;
import net.neoforged.neoforge.network.PacketDistributor;
import org.apache.commons.lang3.StringUtils;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.function.Consumer;
import java.util.function.Function;

/**
 * MiMo TTS 站点编辑表单：仅提供 URL 与 API Key 两个字段，外加「刷新克隆音色」按钮。
 * <p>
 * 不开放通用音色行编辑，避免手工构造或保存任意文件路径；克隆音色以只读文件名 +
 * 可编辑描述（保存至服务端 {@code mimo-clone/descriptions/} 同名 txt）的行展示，
 * 描述随打开编辑页与保存操作即时同步。
 */
public class TTSMimoFormLayout extends TTSSiteFormLayout {
    private static final int REFRESH_BUTTON_HEIGHT = 20;
    private static final int REFRESH_BUTTON_MARGIN = 6;
    private static final int VOICE_ROW_HEIGHT = 22;
    private static final int MAX_CLONE_ROWS = 6;
    private static final int FILE_NAME_WIDTH = 88;
    private static final int SAVE_BUTTON_WIDTH = 44;
    private static final int MAX_DESCRIPTION_LENGTH = 500;

    /** 克隆音色 voiceId → 描述输入框 */
    private final Map<String, EditBox> descriptionBoxes = new LinkedHashMap<>();

    public TTSMimoFormLayout(TTSSite sourceSite) {
        super(sourceSite);
    }

    /** 供客户端描述同步桥接使用 */
    public String getSourceSiteId() {
        return this.sourceSite.id();
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

        // 克隆音色行（只读文件名 + 可编辑描述 + 保存）与摘要行
        int listHeight = this.renderCloneVoiceRows(x, y + REFRESH_BUTTON_HEIGHT + REFRESH_BUTTON_MARGIN, width, screen);

        // 注册为活动编辑页并请求最新描述
        MimoCloneDescriptions.setActiveLayout(this);
        PacketDistributor.sendToServer(new RequestMimoCloneDescriptionsPacket(this.sourceSite.id()));

        return REFRESH_BUTTON_HEIGHT + REFRESH_BUTTON_MARGIN + listHeight;
    }

    /** 渲染摘要行与克隆音色行（每行：文件名 + 描述输入框 + 保存按钮），返回占用高度 */
    private int renderCloneVoiceRows(int x, int y, int width, TTSSiteEditorScreen screen) {
        TTSMimoSite site = (TTSMimoSite) this.sourceSite;
        List<Map.Entry<String, String>> presets = new ArrayList<>();
        List<Map.Entry<String, String>> clones = new ArrayList<>();
        for (Map.Entry<String, String> entry : site.models().entrySet()) {
            if (entry.getKey().startsWith(MimoCloneSampleRepository.CLONE_PREFIX)) {
                clones.add(entry);
            } else {
                presets.add(entry);
            }
        }

        Font font = Minecraft.getInstance().font;
        int used = 0;

        // 摘要行：预置与克隆数量
        Component summary = Component.translatable("tlm_mimo_tts.gui.voice_summary", presets.size(), clones.size())
                .withStyle(ChatFormatting.GRAY);
        screen.addRenderableWidget(new StringWidget(x, y + used, width, 10, summary, font));
        used += 12;

        if (clones.isEmpty()) {
            screen.addRenderableWidget(new StringWidget(x, y + used, width, 10,
                    Component.translatable("tlm_mimo_tts.gui.no_clone_voices").withStyle(ChatFormatting.GRAY), font));
            return used + 10;
        }

        // 已缓存的描述（服务端同步包到达前先展示旧值）
        Map<String, String> cached = MimoCloneDescriptions.getCached(site.id());
        this.descriptionBoxes.clear();
        int shown = Math.min(clones.size(), MAX_CLONE_ROWS);
        for (int i = 0; i < shown; i++) {
            Map.Entry<String, String> entry = clones.get(i);
            String voiceId = entry.getKey();
            String fileName = entry.getValue();

            // 文件名（只读）
            screen.addRenderableWidget(new StringWidget(x, y + used + 2, FILE_NAME_WIDTH, 10,
                    Component.literal(fileName), font));

            // 描述输入框
            int descX = x + FILE_NAME_WIDTH + 6;
            int descWidth = width - FILE_NAME_WIDTH - 6 - SAVE_BUTTON_WIDTH - 6;
            EditBox descBox = new EditBox(font, descX, y + used, descWidth, 16,
                    Component.translatable("tlm_mimo_tts.gui.description_input"));
            descBox.setMaxLength(MAX_DESCRIPTION_LENGTH);
            descBox.setBordered(false);
            descBox.setValue(cached.getOrDefault(voiceId, ""));
            this.descriptionBoxes.put(voiceId, descBox);
            screen.addRenderableWidget(descBox);

            // 保存按钮
            int saveX = descX + descWidth + 6;
            screen.addRenderableWidget(new FlatColorButton(saveX, y + used - 2, SAVE_BUTTON_WIDTH, 18,
                    Component.translatable("selectWorld.edit.save"),
                    button -> saveDescription(voiceId)));

            used += VOICE_ROW_HEIGHT;
        }
        if (clones.size() > shown) {
            screen.addRenderableWidget(new StringWidget(x, y + used, width, 10,
                    Component.literal("… 等 " + clones.size() + " 个克隆音色").withStyle(ChatFormatting.GRAY), font));
            used += 10;
        }
        return used;
    }

    /** 保存指定克隆音色的描述到服务端 */
    private void saveDescription(String voiceId) {
        EditBox box = this.descriptionBoxes.get(voiceId);
        if (box == null) {
            return;
        }
        PacketDistributor.sendToServer(
                new SaveMimoCloneDescriptionPacket(this.sourceSite.id(), voiceId, box.getValue()));
    }

    /** 同步包到达时即时填入描述输入框 */
    public void applyDescriptions(Map<String, String> descriptions) {
        descriptions.forEach((voiceId, description) -> {
            EditBox box = this.descriptionBoxes.get(voiceId);
            if (box != null) {
                box.setValue(description);
            }
        });
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
