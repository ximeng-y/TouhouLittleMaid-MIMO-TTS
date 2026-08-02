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
    private static final int REFRESH_BUTTON_WIDTH = 130;
    private static final int REFRESH_BUTTON_HEIGHT = 20;
    private static final int REFRESH_BUTTON_MARGIN = 6;
    private static final int VOICE_ROW_HEIGHT = 22;
    private static final int FILE_NAME_WIDTH = 88;
    private static final int SAVE_BUTTON_WIDTH = 44;
    private static final int MAX_DESCRIPTION_LENGTH = 500;
    /** 摘要行的固定占用高度 */
    private static final int SUMMARY_HEIGHT = 12;
    /** 翻页按钮宽度与页码文字宽度 */
    private static final int PAGE_BUTTON_WIDTH = 50;
    private static final int PAGE_LABEL_WIDTH = 36;

    /** 克隆音色 voiceId → 描述输入框 */
    private final Map<String, EditBox> descriptionBoxes = new LinkedHashMap<>();
    /** 克隆音色列表分页状态：当前页索引与总页数（渲染时计算） */
    private int pageIndex = 0;
    private int pageCount = 1;
    /** 翻页重建时保留的未保存描述（voiceId → 输入值），本地输入优先于服务端缓存 */
    private final Map<String, String> pendingValues = new LinkedHashMap<>();

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
        // 重建前保留上一页输入框中的未保存内容（screen.init() 会清空并重建全部组件）
        this.descriptionBoxes.forEach((voiceId, box) -> this.pendingValues.put(voiceId, box.getValue()));

        FlatColorButton refreshButton = new FlatColorButton(x, y, REFRESH_BUTTON_WIDTH, REFRESH_BUTTON_HEIGHT,
                Component.translatable("tlm_mimo_tts.gui.refresh_clone_voices"),
                button -> PacketDistributor.sendToServer(new RefreshMimoClonesPacket(this.sourceSite.id())));
        refreshButton.setTooltips("tlm_mimo_tts.gui.clone_dir_tooltip");
        screen.addRenderableWidget(refreshButton);

        // 克隆音色行（只读文件名 + 可编辑描述 + 保存）与摘要行
        int listHeight = this.renderCloneVoiceRows(x, y + REFRESH_BUTTON_HEIGHT + REFRESH_BUTTON_MARGIN, width, screen);

        // 翻页按钮：与刷新按钮同排，不占用列表区高度；仅当克隆音色超过一页时显示
        this.addPageButtons(x, y, screen);

        // 注册为活动编辑页并请求最新描述
        MimoCloneDescriptions.setActiveLayout(this);
        PacketDistributor.sendToServer(new RequestMimoCloneDescriptionsPacket(this.sourceSite.id()));

        return REFRESH_BUTTON_HEIGHT + REFRESH_BUTTON_MARGIN + listHeight;
    }

    /** 翻页按钮（上一页 / 页码 / 下一页），点击后重建编辑页 */
    private void addPageButtons(int x, int y, TTSSiteEditorScreen screen) {
        if (this.pageCount <= 1) {
            return;
        }
        Font font = Minecraft.getInstance().font;
        int pageX = x + REFRESH_BUTTON_WIDTH + 6;

        FlatColorButton prev = new FlatColorButton(pageX, y, PAGE_BUTTON_WIDTH, REFRESH_BUTTON_HEIGHT,
                Component.translatable("tlm_mimo_tts.gui.clone_page_prev"),
                button -> {
                    this.pageIndex = Math.max(0, this.pageIndex - 1);
                    rebuildScreen(screen);
                });
        prev.active = this.pageIndex > 0;
        screen.addRenderableWidget(prev);

        screen.addRenderableWidget(new StringWidget(pageX + PAGE_BUTTON_WIDTH + 4, y + 5, PAGE_LABEL_WIDTH, 10,
                Component.literal((this.pageIndex + 1) + "/" + this.pageCount), font));

        FlatColorButton next = new FlatColorButton(pageX + PAGE_BUTTON_WIDTH + 4 + PAGE_LABEL_WIDTH + 4, y,
                PAGE_BUTTON_WIDTH, REFRESH_BUTTON_HEIGHT,
                Component.translatable("tlm_mimo_tts.gui.clone_page_next"),
                button -> {
                    this.pageIndex = Math.min(this.pageCount - 1, this.pageIndex + 1);
                    rebuildScreen(screen);
                });
        next.active = this.pageIndex < this.pageCount - 1;
        screen.addRenderableWidget(next);
    }

    /**
     * 重建编辑页。{@link TTSSiteEditorScreen#init()} 为 protected，跨包无法直接调用；
     * {@link net.minecraft.client.gui.screens.Screen#resize} 是公开方法且内部会调用 init()，
     * 传入当前宽高即等价于重建（尺寸不变，无缩放副作用）。
     */
    private static void rebuildScreen(TTSSiteEditorScreen screen) {
        screen.resize(Minecraft.getInstance(), screen.width, screen.height);
    }

    /** 渲染摘要行与当前页的克隆音色行（每行：文件名 + 描述输入框 + 保存按钮），返回占用高度 */
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
        used += SUMMARY_HEIGHT;

        if (clones.isEmpty()) {
            screen.addRenderableWidget(new StringWidget(x, y + used, width, 10,
                    Component.translatable("tlm_mimo_tts.gui.no_clone_voices").withStyle(ChatFormatting.GRAY), font));
            return used + 10;
        }

        // 底部保存/取消按钮区（TTSSiteEditorScreen 中 bottomY = startY + BASE_HEIGHT - 24，
        // 且 startY = (窗口高 - 230) / 2，故 bottomY = 窗口高 / 2 + 91）。
        // 列表区不得越过按钮区（下方留 8px 安全边距）；每页行数按可用高度折算，
        // 超出一页的部分通过翻页按钮访问（见 addPageButtons）。
        int bottomY = screen.height / 2 + 91;
        int available = bottomY - y - 8;
        int perPage = Math.max(1, (available - SUMMARY_HEIGHT) / VOICE_ROW_HEIGHT);
        this.pageCount = Math.max(1, (clones.size() + perPage - 1) / perPage);
        if (this.pageIndex >= this.pageCount) {
            this.pageIndex = this.pageCount - 1;
        }

        // 已缓存的描述（服务端同步包到达前先展示旧值）；本地未完成的输入优先
        Map<String, String> cached = MimoCloneDescriptions.getCached(site.id());
        this.descriptionBoxes.clear();
        int start = this.pageIndex * perPage;
        int end = Math.min(clones.size(), start + perPage);
        for (int i = start; i < end; i++) {
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
            descBox.setValue(this.pendingValues.getOrDefault(voiceId, cached.getOrDefault(voiceId, "")));
            this.descriptionBoxes.put(voiceId, descBox);
            screen.addRenderableWidget(descBox);

            // 保存按钮
            int saveX = descX + descWidth + 6;
            screen.addRenderableWidget(new FlatColorButton(saveX, y + used - 2, SAVE_BUTTON_WIDTH, 18,
                    Component.translatable("selectWorld.edit.save"),
                    button -> saveDescription(voiceId)));

            used += VOICE_ROW_HEIGHT;
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
