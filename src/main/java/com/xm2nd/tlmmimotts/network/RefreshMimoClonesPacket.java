package com.xm2nd.tlmmimotts.network;

import com.github.tartaricacid.touhoulittlemaid.ai.manager.site.AvailableSites;
import com.github.tartaricacid.touhoulittlemaid.ai.service.tts.TTSSite;
import com.github.tartaricacid.touhoulittlemaid.network.NetworkHandler;
import com.github.tartaricacid.touhoulittlemaid.network.message.ai.SyncAISitesPacket;
import com.github.tartaricacid.touhoulittlemaid.util.GameModeUtil;
import com.xm2nd.tlmmimotts.TlmMimoTts;
import com.xm2nd.tlmmimotts.ai.service.tts.mimo.TTSMimoSite;
import com.xm2nd.tlmmimotts.server.MimoCloneSampleRepository;
import io.netty.buffer.ByteBuf;
import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.neoforged.neoforge.network.handling.IPayloadContext;
import org.apache.commons.lang3.StringUtils;
import org.jetbrains.annotations.Nullable;

/**
 * 客户端 → 服务端「刷新克隆音色」请求，仅传递站点 ID。
 * <p>
 * 服务端先复用 TLM 的站点编辑权限校验，再扫描固定目录、重建 MiMo 克隆项、
 * 写回 tts.json 的音色文件名元数据，最后复用 TLM 站点同步结果刷新当前 UI。
 * 客户端永远不会上传、下载或读取参考音频。
 */
public record RefreshMimoClonesPacket(String siteId) implements CustomPacketPayload {
    public static final CustomPacketPayload.Type<RefreshMimoClonesPacket> TYPE =
            new CustomPacketPayload.Type<>(ResourceLocation.fromNamespaceAndPath(TlmMimoTts.MOD_ID, "refresh_mimo_clones"));
    public static final StreamCodec<ByteBuf, RefreshMimoClonesPacket> STREAM_CODEC = StreamCodec.composite(
            ByteBufCodecs.STRING_UTF8,
            RefreshMimoClonesPacket::siteId,
            RefreshMimoClonesPacket::new
    );

    @Override
    public CustomPacketPayload.Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }

    public static void handle(RefreshMimoClonesPacket message, IPayloadContext context) {
        if (context.flow().isServerbound()) {
            context.enqueueWork(() -> onHandle(message, (ServerPlayer) context.player()));
        }
    }

    private static void onHandle(RefreshMimoClonesPacket message, @Nullable ServerPlayer player) {
        // 复用 TLM 的站点编辑权限校验（单机/局域网房主/OP2）
        if (!GameModeUtil.canEditSite(player)) {
            return;
        }
        String siteId = StringUtils.trimToNull(message.siteId());
        if (siteId == null) {
            return;
        }
        TTSSite site = AvailableSites.getTTSSite(siteId);
        if (!(site instanceof TTSMimoSite mimoSite)) {
            return;
        }
        try {
            int count = mimoSite.refreshCloneVoices(MimoCloneSampleRepository.defaultInstance());
            AvailableSites.saveSites();
            // 复用 TLM 站点同步结果刷新当前 UI
            NetworkHandler.sendToClientPlayer(
                    new SyncAISitesPacket(AvailableSites.LLM_SITES, AvailableSites.TTS_SITES, false), player);
            if (player != null) {
                player.sendSystemMessage(Component.translatable("tlm_mimo_tts.message.refresh_ok", count));
            }
        } catch (Exception e) {
            TlmMimoTts.LOGGER.error("MiMo 克隆音色刷新失败", e);
            if (player != null) {
                player.sendSystemMessage(Component.translatable("tlm_mimo_tts.message.refresh_failed", e.getLocalizedMessage())
                        .withStyle(ChatFormatting.RED));
            }
        }
    }
}
