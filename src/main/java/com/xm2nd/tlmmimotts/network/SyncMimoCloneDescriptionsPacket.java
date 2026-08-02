package com.xm2nd.tlmmimotts.network;

import com.xm2nd.tlmmimotts.client.gui.MimoCloneDescriptions;
import io.netty.buffer.ByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;
import net.neoforged.neoforge.network.handling.IPayloadContext;

import java.util.LinkedHashMap;
import java.util.Map;

import static com.xm2nd.tlmmimotts.TlmMimoTts.MOD_ID;

/**
 * 服务端 → 客户端：同步某个 MiMo 站点的全部克隆音色描述（voiceId → 描述）。
 * 打开编辑页请求、刷新克隆音色、保存描述后都会触发同步，客户端更新缓存并即时填入编辑页。
 */
public record SyncMimoCloneDescriptionsPacket(String siteId, Map<String, String> descriptions) implements CustomPacketPayload {
    public static final CustomPacketPayload.Type<SyncMimoCloneDescriptionsPacket> TYPE =
            new CustomPacketPayload.Type<>(ResourceLocation.fromNamespaceAndPath(MOD_ID, "sync_mimo_clone_descriptions"));
    public static final StreamCodec<ByteBuf, SyncMimoCloneDescriptionsPacket> STREAM_CODEC = new StreamCodec<>() {
        @Override
        public SyncMimoCloneDescriptionsPacket decode(ByteBuf byteBuf) {
            net.minecraft.network.FriendlyByteBuf buf = new net.minecraft.network.FriendlyByteBuf(byteBuf);
            String siteId = buf.readUtf();
            int size = buf.readInt();
            Map<String, String> descriptions = new LinkedHashMap<>();
            for (int i = 0; i < size; i++) {
                descriptions.put(buf.readUtf(), buf.readUtf());
            }
            return new SyncMimoCloneDescriptionsPacket(siteId, descriptions);
        }

        @Override
        public void encode(ByteBuf byteBuf, SyncMimoCloneDescriptionsPacket message) {
            net.minecraft.network.FriendlyByteBuf buf = new net.minecraft.network.FriendlyByteBuf(byteBuf);
            buf.writeUtf(message.siteId());
            buf.writeInt(message.descriptions().size());
            message.descriptions().forEach((voiceId, description) -> {
                buf.writeUtf(voiceId);
                buf.writeUtf(description);
            });
        }
    };

    @Override
    public CustomPacketPayload.Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }

    public static void handle(SyncMimoCloneDescriptionsPacket message, IPayloadContext context) {
        if (context.flow().isClientbound()) {
            context.enqueueWork(() -> MimoCloneDescriptions.applyToActiveLayout(message.siteId(), message.descriptions()));
        }
    }
}
