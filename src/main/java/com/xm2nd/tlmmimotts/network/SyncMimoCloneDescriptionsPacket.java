package com.xm2nd.tlmmimotts.network;

import com.xm2nd.tlmmimotts.client.gui.MimoCloneDescriptions;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraftforge.network.NetworkEvent;

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.function.Supplier;

/**
 * 服务端 → 客户端：同步某个 MiMo 站点的全部克隆音色描述（voiceId → 描述）。
 * 打开编辑页请求、刷新克隆音色、保存描述后都会触发同步，客户端更新缓存并即时填入编辑页。
 */
public class SyncMimoCloneDescriptionsPacket {
    private final String siteId;
    private final Map<String, String> descriptions;

    public SyncMimoCloneDescriptionsPacket(String siteId, Map<String, String> descriptions) {
        this.siteId = siteId;
        this.descriptions = descriptions;
    }

    public String siteId() {
        return siteId;
    }

    public Map<String, String> descriptions() {
        return descriptions;
    }

    public static void encode(SyncMimoCloneDescriptionsPacket message, FriendlyByteBuf buf) {
        buf.writeUtf(message.siteId());
        buf.writeInt(message.descriptions().size());
        message.descriptions().forEach((voiceId, description) -> {
            buf.writeUtf(voiceId);
            buf.writeUtf(description);
        });
    }

    public static SyncMimoCloneDescriptionsPacket decode(FriendlyByteBuf buf) {
        String siteId = buf.readUtf();
        int size = buf.readInt();
        Map<String, String> descriptions = new LinkedHashMap<>();
        for (int i = 0; i < size; i++) {
            descriptions.put(buf.readUtf(), buf.readUtf());
        }
        return new SyncMimoCloneDescriptionsPacket(siteId, descriptions);
    }

    public static void handle(SyncMimoCloneDescriptionsPacket message, Supplier<NetworkEvent.Context> contextSupplier) {
        NetworkEvent.Context context = contextSupplier.get();
        context.enqueueWork(() -> MimoCloneDescriptions.applyToActiveLayout(message.siteId(), message.descriptions()));
        context.setPacketHandled(true);
    }
}
