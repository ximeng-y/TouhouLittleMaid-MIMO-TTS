package com.xm2nd.tlmmimotts.network;

import com.github.tartaricacid.touhoulittlemaid.ai.manager.site.AvailableSites;
import com.github.tartaricacid.touhoulittlemaid.ai.service.tts.TTSSite;
import com.github.tartaricacid.touhoulittlemaid.network.NetworkHandler;
import com.github.tartaricacid.touhoulittlemaid.util.GameModeUtil;
import com.xm2nd.tlmmimotts.TlmMimoTts;
import com.xm2nd.tlmmimotts.ai.service.tts.mimo.TTSMimoSite;
import com.xm2nd.tlmmimotts.server.MimoCloneSampleRepository;
import net.minecraft.ChatFormatting;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraftforge.network.NetworkEvent;
import org.apache.commons.lang3.StringUtils;
import org.jetbrains.annotations.Nullable;

import java.util.Map;
import java.util.function.Supplier;

/**
 * 客户端 → 服务端：保存某个克隆音色的描述。
 * 服务端复用 TLM 站点编辑权限校验，写入固定根目录下描述文件夹中的同名 txt，
 * 然后回发 {@link SyncMimoCloneDescriptionsPacket} 刷新客户端缓存。
 */
public class SaveMimoCloneDescriptionPacket {
    private final String siteId;
    private final String voiceId;
    private final String description;

    public SaveMimoCloneDescriptionPacket(String siteId, String voiceId, String description) {
        this.siteId = siteId;
        this.voiceId = voiceId;
        this.description = description;
    }

    public String siteId() {
        return siteId;
    }

    public String voiceId() {
        return voiceId;
    }

    public String description() {
        return description;
    }

    public static void encode(SaveMimoCloneDescriptionPacket message, FriendlyByteBuf buf) {
        buf.writeUtf(message.siteId());
        buf.writeUtf(message.voiceId());
        buf.writeUtf(message.description());
    }

    public static SaveMimoCloneDescriptionPacket decode(FriendlyByteBuf buf) {
        return new SaveMimoCloneDescriptionPacket(buf.readUtf(), buf.readUtf(), buf.readUtf());
    }

    public static void handle(SaveMimoCloneDescriptionPacket message, Supplier<NetworkEvent.Context> contextSupplier) {
        NetworkEvent.Context context = contextSupplier.get();
        context.enqueueWork(() -> onHandle(message, context.getSender()));
        context.setPacketHandled(true);
    }

    private static void onHandle(SaveMimoCloneDescriptionPacket message, @Nullable ServerPlayer player) {
        if (!GameModeUtil.canEditSite(player)) {
            return;
        }
        String siteId = StringUtils.trimToNull(message.siteId());
        TTSSite site = siteId == null ? null : AvailableSites.getTTSSite(siteId);
        if (!(site instanceof TTSMimoSite)) {
            return;
        }
        MimoCloneSampleRepository repository = MimoCloneSampleRepository.defaultInstance();
        try {
            repository.saveDescription(message.voiceId(), message.description());
            Map<String, String> descriptions = repository.readAllDescriptions();
            NetworkHandler.sendToClientPlayer(
                    new SyncMimoCloneDescriptionsPacket(message.siteId(), descriptions), player);
            if (player != null) {
                player.sendSystemMessage(Component.translatable(
                        "tlm_mimo_tts.message.description_saved", message.voiceId()));
            }
        } catch (Exception e) {
            TlmMimoTts.LOGGER.error("MiMo 克隆音色描述保存失败", e);
            if (player != null) {
                player.sendSystemMessage(Component.translatable(
                                "tlm_mimo_tts.message.description_failed", e.getLocalizedMessage())
                        .withStyle(ChatFormatting.RED));
            }
        }
    }
}
