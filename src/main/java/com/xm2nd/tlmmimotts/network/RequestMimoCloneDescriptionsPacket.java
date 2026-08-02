package com.xm2nd.tlmmimotts.network;

import com.github.tartaricacid.touhoulittlemaid.ai.manager.site.AvailableSites;
import com.github.tartaricacid.touhoulittlemaid.ai.service.tts.TTSSite;
import com.github.tartaricacid.touhoulittlemaid.network.NetworkHandler;
import com.github.tartaricacid.touhoulittlemaid.util.GameModeUtil;
import com.xm2nd.tlmmimotts.TlmMimoTts;
import com.xm2nd.tlmmimotts.ai.service.tts.mimo.TTSMimoSite;
import com.xm2nd.tlmmimotts.server.MimoCloneSampleRepository;
import io.netty.buffer.ByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.neoforged.neoforge.network.handling.IPayloadContext;
import org.apache.commons.lang3.StringUtils;
import org.jetbrains.annotations.Nullable;

import java.util.Map;

/**
 * 客户端 → 服务端：请求某个 MiMo 站点的全部克隆音色描述。
 * 打开站点编辑页时发送；服务端复用 TLM 站点编辑权限校验后回发
 * {@link SyncMimoCloneDescriptionsPacket}。
 */
public record RequestMimoCloneDescriptionsPacket(String siteId) implements CustomPacketPayload {
    public static final CustomPacketPayload.Type<RequestMimoCloneDescriptionsPacket> TYPE =
            new CustomPacketPayload.Type<>(ResourceLocation.fromNamespaceAndPath(TlmMimoTts.MOD_ID, "request_mimo_clone_descriptions"));
    public static final StreamCodec<ByteBuf, RequestMimoCloneDescriptionsPacket> STREAM_CODEC = StreamCodec.composite(
            ByteBufCodecs.STRING_UTF8,
            RequestMimoCloneDescriptionsPacket::siteId,
            RequestMimoCloneDescriptionsPacket::new
    );

    @Override
    public CustomPacketPayload.Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }

    public static void handle(RequestMimoCloneDescriptionsPacket message, IPayloadContext context) {
        if (context.flow().isServerbound()) {
            context.enqueueWork(() -> onHandle(message, (ServerPlayer) context.player()));
        }
    }

    private static void onHandle(RequestMimoCloneDescriptionsPacket message, @Nullable ServerPlayer player) {
        if (!GameModeUtil.canEditSite(player)) {
            return;
        }
        TTSSite site = AvailableSites.getTTSSite(StringUtils.trimToNull(message.siteId()));
        if (!(site instanceof TTSMimoSite)) {
            return;
        }
        try {
            Map<String, String> descriptions =
                    MimoCloneSampleRepository.defaultInstance().readAllDescriptions();
            NetworkHandler.sendToClientPlayer(
                    new SyncMimoCloneDescriptionsPacket(message.siteId(), descriptions), player);
        } catch (Exception e) {
            TlmMimoTts.LOGGER.error("MiMo 克隆音色描述读取失败", e);
        }
    }
}
