package com.xm2nd.tlmmimotts.network;

import com.github.tartaricacid.touhoulittlemaid.ai.manager.site.AvailableSites;
import com.github.tartaricacid.touhoulittlemaid.ai.service.tts.TTSSite;
import com.github.tartaricacid.touhoulittlemaid.network.NetworkHandler;
import com.github.tartaricacid.touhoulittlemaid.util.GameModeUtil;
import com.xm2nd.tlmmimotts.TlmMimoTts;
import com.xm2nd.tlmmimotts.ai.service.tts.mimo.TTSMimoSite;
import com.xm2nd.tlmmimotts.server.MimoCloneSampleRepository;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.server.level.ServerPlayer;
import net.minecraftforge.network.NetworkEvent;
import org.apache.commons.lang3.StringUtils;
import org.jetbrains.annotations.Nullable;

import java.util.Map;
import java.util.function.Supplier;

/**
 * 客户端 → 服务端：请求某个 MiMo 站点的全部克隆音色描述。
 * 打开站点编辑页时发送；服务端复用 TLM 站点编辑权限校验后回发
 * {@link SyncMimoCloneDescriptionsPacket}。
 */
public class RequestMimoCloneDescriptionsPacket {
    private final String siteId;

    public RequestMimoCloneDescriptionsPacket(String siteId) {
        this.siteId = siteId;
    }

    public String siteId() {
        return siteId;
    }

    public static void encode(RequestMimoCloneDescriptionsPacket message, FriendlyByteBuf buf) {
        buf.writeUtf(message.siteId());
    }

    public static RequestMimoCloneDescriptionsPacket decode(FriendlyByteBuf buf) {
        return new RequestMimoCloneDescriptionsPacket(buf.readUtf());
    }

    public static void handle(RequestMimoCloneDescriptionsPacket message, Supplier<NetworkEvent.Context> contextSupplier) {
        NetworkEvent.Context context = contextSupplier.get();
        context.enqueueWork(() -> onHandle(message, context.getSender()));
        context.setPacketHandled(true);
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
