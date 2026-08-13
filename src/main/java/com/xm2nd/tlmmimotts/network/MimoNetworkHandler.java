package com.xm2nd.tlmmimotts.network;

import com.xm2nd.tlmmimotts.TlmMimoTts;
import net.minecraft.resources.ResourceLocation;
import net.minecraftforge.network.NetworkDirection;
import net.minecraftforge.network.NetworkRegistry;
import net.minecraftforge.network.simple.SimpleChannel;

import java.util.Optional;

/**
 * 网络包注册（Forge SimpleChannel）：
 * 3 个客户端 → 服务端包（刷新克隆音色 / 请求描述 / 保存描述）
 * 与 1 个服务端 → 客户端包（同步描述）。
 */
public final class MimoNetworkHandler {
    private MimoNetworkHandler() {
    }

    public static final String PROTOCOL_VERSION = "1";

    public static final SimpleChannel CHANNEL = NetworkRegistry.newSimpleChannel(
            new ResourceLocation(TlmMimoTts.MOD_ID, "main"),
            () -> PROTOCOL_VERSION,
            PROTOCOL_VERSION::equals,
            PROTOCOL_VERSION::equals);

    public static void register() {
        int id = 0;
        CHANNEL.registerMessage(id++, RefreshMimoClonesPacket.class,
                RefreshMimoClonesPacket::encode, RefreshMimoClonesPacket::decode,
                RefreshMimoClonesPacket::handle, Optional.of(NetworkDirection.PLAY_TO_SERVER));
        CHANNEL.registerMessage(id++, RequestMimoCloneDescriptionsPacket.class,
                RequestMimoCloneDescriptionsPacket::encode, RequestMimoCloneDescriptionsPacket::decode,
                RequestMimoCloneDescriptionsPacket::handle, Optional.of(NetworkDirection.PLAY_TO_SERVER));
        CHANNEL.registerMessage(id++, SaveMimoCloneDescriptionPacket.class,
                SaveMimoCloneDescriptionPacket::encode, SaveMimoCloneDescriptionPacket::decode,
                SaveMimoCloneDescriptionPacket::handle, Optional.of(NetworkDirection.PLAY_TO_SERVER));
        CHANNEL.registerMessage(id, SyncMimoCloneDescriptionsPacket.class,
                SyncMimoCloneDescriptionsPacket::encode, SyncMimoCloneDescriptionsPacket::decode,
                SyncMimoCloneDescriptionsPacket::handle, Optional.of(NetworkDirection.PLAY_TO_CLIENT));
    }
}
